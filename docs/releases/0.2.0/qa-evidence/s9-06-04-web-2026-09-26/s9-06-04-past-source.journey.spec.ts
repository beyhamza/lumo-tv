import { expect, test, type Browser, type Page } from "@playwright/test";
import fr from "../src/messages/fr.json";
import { SESSION_FILE } from "./support/stack";

/**
 * S9-06-04 — les deux cas restants du plan §6 : QA-06-04-03 (fiche d'un
 * programme passé) et QA-06-04-11 (changement de source, fiche ouverte).
 *
 * Harnais identique à `s9-06-04-web.journey.spec.ts` : pile e2e, build de
 * production, banc ancré sur `BENCH_EPG_ANCHOR` = `LUMO_NOW` =
 * `2026-09-26T16:24:57Z`. La fiche `guide-transition.xml` (Game B) ne place des
 * programmes que sur `bench.1` :
 *
 *   T 46  15:24:57Z → 15:54:57Z   (passé, juste avant E1)
 *   E1    15:54:57Z → 16:26:57Z   (courant à T)
 *   E2    16:27:57Z → 16:57:57Z   (futur à T)
 *
 * Le remplissage `T 46` est généré par `xmltv.awk` (index 46 de la boucle
 * `fill`), donc reproductible à l'instant pinné. Le fuseau web est
 * `Europe/Paris` (i18n/request.ts), la journée active du 26/09 couvre
 * 25/09 22:00Z → 26/09 22:00Z : `T 46` y tombe.
 */

const M3U = process.env.QA_M3U_URL ?? "http://bench/playlist.m3u";
const EPG = process.env.QA_EPG_URL ?? "http://bench/guide-transition.xml";

/** Past programme of bench.1, thirty minutes before E1. */
const PAST_TITLE = "T 46";

async function createSource(page: Page, label: string): Promise<string> {
  await page.goto("/fr/app/sources/new");
  await page.getByLabel(fr.App.sourceLabelLabel).fill(label);
  await page.getByLabel(fr.App.sourceM3uUrlLabel).fill(M3U);
  await page.getByLabel(fr.App.sourceEpgUrlLabel).fill(EPG);
  await page.getByRole("button", { name: fr.App.sourceSubmit }).click();
  await expect(page.getByText(fr.App.sourceReadyTitle)).toBeVisible({ timeout: 60_000 });

  await page.goto("/fr/app/sources");
  await page.getByRole("link", { name: label, exact: true }).click();
  await page.getByRole("link", { name: fr.App.sourceOpenCatalogue }).click();
  const url = new URL(page.url());
  const match = /\/sources\/([^/]+)\/channels/.exec(url.pathname);
  if (!match) throw new Error(`source id introuvable dans ${page.url()}`);
  return match[1];
}

function guideHref(sourceId: string): string {
  return `/fr/app/sources/${sourceId}/channels?view=guide`;
}

/** Open the sheet of the named programme and return the dialog. */
async function openSheet(page: Page, title: string) {
  const cell = page.locator(`a[title="${title}"]`);
  await expect(cell).toBeVisible({ timeout: 30_000 });
  await cell.click();
  const sheet = page.getByRole("dialog", { name: fr.App.programmeSheetLabel });
  await expect(sheet).toBeVisible();
  return sheet;
}

/** The switcher row of `label` on the account home, and the switch itself. */
async function switchActiveSource(page: Page, label: string): Promise<void> {
  await page.goto("/fr/app");
  const row = page.getByRole("button", { name: new RegExp(label) });
  await expect(row).toBeVisible({ timeout: 30_000 });
  await row.click();
  // The action redirects to the same section (STAY `/app`); the home heading then
  // names the source that is active for THIS device.
  await expect(page.getByText(/Source active\s*:/)).toContainText(label, {
    timeout: 30_000,
  });
}

test.setTimeout(180_000);

test("QA-06-04-03 — fiche d'un programme passé : informations seules, aucune lecture", async ({
  page,
}) => {
  const sourceId = await createSource(page, "Banc QA S9-06-04 passé");
  await page.goto(guideHref(sourceId));

  const sheet = await openSheet(page, PAST_TITLE);
  await expect(sheet.getByRole("heading", { name: PAST_TITLE })).toBeVisible();
  // Le repère chiffré de la chaîne et ses horaires sont là : la fiche est
  // renseignée, pas une coquille vide.
  await expect(sheet.getByText("Chaîne 01 FHD", { exact: false })).toBeVisible();

  // Aucun bouton de lecture, aucun replay : le seul lien de la fiche est Fermer.
  await expect(sheet.getByRole("link", { name: fr.App.programmeWatch })).toHaveCount(0);
  await expect(sheet.getByRole("link")).toHaveCount(1);
  await expect(sheet.getByRole("link", { name: fr.App.programmeClose })).toBeVisible();

  // La grille reste dessous : la fiche est un panneau, pas une navigation.
  await expect(page.locator(`a[title="${PAST_TITLE}"]`)).toHaveAttribute("href", /programme=/);
});

test("QA-06-04-11 — fiche ouverte : un changement de source venu d'un AUTRE appareil ne la ferme pas (portée par appareil)", async ({
  page,
  browser,
}) => {
  // Deux sources, sur la même compte, sinon le sélecteur de source n'existe pas
  // (une seule source s'affiche en texte, S8-E04).
  const sourceA = await createSource(page, "Banc QA S9-06-11 A");
  await createSource(page, "Banc QA S9-06-11 B");

  // Appareil 1 : fiche E1 ouverte sur la source A.
  await page.goto(guideHref(sourceA));
  const sheet = await openSheet(page, "E1");
  const urlOnDeviceOne = page.url();
  expect(urlOnDeviceOne).toContain("/sources/" + sourceA + "/channels");
  expect(urlOnDeviceOne).toContain("programme=");

  // Appareil 2 : même compte, pot à cookies distinct — c'est ce que « autre
  // appareil » veut dire. Il bascule SON choix de source sur B.
  const deviceTwo = await newContextWithSession(browser);
  const pageTwo = await deviceTwo.newPage();
  await switchActiveSource(pageTwo, "Banc QA S9-06-11 B");

  // Le choix de source web est un cookie d'appareil (« Ce choix ne vaut que
  // pour cet appareil », `sourceSwitcherDeviceNote`) : l'appareil 1 n'a pas
  // changé de source, donc sa fiche reste ouverte et sur sa source.
  await expect(sheet).toBeVisible();
  await expect(sheet.getByRole("heading", { name: "E1" })).toBeVisible();
  expect(page.url()).toBe(urlOnDeviceOne);

  await deviceTwo.close();
});

async function newContextWithSession(browser: Browser) {
  return browser.newContext({ storageState: SESSION_FILE });
}
