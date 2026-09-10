/**
 * The request header carrying the pathname from `proxy.ts` to the account
 * layout.
 *
 * A layout does not know which page it is wrapping, and the account rail has
 * to mark the current section. The proxy sets it before next-intl builds its
 * response — which copies the request headers into the one it forwards — and
 * `app/layout.tsx` reads it with `headers()`. That layout is dynamic anyway;
 * nothing in the marketing zone may read it, because `headers()` would make a
 * static page dynamic.
 *
 * In its own module so that the layout does not import `proxy.ts` — and with
 * it the session refresh and the i18n middleware — for one string.
 */
export const PATHNAME_HEADER = "x-lumo-pathname";
