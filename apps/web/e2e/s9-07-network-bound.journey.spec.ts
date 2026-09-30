import { execFileSync } from "node:child_process";
import { expect, test, type Page } from "@playwright/test";
import fr from "../src/messages/fr.json";

/**
 * S9-07 §5.2 — la preuve réseau web : le nombre d'appels
 * `GET /v1/sources/{id}/epg` est petit et **indépendant du nombre de chaînes
 * affichées**.
 *
 * La lecture du guide part du serveur Next (le chargeur est `server-only`),
 * donc `page.on("request")` ne la voit jamais. On compte là où elle atterrit :
 * dans le journal de l'API, que la pile e2e fait tourner avec le profil
 * `epg-logging` (I-4). Le conteneur est `lumo-e2e-api` (surchargeable par
 * `LUMO_API_CONTAINER`), comme le compte `apps/web/e2e/bench/count-epg.sh`.
 *
 * Répété à 3, 50 puis 100 chaînes (Game D). Les playlists `playlist-3.m3u` et
 * `playlist-50.m3u` sont dérivées dans le conteneur de banc à partir des
 * fixtures committées (voir le RAPPORT), pour comparer le même écran à volume
 * croissant sans multiplier les fixtures versionnées.
 */

const API_CONTAINER = process.env.LUMO_API_CONTAINER ?? "lumo-e2e-api";

/** Le compte de lignes `GET .../epg` du journal de l'API, à l'instant T. */
function epgCalls(): number {
  const out = execFileSync(
    "sh",
    [
      "-c",
      `docker logs ${API_CONTAINER} 2>&1 | grep -cE 'GET "/v1/sources/[0-9a-f-]+/epg' || true`,
    ],
    { encoding: "utf8" },
  );
  return Number(out.trim() || "0");
}

interface Volume {
  readonly channels: number;
  readonly playlist: string;
  readonly epg: string;
}

const VOLUMES: readonly Volume[] = [
  { channels: 3, playlist: "playlist-3.m3u", epg: "guide.xml" },
  { channels: 50, playlist: "playlist-50.m3u", epg: "guide-big.xml" },
  { channels: 100, playlist: "playlist-100.m3u", epg: "guide-big.xml" },
];

async function createSource(page: Page, volume: Volume): Promise<string> {
  const label = `Banc QA S9-07 volume ${volume.channels}`;
  await page.goto("/fr/app/sources/new");
  await page.getByLabel(fr.App.sourceLabelLabel).fill(label);
  await page.getByLabel(fr.App.sourceM3uUrlLabel).fill(`http://bench/${volume.playlist}`);
  await page.getByLabel(fr.App.sourceEpgUrlLabel).fill(`http://bench/${volume.epg}`);
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

test.setTimeout(240_000);

test.describe.serial("S9-07 §5.2 — preuve réseau web (I-4)", () => {
  for (const volume of VOLUMES) {
    test(`${volume.channels} chaînes : appels EPG bornés et indépendants du volume`, async ({
      page,
    }) => {
      const sourceId = await createSource(page, volume);

      const before = epgCalls();
      await page.goto(`/fr/app/sources/${sourceId}/channels?view=guide`);
      await expect(
        page.getByRole("table", { name: fr.App.directGuideTitle }),
      ).toBeVisible({ timeout: 30_000 });
      const after = epgCalls();
      const delta = after - before;

      // Visible dans le journal de la campagne : le nombre par volume y est
      // recopié tel quel, jamais lissé.
      console.log(`[I-4] ${volume.channels} chaînes → ${delta} appel(s) EPG`);

      // « Petit » : un appel groupé en marche normale, `1+2+4` au pire quand la
      // fenêtre est refusée et découpée. Jamais ≈ N.
      expect(delta).toBeGreaterThan(0);
      expect(delta).toBeLessThanOrEqual(8);
    });
  }
});
