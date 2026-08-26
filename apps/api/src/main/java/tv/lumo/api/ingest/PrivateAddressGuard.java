package tv.lumo.api.ingest;

import java.net.InetAddress;
import java.net.UnknownHostException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tv.lumo.api.generated.model.IngestionErrorCode;

/**
 * Refuses to fetch anything that resolves inside our own network.
 *
 * <h2>What this prevents</h2>
 *
 * A source is a URL supplied by a user, and this server fetches it with this
 * server's network position. Without this check, {@code POST /sources} is a
 * request forgery primitive: point a source at {@code http://127.0.0.1:8080},
 * at {@code http://169.254.169.254/latest/meta-data/} on a cloud instance, or at
 * a {@code 10.x} address, and the server fetches it and reports back — as a
 * channel count, as a distinctive error code, or simply as how long it took.
 * That is enough to map an internal network from the outside.
 *
 * <h2>The check is on the resolved address, never on the name</h2>
 *
 * Refusing the string {@code "localhost"} would be theatre. A hostname can
 * resolve wherever its owner points it, and domains that resolve to
 * {@code 127.0.0.1} are not hypothetical — the one that prompted this class
 * resolves there today, on an ordinary residential connection, because the
 * resolver blackholes it. So the name is resolved first and every address it
 * yields is checked.
 *
 * <h2>What it does not prevent</h2>
 *
 * DNS rebinding. Between this check and the connection, a hostile name server
 * can answer differently, and the HTTP client resolves again when it connects.
 * Closing that needs pinning the address we validated and dialling it directly,
 * which means taking over connection establishment. This class is the first
 * line, not the last one, and saying so is better than implying a guarantee it
 * does not give.
 */
public final class PrivateAddressGuard {

    private static final Logger log = LoggerFactory.getLogger(PrivateAddressGuard.class);

    private PrivateAddressGuard() {
    }


    /**
     * @param host hostname or literal address, already lower-cased by {@link SourceUrl#hostOf}
     * @throws IngestionException {@code SOURCE_UNREACHABLE} when the host does not
     *         resolve, or resolves anywhere we refuse to go
     */
    public static void requirePublic(String host, boolean allowPrivate) {
        if (allowPrivate) {
            // Tests only, and the property that opens it is documented as such.
            // A deployment that sets it has turned POST /sources into a way to
            // scan its own network.
            return;
        }
        InetAddress[] addresses;
        try {
            addresses = InetAddress.getAllByName(host);
        } catch (UnknownHostException e) {
            // Same code as a host that does not answer, and the same sentence to
            // the user: from where they stand, a name that resolves to nothing
            // and a server that is switched off are one situation.
            throw new IngestionException(IngestionErrorCode.SOURCE_UNREACHABLE,
                    "The host name does not resolve");
        }

        for (InetAddress address : addresses) {
            if (isForbidden(address)) {
                // The host, never the URL (AGENTS.md §5). The address is worth
                // logging: without it, "unreachable" on a host that resolves
                // perfectly well is the most confusing line in this file's life.
                log.info("Refusing source host {}: it resolves to {}, which is not a public address",
                        host, address.getHostAddress());
                throw new IngestionException(IngestionErrorCode.SOURCE_UNREACHABLE,
                        "The host resolves to an address this server will not fetch");
            }
        }
    }

    /**
     * Every address family we refuse, and each one is a different way in.
     *
     * <p>{@code isSiteLocalAddress} covers 10/8, 172.16/12 and 192.168/16 but not
     * IPv6 unique-local, so that one is matched by prefix. 100.64/10 — carrier
     * NAT, and the address range a container platform is most likely to hand a
     * neighbouring service — has no JDK predicate at all.
     */
    private static boolean isForbidden(InetAddress address) {
        if (address.isLoopbackAddress()
                || address.isAnyLocalAddress()
                || address.isLinkLocalAddress()
                || address.isSiteLocalAddress()
                || address.isMulticastAddress()) {
            return true;
        }

        byte[] bytes = address.getAddress();
        if (bytes.length == 4) {
            int first = bytes[0] & 0xFF;
            int second = bytes[1] & 0xFF;
            // 100.64.0.0/10, carrier-grade NAT.
            return first == 100 && second >= 64 && second <= 127;
        }
        // fc00::/7, IPv6 unique local.
        return (bytes[0] & 0xFE) == 0xFC;
    }
}
