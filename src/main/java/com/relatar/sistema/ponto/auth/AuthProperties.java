package com.relatar.sistema.ponto.auth;

import java.time.Duration;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Configuracoes de autenticacao (prefixo {@code app.auth}).
 *
 * @param jwtSecret      segredo HMAC dos tokens; minimo de 32 caracteres (256 bits)
 * @param tokenTtl       validade do token de acesso
 * @param issuer         emissor gravado e exigido nos tokens
 * @param bootstrapAdmin administrador criado na primeira inicializacao, quando nao ha nenhum usuario
 * @param emailDomain    unico dominio de e-mail aceito no cadastro de usuarios (e-mail corporativo)
 */
@Validated
@ConfigurationProperties("app.auth")
public record AuthProperties(
        @NotBlank @Size(min = 32, message = "APP_JWT_SECRET deve ter ao menos 32 caracteres") String jwtSecret,
        @NotNull Duration tokenTtl,
        @NotBlank String issuer,
        @Valid @NotNull BootstrapAdmin bootstrapAdmin,
        @NotBlank String emailDomain) {

    public record BootstrapAdmin(String nome, String email, String senha) {

        public boolean configurado() {
            return email != null && !email.isBlank() && senha != null && !senha.isBlank();
        }
    }
}
