# S10-05 — `BUG-S10-05-03` (SR-12) : preuve appareil rejouée, rouge → vert reproduit (2026-10-07)

- Date : 2026-10-07. AVD `Television_1080p` (android-tv **android-36**, 1920×1080),
  `emulator-5554`. Aucune modification produit dans l'arbre au final.
- Objet : rejouer **indépendamment** la preuve appareil de SR-12 livrée par @Dev.
  - Tête recettée : `test/S10-05-03-sr12-return-focus` @ **`230c4cb`**
    (base `origin/main` @ `52eaa50`), un seul fichier : `SearchTvReturnFocusTest.kt` (+223).
  - Le correctif d'état SR-12 (`3f44474`, contenu dans `main`) n'est **pas** touché ici :
    on vérifie le test instrumenté qui prouve le **focus au retour** sur la carte quittée.
- Méthode :
  - `ANDROID_SERIAL=emulator-5554 ./gradlew --no-daemon :feature:search:connectedDebugAndroidTest`.
  - Rouge : injection temporaire du `SearchViewModel` **d'avant le correctif**
    (`3f44474^`, équivalent au `63ff6bb` cité par @Dev), test inchangé, puis restauration
    (`md5 81a79663b0af8b0650d7c41ebe2ecb58`, arbre propre, rien committé par la manip).

> Statuts : ✅ CONFORME · ❌ NON CONFORME · ⏸️ NON EXÉCUTÉ · ➖ NON APPLICABLE.

## Verdict

| Cas | Verdict | Preuve |
|---|---|---|
| **SR-12** (`BUG-S10-05-03`) — au retour d'une carte, le focus repose sur **la carte quittée** et les résultats restent affichés | ✅ **CONFORME sur appareil** | `SearchTvReturnFocusTest` vert, 2 runs |
| Banc du module `feature:search` sans régression | ✅ **6/6, 0 skip** (5 `SearchTvFocusTest` + 1 `SearchTvReturnFocusTest`) | `sr12-instrumented-GREEN-television1080p.xml` |
| Rouge → vert exigé | ✅ **reproduit** : `SearchTvReturnFocusTest` seul rouge, à l'assert de focus | `sr12-instrumented-RED-prefix.xml` |

## 1. Vert sur `230c4cb`

```
Starting 6 tests on Television_1080p(AVD) - 16
Finished 6 tests on Television_1080p(AVD) - 16
BUILD SUCCESSFUL in 18s
```

`<testsuites tests="6" failures="0" errors="0" skipped="0">` — le nouveau test
`returningFromAChosenCardKeepsTheResultsAndPutsTheFocusBackOnIt` **tourne** (0.96 s,
non skippé). Reproduit sur deux exécutions de la même tête (18:46:21 puis 18:47:29).

## 2. Rouge rejoué par moi (injection du pré-correctif)

J'ai remis le `SearchViewModel` d'avant le correctif (`3f44474^`, `inputComposing`
absent) **en gardant le test tel quel**, puis relancé :

```
<testsuites tests="6" failures="1" errors="0" skipped="0">
```

- `SearchTvFocusTest` : **5/5 passent** (pas de dégât collatéral).
- `SearchTvReturnFocusTest` : **1/1 échoue**, exactement à
  `SearchTvReturnFocusTest.kt:109` (`assertIsFocused`), message :

  > `Failed to assert the following: (Focused = 'true')`
  > `Reason: Expected exactly '1' node but could not find any node that satisfies: (Text + … contains 'Beta TV')`

C'est exactement le mécanisme annoncé : sans le correctif, le **rejeu du texte
inchangé** à la reprise de focus vide les sections, donc la carte quittée (`Beta TV`)
disparaît et le focus ne peut pas s'y poser. Filet **indépendamment reproduit**.

## 3. Lecture du test (revue adverse)

Le test est **digne de foi pour le critère** :

- Il monte la **vraie** `SearchTvScreen` sur un **vrai** `SearchViewModel`
  (`TwoChannelRepository`, une source active factice) — pas un double de l'écran.
- La carte choisie est **la seconde** (`Beta TV`), pas la première qui porte
  `firstResultFocus` : choisir la première n'aurait rien prouvé.
- Le retour est modélisé par un `rememberSaveableStateHolder` +
  `SaveableStateProvider("search")` retiré puis remis — le geste réel d'un `NavHost`.
  Le rouge ci-dessus montre que ce chemin dépend bien du correctif d'état.
- Il vérifie à la fois la **carte focalisée** (`Beta TV` `assertIsFocused`) et la
  **présence des résultats** (`Alpha TV` `assertIsDisplayed`, l'autre carte présente).

## 4. Réserves (⚠️ hors critère, non bloquantes)

- **Le rejeu est simulé** : `viewModel.onQueryChanged(QUERY, composing = false)` est
  appelé à la main, comme dans le test unitaire — pas par un vrai commit d'IME ni un
  vrai back stack Navigation. C'est le seul moyen déterministe (l'IME ouvre un
  minuteur qui re-planifie la recherche), mais cela reste un **modèle** du retour :
  le test prouve le correctif **sous retour modélisé**, pas la séquence IME réelle.
- **`filtre` non exercé par ce test** : il reste sur le filtre par défaut. La
  dimension « filtre conservé » du critère s'appuie donc encore sur la recette
  mobile du 2026-10-04 (`s10-05-mobile-2026-10-04`), pas sur ce test TV.
- **Écart de harnais au brief @Tech Lead** : `StateRestorationTester` n'est pas
  utilisé (il laisse le framework re-focaliser le champ et `chosen` ne revient pas) ;
  le `SaveableStateProvider` le remplace. L'écart est **justifié** et documenté dans
  l'en-tête du test.

## 5. Conclusion

`BUG-S10-05-03` (SR-12) : le critère « au retour d'une fiche, la carte quittée reprend
le focus et les résultats restent » est **prouvé sur appareil**, et le rouge → vert est
**reproductible indépendamment**. C'est la preuve que @PO attendait pour fermer S10.

Aucun fichier produit modifié ; l'injection du pré-correctif a été restaurée avant
tout commit.
