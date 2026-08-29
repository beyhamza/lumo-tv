import type { components } from "@lumo/contracts";

/**
 * The contract's own types, re-exported under readable names (ADR 0001).
 *
 * **Why this file exists.** Nothing in this application may describe a request
 * or a response shape by hand. Writing `{ id: string; label: string; status:
 * string }` next to a call is convenient and looks harmless — it is how the
 * account pages were first written — but it means a field renamed in
 * `openapi.yaml` compiles fine here and fails at runtime. Worse for optional
 * fields: declaring `last_synced_at?: string | null` locally makes a rename
 * invisible even to the type checker, because an absent optional property is
 * assignable.
 *
 * Separate from `client.ts` because that module imports `server-only`. These are
 * types, erased at build time, and a client component may legitimately want one.
 */
export type Source = components["schemas"]["Source"];
export type SourceStatus = components["schemas"]["SourceStatus"];
export type SourceKind = components["schemas"]["SourceKind"];
export type SyncStep = components["schemas"]["SyncStep"];
export type CreateSourceRequest = components["schemas"]["CreateSourceRequest"];
export type UpdateSourceRequest = components["schemas"]["UpdateSourceRequest"];
export type FieldError = components["schemas"]["FieldError"];
export type IngestionErrorCode = components["schemas"]["IngestionErrorCode"];
export type Category = components["schemas"]["Category"];
export type Channel = components["schemas"]["Channel"];
export type VodItem = components["schemas"]["VodItem"];
export type VodPlaybackInfo = components["schemas"]["VodPlaybackInfo"];
export type Series = components["schemas"]["Series"];
export type SeriesDetail = components["schemas"]["SeriesDetail"];
export type Season = components["schemas"]["Season"];
export type Episode = components["schemas"]["Episode"];
export type EpisodePlaybackInfo = components["schemas"]["EpisodePlaybackInfo"];
export type Favorite = components["schemas"]["Favorite"];
export type FavoriteGroup = components["schemas"]["FavoriteGroup"];
export type Device = components["schemas"]["Device"];
export type DeviceRegistration = components["schemas"]["DeviceRegistration"];
export type Platform = components["schemas"]["Platform"];
export type Entitlement = components["schemas"]["Entitlement"];
export type Plan = components["schemas"]["Plan"];
export type User = components["schemas"]["User"];
export type AuthSession = components["schemas"]["AuthSession"];
export type TokenPair = components["schemas"]["TokenPair"];
export type Problem = components["schemas"]["Problem"];
export type RefreshRequest = components["schemas"]["RefreshRequest"];
export type ErrorCode = components["schemas"]["ErrorCode"];
