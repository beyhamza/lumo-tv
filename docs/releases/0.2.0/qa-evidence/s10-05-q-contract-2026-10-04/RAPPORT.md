# S10-05 — Phase 0 : contrat `q`

- Date : 2026-10-04
- Branche de recette : `qa/S10-05-recette` @ `e7831fd`
  (base `dc0f9d2` = web `0c38784` + mobile `9652e82` + TV `dc0f9d2`, plus
  cherry-pick test-only `546dbdd` pour l'ancre du banc e2e).
- Objet : le point (3) du brief TL — `q` = trim, 1..100, sous-chaîne insensible
  à la casse, **pas** de tolérance fautes/accents. Motif de rejet s'il manque.

## Code inspecté (arbre `dc0f9d2`)

| Surface | Fichier | Règle |
|---|---|---|
| Web | `apps/web/src/lib/search/search.ts` | `normalizeQuery` = `trim()` ; `codePointLength` compte les points de code ; `queryTooLong` = `> 100` (refus, jamais troncature) |
| Android (mobile + TV) | `feature/search/.../SearchViewModel.kt` | `onQueryChanged` : `text.codePointCount(...) > MAX_QUERY_LENGTH` (100) → refus (`dropPending`), pas de troncature ; `text.trim().isEmpty()` → annule et invite |
| API (validateur serveur) | `packages/contracts/openapi.yaml` + `generated/api/CatalogApi.java` + `catalog/CatalogController.java` | `q` : `minLength: 1`, `maxLength: 100` → généré en `@Size(min=1,max=100)` sur les 3 listings ; interface `@Validated` ; contrôleur `q == null || q.isBlank() ? null : q.trim()` |
| API (matching) | `catalog/CatalogReadRepository.java` | `ch.name ILIKE '%' || :search || '%'` (idem vod/series) — sous-chaîne insensible à la casse, aucun `unaccent`, aucun opérateur de similarité trgm |

Le texte OpenAPI décrit bien `q` comme « Case-insensitive substring … the
trigram index makes it fast, **not approximate** » : la divergence de
description signalée dans le doc de design (« typo-tolerant ») est déjà
résolue dans le contrat. Aucun opérateur de correction de fautes n'existe dans
le code.

## Preuves exécutées

1. Web — `apps/web` (worktree `C:/dev/lumo-tv-s1004web`, web identique à
   `dc0f9d2`, `node_modules` présent) :

   ```
   pnpm exec vitest run src/lib/search/search.test.ts
   ```
   → `Test Files 1 passed (1)` / `Tests 26 passed (26)`.
   Couvre : trim, points de code, refus au-delà de 100 (100 OK, 101 refusé,
   emoji compté 1), fallback de filtre inconnu.

2. Android — worktree `C:/dev/lumo-tv-qa-s1005/apps/android` :

   ```
   ./gradlew --no-daemon :feature:search:testDebugUnitTest --rerun-tasks
   ```
   → `BUILD SUCCESSFUL in 29s`, 154 tasks.
   Rapport `build/test-results/testDebugUnitTest/TEST-...SearchViewModelTest.xml` :
   **tests=25 failures=0 errors=0 skipped=0**.
   Dont : « a hundred and one characters are refused, never truncated » et
   « a hundred code points are accepted, even with a non-BMP character »
   (99 ASCII + 1 emoji = 100 points de code mais 101 unités UTF-16).

## Statut par critère

| Critère | Statut | Preuve |
|---|---|---|
| `q` trimé avant requête, saisie vide = invitation sans chargement | ✅ CONFORME | `search.test.ts` (trim), `SearchViewModelTest` (emptying…) |
| 1 ≤ `q` ≤ 100, refus au dépassement sans troncature | ✅ CONFORME (nominal) | web 26 tests, Android 25 tests, `@Size` serveur |
| Sous-chaîne insensible à la casse | ✅ CONFORME | `ILIKE '%' \|\| :search \|\| '%'` sur les 3 listings |
| Pas de tolérance fautes/accents | ✅ CONFORME | pas de `unaccent`, pas d'opérateur trgm d'approximation ; OpenAPI « not approximate » |

## ⚠️ Observation hors critère nominal (à arbitrer)

Le `@Size(min=1,max=100)` serveur compte les **unités UTF-16** (`String.length()`),
alors que les deux clients comptent les **points de code**. Une requête de 100
points de code contenant un caractère astral (p. ex. 99 ASCII + 1 emoji) est
acceptée par le web et Android — c'est explicitement prouvé par leurs tests —
mais vaut 101 unités UTF-16 et serait donc refusée `400` par le validateur
serveur. Le doc de design demande pourtant « Aligner le comptage Unicode avec
les clients **et le validateur serveur** ».

- Impact : uniquement les requêtes frontière contenant un caractère hors BMP.
- Vérification : **lecture de code, non exécutée** (pas d'API lancée pour
  confirmer le 400). À confirmer par un appel réel avant conclusion.
- Recommandation QA : soit borner les clients à 100 **unités UTF-16** pour
  s'aligner sur `@Size`, soit remplacer la contrainte serveur par un validateur
  en points de code. Décision PO/TL.

## Non exécuté (Phase 0)

- Aucun appel réel à l'API pour confirmer le 400 du cas astral ci-dessus.
- Campagne e2e web (Phase 1), D-pad TV (Phase 2), mobile (Phase 3) : à suivre.
