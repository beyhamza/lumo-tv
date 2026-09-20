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
import { currentEntryIndex } from "@/lib/navigation/current-entry";
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
 * the session.
 *
 * Note the absence of a `NextIntlClientProvider`: nothing in this shell is a
 * client component. Sign-out is a form posting to a Server Action, and so is
 * every row of the source switcher.
 *
 * <h2>Two groups of entries (US-017, S8-E03)</h2>
 *
 * The validated menu, in its order: **Home, Live, Films, Series, My library**.
 * Below it, set apart, what manages the account rather than what is watched:
 * Sources, Devices, Subscription. That second group is exactly what it was —
 * S8-06 turns it into Settings and retires the subscription entry, and doing
 * half of that here would be doing it twice.
 *
 * "My library" is the favourites page under the name the product gave it: same
 * route, because a bookmark to `/app/favorites` is somebody's, and the watch
 * list that will join it (sprint 11) is not announced by an entry that cannot
 * open it yet. For the same reason there is no search entry (sprint 10).
 *
 * Two `<nav>` elements, each with its own name: a screen reader lists landmarks,
 * and one landmark holding eight links says less than two that say what they
 * are for.
 *
 * <h2>The active source (US-018)</h2>
 *
 * Under the logotype, the source this browser is browsing and the way to change
 * it (`SourceSwitcher`); in the menu, between Home and My library, the three
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
    description: t("homeMetaDescription"),
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

  // Hrefs are localised here, once: the comparison below and the links
  // further down must be looking at the same strings.
  const entry = (href: string, label: string, exact = false) => ({
    href: hrefFor(locale as Locale, href),
    label,
    exact,
  });

  const primary = [
    // `exact`: every page of the zone is "under" `/app`, and Home is current on
    // the home page only.
    entry("/app", t("navHome"), true),
    ...catalogueLinks.map((link) => entry(link.href, link.label)),
    entry("/app/favorites", t("navLibrary")),
  ];
  const secondary = [
    entry("/app/sources", t("navSources")),
    entry("/app/devices", t("navDevices")),
    entry("/app/subscription", t("navSubscription")),
  ];

  // One current entry across both groups, not one per group and never two. The
  // active source's catalogues live under `/app/sources/…`, so a per-entry
  // prefix test would light up "Sources" alongside "Films" and a screen reader
  // would announce two current pages. The most specific entry wins
  // (`currentEntryIndex`); another source's catalogue, reached from My sources,
  // still marks "Sources", because nothing more specific matches it.
  const current = currentEntryIndex(pathname, [...primary, ...secondary]);
  const groups = [
    { label: t("navPrimaryLabel"), entries: primary, offset: 0, secondary: false },
    {
      label: t("navAccountLabel"),
      entries: secondary,
      offset: primary.length,
      secondary: true,
    },
  ];

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

        {groups.map((group) => (
          <nav
            key={group.label}
            aria-label={group.label}
            className={cn(
              "flex flex-row flex-wrap gap-1.5 md:flex-col",
              // Set apart by a rule rather than by a heading: the two names are
              // for landmarks, and a visible "Account" title over three links
              // is furniture.
              group.secondary && "border-border mt-2 border-t pt-3 md:mt-3 md:pt-4",
            )}
          >
            {group.entries.map((link, index) => {
              const isCurrent = group.offset + index === current;
              return (
                <a
                  key={link.href}
                  href={link.href}
                  aria-current={isCurrent ? "page" : undefined}
                  className={cn(
                    "flex h-11 items-center rounded-xl px-3.5 text-sm",
                    isCurrent
                      ? "bg-secondary text-foreground font-medium"
                      : "text-muted-foreground hover:bg-card hover:text-foreground",
                  )}
                >
                  {link.label}
                </a>
              );
            })}
          </nav>
        ))}

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
