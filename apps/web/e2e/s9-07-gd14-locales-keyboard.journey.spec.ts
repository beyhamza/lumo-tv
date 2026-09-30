import { expect, test, type Page } from "@playwright/test";
import fr from "../src/messages/fr.json";
import en from "../src/messages/en.json";
import { sessionPathFor } from "./support/stack";

/** The account this file signs up for, so its sources are its own. */
test.use({ storageState: sessionPathFor("s9-07-gd14-locales-keyboard") });

/**
 * GD-14 (S9-07-02) — web, FR/EN et clavier, sans matériel.
 *
 * Ce que ce cas couvre : les libellés du guide dans les deux langues, l'absence
 * de français résiduel en anglais, le format d'heure propre à la locale
 * (24 h en FR, 12 h en EN — `clockTime` suit le cycle de la locale), et
 * l'activation d'une case au clavier (Tab jusqu'à la case, Entrée ouvre la
 * fiche).
 *
 * Ce qu'il ne couvre pas : la troncature à 320 px (contrôle visuel), le D-pad
 * réel (télécommande), le glisser horizontal de la grille au clavier. Ces
 * points restent dans `s9-07-manuel.md`.
 */

const M3U = "http://bench/playlist.m3u";
const EPG = "http://bench/guide.xml";

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

test.setTimeout(180_000);

test.describe.serial("GD-14 — web FR/EN et clavier", () => {
  let sourceId = "";

  test("préparation — source de banc avec guide (Game A)", async ({ page }) => {
    sourceId = await createSource(page, "Banc QA GD-14");
    await page.goto(`/fr/app/sources/${sourceId}/channels?view=guide`);
    await expect(page.getByRole("table", { name: fr.App.directGuideTitle })).toBeVisible({
      timeout: 30_000,
    });
  });

  test("FR — libellés, jour courant et horaires 24 h", async ({ page }) => {
    await page.goto(`/fr/app/sources/${sourceId}/channels?view=guide`);

    await expect(page.getByRole("table", { name: fr.App.directGuideTitle })).toBeVisible({
      timeout: 30_000,
    });
    await expect(page.getByRole("navigation", { name: fr.App.directGuideDays })).toBeVisible();
    await expect(page.getByRole("link", { name: fr.App.directDayToday })).toBeVisible();
    await expect(page.getByRole("link", { name: fr.App.directNowButton })).toBeVisible();
    await expect(page.getByRole("link", { name: fr.App.sourceOpenCatalogue }).first()).toBeVisible();
    await expect(page.locator("html")).toHaveAttribute("lang", "fr");

    // La case A1 (bench.1) imprime ses horaires en 24 h, sans AM/PM.
    const cell = page.locator('a[title="A1"]');
    await expect(cell).toBeVisible();
    const text = (await cell.textContent()) ?? "";
    expect(text).toMatch(/\d{1,2}:\d{2}/);
    expect(text).not.toMatch(/(AM|PM)/);
  });

  test("EN — libellés traduits, aucun français résiduel, horaires 12 h", async ({ page }) => {
    await page.goto(`/en/app/sources/${sourceId}/channels?view=guide`);

    await expect(page.getByRole("table", { name: en.App.directGuideTitle })).toBeVisible({
      timeout: 30_000,
    });
    await expect(page.getByRole("navigation", { name: en.App.directGuideDays })).toBeVisible();
    await expect(page.getByRole("link", { name: en.App.directDayToday })).toBeVisible();
    await expect(page.getByRole("link", { name: en.App.directNowButton })).toBeVisible();
    await expect(page.getByRole("link", { name: en.App.sourceOpenCatalogue }).first()).toBeVisible();
    await expect(page.locator("html")).toHaveAttribute("lang", "en");

    const cell = page.locator('a[title="A1"]');
    await expect(cell).toBeVisible();
    const text = (await cell.textContent()) ?? "";
    expect(text).toMatch(/(AM|PM)/);

    // Aucun libellé français résiduel sur la page anglaise.
    const html = await page.content();
    for (const residual of [
      "Aujourd'hui",
      "Maintenant",
      "Voir les chaînes",
      "Grille des programmes",
      "Guide TV",
    ]) {
      expect(html, `français résiduel « ${residual} » sur la page anglaise`).not.toContain(
        residual,
      );
    }
    // Et aucune clé brute.
    expect(html).not.toContain("directGuideTitle");
    expect(html).not.toContain("directViewGuide");
  });

  test("clavier — Tab atteint une case du guide et Entrée ouvre la fiche", async ({ page }) => {
    await page.goto(`/fr/app/sources/${sourceId}/channels?view=guide`);
    await expect(page.getByRole("table", { name: fr.App.directGuideTitle })).toBeVisible({
      timeout: 30_000,
    });

    // Parcours réel au clavier : on avance jusqu'à ce qu'une case du guide ait
    // le focus. On s'arrête au premier `a[title]`, sans cliquer.
    let reached = false;
    for (let i = 0; i < 80; i += 1) {
      await page.keyboard.press("Tab");
      const focusedTitle = await page.evaluate(() => {
        const el = document.activeElement as HTMLElement | null;
        return el && el.tagName === "A" && el.hasAttribute("title") ? el.getAttribute("title") : null;
      });
      if (focusedTitle) {
        reached = true;
        break;
      }
    }
    expect(reached, "le focus clavier n'atteint aucune case de la grille").toBe(true);

    await page.keyboard.press("Enter");
    await expect(page.getByRole("dialog", { name: fr.App.programmeSheetLabel })).toBeVisible();
    await page.keyboard.press("Escape");
    await expect(page.getByRole("dialog", { name: fr.App.programmeSheetLabel })).toHaveCount(0);
  });
});
