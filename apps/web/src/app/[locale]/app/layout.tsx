import type { Metadata } from "next";
import { getTranslations, setRequestLocale } from "next-intl/server";
import { signOut } from "@/actions/auth";
import { hrefFor } from "@/i18n/navigation";
import type { Locale } from "@/i18n/routing";
import { pageMetadata } from "@/lib/seo/metadata";
import { requireSession } from "@/lib/session/session";

/**
 * The account zone: `/app/*` (docs/architecture.md §4).
 *
 * Authenticated SSR. Reading the session cookie makes every route under here
 * dynamic, which is correct — these pages show one user's own data and must
 * never be cached at a CDN.
 *
 * Two guards, on purpose. `proxy.ts` redirects an anonymous request before
 * anything renders, which is what keeps the API from being called at all; and
 * `requireSession` re-checks here, so a page under `/app` cannot render without
 * a session even if the proxy's matcher is narrowed by accident one day.
 *
 * Note the absence of a `NextIntlClientProvider`: nothing in this zone is a
 * client component yet. Sign-out is a form posting to a Server Action.
 */
export async function generateMetadata({
  params,
}: LayoutProps<"/[locale]/app">): Promise<Metadata> {
  const { locale } = await params;
  const t = await getTranslations({ locale, namespace: "App" });

  return pageMetadata({
    locale: locale as Locale,
    href: "/app",
    title: t("metaTitle"),
    description: t("overviewSubtitle"),
    index: false,
  });
}

export default async function AppLayout({
  children,
  params,
}: LayoutProps<"/[locale]/app">) {
  const { locale } = await params;
  setRequestLocale(locale);

  const session = await requireSession();
  const t = await getTranslations("App");
  const nav = await getTranslations("Nav");
  const brand = await getTranslations("Brand");

  const links = [
    { href: "/app", label: t("navOverview") },
    { href: "/app/sources", label: t("navSources") },
    { href: "/app/devices", label: t("navDevices") },
    { href: "/app/subscription", label: t("navSubscription") },
  ] as const;

  return (
    <div className="flex min-h-full flex-1 flex-col">
      <header className="border-border/60 border-b">
        <div className="mx-auto flex w-full max-w-5xl flex-wrap items-center gap-x-6 gap-y-3 px-4 py-4">
          <a href={hrefFor(locale as Locale, "/")} className="text-base font-semibold tracking-tight">
            {brand("name")}
          </a>

          <nav aria-label={t("metaTitle")} className="flex flex-wrap gap-4">
            {links.map((link) => (
              <a
                key={link.href}
                href={hrefFor(locale as Locale, link.href)}
                className="text-muted-foreground hover:text-foreground text-sm"
              >
                {link.label}
              </a>
            ))}
          </nav>

          <div className="ml-auto flex items-center gap-4">
            <span className="text-muted-foreground hidden text-sm sm:inline">
              {session.email}
            </span>
            <form action={signOut}>
              <button type="submit" className="text-sm underline underline-offset-4">
                {nav("logout")}
              </button>
            </form>
          </div>
        </div>
      </header>

      <main id="main" className="mx-auto w-full max-w-5xl flex-1 px-4 py-10">
        {children}
      </main>
    </div>
  );
}
