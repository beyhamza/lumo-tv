import { NextIntlClientProvider } from "next-intl";
import { getMessages, getTranslations, setRequestLocale } from "next-intl/server";
import { hrefFor } from "@/i18n/navigation";
import type { Locale } from "@/i18n/routing";

/**
 * The sign-in and registration zone.
 *
 * Rendered dynamically: the forms are client components driven by Server
 * Actions, and the actions set a cookie. Nothing here is worth prerendering
 * anyway — a sign-in form has no SEO value and `pageMetadata({index: false})`
 * keeps it out of the index.
 *
 * This is where a `NextIntlClientProvider` first appears, and it carries only
 * the `Auth` namespace. Mounting it at the root would push every message of the
 * active locale into the bundle of every page, including the prerendered
 * marketing ones — a page that ships translations it never renders is paying
 * for someone else's strings.
 */
export default async function AuthLayout({
  children,
  params,
}: LayoutProps<"/[locale]">) {
  const { locale } = await params;
  setRequestLocale(locale);

  const messages = await getMessages();
  const brand = await getTranslations("Brand");

  return (
    <div className="dark bg-background text-foreground flex min-h-full flex-1 flex-col">
      <header className="border-border/60 border-b">
        <div className="mx-auto w-full max-w-5xl px-4 py-4">
          <a href={hrefFor(locale as Locale, "/")} className="text-base font-semibold tracking-tight">
            {brand("name")}
          </a>
        </div>
      </header>

      <main id="main" className="flex flex-1 items-center justify-center px-4 py-12">
        <NextIntlClientProvider messages={{ Auth: messages.Auth }}>
          {children}
        </NextIntlClientProvider>
      </main>
    </div>
  );
}
