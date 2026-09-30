# S10 — rejeu indépendant de `fix/e2e-bench-provisioning` en pile fraîche (2026-09-30)

**Artefact recetté :** `fix/e2e-bench-provisioning` @ `d83f8c9`
(« test(e2e): isoler les comptes du banc, provisionner l'ancre et les playlists »),
base `1d34b24` (= `main`).

**Demande :** @PO — rejouer la branche de correction (pas `main`) en pile
**fraîche CI-equivalent**, rendre passed/failed/skipped + la liste des échecs,
confirmer que le **point 5 (plafond d'une source / compte partagé)** est traité,
trancher `journey.spec.ts:254` (dérive de test vs défaut produit), et archiver.

**Verdict court :** la correction tient. Le point 5 est **traité** (comptes
isolés par fichier de spec + épreuve de plafond dédiée), `journey.spec.ts:254`
est une **dérive de test** (elle passe), et **aucun défaut produit** ne ressort.
Deux observations hors critères sont notées : une **instabilité de harnais** sur
`QA-06-04-04` (1 échec sur 3 campagnes complètes, vert en isolé et 2/3 en
complet) et un **banc orphelin laissé par le teardown**.

---

## 1. Méthode — pile fraîche CI-equivalent

Worktree `C:\dev\lumo-tv-s10-qa`, checkout `d83f8c9` (arbre propre).

1. **Démontage complet** de ce qui traînait : `docker rm -f lumo-e2e-bench
   lumo-e2e-api lumo-e2e-postgres`, `docker network rm lumo-e2e`, et
   suppression de `apps/web/.e2e/` (état de pile + empreinte d'image). Le banc de
   dev `lumo-bench` (exited) et la pile de dev `lumo-api`/`lumo-postgres`
   (ports 8080/15432) n'ont **pas** été touchés.
2. Campagne : depuis `apps/web`, `E2E_STACK=docker` (démarre **toujours** la
   pile, n'adopte jamais une API déjà en écoute), **aucun** `E2E_ANCHOR`,
   `LUMO_NOW`, `BENCH_EPG_ANCHOR`, `E2E_FAULT_ARMED` ni `E2E_KEEP_STACK` forcé.
   `E2E_ANCHOR` absent ⇒ `ANCHOR = new Date()` au chargement de la config
   (`support/stack.ts`), poussé aux **deux** horloges (`BENCH_EPG_ANCHOR` côté
   banc, `LUMO_NOW` côté web SSR).
3. La campagne 1 a **reconstruit l'image** de l'API (pas d'empreinte connue :
   `#15 RUN … bootJar` et les 10 étapes se rejouent, `Image lumo-e2e-lumo-api
   Built`), puis `Network lumo-e2e Created` + `lumo-e2e-postgres` (tmpfs) +
   `lumo-e2e-bench` + `lumo-e2e-api` tous **healthy**. Les campagnes 2 et 3
   réutilisent l'image (sources API inchangées) mais **recréent postgres**
   (`down -v` en teardown) : base neuve à chaque passe.
4. Un passage isolé du seul fichier `s9-06-04-web.journey.spec.ts` a été ajouté
   pour trancher l'échec de la campagne 1.

---

## 2. Résultats

| Passe | Commande | passed | failed | skipped | did not run | durée |
|---|---|---|---|---|---|---|
| **1 — fraîche CI-equiv.** | `E2E_STACK=docker pnpm test:e2e` | **62** | **1** | 4 | 4 | 47,2 s |
| 2 | idem, image réutilisée | **67** | 0 | 4 | 0 | 45,3 s |
| 3 | idem, image réutilisée | **67** | 0 | 4 | 0 | 44,9 s |
| isolé | `pnpm test:e2e e2e/s9-06-04-web.journey.spec.ts` | **18** | 0 | 0 | 0 | 37,2 s |
| **avant (rappel)** | `main` @ `1d34b24`, idem | 27 | **9** | 1 | 25 | 4,7 min |

Les 3 skips attendus : `s9-06-04-fault` (GD-10, pass opt-in `E2E_FAULT_ARMED`),
`guide-grid-density` (sa source n'a pas d'`epg_url`), `gd12-midnight` ×2 (pass
opt-in `E2E_ANCHOR`). Le total est de 71 tests ; `67 passed + 4 skipped = 71`.

**Les 9 specs rouges sur `main` sont toutes vertes sur `d83f8c9`** (campagnes 2
et 3) : `journey.spec.ts:106` (playlist → source prête), `s9-06-04-fault`
(désormais skip volontaire), `s9-06-04-partial`, `s9-06-04-past-source` ×2,
`s9-06-04-web` (préparation), `s9-07-gd12-midnight`, `s9-07-gd14-locales-keyboard`,
`s9-07-network-bound`.

---

## 3. PO Q1 — le point 5 (plafond / compte partagé) est-il traité ?

**Oui, la correction traite bien la cause dominante, pas seulement l'ancre et
les playlists.**

- `auth.setup.ts` crée **un compte par fichier `*.journey.spec.ts`** (la liste
  est lue sur disque), chacun avec son `storageState`
  `.e2e/sessions/<clé>.json`. Campagnes 2/3 : **10 comptes** créés (les 10 tests
  `setup` passent, log lignes « un compte est créé pour … »). Plus aucun fichier
  ne se dispute le dernier emplacement de source.
- `docker-compose.e2e.yml` fixe `LUMO_PLANS_FREE_MAX_SOURCES=3` : le pic réel
  **par compte** de `past-source` et `network-bound` est 3, donc chaque compte
  isolé tient. La valeur *shipped* (1) reste couverte côté API par
  `PlanLimitsIntegrationTest`.
- `s9-sources-ceiling.journey.spec.ts` (nouveau, **compte dédié**) monte jusqu'au
  plafond et vérifie l'annonce sans écrire le nombre en dur. Elle passe :
  `ok … › le plafond du compte remplace le bouton d'ajout` (campagnes 2/3 et
  isolé). L'assertion de plafond a été **retirée** de `journey.spec.ts`, où elle
  partageait le compte et supposait `max_sources = 1`.
- Les dérivés de playlist (`/playlist-3.m3u`, `/playlist-50.m3u`) sont bien
  générés après substitution (`entrypoint.sh`), et l'ancre `BENCH_EPG_ANCHOR`
  alimente le banc — mais ce sont là des compléments : ce sont les **comptes
  isolés + le plafond à 3** qui lèvent les échecs `SOURCE_LIMIT`.

---

## 4. PO Q2 — `journey.spec.ts:254` : dérive de test ou défaut produit ?

**Dérive de test.** Sur la branche corrigée le test (décalé à la ligne **259**
par le diff) passe : `ok … › sources › une chaîne mise en favori le reste, et
rend la vue où elle était` — campagnes 1, 2 et 3.

Preuve : le diff `d83f8c9` met le test au niveau de l'UI **actuelle** (S6-09) —
la fiche de groupes `summary[aria-label="Groupes de …"]` et le bouton
`catalogueFavoriteRemoveFrom` du groupe par défaut, qu'on ouvre (un `<details>`
fermé est hors de l'arbre d'accessibilité) avant de retirer, puis referme au
rechargement. Le comportement vérifié (favori persistant relu en base au
rechargement, retour sur la vue filtrée) est inchangé et correct. Aucun `BUG-*`
à ouvrir : c'était l'assertion qui visait l'ancien libellé, pas le produit.

---

## 5. Observation hors critères — ⚠️ instabilité de harnais `QA-06-04-04` (GD-07 web)

Campagne 1 seulement :

```
1) [journey] › s9-06-04-web.journey.spec.ts:105 › QA-06-04-04 — GD-07 : l'action
   disparaît à la fin, fiche ouverte, focus sur Fermer
   expect(locator).toHaveCount(0) failed — Received: 1
   Locator: getByRole('dialog', {name:'Fiche du programme'}).getByRole('link',{name:'Regarder en direct'})
```

- Vert en **isolé** (18/18) et dans les campagnes **2 et 3** → ce n'est pas un
  défaut produit reproductible.
- Mécanisme plausible (non capturé en trace, `test-results/` a été écrasé par la
  passe suivante) : `useProgrammeClock` (`apps/web/src/lib/epg/live-clock.ts`)
  pose sa base de temps écoulé dans un `useEffect`, et arme son `setTimeout` à
  `ends_at + 50 ms`. Le spec fait `page.clock.install()` puis
  `fastForward(125_000)` **dès que la fiche SSR est visible** ; `toBeVisible`
  ne prouve pas que React a hydraté. Si `fastForward` passe avant l'effet, le
  minuteur est ré-armé depuis l'instant factice déjà avancé et ne tire pas dans
  la fenêtre de 5 s de l'assertion → l'action reste. Sous charge (6 workers), ce
  créneau se perd.
- **Ce n'est pas un défaut produit** : la règle GD-07 est correcte
  (`watchAvailable`/`momentOf` dans `programme.ts`, retrait ~50 ms après
  `ends_at`) et le cas est vert en isolé et 2/3 en complet.
- **Recommandation (harnais, @Tech Lead)** : attendre l'hydratation/effet avant
  `fastForward` (p. ex. attendre que le focus d'arrivée soit posé sur l'action
  avant de faire avancer l'horloge) afin de supprimer le créneau. Non modifié
  ici : `d83f8c9` est le mécanisme du @Tech Lead et le @PO a demandé de ne pas y
  toucher.

## 6. Observation hors critères — ⚠️ banc orphelin laissé par le teardown

À chaque fin de campagne, `global-teardown.ts` lance
`docker compose … down -v` **sans activer le profil `bench`**. Résultat observé 3
fois : `lumo-e2e-api` et `lumo-e2e-postgres` sont bien arrêtés/retirés, mais
`lumo-e2e-bench` reste **Up** et retient le réseau (`Network lumo-e2e Removing /
Resource is still in use`). Il faut `docker rm -f lumo-e2e-bench` puis
`docker network rm lumo-e2e`. C'est un défaut de ménage du harnais, pas un défaut
produit — mais il recrée exactement le piège d'orphelin déjà rencontré (bind
18081). Non corrigé ici pour la même raison qu'en §5.

---

## 7. Nettoyage

Après la campagne 3, la pile e2e a été démontée par le teardown puis l'orphelin
`lumo-e2e-bench` et le réseau `lumo-e2e` ont été retirés à la main. Il ne reste
que la pile de dev (`lumo-api`, `lumo-postgres`, `lumo-bench` exited), intacte.

## 8. Fichiers

- `logs/e2e-run1-fresh.log` — campagne fraîche CI-equivalent (62/1/4/4) ;
- `logs/e2e-run2-fresh.log`, `logs/e2e-run3-fresh.log` — 67 passed / 4 skipped ;
- `logs/e2e-isolated-s9-06-04-web.log` — 18/18 sur le seul fichier en échec ;
- `logs/e2e-main-before-fix.log` — rappel du rouge `main` (9 échecs) ;
- `logs/lumo-api-run3.log` — journal API de la pile fraîche (profils
  `dev,epg-logging`, Liquibase, requêtes) ;
- `RAPPORT.md` — ce document.

SHA recetté : `d83f8c981b5a18a7cf6f4719f3e7d506eab938f3`.
