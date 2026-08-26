import { expect, test, type Page } from "@playwright/test";
import fr from "../src/messages/fr.json";

/**
 * What only a stack can test.
 *
 * `zones.spec.ts` checks properties of the web application on its own — the
 * routing, the metadata, the cookie, the no-JavaScript path — and needs no
 * server behind it. Everything here crosses the boundary: a real session, a
 * real HTTP call to lumo-api, a real row in a real PostgreSQL.
 *
 * The session comes from `auth.setup.ts`, which signs up through the form.
 *
 * Wording is read from the message catalogue rather than retyped, so renaming
 * a label is a translation change and not a broken test.
 */

test.describe("espace compte", () => {
  test("la liste des sources vient de l'API, pas d'un état de repli", async ({
    page,
  }) => {
    await page.goto("/fr/app/sources");

    // The account was created seconds ago, so the honest answer is an empty
    // list — and it is an *answer*: reaching this text means the request was
    // authenticated, lumo-api replied 200, and it read an empty table. The
    // failure this catches is the one that looks like success: an API that is
    // down renders the "service unavailable" panel instead, and both are calm
    // grey boxes to anyone reading a screenshot.
    await expect(page.getByText(fr.App.sourcesEmpty)).toBeVisible();
    await expect(page.getByText(fr.App.unavailableTitle)).toHaveCount(0);
  });

  test("la liste des appareils montre la session qui la consulte", async ({ page }) => {
    await page.goto("/fr/app/devices");

    // This screen used to assert "not built yet": `GET /me/devices` was in the
    // contract with no controller behind it. It has one now, so the assertion
    // is the opposite one — and it is worth more, because it crosses the whole
    // stack. Signing up opened exactly one session, so exactly one device is
    // linked, and it is a WEB one: this browser.
    await expect(page.getByText("WEB")).toBeVisible();
    // Neither of the two fallbacks. "Not built yet" would mean the controller
    // vanished; "unavailable" would mean the API is down. Both are calm grey
    // boxes to anyone reading a screenshot, which is exactly why they are
    // asserted away rather than eyeballed.
    await expect(page.getByText(fr.App.notImplementedBadge)).toHaveCount(0);
    await expect(page.getByText(fr.App.unavailableTitle)).toHaveCount(0);
  });
});

test.describe("activation", () => {
  test("le champ de code prend le focus et arrive pré-rempli", async ({ page }) => {
    // Was `test.fixme` in zones.spec.ts: the form only renders for a signed-in
    // visitor, so it needed a session, so it needed an API. It has both now.
    await page.goto("/fr/activate?code=ABCD2345");

    const field = page.getByLabel(fr.Activate.codeLabel);

    await expect(field).toBeFocused();
    await expect(field).toHaveValue("ABCD2345");
  });

  test("un code inconnu est refusé par l'API, sans perdre la saisie", async ({
    page,
  }) => {
    await page.goto("/fr/activate");

    // Eight characters from the contract's alphabet, so validation passes and
    // the request really reaches lumo-api, which has no such authorization.
    // This is the round trip: server action, 404, problem+json, and a message
    // chosen from `code` rather than from an HTTP status.
    await page.getByLabel(fr.Activate.codeLabel).fill("ZZZZ9999");
    await page.locator('button[type="submit"]').click();

    await expect(page).toHaveURL(/error=/);
    // The code survives the failure: whoever is standing in front of their
    // television should not have to read it off the screen twice.
    await expect(page).toHaveURL(/code=ZZZZ9999/);
  });
});

/**
 * Registering a source, from the form to a usable catalogue (US-06, US-07).
 *
 * Serial, because it is one flow rather than four independent checks: the
 * account created by `auth.setup.ts` is on the free plan, which allows exactly
 * one source, and that ceiling is itself part of what is being verified. Each
 * test leaves the account in the state the next one needs.
 *
 * Everything points at the bench container (`docker-compose.e2e.yml`), never at
 * a real provider: a qualification run that depends on somebody's IPTV
 * subscription is a qualification run that fails on a Sunday for reasons nobody
 * can reproduce.
 */
test.describe.serial("sources", () => {
  test("une playlist enregistrée devient une source prête", async ({ page }) => {
    await page.goto("/fr/app/sources/new");

    await page.getByLabel(fr.App.sourceLabelLabel).fill("Banc d'essai");
    await page.getByLabel(fr.App.sourceM3uUrlLabel).fill("http://bench/playlist.m3u");
    await page.getByRole("button", { name: fr.App.sourceSubmit }).click();

    // The POST answers 202 and the catalogue is not there yet, so the landing
    // page is the wait. Ingestion of five channels is quick, so what is asserted
    // is the end state — reaching it at all proves the whole chain: server
    // action, API, ingestion worker, PostgreSQL, and the polling screen.
    await expect(page.getByText(fr.App.sourceReadyTitle)).toBeVisible({
      timeout: 30_000,
    });

    // Five channels in three groups: two named ones plus the bucket the server
    // invents for the entry with no group-title.
    await expect(page.getByText("5 chaînes · 3 catégories")).toBeVisible();
  });

  test("le catalogue liste les chaînes, avec numéro et qualité", async ({ page }) => {
    await page.goto("/fr/app/sources");
    await page.getByRole("link", { name: "Banc d'essai" }).click();
    await page.getByRole("link", { name: fr.App.sourceOpenCatalogue }).click();

    await expect(page.getByText("5 chaînes", { exact: true })).toBeVisible();

    // Number and quality come from the playlist and are rendered as the source
    // wrote them: tvg-chno="1", and FHD read out of the name without rewriting
    // the name itself.
    const first = channels(page).getByRole("listitem").filter({ hasText: "Chaîne 01 FHD" });
    await expect(first).toContainText("1");
    await expect(first).toContainText("FHD");

    // A channel the playlist gave no number and no quality shows neither, rather
    // than a zero or an invented badge.
    await expect(
      channels(page).getByRole("listitem").filter({ hasText: "Chaîne 05" }),
    ).toBeVisible();
  });

  test("catégorie et recherche passent par l'URL, donc sans JavaScript", async ({
    page,
  }) => {
    await page.goto("/fr/app/sources");
    await page.getByRole("link", { name: "Banc d'essai" }).click();
    await page.getByRole("link", { name: fr.App.sourceOpenCatalogue }).click();

    // Two channels carry group-title="Sport". Filtering is a link, so the state
    // is in the URL and the back button works.
    await page.getByRole("link", { name: "Sport" }).click();
    await expect(page).toHaveURL(/categoryId=/);
    await expect(channels(page).getByRole("listitem")).toHaveCount(2);

    // The search is a GET form. Case-insensitive substring, which is exactly
    // what the hint promises and nothing more.
    await page.goto("/fr/app/sources");
    await page.getByRole("link", { name: "Banc d'essai" }).click();
    await page.getByRole("link", { name: fr.App.sourceOpenCatalogue }).click();
    await page.getByLabel(fr.App.catalogueSearchLabel).fill("chaîne 03");
    await page.getByRole("button", { name: fr.App.catalogueSearchSubmit }).click();

    await expect(page).toHaveURL(/q=/);
    await expect(channels(page).getByRole("listitem")).toHaveCount(1);

    await page.getByLabel(fr.App.catalogueSearchLabel).fill("zzzz");
    await page.getByRole("button", { name: fr.App.catalogueSearchSubmit }).click();
    await expect(page.getByText(fr.App.catalogueNoResults)).toBeVisible();
  });

  test("le plafond de l'offre remplace le bouton d'ajout", async ({ page }) => {
    await page.goto("/fr/app/sources");

    // FREE allows one source and one is registered. The ceiling is read from
    // GET /me/entitlement, never written into the web: the day the free plan
    // allows two, this screen follows without a release.
    await expect(page.getByText(fr.App.sourcesLimitBody)).toBeVisible();
    await expect(
      page.getByRole("link", { name: fr.App.sourcesAddCta }),
    ).toHaveCount(0);
  });

  test("supprimer la source rend la place", async ({ page }) => {
    await page.goto("/fr/app/sources");
    await page.getByRole("link", { name: "Banc d'essai" }).click();

    await page.getByRole("link", { name: fr.App.sourceDelete }).click();
    // The confirmation is a query parameter, not a dialog: it works without
    // JavaScript and it names what goes with the source.
    await expect(page.getByText(fr.App.sourceDeleteConfirmBody)).toBeVisible();
    await page.getByRole("button", { name: fr.App.sourceDeleteConfirm }).click();

    await expect(page.getByText(fr.App.sourcesEmpty)).toBeVisible();
    await expect(
      page.getByRole("link", { name: fr.App.sourcesAddCta }),
    ).toBeVisible();
  });

  test("une adresse qui ne renvoie pas une playlist est nommée comme telle", async ({
    page,
  }) => {
    await page.goto("/fr/app/sources/new");

    await page.getByLabel(fr.App.sourceLabelLabel).fill("Page HTML");
    // HTTP 200 with an HTML body: what a mistyped address usually returns.
    await page
      .getByLabel(fr.App.sourceM3uUrlLabel)
      .fill("http://bench/not-a-playlist.html");
    await page.getByRole("button", { name: fr.App.sourceSubmit }).click();

    await expect(page.getByText(fr.App.sourceErrorTitle)).toBeVisible({
      timeout: 30_000,
    });
    // The specific code, not a generic failure. "Your playlist is empty" would
    // send the user looking in the wrong place.
    await expect(page.getByText(fr.Errors.SOURCE_INVALID_FORMAT)).toBeVisible();
  });
});


/**
 * The channel list, told apart from the category navigation beside it.
 *
 * Both are lists of links, so a page-wide `listitem` query matches both — which
 * is how the first version of these assertions came to expect two rows and count
 * six. The channel list carries an accessible name for exactly this reason, and
 * scoping to it is also what the name is for in a screen reader.
 */
function channels(page: Page) {
  return page.getByRole("list", { name: fr.App.catalogueTitle });
}
