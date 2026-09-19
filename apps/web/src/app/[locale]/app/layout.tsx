import type { Metadata } from "next";
import { headers } from "next/headers";
import { getTranslations, setRequestLocale } from "next-intl/server";
import { signOut } from "@/actions/auth";
import { SourceSwitcher } from "@/components/app/SourceSwitcher";
import { Wordmark } from "@/components/site/Wordmark";
import { hrefFor } from "@/i18n/navigation";
import type { Locale } from "@/i18n/routing";
import { api } from "@/lib/api/client";
import { fetched } from "@/lib/api/fetched";
import { PATHNAME_HEADER } from "@/lib/http/pathname-header";
import { pageMetadata } from "@/lib/seo/metadata";
import { requireSession } from "@/lib/session/session";
import { loadActiveSource } from "@/lib/sources/active-source-store";
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
 * client component. Sign-out is a form posting to a Server Action, and so is
 * every row of the source switcher.
 *
 * <h2>The active source (US-018)</h2>
 *
 * Under the logotype, the source this browser is browsing and the way to change
 * it (`SourceSwitcher`); in the rail, above the account's sections, the three
 * catalogues **of that source**. They are absent whenever no source is selected
 * — none registered, a choice still owed, or an API that did not answer — because
 * a "Films" entry has to lead to somebody's films, and a link built from a
 * remembered id that was not confirmed by `GET /sources` is a link to a source
 * that may have been deleted on another device.
 *
 * The catalogue routes themselves still answer for any source the account owns:
 * My sources links to them, and opening one neither redirects nor changes the
 * choice. A GET that rewrote a preference would be a preference changed by a
 * prefetch.
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
  //
  // The active source is asked for alongside it rather than after it: the two
  // are independent, and the rail should not cost two round trips in a row.
  // Resolved once per request and shared with the pages that need it
  // (`loadActiveSource` is memoised): the rail and, say, Favourites must not
  // disagree about which source is active.
  const [me, activeSource] = await Promise.all([
    fetched(() => api(session.accessToken).GET("/me", {})),
    loadActiveSource(session.accessToken, session.userId),
  ]);
  const displayName =
    (me.state === "ok" ? me.data.display_name : null) ?? t("accountCardTitle");
  const initial = (
    (me.state === "ok" && me.data.display_name) ||
    session.email
  )
    .trim()
    .charAt(0)
    .toUpperCase();

  const activeId = activeSource.state === "selected" ? activeSource.source.id : null;

  // Always the three, whatever the source holds — the rule `CatalogueTabs`
  // documents, learned the hard way: a hidden "Films" was read as a missing
  // feature. A playlist has no series and its series page says so in the list,
  // which an absent entry cannot.
  const catalogueLinks = activeId
    ? [
        { href: `/app/sources/${activeId}/channels`, label: t("navLive") },
        { href: `/app/sources/${activeId}/vod`, label: t("navFilms") },
        { href: `/app/sources/${activeId}/series`, label: t("navSeries") },
      ]
    : [];

  const accountLinks = [
    { href: "/app/sources", label: t("navSources") },
    { href: "/app/favorites", label: t("navFavorites") },
    { href: "/app/devices", label: t("navDevices") },
    { href: "/app/subscription", label: t("navSubscription") },
  ];

  const isUnder = (href: string) => {
    const full = hrefFor(locale as Locale, href);
    return pathname === full || pathname.startsWith(`${full}/`);
  };

  // One current entry, not two. The active source's catalogues live under
  // `/app/sources/…`, so without this "Sources" would light up alongside
  // "Films" and a screen reader would announce two current pages. Another
  // source's catalogue, reached from My sources, still marks "Sources".
  const inActiveCatalogue = catalogueLinks.some((link) => isUnder(link.href));
  const links = [...catalogueLinks, ...accountLinks].map((link) => ({
    ...link,
    current:
      isUnder(link.href) && !(inActiveCatalogue && link.href === "/app/sources"),
  }));

  return (
    <div className="dark bg-background text-foreground flex min-h-full flex-1 flex-col md:flex-row">
      <aside className="border-border flex flex-none flex-col gap-1.5 border-b px-5 py-7 md:w-[250px] md:border-r md:border-b-0">
        <a href={hrefFor(locale as Locale, "/app")} className="inline-flex px-3 pb-5">
          <Wordmark />
        </a>

        <SourceSwitcher
          view={activeSource}
          locale={locale as Locale}
          pathname={pathname}
        />

        <nav aria-label={t("metaTitle")} className="flex flex-row flex-wrap gap-1.5 md:flex-col">
          {links.map(({ current, ...link }) => {
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
