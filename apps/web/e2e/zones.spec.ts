import { expect, test } from "@playwright/test";

/**
 * The three zones, tested for the properties that define them
 * (docs/architecture.md §4) rather than for their copy.
 *
 * The browsers are not installed by `pnpm install`. Once, per machine:
 *
 *     pnpm exec playwright install chromium
 *
 * CI installs them in the same step, so these run on every pull request that
 * touches apps/web.
 */

test.describe("marketing", () => {
  test("the root sends a French browser to /fr and an English one to /en", async ({
    browser,
  }) => {
    // This asserted /fr unconditionally and failed the first time it was ever
    // run, against a Chromium that asks for English. The application was right:
    // routing.ts sets localeDetection, so `/` negotiates. Pinning the default
    // here would have meant testing the opposite of what the product does.
    for (const [locale, expected] of [
      ["fr-FR", /\/fr$/],
      ["en-US", /\/en$/],
    ] as const) {
      const context = await browser.newContext({ locale });
      const page = await context.newPage();

      await page.goto("/");
      await expect(page).toHaveURL(expected);

      await context.close();
    }
  });

  test("a locale already in the URL beats the browser's preference", async ({
    browser,
  }) => {
    // The other half of the same rule, and the one that matters in practice: a
    // link written in French opens in French for whoever it was sent to.
    const context = await browser.newContext({ locale: "en-US" });
    const page = await context.newPage();

    await page.goto("/fr/guides");
    await expect(page).toHaveURL(/\/fr\/guides$/);
    await expect(page.locator("html")).toHaveAttribute("lang", "fr");

    await context.close();
  });

  test("the landing page carries its canonical and its alternates", async ({ page }) => {
    await page.goto("/fr");

    await expect(page.locator('link[rel="canonical"]')).toHaveCount(1);
    await expect(page.locator('link[rel="alternate"][hreflang="en"]')).toHaveCount(1);
    await expect(
      page.locator('link[rel="alternate"][hreflang="x-default"]'),
    ).toHaveCount(1);
  });

  test("the guides are reachable and translated", async ({ page }) => {
    await page.goto("/en/guides");
    await page.getByRole("link", { name: /M3U playlist/i }).first().click();
    await expect(page.getByRole("heading", { level: 1 })).toBeVisible();
  });

  test("structured data is present and parses", async ({ page }) => {
    await page.goto("/fr");
    const blocks = await page
      .locator('script[type="application/ld+json"]')
      .allTextContents();

    expect(blocks.length).toBeGreaterThan(0);
    for (const block of blocks) expect(() => JSON.parse(block)).not.toThrow();
  });
});

test.describe("account", () => {
  test("an anonymous visitor is sent to sign-in, and back afterwards", async ({ page }) => {
    await page.goto("/fr/app/sources");

    await expect(page).toHaveURL(/\/fr\/login\?next=/);
    // The `next` parameter is what returns the visitor to where they were
    // going; losing it is the kind of regression nobody notices in review.
    expect(new URL(page.url()).searchParams.get("next")).toContain("/app/sources");
  });

  test("the session cookie is not readable from JavaScript", async ({ page }) => {
    await page.goto("/fr/login");
    const readable = await page.evaluate(() => document.cookie);
    expect(readable).not.toContain("lumo_session");
  });
});

test.describe("activation", () => {
  test("works without JavaScript", async ({ browser }) => {
    // The page is used standing in front of a television, on a phone, on
    // whatever connection the living room has. It must not depend on hydration.
    const context = await browser.newContext({ javaScriptEnabled: false });
    const page = await context.newPage();

    await page.goto("/fr/activate");
    await expect(page.getByRole("heading", { level: 1 })).toBeVisible();

    await context.close();
  });

  test("an anonymous visitor is asked to sign in, without losing the code", async ({
    page,
  }) => {
    await page.goto("/fr/activate?code=ABCD2345");

    // The form only appears for a signed-in visitor — approving a code is an
    // authenticated call. What matters here is that the code survives the
    // detour: whoever is standing in front of their television should not have
    // to read it off the screen twice.
    const signIn = page.getByRole("link").last();
    await expect(signIn).toHaveAttribute("href", /next=.*ABCD2345/);
  });

  // Needs a signed-in session, which needs a running lumo-api. Enable it with a
  // storage-state fixture once the API is part of the e2e environment.
  test.fixme("the code field takes focus and is prefilled", async ({ page }) => {
    await page.goto("/fr/activate?code=ABCD2345");

    const field = page.getByLabel(/code/i);
    await expect(field).toBeFocused();
    await expect(field).toHaveValue("ABCD2345");
  });
});
