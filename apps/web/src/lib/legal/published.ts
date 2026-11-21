import "server-only";

import { PHASE_PRODUCTION_BUILD } from "next/constants";
import createClient from "openapi-fetch";
import type { paths } from "@lumo/contracts";
import { problemCode } from "@/lib/api/client";
import type { LegalDocumentType, LegalDocumentVersion } from "@/lib/api/types";
import { apiBaseUrl } from "@/lib/env";

/**
 * What the marketing zone reads from the API (ADR 0015 §5): the public documents
 * and the public configuration.
 *
 * Not `api()` from `lib/api/client.ts`: that one reads `headers()` to forward the
 * visitor's address, which would make every page calling it dynamic
 * (`apps/web/AGENTS.md` §2). A cached fetch with no cookie and no header keeps the
 * pages static. A publication refreshes them at once with `revalidatePath`.
 */
const REVALIDATE_SECONDS = 3600;

/** A build with no API must not wait for it. Ten pages at thirty seconds each is a broken build. */
const TIMEOUT_MS = 3000;

function publicApi() {
  return createClient<paths>({
    baseUrl: apiBaseUrl(),
    fetch: (request) =>
      fetch(request, {
        next: { revalidate: REVALIDATE_SECONDS },
        signal: AbortSignal.timeout(TIMEOUT_MS),
      }),
  });
}

export type PublishedLegal =
  | { state: "published"; version: LegalDocumentVersion }
  /** The API answered: nothing has been published. */
  | { state: "none" }
  /** No API, which only ever reaches a page during a build. */
  | { state: "build-without-api" };

/**
 * The version in force of a document.
 *
 * **An outage after the build throws**, on purpose. These pages are regenerated
 * in the background, and Next keeps serving the last good page when that throws.
 * Answering "nothing published" instead would replace a policy in force with a
 * draft, for an hour, because the API restarted.
 */
export async function loadPublishedLegal(type: LegalDocumentType): Promise<PublishedLegal> {
  try {
    const { data, error, response } = await publicApi().GET("/legal/{document_type}", {
      params: { path: { document_type: type } },
    });
    if (data) return { state: "published", version: data };
    if (response.status === 404 && problemCode(error) === "LEGAL_DOCUMENT_NOT_PUBLISHED") {
      return { state: "none" };
    }
    throw new Error(`GET /legal/${type} answered ${response.status}`);
  } catch (cause) {
    if (process.env.NEXT_PHASE === PHASE_PRODUCTION_BUILD) return { state: "build-without-api" };
    throw cause;
  }
}

/**
 * The contact address, or null when none is set or the API did not answer.
 *
 * Null rather than a throw: this is in the footer of every marketing page, and
 * a missing contact link is no reason to fail any of them.
 */
export async function loadSupportEmail(): Promise<string | null> {
  try {
    const { data } = await publicApi().GET("/config", {});
    return data?.support_email ?? null;
  } catch {
    return null;
  }
}
