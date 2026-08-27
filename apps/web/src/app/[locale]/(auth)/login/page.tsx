import type { Metadata } from "next";
import { getTranslations, setRequestLocale } from "next-intl/server";
import { GoogleSignInButton } from "@/components/auth/GoogleSignInButton";
import { SignInForm } from "@/components/auth/SignInForm";
import { hrefFor } from "@/i18n/navigation";
import type { Locale } from "@/i18n/routing";
import { googleErrorCode } from "@/lib/auth/google";
import { pageMetadata } from "@/lib/seo/metadata";

export async function generateMetadata({
  params,
}: PageProps<"/[locale]/login">): Promise<Metadata> {
  const { locale } = await params;
  const t = await getTranslations({ locale, namespace: "Auth" });

  return pageMetadata({
    locale: locale as Locale,
    href: "/login",
    title: t("loginMetaTitle"),
    description: t("loginMetaDescription"),
    // Not indexable: a sign-in page in search results is noise, and this one can
    // be reached with a `next` parameter that would multiply its URLs.
    index: false,
  });
}

export default async function LoginPage({
  params,
  searchParams,
}: PageProps<"/[locale]/login">) {
  const { locale } = await params;
  setRequestLocale(locale);

  const { next, status, google } = await searchParams;
  const t = await getTranslations("Auth");
  const errors = await getTranslations("Errors");

  // A Google sign-in that did not complete comes back here as a status, never as
  // a rendered error page: this endpoint is reached by a cross-site POST, and a
  // POST that renders leaves a resubmit prompt on refresh. `googleErrorCode`
  // narrows an untrusted query parameter to a closed set, so a stranger cannot
  // choose what this page says.
  const googleError = googleErrorCode(google);
  const googleMessage = googleError
    ? errors(googleError === "NETWORK" ? "network" : googleError === "GENERIC" ? "generic" : googleError)
    : null;

  return (
    <div className="w-full max-w-sm">
      <h1 className="text-2xl font-semibold tracking-tight">{t("loginTitle")}</h1>
      <p className="text-muted-foreground mt-2 text-sm">{t("loginSubtitle")}</p>

      {/* Where a completed reset lands: the account's sessions have just been
          revoked server-side, so there is nowhere to go but back through here. */}
      {status === "password-reset" ? (
        <p role="status" className="mt-4 text-sm">
          {t("passwordResetDone")}
        </p>
      ) : null}

      {googleMessage ? (
        <p role="alert" className="text-destructive mt-4 text-sm">
          {googleMessage}
        </p>
      ) : null}

      <div className="mt-8">
        <SignInForm next={typeof next === "string" ? next : undefined} />
      </div>

      <GoogleSignInButton />

      <p className="text-muted-foreground mt-6 text-sm">
        <a
          href={hrefFor(locale as Locale, "/forgot-password")}
          className="text-foreground underline underline-offset-4"
        >
          {t("forgotPassword")}
        </a>
      </p>

      <p className="text-muted-foreground mt-2 text-sm">
        {t("noAccount")}{" "}
        <a href={hrefFor(locale as Locale, "/register")} className="text-foreground underline underline-offset-4">
          {t("toRegister")}
        </a>
      </p>
    </div>
  );
}
