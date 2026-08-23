import type { Metadata } from "next";
import { getTranslations, setRequestLocale } from "next-intl/server";
import { requestReset } from "@/actions/password";
import { hrefFor } from "@/i18n/navigation";
import type { Locale } from "@/i18n/routing";
import { errorMessage } from "@/lib/api/error-message";
import { pageMetadata } from "@/lib/seo/metadata";

/**
 * Asking for a reset email.
 *
 * Included with the reset page rather than after it: without somewhere to
 * request the email, `/reset-password` is a page nobody can reach, and the fix
 * to the dead link could not be demonstrated end to end.
 *
 * The answer is the same whether or not the address exists — the contract
 * returns `202` either way, and rendering a different page for a known address
 * would hand back the account enumeration the endpoint was designed to refuse.
 */
export async function generateMetadata({
  params,
}: PageProps<"/[locale]/forgot-password">): Promise<Metadata> {
  const { locale } = await params;
  const t = await getTranslations({ locale, namespace: "Password" });

  return pageMetadata({
    locale: locale as Locale,
    href: "/forgot-password",
    title: t("forgotMetaTitle"),
    description: t("forgotMetaDescription"),
    index: false,
  });
}

export default async function ForgotPasswordPage({
  params,
  searchParams,
}: PageProps<"/[locale]/forgot-password">) {
  const { locale } = await params;
  setRequestLocale(locale);

  const query = await searchParams;
  const status = typeof query.status === "string" ? query.status : undefined;
  const error = typeof query.error === "string" ? query.error : undefined;

  const t = await getTranslations("Password");
  const auth = await getTranslations("Auth");
  const errors = await getTranslations("Errors");

  if (status === "sent") {
    return (
      <div className="w-full max-w-sm">
        <h1 className="text-2xl font-semibold tracking-tight">{t("sentTitle")}</h1>
        <p role="status" className="text-muted-foreground mt-2 text-sm">
          {t("sentBody")}
        </p>
        <a
          href={hrefFor(locale as Locale, "/login")}
          className="mt-6 inline-block text-sm underline underline-offset-4"
        >
          {auth("toLogin")}
        </a>
      </div>
    );
  }

  return (
    <div className="w-full max-w-sm">
      <h1 className="text-2xl font-semibold tracking-tight">{t("forgotTitle")}</h1>
      <p className="text-muted-foreground mt-2 text-sm">{t("forgotSubtitle")}</p>

      <form action={requestReset} className="mt-8 space-y-4">
        <div className="space-y-1.5">
          <label htmlFor="email" className="text-sm font-medium">
            {auth("emailLabel")}
          </label>
          <input
            id="email"
            name="email"
            type="email"
            inputMode="email"
            autoComplete="email"
            placeholder={auth("emailPlaceholder")}
            required
            autoFocus
            enterKeyHint="send"
            className="border-input bg-background focus-visible:border-ring focus-visible:ring-ring/50 h-11 w-full rounded-lg border px-3 text-sm focus-visible:ring-3 focus-visible:outline-none"
          />
        </div>

        {error ? (
          <p role="alert" className="text-destructive text-sm">
            {errorMessage(error, errors)}
          </p>
        ) : null}

        <button
          type="submit"
          className="bg-primary text-primary-foreground h-11 w-full rounded-lg text-sm font-medium"
        >
          {t("submitForgot")}
        </button>
      </form>

      <p className="text-muted-foreground mt-6 text-sm">
        <a
          href={hrefFor(locale as Locale, "/login")}
          className="text-foreground underline underline-offset-4"
        >
          {auth("toLogin")}
        </a>
      </p>
    </div>
  );
}
