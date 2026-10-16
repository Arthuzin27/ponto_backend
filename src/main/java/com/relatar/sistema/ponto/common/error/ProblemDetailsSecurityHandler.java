package com.relatar.sistema.ponto.common.error;

import java.io.IOException;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import tools.jackson.databind.json.JsonMapper;

/**
 * Respostas 401/403 geradas pela cadeia de filtros do Spring Security (antes de chegar aos controllers),
 * no mesmo formato Problem Details do restante da API.
 */
@Component
public class ProblemDetailsSecurityHandler implements AuthenticationEntryPoint, AccessDeniedHandler {

    private final ProblemDetailFactory problems;
    private final JsonMapper jsonMapper;

    public ProblemDetailsSecurityHandler(ProblemDetailFactory problems, JsonMapper jsonMapper) {
        this.problems = problems;
        this.jsonMapper = jsonMapper;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
            AuthenticationException authException) throws IOException {
        response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
        write(response, problems.create(HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED",
                "Autenticação necessária.", request));
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
            AccessDeniedException accessDeniedException) throws IOException {
        write(response, problems.create(HttpStatus.FORBIDDEN, "ACCESS_DENIED",
                "Você não tem permissão para executar esta operação.", request));
    }

    private void write(HttpServletResponse response, ProblemDetail problem) throws IOException {
        response.setStatus(problem.getStatus());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        jsonMapper.writeValue(response.getOutputStream(), problems.toMap(problem));
    }
}
