import type { Metadata } from "next";
import { NextIntlClientProvider } from "next-intl";
import { getMessages, getTranslations, setRequestLocale } from "next-intl/server";
import { AddSourceForm } from "@/components/app/AddSourceForm";
import { hrefFor } from "@/i18n/navigation";
import type { Locale } from "@/i18n/routing";
import { pageMetadata } from "@/lib/seo/metadata";
import { requireSession } from "@/lib/session/session";

/**
 * Registering a source (US-06, US-07).
 *
 * The first page of the account zone with a client component under it, so it is
 * the first to mount a `NextIntlClientProvider` — and it mounts it **here**
 * rather than in `app/layout.tsx`. A provider on the layout would put a client
 * boundary above every account page, including the four that ship no client
 * JavaScript at all.
 *
 * Two namespaces, and no more: `App` for the labels, `Errors` for what the form
 * renders when the API refuses.
 */
export async function generateMetadata({
  params,
}: PageProps<"/[locale]/app/sources/new">): Promise<Metadata> {
  const { locale } = await params;
  const t = await getTranslations({ locale, namespace: "App" });

  return pageMetadata({
    locale: locale as Locale,
    href: "/app/sources/new",
    title: t("sourceAddTitle"),
    description: t("sourceAddSubtitle"),
    index: false,
  });
}

export default async function NewSourcePage({
  params,
}: PageProps<"/[locale]/app/sources/new">) {
  const { locale } = await params;
  setRequestLocale(locale);

  // The layout guards this too. Repeated here because this page reaches the API
  // with the caller's token, and a page that does that must not depend on a
  // parent having checked.
  await requireSession();

  const t = await getTranslations("App");
  const messages = await getMessages();

  return (
    <div className="max-w-xl">
      <h1 className="text-2xl font-semibold tracking-tight">{t("sourceAddTitle")}</h1>
      <p className="text-muted-foreground mt-2">{t("sourceAddSubtitle")}</p>

      <div className="mt-8">
        <NextIntlClientProvider
          messages={{ App: messages.App, Errors: messages.Errors }}
        >
          <AddSourceForm />
        </NextIntlClientProvider>
      </div>

      <p className="mt-6 text-sm">
        <a
          href={hrefFor(locale as Locale, "/app/sources")}
          className="text-muted-foreground underline underline-offset-4"
        >
          {t("sourceAddCancel")}
        </a>
      </p>
    </div>
  );
}
