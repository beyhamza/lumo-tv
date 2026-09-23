import type { Metadata } from "next";
import { getTranslations, setRequestLocale } from "next-intl/server";
import { SignInForm } from "@/components/auth/SignInForm";
import { hrefFor } from "@/i18n/navigation";
import type { Locale } from "@/i18n/routing";
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

  const { next, status } = await searchParams;
  const t = await getTranslations("Auth");

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

      {/* The email form is the whole of sign-in. Google is out of 0.2.0
          (docs/backlog/dette.md §1): no button, and no script fetched. */}
      <div className="mt-8">
        <SignInForm next={typeof next === "string" ? next : undefined} />
      </div>

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
