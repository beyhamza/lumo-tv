package tv.lumo.api.shared.web;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Resolves the caller's IP address for rate-limiting keys.
 *
 * <p>{@code X-Forwarded-For} is honoured because the API sits behind a reverse
 * proxy in every deployed environment. <b>It is client-controlled</b>: anyone can
 * send an arbitrary value, so this is a rate-limit key and nothing more. It must
 * never be used for authorisation, allow-listing or audit.
 *
 * <p>The left-most entry is taken, which is the convention proxies follow when
 * appending. Trusting a specific proxy hop instead would mean knowing the
 * infrastructure topology here, which this layer has no business knowing.
 */
public final class ClientIp {

    private ClientIp() {
    }

    public static String of(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            int comma = forwarded.indexOf(',');
            String first = (comma > 0 ? forwarded.substring(0, comma) : forwarded).trim();
            if (!first.isEmpty()) {
                // Bounded: the header is attacker-controlled, and an unbounded
                // value would become an unbounded rate-limit cache key.
                return first.length() > 45 ? first.substring(0, 45) : first;
            }
        }
        String remote = request.getRemoteAddr();
        return remote == null ? "unknown" : remote;
    }
}
