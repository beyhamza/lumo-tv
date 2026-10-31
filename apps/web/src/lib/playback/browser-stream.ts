/**
 * The address this page can open, or null when it can open neither.
 *
 * A page served over `https:` cannot load an `http:` stream, and the browser does
 * not report that usefully (ADR 0007). So an `http:` stream on such a page gives
 * way to the `https` address its panel announces, when there is one. Only then:
 * everywhere else `url` is what plays, because the announcement is unverified and
 * the plain address is the one known to work.
 *
 * `upgraded` tells the player which address failed, if one does: a refusal of the
 * announced address says nothing about the stream itself.
 */
export function browserStream(
  payload: { url: string; secureUrl?: string | null },
  pageProtocol: string,
): { url: string; upgraded: boolean } | null {
  if (pageProtocol !== "https:" || !payload.url.startsWith("http:")) {
    return { url: payload.url, upgraded: false };
  }
  return payload.secureUrl ? { url: payload.secureUrl, upgraded: true } : null;
}
