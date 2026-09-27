# S9-07 — plan de démarrage de la campagne (intersurfaces)

Préparé par QA le 27 septembre 2026. **Aucun cas GD-01→GD-14 n'est joué par ce
document** : c'est la préparation du démarrage, pas une preuve. Le protocole de
recette reste [`docs/releases/0.2.0/s9-07-recette.md`](../../s9-07-recette.md) ;
ce plan dit seulement *ce qui est prêt*, *dans quel ordre jouer* et *ce qui
manque encore*.

Branche de préparation : `qa/S9-07-campagne` (base `origin/main` @ `a30f103`).

---

## 0. Le signal « S9-06 vert » que j'attends du PO

Je ne commence **aucune** passe de recette avant ce signal, annoncé explicitement
par @PO :

> « S9-06 vert » = @Dev a poussé le SHA final sur
> `feat/S9-06-03-guide-states` (peinture **et** focus : `BUG-S9-06-03-01` **et**
> `BUG-S9-06-03-02`), @Tech Lead l'a vérifié, la PR est ouverte, et @PO a passé
> S9-06 en `En recette` dans Plane.

Tant qu'il n'est pas donné, je ne fais que la **phase 0** (provisionnement +
preuves automatiques), qui ne dépend pas de S9-06.

---

## 1. L'instrumentation I-1→I-6 est livrée (vérifié sur `origin/main` @ `a30f103`)

La §1.3 de `s9-07-recette.md` liste encore I-1/I-2/I-3/I-6 comme « à
instrumenter ». C'est faux depuis la fusion de `feat/S9-07-03-bench-harness`
(`7d3e6d2`, PR #7) : le harnais est dans le dépôt. Rectification à porter au
protocole (fait dans ce même commit).

| # | Livré | Où / preuve |
|---|---|---|
| I-1 | XMLTV à dates relatives, servi par nginx | `apps/web/e2e/bench/entrypoint.sh` + `xmltv.awk`, monté par `docker-compose.yml` |
| I-2 | Variantes `partial/empty/broken/stale/big/huge` + `transition` | même endroit ; 8 fichiers servis en 200 (vérifié 27/09) |
| I-3 | Horloge SSR `LUMO_NOW` centralisée | `apps/web/src/lib/epg/clock.ts` + `clock.test.ts`, lue par les deux appelants de `loadEpgWindow` |
| I-4 | Compteur `GET /v1/sources/{id}/epg` côté API | profil `application-epg-logging.yml` (`DispatcherServlet`) + `apps/web/e2e/bench/count-epg.sh` |
| I-5 | Panne EPG à la demande | `LUMO_EPG_FAULT=503`, `EpgFaultInjection` (1er appel des deux handlers) |
| I-6 | 100 chaînes + guide 100 chaînes alignés | `playlist-100.m3u` + `guide-big.xml` (`bench.001`…`bench.100`) |

**Vérification faite par moi le 27/09** (banc monté depuis un worktree `main`,
ancre `2026-09-27T18:00:00Z`) : `guide.xml` 763 programmes, `guide-transition.xml`
191, `guide-partial.xml` 572, `guide-empty.xml` 0, `guide-broken.xml` 1,
`guide-big.xml` 19 200, `guide-huge.xml` 600, `playlist-100.m3u` 100 chaînes.
Les 9 URLs répondent 200.

### 1.1 Piège à connaître : `C:\dev\lumo-tv` ne peut pas provisionner le banc

Le worktree principal est actuellement sur `feat/S9-06-03-guide-states`, qui est
antérieur à la fusion du harnais. Résultat : `apps/web/e2e/bench/xmltv.awk` y est
un **dossier vide** (artefact de checkout Windows, non suivi). Le bind mount de
`docker-compose.yml` monterait ce dossier au lieu du fichier, et `awk -f` échoue
(ou compose refuse « not a directory »).

**Conséquence pour la campagne** : provisionner depuis un worktree dont
`apps/web/e2e/bench/xmltv.awk` est bien un **fichier**. En pratique :

```bash
cd /c/dev/lumo-tv && git worktree add C:/dev/lumo-tv-qa907 -b qa/S9-07-campagne origin/main
```

ou, après la fusion de S9-06 sur `main`, `git switch main && git pull`.

---

## 2. Provisionner le banc, prêt à l'emploi

Depuis la racine d'un worktree qui **contient le harnais** :

```bash
bash docs/releases/0.2.0/qa-evidence/s9-07-campagne-2026-09-27/prepare-campagne.sh
```

Ce que fait le script (idempotent) :

1. vérifie que `apps/web/e2e/bench/xmltv.awk` est un fichier ;
2. arrête `lumo-bench` (dev, port 18081 en dur) s'il tourne — à relancer après :
   `docker start lumo-bench` ;
3. supprime un éventuel `lumo-e2e-bench` orphelin (celui d'une passe avortée,
   sans réseau → `SOURCE_UNREACHABLE`) ;
4. recrée `lumo-e2e-bench` via compose avec l'ancre demandée ;
5. vérifie les 9 URLs et affiche PASS/FAIL.

Ancre contrôlée (même instant que `LUMO_NOW` pour la phase horloge) :

```bash
BENCH_EPG_ANCHOR=2026-09-27T18:00:00Z \
  bash docs/releases/0.2.0/qa-evidence/s9-07-campagne-2026-09-27/prepare-campagne.sh
```

> Détail qui coûte un après-midi : un `up` **du seul service `bench`** échoue
> quand même si `LUMO_JWT_SECRET` et `LUMO_ENCRYPTION_MASTER_KEY` sont absents,
> parce que compose interpole tout le fichier. Le script fournit des valeurs
> jetables si l'environnement ne les a pas ; en campagne e2e, `global-setup.ts`
> les génère lui-même.

Le banc de campagne ne doit **pas** être monté en même temps que le banc de dev
(port 18081 fixe). Deux piles, deux moments (voir §3).

---

## 3. Les deux piles

| | Pile A — recette manuelle (protocole §1.1) | Pile B — recette e2e / automatique / horloge |
|---|---|---|
| API | `lumo-api` dev, 8080 | `lumo-e2e-api`, 18080 |
| Web | `pnpm dev`, 3000 | build prod Playwright, 3100 |
| Banc | `lumo-bench` dev, 18081 | `lumo-e2e-bench`, 18081 |
| Comptes | `setup-stack.mjs` (2 comptes `test.example`) | `auth.setup.ts` (1 compte) |
| `LUMO_NOW` | exporté avant `pnpm dev` (redémarrer le serveur à chaque ancre) | exporté avant `pnpm test:e2e` (hérité par le `webServer`) |
| Usage | GD-01/02/03/09/11, FR/EN, clavier, D-pad | automatique, GD-07/08/12 (horloge), I-4, GD-10 web |

Vérifié : le `webServer` de Playwright hérite de `process.env`
(`{...process.env, ...webEnv}`), donc `export LUMO_NOW=…` avant `pnpm test:e2e`
atteint bien le serveur Next.

### 3.1 Re-créer la pile e2e pour une passe instrumentée

`global-setup.ts` démarre `postgres`, `lumo-api` **et** `bench` (le service est
nommé, ce qui active le profil). Deux réglages ne passent pas par `composeEnv`
et demandent une reconstruction explicite :

```bash
# Compteur réseau web (I-4) : profil de logging
SPRING_PROFILES_ACTIVE=dev,epg-logging \
  docker compose -p lumo-e2e -f docker-compose.yml -f docker-compose.e2e.yml \
  up -d --no-deps --force-recreate lumo-api

# Panne EPG à la demande (GD-10 web, I-5)
LUMO_EPG_FAULT=503 \
  docker compose -p lumo-e2e -f docker-compose.yml -f docker-compose.e2e.yml \
  up -d --no-deps --force-recreate lumo-api
```

> `--force-recreate lumo-api` relit les variables, mais une recréation
> **régénère** les secrets : il faut relancer la campagne avec `E2E_STACK=docker`
> pour que l'API, le web et la base repartagent les mêmes. Vérifié le 26/09 : la
> recréation manuelle du seul `lumo-e2e-api` casse la session web.

---

## 4. Ordre des passages (prêt à enchaîner)

### Phase 0 — provisionnement et preuves automatiques (sans S9-06)
1. `prepare-campagne.sh` → banc vert, 9 URLs.
2. Preuves automatiques, **avant** toute passe manuelle :
   - Web : `pnpm test` dans `apps/web` (dont `src/lib/epg/split.test.ts`,
     `clock.test.ts`, `freshness.test.ts`).
   - Android : `./gradlew :core:data:testDebugUnitTest --tests '*EpgRepositoryTest*'`
     → `server.requestCount` = 3 pour 4 chaînes, 15 pour 8, jamais N.
   - API : `./gradlew test --tests '*GroupedEpgIntegrationTest*'`.
   Sortie console rangée dans `journal-phase0.log`.

### Phase 1 — web manuel FR/EN (Pile A)
`GD-01`, `GD-02`, `GD-03`, `GD-09` (partie web), `GD-10` (erreur initiale),
`GD-11`, `GD-14` (clavier). Chaque cas en `fr` **et** `en`. Aucun test
automatisé ne remplace la confirmation ; il la précède.

### Phase 2 — horloge web contrôlée (Pile B)
`GD-07`/`GD-08` sur `guide-transition.xml`, puis `GD-12` (minuit, changement
d'heure `Europe/Paris`). Puis la preuve réseau **I-4** :
`LUMO_NOW`/`BENCH_EPG_ANCHOR` au même instant, profil `epg-logging`, charger le
Guide à **3 / 50 / 100** chaînes et recopier `count-epg.sh` dans le rapport.
Attendu : petit et indépendant du nombre de cartes.

### Phase 3 — Android mobile (émulateur `Pixel_10` ou téléphone réel)
`GD-01`, `GD-02`, `GD-03`, `GD-07`, `GD-08`, `GD-09`, `GD-11`, `GD-13`, puis
comptage réseau `LumoHttp` à 3/50/100. APK : copier `apps/android/.env` dans le
worktree avant `:app-mobile:assembleDebug` (sinon base `10.0.2.2` injoignable).

### Phase 4 — Android TV (émulateur `Television_1080p` ou box + télécommande)
`GD-04`, `GD-05`, `GD-06`, `GD-07`, `GD-08`, `GD-09`, `GD-10` (erreur initiale via
proxy 503 + `adb reverse tcp:8080 tcp:18082`), `GD-11`, `GD-12`, navigation
D-pad complète (accueil → Direct → Guide → fiche → lecture → retour), puis
comptage `LumoHttp`.
**Ajout demandé par @Tech Lead** : contrôler aussi les écrans d'état TV **hors
Guide** (Séries / VOD / Sources) — le composant partagé `LumoTvStateMessage` a
changé de forme en S9-06-03 ; vérifier que titre + corps + actions y sont peints
et focalisables.

### Phase 5 — verdict et index
`RECETTE-S9-07-2026-….md` : un verdict par cas (`vert` / `rouge` + étapes +
test qui échoue / `non joué` + raison), puis ligne dans
`docs/releases/0.2.0/qa-evidence/INDEX.md`. Un bug hors story → issue Plane type
Bug avec label plateforme + test qui échoue.

---

## 5. Ce qui manque pour démarrer (à lever par @PO / Hamza)

| # | Manque | Bloque | Action |
|---|---|---|---|
| M-1 | **Signal « S9-06 vert »** (§0) | phases 1→4 | @PO annonce le SHA final + PR + passage `En recette` |
| M-2 | **TV/box réelle + télécommande** | phase 4 « D-pad réel » | le protocole §7 exige du matériel ; l'émulateur `Television_1080p` sert de repli **explicitement non « vert »**. Hamza confirme ou fournit l'accès |
| M-3 | **Téléphone Android réel** | phase 3 (mobile « réel ») | idem : repli `Pixel_10` noté comme tel |
| M-4 | `apps/android/.env` dans un worktree neuf | phase 3/4 | copier depuis le worktree principal (gitignoré) |
| M-5 | Passthrough compose de `LUMO_EPG_FAULT` / `epg-logging` | `GD-10` web, I-4 | recréation manuelle documentée (§3.1), à faire dans le même `E2E_STACK=docker` |
| M-6 | Worktree de campagne sur `main` | phase 0 | `xmltv.awk` doit être un fichier (§1.1) |

**Déjà couvert, pas un manque** : les comptes de test (créés par script, domaine
`test.example`, aucun email réel) ; les fixtures et l'ancre (banc) ; l'horloge SSR
(`LUMO_NOW`) ; le comptage réseau côté API web et Android.

**Connu et non reprovable localement** : la fenêtre « réponse en vol » de `GD-03`
(l'API locale répond trop vite). Couvert par le code et `LiveSourceSwitchTest` ;
à noter `non joué` avec la raison, pas « vert ».

---

## 6. Preuve de provisionnement (cette session)

Banc monté depuis `qa/S9-07-campagne` (worktree `C:\dev\lumo-tv-qa907`),
`BENCH_EPG_ANCHOR=2026-09-27T18:00:00Z` :

```
bench: EPG anchor is 2026-09-27T18:00:00Z
bench: guide.xml (110569 bytes, 763 programmes)
bench: guide-transition.xml (27963 bytes, 191 programmes)
bench: guide-partial.xml (82910 bytes, 572 programmes)
bench: guide-empty.xml (83 bytes, 0 programmes)
bench: guide-broken.xml (302 bytes, 1 programmes)
bench: guide-stale.xml (110569 bytes, 763 programmes)
bench: guide-big.xml (3164583 bytes, 19200 programmes)
bench: guide-huge.xml (5028383 bytes, 600 programmes)
bench: volume playlist at /playlist-100.m3u (100 channels, aligned with guide-big.xml)
```

Journal complet : `journal-provisionnement-2026-09-27.log` (à verser avec le
rapport de campagne).
