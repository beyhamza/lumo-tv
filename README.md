# Lumo TV

Lecteur IPTV multi-plateformes. L'utilisateur apporte sa propre source (M3U ou Xtream
Codes) ; Lumo ne fournit aucun contenu.

| Surface | Dossier | Stack |
|---|---|---|
| lumo.tv | `apps/web` | Next.js App Router |
| iptv-lumo (Android) | `apps/android/app-mobile` | Kotlin, Compose, Media3 |
| iptv-lumo-tv (Android TV) | `apps/android/app-tv` | Kotlin, Compose for TV |
| lumo-api | `apps/api` | Spring Boot, PostgreSQL |

## Par où commencer

1. **[AGENTS.md](./AGENTS.md)** — règles pour les agents IA. À lire en premier.
2. **[docs/architecture.md](./docs/architecture.md)** — vue d'ensemble technique.
3. **[docs/domain-model.md](./docs/domain-model.md)** — vocabulaire, entités, API.
4. **[docs/adr/](./docs/adr/)** — décisions structurantes et leurs raisons.
5. **[docs/backlog/sprint-01.md](./docs/backlog/sprint-01.md)** — la verticale en cours.

Les prompts d'initialisation des agents sont dans **[docs/prompts/](./docs/prompts/)**.

## Règles qui cassent le projet si on les ignore

1. `packages/contracts/openapi.yaml` fait foi. On ne modifie pas un type sans passer par lui.
2. Aucune chaîne, aucun logo de bouquet, aucune URL de flux réelle dans le dépôt.
3. Les droits d'accès sont calculés côté serveur, jamais demandés à un store.

## Structure

```
lumo-tv/
├─ apps/
│  ├─ android/           # projet Gradle unique, 2 modules applicatifs
│  ├─ web/               # Next.js (App Router)
│  └─ api/               # Spring Boot
├─ packages/
│  └─ contracts/         # openapi.yaml + génération des trois clients
├─ docs/                 # architecture, modèle de domaine, ADR, backlog
└─ docker-compose.yml    # postgres + api en local
```

Les trois `apps/*` sont encore vides : leur scaffolding est le périmètre des tâches
S0-04 (api), S0-05 (android) et S0-06 (web) du [sprint 0](./docs/backlog/sprint-01.md).

## Le contrat d'API

`packages/contracts/openapi.yaml` est écrit à la main et fait foi. Les trois clients
en sont **générés**, jamais l'inverse, et le code généré est committé : la CI le
régénère et échoue à la moindre différence (ADR 0001).

```bash
npm ci --prefix packages/contracts
```

```bash
npm --prefix packages/contracts run generate
```

Après toute modification du contrat, régénère et committe le résultat avec le
contrat. Détail du pipeline : [packages/contracts/README.md](./packages/contracts/README.md).

## Environnement local

PostgreSQL 16 + `lumo-api`, avec healthchecks. Le web et Android tournent nativement
et pointent vers `http://localhost:8080` (`10.0.2.2:8080` depuis l'émulateur Android).

```bash
cp apps/api/.env.example apps/api/.env
```

Renseigne au minimum `LUMO_JWT_SECRET` et `LUMO_ENCRYPTION_MASTER_KEY` — la stack
refuse de démarrer sans eux, plutôt que de tourner sur une clé connue de tous.

```bash
docker compose --env-file apps/api/.env up -d
```

Chaque application a son propre modèle d'environnement, chaque variable commentée :
[`apps/api/.env.example`](./apps/api/.env.example),
[`apps/web/.env.example`](./apps/web/.env.example),
[`apps/android/.env.example`](./apps/android/.env.example).
Les `.env` réels sont gitignorés et ne sont jamais committés.

## Licence

Propriétaire — tous droits réservés. Voir [LICENSE](./LICENSE).
