"use server";

import { getLocale, getTranslations } from "next-intl/server";
import { revalidatePath } from "next/cache";
import { redirect } from "@/i18n/navigation";
import { api, problemCode } from "@/lib/api/client";
import { errorMessage } from "@/lib/api/error-message";
import { fetched } from "@/lib/api/fetched";
import type { CreateSourceRequest, FieldError, SourceKind } from "@/lib/api/types";
import { requireSession } from "@/lib/session/session";
import { isUuid } from "@/lib/sources/active-source";
import {
  forgetActiveSource,
  readStoredSourceId,
  rememberActiveSource,
} from "@/lib/sources/active-source-store";
import { parseRetryAfter, retryAtParam } from "@/lib/sources/retry-after";

/**
 * Registering and managing sources, from the web.
 *
 * Server Actions rather than a browser-side call, for the reason that governs
 * this whole zone: the access token lives in an httpOnly cookie and must never
 * be readable from client JavaScript (`apps/web/AGENTS.md` §4). A second benefit
 * falls out of it — every one of these forms works with JavaScript disabled.
 *
 * <h2>What is deliberately NOT validated here</h2>
 *
 * The shape rules (`XTREAM` needs host + username + password, `M3U_URL` needs a
 * playlist URL) are enforced by the API, per field, and this module renders what
 * comes back rather than re-deriving it. A second copy of those rules would be
 * one more place to forget when the contract gains a source kind — and it would
 * be the copy that is wrong, because the API's is the one that decides.
 *
 * What IS checked before the call is the empty form: sending an obviously blank
 * request to be told what we already know is a round trip for nothing.
 */

export type SourceFormState = {
  /** Already translated, ready to render. Never a raw API message. */
  error?: string;
  /**
   * Per-field messages, keyed by input `name`.
   *
   * The API answers a JSON pointer (`/m3u_url`); the form thinks in input names
   * (`m3u_url`). {@link fieldErrorsByName} is the whole of the translation.
   */
  fieldErrors?: Record<string, string>;
  /**
   * What the user typed, echoed back so a rejected form is not an empty form.
   *
   * **Never contains the password.** Echoing it would put an Xtream credential
   * in the HTML of the retry page, and the one place it is allowed to exist on
   * this side is the request that carried it.
   */
  values?: Record<string, string>;
};

/** The kinds `POST /sources` accepts. `M3U_FILE` is in the contract but not creatable. */
const CREATABLE_KINDS: readonly SourceKind[] = ["M3U_URL", "XTREAM"];

export async function createSource(
  _previous: SourceFormState,
  formData: FormData,
): Promise<SourceFormState> {
  const locale = await getLocale();
  const t = await getTranslations({ locale, namespace: "Errors" });
  const session = await requireSession();

  const kind = String(formData.get("kind") ?? "") as SourceKind;
  const label = text(formData, "label");
  const values = {
    kind,
    label,
    host: text(formData, "host"),
    username: text(formData, "username"),
    m3u_url: text(formData, "m3u_url"),
    epg_url: text(formData, "epg_url"),
  };

  if (!CREATABLE_KINDS.includes(kind)) {
    return { error: errorMessage("VALIDATION_FAILED", t), values };
  }
  if (label.length === 0) {
    return { fieldErrors: { label: t("VALIDATION_FAILED") }, values };
  }

  const body: CreateSourceRequest = {
    label,
    kind,
    // Sent as null rather than omitted when empty: the contract types these
    // nullable, and null says "not applicable to this kind" where an empty
    // string would be a value the server has to guess about.
    host: nullable(values.host),
    username: nullable(values.username),
    password: nullable(String(formData.get("password") ?? "")),
    m3u_url: nullable(values.m3u_url),
    epg_url: nullable(values.epg_url),
    // The contract's default, sent explicitly because the generated type asks
    // for it. Not a choice offered at creation: a catalogue that silently goes
    // stale is the failure a user cannot diagnose, so it starts on and the
    // source page is where it can be turned off.
    auto_sync: true,
  };

  // Asked before the POST, because afterwards the answer is always "at least
  // one". Only a list that was actually fetched and actually empty counts: an
  // API that did not answer has not said the account is empty, and guessing so
  // would let an additional source steal the selection (US-024).
  const before = await fetched(() => api(session.accessToken).GET("/sources", {}));
  const isFirstSource = before.state === "ok" && before.data.items.length === 0;

  let result: { data?: { id: string }; error?: unknown };

  try {
    result = await api(session.accessToken).POST("/sources", { body });
  } catch {
    return { error: errorMessage("NETWORK", t), values };
  }

  if (result.error) {
    const fieldErrors = fieldErrorsByName(result.error, t);
    return {
      // A per-field answer is shown at the fields; anything else needs a place
      // to be said, and the top of the form is that place.
      error: Object.keys(fieldErrors).length > 0
        ? undefined
        : errorMessage(problemCode(result.error), t),
      fieldErrors,
      values,
    };
  }

  // The first source becomes the one this browser browses; an additional one
  // does not take the place of the source already in use (US-024, "Après
  // ajout"). With a single source the resolver would pick it anyway — writing it
  // down is for the day a second one arrives, so that day does not open on
  // "choose a source" for somebody who never had a choice to make.
  if (isFirstSource && result.data?.id) {
    await rememberActiveSource(session.userId, result.data.id);
  }

  // 202, not 201: the source exists and its catalogue does not. The detail page
  // is where the wait happens.
  // The layout, not only the list: the rail's source switcher is drawn from the
  // same `GET /sources`, on every page of the zone.
  revalidatePath(`/${locale}/app`, "layout");
  redirect({ href: `/app/sources/${result.data?.id}`, locale });
  return {};
}

/**
 * Renames a source, or toggles automatic re-synchronisation.
 *
 * Nothing here touches host, credentials or URLs. Changing one of those
 * re-triggers an ingestion, which is a different screen and a different
 * conversation with the user — the contract is explicit that `auto_sync` is the
 * one property in this request that leaves the catalogue alone.
 */
export async function updateSource(formData: FormData): Promise<void> {
  const locale = await getLocale();
  const session = await requireSession();
  const id = String(formData.get("id") ?? "");

  const label = text(formData, "label");
  const autoSync = formData.get("auto_sync");

  await api(session.accessToken).PATCH("/sources/{id}", {
    params: { path: { id } },
    body: {
      ...(label.length > 0 ? { label } : {}),
      ...(autoSync === null ? {} : { auto_sync: autoSync === "on" || autoSync === "true" }),
    },
  });

  revalidatePath(`/${locale}/app/sources/${id}`);
  // The layout, not only the list: the rail's source switcher is drawn from the
  // same `GET /sources`, on every page of the zone.
  revalidatePath(`/${locale}/app`, "layout");
  redirect({ href: `/app/sources/${id}`, locale });
}

/**
 * Asks for a re-synchronisation now (US-024, "Actualisation").
 *
 * <h2>Every outcome lands on the source page, and the page says which it was</h2>
 *
 * A form posted without JavaScript gets nothing back from a Server Action except
 * its redirect, so the outcome travels in the redirect's query string and the
 * source page reads it (`sync=`, `retryAt=`):
 *
 * - **`202`** — nothing to add: the page shows the import running.
 * - **`409 SOURCE_SYNC_IN_PROGRESS`** — not an error either. The thing the user
 *   asked for is already happening — two tabs, or the server's own automatic
 *   refresh got there first — and the page they land on shows it happening. The
 *   button is disabled while a refresh runs, so this is the race, not the rule.
 * - **`429 SOURCE_SYNC_RATE_LIMITED`** — `sync=limited`, plus the instant the
 *   wait ends when the server sent a usable `Retry-After`. **Never a duration of
 *   ours**: a missing or unparsable header yields the message without a number
 *   (`lib/sources/retry-after.ts`, which is also why it is an instant and why it
 *   is not a cookie).
 * - **`404 SOURCE_NOT_FOUND`** — no parameter: the source page fetches the
 *   source itself and says it is gone.
 * - **anything else, or no answer** — `sync=failed`. It used to be dropped: the
 *   page reloaded unchanged and the button looked broken.
 *
 * openapi-fetch hands back the raw `response` beside `data` and `error`, which is
 * where the header is read: `Retry-After` is not part of any body.
 */
export async function syncSource(formData: FormData): Promise<void> {
  const locale = await getLocale();
  const session = await requireSession();
  const id = String(formData.get("id") ?? "");

  // The id ends up in a redirect target below. It came from a hidden field,
  // which is the browser's to rewrite, so it is a UUID or it goes nowhere.
  if (!isUuid(id)) {
    redirect({ href: "/app/sources", locale });
    return;
  }

  let outcome = "";

  try {
    const result = await api(session.accessToken).POST("/sources/{id}/sync", {
      params: { path: { id } },
    });
    const code = problemCode(result.error);

    if (code === "SOURCE_SYNC_RATE_LIMITED") {
      const wait = parseRetryAfter(result.response.headers.get("Retry-After"));
      outcome =
        wait === null
          ? "?sync=limited"
          : `?sync=limited&retryAt=${retryAtParam(wait, Date.now())}`;
    } else if (
      result.error &&
      code !== "SOURCE_SYNC_IN_PROGRESS" &&
      code !== "SOURCE_NOT_FOUND"
    ) {
      outcome = "?sync=failed";
    }
  } catch {
    outcome = "?sync=failed";
  }

  revalidatePath(`/${locale}/app/sources/${id}`);
  // The layout too: the rail's switcher shows each source's status.
  revalidatePath(`/${locale}/app`, "layout");
  redirect({ href: `/app/sources/${id}${outcome}`, locale });
}

/**
 * Deletes a source and everything ingested from it (US-024, "Suppression").
 *
 * <h2>A deletion that failed is said, not swallowed</h2>
 *
 * This used to redirect to My sources whatever the API had answered. After a
 * refusal or an outage the user landed on a list that still held the source, with
 * nothing to say why — and no way to tell "it did not work" from "the list is
 * stale". Now only a deletion the server **confirmed** goes to the list; anything
 * else returns to the confirmation, which says it failed and offers the same two
 * buttons again.
 *
 * `404 SOURCE_NOT_FOUND` counts as confirmed: the source is gone — deleted from
 * the phone a minute ago, or by a double submit — which is what was asked for.
 */
export async function deleteSource(formData: FormData): Promise<void> {
  const locale = await getLocale();
  const session = await requireSession();
  const id = String(formData.get("id") ?? "");

  // Same reason as `syncSource`: this value reaches a redirect target.
  if (!isUuid(id)) {
    redirect({ href: "/app/sources", locale });
    return;
  }

  let deleted = false;
  try {
    const result = await api(session.accessToken).DELETE("/sources/{id}", {
      params: { path: { id } },
    });
    deleted = !result.error || problemCode(result.error) === "SOURCE_NOT_FOUND";
  } catch {
    deleted = false;
  }

  if (!deleted) {
    // Nothing is forgotten and nothing is revalidated: nothing changed.
    redirect({
      href: `/app/sources/${id}?confirm=delete&delete=failed#delete-confirmation`,
      locale,
    });
    return;
  }

  // If this browser was browsing it, forget that, and let the next render decide
  // again from what is left: the only one, a question, or "add a source"
  // (US-018, US-024 "Suppression"). Only on a deletion the server confirmed — a
  // refused or failed one leaves the source where it was, and the choice with it.
  //
  // Other browsers are not told, and do not need to be: their cookie now names
  // a source absent from `GET /sources`, which resolves the same way — and a
  // player open elsewhere finds out within a minute (`use-source-gone.ts`).
  if ((await readStoredSourceId(session.userId)) === id) {
    await forgetActiveSource(session.userId);
  }

  // Its catalogue, favourites, playback progress and recent channels went with
  // it, by cascade (contract lot C4 — P4).
  // The layout, not only the list: the rail's source switcher is drawn from the
  // same `GET /sources`, on every page of the zone.
  revalidatePath(`/${locale}/app`, "layout");
  redirect({ href: "/app/sources", locale });
}

// ---- helpers ---------------------------------------------------------------

function text(formData: FormData, name: string): string {
  return String(formData.get(name) ?? "").trim();
}

function nullable(value: string): string | null {
  return value.length > 0 ? value : null;
}

/**
 * Turns `Problem.errors[]` into messages keyed by input name.
 *
 * The pointer is relative to the request body and its properties are named
 * exactly as the inputs are (`/m3u_url` → `m3u_url`), which is not a
 * coincidence: the form is built from the contract's property names for this
 * reason.
 *
 * A pointer nobody expects is dropped rather than rendered next to the wrong
 * field. It still reaches the user through the form-level message, because an
 * empty `fieldErrors` is what makes the caller fall back to it.
 */
function fieldErrorsByName(
  error: unknown,
  t: (key: "VALIDATION_FAILED") => string,
): Record<string, string> {
  const errors = (error as { errors?: FieldError[] } | null)?.errors;
  if (!Array.isArray(errors)) return {};

  const known = ["label", "kind", "host", "username", "password", "m3u_url", "epg_url"];
  const byName: Record<string, string> = {};

  for (const fieldError of errors) {
    const name = fieldError.field.replace(/^\//, "");
    if (known.includes(name)) {
      // One message per field, not per code. `REQUIRED` and `UNSUPPORTED` both
      // mean "this field is why the request was refused", and the field itself
      // says which one it is.
      byName[name] = t("VALIDATION_FAILED");
    }
  }
  return byName;
}
