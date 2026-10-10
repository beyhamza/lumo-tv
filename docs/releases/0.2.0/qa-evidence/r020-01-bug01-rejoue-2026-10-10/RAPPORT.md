# BUG-R020-01-01 — rejoue après correctif — 10/10/2026

- Défaut : la déconnexion TV/mobile laissait le cache local du compte précédent
  (constat : [`r020-01-tv-2026-10-10/RAPPORT.md`](../r020-01-tv-2026-10-10/RAPPORT.md)).
- Correctif : branche `fix/BUG-R020-01-01-signout-purge` (empilée sur `qa/R020-01-isolation-run`,
  elle-même sur `dev` @ `b18c6c8`), APK debug construit depuis cette branche, émulateur `Television_1080p`, API locale
  reconstruite depuis `dev`.
- Même scénario, mêmes comptes de recette et sources du banc que le constat.

## Ce que fait le correctif

- `core:auth` : `SessionEndCleaner`, un ensemble Hilt que `SessionManager` exécute à chaque fin
  de session — déconnexion **et** refresh refusé par le serveur. Session d'abord, puis purge,
  terminée avant le retour de `signOut()`, **non annulable** (voir plus bas), au mieux pour chaque
  nettoyeur.
- `core:data` : `AccountDataCleaner` vide toute la base Room (`DatabaseEraser`, exposé par
  `core:database`) et les deux fichiers de préférences (source active, vue Direct).

## Rejoue

| Étape | Preuve | Base locale (`run-as`) |
|---|---|---|
| A activé | `ecrans/01-A-connecte.png` | `channel` 2, `favorite` 1, `favorite_group` 1, `recent_channel` 1 ; `active_source` 94 o, `session.bin` 429 o |
| « Sign out of this television » confirmé | `ecrans/02-sign-out.png`, `ecrans/03-activation-apres-sign-out.png` | **toutes les tables à 0** ; `active_source` **0 o** ; `session.bin` 0 o |
| B activé | `ecrans/04-B-connecte.png` | source de B à l'écran, rien de A ; **aucune ligne de A en base** |

Verdict : ✅ **`BUG-R020-01-01` conforme au critère « sans reste »** sur TV.

## Rouge vu deux fois

1. **Première version du correctif, sur l'appareil** : la base était vidée, mais
   `active_source.preferences_pb` gardait la source de A (fichier non réécrit). Cause : la
   déconnexion part du `viewModelScope` des Réglages ; vider la session fait quitter l'écran,
   le scope est annulé et la purge s'arrête à mi-chemin. D'où le `NonCancellable`.
2. **Test unitaire** `the purge finishes even when the caller is cancelled half-way` : rouge sans
   `NonCancellable` (11 tests, 1 échec), vert avec.

## Tests

- `SessionManagerTest` (`core:auth`) : 11/11, dont 5 nouveaux (purge à la déconnexion, au refresh
  refusé, aucune purge sur une panne transitoire, un nettoyeur en échec n'arrête pas les autres,
  purge menée à terme malgré l'annulation).
- `DataStoreActiveSourceStoreTest`, `DirectViewStoreTest` (`core:data`) : `clearAll` vide tout.
- Android complet : `assembleDebug`, `lint`, `testDebugUnitTest` — 624 tests, 0 échec.

## Non rejoué

- Mobile : même code (`core:auth`, `core:data`), non rejoué sur appareil.
- Refresh refusé par le serveur (révocation à distance) : couvert par test unitaire, pas sur appareil.
