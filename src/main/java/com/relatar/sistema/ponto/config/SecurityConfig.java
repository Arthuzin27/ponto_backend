package com.relatar.sistema.ponto.config;

import java.time.Duration;
import java.util.List;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter.ReferrerPolicy;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import com.relatar.sistema.ponto.auth.UsuarioAtivoJwtConverter;
import com.relatar.sistema.ponto.common.error.ProblemDetailsSecurityHandler;
import com.relatar.sistema.ponto.common.web.RequestIdFilter;

/**
 * Seguranca base da API: stateless, negando por padrao tudo que nao for explicitamente publico.
 * Autenticacao por token JWT (ver auth.JwtConfig); regras por perfil via {@code @PreAuthorize}.
 */
@Configuration(proxyBeanMethods = false)
@EnableMethodSecurity
public class SecurityConfig {

    static final String[] PUBLIC_ENDPOINTS = {
            "/actuator/health", "/actuator/health/**", "/actuator/info",
            // So respondem quando a documentacao esta habilitada (APP_API_DOCS_ENABLED); caso contrario, 404.
            "/v3/api-docs", "/v3/api-docs/**", "/swagger-ui.html", "/swagger-ui/**",
            "/error"
    };

    @Bean
    SecurityFilterChain apiSecurityFilterChain(HttpSecurity http, ProblemDetailsSecurityHandler problemHandler,
            UsuarioAtivoJwtConverter jwtAuthenticationConverter) throws Exception {
        http
                // Autenticacao apenas por cabecalho Authorization (Bearer), sem cookies: CSRF nao se aplica.
                // Deve ser reavaliado se for adicionado um refresh token em cookie.
                .csrf(AbstractHttpConfigurer::disable)
                .cors(Customizer.withDefaults())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .requestCache(AbstractHttpConfigurer::disable)
                .headers(headers -> headers
                        .frameOptions(frame -> frame.deny())
                        .referrerPolicy(referrer -> referrer.policy(ReferrerPolicy.NO_REFERRER)))
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(problemHandler)
                        .accessDeniedHandler(problemHandler))
                .oauth2ResourceServer(resourceServer -> resourceServer
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter))
                        .authenticationEntryPoint(problemHandler)
                        .accessDeniedHandler(problemHandler))
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(PUBLIC_ENDPOINTS).permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/auth/login").permitAll()
                        .anyRequest().authenticated());
        return http.build();
    }

    /**
     * CORS restrito as origens configuradas em {@code app.cors.allowed-origins}. Sem origens configuradas,
     * nenhuma chamada de navegador de outra origem e aceita.
     */
    @Bean
    CorsConfigurationSource corsConfigurationSource(AppProperties properties) {
        List<String> origins = properties.cors().allowedOrigins().stream()
                .map(String::trim)
                .filter(origin -> !origin.isEmpty())
                .toList();
        if (origins.contains("*")) {
            throw new IllegalStateException("app.cors.allowed-origins nao pode conter '*': informe os dominios autorizados");
        }
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        if (origins.isEmpty()) {
            return source;
        }
        CorsConfiguration cors = new CorsConfiguration();
        cors.setAllowedOrigins(origins);
        cors.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        cors.setAllowedHeaders(List.of("Authorization", "Content-Type", "Idempotency-Key", RequestIdFilter.HEADER));
        cors.setExposedHeaders(List.of(RequestIdFilter.HEADER, "Content-Disposition"));
        // A API usa apenas o cabecalho Authorization; cookies nao sao necessarios.
        cors.setAllowCredentials(false);
        cors.setMaxAge(Duration.ofHours(1));
        source.registerCorsConfiguration("/api/**", cors);
        return source;
    }
}
