package com.challenge.order_processing_api.exception;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger logger = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    private static final Pattern ORDER_FIELD = Pattern.compile(
            "(customerName|customerEmail|items|items\\[\\d+\\]\\.(description|quantity|unitPrice))");
    private static final Set<String> QUERY_FIELDS = Set.of(
            "status", "createdFrom", "createdTo", "page", "size", "sort");

    @ExceptionHandler(OrderNotFoundException.class)
    public ResponseEntity<ProblemDetail> handleNotFound(
            OrderNotFoundException exception,
            HttpServletRequest request) {
        return response(HttpStatus.NOT_FOUND, exception.getMessage(), request, List.of());
    }

    @ExceptionHandler(ClientInputException.class)
    public ResponseEntity<ProblemDetail> handleClientInput(
            ClientInputException exception,
            HttpServletRequest request) {
        return response(HttpStatus.BAD_REQUEST, exception.getMessage(), request, List.of());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ProblemDetail> handleValidation(
            MethodArgumentNotValidException exception,
            HttpServletRequest request) {
        List<FieldViolation> violations = exception.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(this::toViolation)
                .toList();
        return response(HttpStatus.BAD_REQUEST, "Um ou mais campos são inválidos.", request, violations);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ProblemDetail> handleConstraintViolation(
            ConstraintViolationException exception,
            HttpServletRequest request) {
        List<FieldViolation> violations = exception.getConstraintViolations()
                .stream()
                .map(this::toViolation)
                .toList();
        return response(HttpStatus.BAD_REQUEST, "Um ou mais parâmetros são inválidos.", request, violations);
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class})
    public ResponseEntity<ProblemDetail> handleMalformedRequest(
            Exception exception,
            HttpServletRequest request) {
        return response(
                HttpStatus.BAD_REQUEST,
                "A requisição contém valores inválidos.",
                request,
                List.of());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetail> handleUnexpected(
            Exception exception,
            HttpServletRequest request) {
        if (exception instanceof ErrorResponse frameworkError) {
            HttpStatusCode status = frameworkError.getStatusCode();
            if (status.is5xxServerError()) {
                String errorId = UUID.randomUUID().toString();
                logger.error("Erro HTTP interno {} ao processar requisição para {}.",
                        errorId, request.getRequestURI(), exception);
                return response(
                        status,
                        "A requisição não pôde ser processada.",
                        request,
                        List.of(),
                        errorId);
            }
            return response(
                    status,
                    "A requisição não pôde ser processada.",
                    request,
                    List.of());
        }
        String errorId = UUID.randomUUID().toString();
        logger.error("Erro inesperado {} ao processar requisição para {}.",
                errorId, request.getRequestURI(), exception);
        return response(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "Ocorreu um erro interno ao processar a requisição.",
                request,
                List.of(),
                errorId);
    }

    private FieldViolation toViolation(FieldError error) {
        return new FieldViolation(
                safeOrderField(error.getField()),
                safeValidationMessage(error.getCode()));
    }

    private FieldViolation toViolation(ConstraintViolation<?> violation) {
        String path = violation.getPropertyPath().toString();
        String field = QUERY_FIELDS.contains(path) ? path : "parâmetro";
        String constraint = violation.getConstraintDescriptor()
                .getAnnotation()
                .annotationType()
                .getSimpleName();
        return new FieldViolation(field, safeValidationMessage(constraint));
    }

    private String safeOrderField(String field) {
        return ORDER_FIELD.matcher(field).matches() ? field : "campo";
    }

    private String safeValidationMessage(String code) {
        if (code == null) {
            return "Valor inválido.";
        }
        return switch (code) {
            case "NotBlank", "NotEmpty", "NotNull" -> "É obrigatório.";
            case "Email" -> "Deve ser um e-mail válido.";
            case "Size" -> "Tamanho fora do limite permitido.";
            case "Positive" -> "Deve ser maior que zero.";
            case "Min", "DecimalMin" -> "Não pode ser negativo.";
            case "Max" -> "Excede o limite permitido.";
            case "Digits" -> "Formato numérico inválido.";
            case "Pattern" -> "Contém caracteres não permitidos.";
            default -> "Valor inválido.";
        };
    }

    private ResponseEntity<ProblemDetail> response(
            HttpStatusCode status,
            String message,
            HttpServletRequest request,
            List<FieldViolation> violations) {
        return response(status, message, request, violations, UUID.randomUUID().toString());
    }

    private ResponseEntity<ProblemDetail> response(
            HttpStatusCode status,
            String message,
            HttpServletRequest request,
            List<FieldViolation> violations,
            String errorId) {
        ProblemDetail problem = responseBody(
                status,
                resolveTitle(status),
                message,
                request,
                violations,
                errorId);
        return ResponseEntity.status(status)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .header("X-Error-Id", errorId)
                .body(problem);
    }

    private ProblemDetail responseBody(
            HttpStatusCode status,
            String title,
            String message,
            HttpServletRequest request,
            List<FieldViolation> violations,
            String errorId) {
        ProblemDetail problem = ProblemDetail.forStatus(status);
        problem.setTitle(title == null ? resolveTitle(status) : title);
        problem.setDetail(message == null ? "A requisição não pôde ser processada." : message);
        problem.setInstance(URI.create(request.getRequestURI()));
        problem.setProperty("timestamp", Instant.now());
        problem.setProperty("errorId", errorId);
        if (!violations.isEmpty()) {
            problem.setProperty("fieldErrors", violations);
        }
        return problem;
    }

    private String resolveTitle(HttpStatusCode status) {
        HttpStatus resolved = HttpStatus.resolve(status.value());
        return resolved == null ? "Erro HTTP" : resolved.getReasonPhrase();
    }

    public record FieldViolation(String field, String message) {
    }
}
