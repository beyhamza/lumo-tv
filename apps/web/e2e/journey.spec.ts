import { expect, test } from "@playwright/test";
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
