# S10-05 — Phase 1 : recette web

- Date : 2026-10-04
- Branche de recette : `qa/S10-05-recette` (base `dc0f9d2` + cherry-pick test-only
  `546dbdd` pour l'ancre du banc e2e).
- Objet : SR-01→SR-11, SR-14, SR-15 sur le web, `q` compris.

## Commandes exactes et sorties

Worktree `C:/dev/lumo-tv-qa-s1005/apps/web` (`pnpm install --frozen-lockfile` fait).

1. Tests unitaires (tous, pas seulement la recherche) :

   ```
   pnpm test
   ```
   → `Test Files 33 passed (33)` / `Tests 331 passed (331)` (1,21 s).

2. Campagne e2e, recherche S10-02/S10-03 (pile adoptée sur 18080) :

   ```
   E2E_STACK=auto pnpm exec playwright test \
     e2e/s10-02-web-search.journey.spec.ts \
     e2e/s10-03-web-search-open.journey.spec.ts \
     --project=journey --reporter=list
   ```
   → `20 passed (16.6s)` (12 setup + 8 scénarios).

3. Campagne e2e, états S10-04 (champ vide / aucun résultat / hors ligne) :

   ```
   E2E_STACK=auto pnpm exec playwright test \
     e2e/s10-04-web-search-states.journey.spec.ts \
     --project=journey --reporter=list
   ```
   → `18 passed (12.1s)` (14 setup + 4 scénarios).

4. Campagne e2e, erreur partielle S10-04, sur pile dédiée (proxy 503 `/vod`) :

   ```
   node docs/releases/0.2.0/qa-evidence/s10-05-web-2026-10-04/search-fault-proxy.mjs
   E2E_API_PORT=18082 E2E_SEARCH_FAULT_ARMED=1 E2E_STACK=auto pnpm exec playwright test \
     e2e/s10-04-web-search-partial.journey.spec.ts \
     --project=journey --reporter=list
   ```
   → proxy : `health via proxy: 200`, `vod via proxy: 503` ; puis
   `16 passed (11.4s)` (14 setup + 2 scénarios).

Nouveaux tests écrits par QA (non livrés par Dev, test-only) :
- `apps/web/e2e/s10-04-web-search-states.journey.spec.ts` (invitation sans
  chargement, aucun résultat avec texte + source + Effacer, hors ligne distinct) ;
- `apps/web/e2e/s10-04-web-search-partial.journey.spec.ts` (section en échec,
  sections conservées, jamais vide) ;
- `.../s10-05-web-2026-10-04/search-fault-proxy.mjs` (panne 503 sur `/vod`).

## Statut par cas SR (web)

| SR | Statut | Preuve web |
|---|---|---|
| SR-01 saisie rapide / débat 350 ms | N/A web | le web relit l'URL à chaque soumission, pas de débat client (conçu ainsi) ; couvert Android |
| SR-02 Entrée avant délai | N/A web | idem |
| SR-03 composition puis validation | N/A web | idem |
| SR-04 champ vide → invitation, aucun catalogue | ✅ CONFORME | `s10-04-web-search-states` (« champ vide invite… ») + `s10-02` + unit |
| SR-05 limite 100, pas de troncature | ✅ CONFORME (logique) | `search.test.ts` (26) ; message UI `searchTooLong` non joué en e2e → voir non exécuté |
| SR-06 4 en Tous, Voir tous → page 0 de 20 | ✅ CONFORME | `s10-02` (aperçu + Voir tous) + `search.test.ts` |
| SR-07 échec page suivante, réessai ciblé | ⚠️ NON EXÉCUTÉ (chemin échec) | pagination nominale ✅ par unit + S10-02 ; l'échec de page n'a pas de test e2e web |
| SR-08 réponse obsolète ignorée | N/A web | le web n'a pas de curseur async : l'URL est la source de vérité au remontage ; couvert Android |
| SR-09 source modifiée pendant saisie | ⚠️ non joué | non couvert par un test web ici |
| SR-10 une section échoue, les autres restent, réessai local | ✅ CONFORME (rendu) | `s10-04-web-search-partial` : section Films en `role=alert` + Réessayer, section Chaînes conservée, jamais « aucun résultat » |
| SR-11 type absent vs présent sans correspondance | ✅ CONFORME (absent) / ⚠️ partiel | absent : `s10-02` ; présent-sans-correspondance : logique `searchVerdict` + rendu `labels.empty`, non rejoué en e2e dédié |
| SR-12 retour de fiche/lecteur | ✅ CONFORME | `s10-03` (texte, filtre, page) |
| SR-13 focus TV | N/A web | voir Phase 2 |
| SR-14 pas de catalogue local honnête | ✅ CONFORME (web) | hors ligne distinct (`s10-04-web-search-states`) ; le web n'a pas de catalogue local, la bannière le dit |
| SR-15 changement de compte | N/A web | le contexte est porté par l'URL + cookie ; couvert Android |

Détail `q` : voir `s10-05-q-contract-2026-10-04/RAPPORT.md` (trim ✅, 1..100 ✅,
sous-chaîne insensible à la casse ✅, aucune tolérance fautes/accents ✅ ;
⚠️ comptage de points de code des clients vs `@Size` UTF-16 serveur à arbitrer).

## Non exécuté (Phase 1)

- SR-05 : le message visuel de dépassement (`searchTooLong`) n'a pas d'e2e.
- SR-07 : le chemin « échec de la page suivante + réessai ciblé » n'a pas de test
  e2e web. La structure BFF `?only=` et `searchVerdict` sont unit-testées.
- SR-09 : changement de source pendant la saisie/la pagination, non joué web.
- SR-11 seconde moitié : type présent sans correspondance alors qu'un autre a des
  résultats (le rendu `labels.empty` par section), non rejoué en e2e.
- Le clic de réessai ciblé n'est pas compté en requêtes (une seule section) :
  la présence du bouton est prouvée, l'unicité de la requête `?only=` reste
  couverte par le code + unit, pas par un compteur e2e.

## ⚠️ Note

Les deux nouveaux fichiers `*.journey.spec.ts` ajoutés par QA sont des tests,
pas du code produit ; ils ne modifient ni le web, ni le mobile, ni la TV. Ils
seront à retirer/porter quand la recette S10-05 sera close.
