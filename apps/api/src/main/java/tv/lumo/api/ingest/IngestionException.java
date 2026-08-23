package tv.lumo.api.ingest;

import java.io.IOException;
import java.net.UnknownHostException;
import java.net.http.HttpTimeoutException;
import tv.lumo.api.generated.model.IngestionErrorCode;

/**
 * An ingestion failure, carrying the stable code the client turns into an
 * actionable message.
 *
 * <p>This is the number-one friction point of onboarding. "Something went wrong"
 * loses users here; "your credentials were refused by the server" recovers them
 * (docs/domain-model.md §3). Every throw site therefore has to pick a code that
 * genuinely distinguishes the case — in particular SOURCE_UNREACHABLE and
 * SOURCE_AUTH_FAILED must never be conflated, because the user's next action
 * differs completely between the two.
 *
 * <p>The message is for logs. It never contains a URL, a username or a password.
 */
public class IngestionException extends RuntimeException {

    private final IngestionErrorCode code;

    public IngestionException(IngestionErrorCode code, String detail) {
        super(detail);
        this.code = code;
    }

    public IngestionException(IngestionErrorCode code, String detail, Throwable cause) {
        super(detail, cause);
        this.code = code;
    }

    public IngestionErrorCode code() {
        return code;
    }

    /** Maps a transport failure onto the closest honest code. */
    public static IngestionException from(Throwable e) {
        if (e instanceof IngestionException ingestion) {
            return ingestion;
        }
        if (e instanceof HttpTimeoutException) {
            return new IngestionException(IngestionErrorCode.SOURCE_UNREACHABLE,
                    "The server did not answer in time", e);
        }
        if (e instanceof UnknownHostException) {
            return new IngestionException(IngestionErrorCode.SOURCE_UNREACHABLE,
                    "The host name could not be resolved", e);
        }
        if (e instanceof IOException) {
            return new IngestionException(IngestionErrorCode.SOURCE_UNREACHABLE,
                    "The server could not be reached", e);
        }
        return new IngestionException(IngestionErrorCode.SOURCE_INVALID_FORMAT,
                "The response could not be understood", e);
    }
}
