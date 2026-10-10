package tv.lumo.api.shared.web;

import jakarta.servlet.http.HttpServletRequest;
import java.net.InetAddress;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import tv.lumo.api.shared.config.LumoProperties;

/**
 * Resolves the caller's IP address for rate-limiting keys.
 *
 * <p>{@code X-Forwarded-For} is client-controlled: anyone can send any value. It
 * used to be read on every request, left-most entry first, and that turned the
 * sign-in limit into a suggestion — a new header value per attempt was a new
 * bucket per attempt (S10B-02). It is now read <b>only when the socket peer is
 * one of {@code lumo.rate-limit.trusted-proxies}</b>, and walked from the right:
 * the right-most entry that is not itself a trusted proxy is the one our own
 * proxy appended, the only one nobody outside could have written.
 *
 * <p>With no trusted proxy configured — the default — the header is ignored and
 * the socket peer is the client. A deployment behind a proxy that forgets to
 * declare it rate-limits on the proxy's address: too strict, never bypassable.
 *
 * <p>Still a rate-limit key and nothing more. Never authorisation, allow-listing
 * or audit.
 */
@Component
public class ClientIp {

    /** Long enough for any IPv6 literal; an attacker's header must not become an unbounded cache key. */
    private static final int MAX_KEY_LENGTH = 45;

    private final List<Cidr> trustedProxies;

    @Autowired
    public ClientIp(LumoProperties properties) {
        this(properties.rateLimit().trustedProxies());
    }

    public ClientIp(List<String> trustedProxies) {
        this.trustedProxies = trustedProxies == null
                ? List.of()
                : trustedProxies.stream().map(String::trim).filter(s -> !s.isEmpty()).map(Cidr::parse).toList();
    }

    public String of(HttpServletRequest request) {
        String remote = request.getRemoteAddr();
        if (remote == null) {
            return "unknown";
        }
        if (!isTrusted(remote)) {
            return remote;
        }
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded == null || forwarded.isBlank()) {
            return remote;
        }
        String[] hops = forwarded.split(",");
        for (int i = hops.length - 1; i >= 0; i--) {
            String hop = hops[i].trim();
            if (!hop.isEmpty() && !isTrusted(hop)) {
                return hop.length() > MAX_KEY_LENGTH ? hop.substring(0, MAX_KEY_LENGTH) : hop;
            }
        }
        // Every hop is one of ours: the request started inside the proxy layer.
        return remote;
    }

    private boolean isTrusted(String address) {
        if (trustedProxies.isEmpty()) {
            return false;
        }
        InetAddress parsed = literal(address);
        return parsed != null && trustedProxies.stream().anyMatch(cidr -> cidr.contains(parsed));
    }

    /**
     * An IP literal, or {@code null}. Never a DNS lookup: the value may come
     * from a header, and resolving attacker input is its own vulnerability.
     */
    private static InetAddress literal(String address) {
        try {
            return InetAddress.ofLiteral(address);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /** One trusted block; a bare address is a block of its own full length. */
    private record Cidr(byte[] network, int prefix) {

        static Cidr parse(String value) {
            int slash = value.indexOf('/');
            InetAddress address = literal(slash < 0 ? value : value.substring(0, slash));
            if (address == null) {
                throw new IllegalArgumentException(
                        "lumo.rate-limit.trusted-proxies: not an IP literal or CIDR block: " + value);
            }
            byte[] bytes = address.getAddress();
            int prefix = slash < 0 ? bytes.length * 8 : Integer.parseInt(value.substring(slash + 1));
            if (prefix < 1 || prefix > bytes.length * 8) {
                // /0 trusts the whole internet, which is the bypass this class exists to close.
                throw new IllegalArgumentException(
                        "lumo.rate-limit.trusted-proxies: prefix out of range in " + value);
            }
            return new Cidr(bytes, prefix);
        }

        boolean contains(InetAddress address) {
            byte[] candidate = address.getAddress();
            if (candidate.length != network.length) {
                return false;
            }
            int full = prefix / 8;
            for (int i = 0; i < full; i++) {
                if (candidate[i] != network[i]) {
                    return false;
                }
            }
            int rest = prefix % 8;
            if (rest == 0) {
                return true;
            }
            int mask = (0xFF << (8 - rest)) & 0xFF;
            return (candidate[full] & mask) == (network[full] & mask);
        }
    }
}
