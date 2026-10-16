package com.relatar.sistema.ponto.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;

/**
 * Documentacao OpenAPI. A interface Swagger fica desabilitada por padrao em producao
 * (variavel {@code APP_API_DOCS_ENABLED}).
 */
@Configuration(proxyBeanMethods = false)
public class OpenApiConfig {

    static final String BEARER_SCHEME = "bearerAuth";

    @Bean
    OpenAPI pontoOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Relatar - Sistema de Controle de Ponto")
                        .version("v1")
                        .description("""
                                API do sistema corporativo de controle de ponto da Relatar.

                                **ATENÇÃO: sistema NÃO CERTIFICADO para uso oficial** como Registrador \
                                Eletrônico de Ponto (Portaria MTP nº 671/2021). Consulte docs/LEGAL_COMPLIANCE.md.

                                Erros seguem o formato Problem Details (RFC 9457), com os campos adicionais \
                                `code`, `requestId` e `timestamp`."""))
                .components(new Components().addSecuritySchemes(BEARER_SCHEME, new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT")))
                .addSecurityItem(new SecurityRequirement().addList(BEARER_SCHEME));
    }
}
