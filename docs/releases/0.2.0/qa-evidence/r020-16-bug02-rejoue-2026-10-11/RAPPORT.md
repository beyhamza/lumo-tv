# `BUG-R020-16-02` — correctif et rejoue TV — 11/10/2026

- Écart : [`r020-16-2026-10-11/RAPPORT.md`](../r020-16-2026-10-11/RAPPORT.md) §3 — grille « All » du
  Direct vide après la première synchro, alors que la base Room contient les chaînes.
- Branche : `fix/BUG-R020-16-02-tv-grid-empty` (sur `fix/BUG-R020-16-01-initial-catalogue-sync`).
- Appareil : `Television_1080p` ; API locale `dev` ; compte de recette dédié, deux sources (M3U
  `mixed.m3u` et Xtream du banc), ni favori ni récente.

## Reproduction

Effacement des données de l'app (`pm clear`), lancement, activation approuvée par l'API, OK sur le
choix de source (première source, M3U), OK sur « All channels » de l'accueil, 12 s d'attente, puis
lecture de l'écran (`uiautomator`) et de la base Room.

| APK | Passages | Grille | Room |
|---|---|---|---|
| `dev` @ `720f845` | 4 | **vide 4/4** (`01-rouge-dev.png`) | 3 chaînes, 3 catégories |
| `dev` @ `720f845` + ce correctif seul | 3 | **3 chaînes, 3/3** (`02-vert-dev-plus-correctif.png`) | 3 chaînes, 3 catégories |
| branche complète (`BUG-R020-16-01` + `-02`) | 1 | 3 chaînes (`03-vert-branche.png`) | 3 chaînes, 3 catégories |

Entrer par le menu latéral, ou passer par une seule source, ne reproduisait pas : la fenêtre est
étroite. Le correctif de `BUG-R020-16-01` la masquait aussi (une requête Room de plus avant la
synchro) : 0 reproduction sur 6 essais avec son APK, d'où la comparaison sur `dev`.

## Cause

`LimitOffsetPagingSource` de room-paging 2.8.4 (`CommonLimitOffsetImpl`, lu dans le bytecode) :

- s'abonne aux changements de table depuis une coroutine qu'il lance lui-même
  (`InvalidationTracker.createFlow(tables, emitInitialState = false)`) ;
- **ignore toute notification reçue tant que son premier chargement n'est pas terminé**
  (`if (refreshComplete.get()) pagingSource.invalidate()`).

Une TV qui entre dans le Direct d'une source jamais synchronisée lit la première page **vide** pendant
que `CatalogueRepository.refresh` écrit le catalogue : la notification de cette écriture tombe dans
la fenêtre et se perd. Une page vide ne demande pas de page suivante, et le `PagingData` mis en cache
par le view model (`cachedIn`) survit au passage par le menu : la grille reste vide jusqu'à ce que la
requête change (une recherche, un filtre).

## Correctif

- `core:database` : `LivePagingSources` garde (faiblement) les `PagingSource` distribuées par
  `CataloguePager`, `VodPager` et `SeriesPager` ; chacun expose `invalidate()`.
- `CatalogueRepository`, `VodRepository`, `SeriesRepository` : `refresh` appelle `pager.invalidate()`
  juste après avoir remplacé les lignes. Le dépôt sait quand il a réécrit le catalogue ; il le dit,
  au lieu de compter sur la notification de Room. Une invalidation de plus que Room est sans effet
  visible : la source est remplacée, à la même position.
- Films et Séries ont la même construction : corrigés avec.

## Tests

- JVM, rouge puis vert : `a refresh invalidates the channel lists already open`
  (`CatalogueRepositoryTest`), `… film lists …` (`VodRepositoryTest`), `… series lists …`
  (`SeriesTreeStateTest`). Sans les trois appels `pager.invalidate()` : **3/3 échouent** sur
  `invalid` (« expected to be true ») ; avec : verts.
- `assembleDebug`, `lint`, `testDebugUnitTest` : 633 tests, 0 échec.

## Non joué

- Mobile : même code (`core:data`) ; la course n'y a pas été observée.
- Films et Séries sur appareil : même construction, couverte par les tests JVM.
- `ChannelResolver` (favoris, récentes) écrit aussi dans `channel` sans passer par `refresh` : la
  même fenêtre peut lui faire perdre une invalidation. Effet borné — les lignes manquantes sont
  celles d'un favori, et la synchro complète qui suit (`BUG-R020-16-01`) invalide tout.
