import "server-only";

import { problemCode } from "./client";

/**
 * The three states an account page can actually be in.
 *
 * Two of them used to be one. `/me/devices` and `/me/entitlement` are in the
 * contract but no controller implements them yet — sprint 1 covers auth,
 * sources and catalogue — so those pages called an endpoint that 404s and
 * rendered "the service is not responding". A feature nobody has built was
 * being reported as an outage: it tells the user to come back in a minute,
 * which is false, and it hides from us that the endpoint is missing.
 */
export type Fetched<T> =
  | { state: "ok"; data: T }
  | { state: "not-implemented" }
  | { state: "unavailable" };

type ApiCall<T> = () => Promise<{
  data?: T;
  error?: unknown;
  response: Response;
}>;

/**
 * Runs an API call and classifies the outcome.
 *
 * The discriminator between "not built" and "broken" is a **404 carrying the
 * generic `NOT_FOUND` code**. Every error the API produces carries a code — the
 * contract guarantees it, and the router-level handler is no exception — but it
 * is the only thing that answers with the generic one: a resource that does not
 * exist on an endpoint that does answers `SOURCE_NOT_FOUND`,
 * `CHANNEL_NOT_FOUND`, `DEVICE_NOT_FOUND`. No controller emits the bare
 * `NOT_FOUND`.
 *
 * That was worth checking rather than assuming: the first version of this rule
 * keyed on a 404 with *no* code at all, which never happens — the API's
 * exception handler gives an unrouted path a proper problem+json body like
 * everything else, so every unbuilt screen would have gone on claiming an
 * outage. `NotImplementedEndpointsTest` in apps/api pins both halves so the
 * next change to that handler breaks a test instead of these pages.
 *
 * The day AccountApi is implemented, these pages start showing data with no
 * change here.
 */
export async function fetched<T>(call: ApiCall<T>): Promise<Fetched<T>> {
  let result: Awaited<ReturnType<ApiCall<T>>>;

  try {
    result = await call();
  } catch {
    // No response at all: the API is down, or unreachable from this server.
    return { state: "unavailable" };
  }

  if (result.response.status === 404 && problemCode(result.error) === "NOT_FOUND") {
    return { state: "not-implemented" };
  }

  if (result.data === undefined) {
    return { state: "unavailable" };
  }

  return { state: "ok", data: result.data };
}
