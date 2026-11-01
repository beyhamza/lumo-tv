"use server";

import { revalidatePath } from "next/cache";
import { getLocale, getTranslations } from "next-intl/server";
import { redirect } from "@/i18n/navigation";
import { adminFailure, type AdminFailure } from "@/lib/admin/notices";
import { api, problemCode } from "@/lib/api/client";
import type { LegalDocumentVersion } from "@/lib/api/types";
import { legalDocument, legalDocumentType } from "@/lib/legal/documents";
import { legalPlaceholders } from "@/lib/legal/markdown";
import { requireSession } from "@/lib/session/session";

/**
 * The administration's writes (S16B-08, ADR 0015).
 *
 * The API decides who may call them: an ordinary account gets a 403 here as it
 * would anywhere else. Results travel in the redirect (`apps/web/AGENTS.md` §4).
 */

const LEGAL_PATH = "/app/admin/legal";

/** Every marketing page: the documents, and the contact address in the footer. */
function refreshPublicPages() {
  revalidatePath("/[locale]/(marketing)", "layout");
}

function versionPath(id: string) {
  return `${LEGAL_PATH}/${id}`;
}

function nullIfBlank(value: FormDataEntryValue | null): string | null {
  const text = String(value ?? "").trim();
  return text === "" ? null : text;
}

/**
 * What a new draft starts from: the version in force when there is one, so that
 * a new version is a change and not a retyping. The first privacy policy starts
 * from the text the site has been showing as a draft.
 */
async function firstContent(type: LegalDocumentVersion["document_type"], accessToken: string) {
  const { data } = await api(accessToken).GET("/legal/{document_type}", {
    params: { path: { document_type: type } },
  });
  if (data) {
    return { title_fr: data.title_fr, title_en: data.title_en, body_fr: data.body_fr, body_en: data.body_en };
  }

  const { key } = legalDocument(type);
  const [fr, en] = await Promise.all(
    (["fr", "en"] as const).map(async (locale) => {
      const legal = await getTranslations({ locale, namespace: "Legal" });
      const title = legal(`${key}.title`);
      return { title, body: type === "PRIVACY_POLICY" ? await privacyStarter(locale) : "" };
    }),
  );
  return { title_fr: fr.title, title_en: en.title, body_fr: fr.body, body_en: en.body };
}

async function privacyStarter(locale: "fr" | "en"): Promise<string> {
  const privacy = await getTranslations({ locale, namespace: "Privacy" });
  const sections = privacy.raw("sections") as { title: string; body?: string[]; items?: string[] }[];
  return [
    privacy("intro"),
    ...sections.flatMap((section) => [
      `## ${section.title}`,
      ...(section.body ?? []),
      ...(section.items ? [section.items.map((item) => `- ${item}`).join("\n")] : []),
    ]),
  ].join("\n\n");
}

export async function createLegalDraft(formData: FormData): Promise<void> {
  const locale = await getLocale();
  const session = await requireSession();
  const type = legalDocumentType(formData.get("document_type"));

  let target = `${LEGAL_PATH}?notice=failed`;
  if (type) {
    try {
      const { data, error } = await api(session.accessToken).POST("/admin/legal/versions", {
        body: { document_type: type, ...(await firstContent(type, session.accessToken)) },
      });
      target = data ? versionPath(data.id) : `${LEGAL_PATH}?notice=${adminFailure(problemCode(error))}`;
    } catch {
      // No answer: the default target says it failed.
    }
  }
  redirect({ href: target, locale });
}

export type LegalDraftFormState = {
  failure?: AdminFailure;
  /** What was typed, given back: a refused save must not cost a page of legal text. */
  values?: {
    title_fr: string;
    title_en: string;
    body_fr: string;
    body_en: string;
    change_summary: string;
  };
};

/**
 * Saves a draft, then stays on it or moves to the publication step.
 *
 * Through `useActionState` rather than a redirect on failure, unlike the other
 * actions here: this form can hold an afternoon of writing, and a redirect would
 * drop all of it the one time the API is restarting.
 */
export async function saveLegalDraft(
  _previous: LegalDraftFormState,
  formData: FormData,
): Promise<LegalDraftFormState> {
  const locale = await getLocale();
  const session = await requireSession();
  const id = String(formData.get("id") ?? "");
  const values = {
    title_fr: String(formData.get("title_fr") ?? ""),
    title_en: String(formData.get("title_en") ?? ""),
    body_fr: String(formData.get("body_fr") ?? ""),
    body_en: String(formData.get("body_en") ?? ""),
    change_summary: String(formData.get("change_summary") ?? ""),
  };

  let failure: AdminFailure | null = null;
  try {
    const { error, response } = await api(session.accessToken).PUT("/admin/legal/versions/{id}", {
      params: { path: { id } },
      body: { ...values, change_summary: nullIfBlank(values.change_summary) },
    });
    if (!response.ok) failure = adminFailure(problemCode(error));
  } catch {
    failure = "failed";
  }
  if (failure) return { failure, values };

  const next =
    formData.get("intent") === "review"
      ? `${versionPath(id)}?confirm=publish#publish-confirmation`
      : `${versionPath(id)}?notice=draft-saved`;
  redirect({ href: next, locale });
  return {};
}

export async function publishLegalDraft(formData: FormData): Promise<void> {
  const locale = await getLocale();
  const session = await requireSession();
  const id = String(formData.get("id") ?? "");
  const client = api(session.accessToken);

  let target = `${versionPath(id)}?confirm=publish&notice=failed#publish-confirmation`;
  try {
    const { data: version } = await client.GET("/admin/legal/versions/{id}", {
      params: { path: { id } },
    });
    if (version) {
      const texts = [version.title_fr, version.title_en, version.body_fr, version.body_en];
      if (texts.some((text) => legalPlaceholders(text).length > 0)) {
        target = `${versionPath(id)}?confirm=publish&notice=placeholders#publish-confirmation`;
      } else {
        const { data, error } = await client.POST("/admin/legal/versions/{id}/publish", {
          params: { path: { id } },
        });
        if (data) {
          refreshPublicPages();
          target = `${LEGAL_PATH}?notice=published`;
        } else {
          target = `${versionPath(id)}?confirm=publish&notice=${adminFailure(problemCode(error))}#publish-confirmation`;
        }
      }
    }
  } catch {
    // No answer: the default target says it failed.
  }
  redirect({ href: target, locale });
}

export async function deleteLegalDraft(formData: FormData): Promise<void> {
  const locale = await getLocale();
  const session = await requireSession();
  const id = String(formData.get("id") ?? "");

  let target = `${versionPath(id)}?notice=failed`;
  try {
    const { error, response } = await api(session.accessToken).DELETE("/admin/legal/versions/{id}", {
      params: { path: { id } },
    });
    target = response.ok
      ? `${LEGAL_PATH}?notice=draft-deleted`
      : `${versionPath(id)}?notice=${adminFailure(problemCode(error))}`;
  } catch {
    // No answer: the default target says it failed.
  }
  redirect({ href: target, locale });
}

export async function saveAppSettings(formData: FormData): Promise<void> {
  const locale = await getLocale();
  const session = await requireSession();

  let notice: string = "failed";
  try {
    const { error, response } = await api(session.accessToken).PUT("/admin/settings", {
      body: {
        support_email: nullIfBlank(formData.get("support_email")),
        registration_open: formData.get("registration_open") === "on",
      },
    });
    if (response.ok) {
      refreshPublicPages();
      notice = "settings-saved";
    } else {
      notice = adminFailure(problemCode(error));
    }
  } catch {
    // No answer: the default notice says it failed.
  }
  redirect({ href: `/app/admin/settings?notice=${notice}`, locale });
}
