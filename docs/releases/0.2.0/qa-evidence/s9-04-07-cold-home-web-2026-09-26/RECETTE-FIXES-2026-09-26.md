# Recette des correctifs — Lumo TV, 26 septembre 2026

Passe QA sur les trois branches de correction (base `76c2c42`), avant toute PR.
Rapport de la passe initiale : `PASS-2026-09-26.md`.

## Verdict court

| Bug | Branche | Verdict | Preuve |
|---|---|---|---|
| #213 `BUG-S9-04-07-01` | `fix/S9-04-07-01-home-cold-entries` @ `3571f69` | ✅ **vérifié rouge → vert** | test d'acceptation e2e indépendant |
| #214 `BUG-S9-05-02-01` | `fix/S9-05-02-01-web-grid-density` @ `ee9bb1e` | ⏳ diff revu conforme à l'arbitrage ; recette **navigateur à 1440 px avec données** pas encore faite | — |
| #215 + #216 `BUG-S9-05-03-01/02` | `fix/S9-05-03-tv-grid-density` @ `869316f` | ⏳ diff revu conforme à l'arbitrage ; recette **D-pad TV** après merge, comme convenu | — |

## #213 — accueil à froid (web), vérifié

Test d'acceptation écrit depuis le critère du PO (`decisions.md`, « Source prête
sans activité : accès aux catalogues disponibles »), pas depuis le code :

- enregistre l'unique source du compte (`http://bench/playlist.m3u`) ;
- va sur `/fr/app` **sans aucune lecture** ;
- exige « Toutes les chaînes » et « Guide TV » visibles ;
- vérifie que la règle US-017 tient toujours pour Favoris (« Voir tous les
  favoris » absent à froid) — l'opt-in `keepEntriesWhenEmpty` n'a pas fuité ;
- ouvre les deux portes et vérifie `?view=guide` / `?view=channels`.

Fichier archivé : `cold-home-journey.spec.ts` (à poser dans la PR #213 ; il
tombe dans le projet Playwright `journey` car son nom contient
`journey.spec.ts`).

Commandes (depuis `apps/web`) :

```
E2E_KEEP_STACK=1 pnpm exec playwright test e2e/cold-home-journey.spec.ts --project=journey
```

- `main` @ `76c2c42` → **échec** : `getByRole('link', { name: 'Toutes les chaînes' })` introuvable.
  Journal : `2026-09-26-red-main-cold-home.log`.
- `fix/S9-04-07-01-home-cold-entries` @ `3571f69` → **2 passed**.
  Journal : `2026-09-26-green-fix213-cold-home.log`.

Le test échoue bien sur le comportement d'avant et passe après — prouvé par
moi, pas repris du Dev.

## Ce qui reste à recetter

1. **#214** — l'acceptation du PO est *visuelle* (« à 1440 px, plus d'heures qui
   se chevauchent ni de titres à 1–3 caractères »). Le test du Dev porte sur la
   fonction pure `gridWidth` ; il ne prouve pas le rendu. Il faut une source
   avec guide, l'écran Guide à 1440 px, et vérifier que `10:00`/`11:00` ne se
   chevauchent plus et qu'un titre de 30 min garde ≈ 8 caractères (64 px).
2. **#215/#216** — après merge de `fix/S9-05-03-tv-grid-density` : en-tête sur
   deux lignes (5 onglets visibles) et cellules qui affichent titre + horaire sur
   1080p. Passe D-pad à refaire sur l'émulateur `Television_1080p`.
3. **S9-04-04 mobile à froid** — jamais recetté (demande du PO). Android mobile
   (`Pixel_10`), pas la vue mobile web : vérifier que le home Android mobile
   expose les deux entrées à froid, comme la TV.

## Note d'environnement (à retenir pour les prochaines campagnes)

- Le banc de développement `lumo-bench` publie **18081 en dur** (non
  paramétrable). Il doit être **arrêté** avant une campagne e2e, qui monte son
  propre `lumo-e2e-bench`. Le relancer après.
- Un `docker compose up` e2e avorté (conflit de port) laisse un conteneur
  orphelin `lumo-e2e-bench` **sans réseau** ; la campagne suivante le réutilise
  tel quel et l'ingestion échoue en `SOURCE_UNREACHABLE` (le banc est
  injoignable depuis l'API). Nettoyage avant de relancer :
  `docker rm -f lumo-e2e-bench lumo-e2e-api lumo-e2e-postgres && docker network rm lumo-e2e`.

Aucune écriture dans Plane ni dans le dépôt (branche `main` laissée dans son
état d'origine, seul `docs/backlog/sprint-09.md` modifié par le PO).
