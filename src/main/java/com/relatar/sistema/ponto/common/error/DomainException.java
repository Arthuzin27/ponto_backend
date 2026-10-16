package com.relatar.sistema.ponto.common.error;

import java.util.Map;

import org.springframework.http.HttpStatus;

/**
 * Excecao base para violacoes de regra de negocio. A mensagem e exibida ao cliente,
 * portanto deve ser escrita para o usuario e nunca conter dados internos ou pessoais.
 * O {@code code} e estavel e pode ser usado pelo frontend para tratamento especifico.
 */
public abstract class DomainException extends RuntimeException {

    private final HttpStatus status;
    private final String code;
    private final transient Map<String, Object> properties;

    protected DomainException(HttpStatus status, String code, String message, Map<String, Object> properties) {
        super(message);
        this.status = status;
        this.code = code;
        this.properties = Map.copyOf(properties);
    }

    public HttpStatus status() {
        return status;
    }

    public String code() {
        return code;
    }

    /** Informacoes adicionais seguras para o cliente (ex.: proximos tipos de marcacao permitidos). */
    public Map<String, Object> properties() {
        return properties;
    }
}
