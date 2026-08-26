package tv.lumo.api.ingest;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The definition badge a channel carries, as its own source advertises it.
 *
 * <p>The contract types {@code Channel.quality} as a free string and says why:
 * sources write {@code HD}, {@code FHD}, {@code UHD}, {@code 4K}, {@code H265},
 * and an enumeration would force this layer to file the unrecognised under some
 * value — which is to say to lie about it. So nothing here maps or normalises.
 * What it finds, it <b>echoes verbatim</b>: a playlist that wrote {@code fhd} in
 * lower case gets {@code fhd} back.
 *
 * <p>Two places to look, in order:
 *
 * <ol>
 *   <li>An explicit attribute, when the playlist carries one. Rare, and
 *       authoritative when present.</li>
 *   <li>The display name, which is where nearly every real playlist puts it —
 *       {@code "TF1 FHD"}, {@code "Canal+ Sport 4K"}. Without this second pass
 *       the property would be null for essentially every source and the badge
 *       would never render.</li>
 * </ol>
 *
 * <p><b>The name is not modified.</b> The token is read out of it, not taken out
 * of it: {@code "TF1 FHD"} stays {@code "TF1 FHD"}, because that is the string
 * the user sees in every other player they own, and a channel list that renames
 * their channels is a channel list they do not recognise.
 */
public final class ChannelQuality {

    /**
     * Tokens recognised as a definition, longest-first within a position so that
     * {@code FHD} is never read as an {@code HD} that happens to follow an F.
     *
     * <p>Bounded by a non-alphanumeric on both sides, which is what keeps
     * {@code "Discovery HD"} a match and {@code "SHDTV"} not one. Deliberately
     * short: every entry here is a string sources actually write, and a
     * speculative one costs a wrong badge on somebody's channel.
     */
    private static final Pattern TOKEN = Pattern.compile(
            "(?<![A-Za-z0-9])(8K|4K|UHD|FHD|FULL\\s?HD|HDR|HEVC|H\\.?265|H\\.?264|HD|SD)(?![A-Za-z0-9])",
            Pattern.CASE_INSENSITIVE);

    /** The contract caps the property at 20 characters. */
    private static final int MAX_LENGTH = 20;

    private ChannelQuality() {
    }

    /**
     * @param declared attribute value from the playlist, when it carries one
     * @param name     the channel's display name, searched only if {@code declared}
     *                 is absent
     * @return the token as written, or null when the source advertises none
     */
    public static String detect(String declared, String name) {
        if (declared != null && !declared.isBlank()) {
            String trimmed = declared.trim();
            return trimmed.length() > MAX_LENGTH ? trimmed.substring(0, MAX_LENGTH) : trimmed;
        }
        return fromName(name);
    }

    /**
     * The <b>last</b> match in the name wins.
     *
     * <p>Qualifiers trail: {@code "Sport HD 4K"} is a 4K feed of a channel whose
     * name contains HD, and reading it as HD would downgrade it. Leftmost would
     * get that backwards.
     */
    private static String fromName(String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        Matcher matcher = TOKEN.matcher(name);
        String found = null;
        while (matcher.find()) {
            found = matcher.group(1);
        }
        return found;
    }

    /**
     * The channel number a provider assigns, when it is one.
     *
     * <p>Playlists write it as text and write anything in it: an empty attribute,
     * {@code "N/A"}, a decimal, a number with spaces. None of those is a channel
     * number, and none of them is an error either — the field is optional, so
     * anything unreadable is simply absent.
     *
     * @return the number, or null when there is none to read
     */
    public static Integer parseNumber(String raw) {
        if (raw == null) {
            return null;
        }
        String trimmed = raw.trim();
        if (trimmed.isEmpty() || trimmed.length() > 6) {
            return null;
        }
        try {
            int number = Integer.parseInt(trimmed);
            // Zero and negatives are not numbers anyone dials.
            return number > 0 ? number : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
