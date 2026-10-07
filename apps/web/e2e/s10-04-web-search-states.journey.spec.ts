import { expect, test, type Page } from "@playwright/test";
import fr from "../src/messages/fr.json";
import { sessionPathFor } from "./support/stack";

/** Compte dédié à ce fichier (S10-01 web), comme tout `*.journey.spec.ts`. */
test.use({ storageState: sessionPathFor("s10-04-web-search-states") });

/**
 * S10-04 — les quatre états de la recherche web (US-021, Q9/SR-04, SR-10).
 *
 * <h2>Ce que ce cas prouve</h2>
 *
 * Qu'un champ vide invite sans tirer le catalogue ; qu'une recherche sans
 * correspondance rappelle le texte saisi ET la source active, avec une action
 * d'effacement ; et que « hors ligne » est un état distinct de « aucun
 * résultat » — jamais confondus.
 *
 * <h2>Ce qui reste ailleurs</h2>
 *
 * L'erreur partielle (une section en échec, les autres conservées, réessai
 * ciblé) exige une panne injectée : elle vit dans
 * `s10-04-web-search-partial.journey.spec.ts`, sur une pile dédiée (proxy 503
 * sur `/vod`). Le retour de fiche/lecteur est couvert par S10-03.
 */

const MIXED = "http://bench/mixed.m3u";
const LABEL = "Banc S10-04 états";
const NO_MATCH = "zzzznonexistent";

async function createMixedSource(page: Page): Promise<void> {
  await page.goto("/fr/app/sources/new");
  await page.getByLabel(fr.App.sourceLabelLabel).fill(LABEL);
  await page.getByLabel(fr.App.sourceM3uUrlLabel).fill(MIXED);
  await page.getByRole("button", { name: fr.App.sourceSubmit }).click();
  await expect(page.getByText(fr.App.sourceReadyTitle)).toBeVisible({ timeout: 60_000 });
}

test.setTimeout(120_000);

test.describe.serial("S10-04 — états web de la recherche", () => {
  test("préparation — source mixte (chaînes et films)", async ({ page }) => {
    await createMixedSource(page);
  });

  test("un champ vide invite et ne charge aucun catalogue", async ({ page }) => {
    await page.goto("/fr/app/search?q=%20%20");

    await expect(page.getByText(fr.App.searchInvitation)).toBeVisible();
    await expect(page.getByText(fr.App.searchInvitationHint)).toBeVisible();
    // Aucune section dessinée : le catalogue entier n'est pas tiré.
    await expect(
      page.getByRole("region", { name: fr.App.searchSectionChannels }),
    ).toHaveCount(0);
    await expect(page.getByRole("region", { name: fr.App.searchSectionFilms })).toHaveCount(0);
  });

  test("aucun résultat rappelle le texte saisi ET la source active, et propose d'effacer", async ({
    page,
  }) => {
    await page.goto(`/fr/app/search?q=${NO_MATCH}`);

    const noResult = page.getByText(new RegExp(NO_MATCH));
    await expect(noResult).toBeVisible({ timeout: 30_000 });
    // Le nom de la source active est dans la même phrase (jamais un ensemble vide muet).
    await expect(noResult).toContainText(LABEL);

    await expect(page.getByRole("link", { name: fr.App.searchClear })).toBeVisible();
    // L'invitation du champ vide n'est pas ce qu'on montre : le texte a été saisi.
    await expect(page.getByText(fr.App.searchInvitation)).toHaveCount(0);
  });

  test("hors ligne : un état distinct de « aucun résultat », qui ne l'efface pas", async ({
    page,
    context,
  }) => {
    await page.goto(`/fr/app/search?q=${NO_MATCH}`);
    const noResult = page.getByText(new RegExp(NO_MATCH));
    await expect(noResult).toBeVisible({ timeout: 30_000 });

    // Le composant lit `navigator.onLine` : ce basculement déclenche l'événement
    // « offline » du navigateur, sans rechargement serveur.
    await context.setOffline(true);
    const offline = page.getByRole("status");
    await expect(offline).toBeVisible({ timeout: 10_000 });
    await expect(offline).toContainText(fr.App.searchOffline);

    // Distinct : le résultat vide reste affiché, la bannière ne le remplace pas.
    await expect(noResult).toBeVisible();
    await context.setOffline(false);
  });
});
