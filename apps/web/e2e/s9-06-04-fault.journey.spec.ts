import { expect, test, type Page } from "@playwright/test";
import fr from "../src/messages/fr.json";
import { sessionPathFor } from "./support/stack";

/** The account this file signs up for, so its sources are its own. */
test.use({ storageState: sessionPathFor("s9-06-04-fault") });

/**
 * QA-06-04-06 — GD-10 web, erreur initiale (S9-06-04).
 *
 * La lecture du guide est refusée par un proxy qui répond 503 sur `.../epg`
 * (voir qa-evidence). Le reste de l'écran répond normalement : c'est la
 * distinction que GD-10 exige entre « le guide n'a pas pu être chargé » et
 * « le guide est vide ».
 */

const LABEL = "Banc QA S9-06-04 erreur";
const M3U = "http://bench/playlist.m3u";
const EPG = "http://bench/guide-transition.xml";

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

// La panne est un réglage GLOBAL de l'API (`EpgFaultInjection`, `LUMO_EPG_FAULT`,
// défaut 0) : armée, elle ferait échouer toutes les autres specs guide. Elle
// exige donc une pile dédiée, et ce fichier se saute partout ailleurs — comme
// GD-12 attend son `E2E_ANCHOR`.
test.skip(
  !process.env.E2E_FAULT_ARMED,
  "requiert LUMO_EPG_FAULT=503 sur une pile dédiée (E2E_FAULT_ARMED=1)",
);

test("QA-06-04-06 — GD-10 erreur initiale : message distinct, Réessayer, jamais « guide vide »", async ({
  page,
}) => {
  const sourceId = await createSource(page);
  await page.goto(`/fr/app/sources/${sourceId}/channels?view=guide`);

  const alert = page
    .getByRole("alert")
    .filter({ hasText: fr.App.directGuideError });
  await expect(alert).toBeVisible({ timeout: 30_000 });
  await expect(alert).toContainText(fr.App.directGuideError);
  await expect(alert).toContainText(fr.App.directGuideErrorHint);
  await expect(alert.getByRole("link", { name: fr.App.directGuideRetry })).toBeVisible();
  await expect(alert.getByRole("link", { name: fr.App.sourceOpenCatalogue })).toBeVisible();

  // Le défaut que GD-10 nomme : annoncer un guide vide pour une lecture ratée.
  await expect(page.getByText(fr.App.directGuideEmptySlot)).toHaveCount(0);
  await expect(page.getByText(fr.App.directGuideNoGuide)).toHaveCount(0);
});
