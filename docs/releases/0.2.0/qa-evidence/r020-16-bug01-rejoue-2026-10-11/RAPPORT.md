# `BUG-R020-16-01` — correctif et rejoue TV — 11/10/2026

- Écart : [`r020-16-2026-10-11/RAPPORT.md`](../r020-16-2026-10-11/RAPPORT.md) §3 — la grille du Direct
  d'un nouvel appareil ne montrait que les favoris et récentes du compte, et le catalogue n'était
  jamais tiré.
- Branche : `fix/BUG-R020-16-01-initial-catalogue-sync` (sur `qa/R020-16-update-drill`).
- Appareil : `Television_1080p`, APK debug de la branche, installation propre ; API locale `dev` ;
  compte de recette A de R020-01 (source « Banc R020-01 A », 5 chaînes, un favori, une récente).

## Cause

Les écrans Direct, Films et Séries ne lançaient leur première synchro que si le cache de la source
était vide (`cached == 0`). Or ces tables reçoivent aussi des lignes isolées : `ChannelResolver`
(favoris, récentes), `VodRepository.filmsByIds` (rail « Continuer »), la fiche série ouverte par
lien. Une seule de ces lignes suffisait à sauter la synchro.

## Correctif

- Room 7 → 8 : table `catalogue_sync (source_id, content_type, synced_at)`, écrite par chaque
  `refresh` complet **après** ses lignes, effacée par `forget` et par `clearAllTables` à la
  déconnexion. Aucune ligne rétro-remplie : après la mise à jour, chaque écran tire son catalogue une
  fois, ce qui répare aussi un cache resté tronqué.
- `CatalogueRepository`, `VodRepository`, `SeriesRepository` : `isSynchronised(sourceId)`.
- `CatalogueSource.needsFirstRefresh(synchronised)` : la règle, une fois pour les trois écrans ;
  `LiveViewModel`, `VodViewModel`, `SeriesViewModel` l'appliquent. Le compteur de lignes ne décide plus
  que de l'état affiché (grille ou import en cours), comme avant.

## Preuves

| Étape | Résultat |
|---|---|
| Accueil après activation (`01-accueil-favori-recente.png`) | favori « Chaîne 05 » et récente « Chaîne 03 HD » ; Room : **2 chaînes, 0 catégorie, aucune ligne `catalogue_sync`**, `user_version` 8 — la situation du bug |
| Ouverture du Direct (`02-direct-catalogue-complet.png`) | `GET …/categories?contentType=LIVE` puis `GET …/channels?page=0&size=200` ; Room : **5 chaînes, 3 catégories, `catalogue_sync` = (source, `LIVE`)** ; grille : les 5 chaînes, catégories « Généralistes (2) », « Sport (2) » |
| Réouverture du Direct, puis redémarrage de l'app | **0** parcours du catalogue : la synchro ne se répète pas |

Avant correctif, la même situation a été observée sur TV et sur mobile pendant le drill R020-16
(captures `15-…`/`16-…` du passage R020-16). Le rouge n'a pas été rejoué avec un APK d'avant correctif.

Tests :

- JVM : `assembleDebug`, `lint`, `testDebugUnitTest` verts, 630 tests. Nouveaux tests :
  `CatalogueSourceTest` (règle), `CatalogueRepositoryTest` (+4 : synchro complète = marqueur, chaînes
  résolues ≠ catalogue, échec à mi-chemin, `forget`), `VodRepositoryTest` (+1).
- Instrumenté : `:core:database:connectedDebugAndroidTest` sur `Television_1080p`, 3/3, chemin
  1 → 8 et 7 → 8 sans marqueur.

## Non joué

- Mobile après correctif : même code (`core:data`, `LiveViewModel` partagé).
- Films et Séries sur appareil : même règle, couverte par les tests JVM.
- Mise à jour réelle d'une base v7 remplie vers v8 sur appareil : couverte par le test de migration ;
  conséquence attendue, une synchro par écran à la première ouverture.
