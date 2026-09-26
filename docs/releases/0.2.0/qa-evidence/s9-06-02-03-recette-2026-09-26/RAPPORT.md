# Recette S9-06-02 (GD-09/GD-13) + S9-06-03 (GD-10/GD-11) — 2026-09-26

Rapport QA. Cas du plan : `docs/releases/0.2.0/s9-06-plan-de-test.md` §3
(QA-06-02-01→04, QA-06-03-01→06).

- **Artefact testé** : `feat/S9-06-03-guide-states` @ **`377b48b`** (base
  `f2d738f` ; contient `f221f9b` S9-06-02 puis `377b48b` S9-06-03). Poussé sur
  origin, non mergé.
- **Aucun code touché.** Le seul écart au tip est l'infra de test (voir §4) ;
  rien n'a été ajouté au code de production.
- **Preuves** : branche `qa/S9-06-02-03-recette`, **depuis `origin/main` `7d3e6d2`**.
- **Règle** : aucune case non exécutée n'est comptée verte. Un verdict « conforme
  par lecture » n'est pas un verdict de recette ; il est marqué comme tel.

Environnement : Windows, JDK 25, Gradle wrapper 9.7.1, SDK
`%LOCALAPPDATA%/Android/Sdk`, émulateur **Television_1080p** (`emulator-5554`,
API 36), `lumo-api` sur `127.0.0.1:8080`, banc `lumo-bench` sur `:18081`,
`adb reverse tcp:8080 tcp:8080`.

---

## 1. Verdict par cas

| # | Cas | Méthode réellement employée | Verdict |
|---|---|---|---|
| QA-06-02-01 | GD-13 retour par niveau (mobile), position conservée | lecture `LiveMobileScreen.kt` (`guideList`/`dayList` hoistés l.222-223, passés l.337/356) | **conforme par lecture** — non exécuté sur appareil |
| QA-06-02-02 | GD-09 retour lecteur, ancre restaurée | lecture `GuideGridTv.kt` l.181/209 (`resolveReturnSelection`) + l.228 (`onAnchorChanged`), `LiveViewModel.kt` l.288-289, `LiveTvScreen.kt` (`onAnchorChanged`) | **conforme par lecture** — non exécuté sur appareil |
| QA-06-02-03 | GD-09 repli même chaîne/même heure → suivante → précédente → null | `GuideFocus.kt` l.228 `resolveReturnSelection`, l.260 `fallbackRow` ; **9 tests** `GuideFocusTest` (section « return to a remembered cell ») | **conforme (pur + unitaire vert)** — pas d'instrumenté |
| QA-06-02-04 | Aucun focus sur squelette / case masquée | `GuideFocus.kt` l.278 `moveVertical` saute les lignes non répondues ; `fallbackRow` saute les squelettes ; tests « steps over a row Paging has not loaded », « a return never lands on a skeleton row » | **conforme par lecture + unitaire** — focus réel non instrumenté |
| QA-06-03-01 | GD-10 **erreur initiale** | `GuideStates.kt` l.134 `guideStateOf` → `InitialError` ; `GuideGridTv.kt` l.325 et `GuideDayMobile.kt` rendent `LumoTvStateMessage` (2 actions). **Appareil réel** : état + 2 actions présents et fonctionnels, mais **rendu non visible** (voir §5) | **⚠️ partiel — rendu à confirmer** (pas vert) |
| QA-06-03-02 | GD-10 **erreur avec données** | `guideStateOf` → `DataError` ; `GuideGridTv.kt` l.341 `LumoTvSourceNotice` garde la grille. **Appareil réel** : « Guide not updated » + relance → nouvelle requête `/epg` | **conforme (appareil réel, capture)** |
| QA-06-03-03 | GD-11 guide partiel → « Aucun programme disponible sur ce créneau » | code : `GuideGridTv.kt`/`GuideDayMobile.kt` rendent `feature_live_guide_empty_slot` sur bloc vide ; banc sert `guide-partial.xml` (200, sans `bench.3`) | **non exécuté** (cache-first, pas de harnais) |
| QA-06-03-04 | GD-11 logo/description absents | — | **non exécuté** |
| QA-06-03-05 | Liste vide, cause non inventée | — | **non exécuté** |
| QA-06-03-06 | GD-11 guide ancien → date de MAJ | code : `guideAgeOf` l.201 → `GuideAge.LastImport` ; libellé `feature_live_guide_age` ; unitaires `GuideStatesTest` (stale/incomplete/fresh/undated) | **conforme par lecture + unitaire** — **non exécuté** sur appareil (staleness dérivée de la base, horloge non contrôlée) |

Points de code qui fondent les verdicts GD-10/11 :

- `GuideStates.kt` : `GuideStatus.afterRead` l.76 (cache → `FromCache`,
  réseau → `Complete`, cache avec `staleReason` → `Failed`) ; `guideStateOf`
  l.134 (ordre : `NotConfigured` → `Failed` (DataError si données) → `Content`
  → `Empty` seulement si `Complete` **et** `answered` non vide, sinon
  `Loading`) ; `guideRowAnswered` l.171 ; `guideAgeOf` l.201.
- `LiveViewModel.kt` : `readGuideDay` l.352 applique `afterRead` l.372 sur les
  **deux** émissions ; `onGuideRetry` l.332 rejoue la lecture sans vider la
  grille ; `guideAnchor` l.939 remis à zéro par `browsing(...)` au changement de
  source.

---

## 2. Pur / reproductible — sortie brute

```
cd apps/android && ./gradlew :feature:live:testDebugUnitTest --rerun-tasks
BUILD SUCCESSFUL in 44s
```

Résultats JUnit agrégés (`unit/*.xml`) :

| Classe | tests | échecs | erreurs | skips |
|---|---|---|---|---|
| GuideCellTimeLabelTest | 2 | 0 | 0 | 0 |
| GuideDayMobileTest | 10 | 0 | 0 | 0 |
| **GuideFocusTest** | **32** | 0 | 0 | 0 |
| GuideNowListTest | 8 | 0 | 0 | 0 |
| **GuideStatesTest** | **19** | 0 | 0 | 0 |
| LiveDirectViewsTest | 11 | 0 | 0 | 0 |
| LiveFavoritesTest | 7 | 0 | 0 | 0 |
| LiveSourceSwitchTest | 6 | 0 | 0 | 0 |
| LiveTvFocusTest | 5 | 0 | 0 | 0 |
| PlayerFailureTest | 6 | 0 | 0 | 0 |
| PlayerGuideTest | 3 | 0 | 0 | 0 |
| ProgrammeSheetTest | 10 | 0 | 0 | 0 |

**Total : 12 classes / 119 tests / 0 échec / 0 erreur / 0 skip.** Conforme à
l'attendu de @Tech Lead.

---

## 3. Appareil réel — GD-10 (TV 1080p)

APK `app-tv` debug **buildé depuis `377b48b`** (worktree de recette), installé
sur `emulator-5554` (`Sélection` → Success). Panne EPG injectée par un proxy
local : tout chemin contenant `/epg` → **503**, le reste relayé à `lumo-api`
(`device/fault-proxy.mjs`, `device/fault-proxy.log`), branché par
`adb reverse tcp:8080 tcp:18082`.

### QA-06-03-02 — erreur avec données → **conforme**

Guide ouvert sur « Today » (grille déjà en cache). Écran observé
(`device/01-…png`, `device/02-…png`) : **« Guide not updated »** +
« The grid below is what your device already holds. » + **« Try again »**, la
zone de grille restant affichée. Le proxy journalise le `503` sur
`GET /v1/sources/<id>/epg?…`. Appui sur « Try again » → **une nouvelle requête
`/epg`** part (proxy l.8→9). La grille et l'axe de temps survivent : conforme au
critère « la grille et le focus survivent ; message distinct ».

### QA-06-03-01 — erreur initiale → **⚠️ rendu non confirmé**

Sélection d'un jour **sans cache** (« Sun 27 ») sous panne : l'arbre
d'accessibilité (`device/initial-error-dump-t1.xml`) contient **« Guide
unavailable »**, **« Try again »**
(`clickable="true" focusable="true"`, bounds `[590,743][896,839]`) et
**« See channels »** — donc l'état `InitialError` est bien construit et les deux
actions existent. Elles sont **fonctionnelles** : « Try again » relance une
requête `/epg` (proxy), « See channels » ouvre la vue Chaînes
(`device/after-see-channels-dump.xml`).

**Mais aucun de ces trois éléments n'est dessiné à l'écran.** Les 3 captures
versionnées (03/04/05) **ne sont pas** identiques octet pour octet — leurs md5
diffèrent (`03=c4358cf2`, `04=500ea901`, `05=645126c1`) — mais elles ne
diffèrent **que hors de la zone du message** : 03↔04 dans la pastille de vue
(`Channels`/`Guide`, y≈117-217), 03↔05 dans la ligne des onglets de jour
(y≈475-555). La zone du message d'erreur (y≥700, x≥440) est, elle,
**strictement identique** entre les trois (0 pixel différent) et uniformément au
fond `[13,12,18]` : **0 pixel s'y distingue du fond** dans chacune d'elles. Le
`exec-out screencap` cité n'ajoute rien de versionné. Détail troublant : le nœud
du titre est mesuré `[584,783][1439,785]` — **2 px de haut**, sous les boutons —
ce qui ressemble à un problème de layout, pas à un artefact GPU. Voir §5.

---

## 4. Ce qui n'a pas été exécuté, et pourquoi

1. **Instrumenté QA-06-02-01/02/03 et QA-06-03-01** : l'infra
   (`testInstrumentationRunner` + `androidTestImplementation(libs.androidx.test.ext.junit)`)
   n'existe **que sur `c91eedd`**, pas sur `377b48b`
   (`apps/android/feature/live/build.gradle.kts` de `377b48b` ne l'a pas). Monter
   une branche QA fusionnant cette tranche **et** écrire de nouveaux tests
   Compose pour GD-09/GD-10 sortait de la fenêtre ; je l'ai remplacé par un
   passage **manuel sur appareil réel** (GD-10, §3). **Aucun test instrumenté
   n'a été fabriqué.**
2. **QA-06-03-03 (guide partiel)** : le banc sert bien `guide-partial.xml`
   (`200`, `bench.3` absent → colonne = gap). Non joué sur appareil :
   l'EPG est **cache-first** (S7-02), le cache du jour contient encore les
   programmes `bench.3` de la synchro canonique, et le harnais S9-07-03
   (contrôle cache/horloge) n'est pas monté ici — la scène ne serait pas
   interprétable.
3. **QA-06-03-06 (guide ancien)** : `guide-stale.xml` est **identique octet pour
   octet** au canonique ; l'ancienneté est dérivée de
   `lastSuccessfulImportAt` en base. La jouer demande de reculer cette date en
   base ou de contrôler l'horloge — non fait.
4. **QA-06-03-04 / QA-06-03-05** : non exécutés.

Éléments d'infra **effectivement disponibles** (vérifiés) : `lumo-bench`
répond `200` sur `guide.xml`, `guide-partial.xml`, `guide-stale.xml`,
`guide-empty.xml`, `guide-broken.xml` ; `docker-compose.yml` (main `7d3e6d2`)
ne transmet toujours pas `LUMO_EPG_FAULT` à `lumo-api` — d'où le proxy.

---

## 5. Observations hors critères (à trancher)

- **⚠️ GD-10 « erreur initiale » invisible sur TV (probable défaut, à
  confirmer).** Voir §3/QA-06-03-01. Reproduce : source avec guide, panne
  `/epg` (503), Guide ouvert sur un jour sans cache. L'écran est dans l'arbre
  d'accessibilité et réagit, mais n'est jamais composité — les boutons sont
  cliquables sans être visibles. Candidats : `LumoTvStateMessage` (layout du
  titre mesuré à 2 px) ou le pipeline de composition de l'émulateur
  (swiftshader). **À reproduire sur matériel réel** avant d'en faire une issue
  Bug ; si confirmé, c'est un critère GD-10 en échec, pas une observation.
- **GD-13 (mobile)** : repose sur le hoisting de `guideList`/`dayList` et non
  sur S9-06-02 ; aucun test dédié ne couvre la conservation de position. À
  instrumenter si le PO veut une preuve.
- **Cache-first** : confirme que les scènes « partiel/ancien » ne sont pas
  observables sans contrôle du cache ; c'est un prérequis de recette, pas un
  défaut de code.

---

## 6. Inventaire des preuves

```
qa-evidence/s9-06-02-03-recette-2026-09-26/
  RAPPORT.md
  unit/  12 XML JUnit + unit-testDebugUnitTest.log   (12 classes / 119 / 0 / 0)
  device/
    01-gd10-data-error-today.png            GD-10 « erreur avec données »
    02-gd10-data-error-today-reentry.png    idem, après ré-entrée
    03-gd10-initial-error-sun27-blank.png   GD-10 « erreur initiale » : zone vide
    04-gd10-initial-error-redraw-blank.png  idem après redraw du focus
    initial-error-dump-t1.xml               arbre uiautomator : message + 2 boutons
    after-see-channels-dump.xml             « See channels » a bien navigué
    fault-proxy.mjs, fault-proxy.log        injection 503 sur /epg
```

Commandes clés :

```bash
# unitaire
cd apps/android && ./gradlew :feature:live:testDebugUnitTest --rerun-tasks
# APK de recette depuis 377b48b
./gradlew :app-tv:assembleDebug
adb -s emulator-5554 install -r app-tv/build/outputs/apk/debug/app-tv-debug.apk
# panne EPG
node device/fault-proxy.mjs            # 503 sur /epg, relief vers 127.0.0.1:8080
adb -s emulator-5554 reverse tcp:8080 tcp:18082
# constat
adb -s emulator-5554 shell uiautomator dump /sdcard/x.xml
adb -s emulator-5554 shell screencap /sdcard/x.png
```

**Verdict global : S9-06-02 conforme par lecture + unitaires verts ; S9-06-03
partiellement recetté — GD-10 « erreur avec données » conforme sur appareil,
GD-10 « erreur initiale » à confirmer (rendu), GD-11 non exécuté côté scènes.**
Aucun verdict ne vaut fermeture de S9-06-02/03 sans lever le point GD-10
« erreur initiale ».
