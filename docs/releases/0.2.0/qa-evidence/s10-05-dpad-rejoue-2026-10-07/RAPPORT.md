# S10-05 — Rejoue D-pad `BUG-S10-05-01` (2026-10-07) : le banc passe, et la cause n'est pas le placement du handler

- Date : 2026-10-07. AVD `Television_1080p` (android-tv android-36, 1920x1080), `emulator-5554`.
- Objet : trancher, sur les deux têtes du lot, si `BUG-S10-05-01` (DOWN ne quitte pas
  le champ de recherche TV) est fermé.
  - `70e54ef` — `fix/S10-04-tv-search-dpad-v2` : `onPreviewKeyEvent` déplacé sur le
    `Box` qui enveloppe le champ.
  - `93fff2f` — `fix/S10-04-tv-search-dpad` (dans `main`) : handler posé sur le
    `BasicTextField` lui-même.
- Méthode : deux worktrees **neufs et détachés** (`C:/dev/lumo-tv-qa-dpadv2` sur
  `70e54ef`, `C:/dev/lumo-tv-qa-field` sur `93fff2f`), `apps/android/.env` +
  `local.properties` copiés, `adb reverse tcp:8080 tcp:8080`, API dev 8080 en 200.
  Aucune modification produit.

> Statuts : ✅ CONFORME · ❌ NON CONFORME · ⏸️ NON EXÉCUTÉ · ➖ NON APPLICABLE.

## Verdict

| Cas | Verdict | Preuve |
|---|---|---|
| **BUG-S10-05-01** — clavier **fermé**, DOWN quitte le champ (onglet, puis « Effacer ») | ✅ **CONFORME sur les deux têtes** | Live : DOWN → `All`, DOWN → `Clear the search` (70e54ef ET 93fff2f) |
| Banc instrumenté `SearchTvFocusTest` | ✅ 5/5, 0 skip, **3/3 runs sur chaque tête** | `instrumented-70e54ef-1of3.xml`, `instrumented-93fff2f-3of3.xml` |
| ⚠️ hors critère — clavier **ouvert** (Gboard visible) | DOWN est consommé par l'IME | 4 dumps identiques `1f3db497…`, champ toujours focalisé |
| SR-12 instrumenté | ⏸️ **NON JOUABLE** | aucun test instrumenté `restoreFocus` n'existe encore (seul `SearchTvFocusTest` est versionné) |

## 1. Je retire mon verdict précédent : le rouge instrumenté de 2026-10-04 n'est pas reproductible

Le `RAPPORT-dpad.md` du 04/10 concluait « banc instrumenté 4/5 rouge » sur `93fff2f`.
Rejoue aujourd'hui, sur la **même** tête `93fff2f` et sur `70e54ef`, worktrees neufs :

```
./gradlew :feature:search:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=tv.lumo.android.feature.search.SearchTvFocusTest \
  --rerun-tasks
```

- `70e54ef` : 5 tests / 0 échec / 0 skip, **3 runs sur 3** (`BUILD SUCCESSFUL`).
- `93fff2f` : 5 tests / 0 échec / 0 skip, **3 runs sur 3**.

Le test est identique entre les deux têtes (seul `SearchTvScreen.kt` change) ; seul
l'emplacement du handler diffère, et les deux passent. **Le rouge du 04/10 était un
artefact d'environnement (session à focus de fenêtre perdu, cf. §5 du rapport
précédent), pas un défaut du handler.** Le handler n'a jamais été mort.

## 2. La vraie cause : l'IME (Gboard) consomme DOWN tant qu'il est affiché

Expérience contrôlée sur le **même** écran, **même** APK `70e54ef`, champ focalisé,
requête `zz` tapée :

| État clavier | `dumpsys input_method` | DOWN #1 | DOWN #2 |
|---|---|---|---|
| **Gboard affiché** | `mInputShown=true` | champ `zz` (inchangé) | champ `zz` (inchangé) |
| **Gboard fermé** (BACK) | `mInputShown=false` | `All` | `Clear the search` |

- Clavier ouvert : 4 appuis DOWN, 4 dumps **bit-à-bit identiques**
  (`md5 1f3db4978e18ecf487600981f251558c`), champ `EditText` toujours `focused="true"` ;
  capture `live-70e54ef-gboard-visible.png` (Gboard bien peint).
- Clavier fermé : DOWN #1 → `All` (`live-70e54ef-ime-closed-down1-all.xml`),
  DOWN #2 → `Clear the search` (`…down2-clear.xml`). Idem sur `93fff2f`
  (`live-93fff2f-ime-closed-down1-all.xml`, `…down2-clear.xml`).

Ce n'est donc pas le placement du handler qui décide : **tant que le clavier est
affiché, la touche est routée vers l'IME (fenêtre `InputMethod`,
`com.google.android.inputmethod.latin`), qui la consomme ; l'app ne la voit pas.**
Le `onPreviewKeyEvent` sur le `Box` ne peut rien y faire, puisqu'il n'y a plus
d'événement à prévisualiser côté Compose.

## 3. Pourquoi le banc instrumenté ne l'a jamais vu

`SearchTvFocusTest.typeNoResult()` pose le texte **directement sur le ViewModel**
(KDoc du test : « a `performTextInput` would open the platform IME »). Le test est
donc exécuté **sans IME affiché** — exactement la seule condition où DOWN fonctionne.
Il prouve la traversée de focus Compose quand aucune IME n'intercepte la touche, mais
il ne peut pas attraper la capture par le clavier. C'est un angle mort du harnais, à
documenter comme tel.

## 4. Conséquences pour le lot

1. **`BUG-S10-05-01` : CONFORME au critère écrit** (« champ focalisé, clavier fermé,
   DOWN atteint l'onglet puis Effacer »), sur `70e54ef` comme sur `93fff2f`.
2. `70e54ef` (handler sur le `Box`) reste un **durcissement sûr** : il ne casse rien
   et place l'interception plus tôt sur le chemin root→focus. À garder, mais il
   n'est **pas** ce qui ferme le bug — la tête `93fff2f` passait déjà.
3. **Observation hors critère à arbitrer par le @PO** : au flux réel, taper dans le
   champ ouvre Gboard ; DOWN y est alors happé par le clavier, et les onglets /
   « Effacer » ne sont joignables qu'après BACK (fermer le clavier) ou TAB. Si
   l'exigence produit est « DOWN quitte le champ clavier affiché », ce n'est **pas**
   couvert — ni par `93fff2f`, ni par `70e54ef`, ni par le banc actuel.
4. **SR-12 instrumenté : non jouable en l'état.** Aucun test `restoreFocus` n'est
   versionné (vérifié sur `origin/main` et `3f44474` : seul `SearchTvFocusTest.kt`).
   À instrumenter par le @Dev avant tout rejoue QA de SR-12.

## 5. Limites

- Émulateur uniquement, pas de télécommande/matériel réel.
- `mInputShown` lu via `dumpsys input_method`, le clavier Gboard vérifié à la capture.
- Les runs instrumentés ont été faits dans des worktrees neufs (pas le worktree du
  04/10), à SHA fixes, avec `--rerun-tasks` pour exclure tout « UP-TO-DATE ».

## Fichiers de ce passage

- `instrumented-70e54ef-1of3.xml`, `instrumented-93fff2f-3of3.xml` — résultats JUnit.
- `live-70e54ef-ime-shown-down1..4.xml` — 4 dumps identiques (clavier ouvert).
- `live-70e54ef-ime-closed-down1-all.xml`, `…down2-clear.xml` — parcours clavier fermé.
- `live-93fff2f-ime-closed-down1-all.xml`, `…down2-clear.xml` — idem sur `93fff2f`.
- `live-70e54ef-gboard-visible.png` — Gboard affiché au moment des DOWN sans effet.
