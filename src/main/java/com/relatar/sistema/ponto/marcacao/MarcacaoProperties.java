package com.relatar.sistema.ponto.marcacao;

import java.time.Duration;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * Configuracoes de marcacao (prefixo {@code app.marcacao}).
 *
 * @param localizacaoObrigatoria recusa marcacoes sem a localizacao do dispositivo
 * @param areaPermitida          local (rua) onde o ponto pode ser registrado
 * @param geocodificacao         consulta do endereco (rua) a partir das coordenadas
 */
@Validated
@ConfigurationProperties("app.marcacao")
public record MarcacaoProperties(
        boolean localizacaoObrigatoria,
        @Valid @NotNull AreaPermitida areaPermitida,
        @Valid @NotNull Geocodificacao geocodificacao) {

    /**
     * @param habilitada           false aceita marcacoes de qualquer lugar
     * @param nome                 nome exibido ao usuario (ex.: "Rua Waldemiro Alberto Hulle")
     * @param toleranciaMetros     distancia maxima entre o dispositivo e o tracado da rua
     * @param precisaoMaximaMetros raio de incerteza maximo aceito; acima disso a posicao nao e confiavel
     * @param tracado              pontos "latitude,longitude" do eixo da rua, na ordem
     */
    public record AreaPermitida(
            boolean habilitada,
            @NotBlank String nome,
            @Positive double toleranciaMetros,
            @Positive double precisaoMaximaMetros,
            @DefaultValue List<String> tracado) {
    }

    /**
     * @param googleApiKey chave da Google Geocoding API; quando vazia, usa o Nominatim (OpenStreetMap)
     * @param nominatimUrl endereco base do servico Nominatim
     * @param userAgent    identificacao exigida pela politica de uso do Nominatim
     * @param timeout      tempo maximo de espera pela resposta
     */
    public record Geocodificacao(
            boolean habilitada,
            String googleApiKey,
            @NotBlank String nominatimUrl,
            @NotBlank String userAgent,
            @NotNull Duration timeout) {

        public boolean usaGoogle() {
            return googleApiKey != null && !googleApiKey.isBlank();
        }
    }
}
