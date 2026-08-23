import type { Metadata } from "next";
import { getTranslations, setRequestLocale } from "next-intl/server";
import { confirmEmail } from "@/actions/password";
import { hrefFor } from "@/i18n/navigation";
import type { Locale } from "@/i18n/routing";
import { errorMessage } from "@/lib/api/error-message";
import { pageMetadata } from "@/lib/seo/metadata";

/**
 * The page the verification email links to (US-01).
 *
 * It exists because `AccountMailer` sends every new account to
 * `{web.base-url}/verify-email?token=…`, and until now that URL was a 404: the
 * API implemented the endpoint, the email carried the link, and the last step
 * of registration led nowhere.
 *
 * Confirmation happens on a button press, not on arrival. The token is
 * single-use, and mail providers fetch the links they deliver in order to scan
 * them — verifying during render means a scanner burns the token and the person
 * who actually clicks is told it is invalid. One press, no client JavaScript.
 */
export async function generateMetadata({
  params,
}: PageProps<"/[locale]/verify-email">): Promise<Metadata> {
  const { locale } = await params;
  const t = await getTranslations({ locale, namespace: "VerifyEmail" });

  return pageMetadata({
    locale: locale as Locale,
    href: "/verify-email",
    title: t("metaTitle"),
    description: t("metaDescription"),
    // A URL carrying a single-use token has no business in a search index.
    index: false,
  });
}

export default async function VerifyEmailPage({
  params,
  searchParams,
}: PageProps<"/[locale]/verify-email">) {
  const { locale } = await params;
  setRequestLocale(locale);

  const query = await searchParams;
  const token = typeof query.token === "string" ? query.token : "";
  const status = typeof query.status === "string" ? query.status : undefined;
  const error = typeof query.error === "string" ? query.error : undefined;

  const t = await getTranslations("VerifyEmail");
  const auth = await getTranslations("Auth");
  const errors = await getTranslations("Errors");

  if (status === "ok") {
    return (
      <div className="w-full max-w-sm">
        <h1 className="text-2xl font-semibold tracking-tight">{t("successTitle")}</h1>
        <p role="status" className="text-muted-foreground mt-2 text-sm">
          {t("successBody")}
        </p>
        <a
          href={hrefFor(locale as Locale, "/login")}
          className="bg-primary text-primary-foreground mt-6 inline-flex h-11 items-center rounded-lg px-4 text-sm font-medium"
        >
          {auth("submitLogin")}
        </a>
      </div>
    );
  }

  return (
    <div className="w-full max-w-sm">
      <h1 className="text-2xl font-semibold tracking-tight">{t("title")}</h1>
      <p className="text-muted-foreground mt-2 text-sm">{t("body")}</p>

      {error ? (
        <p role="alert" className="text-destructive mt-4 text-sm">
          {errorMessage(error, errors)}
        </p>
      ) : null}

      {token ? (
        <form action={confirmEmail} className="mt-6">
          <input type="hidden" name="token" value={token} />
          <button
            type="submit"
            className="bg-primary text-primary-foreground h-11 w-full rounded-lg text-sm font-medium"
          >
            {t("confirm")}
          </button>
        </form>
      ) : (
        <p className="mt-6 text-sm">{t("missingToken")}</p>
      )}
    </div>
  );
}
