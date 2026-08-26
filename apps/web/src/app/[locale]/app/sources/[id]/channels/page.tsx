import type { Metadata } from "next";
import { NextIntlClientProvider } from "next-intl";
import { getMessages, getTranslations, setRequestLocale } from "next-intl/server";
import { ChannelPlayer } from "@/components/app/ChannelPlayer";
import { Unavailable } from "@/components/app/Unavailable";
import { hrefFor } from "@/i18n/navigation";
import type { Locale } from "@/i18n/routing";
import { api, problemCode } from "@/lib/api/client";
import type { Category, Channel } from "@/lib/api/types";
import { pageMetadata } from "@/lib/seo/metadata";
import { requireSession } from "@/lib/session/session";

/**
 * The channels of one source (US-08).
 *
 * <h2>Everything that filters is a link, and everything that searches is a GET</h2>
 *
 * Category, page and query all live in the URL. That is not a preference: it is
 * what makes this screen work without JavaScript, shareable, and correct when
 * the browser's back button is pressed. A client-side grid with its own state
 * would lose all three.
 *
 * <h2>The catalogue is large</h2>
 *
 * Fifteen thousand channels is an ordinary source. The page size is capped
 * server-side and the pagination is server-side too — nothing here holds a full
 * catalogue in memory, on either end.
 *
 * <h2>Two lists side by side</h2>
 *
 * The categories are a `<nav>` of links and the channels are a list of rows, so
 * both answer to `listitem`. The channel list therefore carries an accessible
 * name: assistive technology needs it to announce which list is which, and it is
 * the same name the end-to-end suite scopes its assertions with — the first
 * version of those expected two rows and counted six.
 */
export async function generateMetadata({
  params,
}: PageProps<"/[locale]/app/sources/[id]/channels">): Promise<Metadata> {
  const { locale } = await params;
  const t = await getTranslations({ locale, namespace: "App" });

  return pageMetadata({
    locale: locale as Locale,
    href: "/app/sources",
    title: t("catalogueTitle"),
    description: t("sourcesSubtitle"),
    index: false,
  });
}

/** Matches the contract's default. The server caps anything larger at 200. */
const PAGE_SIZE = 50;

export default async function ChannelsPage({
  params,
  searchParams,
}: PageProps<"/[locale]/app/sources/[id]/channels">) {
  const { locale, id } = await params;
  const query = await searchParams;
  setRequestLocale(locale);

  const session = await requireSession();
  const t = await getTranslations("App");
  const tErrors = await getTranslations("Errors");

  const categoryId = single(query.categoryId);
  const search = single(query.q);
  const page = Math.max(0, Number.parseInt(single(query.page) ?? "0", 10) || 0);
  const playing = single(query.play);

  // Not through `fetched()`: this screen has to tell three failures apart, and
  // that helper deliberately collapses everything that is not an unrouted 404
  // into "unavailable". A source still importing is not an outage.
  const [categories, channels] = await Promise.all([
    api(session.accessToken).GET("/sources/{id}/categories", {
      params: { path: { id }, query: { contentType: "LIVE" } },
    }),
    api(session.accessToken).GET("/sources/{id}/channels", {
      params: {
        path: { id },
        query: {
          ...(categoryId ? { categoryId } : {}),
          ...(search ? { q: search } : {}),
          page,
          size: PAGE_SIZE,
        },
      },
    }),
  ]);

  const failure = problemCode(channels.error) ?? problemCode(categories.error);

  if (failure === "SOURCE_NOT_READY") {
    return (
      <NotReady
        id={id}
        locale={locale as Locale}
        title={t("catalogueNotReady")}
        link={t("catalogueNotReadyLink")}
      />
    );
  }
  if (failure) {
    // Includes SOURCE_NOT_FOUND. The message comes from the code, never from an
    // HTTP status.
    return (
      <p role="alert" className="text-destructive text-sm">
        {tErrors(failure as never)}
      </p>
    );
  }
  if (!channels.data || !categories.data) {
    return <Unavailable />;
  }

  const totalPages = channels.data.total_pages;
  const messages = await getMessages();
  // Named from the row that was clicked rather than from a lookup: the contract
  // has no endpoint returning one channel by id, and inventing one to fill a
  // heading would be inventing an endpoint (AGENTS.md §3). The channel is on the
  // page the link was built from, so its name is already in hand.
  const nowPlaying = playing
    ? channels.data.items.find((channel) => channel.id === playing)
    : undefined;

  return (
    <div>
      <p className="text-sm">
        <a
          href={hrefFor(locale as Locale, `/app/sources/${id}`)}
          className="text-muted-foreground underline underline-offset-4"
        >
          {t("catalogueBackToSource")}
        </a>
      </p>

      <h1 className="mt-4 text-2xl font-semibold tracking-tight">
        {t("catalogueTitle")}
      </h1>
      <p className="text-muted-foreground mt-2">
        {t("catalogueCount", { total: channels.data.total_elements })}
      </p>

      {/* A plain GET form. Submitting it changes the URL, which is what every
          other control on this page does too. `page` is deliberately absent:
          a new search starts at the first page. */}
      <form method="get" className="mt-6 flex flex-wrap items-end gap-3">
        {categoryId ? (
          <input type="hidden" name="categoryId" value={categoryId} />
        ) : null}
        <div className="space-y-1.5">
          <label htmlFor="q" className="text-sm font-medium">
            {t("catalogueSearchLabel")}
          </label>
          <input
            id="q"
            name="q"
            type="search"
            defaultValue={search ?? ""}
            className="border-input bg-background h-9 rounded-lg border px-3 text-sm"
          />
        </div>
        <button
          type="submit"
          className="bg-secondary text-secondary-foreground h-9 rounded-lg px-4 text-sm font-medium"
        >
          {t("catalogueSearchSubmit")}
        </button>
        {/* Said plainly, because the server does a case-insensitive substring
            match and nothing more. Promising tolerance for typos would be a
            promise the query does not keep. */}
        <p className="text-muted-foreground w-full text-sm">
          {t("catalogueSearchHint")}
        </p>
      </form>

      <div className="mt-8 grid gap-8 md:grid-cols-[14rem_1fr]">
        <CategoryList
          categories={categories.data.items}
          activeId={categoryId}
          sourceId={id}
          locale={locale as Locale}
          search={search}
          allLabel={t("catalogueAllCategories")}
        />

        <div>
          {nowPlaying ? (
            <NextIntlClientProvider
              messages={{ App: messages.App, Errors: messages.Errors }}
            >
              <ChannelPlayer
                channelId={nowPlaying.id}
                name={nowPlaying.name}
                quality={nowPlaying.quality}
              />
            </NextIntlClientProvider>
          ) : null}

          {channels.data.items.length === 0 ? (
            <div className="border-border rounded-xl border border-dashed px-5 py-6">
              <p className="font-medium">
                {search ? t("catalogueNoResults") : t("catalogueEmpty")}
              </p>
              <p className="text-muted-foreground mt-1 text-sm">
                {search ? t("catalogueNoResultsHint") : t("catalogueEmptyHint")}
              </p>
            </div>
          ) : (
            <ul aria-label={t("catalogueTitle")} className="space-y-2">
              {channels.data.items.map((channel) => (
                <ChannelRow
                  key={channel.id}
                  channel={channel}
                  playing={channel.id === playing}
                  href={hrefFor(
                    locale as Locale,
                    `/app/sources/${id}/channels${queryString({
                      categoryId,
                      q: search,
                      page: page > 0 ? String(page) : undefined,
                      play: channel.id,
                    })}`,
                  )}
                />
              ))}
            </ul>
          )}

          {totalPages > 1 ? (
            <Pagination
              page={page}
              totalPages={totalPages}
              sourceId={id}
              locale={locale as Locale}
              categoryId={categoryId}
              search={search}
              previousLabel={t("cataloguePrevious")}
              nextLabel={t("catalogueNext")}
              positionLabel={t("cataloguePageOf", {
                page: page + 1,
                total: totalPages,
              })}
            />
          ) : null}
        </div>
      </div>
    </div>
  );
}

function NotReady({
  id,
  locale,
  title,
  link,
}: {
  id: string;
  locale: Locale;
  title: string;
  link: string;
}) {
  return (
    <div className="border-border rounded-xl border border-dashed px-5 py-6">
      <p className="font-medium">{title}</p>
      <p className="mt-3">
        <a
          href={hrefFor(locale, `/app/sources/${id}`)}
          className="text-sm underline underline-offset-4"
        >
          {link}
        </a>
      </p>
    </div>
  );
}

function CategoryList({
  categories,
  activeId,
  sourceId,
  locale,
  search,
  allLabel,
}: {
  categories: Category[];
  activeId?: string;
  sourceId: string;
  locale: Locale;
  search?: string;
  allLabel: string;
}) {
  const link = (categoryId?: string) =>
    hrefFor(
      locale,
      `/app/sources/${sourceId}/channels${queryString({ categoryId, q: search })}`,
    );

  return (
    <nav aria-label={allLabel}>
      <ul className="space-y-1">
        <li>
          <a
            href={link()}
            className={`block rounded-lg px-3 py-2 text-sm ${
              activeId ? "text-muted-foreground hover:bg-secondary/60" : "bg-secondary font-medium"
            }`}
          >
            {allLabel}
          </a>
        </li>
        {categories.map((category) => (
          <li key={category.id}>
            <a
              href={link(category.id)}
              className={`flex items-center justify-between rounded-lg px-3 py-2 text-sm ${
                activeId === category.id
                  ? "bg-secondary font-medium"
                  : "text-muted-foreground hover:bg-secondary/60"
              }`}
            >
              <span>{category.name}</span>
              {category.channel_count != null ? (
                <span className="text-muted-foreground text-xs">
                  {category.channel_count}
                </span>
              ) : null}
            </a>
          </li>
        ))}
      </ul>
    </nav>
  );
}

/**
 * One channel.
 *
 * `number` is the provider's, and it is not `position` — that one is a display
 * index reassigned at every ingestion. This is the number the user knows by
 * heart, and it is null for the many playlists that carry none.
 *
 * `quality` is echoed exactly as the source wrote it. A badge reading `fhd` in
 * lower case is a source that wrote `fhd`, and normalising it here would be this
 * layer deciding what the provider meant.
 */
function ChannelRow({
  channel,
  href,
  playing,
}: {
  channel: Channel;
  href: string;
  playing: boolean;
}) {
  return (
    <li
      className={`flex items-center gap-3 rounded-xl border px-4 py-3 ${
        playing ? "border-primary bg-primary/5" : "border-border"
      }`}
    >
      <span className="text-muted-foreground w-10 shrink-0 text-right text-sm tabular-nums">
        {channel.number ?? ""}
      </span>

      <Logo channel={channel} />

      {/* A link, so playing a channel is a URL like every other state on this
          page: it survives a reload, it can be shared, and the back button
          closes the player. */}
      <a href={href} className="min-w-0 flex-1 truncate font-medium underline-offset-4 hover:underline">
        {channel.name}
      </a>

      {channel.quality ? (
        <span className="border-border text-muted-foreground shrink-0 rounded border px-1.5 py-0.5 text-xs">
          {channel.quality}
        </span>
      ) : null}
    </li>
  );
}

/**
 * The logo the user's own playlist advertises, or nothing.
 *
 * <b>Lumo ships no fallback artwork</b> (AGENTS.md §1): a channel with no logo
 * gets its initial, never a bundled image of ours.
 *
 * A plain `<img>`, deliberately, and not `next/image`. The optimiser would fetch
 * every provider logo through our own server, which is the same posture question
 * as relaying a stream (ADR 0007) for a far smaller benefit. The cost of the
 * plain tag is that a logo served over `http` will not load on an `https` page —
 * which is honest: it is the provider's choice, and the initial takes its place.
 */
function Logo({ channel }: { channel: Channel }) {
  if (!channel.logo_url) {
    return (
      <span className="bg-secondary text-muted-foreground flex h-8 w-8 shrink-0 items-center justify-center rounded text-xs">
        {channel.name.slice(0, 1).toUpperCase()}
      </span>
    );
  }

  return (
    // eslint-disable-next-line @next/next/no-img-element
    <img
      src={channel.logo_url}
      alt=""
      width={32}
      height={32}
      loading="lazy"
      decoding="async"
      // The provider learns nothing about which of their channels is being
      // looked at from which page.
      referrerPolicy="no-referrer"
      className="h-8 w-8 shrink-0 rounded object-contain"
    />
  );
}

function Pagination({
  page,
  totalPages,
  sourceId,
  locale,
  categoryId,
  search,
  previousLabel,
  nextLabel,
  positionLabel,
}: {
  page: number;
  totalPages: number;
  sourceId: string;
  locale: Locale;
  categoryId?: string;
  search?: string;
  previousLabel: string;
  nextLabel: string;
  positionLabel: string;
}) {
  const link = (target: number) =>
    hrefFor(
      locale,
      `/app/sources/${sourceId}/channels${queryString({
        categoryId,
        q: search,
        page: target > 0 ? String(target) : undefined,
      })}`,
    );

  return (
    <nav className="mt-6 flex items-center gap-4 text-sm" aria-label={positionLabel}>
      {page > 0 ? (
        <a href={link(page - 1)} className="underline underline-offset-4">
          {previousLabel}
        </a>
      ) : null}
      <span className="text-muted-foreground">{positionLabel}</span>
      {page + 1 < totalPages ? (
        <a href={link(page + 1)} className="underline underline-offset-4">
          {nextLabel}
        </a>
      ) : null}
    </nav>
  );
}

/** `?a=1&b=2`, or an empty string. Absent values are omitted, never sent empty. */
function queryString(values: Record<string, string | undefined>): string {
  const params = new URLSearchParams();
  for (const [key, value] of Object.entries(values)) {
    if (value) params.set(key, value);
  }
  const encoded = params.toString();
  return encoded ? `?${encoded}` : "";
}

/** `searchParams` hands back a string, an array, or nothing. Only the first matters. */
function single(value: string | string[] | undefined): string | undefined {
  return Array.isArray(value) ? value[0] : value;
}
