package com.relatar.sistema.ponto.marcacao;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.relatar.sistema.ponto.common.error.BusinessRuleException;
import com.relatar.sistema.ponto.usuario.Perfil;
import com.relatar.sistema.ponto.usuario.Usuario;
import com.relatar.sistema.ponto.usuario.UsuarioRepository;
import com.relatar.sistema.ponto.usuario.UsuarioService;

/**
 * Paineis: situacao da equipe hoje e banco de horas.
 * <p>
 * Banco de horas: saldo = horas trabalhadas - jornada esperada, somado dia a dia. A jornada vale de segunda a
 * sexta (sabado e domingo esperam zero; feriados ainda nao sao considerados). O dia de hoje so entra no saldo
 * quando termina, e dias anteriores ao cadastro do usuario sao ignorados.
 */
@Service
public class PainelService {

    static final long MAX_DIAS_BANCO = 366;

    private final MarcacaoRepository marcacoes;
    private final MarcacaoService marcacaoService;
    private final UsuarioRepository usuarios;
    private final UsuarioService usuarioService;
    private final Clock clock;

    public PainelService(MarcacaoRepository marcacoes, MarcacaoService marcacaoService, UsuarioRepository usuarios,
            UsuarioService usuarioService, Clock clock) {
        this.marcacoes = marcacoes;
        this.marcacaoService = marcacaoService;
        this.usuarios = usuarios;
        this.usuarioService = usuarioService;
        this.clock = clock;
    }

    /** Usuarios ativos e as marcacoes de hoje de cada um. */
    @Transactional(readOnly = true)
    public List<MembroHoje> equipeHoje() {
        Instant agora = clock.instant();
        LocalDate hoje = marcacaoService.hoje();
        Map<Long, List<Marcacao>> porUsuario = agrupar(marcacoes.findByDataReferenciaBetweenOrderByInstanteAsc(hoje, hoje));
        return ativos().stream()
                .map(usuario -> {
                    DiaResponse dia = marcacaoService.resumir(hoje,
                            porUsuario.getOrDefault(usuario.getId(), List.of()), agora);
                    return new MembroHoje(UsuarioResumo.de(usuario), dia, situacao(dia));
                })
                .toList();
    }

    @Transactional(readOnly = true)
    public BancoHoras bancoDeHoras(Long usuarioId, LocalDate de, LocalDate ate) {
        validarPeriodo(de, ate);
        Usuario usuario = usuarioService.buscar(usuarioId);
        List<Marcacao> doPeriodo = marcacoes.findByUsuarioIdAndDataReferenciaBetweenOrderByInstanteAsc(usuarioId, de, ate);
        return calcular(usuario, de, ate, doPeriodo, true);
    }

    /** Resumo do banco de horas de cada usuario ativo (sem o detalhe por dia). */
    @Transactional(readOnly = true)
    public List<BancoHoras> bancoDaEquipe(LocalDate de, LocalDate ate) {
        validarPeriodo(de, ate);
        Map<Long, List<Marcacao>> porUsuario = agrupar(marcacoes.findByDataReferenciaBetweenOrderByInstanteAsc(de, ate));
        return ativos().stream()
                .map(usuario -> calcular(usuario, de, ate, porUsuario.getOrDefault(usuario.getId(), List.of()), false))
                .toList();
    }

    private BancoHoras calcular(Usuario usuario, LocalDate de, LocalDate ate, List<Marcacao> doPeriodo,
            boolean comDias) {
        Instant agora = clock.instant();
        LocalDate hoje = marcacaoService.hoje();
        LocalDate cadastro = LocalDate.ofInstant(usuario.getCriadoEm(), marcacaoService.zona());
        LocalDate inicio = de.isBefore(cadastro) ? cadastro : de;
        LocalDate fim = ate.isAfter(hoje) ? hoje : ate;
        long esperadoPorDia = usuario.getJornadaDiariaMinutos() * 60L;

        Map<LocalDate, List<Marcacao>> porDia = doPeriodo.stream()
                .collect(Collectors.groupingBy(Marcacao::getDataReferencia));
        List<DiaBanco> dias = new ArrayList<>();
        long trabalhado = 0;
        long esperado = 0;
        int diasUteis = 0;
        int faltas = 0;
        int pendencias = 0;
        DiaBanco diaDeHoje = null;
        for (LocalDate data = inicio; !data.isAfter(fim); data = data.plusDays(1)) {
            DiaResponse dia = marcacaoService.resumir(data, porDia.getOrDefault(data, List.of()), agora);
            boolean util = diaUtil(data);
            long esperadoDia = util ? esperadoPorDia : 0;
            boolean ehHoje = data.equals(hoje);
            DiaBanco linha = new DiaBanco(data, util, ehHoje, dia.segundosTrabalhados(), esperadoDia,
                    ehHoje ? 0 : dia.segundosTrabalhados() - esperadoDia, dia.emJornada(), dia.incompleto(),
                    dia.marcacoes());
            if (ehHoje) {
                diaDeHoje = linha;
            } else {
                trabalhado += dia.segundosTrabalhados();
                esperado += esperadoDia;
                diasUteis += util ? 1 : 0;
                faltas += util && esperadoDia > 0 && dia.marcacoes().isEmpty() ? 1 : 0;
                pendencias += dia.incompleto() ? 1 : 0;
            }
            if (comDias) {
                dias.add(linha);
            }
        }
        dias.sort(Comparator.comparing(DiaBanco::data).reversed());
        return new BancoHoras(UsuarioResumo.de(usuario), de, ate, trabalhado, esperado, trabalhado - esperado,
                diasUteis, faltas, pendencias, diaDeHoje, dias);
    }

    private List<Usuario> ativos() {
        return usuarios.findAllByOrderByNomeAsc().stream().filter(Usuario::isAtivo).toList();
    }

    private static Map<Long, List<Marcacao>> agrupar(List<Marcacao> lista) {
        Map<Long, List<Marcacao>> porUsuario = new HashMap<>();
        for (Marcacao marcacao : lista) {
            porUsuario.computeIfAbsent(marcacao.getUsuarioId(), id -> new ArrayList<>()).add(marcacao);
        }
        return porUsuario;
    }

    private static boolean diaUtil(LocalDate data) {
        DayOfWeek dia = data.getDayOfWeek();
        return dia != DayOfWeek.SATURDAY && dia != DayOfWeek.SUNDAY;
    }

    private static Situacao situacao(DiaResponse dia) {
        if (dia.emJornada()) {
            return Situacao.EM_JORNADA;
        }
        return dia.marcacoes().isEmpty() ? Situacao.SEM_MARCACAO : Situacao.ENCERRADO;
    }

    private static void validarPeriodo(LocalDate de, LocalDate ate) {
        if (de.isAfter(ate)) {
            throw new BusinessRuleException("PERIODO_INVALIDO", "A data inicial deve ser anterior à final.");
        }
        if (ChronoUnit.DAYS.between(de, ate) >= MAX_DIAS_BANCO) {
            throw new BusinessRuleException("PERIODO_MUITO_LONGO",
                    "Consulte no máximo " + MAX_DIAS_BANCO + " dias por vez.");
        }
    }

    public enum Situacao {
        EM_JORNADA,
        ENCERRADO,
        SEM_MARCACAO
    }

    public record UsuarioResumo(Long id, String nome, Perfil perfil, int jornadaDiariaMinutos) {

        static UsuarioResumo de(Usuario usuario) {
            return new UsuarioResumo(usuario.getId(), usuario.getNome(), usuario.getPerfil(),
                    usuario.getJornadaDiariaMinutos());
        }
    }

    public record MembroHoje(UsuarioResumo usuario, DiaResponse dia, Situacao situacao) {
    }

    /**
     * @param saldoSegundos       positivo = horas a mais; negativo = horas devidas. Nao inclui o dia de hoje.
     * @param faltas              dias uteis encerrados sem nenhuma marcacao
     * @param pendencias          dias encerrados com entrada sem saida (o trecho aberto nao conta)
     * @param hoje                andamento do dia atual, quando dentro do periodo
     * @param dias                detalhe por dia, do mais recente para o mais antigo (vazio no resumo da equipe)
     */
    public record BancoHoras(UsuarioResumo usuario, LocalDate de, LocalDate ate, long segundosTrabalhados,
            long segundosEsperados, long saldoSegundos, int diasUteis, int faltas, int pendencias, DiaBanco hoje,
            List<DiaBanco> dias) {
    }

    public record DiaBanco(LocalDate data, boolean diaUtil, boolean hoje, long segundosTrabalhados,
            long segundosEsperados, long saldoSegundos, boolean emJornada, boolean incompleto,
            List<DiaResponse.MarcacaoResponse> marcacoes) {
    }
}
