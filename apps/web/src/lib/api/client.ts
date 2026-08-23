import "server-only";

import createClient from "openapi-fetch";
import type { paths } from "@lumo/contracts";
import { apiBaseUrl } from "@/lib/env";
import type { Problem } from "./types";

/**
 * The typed API client (ADR 0001).
 *
 * `paths` comes from packages/contracts/generated/typescript/api.d.ts, generated
 * from openapi.yaml. openapi-fetch emits no runtime of its own: it is a thin
 * wrapper over `fetch` that reads those types, so a call to an endpoint that
 * does not exist, or a body that does not match the contract, is a **type
 * error** rather than a 404 discovered in production.
 *
 * Nothing in this application declares a request or a response shape by hand.
 * The contract changes first, then this compiles or it does not.
 *
 * Server-only, deliberately. Every authenticated call goes out from the Next.js
 * server, because the access token lives in an httpOnly cookie and must never
 * be readable from client JavaScript. A browser-side client would need the token
 * in JavaScript to be useful, which is the thing we are not doing.
 */
export function api(accessToken?: string) {
  return createClient<paths>({
    baseUrl: apiBaseUrl(),
    headers: accessToken
      ? { Authorization: `Bearer ${accessToken}` }
      : undefined,
  });
}

/**
 * Pulls the machine-readable code out of an RFC 7807 body.
 *
 * The contract is explicit that `code` is the only field a client may branch on,
 * and that an unrecognised value must be handled gracefully rather than crash:
 * new codes can appear within v1. Hence `string | undefined` and a caller that
 * falls back to a generic message.
 */
export function problemCode(error: unknown): string | undefined {
  if (!error || typeof error !== "object") return undefined;
  const code = (error as Partial<Problem>).code;
  return typeof code === "string" ? code : undefined;
}
