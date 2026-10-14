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
  const outcome = await settle(call);
  return outcome.kind === "no-response" ? { state: "unavailable" } : classify(outcome.result);
}

/**
 * {@link Fetched}, plus the one answer a page about **one resource** must not
 * mistake for an outage: that resource is gone (BUG-R020-03-03).
 *
 * `gone` is a `404` carrying the resource's own code — `SOURCE_NOT_FOUND` for a
 * source — and nothing else: the generic `NOT_FOUND` stays "not built", and
 * every other failure stays "unavailable". Without it the page of a source
 * deleted from the phone, still open in a tab, told its visitor that the
 * service was down and to try again in a minute — which would never work.
 *
 * A separate function rather than a fourth state of {@link Fetched}: the ten
 * other callers read lists or the account, where no such answer exists, and a
 * state they would all have to handle for nothing is how one of them ends up
 * handling it wrong.
 */
export type FetchedResource<T> = Fetched<T> | { state: "gone" };

export async function fetchedResource<T>(
  call: ApiCall<T>,
  goneCode: string,
): Promise<FetchedResource<T>> {
  const outcome = await settle(call);
  if (outcome.kind === "no-response") return { state: "unavailable" };

  const { response, error } = outcome.result;
  if (response.status === 404 && problemCode(error) === goneCode) {
    return { state: "gone" };
  }
  return classify(outcome.result);
}

type Settled<T> =
  | { kind: "no-response" }
  | { kind: "response"; result: Awaited<ReturnType<ApiCall<T>>> };

async function settle<T>(call: ApiCall<T>): Promise<Settled<T>> {
  try {
    return { kind: "response", result: await call() };
  } catch {
    // No response at all: the API is down, or unreachable from this server.
    return { kind: "no-response" };
  }
}

function classify<T>(result: Awaited<ReturnType<ApiCall<T>>>): Fetched<T> {
  if (result.response.status === 404 && problemCode(result.error) === "NOT_FOUND") {
    return { state: "not-implemented" };
  }

  if (result.data === undefined) {
    return { state: "unavailable" };
  }

  return { state: "ok", data: result.data };
}
