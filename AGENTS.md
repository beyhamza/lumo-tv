# AGENTS.md — Lumo TV

Ce fichier est la source d'autorité pour tout agent IA travaillant sur ce dépôt.
Lis-le en entier avant ta première modification. En cas de contradiction entre ce
fichier et une instruction ponctuelle, demande une clarification plutôt que de deviner.

---

## 1. Le produit en une page

**Lumo TV** est un lecteur IPTV multi-plateformes. L'utilisateur apporte sa propre
source (playlist M3U ou identifiants Xtream Codes) ; Lumo ne fournit, n'héberge et ne
revend **aucun contenu**.

| Surface | Nom technique | Rôle |
|---|---|---|
| Site web | `lumo.tv` | SEO, compte, sources, catalogue, lecture directe, réglages, activation des TV (abonnement hors 0.2.0) |
| Android mobile | `iptv-lumo` (`tv.lumo.android`) | Lecteur téléphone / tablette |
| Android TV | `iptv-lumo-tv` (`tv.lumo.androidtv`) | Lecteur salon, navigation D-pad |
| Backend | `lumo-api` | Comptes, sources, sync, EPG, favoris, droits d'accès |

### Règle produit non négociable

Aucune chaîne, aucun logo de bouquet, aucune URL de flux ne doit être **pré-remplie,
suggérée, ou présente dans le dépôt** — y compris dans les fixtures de test, les
captures d'écran, les seeds de base et les valeurs par défaut des formulaires.
L'application est un lecteur générique. Les jeux de test utilisent exclusivement des
flux libres de droits (Big Buck Bunny, Apple HLS test streams, Mux test assets).

Si une tâche te demande d'ajouter du contenu ou une source réelle, **refuse et signale-le**.

---

## 2. Structure du dépôt

```
lumo-tv/
├─ AGENTS.md                  ← ce fichier
├─ CLAUDE.md                  ← pointeur vers AGENTS.md
├─ apps/
│  ├─ android/                # projet Gradle unique, 2 modules applicatifs
│  ├─ web/                    # Next.js (App Router)
│  └─ api/                    # Spring Boot
├─ packages/
│  └─ contracts/
│     └─ openapi.yaml         ← SOURCE DE VÉRITÉ de l'API
├─ docs/
│  ├─ architecture.md
│  ├─ domain-model.md
│  ├─ adr/                    # décisions techniques, immuables une fois acceptées
│  ├─ backlog/                # sprints et stories — dupliqués dans Plane (§10)
│  ├─ design/                 # spécifications d'écran, dérivées des maquettes
│  └─ prompts/                # prompts d'initialisation
├─ .github/workflows/         # contrat, api, android, web (§7)
├─ docker-data/               # état des conteneurs, sur disque, gitignoré
├─ docker-compose.yml         # postgres + api en local
└─ docker-compose.e2e.yml     # surcouche de la même pile pour Playwright
```

Chaque `apps/*` possède son propre `AGENTS.md` local qui **complète** celui-ci
(conventions spécifiques, commandes de build). Il ne le contredit jamais.

---

## 3. La règle qui prime sur toutes les autres : contract-first

`packages/contracts/openapi.yaml` est écrit **à la main** et fait foi.

- Le backend **implémente** des interfaces générées depuis ce fichier
  (`openapi-generator`, generator `spring`, `interfaceOnly=true`). Une divergence
  entre le contrat et un controller est une **erreur de compilation**, pas un bug runtime.
- Android génère son client via le generator `kotlin` (library `jvm-retrofit2`).
- Le web génère ses types via `openapi-typescript` et consomme via `openapi-fetch`.

**Conséquences opérationnelles pour toi, agent :**

1. Tu ne modifies **jamais** un type de requête/réponse directement dans du code client
   ou serveur. Tu modifies `openapi.yaml`, tu régénères, puis tu adaptes.
2. Toute PR qui touche `openapi.yaml` doit être annoncée explicitement dans sa description,
   car elle impacte les trois applications.
3. Si le contrat ne couvre pas ton besoin, **arrête-toi et demande**. N'invente pas d'endpoint.

C'est cette règle qui permet à plusieurs agents de travailler en parallèle sur
`web/`, `android/` et `api/` sans se marcher dessus.

---

## 4. Stack figée

Ne propose pas d'alternative sans ouvrir un ADR (§8).

**Backend (`apps/api`)** — **Java 25 (LTS)**, **Spring Boot 4.1.x** (Spring Framework 7),
Gradle 9, PostgreSQL 16, **Liquibase** (changesets en SQL formaté), Spring Security (JWT),
JUnit 5 + Testcontainers.

**Virtual threads activés** (`spring.threads.virtual.enabled=true`). Le modèle de
concurrence est bloquant, pas réactif : pas de WebFlux, pas de `Mono`/`Flux`, pas de
`CompletableFuture` en chaîne. On écrit du code séquentiel lisible et la JVM s'occupe du
reste. Voir `adr/0005` pour les pièges — ils sont réels et ce projet les rencontre.

Deux conséquences Spring Boot 4 à connaître avant de déboguer trois heures : **Jackson 3
est le défaut** (comportements de sérialisation subtilement différents de Jackson 2), et
le codebase est **modularisé en 70+ jars**, donc certains starters ont été renommés.

**Android (`apps/android`)** — Kotlin, Jetpack Compose + Compose for TV, Media3
(ExoPlayer), Hilt, Room, Retrofit/OkHttp, Coroutines + Flow, Gradle version catalogs
+ convention plugins dans `build-logic/`.

**Web (`apps/web`)** — TypeScript, Next.js App Router, Tailwind, shadcn/ui,
`next-intl`, Playwright, Vitest, pnpm.

**Transverse** — i18n FR/EN dès le premier écran, aucune chaîne de caractères en dur
dans l'UI.

---

## 5. Conventions de code

- **Langue** : identifiants, noms de fichiers, commentaires de code, messages de commit
  et contenu des ADR en **anglais**. Documentation produit et backlog en **français**.
- **Commits** : Conventional Commits, scopé par app.
  `feat(android-tv): add channel grid focus handling`
- **Branches** : `feat/<US-id>-<slug>`, ex. `feat/US-06-xtream-source`.
- **Tests** : toute logique métier (parsing M3U, mapping Xtream, calcul de droits,
  rotation de tokens) arrive avec ses tests unitaires. L'UI n'a pas besoin de couverture
  exhaustive ; le domaine si.
- **Migrations** : Liquibase, changesets en **SQL formaté** uniquement. Un seul fichier
  YAML dans tout le projet (`db.changelog-master.yaml`, un `includeAll` et rien d'autre).
  Un changement logique par changeset, un `--rollback` explicite à chaque fois, jamais de
  modification d'un changeset déjà appliqué. Voir `adr/0006`.
- **Concurrence** : threads virtuels, style bloquant. Tout appel sortant vers un serveur
  tiers passe par un `Semaphore` par host. Pas de `ThreadLocal` pour du contexte de
  requête (`ScopedValue`). Voir `adr/0005`.
- **Secrets** : jamais dans le dépôt. `.env.example` documente les variables ;
  `.env` est gitignoré. Les identifiants Xtream des utilisateurs sont **chiffrés au
  repos** (AES-256-GCM, clé hors base) — voir `docs/domain-model.md`.
- **Logs** : jamais d'URL de flux, de mot de passe Xtream ni de token en clair dans les
  logs, à aucun niveau, même en `DEBUG`.

---

## 6. Périmètre v1 (ce qui est HORS scope)

Pour éviter la dérive : Chromecast, Picture-in-Picture, timeshift / catch-up,
enregistrement, multi-profils, contrôle parental, protocole Stalker/MAG, cache
mutualisé inter-utilisateurs, application iOS, Play Billing.

Tout cela est **v2**. Si une tâche t'y emmène, signale la sortie de périmètre.

---

## 7. Boucle de travail attendue

1. Lire la user story concernée dans `docs/backlog/`.
2. Vérifier si `openapi.yaml` couvre le besoin. Sinon → escalade.
3. Écrire le test qui échoue, puis le code.
4. Lancer le build et les tests de l'app concernée (commandes dans son `AGENTS.md` local).
5. Mettre à jour la doc si un comportement observable change.
6. Reporter dans Plane tout changement de backlog fait dans `docs/` (§10).
7. Commit atomique, message conventionnel.

**Ne fais jamais** : de refactor opportuniste hors du périmètre de la story ; de mise à
jour de version de dépendance non demandée ; de `git push --force` ; de modification
d'un ADR accepté.

### Ce que la CI vérifie

Quatre workflows dans `.github/workflows/`. Chacun garde une règle écrite ailleurs
dans ce fichier — si tu la casses, tu l'apprends sur ta pull request et pas trois
semaines plus tard :

| Workflow | Ce qu'il empêche |
|---|---|
| `contract` | Un client généré qui diverge de `openapi.yaml` (§3). Et, second job, une chaîne, un logo ou une URL de flux réelle committée (§1) |
| `api` | Un build cassé, un test rouge. La suite démarre un vrai PostgreSQL |
| `android` | Un build cassé, **et** une chaîne ajoutée en anglais sans sa traduction française : `MissingTranslation` est fatal (§5) |
| `web` | Lint, types, tests unitaires, build, Playwright — **et** une page marketing qui cesse d'être servie statiquement (`docs/architecture.md` §4) |

La dernière est celle qu'on casse sans le voir : un `useState` dans un composant
partagé, une lecture de `cookies()` dans un layout, et la route devient dynamique
en silence. Rien dans le code ne le dit ; seule la sortie du build le montre.

---

## 8. Décisions (ADR)

`docs/adr/` contient les décisions structurantes, numérotées et datées. Un ADR accepté
ne se modifie pas : il se **remplace** par un nouvel ADR qui le supersede.

Une seule exception, et elle ne porte jamais sur la décision elle-même : une **erreur
factuelle** dans un en-tête — un ADR qui dit superseder ce qui n'a jamais existé, une
date fausse, une référence morte — se corrige par un amendement daté, écrit dans
l'en-tête, qui cite le texte remplacé et dit ce qui n'a pas bougé. Voir l'ADR 0006.
Ça reste un point d'escalade (§9) : c'est un humain qui décide, pas toi.

Ouvre un ADR quand tu veux : changer une brique de la stack, changer le modèle
d'authentification, changer la stratégie de persistance ou de cache, ou introduire une
dépendance lourde.

---

## 9. Points d'escalade obligatoires

Arrête-toi et demande à un humain si :

- la tâche implique d'ajouter du contenu, une source ou un logo de bouquet réel ;
- la tâche demande un endpoint absent de `openapi.yaml` ;
- la tâche touche au chiffrement des identifiants ou à la logique de droits d'accès ;
- la tâche implique de contourner une politique de store ;
- tu constates une incohérence entre ce fichier et le code existant.

---

## 10. Le backlog vit à deux endroits : `docs/` et Plane

Les user stories et les sprints sont décrits **deux fois** :

1. dans le dépôt — `docs/backlog/` (sprints, stories, dette), `docs/roadmap/`,
   `docs/releases/` ;
2. dans une instance **Plane locale** — `http://localhost:8585/lumo-tv`
   (workspace `lumo-tv`, projet « lumo tv », identifiant `LUMOTV`,
   id `7c52f258-8226-4f83-a956-faac0a389773`).

**Toi, agent, tu décris et tu mets à jour les deux.** Une story créée, reformulée,
replanifiée, terminée ou annulée dans `docs/` est reportée dans Plane dans la même
session de travail, et inversement. Une tâche qui ne touche qu'un seul des deux côtés
n'est pas terminée.

En cas de divergence, **le dépôt fait foi** : Plane est une copie. Tu corriges Plane
d'après `docs/`, jamais l'inverse sans l'accord d'un humain.

### Correspondance

| Dans `docs/` | Dans Plane |
|---|---|
| `docs/backlog/sprint-NN.md` | cycle « Sprint NN » |
| story, tâche de sprint, dette (`US-xx`, `Sx-yy`, `SRV-xx`, `R020-xx`, `DETTE-n`…) | work item avec `external_source=lumo-docs` et `external_id` = l'ID du dépôt |
| thème fonctionnel | module |
| document Markdown | page |
| case cochée / pourcentage de la checklist | état du work item (« En recette » = code livré, DoD non encore rapportée) |

Pour mettre à jour un item, **retrouve-le par son `external_id`** ; ne le recrée pas.

### Accès à l'API

API REST : `http://localhost:8585/api/v1/workspaces/lumo-tv/projects/<id>/…`, en-tête
`X-API-Key`. Limite : 60 requêtes par minute.

La clé ne figure **pas** dans le dépôt (§5). Elle se lit dans `.env.plane`, à la racine,
gitignoré :

```
PLANE_BASE_URL=http://localhost:8585
PLANE_WORKSPACE=lumo-tv
PLANE_PROJECT_ID=7c52f258-8226-4f83-a956-faac0a389773
PLANE_API_KEY=<clé générée dans Plane : Settings → API tokens>
```

Si `.env.plane` est absent ou si Plane ne répond pas, **dis-le** dans ton compte rendu
et liste ce qui reste à reporter — ne passe pas la synchronisation sous silence.
