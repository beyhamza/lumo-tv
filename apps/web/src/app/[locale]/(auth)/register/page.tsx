import type { Metadata } from "next";
import { getTranslations, setRequestLocale } from "next-intl/server";
import { GoogleSignInButton } from "@/components/auth/GoogleSignInButton";
import { SignUpForm } from "@/components/auth/SignUpForm";
import { hrefFor } from "@/i18n/navigation";
import type { Locale } from "@/i18n/routing";
import { pageMetadata } from "@/lib/seo/metadata";

export async function generateMetadata({
  params,
}: PageProps<"/[locale]/register">): Promise<Metadata> {
  const { locale } = await params;
  const t = await getTranslations({ locale, namespace: "Auth" });

  return pageMetadata({
    locale: locale as Locale,
    href: "/register",
    title: t("registerMetaTitle"),
    description: t("registerMetaDescription"),
    index: false,
  });
}

export default async function RegisterPage({
  params,
}: PageProps<"/[locale]/register">) {
  const { locale } = await params;
  setRequestLocale(locale);

  const t = await getTranslations("Auth");

  return (
    <div className="w-full max-w-sm">
      <h1 className="text-2xl font-semibold tracking-tight">
        {t("registerTitle")}
      </h1>
      <p className="text-muted-foreground mt-2 text-sm">
        {t("registerSubtitle")}
      </p>

      <div className="mt-8">
        <SignUpForm />
      </div>

      {/* The same control as the sign-in page, and the same flow behind it: a
          first Google sign-in creates the account, a later one reuses it, and
          the server decides which (US-03). Putting it on only one of the two
          pages would ask the visitor to answer that in advance. */}
      <GoogleSignInButton />

      <p className="text-muted-foreground mt-6 text-sm">
        {t("haveAccount")}{" "}
        <a href={hrefFor(locale as Locale, "/login")} className="text-foreground underline underline-offset-4">
          {t("toLogin")}
        </a>
      </p>
    </div>
  );
}
