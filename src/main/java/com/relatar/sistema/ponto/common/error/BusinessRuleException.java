package com.relatar.sistema.ponto.common.error;

import java.util.Map;

import org.springframework.http.HttpStatus;

/** Requisicao bem formada, mas que viola uma regra de negocio (ex.: sequencia de marcacao invalida). */
public class BusinessRuleException extends DomainException {

    public BusinessRuleException(String code, String message) {
        this(code, message, Map.of());
    }

    public BusinessRuleException(String code, String message, Map<String, Object> properties) {
        super(HttpStatus.UNPROCESSABLE_CONTENT, code, message, properties);
    }
}
