import { expect, test } from "@playwright/test";
import fr from "../src/messages/fr.json";

/**
 * QA — l'accueil d'un compte à froid, écrit depuis le critère du PO.
 *
 * Le défaut #1 (passe QA du 26 septembre, `qa-evidence/PASS-2026-09-26.md`) :
 * sur une source prête et **aucun historique**, `ChannelRail` renvoyait `null`
 * quand `rails.recents` était vide, donc les deux portes explicites du rail Live
 * — *Toutes les chaînes* et *Guide TV* — n'existaient pas sur l'accueil. Le
 * critère validé (`docs/roadmap/0.2.0/decisions.md`, « États validés ») exige
 * « Source prête sans activité : accès aux catalogues disponibles ».
 *
 * Le test part de l'état, pas du code : il enregistre une source, ne lit rien,
 * et exige que les deux portes soient là. Il échoue sur le comportement d'avant
 * le correctif (Branche `fix/S9-04-07-01-home-cold-entries`).
 *
 * Le fichier se nomme `…journey.spec.ts` parce qu'il croise la pile (session
 * réelle, lumo-api, PostgreSQL) : il tombe donc dans le projet `journey` de
 * `playwright.config.ts` et n'est joué que là. Il est autonome — il crée lui
 * même l'unique source du compte — pour pouvoir tourner seul.
 */
test.describe.serial("accueil à froid — les portes du Direct (BUG-S9-04-07-01)", () => {
  test("une source prête sans historique garde « Toutes les chaînes » et « Guide TV »", async ({
    page,
  }) => {
    // 1. Une source prête, zéro lecture : l'unique état où le rail Live est vide.
    await page.goto("/fr/app/sources/new");
    await page.getByLabel(fr.App.sourceLabelLabel).fill("Banc à froid");
    await page.getByLabel(fr.App.sourceM3uUrlLabel).fill("http://bench/playlist.m3u");
    await page.getByRole("button", { name: fr.App.sourceSubmit }).click();
    await expect(page.getByText(fr.App.sourceReadyTitle)).toBeVisible({ timeout: 30_000 });

    // 2. L'accueil, à froid. Aucun historique : `rails.recents` est vide.
    await page.goto("/fr/app");

    // Les deux portes de S9-04-07. `exact` pour ne pas confondre avec le
    // « Live » de la navigation d'exploration, qui porte un autre libellé.
    const allChannels = page.getByRole("link", { name: fr.App.homeAllChannels, exact: true });
    const guideTv = page.getByRole("link", { name: fr.App.homeGuideTv, exact: true });
    await expect(allChannels).toBeVisible();
    await expect(guideTv).toBeVisible();

    // 3. Le correctif ne doit pas casser la règle US-017 qu'il côtoie : Favoris
    //    reste un rail vide = rail absent, donc « Voir tous les favoris » n'est
    //    pas là. C'est la preuve que l'opt-in n'a pas fuité d'un rail à l'autre.
    await expect(
      page.getByRole("link", { name: fr.App.catalogueFavoritesSeeAll, exact: true }),
    ).toHaveCount(0);

    // 4. Les portes ouvrent la vue annoncée, sans filtre.
    await guideTv.click();
    await expect(page).toHaveURL(/view=guide/);
    await page.goBack();
    await expect(allChannels).toBeVisible();
    await allChannels.click();
    await expect(page).toHaveURL(/view=channels/);
  });
});
