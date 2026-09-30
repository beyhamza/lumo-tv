import { expect, test } from "@playwright/test";
import fr from "../src/messages/fr.json";
import { sessionPathFor } from "./support/stack";

/** The account this file signs up for: the one that reaches its ceiling. */
test.use({ storageState: sessionPathFor("s9-sources-ceiling") });

/**
 * Le plafond de sources du compte (S8-06).
 *
 * <h2>Pourquoi un fichier à part, et son propre compte</h2>
 *
 * Monter jusqu'au plafond consomme toutes les places du compte. Un test qui
 * vérifie le plafond ne peut donc pas partager son compte avec un test qui a
 * besoin d'une source — c'est exactement la contention qui faisait échouer huit
 * specs en `SOURCE_LIMIT_REACHED`. Cette assertion vivait dans
 * `journey.spec.ts`, sur le compte partagé, et supposait `max_sources = 1` en
 * dur ; elle a son fichier et son compte depuis.
 *
 * <h2>Le nombre n'est jamais écrit ici</h2>
 *
 * La boucle enregistre des sources jusqu'à ce que l'écran annonce le plafond,
 * puis vérifie l'annonce. `LUMO_PLANS_FREE_MAX_SOURCES` peut passer de 3 à 4
 * sans toucher ce fichier : l'assertion suit l'entitlement du serveur, elle ne
 * le recopie pas. La valeur *shipped* (1) reste prouvée côté API par
 * `PlanLimitsIntegrationTest`, pas ici.
 */
test.setTimeout(120_000);

test("le plafond du compte remplace le bouton d'ajout", async ({ page }) => {
  // Bornée : si le plafond n'est pas atteint en six sources, c'est que l'écran
  // ou la configuration est faux, et l'assertion qui suit le dira. Six est une
  // limite de boucle, jamais la valeur attendue.
  for (let attempt = 0; attempt < 6; attempt += 1) {
    await page.goto("/fr/app/sources");
    if ((await page.getByText(fr.App.sourcesLimitBody).count()) > 0) {
      break;
    }

    await page.goto("/fr/app/sources/new");
    await page.getByLabel(fr.App.sourceLabelLabel).fill(`Plafond ${attempt}`);
    await page.getByLabel(fr.App.sourceM3uUrlLabel).fill("http://bench/playlist.m3u");
    await page.getByRole("button", { name: fr.App.sourceSubmit }).click();
    await expect(page.getByText(fr.App.sourceReadyTitle)).toBeVisible({
      timeout: 30_000,
    });
  }

  await page.goto("/fr/app/sources");

  // Le plafond est dit comme un fait, avec rien à acheter derrière (S8-06), et
  // il remplace le bouton d'ajout plutôt que de le laisser mener à un refus.
  await expect(page.getByText(fr.App.sourcesLimitBody)).toBeVisible();
  await expect(page.getByRole("link", { name: fr.App.sourcesAddCta })).toHaveCount(0);
});
