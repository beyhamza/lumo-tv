import type { Metadata } from "next";
import { getTranslations, setRequestLocale } from "next-intl/server";
import { approveDeviceCode } from "@/actions/activate";
import { hrefFor } from "@/i18n/navigation";
import type { Locale } from "@/i18n/routing";
import { pageMetadata } from "@/lib/seo/metadata";
import { getSession } from "@/lib/session/session";

/**
 * TV activation (US-05) — the third rendering zone, and the most constrained.
 *
 * docs/architecture.md §4 calls it "SSR minimal", and the reason is the
 * situation it is used in: standing up, phone in hand, in front of a television
 * showing an eight-character code. So:
 *
 * - **Zero client components.** The form posts to a Server Action, the result
 *   comes back as a redirect with a query parameter. No hydration, no
 *   JavaScript required — the page works on a phone with a flaky connection, or
 *   with scripting disabled entirely.
 * - **Keyboard-first.** One field, focused on load, with `enterKeyHint="go"` so
 *   the on-screen keyboard offers to submit. Tab reaches the button, Enter
 *   submits from the field.
 * - **Readable at arm's length.** Large type, a tall input, generous spacing,
 *   and a code field in a monospace font with wide tracking so a `0` and an `O`
 *   are told apart — even though the server's alphabet excludes both, what the
 *   user types is unconstrained.
 *
 * Rendered dynamically because it reads the session cookie, and `noindex`
 * because an activation URL carries a single-use code.
 */
export async function generateMetadata({
  params,
}: PageProps<"/[locale]/activate">): Promise<Metadata> {
  const { locale } = await params;
  const t = await getTranslations({ locale, namespace: "Activate" });

  return pageMetadata({
    locale: locale as Locale,
    href: "/activate",
    title: t("metaTitle"),
    description: t("metaDescription"),
    index: false,
  });
}

export default async function ActivatePage({
  params,
  searchParams,
}: PageProps<"/[locale]/activate">) {
  const { locale } = await params;
  setRequestLocale(locale);

  const query = await searchParams;
  const code = typeof query.code === "string" ? query.code : "";
  const status = typeof query.status === "string" ? query.status : undefined;
  const error = typeof query.error === "string" ? query.error : undefined;

  const t = await getTranslations("Activate");
  const errors = await getTranslations("Errors");
  const brand = await getTranslations("Brand");
  const session = await getSession();

  return (
    <div className="mx-auto flex min-h-full w-full max-w-md flex-1 flex-col px-5 py-10">
      <a href={hrefFor(locale as Locale, "/")} className="text-muted-foreground text-sm">
        {brand("name")}
      </a>

      <main id="main" className="mt-10">
        <h1 className="text-3xl font-semibold tracking-tight">{t("title")}</h1>
        <p className="text-muted-foreground mt-3 text-base">{t("subtitle")}</p>

        {status === "ok" ? (
          <p
            role="status"
            className="border-border mt-8 rounded-xl border px-4 py-3 text-base"
          >
            {t("success")}
          </p>
        ) : null}

        {error ? (
          <p
            role="alert"
            className="border-destructive/40 text-destructive mt-8 rounded-xl border px-4 py-3 text-base"
          >
            {errorMessage(error, errors)}
          </p>
        ) : null}

        {session ? (
          <form action={approveDeviceCode} className="mt-8 space-y-5">
            <div className="space-y-2">
              <label htmlFor="user_code" className="block text-base font-medium">
                {t("codeLabel")}
              </label>
              <input
                id="user_code"
                name="user_code"
                defaultValue={code}
                autoFocus
                required
                // The code is alphanumeric and case-insensitive; the browser's
                // own helpers only get in the way here.
                autoComplete="off"
                autoCapitalize="characters"
                autoCorrect="off"
                spellCheck={false}
                inputMode="text"
                enterKeyHint="go"
                maxLength={16}
                aria-describedby="code-hint"
                className="border-input bg-background focus-visible:border-ring focus-visible:ring-ring/50 h-14 w-full rounded-xl border px-4 font-mono text-2xl tracking-[0.3em] uppercase focus-visible:ring-3 focus-visible:outline-none"
              />
              <p id="code-hint" className="text-muted-foreground text-sm">
                {t("codeHint")}
              </p>
            </div>

            {/* A plain button, not the shared one: the shared Button is fine
                here, but this page keeps its dependency surface at zero so it
                cannot acquire a client component by accident. */}
            <button
              type="submit"
              className="bg-primary text-primary-foreground h-14 w-full rounded-xl text-base font-medium"
            >
              {t("submit")}
            </button>
          </form>
        ) : (
          <div className="mt-8">
            <p className="text-base">{t("signInFirst")}</p>
            <a
              href={hrefFor(locale as Locale, `/login?next=${encodeURIComponent(`/activate?code=${code}`)}`)}
              className="bg-primary text-primary-foreground mt-4 inline-flex h-14 w-full items-center justify-center rounded-xl text-base font-medium"
            >
              {t("submit")}
            </a>
          </div>
        )}

        <section className="mt-12">
          <h2 className="text-base font-medium">{t("helpTitle")}</h2>
          <p className="text-muted-foreground mt-2 text-sm">{t("helpBody")}</p>
        </section>
      </main>
    </div>
  );
}

const TRANSLATED_CODES = [
  "VALIDATION_FAILED",
  "RATE_LIMITED",
  "UNAUTHENTICATED",
  "DEVICE_CODE_NOT_FOUND",
  "DEVICE_CODE_EXPIRED",
  "DEVICE_CODE_ALREADY_USED",
] as const;

function errorMessage(
  code: string,
  t: (key: (typeof TRANSLATED_CODES)[number] | "network" | "generic") => string,
): string {
  if ((TRANSLATED_CODES as readonly string[]).includes(code)) {
    return t(code as (typeof TRANSLATED_CODES)[number]);
  }
  return code === "NETWORK" ? t("network") : t("generic");
}
