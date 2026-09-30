import { expect, test, type Page } from "@playwright/test";
import fr from "../src/messages/fr.json";
import { sessionPathFor } from "./support/stack";

/** The account this file signs up for, so its sources are its own (S10-01 web). */
test.use({ storageState: sessionPathFor("s10-02-web-search") });

/**
 * La recherche web — filtres et « Voir tous » (US-021, S10-02).
 *
 * <h2>Ce que ce cas prouve</h2>
 *
 * Le champ, les onglets, l'aperçu groupé de quatre, « Voir tous » qui démarre
 * une page 0 de vingt, et la pagination. Le banc `playlist-100.m3u` sert cent
 * chaînes, ce qui est ce qu'il faut pour que l'aperçu soit coupé et que
 * « Voir tous » ait une seconde page.
 *
 * <h2>Ce qu'il prouve aussi, et qui est SR-11</h2>
 *
 * Une source M3U ne porte pas de films ni de séries : seuls « Tous » et
 * « Chaînes » apparaissent. Un type absent n'a pas d'onglet, alors qu'un type
 * présent sans correspondance garde le sien et affiche son état vide — la
 * seconde moitié se lit sur la source mixte et n'est pas rejouée ici.
 *
 * <h2>Ce qui reste à S10-03/S10-04</h2>
 *
 * L'ouverture d'un résultat, le retour avec la position, le champ vide
 * détaillé, l'erreur partielle et le hors ligne. Ce fichier s'arrête à ce que
 * S10-02 livre.
 */

const M3U = "http://bench/playlist-100.m3u";

async function createSource(page: Page, label: string): Promise<string> {
  await page.goto("/fr/app/sources/new");
  await page.getByLabel(fr.App.sourceLabelLabel).fill(label);
  await page.getByLabel(fr.App.sourceM3uUrlLabel).fill(M3U);
  await page.getByRole("button", { name: fr.App.sourceSubmit }).click();
  await expect(page.getByText(fr.App.sourceReadyTitle)).toBeVisible({ timeout: 60_000 });

  await page.goto("/fr/app/sources");
  await page.getByRole("link", { name: label, exact: true }).click();
  await page.getByRole("link", { name: fr.App.sourceOpenCatalogue }).click();
  const match = /\/sources\/([^/]+)\/channels/.exec(new URL(page.url()).pathname);
  if (!match) throw new Error(`source id introuvable dans ${page.url()}`);
  return match[1];
}

test.setTimeout(180_000);

test.describe.serial("S10-02 — recherche web, filtres et Voir tous", () => {
  test("préparation — source de banc à cent chaînes", async ({ page }) => {
    await createSource(page, "Banc S10-02");
  });

  test("le champ invite, les onglets suivent les catalogues de la source", async ({ page }) => {
    await page.goto("/fr/app/search");

    await expect(page.getByRole("heading", { name: fr.App.searchTitle, level: 1 })).toBeVisible();
    await expect(page.getByText(fr.App.searchInvitation)).toBeVisible();

    // Une source M3U ne porte que des chaînes : pas d'onglet Films ni Séries.
    // Scopé à la barre de filtres, sinon le lien « Films » du menu de source
    // (S10-02, layout) serait pris pour un onglet de recherche.
    const filters = page.getByRole("navigation", { name: fr.App.searchFiltersLabel });
    await expect(filters.getByRole("link", { name: fr.App.searchFilterAll, exact: true })).toBeVisible();
    await expect(filters.getByRole("link", { name: fr.App.searchFilterChannels, exact: true })).toBeVisible();
    await expect(filters.getByRole("link", { name: fr.App.searchFilterFilms, exact: true })).toHaveCount(0);
    await expect(filters.getByRole("link", { name: fr.App.searchFilterSeries, exact: true })).toHaveCount(0);
  });

  test("l'aperçu groupe quatre résultats et « Voir tous » ouvre la page 0 de vingt", async ({
    page,
  }) => {
    await page.goto("/fr/app/search");

    await page.getByLabel(fr.App.searchLabel).fill("Chaîne");
    await page.getByRole("button", { name: fr.App.searchSubmit }).click();

    // Le résultat est groupé : quatre chaînes, pas cent.
    const section = page.getByRole("region", { name: fr.App.searchSectionChannels });
    await expect(section).toBeVisible({ timeout: 30_000 });
    await expect(section.getByRole("listitem")).toHaveCount(4);

    // « Voir tous » quitte l'aperçu pour une page de vingt, distincte.
    await section.getByRole("link", { name: fr.App.searchSeeAll }).click();
    await expect(page).toHaveURL(/type=channels/);

    const list = page.getByRole("region", { name: fr.App.searchSectionChannels });
    await expect(list.getByRole("listitem")).toHaveCount(20, { timeout: 30_000 });
    await expect(page.getByText(fr.App.searchPageOf.replace("{page}", "1").replace("{total}", "5"))).toBeVisible();

    // La page suivante est une autre page, et garde le filtre.
    await page.getByRole("link", { name: fr.App.searchNext, exact: true }).click();
    await expect(page).toHaveURL(/page=1/);
    await expect(page.getByRole("region", { name: fr.App.searchSectionChannels }).getByRole("listitem")).toHaveCount(20, {
      timeout: 30_000,
    });
  });

  test("un champ vidé après espaces ne charge pas le catalogue entier", async ({ page }) => {
    await page.goto("/fr/app/search?q=%20%20");
    await expect(page.getByText(fr.App.searchInvitation)).toBeVisible();
    // Aucune section dessinée : le catalogue de cent chaînes n'est pas tiré.
    await expect(page.getByRole("region", { name: fr.App.searchSectionChannels })).toHaveCount(0);
  });
});
