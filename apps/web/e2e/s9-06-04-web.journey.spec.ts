import { expect, test, type Page } from "@playwright/test";
import fr from "../src/messages/fr.json";
import en from "../src/messages/en.json";

/**
 * Recette web S9-06-04 (GD-07/08/10/11), jouée par QA contre l'artefact
 * `feat/S9-06-04-web-programme-sheet` @ 943d27e.
 *
 * Harnais : pile e2e Playwright. Le banc est ancré sur le même instant que le
 * `LUMO_NOW` du serveur web (BENCH_EPG_ANCHOR == LUMO_NOW), donc la fiche
 * `guide-transition.xml` (Game B) offre les deux transitions sans attente
 * réelle : E1 court de T−30 min à T+2 min, E2 de T+3 min à T+33 min.
 *
 * La fiche est un composant client : sa durée de vie part de l'instant serveur
 * (`nowIso`) et avance au temps écoulé `performance.now()`. Un remontage du
 * composant repart donc de T ; cela rend chaque cas rejouable, où qu'on soit
 * dans la vraie journée. Les cas temporels utilisent `page.clock` pour franchir
 * la transition sans attendre 2 ou 3 minutes réelles.
 */

const LABEL = "Banc QA S9-06-04";
const M3U = process.env.QA_M3U_URL ?? "http://bench/playlist.m3u";
const EPG = process.env.QA_EPG_URL ?? "http://bench/guide-transition.xml";

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

/** The Guide view of the source, on the pinned day. */
function guideHref(sourceId: string): string {
  return `/fr/app/sources/${sourceId}/channels?view=guide`;
}

/** The grid link of a named programme, without opening it. */
async function programmeHref(page: Page, title: string): Promise<string> {
  const link = page.locator(`a[title="${title}"]`);
  await expect(link).toBeVisible({ timeout: 30_000 });
  const href = await link.getAttribute("href");
  if (!href) throw new Error(`pas de href pour ${title}`);
  return href;
}

async function openGuide(page: Page, sourceId: string) {
  await page.goto(guideHref(sourceId));
  await expect(page.getByRole("link", { name: fr.App.directViewGuide })).toHaveAttribute(
    "aria-current",
    "page",
  );
}

test.setTimeout(180_000);

test.describe.serial("S9-06-04 — fiche programme web (GD-07/08/10/11)", () => {
  let sourceId = "";
  let e1Href = "";
  let e2Href = "";

  test("préparation — source de banc avec guide (Game B) et grille du Guide", async ({ page }) => {
    sourceId = await createSource(page);
    await openGuide(page, sourceId);
    // La grille a bien le programme E1 (courant à T) et E2 (futur à T).
    await expect(page.locator('a[title="E1"]')).toBeVisible({ timeout: 30_000 });
    await expect(page.locator('a[title="E2"]')).toBeVisible();
    e1Href = await programmeHref(page, "E1");
    e2Href = await programmeHref(page, "E2");
  });

  test("QA-06-04-01 — fiche d'un programme courant : action visible", async ({ page }) => {
    await page.goto(e1Href);

    const sheet = page.getByRole("dialog", { name: fr.App.programmeSheetLabel });
    await expect(sheet).toBeVisible();
    await expect(sheet.getByRole("heading", { name: "E1" })).toBeVisible();
    const watch = sheet.getByRole("link", { name: fr.App.programmeWatch });
    await expect(watch).toBeVisible();
    await expect(sheet.getByRole("link", { name: fr.App.programmeClose })).toBeVisible();
  });

  test("QA-06-04-02 — fiche d'un programme futur : aucune action", async ({ page }) => {
    await page.goto(e2Href);

    const sheet = page.getByRole("dialog", { name: fr.App.programmeSheetLabel });
    await expect(sheet.getByRole("heading", { name: "E2" })).toBeVisible();
    await expect(sheet.getByRole("link", { name: fr.App.programmeWatch })).toHaveCount(0);
    await expect(sheet.getByRole("link", { name: fr.App.programmeClose })).toBeVisible();
  });

  test("QA-06-04-04 — GD-07 : l'action disparaît à la fin, fiche ouverte, focus sur Fermer", async ({
    page,
  }) => {
    await page.clock.install();
    let loads = 0;
    page.on("load", () => {
      loads += 1;
    });
    await page.goto(e1Href);
    const url = page.url();
    expect(loads).toBe(1);

    const sheet = page.getByRole("dialog", { name: fr.App.programmeSheetLabel });
    const watch = sheet.getByRole("link", { name: fr.App.programmeWatch });
    const close = sheet.getByRole("link", { name: fr.App.programmeClose });
    await expect(watch).toBeVisible();
    // Le titre est capturé : il ne doit pas changer sous la fiche.
    await expect(sheet.getByRole("heading", { name: "E1" })).toBeVisible();

    // E1 finit à T+2 min. On avance au-delà, sans rechargement.
    await page.clock.fastForward(125_000);

    await expect(watch).toHaveCount(0, { timeout: 5_000 });
    await expect(sheet).toBeVisible();
    await expect(sheet.getByRole("heading", { name: "E1" })).toBeVisible();
    // GD-07 : le focus rejoint Fermer plutôt que de tomber au document.
    await expect(close).toBeFocused();
    // « Sans rechargement » : aucune navigation, l'URL n'a pas bougé.
    expect(loads).toBe(1);
    expect(page.url()).toBe(url);
  });

  test("QA-06-04-05 — GD-08 : l'action apparaît au démarrage, sans voler le focus", async ({
    page,
  }) => {
    await page.clock.install();
    let loads = 0;
    page.on("load", () => {
      loads += 1;
    });
    await page.goto(e2Href);
    expect(loads).toBe(1);

    const sheet = page.getByRole("dialog", { name: fr.App.programmeSheetLabel });
    const watch = sheet.getByRole("link", { name: fr.App.programmeWatch });
    const close = sheet.getByRole("link", { name: fr.App.programmeClose });
    await expect(sheet.getByRole("heading", { name: "E2" })).toBeVisible();
    await expect(watch).toHaveCount(0);
    // L'arrivée d'une fiche future pose le focus sur Fermer.
    await expect(close).toBeFocused();

    // E2 démarre à T+3 min.
    await page.clock.fastForward(185_000);

    await expect(watch).toBeVisible({ timeout: 5_000 });
    // Le focus ne doit pas avoir été volé par l'apparition de l'action.
    await expect(close).toBeFocused();
    // « Sans rechargement » : aucune navigation.
    expect(loads).toBe(1);
  });

  test("QA-06-04-09 — nom lisible sans logo, pas de zone description vide", async ({ page }) => {
    await page.goto(e1Href);

    const sheet = page.getByRole("dialog", { name: fr.App.programmeSheetLabel });
    // Le repère neutre de ChannelLogo : l'initiale du nom, sans logo fictif.
    await expect(sheet.getByText("Chaîne 01 FHD", { exact: false })).toBeVisible();
    // E1 n'a pas de description : aucune zone vide n'est rendue.
    const paragraphs = sheet.locator("p");
    await expect(paragraphs.filter({ hasText: /^\s*$/ })).toHaveCount(0);
  });

  test("QA-06-04-10 — FR puis EN : libellés traduits, aucune clé brute", async ({ page }) => {
    await page.goto(e1Href.replace("/fr/", "/en/"));

    const sheet = page.getByRole("dialog", { name: en.App.programmeSheetLabel });
    await expect(sheet.getByRole("link", { name: en.App.programmeWatch })).toBeVisible();
    await expect(sheet.getByRole("link", { name: en.App.programmeClose })).toBeVisible();
    const html = await page.content();
    expect(html).not.toContain("programmeSheetLabel");
    expect(html).not.toContain("programmeWatch");
    expect(html).not.toContain("programmeClose");
  });

  test("QA-06-04-12 — Échap ferme la fiche d'abord, focus visible et non piégé", async ({
    page,
  }) => {
    await page.goto(e1Href);

    const sheet = page.getByRole("dialog", { name: fr.App.programmeSheetLabel });
    await expect(sheet).toBeVisible();
    // Le focus d'arrivée est visible (sur le bouton, courant).
    await expect(sheet.getByRole("link", { name: fr.App.programmeWatch })).toBeFocused();

    await page.keyboard.press("Escape");

    // La fiche se ferme, la grille reste.
    await expect(sheet).toHaveCount(0);
    await expect(page.locator('a[title="E1"]')).toBeVisible();
    await expect(page).not.toHaveURL(/programme=/);
  });
});
