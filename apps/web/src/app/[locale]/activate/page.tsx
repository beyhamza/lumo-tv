import type { Metadata } from "next";
import { getTranslations, setRequestLocale } from "next-intl/server";
import { approveDeviceCode } from "@/actions/activate";
import { MockBadge } from "@/components/site/MockBadge";
import { Wordmark } from "@/components/site/Wordmark";
import { hrefFor } from "@/i18n/navigation";
import type { Locale } from "@/i18n/routing";
import { normaliseActivationCode } from "@/lib/activation/code";
import { errorMessage } from "@/lib/api/error-message";
import { pageMetadata } from "@/lib/seo/metadata";
import { getSession } from "@/lib/session/session";
import { cn } from "@/lib/utils";

/**
 * TV activation (US-05, W4 in docs/design/web-sprint-1.md) — the third
 * rendering zone, and the most constrained.
 *
 * docs/architecture.md §4 calls it "SSR minimal", and the reason is the
 * situation it is used in: standing up, phone in hand, in front of a television
 * showing an eight-character code. So:
 *
 * - **Zero client components.** The form posts to a Server Action, the result
 *   comes back as a redirect with a query parameter. No hydration, no
 *   JavaScript required — the page works on a phone with a flaky connection, or
 *   with scripting disabled entirely.
 * - **One field, eight cells.** The mock-up's 4 + 4 boxes are drawn under a
 *   single `<input>` whose letter-spacing puts one character per box: what
 *   looks like eight fields is one, so the caret advances by itself, `?code=`
 *   prefills it, and a paste lands whole. The grouping is CSS, not a controller
 *   (the specification is explicit on that), which is also why the separator is
 *   a drawn dash rather than a typed one.
 * - **Readable at arm's length.** 30 px monospace, so a `0` and an `O` are told
 *   apart — even though the server's alphabet excludes both, what the user
 *   types is unconstrained.
 *
 * Dark from edge to edge: the `.dark` scope sits on the element that fills the
 * viewport, because a dark card in the middle of a light body is the light
 * strip docs/design/design-system.md warns about.
 *
 * Rendered dynamically because it reads the session cookie, and `noindex`
 * because an activation URL carries a single-use code.
 */
export async function generateMetadata({
  params,
}: PageProps<"/[locale]/activate">): Promise<Metadata> {
  const { locale } = await params;
  const t = await getTranslations({ locale, namespace: "Activate" });

  return pageMetadata({
    locale: locale as Locale,
    href: "/activate",
    title: t("metaTitle"),
    description: t("metaDescription"),
    index: false,
  });
}

export default async function ActivatePage({
  params,
  searchParams,
}: PageProps<"/[locale]/activate">) {
  const { locale } = await params;
  setRequestLocale(locale);

  const query = await searchParams;
  const code = typeof query.code === "string" ? query.code : "";
  const status = typeof query.status === "string" ? query.status : undefined;
  const error = typeof query.error === "string" ? query.error : undefined;

  const t = await getTranslations("Activate");
  const errors = await getTranslations("Errors");
  const session = await getSession();

  return (
    <div className="dark bg-background text-foreground flex min-h-full w-full flex-1 flex-col items-center justify-center px-4 py-10">
      <main
        id="main"
        className="border-border flex w-full max-w-[420px] flex-col items-center gap-7 rounded-xl border px-5 py-12 text-center"
      >
        {status === "ok" ? (
          <Success locale={locale as Locale} t={t} />
        ) : error ? (
          <Failure code={code} error={error} locale={locale as Locale} t={t} errors={errors} />
        ) : (
          <>
            <a href={hrefFor(locale as Locale, "/")} className="inline-flex">
              <Wordmark size="lg" />
            </a>
            <h1 className="text-2xl leading-tight font-semibold text-balance">{t("title")}</h1>

            {session ? (
              <form action={approveDeviceCode} className="flex w-full flex-col items-center gap-7">
                <div className="flex w-full flex-col items-center gap-4">
                  <label htmlFor="user_code" className="sr-only">
                    {t("codeLabel")}
                  </label>
                  <CodeField code={code} />
                  <p id="code-hint" className="text-muted-foreground text-sm leading-normal">
                    {t("codeHint")}
                  </p>
                </div>

                {/* A plain button, not the shared one: this page keeps its
                    dependency surface at zero so it cannot acquire a client
                    component by accident. */}
                <button
                  type="submit"
                  className="bg-primary text-primary-foreground inline-flex h-12 items-center justify-center rounded-full px-7 text-sm font-semibold"
                >
                  {t("submit")}
                </button>
              </form>
            ) : (
              <div className="flex flex-col items-center gap-5">
                <p className="text-muted-foreground text-[15px] leading-relaxed">
                  {t("signInFirst")}
                </p>
                <a
                  href={hrefFor(
                    locale as Locale,
                    `/login?next=${encodeURIComponent(`/activate?code=${code}`)}`,
                  )}
                  className="bg-primary text-primary-foreground inline-flex h-12 items-center justify-center rounded-full px-7 text-sm font-semibold"
                >
                  {t("signInCta")}
                </a>
              </div>
            )}

            <section className="mt-2">
              <h2 className="text-sm font-medium">{t("helpTitle")}</h2>
              <p className="text-muted-foreground mt-1.5 text-[13px] leading-normal">{t("helpBody")}</p>
            </section>
          </>
        )}
      </main>
    </div>
  );
}

/**
 * The eight cells, drawn under one input.
 *
 * `--pitch` is the distance from one character to the next: 46 px on a phone
 * held upright, less when the card is narrower than that (container units, so
 * it follows the card and not the viewport). The input's letter-spacing is
 * `pitch − 1ch`, which puts each glyph in the middle of its cell whatever the
 * exact advance width of the monospace face turns out to be.
 */
function CodeField({ code }: { code: string }) {
  return (
    <div className="@container w-full max-w-[368px]">
      <div className="relative h-[60px] w-[calc(var(--pitch)*8)] [--pitch:min(46px,12.5cqw)]">
        <Cells />
        <input
          id="user_code"
          name="user_code"
          defaultValue={code}
          autoFocus
          required
          // The code is alphanumeric and case-insensitive; the browser's own
          // helpers only get in the way here.
          autoComplete="off"
          autoCapitalize="characters"
          autoCorrect="off"
          spellCheck={false}
          inputMode="text"
          enterKeyHint="go"
          maxLength={8}
          aria-describedby="code-hint"
          className="text-foreground caret-brand-cyan absolute inset-0 h-full w-full rounded-[10px] border-0 bg-transparent pl-[calc((var(--pitch)-1ch)/2)] font-mono text-[30px] tracking-[calc(var(--pitch)-1ch)] uppercase outline-none focus-visible:outline-2 focus-visible:outline-offset-3 focus-visible:outline-ring"
        />
      </div>
    </div>
  );
}

/** The boxes and the dash between the two groups. Decorative: the input is the control. */
function Cells({ value, danger }: { value?: string; danger?: boolean }) {
  return (
    <div aria-hidden="true" className="absolute inset-0 flex gap-2">
      {Array.from({ length: 8 }, (_, i) => (
        <span
          key={i}
          className={cn(
            "flex h-[60px] w-[calc(var(--pitch)-8px)] flex-none items-center justify-center rounded-[10px] border font-mono text-[30px]",
            danger ? "border-destructive bg-destructive/10" : "border-border bg-card",
            i >= 4 && !value && "bg-card/60",
          )}
        >
          {value?.[i] ?? ""}
        </span>
      ))}
      <span className="bg-muted-foreground/60 absolute top-1/2 left-[calc(var(--pitch)*4-8px)] h-0.5 w-2 -translate-y-1/2 rounded-full" />
    </div>
  );
}

/**
 * State 2 — the television is linked.
 *
 * The specification wants the device named here ("TV du salon"). The API now
 * returns it (`DeviceApproval`, G4), but carrying it through a redirect query
 * parameter is the wrong path, so this page still says it generically — and
 * says so.
 */
function Success({ locale, t }: { locale: Locale; t: Translate }) {
  return (
    <>
      <span
        aria-hidden="true"
        className="bg-brand-orb text-primary-foreground flex size-20 items-center justify-center rounded-full text-[32px] font-bold"
      >
        ✓
      </span>
      <h1 className="text-[26px] font-semibold">{t("successTitle")}</h1>
      <p role="status" className="text-muted-foreground text-[15px] leading-relaxed">
        {t("success")} <MockBadge className="align-middle" />
      </p>
      <a
        href={hrefFor(locale, "/app/devices")}
        className="bg-secondary text-foreground inline-flex h-12 items-center rounded-full px-6 text-sm"
      >
        {t("successCta")}
      </a>
    </>
  );
}

/**
 * State 3 — the code was refused.
 *
 * The cells turn `danger` and keep what was typed, so the user can compare with
 * the television; the way out is a filled action, not a discreet link.
 */
function Failure({
  code,
  error,
  locale,
  t,
  errors,
}: {
  code: string;
  error: string;
  locale: Locale;
  t: Translate;
  errors: Parameters<typeof errorMessage>[1];
}) {
  const titleKey =
    error === "DEVICE_CODE_EXPIRED"
      ? "errorTitleExpired"
      : error === "DEVICE_CODE_NOT_FOUND"
        ? "errorTitleNotFound"
        : error === "DEVICE_CODE_ALREADY_USED"
          ? "errorTitleUsed"
          : error === "RATE_LIMITED"
            ? "errorTitleRateLimited"
            : "errorTitleGeneric";

  return (
    <>
      <div className="@container w-full max-w-[368px]">
        <div className="relative h-[60px] w-[calc(var(--pitch)*8)] [--pitch:min(46px,12.5cqw)]">
          <Cells value={normaliseActivationCode(code)} danger />
          <span className="sr-only">{normaliseActivationCode(code)}</span>
        </div>
      </div>
      <h1 role="alert" className="text-destructive text-base font-semibold">
        {t(titleKey)}
      </h1>
      <p className="text-muted-foreground text-sm leading-relaxed">{errorMessage(error, errors)}</p>
      <a
        href={hrefFor(locale, "/activate")}
        className="bg-primary text-primary-foreground inline-flex h-12 items-center rounded-full px-7 text-sm font-semibold"
      >
        {t("errorCta")}
      </a>
    </>
  );
}

type Translate = Awaited<ReturnType<typeof getTranslations<"Activate">>>;
