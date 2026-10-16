package com.relatar.sistema.ponto.config;

import java.time.Clock;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Fonte unica de tempo da aplicacao. Todo codigo de dominio deve obter o instante atual
 * a partir deste {@link Clock} (nunca de {@code Instant.now()} ou do horario enviado pelo cliente),
 * o que tambem permite testes deterministicos.
 */
@Configuration(proxyBeanMethods = false)
public class TimeConfig {

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}
