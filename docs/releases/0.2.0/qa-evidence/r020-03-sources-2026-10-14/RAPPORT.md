# R020-03 — Sources (S8 §5, R-420 → R-428) — 14/10/2026

- Objet : ligne `R020-03` de [`acceptance.md`](../../acceptance.md) — « ajout, états, ancienne donnée
  pendant sync, retry, suppression et remplacement », jouée selon
  [`sprint-08-recette.md`](../../../../backlog/sprint-08-recette.md) §5.
- Base : `dev` @ `ba64f30`. API locale (profil `dev`, `LUMO_MANUAL_SYNC_INTERVAL=PT5M`), banc
  `lumo-bench` vu par l'API en `http://bench/…`. Web `pnpm dev` (navigateur intégré, **français**),
  APK debug sur `Television_1080p` et `Pixel_10` (**anglais**).
- Comptes : `recette-r02003-…@test.example`, créés pour la séance, hors dépôt (`.env.recette`).
- Les états `SYNCING` et `SOURCE_AUTH_FAILED` ont été **forcés en base**, comme le plan le prévoit pour
  `SYNCING` : le banc ingère trop vite pour qu'on voie le premier, et ne sait pas produire le second
  (voir Observations).

## Verdict

⚠️ **Joué sur les trois surfaces ; les règles tiennent, quatre écarts.** L'API passe 30/30 contrôles ;
web, mobile et TV suivent R-420 → R-428 sauf :

| Écart | Surface | Gravité proposée |
|---|---|---|
| **`BUG-R020-03-01`** — aucun formulaire web pour corriger l'adresse ou les identifiants d'une source ; « Corriger les identifiants » et « Corriger la source » ramènent sur la même page | web | moyenne |
| **`BUG-R020-03-02`** — `POST /sources` ignore `auto_sync: false` (la source naît toujours en actualisation automatique) | API | faible |
| **`BUG-R020-03-03`** — la page d'une source supprimée affiche « Le service ne répond pas pour le moment » au lieu de dire que la source n'existe plus | web | moyenne |
| **`BUG-R020-03-04`** — sur le Direct TV, le bandeau d'échec ou de panne est invisible : seul le bouton « My sources » reste, sans « l'actualisation a échoué » ni « ce catalogue peut être ancien » | TV | moyenne |

## 1. API — [`api-checks.txt`](api-checks.txt), 30/30

Ajout M3U et Xtream (`202`, compteurs nuls avant la première ingestion, pas « 0 ») ; sources en
erreur **conservées** avec leur code (`SOURCE_INVALID_FORMAT`, `SOURCE_UNREACHABLE`) ; *Réessayer*
relance la même source et n'en crée aucune ; seconde actualisation à moins de 5 min → `429
SOURCE_SYNC_RATE_LIMITED` avec `Retry-After: 299` ; en `SYNCING` : catalogue consultable, lecture
`409 SOURCE_NOT_READY`, nouvelle demande `409 SOURCE_SYNC_IN_PROGRESS` ; correction d'adresse
acceptée malgré le délai ; échec de format avec ancien catalogue : consultable **et lisible** ;
`SOURCE_AUTH_FAILED` : consultable, lecture `409 SOURCE_AUTH_FAILED` ; remplacement de l'adresse et
des identifiants → de nouveau `READY` ; renommer et `auto_sync` par source, sans réingestion ;
suppression `204` puis `404 SOURCE_NOT_FOUND` sur la source et son catalogue.

## 2. Cas du plan

| Cas | Web (FR) | Mobile (EN) | TV (EN) |
|---|---|---|---|
| **Ajout** | ✅ Xtream ajoutée par le formulaire, prête, 3 chaînes · 2 films · 2 séries ; la première source reste active | — (sources ajoutées par l'API) | — (pas d'ajout sur TV, R-428) |
| **R-420 La liste** | ✅ nom, type, état, « vérifiée il y a 27 s », compteurs connus, pas de séries pour la M3U, « Active sur cet appareil » | ✅ idem, « In use on this device » (`m01`) | ✅ (`t03`) |
| **R-421 Utiliser** | ✅ immédiat, on reste sur la page | ✅ immédiat, on reste sur *My sources* | ✅ au D-pad, le focus passe sur *Refresh* de la source choisie (`t06`) ; le choix ne quitte pas l'appareil (R-401 : la TV garde la M3U quand le mobile passe à la Xtream) |
| **R-422 Actualiser ×2** | ✅ « Actualisation en cours », étapes réelles, bouton « …en cours » ; puis « À nouveau possible dans environ 5 minutes » (délai du serveur, `retryAt`) | ✅ « Too soon. Again in about 5 min. » (`m03`) | ✅ idem (`t08`) |
| **R-423 Ancien catalogue pendant l'actualisation** | ✅ bandeau « Synchronisation … en cours », grille consultable ; lecture : « Cette source est en cours d'actualisation… » | ✅ (`m04`, `m05`) | ✅ (`t09`, `t10`) |
| **R-424 Échec avec ancien catalogue** | ⚠️ affichage ✅ (« L'actualisation a échoué », cause, « Ce catalogue peut être ancien », *Réessayer*), format invalide **lisible** (vidéo à 4,8 s), identifiants refusés **refusés** avec lien ; **correction impossible** → `BUG-R020-03-01` | ✅ « Correct the address » et « Correct my credentials » : remplacement accepté malgré le délai, source de nouveau prête (`m07` → `m09`) ; lecture refusée « Correct them in My sources » (`m06`) | ⚠️ catalogue consultable, **lecture possible** (`t12`), mais **bandeau invisible** → `BUG-R020-03-04` (`t11`) |
| **R-425 Renommer, actualisation auto** | ✅ nom et interrupteur, relus par l'API | ✅ par source, sur tout le compte (API : M3U `false`, Xtream `true`) (`m10`) | ✅ les changements apparaissent à la réouverture des réglages |
| **R-426 Supprimer** | ✅ confirmation qui nomme la source et ses conséquences (catalogue, favoris, progressions, récentes ; abonnement non résilié ; réajout sans restauration), *Annuler* en premier ; plusieurs, une, zéro sources : liste mise à jour, puis « Ajoutez votre première source » ; ⚠️ ancienne adresse → `BUG-R020-03-03` | ✅ zéro source → *Add a source* (`m11`) | ✅ une source restante choisie seule ; zéro → « No source configured … from your phone or lumo.tv » (`t19`) |
| **R-427 Supprimée ailleurs pendant une lecture** | (déclencheur) | — | ✅ « This source has been removed from your account. », *Continue* seul, **47 s** après la suppression ; *Continue* → Direct de la source restante (`t17`, `t18`) |
| **R-428 TV : gestion guidée** | — | — | ✅ choisir et actualiser seulement ; « Add, rename or delete a source — From the phone app or lumo.tv… », sans parler d'activation |

## 3. Écarts

- **`BUG-R020-03-01`** — déjà relevé dans [`sprint-08.md`](../../../../backlog/sprint-08.md) (« la
  réalisation reste à planifier »), jamais ouvert dans Plane. `updateSource` ne porte que le nom et
  l'actualisation automatique ; le lien « Corriger les identifiants » (`?confirm=none`) et « Corriger la
  source » du lecteur ramènent à la page de la source ; la M3U en erreur n'a aucune action de correction.
  Le mobile, lui, a le parcours complet. Contournement : corriger depuis le téléphone.
- **`BUG-R020-03-02`** — `SourceService.create` ne transmet pas `auto_sync` à
  `SourceRepository.insert` : la colonne prend son défaut (`true`). Aucun client n'envoie ce champ à la
  création, donc invisible à l'écran ; non-conformité au contrat (`CreateSourceRequest.auto_sync`).
- **`BUG-R020-03-03`** — `fetched()` ne distingue que « pas de réponse » et « route non servie » ;
  un `404 SOURCE_NOT_FOUND` tombe dans `unavailable`, et la page dit « Le service ne répond pas » — le
  contraire de ce que promet son propre commentaire (« one asks the user to come back in a minute, the
  other tells them the source is gone »). Cas réel : un onglet ouvert sur une source supprimée depuis
  le téléphone.
- **`BUG-R020-03-04`** — `LumoTvSourceNotice(compact = true)` partage la ligne de titre (« Live TV »,
  onglets) ; quand il porte un bouton (échec) ou deux (panne), sa colonne de texte n'a plus de largeur
  sur un écran 1080p et ne s'affiche pas. Pendant `SYNCING`, sans bouton, le texte s'affiche.

## Observations sans lot

- **Banc** : `/xtream-401/` est injoignable par le champ `host` (`HostNormaliser` ne garde que
  `scheme://host:port`, à raison : un panel n'a pas de chemin), et `player_api.php` accepte tous les
  identifiants. `SOURCE_AUTH_FAILED` ne se provoque donc plus que par la base. Une réponse `auth: 0`
  pour un identifiant réservé (ex. `refused`) rendrait le cas jouable de bout en bout.
- Web : la page d'une source M3U propose « Parcourir les séries », qu'une M3U n'a jamais.
- TV : titre « Stream interrupted » quand la lecture attend une actualisation qui n'a pas commencé ;
  mobile : « Refreshed 0 minutes ago » plutôt que « à l'instant ».
- Mobile : une source supprimée ailleurs reste listée **tant que l'app reste au premier plan** ; elle
  disparaît au retour au premier plan (vérifié) — pas d'écart au plan.
- Le rail TV a gardé l'ancien nom d'une source renommée jusqu'à la réouverture des réglages.
- Horloge des émulateurs restée au 10/10 (impossible à régler, « Operation not permitted ») : d'où les
  « Refreshed In 4 days » des captures TV — artefact d'appareil.

## Non joué

- R-427 second volet (« couper le réseau pendant une lecture : rien ne s'arrête ») : avec
  `adb reverse`, couper le réseau de l'émulateur ne coupe pas l'API ; non représentatif.
- Chaque cas dans les deux langues : web en français, Android en anglais seulement.
- Captures web : la fenêtre du navigateur intégré n'était pas affichée, les preuves web sont les textes
  de page cités ici.

## Correctifs (14/10/2026)

Branche `fix/BUG-R020-03-web-tv-sources` (sur `qa/R020-03-sources`), à la demande de Hamza
(« corrige BUG-R020-03-01, 03 et 04 ») ; `BUG-R020-03-02` reste au backlog.

- **`BUG-R020-03-01`** — formulaire web de correction. `correctSource` (Server Action) envoie
  `PATCH /sources/{id}` avec l'adresse (M3U) ou l'hôte, l'identifiant et, s'il est saisi, le mot de passe
  (Xtream) — **mot de passe vide = celui enregistré est gardé**, jamais renvoyé dans la page.
  `CorrectSourceForm` reprend les champs du formulaire d'ajout, pré-remplis. La page de la source
  l'ouvre par `?edit=connection#correct` depuis un lien adapté à l'erreur : « Corriger l'adresse »
  (M3U), « Corriger les identifiants » (refus, expiration), « Corriger la source » (autre échec Xtream) ;
  le lien « Corriger la source » du lecteur y mène aussi. Vérifié dans le navigateur : M3U
  `not-a-playlist.html` → `mixed.m3u` → « Source prête » ; Xtream en `SOURCE_AUTH_FAILED` → hôte vidé :
  refus au champ, sans appel ; hôte remis, mot de passe vide → « Source prête ».
- **`BUG-R020-03-03`** — `fetchedResource(call, "SOURCE_NOT_FOUND")` ajoute l'état `gone` aux trois de
  `fetched()`, pour la seule page d'une ressource ; la page d'une source supprimée dit « Cette source
  n'existe plus » (FR) / « This source no longer exists » (EN) avec un lien vers Mes sources. Vérifié sur
  la source supprimée pendant la recette. Tests : `fetched.test.ts` (5).
- **`BUG-R020-03-04`** — sur le Direct TV, un bandeau qui porte des boutons (échec, panne) passe sur sa
  propre ligne sous l'en-tête ; le bandeau sans bouton (actualisation) reste dans l'en-tête. Sur cette
  ligne, titre et message forment un seul bloc (`runIn`, style de corps) pour tenir en deux lignes et
  ne pas écraser la grille. Vérifié sur `Television_1080p` : « Your source has a problem — This address
  does not return a usable playlist. This catalogue may be out of date. » en entier, *My sources* à
  droite, première rangée de cartes visible (`t20-correctif-bandeau-echec.png`) ; au D-pad, `DOWN`
  depuis *Channels* atteint *My sources*, puis la recherche.

Tests : web `typecheck`, ESLint (`src`), Vitest 348/348, `pnpm build` (marketing toujours `●`) ;
Android `assembleDebug`, `lint`, `testDebugUnitTest`.

Observation : sur l'émulateur, `UP` depuis le champ de recherche du Direct ne quitte pas le champ (un
champ texte d'une ligne garde haut/bas pour son curseur), avec ou sans bandeau ; non lié à ce correctif,
à rejouer au D-pad réel avec `R020-13`.

