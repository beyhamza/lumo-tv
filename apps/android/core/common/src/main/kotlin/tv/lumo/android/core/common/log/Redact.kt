package tv.lumo.android.core.common.log

/**
 * Redaction helpers for anything that reaches a log.
 *
 * AGENTS.md §5 is absolute: no stream URL, no Xtream password, no token in the
 * logs, at any level, `DEBUG` included. On Android that rule has teeth — logcat
 * is readable by `adb` from any machine the device is plugged into, and crash
 * reporters upload what they find.
 *
 * The awkward part is that stream URLs are exactly what you want to see when a
 * channel will not play. So the compromise is here: enough of the URL to tell
 * one failure from another, never enough to replay it. A URL that reaches a log
 * through [url] leaks neither host nor path nor the credentials Xtream panels
 * put in the query string.
 */
object Redact {

    /**
     * `https://panel.example.com:8080/live/user/pass/123.ts` becomes
     * `https://<host>/…(3 segments)`.
     */
    fun url(value: String?): String {
        if (value.isNullOrBlank()) return "<none>"
        val scheme = value.substringBefore("://", missingDelimiterValue = "")
        val rest = if (scheme.isEmpty()) value else value.substringAfter("://")
        val path = rest.substringAfter('/', missingDelimiterValue = "")
        val segments = path.substringBefore('?').split('/').count { it.isNotEmpty() }
        val prefix = if (scheme.isEmpty()) "" else "$scheme://"
        return "$prefix<host>/…($segments segments)"
    }

    /** Any secret: token, password, device code. Length only, never content. */
    fun secret(value: String?): String =
        if (value.isNullOrEmpty()) "<none>" else "<redacted:${value.length}>"

    /**
     * An email, for the rare log line that has to identify an account.
     * `someone@example.com` becomes `s…e@example.com`.
     */
    fun email(value: String?): String {
        if (value.isNullOrBlank()) return "<none>"
        val local = value.substringBefore('@')
        val domain = value.substringAfter('@', missingDelimiterValue = "")
        if (domain.isEmpty() || local.isEmpty()) return "<redacted>"
        val masked = if (local.length <= 2) "…" else "${local.first()}…${local.last()}"
        return "$masked@$domain"
    }
}
