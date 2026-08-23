package tv.lumo.api.shared.error;

import org.springframework.http.HttpStatus;
import tv.lumo.api.generated.model.ErrorCode;

/**
 * Rate limit exceeded. Carries the {@code Retry-After} value the client should
 * honour, which the handler copies into the response header.
 */
public class RateLimitedException extends ApiException {

    private final long retryAfterSeconds;

    public RateLimitedException(ErrorCode code, long retryAfterSeconds, String detail) {
        super(HttpStatus.TOO_MANY_REQUESTS, code, detail);
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public long retryAfterSeconds() {
        return retryAfterSeconds;
    }
}
