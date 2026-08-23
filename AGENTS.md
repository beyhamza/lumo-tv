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
| Site web | `lumo.tv` | SEO, compte, abonnement, activation des TV |
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
│  └─ backlog/
├─ prompts/                   # prompts d'initialisation
└─ docker-compose.yml         # postgres + api en local
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
6. Commit atomique, message conventionnel.

**Ne fais jamais** : de refactor opportuniste hors du périmètre de la story ; de mise à
jour de version de dépendance non demandée ; de `git push --force` ; de modification
d'un ADR accepté.

---

## 8. Décisions (ADR)

`docs/adr/` contient les décisions structurantes, numérotées et datées. Un ADR accepté
ne se modifie pas : il se **remplace** par un nouvel ADR qui le supersede.

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
