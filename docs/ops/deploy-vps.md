# Mettre Lumo en ligne sur un VPS (Docker)

Sprint 16-bis, tâche S16B-01. Environnement visé : **la piste de test interne Google Play**. Ce
n'est pas encore une production ouverte au public : voir « Limites » en fin de page.

Tout est dans [`deploy/`](../../deploy) :

| Fichier | Rôle |
|---|---|
| `docker-compose.prod.yml` | PostgreSQL, API, site web, Caddy (TLS), sauvegarde quotidienne ; le banc de test en option. Les images de l'API et du site sont **tirées de GHCR**, jamais construites sur le serveur |
| `docker-compose.build.yml` | Surcouche qui construit ces deux images sur place, pour l'essai local |
| `Caddyfile` | Certificats Let's Encrypt et proxy vers l'API et le site |
| `caddy.d/bench.caddy.example` | Le banc de test sur son propre sous-domaine, à activer au besoin |
| `backup.sh` | Un `pg_dump` par jour, gardé `LUMO_BACKUP_KEEP_DAYS` jours |
| `.env.example` | Toutes les variables ; la copie `deploy/.env` ne quitte jamais le serveur |

Les images sont construites par [`.github/workflows/images.yml`](../../.github/workflows/images.yml)
et publiées sur le registre de GitHub (`ghcr.io/beyhamza/lumo-api` et `lumo-web`).

## Ce qu'il faut avant de commencer

- Un VPS Linux avec Docker et le plugin Compose, **2 Go de RAM au minimum** (API Java + PostgreSQL
  + Next.js ; 4 Go confortables), ports 80 et 443 ouverts et rien d'autre que SSH. Docker publie
  ses ports en contournant `ufw` : seul Caddy en publie, ne jamais en ajouter « pour tester ».
- Deux noms DNS (trois avec le banc) pointant vers l'adresse du VPS : l'API (`api.…`) et le site.
  `www.` peut s'y ajouter (CNAME vers le site) : Caddy le redirige vers le domaine nu. Aucun autre
  enregistrement A sur ces noms — la page de parking d'un registraire, par exemple, en ajoute un et
  fait échouer une partie des visites et le certificat Let's Encrypt.
  Les apps Android sont construites avec l'adresse de l'API (`LUMO_API_BASE_URL_RELEASE`) : la
  choisir avant de construire les bundles.
- Un fournisseur SMTP (vérification d'adresse e-mail, mot de passe oublié), avec un expéditeur
  vérifié.

## Installer Docker (Ubuntu)

Depuis le dépôt de Docker, pas le paquet `docker.io` d'Ubuntu :

```bash
sudo install -m 0755 -d /etc/apt/keyrings && sudo curl -fsSL https://download.docker.com/linux/ubuntu/gpg -o /etc/apt/keyrings/docker.asc
```

```bash
echo "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.asc] https://download.docker.com/linux/ubuntu $(. /etc/os-release && echo $VERSION_CODENAME) stable" | sudo tee /etc/apt/sources.list.d/docker.list
```

```bash
sudo apt update && sudo apt install -y docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin
```

```bash
sudo usermod -aG docker $USER
```

Se reconnecter, puis `docker compose version`. Si Docker ne publie pas encore de dépôt pour la
version d'Ubuntu installée, remplacer le nom de version par celui de la LTS précédente dans
`/etc/apt/sources.list.d/docker.list`.

## Publier les images

Le workflow `images` construit `lumo-api` et `lumo-web` et les pousse sur GHCR, taguées par la
version (sans le `v`) et par le commit court :

- **à chaque tag de version** (`v0.4.0`, `v0.4.0-rc.1`), posé sur un commit dont la CI est verte
  (AGENTS.md §7) ;
- **à la main** (GitHub → Actions → images → Run workflow), avec une version explicite, pour
  publier avant tout tag.

L'image du site contient en dur l'adresse de l'API et celle du site (variables `NEXT_PUBLIC_`).
Le workflow lit les variables du dépôt `LUMO_API_DOMAIN` et `LUMO_WEB_DOMAIN` (Settings → Secrets
and variables → Actions → Variables), par défaut `api.lumo-tv.com` et `lumo-tv.com`. Les images
sont construites pour `linux/amd64` ; un VPS ARM demande `LUMO_IMAGE_PLATFORMS=linux/arm64`.

Les paquets d'un dépôt privé sont privés : le serveur se connecte une fois à GHCR avec un jeton
**classique** limité à `read:packages` (GitHub → Settings → Developer settings → Personal access
tokens → Tokens (classic)) :

```bash
docker login ghcr.io -u <utilisateur GitHub>
```

Le jeton se colle à l'invite, pas sur la ligne de commande. Les anciennes versions sont à
supprimer de temps en temps (page du paquet → Package settings) : le stockage des paquets privés
est limité selon l'offre GitHub.

## Première mise en ligne

Le serveur n'a besoin que de `deploy/` (et de `apps/web/e2e/bench` pour le banc). Un clone du
dépôt reste le plus simple pour suivre les changements de configuration ; dépôt privé : une
**clé de déploiement** en lecture seule (Settings → Deploy keys), générée sur le serveur.

```bash
git clone git@github.com:beyhamza/lumo-tv.git lumo && cd lumo/deploy
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

`LUMO_VERSION` désigne des images déjà publiées (voir « Publier les images »).

```bash
docker compose -f docker-compose.prod.yml --env-file .env pull
```

```bash
docker compose -f docker-compose.prod.yml --env-file .env up -d
```

Le premier démarrage crée la base, applique les migrations Liquibase, puis Caddy obtient les
certificats à la première requête sur chaque nom.

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

Publier la nouvelle version (tag), puis sur le serveur : récupérer la configuration, mettre
`LUMO_VERSION` à jour dans `.env`, tirer et relancer.

```bash
cd lumo && git pull && cd deploy
```

```bash
docker compose -f docker-compose.prod.yml --env-file .env pull
```

```bash
docker compose -f docker-compose.prod.yml --env-file .env up -d
```

Les migrations s'appliquent au démarrage de l'API. Revenir en arrière, c'est remettre l'ancienne
`LUMO_VERSION` — sauf si une migration de la nouvelle version a déjà tourné : son `--rollback`
d'abord.

```bash
docker image prune -f
```

libère les images qui ne servent plus.

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
`docs/backlog/sprint-16-bis.md`). Les images se construisent alors sur place, avec la surcouche :

```bash
docker compose -f docker-compose.prod.yml -f docker-compose.build.yml --env-file .env up -d --build
```
