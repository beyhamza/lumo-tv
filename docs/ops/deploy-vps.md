# Mettre Lumo en ligne sur un VPS (Docker)

Sprint 16-bis, tâche S16B-01. Environnement visé : **la piste de test interne Google Play**. Ce
n'est pas encore une production ouverte au public : voir « Limites » en fin de page.

Tout est dans [`deploy/`](../../deploy) :

| Fichier | Rôle |
|---|---|
| `docker-compose.prod.yml` | PostgreSQL, API, site web, Caddy (TLS), sauvegarde quotidienne ; le banc de test en option |
| `Caddyfile` | Certificats Let's Encrypt et proxy vers l'API et le site |
| `caddy.d/bench.caddy.example` | Le banc de test sur son propre sous-domaine, à activer au besoin |
| `backup.sh` | Un `pg_dump` par jour, gardé `LUMO_BACKUP_KEEP_DAYS` jours |
| `.env.example` | Toutes les variables ; la copie `deploy/.env` ne quitte jamais le serveur |

## Ce qu'il faut avant de commencer

- Un VPS Linux avec Docker et le plugin Compose, **2 Go de RAM au minimum** (API Java + PostgreSQL
  + Next.js ; 4 Go confortables), ports 80 et 443 ouverts.
- Deux noms DNS (trois avec le banc) pointant vers l'adresse du VPS : l'API (`api.…`) et le site.
  Les apps Android sont construites avec l'adresse de l'API (`LUMO_API_BASE_URL_RELEASE`) : la
  choisir avant de construire les bundles.
- Un fournisseur SMTP (vérification d'adresse e-mail, mot de passe oublié), avec un expéditeur
  vérifié.

## Première mise en ligne

```bash
git clone <dépôt> lumo && cd lumo/deploy
```

```bash
cp .env.example .env && chmod 600 .env
```

Remplir `.env`. Les secrets se génèrent sur le serveur :

```bash
openssl rand -base64 48
```

(`LUMO_JWT_SECRET`, `SESSION_SECRET`, `POSTGRES_PASSWORD`) et, pour la clé de chiffrement des
identifiants de source, **exactement 32 octets** :

```bash
openssl rand -base64 32
```

**Garder une copie de `LUMO_ENCRYPTION_MASTER_KEY` hors du VPS** : la perdre rend illisibles tous
les mots de passe de fournisseur enregistrés.

```bash
docker compose -f docker-compose.prod.yml --env-file .env up -d --build
```

Le premier démarrage construit les deux images (plusieurs minutes), crée la base, applique les
migrations Liquibase, puis Caddy obtient les certificats à la première requête sur chaque nom.

Vérifier :

```bash
docker compose -f docker-compose.prod.yml --env-file .env ps
```

```bash
curl -fsS https://<LUMO_API_DOMAIN>/actuator/health
```

Puis ouvrir `https://<LUMO_WEB_DOMAIN>/fr`, créer un compte, recevoir l'e-mail de vérification.

### Le banc de test (optionnel)

Pour que les testeurs aient une source sans rien de réel (mires et noms inventés) :

```bash
cp caddy.d/bench.caddy.example caddy.d/bench.caddy
```

```bash
docker compose -f docker-compose.prod.yml --env-file .env --profile bench up -d
```

Sources à saisir dans l'app : `https://<LUMO_BENCH_DOMAIN>/mixed.m3u` (chaînes et films) ou
`https://<LUMO_BENCH_DOMAIN>/multitrack.m3u` (pistes audio, sous-titres, qualités).

## Mettre à jour

```bash
cd lumo && git pull && cd deploy
```

```bash
docker compose -f docker-compose.prod.yml --env-file .env up -d --build
```

Les migrations s'appliquent au démarrage de l'API. Mettre `LUMO_VERSION` à jour dans `.env` pour
que le site affiche la bonne version.

## Sauvegardes

Un fichier `deploy/backups/lumo-<date>.dump` par jour. **Les copier ailleurs** (autre machine,
stockage objet) : une sauvegarde sur le disque du VPS ne protège pas d'une perte du VPS.

### Restaurer une sauvegarde

```bash
docker compose -f docker-compose.prod.yml --env-file .env stop lumo-api lumo-web
```

```bash
docker compose -f docker-compose.prod.yml --env-file .env exec -T postgres pg_restore --clean --if-exists -U lumo -d lumo < backups/lumo-<date>.dump
```

```bash
docker compose -f docker-compose.prod.yml --env-file .env start lumo-api lumo-web
```

## Journaux

```bash
docker compose -f docker-compose.prod.yml --env-file .env logs -f lumo-api
```

Aucune URL de flux ni aucun identifiant ne doit apparaître dans les journaux (AGENTS.md §5) : si
c'est le cas, c'est un défaut à ouvrir.

## Ce que fait la pile, et pourquoi

- **Seul Caddy est publié** (80/443). PostgreSQL, l'API et le site ne sont joignables que sur le
  réseau Docker interne.
- **Adresse du visiteur** : Caddy pose `X-Forwarded-For`, le serveur web le transmet à l'API
  (`apps/web/src/lib/api/client.ts`), et l'API ne le croit que de Caddy et du serveur web
  (`LUMO_TRUSTED_PROXIES`, adresses fixes du réseau `10.89.30.0/24`). Sans ça, toutes les
  connexions par le site partageraient une seule limite de tentatives.
- **Les flux ne passent jamais par le VPS** (ADR 0007) : il ne sert que l'API et le site.

## Limites (à lever avant une production ouverte)

- **Clé de chiffrement en variable d'environnement.** `apps/api/.env.example` prévoit un KMS en
  production. Acceptable pour un test interne avec des testeurs connus ; à décider avant la
  production.
- **Un seul serveur, pas de redondance.** Une panne du VPS arrête le service jusqu'à sa remise en
  route ; les sauvegardes quotidiennes bornent la perte à une journée.
- **Pas de supervision** au-delà des healthchecks Docker et de `restart: unless-stopped`.

## Essai local de la pile

Avant le premier déploiement, la pile peut tourner sur un poste, avec des certificats signés par
Caddy (`LUMO_CADDY_GLOBAL=local_certs`), des noms en `.localhost` et d'autres ports
(`LUMO_HTTP_PORT`, `LUMO_HTTPS_PORT`). C'est ainsi qu'elle a été vérifiée le 19/10/2026 (voir
`docs/backlog/sprint-16-bis.md`).
