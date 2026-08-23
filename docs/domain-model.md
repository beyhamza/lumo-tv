# Modèle de domaine — Lumo TV

Vocabulaire commun aux trois applications. Les noms d'entités et de champs ci-dessous
sont ceux qui doivent apparaître dans le code, dans `openapi.yaml` et dans les schémas
de base. Pas de synonymes.

---

## 1. Glossaire

| Terme | Définition |
|---|---|
| **Source** | Ce que l'utilisateur enregistre pour accéder à son contenu : une playlist M3U ou un compte Xtream Codes. Un utilisateur peut en avoir plusieurs. |
| **M3U / M3U8** | Fichier playlist texte. Universel mais pauvre : pas de catalogue structuré, pas d'info de compte. Attributs utiles portés par `#EXTINF` : `tvg-id`, `tvg-name`, `tvg-logo`, `group-title`. |
| **Xtream Codes** | API HTTP (`player_api.php`) exposée par la plupart des panels. Entrée : `host` + `username` + `password`. Retourne un catalogue structuré (Live / VOD / Séries), l'EPG et les infos de compte. **Format privilégié** : bien meilleure UX. |
| **Stalker / MAG** | Portail ancien, identification par adresse MAC. Hors périmètre v1. |
| **XMLTV** | Format standard du guide des programmes, souvent servi gzippé. |
| **Channel** | Une chaîne live. |
| **Stream URL** | URL du flux. **Donnée sensible** : jamais loguée, jamais exposée hors du contexte de l'utilisateur propriétaire. |
| **Entitlement** | Droit d'accès d'un utilisateur à une offre. Calculé côté serveur, seule source de vérité. |
| **Device** | Une installation de l'app liée à un compte. |
| **Device code** | Code court affiché par la TV pour s'appairer via le web. |

---

## 2. Entités

### `user`
| Champ | Type | Note |
|---|---|---|
| `id` | uuid | |
| `email` | citext unique | |
| `password_hash` | text nullable | null si compte SSO uniquement |
| `display_name` | text nullable | |
| `locale` | text | `fr`, `en` |
| `email_verified_at` | timestamptz nullable | |
| `created_at` / `updated_at` | timestamptz | |

Hachage : Argon2id.

### `oauth_identity`
`id`, `user_id` → user, `provider` (`GOOGLE` | `APPLE`), `provider_user_id`, `created_at`.
Unique sur `(provider, provider_user_id)`.

Un même email arrivant par SSO puis par mot de passe doit **fusionner** sur le compte
existant, jamais en créer un second.

### `device`
`id`, `user_id`, `platform` (`ANDROID_MOBILE` | `ANDROID_TV` | `WEB`), `name`,
`model`, `app_version`, `last_seen_at`, `created_at`.

### `refresh_token`
`id`, `user_id`, `device_id`, `token_hash`, `expires_at`, `revoked_at`, `replaced_by`.

Rotation : chaque usage émet un nouveau token et révoque l'ancien. La réutilisation d'un
token révoqué révoque **toute la chaîne** du device — c'est la détection de vol.

### `device_authorization`
`id`, `device_code_hash`, `user_code` (8 car.), `platform`, `status`
(`PENDING` | `APPROVED` | `DENIED` | `EXPIRED` | `CONSUMED`), `user_id` nullable,
`expires_at` (10 min), `interval_seconds` (5), `created_at`.

### `source`
| Champ | Type | Note |
|---|---|---|
| `id` | uuid | |
| `user_id` | uuid | |
| `label` | text | nom donné par l'utilisateur |
| `kind` | enum | `M3U_URL` \| `M3U_FILE` \| `XTREAM` |
| `host` | text nullable | Xtream : base URL |
| `username` | text nullable | Xtream |
| `password_encrypted` | bytea nullable | Xtream, AES-256-GCM |
| `m3u_url` | text nullable | |
| `epg_url` | text nullable | XMLTV |
| `status` | enum | `PENDING` \| `SYNCING` \| `READY` \| `ERROR` |
| `error_code` | text nullable | code stable, pas un message libre |
| `last_synced_at` | timestamptz nullable | |
| `expires_at` | timestamptz nullable | Xtream : expiration du compte |
| `max_connections` | int nullable | Xtream |

**Chiffrement.** `password_encrypted` utilise AES-256-GCM avec une clé de données
elle-même chiffrée par une clé maître hors base (variable d'environnement en dev, KMS
en production). Le nonce est stocké avec le ciphertext. Le mot de passe en clair ne
quitte jamais la couche `source`, n'est **jamais** renvoyé par l'API — même à son
propriétaire — et n'apparaît dans aucun log.

### `category`
`id`, `source_id`, `external_id`, `name`, `content_type` (`LIVE` | `VOD` | `SERIES`),
`position`.

### `channel`
`id`, `source_id`, `category_id` nullable, `external_id`, `name`, `logo_url`,
`tvg_id`, `stream_url`, `position`, `is_adult`.

Index : `(source_id, category_id, position)` et un index trigram sur `name` pour la recherche.

### `epg_programme`
`id`, `source_id`, `tvg_id`, `starts_at`, `ends_at`, `title`, `description`, `category`.

Index : `(source_id, tvg_id, starts_at)`. Purge automatique au-delà de J+3 / J-1.

### `favorite_group`
`id`, `user_id`, `name`, `position`.
Un groupe par défaut (« Favoris ») est créé au premier ajout.

### `favorite`
`id`, `group_id`, `user_id`, `source_id`, `channel_id`, `position`.

### `playback_progress`
`id`, `user_id`, `item_type` (`VOD` | `EPISODE`), `item_ref`, `position_ms`,
`duration_ms`, `updated_at`. Le live n'a pas de progression.

### `entitlement`
`id`, `user_id`, `plan` (`FREE` | `PREMIUM`), `status` (`ACTIVE` | `PAST_DUE` |
`CANCELED` | `EXPIRED`), `provider` (`STRIPE` | `PLAY` | `MANUAL`),
`provider_ref`, `current_period_end`, `updated_at`.

Un seul entitlement actif par utilisateur. Alimenté par les webhooks Stripe (v1),
puis par les RTDN Play (v2).

---

## 3. Surface API v1

Base `/v1`. Auth par `Authorization: Bearer <access_token>` sauf mention contraire.

### Auth
```
POST   /auth/register              public
POST   /auth/login                 public
POST   /auth/refresh               public (refresh token)
POST   /auth/logout
POST   /auth/oauth/google          public — échange d'un id_token Google
GET    /auth/verify-email          public
POST   /auth/password/forgot       public
POST   /auth/password/reset        public

POST   /auth/device/code           public — la TV demande un code
POST   /auth/device/approve        authentifié web — valide le user_code
POST   /auth/device/token          public — polling par la TV
```

### Compte
```
GET    /me
PATCH  /me
GET    /me/entitlement
GET    /me/devices
DELETE /me/devices/{id}
```

### Sources
```
GET    /sources
POST   /sources                    validation puis ingestion asynchrone
GET    /sources/{id}
PATCH  /sources/{id}
DELETE /sources/{id}
POST   /sources/{id}/sync          resynchronisation forcée
```

### Catalogue
```
GET    /sources/{id}/categories?contentType=LIVE
GET    /sources/{id}/channels?categoryId=&q=&page=&size=
GET    /channels/{id}/playback     retourne la stream URL, à la demande
GET    /channels/{id}/epg?from=&to=
```

`GET /channels/{id}/playback` est **délibérément séparé** du listing : la stream URL
n'est émise qu'au moment de la lecture, avec vérification de propriété. Une liste de
1 000 chaînes ne transporte pas 1 000 URL de flux.

### Données utilisateur
```
GET    /me/favorites
POST   /me/favorites
DELETE /me/favorites/{id}
GET    /me/favorite-groups
POST   /me/favorite-groups
PUT    /me/progress
```

### Format d'erreur

RFC 7807 (`application/problem+json`), avec un champ `code` **stable et machine-readable**
que les clients traduisent. Le champ `detail` est destiné aux logs, jamais affiché brut.

```json
{
  "type": "https://lumo.tv/errors/source-unreachable",
  "title": "Source unreachable",
  "status": 422,
  "code": "SOURCE_UNREACHABLE",
  "detail": "Connection timed out after 10s",
  "instance": "/v1/sources"
}
```

Codes d'erreur d'ingestion à couvrir dès la v1, chacun avec un message utilisateur
actionnable en FR et EN :

`SOURCE_UNREACHABLE` · `SOURCE_AUTH_FAILED` · `SOURCE_EXPIRED` ·
`SOURCE_MAX_CONNECTIONS` · `SOURCE_INVALID_FORMAT` · `SOURCE_EMPTY` ·
`SOURCE_TOO_LARGE`

C'est le point de friction n°1 de l'onboarding. « Une erreur est survenue » y perd des
utilisateurs ; « Vos identifiants ont été refusés par le serveur » les récupère.
