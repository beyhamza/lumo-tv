import { expect, test, type Page } from "@playwright/test";
import fr from "../src/messages/fr.json";

/**
 * QA-06-04-08 — GD-11 web, guide partiel (S9-06-04, dépend I-2).
 *
 * `guide-partial.xml` (Game A privée de `bench.3`) laisse une colonne sans
 * programme. La grille doit l'annoncer par « Aucun programme disponible sur ce
 * créneau » et n'inventer aucun texte ; la lecture réussie n'est pas une erreur
 * (GD-10).
 */

const LABEL = "Banc QA S9-06-04 partiel";
const M3U = "http://bench/playlist.m3u";
const EPG = "http://bench/guide-partial.xml";

async function createSource(page: Page): Promise<string> {
  await page.goto("/fr/app/sources/new");
  await page.getByLabel(fr.App.sourceLabelLabel).fill(LABEL);
  await page.getByLabel(fr.App.sourceM3uUrlLabel).fill(M3U);
  await page.getByLabel(fr.App.sourceEpgUrlLabel).fill(EPG);
  await page.getByRole("button", { name: fr.App.sourceSubmit }).click();
  await expect(page.getByText(fr.App.sourceReadyTitle)).toBeVisible({ timeout: 60_000 });

  await page.goto("/fr/app/sources");
  await page.getByRole("link", { name: LABEL, exact: true }).click();
  await page.getByRole("link", { name: fr.App.sourceOpenCatalogue }).click();
  const url = new URL(page.url());
  const match = /\/sources\/([^/]+)\/channels/.exec(url.pathname);
  if (!match) throw new Error(`source id introuvable dans ${page.url()}`);
  return match[1];
}

test.setTimeout(120_000);

test("QA-06-04-08 — GD-11 guide partiel : créneau vide annoncé, aucune erreur", async ({
  page,
}) => {
  const sourceId = await createSource(page);
  await page.goto(`/fr/app/sources/${sourceId}/channels?view=guide`);

  // La grille est là : le programme A1 (bench.1) est rendu.
  await expect(page.locator('a[title="A1"]')).toBeVisible({ timeout: 30_000 });

  // Le trou de bench.3 est annoncé, sans texte inventé.
  await expect(page.getByText(fr.App.directGuideEmptySlot).first()).toBeVisible();

  // Une grille partielle n'est pas une erreur (GD-10).
  await expect(
    page.getByRole("alert").filter({ hasText: fr.App.directGuideError }),
  ).toHaveCount(0);
});
