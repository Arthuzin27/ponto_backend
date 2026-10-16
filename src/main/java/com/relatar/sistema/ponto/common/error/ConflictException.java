package com.relatar.sistema.ponto.common.error;

import java.util.Map;

import org.springframework.http.HttpStatus;

/** Conflito com o estado atual do recurso (duplicidade, alteracao concorrente, periodo fechado...). */
public class ConflictException extends DomainException {

    public ConflictException(String code, String message) {
        this(code, message, Map.of());
    }

    public ConflictException(String code, String message, Map<String, Object> properties) {
        super(HttpStatus.CONFLICT, code, message, properties);
    }
}
