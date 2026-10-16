package com.relatar.sistema.ponto.common.error;

import java.net.URI;
import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.stereotype.Component;

import com.relatar.sistema.ponto.common.web.RequestIdFilter;

/**
 * Monta respostas de erro no formato Problem Details (RFC 9457), acrescidas de:
 * {@code code} (codigo estavel para o frontend), {@code requestId} (correlacao com os logs)
 * e {@code timestamp}. Nunca inclui stack traces, SQL ou mensagens internas.
 */
@Component
public class ProblemDetailFactory {

    public static final String TYPE_PREFIX = "urn:relatar:ponto:problem:";

    private final Clock clock;

    public ProblemDetailFactory(Clock clock) {
        this.clock = clock;
    }

    public ProblemDetail create(HttpStatusCode status, String code, String detail, HttpServletRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        enrich(problem, code, request);
        return problem;
    }

    public void enrich(ProblemDetail problem, String code, HttpServletRequest request) {
        problem.setType(URI.create(TYPE_PREFIX + code.toLowerCase(Locale.ROOT).replace('_', '-')));
        if (problem.getTitle() == null) {
            HttpStatus status = HttpStatus.resolve(problem.getStatus());
            problem.setTitle(status != null ? status.getReasonPhrase() : "Error");
        }
        if (request != null) {
            problem.setInstance(safeUri(request.getRequestURI()));
            problem.setProperty("requestId", RequestIdFilter.current(request));
        }
        problem.setProperty("code", code);
        problem.setProperty("timestamp", clock.instant());
    }

    /** Representacao plana, para escrita fora do Spring MVC (ex.: filtros de seguranca). */
    public Map<String, Object> toMap(ProblemDetail problem) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("type", problem.getType());
        body.put("title", problem.getTitle());
        body.put("status", problem.getStatus());
        body.put("detail", problem.getDetail());
        body.put("instance", problem.getInstance());
        if (problem.getProperties() != null) {
            body.putAll(problem.getProperties());
        }
        return body;
    }

    public static String defaultCode(HttpStatusCode status) {
        return switch (status.value()) {
            case 400 -> "BAD_REQUEST";
            case 401 -> "UNAUTHENTICATED";
            case 403 -> "ACCESS_DENIED";
            case 404 -> "NOT_FOUND";
            case 405 -> "METHOD_NOT_ALLOWED";
            case 406 -> "NOT_ACCEPTABLE";
            case 409 -> "CONFLICT";
            case 413 -> "PAYLOAD_TOO_LARGE";
            case 415 -> "UNSUPPORTED_MEDIA_TYPE";
            case 422 -> "UNPROCESSABLE";
            case 429 -> "TOO_MANY_REQUESTS";
            default -> status.is5xxServerError() ? "INTERNAL_ERROR" : "HTTP_" + status.value();
        };
    }

    private static URI safeUri(String path) {
        try {
            return URI.create(path);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }
}
