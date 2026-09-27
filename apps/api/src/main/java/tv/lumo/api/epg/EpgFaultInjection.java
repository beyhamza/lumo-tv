package tv.lumo.api.epg;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import tv.lumo.api.generated.model.ErrorCode;
import tv.lumo.api.shared.error.ApiException;

/**
 * Dev-only failure injected into the guide reads (I-5, S9-07-03).
 *
 * <h2>What it is for</h2>
 *
 * GD-10's second half has to be played on the web: a grid that has already been
 * loaded, then an API that fails — the grid and its focus must stay, with a
 * distinct error rather than a blank screen or "no programme". The web renders
 * that guide on the Next.js server, so the browser's own fault injection
 * (Playwright's {@code page.clock}, a route interception) cannot reach it. The
 * only way to produce the failure is to ask the API for it.
 *
 * <h2>How it is armed</h2>
 *
 * {@code LUMO_EPG_FAULT=503} in the API's environment. Unset means {@code 0},
 * which means off, which is what ships — like {@code allow-private-hosts}, this
 * is a relaxation that exists for qualification and defaults to closed. The
 * value is the status to serve, and it is validated at start-up rather than at
 * the first request: a value that is not {@code 0} or a 5xx is a configuration
 * mistake that should stop the container, not a surprise body later.
 *
 * <h2>What it does not do</h2>
 *
 * It fails the two guide reads and nothing else — the catalogue, the source
 * list and playback answer normally, which is the point: a fault that took the
 * whole API down would not reproduce "the guide is refused while the rest of
 * the screen is fine". It is deliberately not a Spring profile: a profile can be
 * activated by accident by a deployment that copies the wrong environment, and
 * an environment variable that is never set does nothing.
 */
@Component
public class EpgFaultInjection {

    /** Off. The value the product runs with, and the only non-error one. */
    private static final int OFF = 0;

    private final int status;

    public EpgFaultInjection(@Value("${LUMO_EPG_FAULT:0}") int status) {
        if (status != OFF) {
            HttpStatus resolved = HttpStatus.resolve(status);
            if (resolved == null || !resolved.is5xxServerError()) {
                throw new IllegalStateException(
                        "LUMO_EPG_FAULT must be 0 (off) or a 5xx status; got " + status);
            }
        }
        this.status = status;
    }

    /**
     * Fails the guide read when the fault is armed, and does nothing otherwise.
     *
     * <p>Called first in each guide handler, before any work: an injected fault
     * that ran after a query would still fill a connection pool and a log line
     * for a request that was never going to answer.
     */
    public void check() {
        if (status == OFF) {
            return;
        }
        HttpStatus resolved = HttpStatus.resolve(status);
        // The status is the injected one; the code is the generic server-side
        // one, because the failure is ours and not the provider's. Clients key
        // on the status, and the web's loader reads "not 200" as unavailable.
        throw new ApiException(resolved, ErrorCode.INTERNAL_ERROR,
                "Injected guide failure (LUMO_EPG_FAULT=" + status + ")");
    }
}
