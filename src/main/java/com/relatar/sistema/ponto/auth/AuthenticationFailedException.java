package com.relatar.sistema.ponto.auth;

import java.util.Map;

import org.springframework.http.HttpStatus;

import com.relatar.sistema.ponto.common.error.DomainException;

/** Falha de autenticacao (401). A mensagem nunca revela se o e-mail existe. */
public class AuthenticationFailedException extends DomainException {

    private AuthenticationFailedException(String code, String message) {
        super(HttpStatus.UNAUTHORIZED, code, message, Map.of());
    }

    public static AuthenticationFailedException credenciaisInvalidas() {
        return new AuthenticationFailedException("INVALID_CREDENTIALS", "E-mail ou senha inválidos.");
    }

    /** Token valido, mas o usuario foi desativado ou removido depois da emissao. */
    public static AuthenticationFailedException sessaoInvalida() {
        return new AuthenticationFailedException("SESSION_INVALID", "Sua sessão não é mais válida. Entre novamente.");
    }
}
