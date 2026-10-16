package com.relatar.sistema.ponto.config;

import java.time.Duration;
import java.time.ZoneId;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * Configuracoes proprias da aplicacao (prefixo {@code app}).
 *
 * @param timezone fuso horario padrao da empresa, usado na apresentacao e na data de referencia da jornada
 * @param cors     origens autorizadas a chamar a API pelo navegador
 * @param clock    monitoramento da sincronizacao do relogio do servidor
 */
@Validated
@ConfigurationProperties("app")
public record AppProperties(
        @NotNull ZoneId timezone,
        @Valid @NotNull Cors cors,
        @Valid @NotNull ClockSync clock) {

    public record Cors(@DefaultValue List<String> allowedOrigins) {
    }

    public record ClockSync(@Valid @NotNull Ntp ntp) {
    }

    /**
     * @param enabled   habilita a verificacao periodica contra um servidor NTP
     * @param server    servidor NTP de referencia (padrao: NTP.br)
     * @param maxOffset diferenca maxima aceitavel entre o relogio local e o servidor NTP
     * @param timeout   tempo maximo de espera pela resposta NTP
     * @param cacheTtl  intervalo minimo entre consultas, para nao sobrecarregar o servidor NTP
     */
    public record Ntp(
            boolean enabled,
            @NotBlank String server,
            @NotNull Duration maxOffset,
            @NotNull Duration timeout,
            @NotNull Duration cacheTtl) {
    }
}
