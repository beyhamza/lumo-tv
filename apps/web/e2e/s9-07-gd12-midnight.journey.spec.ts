import { expect, test, type Page } from "@playwright/test";
import fr from "../src/messages/fr.json";
import { sessionPathFor } from "./support/stack";

/** The account this file signs up for, so its sources are its own. */
test.use({ storageState: sessionPathFor("s9-07-gd12-midnight") });

/**
 * GD-12 — minuit, côté web SSR, horloge contrôlée (`LUMO_NOW`).
 *
 * La session qui joue ce fichier est ancrée à **23:50 Europe/Paris**
 * (`LUMO_NOW` = `BENCH_EPG_ANCHOR`, un même instant). Le passage ancré pose
 * `E2E_ANCHOR` au prochain 23:50 Europe/Paris ; sans lui, ce fichier se saute
 * (`test.skip` ci-dessous) parce qu'un banc resté sur l'heure réelle sert un
 * guide d'aujourd'hui, pas celui de la nuit de minuit.
 * Le jeu A place `A1` de `T` à `T+45 min`, soit 23:50 → 00:35 : un programme à
 * cheval sur minuit.
 *
 * Ce qui est vérifié, et que seule une horloge contrôlée permet :
 *   - la journée du 30 septembre **coupe** `A1` à minuit (fin affichée 00:00) ;
 *   - la journée du 1<sup>er</sup> octobre **reprend** `A1` à son début effectif
 *     (00:00 → 00:35).
 * Le même bloc est donc placé par ses deux **instants**, pas par une heure
 * locale : c'est la propriété GD-12 côté rendu serveur.
 *
 * Le changement d'heure du 25 octobre 2026 n'est **pas** automatisable ici : le
 * parseur XMLTV de l'API ne retient que `[maintenant − 1 j, maintenant + 3 j]`
 * (`XmltvStreamParser`, RETENTION_DAYS_BACK/FORWARD), donc un banc ancré à cette
 * date n'importerait aucun programme. Le repli est le test unitaire de
 * `src/lib/epg/day-window.test.ts` (jour de 23 h et jour de 25 h), et le cas
 * reste dans `s9-07-manuel.md`.
 */

const M3U = "http://bench/playlist.m3u";
const EPG = "http://bench/guide.xml";

async function createSource(page: Page): Promise<string> {
  const label = "Banc QA GD-12 minuit";
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

test.setTimeout(180_000);

// Une horloge contrôlée se demande, elle ne se suppose pas. Le passage ancré
// (`.github/workflows/web.yml`, `workflow_dispatch`) pose `E2E_ANCHOR` ; partout
// ailleurs la suite suit l'heure réelle et ce fichier s'annonce comme sauté au
// lieu d'échouer sur un guide ancré à un autre instant.
test.skip(
  !process.env.E2E_ANCHOR,
  "horloge contrôlée : requiert E2E_ANCHOR (prochain 23:50 Europe/Paris)",
);

test.describe.serial("GD-12 — minuit, web SSR sous LUMO_NOW", () => {
  let sourceId = "";

  test("préparation — source de banc et jour courant", async ({ page }) => {
    sourceId = await createSource(page);
    await page.goto(`/fr/app/sources/${sourceId}/channels?view=guide`);
    await expect(page.getByRole("link", { name: fr.App.directDayToday })).toBeVisible({
      timeout: 30_000,
    });
  });

  test("la journée coupe A1 à minuit, la suivante le reprend à 00:00", async ({ page }) => {
    await page.goto(`/fr/app/sources/${sourceId}/channels?view=guide`);
    await expect(page.getByRole("table", { name: fr.App.directGuideTitle })).toBeVisible({
      timeout: 30_000,
    });

    // Journée du 30 septembre : A1 (23:50 → 00:35) est coupé à minuit.
    const todayCell = page.locator('a[title="A1"]');
    await expect(todayCell).toBeVisible();
    const todayText = (await todayCell.textContent()) ?? "";
    expect(todayText).toMatch(/23:50/);
    // Minuit s'écrit « 0:00 » (heure numérique) dans le formateur Intl de la
    // locale, comme 8:05 PM en anglais : on accepte les deux écritures.
    expect(todayText).toMatch(/0?0:00/);

    // Les onglets de jour : ils commencent à J−1, donc « Aujourd'hui » n'est
    // pas forcément le premier. On repère l'onglet du jour courant par son
    // libellé, puis on ouvre le suivant — celui de la journée qui suit minuit.
    const dayTabs = page
      .getByRole("navigation", { name: fr.App.directGuideDays })
      .getByRole("link");
    const tabCount = await dayTabs.count();
    let todayIndex = -1;
    for (let i = 0; i < tabCount; i += 1) {
      const label = ((await dayTabs.nth(i).textContent()) ?? "").trim();
      if (label === fr.App.directDayToday) {
        todayIndex = i;
        break;
      }
    }
    expect(todayIndex, "onglet « Aujourd'hui » introuvable").toBeGreaterThanOrEqual(0);
    expect(todayIndex + 1, "aucune journée après aujourd'hui").toBeLessThan(tabCount);
    await dayTabs.nth(todayIndex + 1).click();

    await expect(page.getByRole("table", { name: fr.App.directGuideTitle })).toBeVisible({
      timeout: 30_000,
    });
    const nextCell = page.locator('a[title="A1"]');
    await expect(nextCell).toBeVisible();
    const nextText = (await nextCell.textContent()) ?? "";
    // Le même programme, repris à son début effectif sur la nouvelle journée.
    expect(nextText).toMatch(/0?0:00/);
    expect(nextText).toMatch(/0?0:35/);
  });
});
