package tv.lumo.api.shared.error;

import java.util.List;
import org.springframework.http.HttpStatus;
import tv.lumo.api.generated.model.ErrorCode;
import tv.lumo.api.generated.model.FieldError;

/**
 * The single exception type this application throws for anything a client is
 * meant to see.
 *
 * <p>It carries the stable {@link ErrorCode} from the contract, which is the only
 * field clients are allowed to branch on. {@code detail} is written for logs and
 * support and is never rendered raw in a UI — so it must never contain a stream
 * URL, an Xtream password or a token (AGENTS.md §5).
 */
public class ApiException extends RuntimeException {

    private final HttpStatus status;
    private final ErrorCode code;
    private final transient List<FieldError> fieldErrors;

    public ApiException(HttpStatus status, ErrorCode code, String detail) {
        this(status, code, detail, List.of());
    }

    public ApiException(HttpStatus status, ErrorCode code, String detail, List<FieldError> fieldErrors) {
        super(detail);
        this.status = status;
        this.code = code;
        this.fieldErrors = List.copyOf(fieldErrors);
    }

    public HttpStatus status() {
        return status;
    }

    public ErrorCode code() {
        return code;
    }

    public List<FieldError> fieldErrors() {
        return fieldErrors;
    }

    // ---- Factories for the cases raised in more than one place --------------

    public static ApiException notFound(ErrorCode code, String detail) {
        return new ApiException(HttpStatus.NOT_FOUND, code, detail);
    }

    public static ApiException conflict(ErrorCode code, String detail) {
        return new ApiException(HttpStatus.CONFLICT, code, detail);
    }

    public static ApiException unauthenticated(ErrorCode code, String detail) {
        return new ApiException(HttpStatus.UNAUTHORIZED, code, detail);
    }

    public static ApiException validation(String detail, List<FieldError> fieldErrors) {
        return new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_FAILED, detail, fieldErrors);
    }

    public static ApiException unprocessable(ErrorCode code, String detail) {
        return new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, code, detail);
    }
}
