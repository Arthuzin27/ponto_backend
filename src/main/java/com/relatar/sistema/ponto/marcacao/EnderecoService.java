package com.relatar.sistema.ponto.marcacao;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Descobre o endereco (rua, bairro, cidade) de uma coordenada. Usa a Google Geocoding API quando ha chave
 * configurada (APP_GOOGLE_MAPS_API_KEY) e, sem ela, o Nominatim (OpenStreetMap).
 * Falhas nunca impedem a marcacao: o endereco e informativo; a regra de local usa o tracado da rua.
 */
@Service
public class EnderecoService {

    private static final Logger log = LoggerFactory.getLogger(EnderecoService.class);
    private static final int TAMANHO_CACHE = 1000;
    /** Politica de uso do Nominatim publico: no maximo 1 consulta por segundo. */
    private static final long INTERVALO_NOMINATIM_MS = 1100;

    private final MarcacaoProperties.Geocodificacao config;
    private final RestClient http;
    /** Chave: coordenadas arredondadas (~11 m). Evita consultas repetidas ao mesmo ponto. */
    private final Map<String, Endereco> cache = new LinkedHashMap<>(64, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, Endereco> maisAntiga) {
            return size() > TAMANHO_CACHE;
        }
    };
    private long proximaConsultaNominatimMs;

    public EnderecoService(MarcacaoProperties properties) {
        this.config = properties.geocodificacao();
        JdkClientHttpRequestFactory fabrica = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder().connectTimeout(config.timeout()).build());
        fabrica.setReadTimeout(config.timeout());
        this.http = RestClient.builder().requestFactory(fabrica).build();
        if (config.habilitada()) {
            log.info("Consulta de endereco pelo {}", config.usaGoogle() ? "Google Maps" : "Nominatim (OpenStreetMap)");
        }
    }

    public Optional<Endereco> buscar(double latitude, double longitude) {
        if (!config.habilitada()) {
            return Optional.empty();
        }
        String chave = String.format(Locale.ROOT, "%.4f,%.4f", latitude, longitude);
        synchronized (cache) {
            Endereco emCache = cache.get(chave);
            if (emCache != null) {
                return Optional.of(emCache);
            }
        }
        Optional<Endereco> endereco;
        try {
            endereco = config.usaGoogle() ? consultarGoogle(latitude, longitude) : consultarNominatim(latitude, longitude);
        } catch (RestClientException ex) {
            // A mensagem pode conter a URL (e a chave do Google): registra apenas o tipo do erro.
            log.warn("Nao foi possivel consultar o endereco da localizacao: {}", ex.getClass().getSimpleName());
            endereco = Optional.empty();
        }
        endereco.ifPresent(e -> {
            synchronized (cache) {
                cache.put(chave, e);
            }
        });
        return endereco;
    }

    private Optional<Endereco> consultarGoogle(double latitude, double longitude) {
        RespostaGoogle resposta = http.get()
                .uri("https://maps.googleapis.com/maps/api/geocode/json?latlng={lat},{lon}&language=pt-BR&key={key}",
                        coordenada(latitude), coordenada(longitude), config.googleApiKey())
                .retrieve()
                .body(RespostaGoogle.class);
        if (resposta == null || !"OK".equals(resposta.status()) || resposta.results() == null) {
            if (resposta != null && !"ZERO_RESULTS".equals(resposta.status())) {
                log.warn("Google Geocoding respondeu {}: verifique a chave e se a API esta ativada", resposta.status());
            }
            return Optional.empty();
        }
        // O primeiro resultado e o mais especifico; procura o primeiro que tenha a rua.
        return resposta.results().stream()
                .map(RespostaGoogle.Resultado::address_components)
                .filter(Objects::nonNull)
                .map(EnderecoService::montarGoogle)
                .filter(e -> e.logradouro() != null)
                .findFirst()
                .or(() -> resposta.results().stream()
                        .map(RespostaGoogle.Resultado::address_components)
                        .filter(Objects::nonNull)
                        .map(EnderecoService::montarGoogle)
                        .findFirst());
    }

    private static Endereco montarGoogle(List<RespostaGoogle.Componente> componentes) {
        return new Endereco(
                componente(componentes, "route"),
                componente(componentes, "sublocality_level_1", "sublocality", "neighborhood"),
                componente(componentes, "administrative_area_level_2", "locality"));
    }

    private static String componente(List<RespostaGoogle.Componente> componentes, String... tipos) {
        for (String tipo : tipos) {
            for (RespostaGoogle.Componente c : componentes) {
                if (c.types() != null && c.types().contains(tipo) && c.long_name() != null) {
                    return c.long_name();
                }
            }
        }
        return null;
    }

    private Optional<Endereco> consultarNominatim(double latitude, double longitude) {
        try {
            aguardarVezNominatim();
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return Optional.empty();
        }
        RespostaNominatim resposta = http.get()
                .uri(config.nominatimUrl() + "/reverse?format=jsonv2&lat={lat}&lon={lon}&zoom=17&addressdetails=1"
                        + "&accept-language=pt-BR", coordenada(latitude), coordenada(longitude))
                .header("User-Agent", config.userAgent())
                .retrieve()
                .body(RespostaNominatim.class);
        if (resposta == null || resposta.address() == null) {
            return Optional.empty();
        }
        Map<String, String> a = resposta.address();
        Endereco endereco = new Endereco(
                primeiro(a, "road", "pedestrian", "footway", "path"),
                primeiro(a, "suburb", "neighbourhood", "quarter", "hamlet", "village"),
                primeiro(a, "city", "town", "municipality", "village"));
        return endereco.vazio() ? Optional.empty() : Optional.of(endereco);
    }

    private void aguardarVezNominatim() throws InterruptedException {
        long espera;
        synchronized (this) {
            long agora = System.currentTimeMillis();
            long vez = Math.max(agora, proximaConsultaNominatimMs);
            proximaConsultaNominatimMs = vez + INTERVALO_NOMINATIM_MS;
            espera = vez - agora;
        }
        if (espera > 0) {
            Thread.sleep(Duration.ofMillis(espera));
        }
    }

    private static String primeiro(Map<String, String> valores, String... chaves) {
        return Stream.of(chaves).map(valores::get).filter(v -> v != null && !v.isBlank()).findFirst().orElse(null);
    }

    private static String coordenada(double valor) {
        return String.format(Locale.ROOT, "%.7f", valor);
    }

    record RespostaNominatim(Map<String, String> address) {
    }

    record RespostaGoogle(String status, List<Resultado> results) {

        record Resultado(List<Componente> address_components) {
        }

        record Componente(String long_name, List<String> types) {
        }
    }

    /** Endereco aproximado de uma coordenada. Qualquer parte pode faltar. */
    public record Endereco(String logradouro, String bairro, String cidade) {

        boolean vazio() {
            return logradouro == null && bairro == null && cidade == null;
        }

        /** Ex.: "Rua Waldemiro Alberto Hulle, Jefferson de Aguiar, Domingos Martins". */
        public String descricao() {
            return String.join(", ", Stream.of(logradouro, bairro, cidade).filter(Objects::nonNull).distinct().toList());
        }
    }
}
