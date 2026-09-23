import type { AuthSession } from "@/lib/api/types";
import type { SessionPayload } from "./cookie";

/**
 * Shapes the contract's `AuthSession` into what the cookie stores.
 *
 * The parameter is the contract's type, not a local description of it: a field
 * renamed in openapi.yaml has to fail here, at build time, rather than at the
 * first sign-in after deployment.
 *
 * Shared by every road to a session — the sign-in and registration actions —
 * because they all receive the same `AuthSession` and there is nothing about it
 * for any of them to interpret differently.
 */
export function sessionFrom(authSession: AuthSession): SessionPayload {
  return {
    accessToken: authSession.access_token,
    refreshToken: authSession.refresh_token,
    accessTokenExpiresAt: Math.floor(Date.now() / 1000) + authSession.expires_in,
    userId: authSession.user.id,
    deviceId: authSession.device_id,
    email: authSession.user.email,
  };
}
