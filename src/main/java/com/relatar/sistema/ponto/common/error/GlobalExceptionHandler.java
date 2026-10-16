package com.relatar.sistema.ponto.common.error;

import java.util.ArrayList;
import java.util.List;

import jakarta.servlet.http.HttpServletRequest;

import org.hibernate.exception.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Tratamento centralizado de erros da API. Todas as respostas seguem Problem Details
 * (ver {@link ProblemDetailFactory}). Detalhes tecnicos ficam apenas nos logs do servidor,
 * correlacionados pelo {@code requestId}.
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private final ProblemDetailFactory problems;

    public GlobalExceptionHandler(ProblemDetailFactory problems) {
        this.problems = problems;
    }

    @ExceptionHandler(DomainException.class)
    ResponseEntity<ProblemDetail> handleDomain(DomainException ex, HttpServletRequest request) {
        ProblemDetail problem = problems.create(ex.status(), ex.code(), ex.getMessage(), request);
        ex.properties().forEach(problem::setProperty);
        return ResponseEntity.status(ex.status()).body(problem);
    }

    @ExceptionHandler(AccessDeniedException.class)
    ResponseEntity<ProblemDetail> handleAccessDenied(AccessDeniedException ex, HttpServletRequest request) {
        return respond(HttpStatus.FORBIDDEN, "ACCESS_DENIED",
                "Você não tem permissão para executar esta operação.", request);
    }

    @ExceptionHandler(AuthenticationException.class)
    ResponseEntity<ProblemDetail> handleAuthentication(AuthenticationException ex, HttpServletRequest request) {
        return respond(HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED", "Autenticação necessária.", request);
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    ResponseEntity<ProblemDetail> handleOptimisticLock(OptimisticLockingFailureException ex,
            HttpServletRequest request) {
        return respond(HttpStatus.CONFLICT, "CONCURRENT_MODIFICATION",
                "O registro foi alterado por outra operação. Recarregue os dados e tente novamente.", request);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<ProblemDetail> handleDataIntegrity(DataIntegrityViolationException ex,
            HttpServletRequest request) {
        // Registra somente o nome da restricao: a mensagem do driver pode conter valores (dados pessoais).
        log.warn("Violacao de integridade de dados: constraint={}", constraintName(ex));
        return respond(HttpStatus.CONFLICT, "DATA_CONFLICT",
                "A operação conflita com dados já existentes.", request);
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ProblemDetail> handleUnexpected(Exception ex, HttpServletRequest request) {
        log.error("Erro inesperado ao processar {} {}", request.getMethod(), request.getRequestURI(), ex);
        return respond(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR",
                "Ocorreu um erro inesperado. Informe o código da requisição (requestId) ao suporte.", request);
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        List<FieldViolation> errors = new ArrayList<>();
        ex.getBindingResult().getFieldErrors()
                .forEach(error -> errors.add(new FieldViolation(error.getField(), error.getDefaultMessage())));
        ex.getBindingResult().getGlobalErrors()
                .forEach(error -> errors.add(new FieldViolation(error.getObjectName(), error.getDefaultMessage())));
        return validationProblem(errors, headers, status, request);
    }

    @Override
    protected ResponseEntity<Object> handleHandlerMethodValidationException(HandlerMethodValidationException ex,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        List<FieldViolation> errors = new ArrayList<>();
        ex.getParameterValidationResults().forEach(result -> {
            String parameter = result.getMethodParameter().getParameterName();
            for (MessageSourceResolvable error : result.getResolvableErrors()) {
                String field = error instanceof FieldError fieldError ? fieldError.getField() : parameter;
                errors.add(new FieldViolation(field, error.getDefaultMessage()));
            }
        });
        return validationProblem(errors, headers, status, request);
    }

    /** Padroniza tambem as respostas das excecoes do Spring MVC tratadas pela classe base. */
    @Override
    protected ResponseEntity<Object> createResponseEntity(Object body, HttpHeaders headers, HttpStatusCode statusCode,
            WebRequest request) {
        if (body instanceof ProblemDetail problem
                && (problem.getProperties() == null || !problem.getProperties().containsKey("code"))) {
            problems.enrich(problem, ProblemDetailFactory.defaultCode(statusCode), servletRequest(request));
        }
        return super.createResponseEntity(body, headers, statusCode, request);
    }

    private ResponseEntity<Object> validationProblem(List<FieldViolation> errors, HttpHeaders headers,
            HttpStatusCode status, WebRequest request) {
        ProblemDetail problem = problems.create(status, "VALIDATION_FAILED",
                "Um ou mais campos são inválidos.", servletRequest(request));
        problem.setProperty("errors", errors);
        return super.createResponseEntity(problem, headers, status, request);
    }

    private ResponseEntity<ProblemDetail> respond(HttpStatus status, String code, String detail,
            HttpServletRequest request) {
        return ResponseEntity.status(status).body(problems.create(status, code, detail, request));
    }

    private static HttpServletRequest servletRequest(WebRequest request) {
        return request instanceof NativeWebRequest nativeRequest
                ? nativeRequest.getNativeRequest(HttpServletRequest.class)
                : null;
    }

    private static String constraintName(Throwable ex) {
        for (Throwable cause = ex; cause != null; cause = cause.getCause()) {
            if (cause instanceof ConstraintViolationException violation) {
                return violation.getConstraintName();
            }
        }
        return "desconhecida";
    }

    /** Erro de validacao de um campo. O valor rejeitado nunca e devolvido (pode conter dados pessoais). */
    public record FieldViolation(String field, String message) {
    }
}
