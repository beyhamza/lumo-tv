package tv.lumo.api.shared.error;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import java.util.List;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import tv.lumo.api.generated.model.ErrorCode;
import tv.lumo.api.generated.model.FieldError;
import tv.lumo.api.generated.model.Problem;

/**
 * Every error leaves this application through here, as
 * {@code application/problem+json}.
 *
 * <p>Spring's own RFC 7807 support is switched off in {@code application.yml}
 * precisely so this class answers first: the framework body carries no stable
 * {@code code} field, and {@code code} is the only thing clients branch on.
 *
 * <p><b>Logging rule.</b> A 5xx is logged with its stack trace, because nobody
 * else will diagnose it. A 4xx is logged at DEBUG and without the exception: a
 * 4xx is a client mistake rather than an incident, and the request that caused
 * it may carry a password or a stream URL, which must not reach a log line at
 * any level (AGENTS.md §5).
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private final ProblemFactory problems;

    public GlobalExceptionHandler(ProblemFactory problems) {
        this.problems = problems;
    }

    @ExceptionHandler(RateLimitedException.class)
    public ResponseEntity<Problem> handleRateLimited(RateLimitedException ex, HttpServletRequest request) {
        log.debug("Rate limit hit on {} {}", request.getMethod(), request.getRequestURI());
        return problem(ex.status(), ex.code(), ex.getMessage(), request, List.of())
                .header(HttpHeaders.RETRY_AFTER, Long.toString(ex.retryAfterSeconds()))
                .body(problems.create(ex.status().value(), ex.code(), ex.getMessage(),
                        request.getRequestURI(), List.of()));
    }

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<Problem> handleApi(ApiException ex, HttpServletRequest request) {
        log.debug("API error {} on {} {}", ex.code(), request.getMethod(), request.getRequestURI());
        return build(ex.status(), ex.code(), ex.getMessage(), request, ex.fieldErrors());
    }

    /** Bean-validation failure on a request body, raised by the generated {@code @Valid}. */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Problem> handleBodyValidation(MethodArgumentNotValidException ex,
                                                        HttpServletRequest request) {
        List<FieldError> fields = ex.getBindingResult().getFieldErrors().stream()
                .map(f -> {
                    FieldError fe = new FieldError("/" + f.getField(), constraintCode(f.getCode()));
                    // The rejected VALUE is deliberately not copied: for /password
                    // or /m3u_url it is exactly what must not be echoed back.
                    fe.setDetail(f.getDefaultMessage());
                    return fe;
                })
                .toList();
        return build(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_FAILED, "Request validation failed",
                request, fields);
    }

    /** Validation failure on a path variable or query parameter. */
    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<Problem> handleParameterValidation(HandlerMethodValidationException ex,
                                                             HttpServletRequest request) {
        return build(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_FAILED, "Request validation failed",
                request, List.of());
    }

    /**
     * The same failure, raised the other way.
     *
     * <p>The generated interfaces are {@code @Validated}, so a constraint on a
     * parameter — {@code @Size} on {@code channelIds}, on {@code ids}, on
     * {@code q} — is checked by the AOP method-validation interceptor and
     * surfaces as this exception, not as the MVC one above. Until this handler
     * existed, sending 101 identifiers answered {@code 500} with a stack trace
     * in the log, for a request the contract documents as {@code 400}.
     *
     * <p>Logged without the exception: its message quotes the violated value,
     * which for a body constraint could be a password (AGENTS.md §5).
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<Problem> handleConstraintViolation(ConstraintViolationException ex,
                                                             HttpServletRequest request) {
        log.debug("Constraint violation on {} {}", request.getMethod(), request.getRequestURI());
        return build(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_FAILED, "Request validation failed",
                request, List.of());
    }

    @ExceptionHandler({
            HttpMessageNotReadableException.class,
            MissingServletRequestParameterException.class,
            MethodArgumentTypeMismatchException.class
    })
    public ResponseEntity<Problem> handleMalformed(Exception ex, HttpServletRequest request) {
        // The exception message quotes the offending JSON, which on a login body
        // is the password. Only the exception type is logged.
        log.debug("Malformed request on {} {}: {}", request.getMethod(), request.getRequestURI(),
                ex.getClass().getSimpleName());
        return build(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_FAILED, "Malformed request",
                request, List.of());
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<Problem> handleNoResource(NoResourceFoundException ex, HttpServletRequest request) {
        return build(HttpStatus.NOT_FOUND, ErrorCode.NOT_FOUND, "No handler for this path",
                request, List.of());
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<Problem> handleMethod(HttpRequestMethodNotSupportedException ex,
                                                HttpServletRequest request) {
        return build(HttpStatus.METHOD_NOT_ALLOWED, ErrorCode.NOT_FOUND, "Method not supported",
                request, List.of());
    }

    /**
     * Anything unplanned. The client learns nothing beyond {@code INTERNAL_ERROR}:
     * an exception message from deep in the stack is as likely to contain a
     * connection string as it is to help.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Problem> handleUnexpected(Exception ex, HttpServletRequest request) {
        log.error("Unhandled exception on {} {}", request.getMethod(), request.getRequestURI(), ex);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, ErrorCode.INTERNAL_ERROR, null,
                request, List.of());
    }

    // ---- helpers ------------------------------------------------------------

    private ResponseEntity<Problem> build(HttpStatus status, ErrorCode code, String detail,
                                          HttpServletRequest request, List<FieldError> fields) {
        return problem(status, code, detail, request, fields)
                .body(problems.create(status.value(), code, detail, request.getRequestURI(), fields));
    }

    private ResponseEntity.BodyBuilder problem(HttpStatus status, ErrorCode code, String detail,
                                               HttpServletRequest request, List<FieldError> fields) {
        return ResponseEntity.status(status).contentType(MediaType.APPLICATION_PROBLEM_JSON);
    }

    private static String constraintCode(String springCode) {
        if (springCode == null) {
            return "INVALID";
        }
        return switch (springCode) {
            case "NotNull", "NotEmpty", "NotBlank" -> "REQUIRED";
            case "Size", "Length" -> "LENGTH";
            case "Pattern" -> "PATTERN";
            case "Email" -> "FORMAT";
            case "Min", "Max", "Positive", "PositiveOrZero" -> "RANGE";
            default -> springCode.toUpperCase(Locale.ROOT);
        };
    }
}
