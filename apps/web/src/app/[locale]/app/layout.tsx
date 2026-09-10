import type { Metadata } from "next";
import { headers } from "next/headers";
import { getTranslations, setRequestLocale } from "next-intl/server";
import { signOut } from "@/actions/auth";
import { Wordmark } from "@/components/site/Wordmark";
import { hrefFor } from "@/i18n/navigation";
import type { Locale } from "@/i18n/routing";
import { api } from "@/lib/api/client";
import { fetched } from "@/lib/api/fetched";
import { PATHNAME_HEADER } from "@/lib/http/pathname-header";
import { pageMetadata } from "@/lib/seo/metadata";
import { requireSession } from "@/lib/session/session";
import { cn } from "@/lib/utils";

/**
 * The account zone: `/app/*` (docs/architecture.md §4, W3 in
 * docs/design/web-sprint-1.md).
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
 * The shell is the mock-up's: a 250 px rail with the logotype, the sections,
 * and the account card at the bottom — gradient disc with the initial, display
 * name, truncated email. The name comes from `GET /me`; the email is already in
 * the session. Favourites is not on the mock-up but the page exists, so it
 * stays in the rail rather than becoming unreachable.
 *
 * Note the absence of a `NextIntlClientProvider`: nothing in this shell is a
 * client component. Sign-out is a form posting to a Server Action.
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
  const pathname = (await headers()).get(PATHNAME_HEADER) ?? "";

  // The display name, and nothing else from the profile. `fetched` keeps an
  // API outage from taking the whole shell down: the card falls back to the
  // session's email, which is what it would show for a user with no name.
  const me = await fetched(() => api(session.accessToken).GET("/me", {}));
  const displayName =
    (me.state === "ok" ? me.data.display_name : null) ?? t("accountCardTitle");
  const initial = (
    (me.state === "ok" && me.data.display_name) ||
    session.email
  )
    .trim()
    .charAt(0)
    .toUpperCase();

  const links = [
    { href: "/app/sources", label: t("navSources") },
    { href: "/app/favorites", label: t("navFavorites") },
    { href: "/app/devices", label: t("navDevices") },
    { href: "/app/subscription", label: t("navSubscription") },
  ] as const;

  const isCurrent = (href: string) => {
    const full = hrefFor(locale as Locale, href);
    return pathname === full || pathname.startsWith(`${full}/`);
  };

  return (
    <div className="dark bg-background text-foreground flex min-h-full flex-1 flex-col md:flex-row">
      <aside className="border-border flex flex-none flex-col gap-1.5 border-b px-5 py-7 md:w-[250px] md:border-r md:border-b-0">
        <a href={hrefFor(locale as Locale, "/app")} className="inline-flex px-3 pb-5">
          <Wordmark />
        </a>

        <nav aria-label={t("metaTitle")} className="flex flex-row flex-wrap gap-1.5 md:flex-col">
          {links.map((link) => {
            const current = isCurrent(link.href);
            return (
              <a
                key={link.href}
                href={hrefFor(locale as Locale, link.href)}
                aria-current={current ? "page" : undefined}
                className={cn(
                  "flex h-11 items-center rounded-xl px-3.5 text-sm",
                  current
                    ? "bg-secondary text-foreground font-medium"
                    : "text-muted-foreground hover:bg-card hover:text-foreground",
                )}
              >
                {link.label}
              </a>
            );
          })}
        </nav>

        <div className="bg-card mt-4 flex items-center gap-2.5 rounded-xl px-3.5 py-3 md:mt-auto">
          <span
            aria-hidden="true"
            className="bg-brand-orb text-primary-foreground flex size-8 flex-none items-center justify-center rounded-full text-[13px] font-bold"
          >
            {initial}
          </span>
          <div className="min-w-0 flex-1">
            <p className="truncate text-xs font-medium">{displayName}</p>
            <p className="text-muted-foreground/80 truncate text-[11px]">{session.email}</p>
          </div>
          <form action={signOut} className="flex-none">
            <button
              type="submit"
              className="text-muted-foreground hover:text-foreground text-[11px] underline underline-offset-4"
            >
              {nav("logout")}
            </button>
          </form>
        </div>
      </aside>

      <main id="main" className="min-w-0 flex-1 px-5 py-8 sm:px-8 lg:px-11 lg:py-9">
        {children}
      </main>
    </div>
  );
}
