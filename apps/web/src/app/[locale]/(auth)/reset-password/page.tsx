import type { Metadata } from "next";
import { getTranslations, setRequestLocale } from "next-intl/server";
import { resetPassword } from "@/actions/password";
import { hrefFor } from "@/i18n/navigation";
import type { Locale } from "@/i18n/routing";
import { errorMessage } from "@/lib/api/error-message";
import { pageMetadata } from "@/lib/seo/metadata";

/**
 * The page the reset email links to (US-02).
 *
 * Like `/verify-email`, it exists because `AccountMailer` already sends people
 * to `{web.base-url}/reset-password?token=…` and that URL answered 404.
 *
 * Server-rendered with a plain form and a Server Action: no client JavaScript,
 * so it works on whatever connection and whatever browser opened the email. The
 * token rides in a hidden field rather than staying in the URL of the POST, and
 * the minimum length is stated next to the field before submission (US-01),
 * then enforced again in the action.
 */
export async function generateMetadata({
  params,
}: PageProps<"/[locale]/reset-password">): Promise<Metadata> {
  const { locale } = await params;
  const t = await getTranslations({ locale, namespace: "Password" });

  return pageMetadata({
    locale: locale as Locale,
    href: "/reset-password",
    title: t("resetMetaTitle"),
    description: t("resetMetaDescription"),
    index: false,
  });
}

export default async function ResetPasswordPage({
  params,
  searchParams,
}: PageProps<"/[locale]/reset-password">) {
  const { locale } = await params;
  setRequestLocale(locale);

  const query = await searchParams;
  const token = typeof query.token === "string" ? query.token : "";
  const error = typeof query.error === "string" ? query.error : undefined;

  const t = await getTranslations("Password");
  const auth = await getTranslations("Auth");
  const errors = await getTranslations("Errors");

  return (
    <div className="w-full max-w-sm">
      <h1 className="text-2xl font-semibold tracking-tight">{t("resetTitle")}</h1>
      <p className="text-muted-foreground mt-2 text-sm">{t("resetSubtitle")}</p>

      {error ? (
        <p role="alert" className="text-destructive mt-4 text-sm">
          {errorMessage(error, errors)}
        </p>
      ) : null}

      {token ? (
        <form action={resetPassword} className="mt-8 space-y-4">
          <input type="hidden" name="token" value={token} />

          <div className="space-y-1.5">
            <label htmlFor="password" className="text-sm font-medium">
              {t("newPasswordLabel")}
            </label>
            <input
              id="password"
              name="password"
              type="password"
              autoComplete="new-password"
              minLength={10}
              required
              autoFocus
              aria-describedby="password-hint"
              className="border-input bg-background focus-visible:border-ring focus-visible:ring-ring/50 h-11 w-full rounded-lg border px-3 text-sm focus-visible:ring-3 focus-visible:outline-none"
            />
            <p id="password-hint" className="text-muted-foreground text-sm">
              {auth("passwordHint")}
            </p>
          </div>

          <button
            type="submit"
            className="bg-primary text-primary-foreground h-11 w-full rounded-lg text-sm font-medium"
          >
            {t("submitReset")}
          </button>
        </form>
      ) : (
        <div className="mt-8">
          <p className="text-sm">{t("missingToken")}</p>
          <a
            href={hrefFor(locale as Locale, "/forgot-password")}
            className="mt-4 inline-block text-sm underline underline-offset-4"
          >
            {t("forgotTitle")}
          </a>
        </div>
      )}
    </div>
  );
}
