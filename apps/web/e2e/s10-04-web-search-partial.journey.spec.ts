import { expect, test, type Page } from "@playwright/test";
import fr from "../src/messages/fr.json";
import { sessionPathFor } from "./support/stack";

/** Compte dédié à ce fichier (S10-01 web), comme tout `*.journey.spec.ts`. */
test.use({ storageState: sessionPathFor("s10-04-web-search-partial") });

/**
 * S10-04 — erreur partielle web : une section échoue, les autres restent.
 *
 * <h2>La panne est injectée, pas simulée</h2>
 *
 * `..` exige qu'UNE section ne réponde pas pendant que les autres répondent.
 * La pile est dédiée : un proxy Node relaie tout à lumo-api sauf les lectures
 * de films (`/vod`), qui répondent 503
 * (`qa-evidence/s10-05-web-2026-10-04/search-fault-proxy.mjs`). La variable
 * `E2E_SEARCH_FAULT_ARMED=1` arme à la fois la pile (`E2E_API_PORT` du proxy) et
 * ce fichier, donc les deux ne peuvent pas se contredire.
 *
 * <h2>Ce qui doit tenir</h2>
 *
 * La section en échec porte son message et son réessai ; les sections qui ont
 * répondu restent affichées ; et l'ensemble n'est JAMAIS présenté comme vide
 * (Q9, SR-10).
 */

const MIXED = "http://bench/mixed.m3u";
const LABEL = "Banc S10-04 partiel";

test.skip(
  !process.env.E2E_SEARCH_FAULT_ARMED,
  "requiert un proxy 503 sur `/vod` (E2E_SEARCH_FAULT_ARMED=1, E2E_API_PORT=18082)",
);

async function createMixedSource(page: Page): Promise<void> {
  await page.goto("/fr/app/sources/new");
  await page.getByLabel(fr.App.sourceLabelLabel).fill(LABEL);
  await page.getByLabel(fr.App.sourceM3uUrlLabel).fill(MIXED);
  await page.getByRole("button", { name: fr.App.sourceSubmit }).click();
  await expect(page.getByText(fr.App.sourceReadyTitle)).toBeVisible({ timeout: 60_000 });
}

test.setTimeout(120_000);

test.describe.serial("S10-04 — erreur partielle web", () => {
  test("préparation — source mixte (chaînes et films)", async ({ page }) => {
    await createMixedSource(page);
  });

  test("une section en échec garde les autres et n'est jamais rendue comme vide", async ({
    page,
  }) => {
    // « a » correspond à des chaînes (« Chaîne… ») et à des films (« Le Voyage »).
    await page.goto("/fr/app/search?q=a");

    // Chaînes : la section a répondu et reste affichée.
    const channels = page.getByRole("region", { name: fr.App.searchSectionChannels });
    await expect(channels).toBeVisible({ timeout: 30_000 });

    // Films : la seule section en échec, nommée, avec son propre réessai.
    const films = page.getByRole("region", { name: fr.App.searchSectionFilms });
    await expect(films).toBeVisible();
    const alert = films.getByRole("alert");
    await expect(alert).toBeVisible();
    await expect(alert).toContainText(fr.App.searchSectionFailed);
    await expect(films.getByRole("button", { name: fr.App.searchRetry })).toBeVisible();

    // Le motif de rejet : un échec partiel ne devient jamais « aucun résultat ».
    await expect(page.getByRole("link", { name: fr.App.searchClear })).toHaveCount(0);
  });
});
