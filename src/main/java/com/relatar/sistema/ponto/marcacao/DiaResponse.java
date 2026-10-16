package com.relatar.sistema.ponto.marcacao;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * Marcacoes de um dia e o tempo trabalhado.
 *
 * @param segundosTrabalhados soma dos intervalos ENTRADA-SAIDA; com jornada em aberto hoje, conta ate agora
 * @param emJornada           ultima marcacao de hoje e uma ENTRADA
 * @param incompleto          dia passado terminado em ENTRADA (falta a SAIDA; o trecho aberto nao e contado)
 */
public record DiaResponse(
        LocalDate data,
        List<MarcacaoResponse> marcacoes,
        long segundosTrabalhados,
        boolean emJornada,
        boolean incompleto) {

    /**
     * @param localizacao localizacao do dispositivo no momento da marcacao, ou null se nao houver
     * @param endereco    rua, bairro e cidade aproximados, ou null
     */
    public record MarcacaoResponse(Long id, TipoMarcacao tipo, Instant instante, Localizacao localizacao,
            String endereco) {

        static MarcacaoResponse de(Marcacao marcacao) {
            return new MarcacaoResponse(marcacao.getId(), marcacao.getTipo(), marcacao.getInstante(),
                    marcacao.getLocalizacao(), marcacao.getEndereco());
        }
    }
}
