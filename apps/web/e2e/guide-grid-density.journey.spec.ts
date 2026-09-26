import { expect, test } from "@playwright/test";
import fr from "../src/messages/fr.json";

/**
 * La grille du guide à 1440 px — le défilement est celui de la grille, pas de la
 * page (BUG-S9-05-02-01).
 *
 * <h2>Le défaut, tel que la recette l'a vu</h2>
 *
 * La branche ramenait la colonne-horaire à 128 px, mais l'item de grille qui
 * porte `EpgGrid` gardait le `min-width: auto` par défaut des items de grille.
 * Le tableau de ~3 200 px étirait donc sa colonne au lieu d'être borné par
 * elle : `document.scrollWidth` valait 3 782 px pour un viewport de 1 440, et
 * « Maintenant » se retrouvait à x ≈ 3 531 px. `.overflow-x-auto` ne
 * s'enclenchait jamais. Le correctif est `min-w-0` sur cet item.
 *
 * <h2>Le test part du rendu, pas du code</h2>
 *
 * Il exige la propriété observable — la page ne déborde pas, la grille défile —
 * et pas la présence de la classe. Un guide vide suffit : la grille dessine ses
 * 24 colonnes d'heure même sans programme, et c'est cette largeur qui déborde.
 *
 * Le fichier se nomme `…journey.spec.ts` parce qu'il croise la pile (session
 * réelle, lumo-api, PostgreSQL) : il tombe donc dans le projet `journey` de
 * `playwright.config.ts` et n'est joué que là. Il crée lui-même l'unique source
 * du compte, pour pouvoir tourner seul.
 */
test.describe.serial("grille du guide — densité 1440 px (BUG-S9-05-02-01)", () => {
  test("la grille défile dans sa cellule, la page ne défile pas", async ({ page }) => {
    await page.setViewportSize({ width: 1440, height: 900 });

    // 1. Une source prête : cinq chaînes, aucune lecture.
    await page.goto("/fr/app/sources/new");
    await page.getByLabel(fr.App.sourceLabelLabel).fill("Grille densité");
    await page.getByLabel(fr.App.sourceM3uUrlLabel).fill("http://bench/playlist.m3u");
    await page.getByRole("button", { name: fr.App.sourceSubmit }).click();
    await expect(page.getByText(fr.App.sourceReadyTitle)).toBeVisible({ timeout: 30_000 });

    // 2. Le catalogue de cette source, puis sa vue Guide (`?view=guide`).
    await page.goto("/fr/app/sources");
    await page.getByRole("link", { name: "Grille densité", exact: true }).click();
    await page.getByRole("link", { name: fr.App.sourceOpenCatalogue }).click();
    await page.getByRole("link", { name: fr.App.directViewGuide, exact: true }).click();
    await expect(page).toHaveURL(/view=guide/);

    // 3. La grille est là, et c'est bien elle qui porte le défilement.
    //
    // **Le banc ne sert pas encore d'XMLTV** (S9-07-03) : une source
    // enregistrée depuis `playlist.m3u` n'a pas de guide configuré, et
    // `EpgGrid` ne dessine alors rien (S7-03). L'assertion de mise en page n'a
    // de sens qu'avec la grille ; tant que le harnais n'est pas là, le test est
    // **sauté avec sa raison** plutôt que vert sur une page vide, et il
    // s'activera de lui-même le jour où le banc servira un guide.
    const grid = page.getByRole("table", { name: fr.App.directGuideTitle });
    if ((await grid.count()) === 0) {
      test.skip(true, "le banc ne sert pas d'XMLTV (S9-07-03) : la grille ne se dessine pas");
    }
    await expect(grid).toBeVisible();

    // Le conteneur de défilement est celui de la grille.
    const scroller = page.locator(".overflow-x-auto").first();
    await expect(scroller).toBeVisible();

    // Le défaut : le tableau étire la page entière. La page doit tenir dans le
    // viewport.
    const overflow = await page.evaluate(() => ({
      document: document.documentElement.scrollWidth,
      viewport: window.innerWidth,
    }));
    expect(overflow.document).toBeLessThanOrEqual(overflow.viewport);

    // Et la grille, elle, doit bel et bien défiler : sa largeur interne dépasse
    // sa largeur visible.
    const cell = await scroller.evaluate((element) => ({
      client: element.clientWidth,
      scroll: element.scrollWidth,
    }));
    expect(cell.scroll).toBeGreaterThan(cell.client);

    // 4. « Maintenant » reste hors du scroll horizontal : visible à 1440 px, et
    // pas repoussé à ~3 500 px comme sur le comportement d'avant.
    const now = page.getByRole("link", { name: fr.App.directNowButton, exact: true });
    await expect(now).toBeVisible();
    const box = await now.boundingBox();
    expect(box).not.toBeNull();
    expect((box?.x ?? 0) + (box?.width ?? 0)).toBeLessThanOrEqual(1440);
  });
});
