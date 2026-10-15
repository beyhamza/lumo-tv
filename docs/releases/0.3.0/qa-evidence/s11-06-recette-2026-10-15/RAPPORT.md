# S11-06 — Recette entre appareils — 15/10/2026

- Objet : lot `S11-06` du [sprint 11](../../../../backlog/sprint-11.md) — « recette entre appareils ;
  vérifier favoris de source distincte et absence d'effet de la lecture sur la liste ». Référentiels :
  [§ Démo et sortie](../../../../backlog/sprint-11.md#démo-et-sortie),
  [US-022](../../../../backlog/stories/US-022-library-watchlist.md),
  [FO-01→12](../../../../design/0.2.0/favorite-organization-cases.md),
  [WL-01→17](../../../../design/0.2.0/watchlist-states.md),
  [G1→G8](../../../../backlog/sprint-11/S11-00-guarantees.md) (G4 sur appareil),
  [US-024 § Suppression](../../../../backlog/stories/US-024-source-management.md),
  [ADR 0011](../../../../adr/0011-watchlist-protocol.md).
- Base : `dev` @ `0de950b` (S11-00 → S11-05 fusionnés). Image `lumo-api` reconstruite depuis cette
  base, profil `dev` ; banc `lumo-bench` vu par l'API en `http://bench/…`.
- Surfaces : web (build de production `pnpm build && pnpm start`, Playwright headless, `/fr` et `/en`),
  **Pixel_10** `tv.lumo.android.debug` en **français** (`set-app-locales fr-FR`, première recette FR
  sur appareil), **Television_1080p** `tv.lumo.androidtv.debug` en **anglais**, au D-pad
  (`input keyevent`, appui long = `--longpress 23`). APK debug construits depuis la même base.
- Comptes créés pour la séance, identifiants hors dépôt (`apps/api/.env.recette`, clés `S1106_*`) :
  **A** `s1106-a-…@test.example` avec **A1** « Banc Xtream A1 » (Xtream, 3 chaînes, 2 films, 2 séries)
  et **A2** « Banc M3U A2 » (`mixed.m3u`, 3 chaînes, 4 films) ; **B** `s1106-b-…@test.example` avec
  « Banc Xtream B ». TV activée par code d'appareil approuvé avec le jeton de A ; téléphone connecté
  à A (puis à B pour l'isolation).
- Données de départ posées par l'API (groupes « Groupe Soir » = X-Ch01, M-Ch01, X-Ch02, M-Ch02 —
  soit A1, B1, A2, B2 au sens FO — et « Groupe Matin » = X-Ch03) ; tous les gestes recettés ont été
  faits **à l'écran**. Disparition d'un contenu provoquée en base (`delete from vod_item / series`),
  panne par `docker stop lumo-api`, hors ligne par coupure du tunnel `adb reverse` du téléphone.

## Verdict

⚠️ **Les règles de fond tiennent sur les trois surfaces** — agrégation sans doublon, ordre par source
(FO-01/03), favoris et liste filtrés par source sans perte, liste partagée et triée de la même façon
partout, lecture sans effet sur la liste, carte « Indisponible » retirable sans fiche, cascade de
suppression de source, isolation entre comptes. **Dix écarts**, dont trois **majeurs** :

| Écart | Surface | Gravité |
|---|---|---|
| **`BUG-S11-06-01`** — supprimer un groupe affiche « Cette modification n'a pas pu être enregistrée » alors que le serveur a accepté (204) ; le bandeau reste jusqu'au redémarrage de l'app | mobile, TV | **majeur** |
| **`BUG-S11-06-02`** — après « This source has been removed… » → *Continue*, la TV revient sur la fiche du film supprimé (affiche vide, *Play*, « ✓ In my list ») au lieu de la navigation de la source restante | TV | **majeur** |
| **`BUG-S11-06-03`** — API en panne : la page Favoris web plante (« This page couldn't load », en anglais sur `/fr`) au lieu de l'état « Données indisponibles » | web | **majeur** |
| **`BUG-S11-06-04`** — hors ligne, la fiche d'un film reste un squelette vide sans titre ni explication ; « Ma liste… » grisé sans raison | mobile | mineur |
| **`BUG-S11-06-05`** — la confirmation web de suppression de source ne cite pas la liste À regarder parmi les données supprimées | web | mineur |
| **`BUG-S11-06-06`** — la carte d'un contenu indisponible n'affiche plus son type (Film / Série), seulement « Indisponible » | mobile, TV | mineur |
| **`BUG-S11-06-07`** — au D-pad, `GAUCHE` depuis le contenu va à l'entrée géométriquement voisine (rail : *My library*, *Search*, *Live TV* ; groupes : *+ New group*), pas à l'entrée courante | TV | mineur |
| **`BUG-S11-06-08`** — confirmation de suppression d'un groupe vide : « Sa 0 chaîne ira dans « Favoris » » / « Its 0 channels will move… » | mobile, TV | cosmétique |
| **`BUG-S11-06-09`** — fiche série : durée des épisodes « 2700 min » (secondes affichées en minutes) sous « ~45 min par épisode » — hors périmètre S11 | mobile | mineur |
| **`BUG-S11-06-10`** — rail TV : l'entrée *Source* n'affiche pas le nom de la source active après un changement de source, ou le tronque (« Banc Xtr… ») | TV | cosmétique |

## Cas joués

Légende : ✅ conforme · ❌ non conforme · — non applicable · ⏸ non joué.

| # | Cas | Web | Mobile (FR) | TV (EN) | Verdict |
|---|---|---|---|---|---|
| R01 | Démo — deux groupes, une chaîne dans les deux → une seule carte agrégée | ✅ « Dans : Groupe Soir, Groupe Matin » (`r01-web-…`) | ✅ Organiser… → Groupe Matin ; *Tous les favoris* = Ch01, Ch02, Ch03 (`r01-mobile-…`) | ✅ accueil et *All favourites* sans doublon | **Conforme** |
| R02 | Démo / FO-01 — ordre mis à jour après déplacement : A1, B1, A2 → A2, B1, A1 | — | ✅ *Monter* sur Chaîne 02 dans Groupe Soir ; API : X-Ch02, M-Ch01, X-Ch01, M-Ch02 (B1 garde l'index 1) (`r02-mobile-…`) | ✅ relu au retour dans l'app : accueil Ch02, Ch01, Ch03 (`r02-tv-…`) ; *Move before* en sens inverse, B2 garde sa place | **Conforme** |
| R03 | FO-02 — A3 en tête dans A1, B1, A2, B2, A3 | | | | **Non joué** — banc à 3 chaînes par source ; garanti par le test unitaire G1 |
| R04 | FO-03 — l'autre source vue sur un autre appareil après le déplacement | ✅ A2 active : Chaîne 01 FHD puis Chaîne 02, « 2 chaînes de ce groupe viennent d'autres sources » (`r04-web-…`) | — | — | **Conforme** |
| R05 | FO-09 — retirer d'un seul groupe | — | — | ✅ *Remove from this group* (sans confirmation, comme prévu) ; l'appartenance à Groupe Matin reste (`r05-tv-…`) | **Conforme** |
| R06 | FO-10 — retirer la première occurrence | — | — | ✅ une seule carte, à la place de l'occurrence restante : Ch02, Ch03, Ch01 (`r06-tv-…`) | **Conforme** |
| R07 | Démo — retirer de tous les groupes avec confirmation | ✅ confirmation qui nomme les 2 groupes, *Annuler* proposé ; seule M-Ch01 part, A1 intacte (`r07-web-…`) | — | ✅ dialogue, **Cancel focalisé par défaut** ; *Cancel* rend le focus à la carte (`r24-tv-…`) | **Conforme** |
| R08 | Démo — supprimer un groupe sans perdre ses favoris | — | ❌ confirmation correcte (« Ses 2 chaînes iront dans « Favoris ». Rien n'est retiré… »), chaînes bien transférées au groupe par défaut **mais faux échec** `BUG-S11-06-01` ; libellé à 0 chaîne `BUG-S11-06-08` | ❌ même faux échec (`r08-bug01-tv-…`) ; *Cancel* focalisé par défaut ✅ | **Non conforme** (`BUG-S11-06-01`, `-08`) |
| R09 | Démo — ajouter film et série sur le téléphone, les retrouver web et TV | ✅ 2 titres, FR et EN (`r09-web-…`) | ✅ « Ajouter à ma liste » → « ✓ Dans ma liste » sur film et série (`r09-mobile-…`) | ✅ mêmes 2 cartes (`r09-tv-…`) | **Conforme** |
| R10 | WL-02 / US-022 — une série = une seule entrée | ✅ | ✅ | ✅ | **Conforme** |
| R11 | Ordre : ajout le plus récent en premier, identique partout | ✅ | ✅ | ✅ Les Falaises puis Le Voyage, puis Le Voyage en tête après réajout | **Conforme** |
| R12 | WL-17 — filtres Tous / Films / Séries ; omis s'il n'y a qu'un type | ✅ `?type=films/series` ; 1 type → pas de filtre | ✅ | ✅ au D-pad (`r12-tv-…`) ; 1 type → pas de filtre (`r15-tv-wl14-…`) | **Conforme** |
| R13 | WL-09 — retrait sur un appareil, ajout sur un autre | ✅ réajout → en tête | ✅ retrait TV vu à la relecture ; réajout web en tête après *Actualiser* (`r13-mobile-…`) | ✅ retrait depuis la fiche : « Add to my list » (`r13-tv-…`) | **Conforme** |
| R14 | WL-13 / G5 — la lecture ne retire rien | ✅ « Le Voyage » toujours là (`r14-web-…`) | ✅ (`r14-mobile-…`) | ✅ lecture de « Le Voyage » (A2, mire de test), progression enregistrée (`position_ms` 6035), élément conservé, `added_at` inchangé (`r14-tv-…`) | **Conforme** |
| R15 | **Favoris de source distincte** / WL-14 — ceux de A1 jamais visibles sous A2 et inversement ; rien de perdu au retour | ✅ A2 : seules les chaînes M3U ; retour A1 : Ch03, Ch01, Ch02 (`r15-web-…`) | ✅ A2 : Chaîne 02 seule ; retour A1 : tout est là (`r15-mobile-…`) | ✅ A2 : Chaîne 02 seule, liste « Le Voyage » (A2) seule ; retour A1 : tout est là (`r15-tv-…`) | **Conforme** |
| R16 | WL-15 — retour au catalogue d'un contenu reconnu | — | ✅ « La Traversée » de nouveau disponible après réactualisation, même élément, même `added_at`, sans doublon (API : nouveau `content_id`, même `id`) | — | **Conforme** |
| R17 | G4 (a) / WL-01, 02, 03 — contenu disparu → « Indisponible », retrait sans fiche | ✅ « Film · Indisponible », « Série · Indisponible », message, *Retirer de ma liste* sans fiche (`r17-web-…`) | ❌ carte « Indisponible » **sans le type** `BUG-S11-06-06` ; panneau compact « Indisponible · Film », *Fermer* / *Retirer de ma liste* ✅ (`r17-mobile-…`) | ❌ carte sans type `BUG-S11-06-06` ; panneau au D-pad, *Close* rend le focus à la carte, retrait → focus sur le titre de section ✅ (`r17-tv-…`) | **Non conforme** (`BUG-S11-06-06`) |
| R18 | G4 (b) — panne (`docker stop lumo-api`) : listes conservées, jamais vides, pas de faux succès ; relecture au retour | ❌ Ma liste et Accueil : « Données indisponibles… » ✅ ; **Favoris : erreur générique anglaise** `BUG-S11-06-03` (`r18-bug03-…`) | ✅ liste conservée, « Hors ligne : voici la liste déjà chargée… », *Retirer* désactivé avec « Retirer nécessite une connexion. » ; favoris consultables (`r18-mobile-…`) ; relecture au changement d'onglet | ✅ « You're offline… » ; options visibles mais aucun envoi ; relu au retour sur l'écran (`r18-tv-…`) | **Non conforme** (`BUG-S11-06-03`) |
| R19 | G4 (c) / WL-06, 08 — hors ligne sur un appareil | — | ❌ liste : consultation seule et *Réessayer* ✅, relecture puis mutations réactivées ✅ ; **fiche film vide sans explication** `BUG-S11-06-04` | ⏸ (voir Non joué) | **Non conforme** (`BUG-S11-06-04`) |
| R20 | G4 (d) / WL-05 / US-024 — suppression de A2 depuis le web pendant que téléphone et TV l'affichent, et pendant une lecture TV | ❌ confirmation complète **sauf la liste À regarder** `BUG-S11-06-05` ; page de la source : « Cette source n'existe plus » | ✅ au retour au premier plan : repli sur A1, liste A1 intacte, aucune carte orpheline (`r20-mobile-…`) | ❌ lecture arrêtée, « This source has been removed from your account. », *Continue* seul, pas d'enchaînement ✅ (`r20-tv-source-…`) ; **mais *Continue* rouvre la fiche du film supprimé** `BUG-S11-06-02` (`r20-bug02-…`) | **Non conforme** (`BUG-S11-06-02`, `-05`) |
| R21 | Démo — supprimer une source de test et conserver les données de l'autre | ✅ | ✅ | ✅ | **Conforme** — API : favoris, liste et progressions de A2 = 0 ; ceux de A1 intacts |
| R22 | Isolation — B ne voit rien de A | ✅ sources, favoris (pas de « Groupe Soir »), liste (`r22-web-…`) | ✅ (`r22-mobile-…`) | ⏸ | **Conforme** — API : `DELETE` d'un élément de A par B → `404 WATCHLIST_ITEM_NOT_FOUND` ; `GET /sources/{A1}` → `404` ; favori sur une chaîne de A → `404 CHANNEL_NOT_FOUND` |
| R23 | FR / EN — pas de chaîne non traduite ni de texte coupé (Ma liste, favoris, dialogues, bandeaux) | ❌ `BUG-S11-06-03` (anglais sur `/fr`) ; reste ✅ en FR et EN | ❌ `BUG-S11-06-08` ; tout le reste en français, sans coupure | ❌ `BUG-S11-06-10` (nom de source tronqué) | **Non conforme** (`-03`, `-08`, `-10`) |
| R24 | Parcours télécommande — Ma liste (filtres, carte, panneau, retour du focus), options par appui long OK, *Move before / after*, confirmations | — | — | ❌ tout est faisable au D-pad, focus rendu à la carte déplacée, à la carte après *Cancel/Close*, au titre après retrait ✅ ; **retour vers le rail / la colonne des groupes imprécis** `BUG-S11-06-07` | **Non conforme** (`BUG-S11-06-07`) |
| R25 | FO-05 → FO-08, FO-11, FO-12 — échecs partiels et concurrence | | | | **Non joué** — demande une injection de panne au milieu d'une séquence ; couvert par les tests G2/G3 de l'API |
| R26 | WL-10, 11, 12 — réessai ≠ nouvelle intention | | | | **Non joué** sur appareil — couvert par `WatchlistIntegrationTest` (G6 → G8) |
| R27 | WL-16 — homonyme d'un contenu disparu | | | | **Non joué** — le banc n'a pas d'homonyme dans une même source |

**Bilan : 16 conformes, 7 non conformes, 4 non joués** (27 cas).

## Écarts

### `BUG-S11-06-01` — faux échec à la suppression d'un groupe (mobile, TV) — majeur

- Étapes : Bibliothèque → appui long sur le groupe « Groupe Matin » → *Supprimer* → *Supprimer le
  groupe*. Reproduit trois fois sur mobile (groupe à 2 chaînes, à 1 chaîne, vide, après redémarrage
  de l'app) et une fois sur TV (« Groupe TV »).
- Attendu : le groupe disparaît, ses chaînes passent dans le groupe par défaut, sans message d'échec ;
  « Le résultat n'est jamais annoncé comme un succès global » (G2) vaut aussi dans l'autre sens : on
  n'annonce pas un échec que le serveur n'a pas eu ([watchlist-states](../../../../design/0.2.0/watchlist-states.md) :
  « vérifier l'état avant d'annoncer réussite ou échec définitif »).
- Observé : `DELETE /me/favorite-groups/{id}` → **204** (journal `LumoHttp`), groupe supprimé et chaînes
  transférées (API), mais bandeau « Cette modification n'a pas pu être enregistrée. La liste montre vos
  favoris tels qu'ils sont. » / « That change could not be saved… ». Le bandeau survit aux changements
  d'écran et de source jusqu'au redémarrage de l'app.
- Piste : `FavoriteRepository.deleteGroup` (`core/data/…/FavoriteRepository.kt:243`) passe par
  `calls.call { … }`, qui exige un corps ; une réponse 204 devient `LumoError.Unreadable(204)`.
  `ApiCaller.empty` existe pour ce cas.
- Captures : `r08-bug01-mobile-faux-echec-suppression-groupe.png`, `r08-bug01-tv-faux-echec-suppression-groupe.png`.

### `BUG-S11-06-02` — *Continue* après suppression de la source rouvre la fiche supprimée (TV) — majeur

- Étapes : TV sur A2, lecture de « Le Voyage » ; suppression de A2 depuis le web ; ~50 s plus tard
  « This source has been removed from your account. » → *Continue*.
- Attendu : US-024 § Suppression — « Proposer Continuer pour revenir à la navigation et appliquer le
  choix de source restante » ; WL-05 / G4 (d) : pas de carte orpheline.
- Observé : la source restante (A1) est bien choisie (rail), mais l'écran affiché est la **fiche du film
  supprimé** : affiche vide, *Play*, « ✓ In my list ». Le focus est sur *Home* du rail.
- Capture : `r20-bug02-tv-fiche-orpheline-apres-continuer.png`.

### `BUG-S11-06-03` — page Favoris web en erreur générique pendant une panne — majeur

- Étapes : `docker stop lumo-api`, ouvrir `/fr/app/favorites` (puis `/en/app/favorites`).
- Attendu : G4 (b) et [watchlist-states](../../../../design/0.2.0/watchlist-states.md) « Chargement échoué :
  explication et Réessayer » ; même traitement que Ma liste et l'Accueil (« Données indisponibles — Le
  service ne répond pas pour le moment »), en français sur `/fr`.
- Observé : page d'erreur Next.js « This page couldn't load — A server error occurred. Reload to try
  again. ERROR 2634748278 », **en anglais sur `/fr`** ; journal serveur `TypeError: fetch failed`
  (`ECONNREFUSED`) non rattrapé.
- Capture : `r18-bug03-web-favoris-panne-erreur-generique.png`.

### `BUG-S11-06-04` — fiche film hors ligne : squelette vide sans explication (mobile) — mineur

- Étapes : liste chargée, tunnel vers l'API coupé ; *Ma liste* → carte « La Traversée ».
- Attendu : WL-06 — « ajout/retrait indisponibles, **avec une explication** ».
- Observé : après plus de 40 s, ni titre, ni message hors ligne ; *Lire* et « Ma liste… » grisés sans
  raison. La liste, elle, explique bien l'état hors ligne.
- Capture : `r19-bug04-mobile-hors-ligne-fiche-vide.png`.

### `BUG-S11-06-05` — confirmation de suppression de source sans la liste À regarder (web) — mineur

- Attendu : US-024 § Suppression — « Supprimer son catalogue de Lumo, ses favoris, progressions et
  **éléments À regarder** » ; la confirmation nomme les conséquences.
- Observé : « Son catalogue, vos favoris, vos progressions de lecture et vos chaînes récentes sur cette
  source seront supprimés. » — la liste n'est pas citée (elle est pourtant bien supprimée : API, 0
  élément de A2).
- Capture : `r20-bug05-web-confirmation-suppression-source.png`.

### `BUG-S11-06-06` — carte indisponible sans type de contenu (mobile, TV) — mineur

- Attendu : [watchlist-states](../../../../design/0.2.0/watchlist-states.md) — « Conserver au minimum un titre
  connu **et le type de contenu** pour identifier une carte indisponible ».
- Observé : la ligne « Film » / « Série » est remplacée par « Indisponible » ; le type n'est que dans la
  description d'accessibilité et dans le panneau. Le web affiche « Film · Indisponible ».
- Captures : `r17-bug06-mobile-indisponibles-sans-type.png`, `r17-bug06-tv-carte-indisponible.png`.

### `BUG-S11-06-07` — retour du focus imprécis au D-pad (TV) — mineur

- Observé, à chaque fois : `GAUCHE` depuis une carte de *My list* donne *My library* ou *Search* ;
  depuis l'accueil, *Series* ou *Live TV* ; depuis les cartes de *My library*, « + New group » au lieu
  du groupe ouvert. Il faut ensuite remonter à la main.
- Attendu : le focus revient à l'entrée courante (celle qui est en surbrillance), règle de navigation TV
  (focus prévisible, retour au déclencheur).
- Pas de capture dédiée (état de focus, relevé par `uiautomator`).

### `BUG-S11-06-08` — « Sa 0 chaîne » (mobile, TV) — cosmétique

- Confirmation de suppression d'un groupe vide : « Sa 0 chaîne ira dans « Favoris ». » /
  « Its 0 channels will move to "Favourites". » — prévoir une formulation pour 0.
- Capture : `r08-bug08-tv-zero-channels.png`.

### `BUG-S11-06-09` — durée des épisodes en secondes affichées en minutes (mobile) — mineur, hors S11

- Fiche « Les Falaises » : en-tête « ~45 min par épisode », épisodes « 2700 min », « 2680 min »,
  « 2710 min ».
- Capture : `bug09-mobile-duree-episodes.png`.

### `BUG-S11-06-10` — nom de la source active absent ou tronqué dans le rail TV — cosmétique

- Après un changement de source par le sélecteur, l'entrée *Source* du rail n'affiche que « Source »
  (pastille de focus vide) ; ailleurs « Banc Xtr… ».
- Capture : `r24-bug10-tv-rail-source-vide.png` (et `r20-bug02-…` pour la troncature).

## Limites connues (pas des écarts)

- TV : *My list* est une entrée séparée du rail (décision produit du 15/10).
- Web : pas de glisser-déposer, boutons *Monter / Descendre* à la place (prévu, S11-03).
- Pas de relecture **automatique** au retour du réseau (S11-04, « reste ») : la TV a gardé « You're
  offline » plus de 25 s après le redémarrage de l'API, jusqu'à une nouvelle navigation ; le mobile se
  relit au changement d'onglet ou sur *Réessayer*.
- Mobile : une source supprimée ailleurs reste affichée tant que l'app reste au premier plan ; elle
  disparaît au retour au premier plan (déjà relevé en R020-03).

## Observations

- Le groupe par défaut naît à la première suppression de groupe, **en position 0** : l'ordre agrégé
  passe de Ch02, Ch03, Ch01 à Ch03, Ch01, Ch02. Conforme au contrat, mais surprenant ; à arbitrer par
  le PO. Son nom serveur est « Favorites », traduit par chaque client (« Favoris » / « Favourites »).
- TV hors ligne : les options d'une chaîne restent sélectionnables (le dialogue rappelle « You're
  offline… ») ; *Remove from all my favourites* ne fait rien et n'annonce rien — pas de faux succès,
  mais un bouton visiblement désactivé, comme sur mobile, serait plus clair.
- Banc : les flux Xtream sont servis en `http://bench/…`, nom résolu seulement dans le réseau Docker ;
  un appareil ne peut pas les lire (« Your server could not be reached »). La lecture a été recettée sur
  la M3U (`http://localhost:18081/…` via `adb reverse`).
- Web : la page d'une source M3U propose « Parcourir les séries » (déjà relevé en R020-03).
- `GET /me/watchlist?sourceId=<source d'un autre compte>` répond `200` liste vide : aucune fuite.
- Web : juste après *Supprimer définitivement*, la page a encore montré la source pendant ~1,5 s avant
  « Cette source n'existe plus » — non reproduit, à surveiller.
- L'accueil web n'a pas de rail *Continuer* après 6 s de lecture : seuil de progression probable, hors
  périmètre.

## Non joué

- R03 (FO-02), R25 (FO-05 → 08, 11, 12), R26 (WL-10 → 12), R27 (WL-16) : voir le tableau.
- Hors ligne **sur la TV** (G4 c) : seule la panne d'API a été jouée sur la TV ; couper le tunnel d'une
  TV dont les connexions sont déjà ouvertes ne coupe rien (connexions OkHttp conservées), il a fallu
  redémarrer `adb` pour le téléphone.
- Compte B sur la TV (isolation jouée sur web, mobile et API).
- TV en français et mobile en anglais (consigne : TV EN, mobile FR).
- Glisser-déposer mobile (le menu *Monter* a servi) ; vraie télécommande.

## État laissé

- Comptes `S1106_A` (source A1 seule, groupes « Favoris » + « Groupe Soir », 4 favoris, liste vide) et
  `S1106_B` (source « Banc Xtream B », 1 favori, 1 élément de liste), base `lumo`. Source A2 supprimée.
- Pixel_10 connecté au compte **B**, en français ; Television_1080p connectée au compte **A** (A1).
- `lumo-api` relancée sur l'image de `dev` @ `0de950b` ; `adb reverse` 8080 et 18081 rétablis sur les
  deux émulateurs.
