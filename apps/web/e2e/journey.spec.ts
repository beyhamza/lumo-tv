import { expect, test, type Page } from "@playwright/test";
import fr from "../src/messages/fr.json";
import { SESSION_FILE } from "./support/stack";

/**
 * What only a stack can test.
 *
 * `zones.spec.ts` checks properties of the web application on its own — the
 * routing, the metadata, the cookie, the no-JavaScript path — and needs no
 * server behind it. Everything here crosses the boundary: a real session, a
 * real HTTP call to lumo-api, a real row in a real PostgreSQL.
 *
 * The session comes from `auth.setup.ts`, which signs up through the form.
 *
 * Wording is read from the message catalogue rather than retyped, so renaming
 * a label is a translation change and not a broken test.
 */

test.describe("espace compte", () => {
  test("la liste des sources vient de l'API, pas d'un état de repli", async ({
    page,
  }) => {
    await page.goto("/fr/app/sources");

    // The account was created seconds ago, so the honest answer is an empty
    // list — and it is an *answer*: reaching this text means the request was
    // authenticated, lumo-api replied 200, and it read an empty table. The
    // failure this catches is the one that looks like success: an API that is
    // down renders the "service unavailable" panel instead, and both are calm
    // grey boxes to anyone reading a screenshot.
    await expect(page.getByText(fr.App.sourcesEmpty)).toBeVisible();
    await expect(page.getByText(fr.App.unavailableTitle)).toHaveCount(0);
  });

  test("la liste des appareils montre la session qui la consulte", async ({ page }) => {
    await page.goto("/fr/app/devices");

    // This screen used to assert "not built yet": `GET /me/devices` was in the
    // contract with no controller behind it. It has one now, so the assertion
    // is the opposite one — and it is worth more, because it crosses the whole
    // stack. Signing up opened exactly one session, so exactly one device is
    // linked, and it is a WEB one: this browser.
    await expect(page.getByText("WEB")).toBeVisible();
    // Neither of the two fallbacks. "Not built yet" would mean the controller
    // vanished; "unavailable" would mean the API is down. Both are calm grey
    // boxes to anyone reading a screenshot, which is exactly why they are
    // asserted away rather than eyeballed.
    await expect(page.getByText(fr.App.notImplementedBadge)).toHaveCount(0);
    await expect(page.getByText(fr.App.unavailableTitle)).toHaveCount(0);
  });
});

test.describe("activation", () => {
  test("le champ de code prend le focus et arrive pré-rempli", async ({ page }) => {
    // Was `test.fixme` in zones.spec.ts: the form only renders for a signed-in
    // visitor, so it needed a session, so it needed an API. It has both now.
    await page.goto("/fr/activate?code=ABCD2345");

    const field = page.getByLabel(fr.Activate.codeLabel);

    await expect(field).toBeFocused();
    await expect(field).toHaveValue("ABCD2345");
  });

  test("un code inconnu est refusé par l'API, sans perdre la saisie", async ({
    page,
  }) => {
    await page.goto("/fr/activate");

    // Eight characters from the contract's alphabet, so validation passes and
    // the request really reaches lumo-api, which has no such authorization.
    // This is the round trip: server action, 404, problem+json, and a message
    // chosen from `code` rather than from an HTTP status.
    await page.getByLabel(fr.Activate.codeLabel).fill("ZZZZ9999");
    await page.locator('button[type="submit"]').click();

    await expect(page).toHaveURL(/error=/);
    // The code survives the failure: whoever is standing in front of their
    // television should not have to read it off the screen twice.
    await expect(page).toHaveURL(/code=ZZZZ9999/);
  });
});

/**
 * Registering a source, from the form to a usable catalogue (US-06, US-07).
 *
 * Serial, because it is one flow rather than four independent checks: the
 * account created by `auth.setup.ts` is on the free plan, which allows exactly
 * one source, and that ceiling is itself part of what is being verified. Each
 * test leaves the account in the state the next one needs.
 *
 * Everything points at the bench container (`docker-compose.e2e.yml`), never at
 * a real provider: a qualification run that depends on somebody's IPTV
 * subscription is a qualification run that fails on a Sunday for reasons nobody
 * can reproduce.
 */
test.describe.serial("sources", () => {
  test("une playlist enregistrée devient une source prête", async ({ page }) => {
    await page.goto("/fr/app/sources/new");

    await page.getByLabel(fr.App.sourceLabelLabel).fill("Banc d'essai");
    await page.getByLabel(fr.App.sourceM3uUrlLabel).fill("http://bench/playlist.m3u");
    await page.getByRole("button", { name: fr.App.sourceSubmit }).click();

    // The POST answers 202 and the catalogue is not there yet, so the landing
    // page is the wait. Ingestion of five channels is quick, so what is asserted
    // is the end state — reaching it at all proves the whole chain: server
    // action, API, ingestion worker, PostgreSQL, and the polling screen.
    await expect(page.getByText(fr.App.sourceReadyTitle)).toBeVisible({
      timeout: 30_000,
    });

    // Five channels in three groups: two named ones plus the bucket the server
    // invents for the entry with no group-title.
    await expect(page.getByText("5 chaînes · 3 catégories")).toBeVisible();
  });

  test("le catalogue liste les chaînes, avec numéro et qualité", async ({ page }) => {
    await page.goto("/fr/app/sources");
    await page.getByRole("link", { name: "Banc d'essai", exact: true }).click();
    await page.getByRole("link", { name: fr.App.sourceOpenCatalogue }).click();

    await expect(page.getByText("5 chaînes", { exact: true })).toBeVisible();

    // Number and quality come from the playlist and are rendered as the source
    // wrote them: tvg-chno="1", and FHD read out of the name without rewriting
    // the name itself.
    const first = channels(page).getByRole("listitem").filter({ hasText: "Chaîne 01 FHD" });
    await expect(first).toContainText("1");
    await expect(first).toContainText("FHD");

    // A channel the playlist gave no number and no quality shows neither, rather
    // than a zero or an invented badge.
    await expect(
      channels(page).getByRole("listitem").filter({ hasText: "Chaîne 05" }),
    ).toBeVisible();
  });

  test("catégorie et recherche passent par l'URL, donc sans JavaScript", async ({
    page,
  }) => {
    await page.goto("/fr/app/sources");
    await page.getByRole("link", { name: "Banc d'essai", exact: true }).click();
    await page.getByRole("link", { name: fr.App.sourceOpenCatalogue }).click();

    // Two channels carry group-title="Sport". Filtering is a link, so the state
    // is in the URL and the back button works.
    await page.getByRole("link", { name: "Sport" }).click();
    await expect(page).toHaveURL(/categoryId=/);
    await expect(channels(page).getByRole("listitem")).toHaveCount(2);

    // The search is a GET form. Case-insensitive substring, which is exactly
    // what the hint promises and nothing more.
    await page.goto("/fr/app/sources");
    await page.getByRole("link", { name: "Banc d'essai", exact: true }).click();
    await page.getByRole("link", { name: fr.App.sourceOpenCatalogue }).click();
    await page.getByLabel(fr.App.catalogueSearchLabel).fill("chaîne 03");
    await page.getByRole("button", { name: fr.App.catalogueSearchSubmit }).click();

    await expect(page).toHaveURL(/q=/);
    await expect(channels(page).getByRole("listitem")).toHaveCount(1);

    await page.getByLabel(fr.App.catalogueSearchLabel).fill("zzzz");
    await page.getByRole("button", { name: fr.App.catalogueSearchSubmit }).click();
    await expect(page.getByText(fr.App.catalogueNoResults)).toBeVisible();
  });

  test("lancer une chaîne demande l'URL au serveur, sans jamais la mettre dans la page", async ({
    page,
  }) => {
    await page.goto("/fr/app/sources");
    await page.getByRole("link", { name: "Banc d'essai", exact: true }).click();
    await page.getByRole("link", { name: fr.App.sourceOpenCatalogue }).click();

    const playbackCalls: string[] = [];
    page.on("request", (request) => {
      if (request.url().includes("/api/playback/")) playbackCalls.push(request.url());
    });

    await page.getByRole("link", { name: "Chaîne 01 FHD" }).click();

    // L'état de lecture est dans l'URL, comme le reste de cet écran.
    await expect(page).toHaveURL(/play=/);
    await expect(page.locator("video")).toBeVisible();

    // L'URL du flux est demandée à la volée par le lecteur, pas rendue par le
    // serveur : elle porte les identifiants du panel de l'utilisateur.
    await expect.poll(() => playbackCalls.length).toBeGreaterThan(0);
    const html = await page.content();
    expect(html).not.toContain("/stream/index.m3u8");
  });

  test("un flux que le navigateur ne peut pas ouvrir le dit, et propose la sortie", async ({
    page,
  }) => {
    // La playlist du banc pointe des flux qui n'existent pas et dont l'hôte
    // n'est même pas résolvable depuis le navigateur — exactement ce que fait un
    // panel qui refuse une requête venue d'une page web. Ce que ce test refuse,
    // c'est le carré noir silencieux : c'est lui qui fait conclure que le
    // produit est cassé alors que c'est le fournisseur qui dit non.
    await page.goto("/fr/app/sources");
    await page.getByRole("link", { name: "Banc d'essai", exact: true }).click();
    await page.getByRole("link", { name: fr.App.sourceOpenCatalogue }).click();
    // Chaîne 02 pointe le flux servi SANS en-tête CORS, qui est le cas
    // majoritaire chez les vrais panels.
    await page.getByRole("link", { name: "Chaîne 02" }).click();

    await expect(page.getByText(fr.App.playerFailedTitle)).toBeVisible({
      timeout: 30_000,
    });
    await expect(page.getByText(fr.App.playerBlocked)).toBeVisible();
    // Réessayer ne servirait à rien : la sortie proposée est celle qui existe.
    await expect(page.getByText(fr.App.playerUseApps)).toBeVisible();
  });

  test("un flux que le navigateur peut ouvrir affiche une image", async ({ page }) => {
    await page.goto("/fr/app/sources");
    await page.getByRole("link", { name: "Banc d'essai", exact: true }).click();
    await page.getByRole("link", { name: fr.App.sourceOpenCatalogue }).click();

    // Chaîne 01 pointe le flux servi AVEC son en-tête CORS : le fournisseur qui
    // autorise la lecture dans un navigateur.
    await page.getByRole("link", { name: "Chaîne 01 FHD" }).click();

    const video = page.locator("video");
    await expect(video).toBeVisible();

    // Pas la présence d'une balise `video` — une image décodée. readyState >= 2
    // veut dire HAVE_CURRENT_DATA : le navigateur tient la frame courante.
    await expect
      .poll(() => video.evaluate((element: HTMLVideoElement) => element.readyState), {
        timeout: 30_000,
      })
      .toBeGreaterThanOrEqual(2);

    // Et le temps avance, donc ça joue vraiment.
    await expect
      .poll(() => video.evaluate((element: HTMLVideoElement) => element.currentTime), {
        timeout: 15_000,
      })
      .toBeGreaterThan(0);

    await expect(page.getByText(fr.App.playerFailedTitle)).toHaveCount(0);
  });

  test("une chaîne mise en favori le reste, et rend la vue où elle était", async ({
    page,
  }) => {
    await page.goto("/fr/app/sources");
    await page.getByRole("link", { name: "Banc d'essai", exact: true }).click();
    await page.getByRole("link", { name: fr.App.sourceOpenCatalogue }).click();

    // Filtrer d'abord. L'étoile doit revenir sur cette vue-là — catégorie,
    // recherche et page comprises — et pas sur la page 1 de tout : c'est la
    // régression qu'un simple `redirect("/app/sources/x/channels")` produit.
    await page.getByRole("link", { name: "Sport" }).click();
    const filtered = page.url();
    await expect(channels(page).getByRole("listitem")).toHaveCount(2);

    await stars(page, fr.App.catalogueFavoriteAdd).first().click();

    await expect(page).toHaveURL(filtered);
    await expect(stars(page, fr.App.catalogueFavoriteRemove)).toHaveCount(1);

    // Le rail nomme la chaîne. Or `Favorite` ne porte qu'un `channel_id` : ce
    // nom ne peut venir que du paramètre `ids` de GET /sources/{id}/channels.
    const favorites = rail(page, fr.App.catalogueFavoritesTitle).getByRole("listitem");
    await expect(favorites).toHaveCount(1);
    await expect(favorites.first()).toContainText("Chaîne 03 HD");

    // Rechargement : l'étoile pleine ne vient pas d'un état de page mais d'un
    // GET /me/favorites, donc d'une ligne écrite en base par lumo-api.
    await page.reload();
    await expect(stars(page, fr.App.catalogueFavoriteRemove)).toHaveCount(1);

    await stars(page, fr.App.catalogueFavoriteRemove).click();
    await expect(stars(page, fr.App.catalogueFavoriteRemove)).toHaveCount(0);
    await expect(stars(page, fr.App.catalogueFavoriteAdd)).toHaveCount(2);

    // Et le rail disparaît avec son dernier favori, plutôt que de laisser un
    // titre au-dessus d'une bande vide.
    await expect(rail(page, fr.App.catalogueFavoritesTitle)).toHaveCount(0);
  });

  test("le rail des chaînes récentes nomme une chaîne absente de la page", async ({
    page,
  }) => {
    await page.goto("/fr/app/sources");
    await page.getByRole("link", { name: "Banc d'essai", exact: true }).click();
    await page.getByRole("link", { name: fr.App.sourceOpenCatalogue }).click();

    // Chaîne 01 a été lancée par les tests de lecture plus haut, et le lecteur a
    // envoyé PUT /me/recent-channels au démarrage — jamais au survol.
    const recent = rail(page, fr.App.catalogueRecentTitle);
    await expect(recent.getByRole("listitem").first()).toContainText("Chaîne 01 FHD");

    // Le vrai test est ici : filtrer sur Sport retire Chaîne 01 de la liste, et
    // le rail continue de la nommer. C'est exactement ce que le catalogue local
    // d'Android fait, et ce que `ids` rend possible sans catalogue local.
    await page.getByRole("link", { name: "Sport" }).click();
    await expect(channels(page).getByRole("listitem")).toHaveCount(2);
    await expect(recent.getByRole("listitem").first()).toContainText("Chaîne 01 FHD");

    // Et une carte du rail lance la chaîne sans quitter la vue filtrée.
    await recent.getByRole("link", { name: "Chaîne 01 FHD" }).click();
    await expect(page).toHaveURL(/categoryId=/);
    await expect(page).toHaveURL(/play=/);
    await expect(page.locator("video")).toBeVisible();
  });

  test("l'étoile fonctionne sans JavaScript", async ({ browser }) => {
    // Une Server Action posée sur `<form action={...}>` est soumise par le
    // navigateur lui-même quand l'hydratation n'a pas lieu. C'est la promesse
    // d'AGENTS.md §3 pour tous les formulaires de cette zone, et elle ne vaut
    // que si quelque chose la vérifie : un `onClick` déguisé passerait tous les
    // autres tests.
    const context = await browser.newContext({
      javaScriptEnabled: false,
      storageState: SESSION_FILE,
    });
    const page = await context.newPage();

    await page.goto("/fr/app/sources");
    await page.getByRole("link", { name: "Banc d'essai", exact: true }).click();
    await page.getByRole("link", { name: fr.App.sourceOpenCatalogue }).click();

    await stars(page, fr.App.catalogueFavoriteAdd).first().click();
    await expect(stars(page, fr.App.catalogueFavoriteRemove)).toHaveCount(1);

    await stars(page, fr.App.catalogueFavoriteRemove).click();
    await expect(stars(page, fr.App.catalogueFavoriteRemove)).toHaveCount(0);

    await context.close();
  });

  test("le plafond de l'offre remplace le bouton d'ajout", async ({ page }) => {
    await page.goto("/fr/app/sources");

    // FREE allows one source and one is registered. The ceiling is read from
    // GET /me/entitlement, never written into the web: the day the free plan
    // allows two, this screen follows without a release.
    await expect(page.getByText(fr.App.sourcesLimitBody)).toBeVisible();
    await expect(
      page.getByRole("link", { name: fr.App.sourcesAddCta }),
    ).toHaveCount(0);
  });

  test("supprimer la source rend la place", async ({ page }) => {
    await page.goto("/fr/app/sources");
    await page.getByRole("link", { name: "Banc d'essai", exact: true }).click();

    await page.getByRole("link", { name: fr.App.sourceDelete }).click();
    // The confirmation is a query parameter, not a dialog: it works without
    // JavaScript, it names the source, and it says what goes with it and what
    // does not (US-024, "Suppression").
    await expect(
      page.getByRole("heading", {
        name: fr.App.sourceDeleteConfirmTitle.replace("{label}", "Banc d'essai"),
      }),
    ).toBeVisible();
    await expect(page.getByText(fr.App.sourceDeleteConfirmRemoves)).toBeVisible();
    await expect(page.getByText(fr.App.sourceDeleteConfirmKeeps)).toBeVisible();
    await expect(page.getByText(fr.App.sourceDeleteConfirmProvider)).toBeVisible();
    await expect(page.getByText(fr.App.sourceDeleteConfirmNoRestore)).toBeVisible();
    // Cancel comes before the destructive button, for the keyboard.
    await expect(page.getByRole("link", { name: fr.App.sourceDeleteCancel })).toBeVisible();
    await page.getByRole("button", { name: fr.App.sourceDeleteConfirm }).click();

    await expect(page.getByText(fr.App.sourcesEmpty)).toBeVisible();
    await expect(
      page.getByRole("link", { name: fr.App.sourcesAddCta }),
    ).toBeVisible();
  });

  test("une adresse qui ne renvoie pas une playlist est nommée comme telle", async ({
    page,
  }) => {
    await page.goto("/fr/app/sources/new");

    await page.getByLabel(fr.App.sourceLabelLabel).fill("Page HTML");
    // HTTP 200 with an HTML body: what a mistyped address usually returns.
    await page
      .getByLabel(fr.App.sourceM3uUrlLabel)
      .fill("http://bench/not-a-playlist.html");
    await page.getByRole("button", { name: fr.App.sourceSubmit }).click();

    await expect(page.getByText(fr.App.sourceErrorTitle)).toBeVisible({
      timeout: 30_000,
    });
    // The specific code, not a generic failure. "Your playlist is empty" would
    // send the user looking in the wrong place.
    await expect(page.getByText(fr.Errors.SOURCE_INVALID_FORMAT)).toBeVisible();
  });

  test("une playlist au-delà du plafond est refusée sur sa taille", async ({
    page,
  }) => {
    // The sixth path of the bench, and the last one S2-03 was missing.
    //
    // What it actually exercises is not "a big file": it is that the API caps
    // the bytes it READS. The bench sends a few hundred kilobytes of gzip that
    // expand past two hundred megabytes, which is exactly the case a
    // Content-Length check would wave through — and the case a hostile panel
    // produces on purpose.
    //
    // The free plan allows ONE source and the previous test left one behind.
    // This suite is a chained narrative against a single account, so a test that
    // registers a source has to make room first — the ceiling is the product's,
    // and the test bends rather than the plan.
    await page.goto("/fr/app/sources");
    await page.getByRole("link", { name: "Page HTML" }).click();
    await page.getByRole("link", { name: fr.App.sourceDelete }).click();
    await page.getByRole("button", { name: fr.App.sourceDeleteConfirm }).click();
    await expect(page.getByText(fr.App.sourcesEmpty)).toBeVisible();

    await page.goto("/fr/app/sources/new");

    await page.getByLabel(fr.App.sourceLabelLabel).fill("Trop volumineuse");
    await page
      .getByLabel(fr.App.sourceM3uUrlLabel)
      .fill("http://bench/oversized.m3u");
    await page.getByRole("button", { name: fr.App.sourceSubmit }).click();

    await expect(page.getByText(fr.App.sourceErrorTitle)).toBeVisible({
      // The same budget as its neighbours, and it turns out to be plenty:
      // decoding and walking two hundred megabytes costs the API about a
      // second. Cheap, because the parser skips comment lines without
      // allocating — which is exactly why the fixture is comment lines.
      timeout: 30_000,
    });
    await expect(page.getByText(fr.Errors.SOURCE_TOO_LARGE)).toBeVisible();
  });
});


/**
 * The channel list, told apart from the category navigation beside it.
 *
 * Both are lists of links, so a page-wide `listitem` query matches both — which
 * is how the first version of these assertions came to expect two rows and count
 * six. The channel list carries an accessible name for exactly this reason, and
 * scoping to it is also what the name is for in a screen reader.
 */
function channels(page: Page) {
  return page.getByRole("list", { name: fr.App.catalogueTitle });
}

/**
 * The favourite toggles in one of their two states.
 *
 * Queried by accessible name rather than by class or position: the name is the
 * only thing a screen reader gets out of that button — the glyph inside it is
 * `aria-hidden` — so a test that stops finding it is a test reporting that the
 * control became unreachable.
 */
function stars(page: Page, label: string) {
  return page.getByRole("button", { name: label });
}

/**
 * One of the two rails above the catalogue, by its heading.
 *
 * Same reasoning as {@link channels}: three lists share this page, and a
 * page-wide `listitem` query would count all of them. Each rail carries the
 * accessible name a screen reader announces, so scoping to it here is scoping to
 * the same thing a person using one would hear.
 */
function rail(page: Page, title: string) {
  return page.getByRole("list", { name: title });
}
