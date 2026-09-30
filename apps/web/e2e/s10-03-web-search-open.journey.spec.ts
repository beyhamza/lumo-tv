import { expect, test, type Page } from "@playwright/test";
import fr from "../src/messages/fr.json";
import { sessionPathFor } from "./support/stack";

/** The account this file signs up for, so its sources are its own (S10-01 web). */
test.use({ storageState: sessionPathFor("s10-03-web-search-open") });

/**
 * Ouvrir un résultat et revenir à sa recherche (US-021, S10-03).
 *
 * <h2>Ce que ce cas prouve</h2>
 *
 * Qu'une ligne de résultat est un lien : un film mène à sa fiche et la fiche
 * sait revenir à la recherche qui l'a ouverte — texte, filtre et page —, une
 * chaîne démarre le direct sur sa page et le retour navigateur retrouve le
 * filtre. Le marqueur de provenance est restreint : la fiche ne lit que
 * `from=search` et trois paramètres connus, jamais un chemin ni un hôte.
 *
 * <h2>Pourquoi une source mixte</h2>
 *
 * `mixed.m3u` porte des chaînes ET des films (ADR 0009), donc les deux
 * parcours tiennent sur une seule source et un seul compte. Le banc ne sert pas
 * d'arbre de séries pour une playlist : le retour d'une fiche série se prouve
 * sur la même règle pure, `searchReturnPath`, couverte par
 * `src/lib/search/search.test.ts`.
 */

const MIXED = "http://bench/mixed.m3u";

async function createMixedSource(page: Page, label: string): Promise<void> {
  await page.goto("/fr/app/sources/new");
  await page.getByLabel(fr.App.sourceLabelLabel).fill(label);
  await page.getByLabel(fr.App.sourceM3uUrlLabel).fill(MIXED);
  await page.getByRole("button", { name: fr.App.sourceSubmit }).click();
  await expect(page.getByText(fr.App.sourceReadyTitle)).toBeVisible({ timeout: 60_000 });
}

test.setTimeout(180_000);

test.describe.serial("S10-03 — ouvrir un résultat et revenir à la recherche", () => {
  test("préparation — source mixte (chaînes et films)", async ({ page }) => {
    await createMixedSource(page, "Banc S10-03");
  });

  test("un film mène à sa fiche, et le retour retrouve la même recherche", async ({ page }) => {
    await page.goto("/fr/app/search");
    await page.getByLabel(fr.App.searchLabel).fill("Voyage");
    await page.getByRole("button", { name: fr.App.searchSubmit }).click();

    const films = page.getByRole("region", { name: fr.App.searchSectionFilms });
    await expect(films).toBeVisible({ timeout: 30_000 });
    await films.getByRole("link", { name: /Le Voyage/ }).click();

    // La fiche du film, pas la liste.
    await expect(page.getByRole("heading", { name: "Le Voyage", level: 1 })).toBeVisible({
      timeout: 30_000,
    });

    // Le retour est un lien explicite vers la recherche, texte compris.
    const back = page.getByRole("link", { name: fr.App.searchBackToResults });
    await expect(back).toHaveAttribute("href", /\/app\/search\?q=Voyage$/);
    await back.click();
    await expect(page).toHaveURL(/\/app\/search\?q=Voyage$/);
    await expect(page.getByLabel(fr.App.searchLabel)).toHaveValue("Voyage");
    await expect(page.getByRole("region", { name: fr.App.searchSectionFilms })).toBeVisible();
  });

  test("le retour de la fiche conserve aussi le filtre et la page", async ({ page }) => {
    // Le parcours réel ne descend pas sous la première page avec deux films ;
    // le lien, lui, doit rendre exactement ce que la fiche a reçu.
    await page.goto("/fr/app/search?q=Voyage");
    const films = page.getByRole("region", { name: fr.App.searchSectionFilms });
    await expect(films).toBeVisible({ timeout: 30_000 });
    const filmHref = await films.getByRole("link", { name: /Le Voyage/ }).getAttribute("href");
    if (!filmHref) throw new Error("lien de film introuvable");

    const fiche = filmHref.split("?")[0];
    await page.goto(`${fiche}?from=search&q=Voyage&type=films&page=3`);

    const back = page.getByRole("link", { name: fr.App.searchBackToResults });
    await expect(back).toHaveAttribute("href", /q=Voyage&type=films&page=3$/);
    await back.click();
    await expect(page).toHaveURL(/\/app\/search\?q=Voyage&type=films&page=3$/);
  });

  test("une chaîne démarre le direct et le retour garde la recherche filtrée", async ({ page }) => {
    await page.goto("/fr/app/search?q=Cha%C3%AEne&type=channels");

    const channels = page.getByRole("region", { name: fr.App.searchSectionChannels });
    await expect(channels).toBeVisible({ timeout: 30_000 });
    await channels.getByRole("link").first().click();

    // Le direct s'ouvre sur la page des chaînes de la source.
    await expect(page).toHaveURL(/\/sources\/[^/]+\/channels\?play=/);

    // Le retour navigateur retrouve la même recherche, filtre compris.
    await page.goBack();
    await expect(page).toHaveURL(/\/app\/search\?q=Cha%C3%AEne&type=channels$/);
    await expect(page.getByLabel(fr.App.searchLabel)).toHaveValue("Chaîne");
  });
});
