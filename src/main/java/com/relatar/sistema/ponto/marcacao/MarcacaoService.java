package com.relatar.sistema.ponto.marcacao;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import com.relatar.sistema.ponto.auth.AuthenticationFailedException;
import com.relatar.sistema.ponto.common.error.BusinessRuleException;
import com.relatar.sistema.ponto.config.AppProperties;
import com.relatar.sistema.ponto.usuario.Usuario;
import com.relatar.sistema.ponto.usuario.UsuarioRepository;

/**
 * Registro e consulta de marcacoes. O instante vem sempre do relogio do servidor e o tipo e definido
 * automaticamente: a primeira marcacao do dia e ENTRADA e as seguintes alternam.
 */
@Service
public class MarcacaoService {

    /** Evita marcacoes duplicadas por clique repetido. */
    static final Duration INTERVALO_MINIMO = Duration.ofMinutes(1);
    static final long MAX_DIAS_CONSULTA = 92;

    private final MarcacaoRepository marcacoes;
    private final UsuarioRepository usuarios;
    private final Clock clock;
    private final ZoneId zona;
    private final boolean localizacaoObrigatoria;
    private final LocalPermitidoService localPermitido;
    private final TransactionTemplate transacao;

    public MarcacaoService(MarcacaoRepository marcacoes, UsuarioRepository usuarios, Clock clock,
            AppProperties properties, MarcacaoProperties marcacaoProperties, LocalPermitidoService localPermitido,
            PlatformTransactionManager transactionManager) {
        this.marcacoes = marcacoes;
        this.usuarios = usuarios;
        this.clock = clock;
        this.zona = properties.timezone();
        this.localizacaoObrigatoria = marcacaoProperties.localizacaoObrigatoria();
        this.localPermitido = localPermitido;
        this.transacao = new TransactionTemplate(transactionManager);
    }

    /**
     * Registra o ponto. A verificacao do local (que pode consultar o servico de mapas) acontece antes da
     * transacao, para nao manter a linha do usuario bloqueada durante a chamada externa.
     */
    public Marcacao registrar(Long usuarioId, Localizacao localizacao) {
        if (localizacao == null && (localizacaoObrigatoria || localPermitido.habilitado())) {
            throw new BusinessRuleException("LOCALIZACAO_OBRIGATORIA",
                    "Permita o acesso à localização do dispositivo para registrar o ponto.");
        }
        String endereco = localizacao == null ? null : localPermitido.exigirPermitido(localizacao).endereco();
        return transacao.execute(status -> gravar(usuarioId, localizacao, endereco));
    }

    private Marcacao gravar(Long usuarioId, Localizacao localizacao, String endereco) {
        // O bloqueio da linha do usuario serializa cliques simultaneos.
        Usuario usuario = usuarios.findByIdForUpdate(usuarioId)
                .filter(Usuario::isAtivo)
                .orElseThrow(AuthenticationFailedException::sessaoInvalida);
        Instant agora = clock.instant().truncatedTo(ChronoUnit.MICROS);
        marcacoes.findFirstByUsuarioIdOrderByInstanteDesc(usuario.getId())
                .filter(ultima -> Duration.between(ultima.getInstante(), agora).compareTo(INTERVALO_MINIMO) < 0)
                .ifPresent(ultima -> {
                    throw new BusinessRuleException("MARCACAO_DUPLICADA",
                            "Você já registrou o ponto há menos de 1 minuto.");
                });
        LocalDate hoje = LocalDate.ofInstant(agora, zona);
        List<Marcacao> doDia = marcacoes.findByUsuarioIdAndDataReferenciaOrderByInstanteAsc(usuario.getId(), hoje);
        return marcacoes.save(new Marcacao(usuario.getId(), proximoTipo(doDia), agora, hoje, localizacao, endereco));
    }

    @Transactional(readOnly = true)
    public Hoje hoje(Long usuarioId) {
        Instant agora = clock.instant();
        LocalDate hoje = LocalDate.ofInstant(agora, zona);
        List<Marcacao> doDia = marcacoes.findByUsuarioIdAndDataReferenciaOrderByInstanteAsc(usuarioId, hoje);
        return new Hoje(resumir(hoje, doDia, agora), proximoTipo(doDia), agora, zona.getId());
    }

    /** Dias com marcacoes no periodo, do mais recente para o mais antigo. */
    @Transactional(readOnly = true)
    public List<DiaResponse> historico(Long usuarioId, LocalDate de, LocalDate ate) {
        if (de.isAfter(ate)) {
            throw new BusinessRuleException("PERIODO_INVALIDO", "A data inicial deve ser anterior à final.");
        }
        if (ChronoUnit.DAYS.between(de, ate) >= MAX_DIAS_CONSULTA) {
            throw new BusinessRuleException("PERIODO_MUITO_LONGO",
                    "Consulte no máximo " + MAX_DIAS_CONSULTA + " dias por vez.");
        }
        Instant agora = clock.instant();
        Map<LocalDate, List<Marcacao>> porDia = new TreeMap<>(Comparator.reverseOrder());
        for (Marcacao marcacao : marcacoes.findByUsuarioIdAndDataReferenciaBetweenOrderByInstanteAsc(usuarioId, de, ate)) {
            porDia.computeIfAbsent(marcacao.getDataReferencia(), d -> new ArrayList<>()).add(marcacao);
        }
        return porDia.entrySet().stream()
                .map(dia -> resumir(dia.getKey(), dia.getValue(), agora))
                .toList();
    }

    private static TipoMarcacao proximoTipo(List<Marcacao> doDia) {
        return doDia.isEmpty() ? TipoMarcacao.ENTRADA : doDia.getLast().getTipo().oposto();
    }

    ZoneId zona() {
        return zona;
    }

    LocalDate hoje() {
        return LocalDate.ofInstant(clock.instant(), zona);
    }

    DiaResponse resumir(LocalDate data, List<Marcacao> doDia, Instant agora) {
        long segundos = 0;
        Instant entradaAberta = null;
        for (Marcacao marcacao : doDia) {
            if (marcacao.getTipo() == TipoMarcacao.ENTRADA) {
                entradaAberta = marcacao.getInstante();
            } else if (entradaAberta != null) {
                segundos += Duration.between(entradaAberta, marcacao.getInstante()).toSeconds();
                entradaAberta = null;
            }
        }
        boolean ehHoje = data.equals(LocalDate.ofInstant(agora, zona));
        boolean emJornada = entradaAberta != null && ehHoje;
        if (emJornada) {
            segundos += Duration.between(entradaAberta, agora).toSeconds();
        }
        List<DiaResponse.MarcacaoResponse> itens = doDia.stream().map(DiaResponse.MarcacaoResponse::de).toList();
        return new DiaResponse(data, itens, segundos, emJornada, entradaAberta != null && !ehHoje);
    }

    /**
     * @param agora    instante do servidor, para o cliente calcular a diferenca do proprio relogio
     * @param timezone fuso usado na data de referencia
     */
    public record Hoje(DiaResponse dia, TipoMarcacao proximoTipo, Instant agora, String timezone) {
    }
}
