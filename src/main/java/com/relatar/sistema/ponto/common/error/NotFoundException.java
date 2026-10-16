package com.relatar.sistema.ponto.common.error;

import java.util.Map;

import org.springframework.http.HttpStatus;

/** Recurso inexistente ou que o usuario nao pode ver (nao revela qual dos dois, para evitar enumeracao). */
public class NotFoundException extends DomainException {

    public NotFoundException(String code, String message) {
        super(HttpStatus.NOT_FOUND, code, message, Map.of());
    }
}
