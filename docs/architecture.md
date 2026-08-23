# Architecture — Lumo TV

## 1. Vue d'ensemble

```
                        ┌──────────────────┐
                        │   lumo.tv (web)  │
                        │  Next.js         │
                        │  SEO · compte    │
                        │  /activate       │
                        └────────┬─────────┘
                                 │ REST /v1
┌────────────────┐               │              ┌────────────────┐
│ iptv-lumo      │───────────────┼──────────────│ iptv-lumo-tv   │
│ Android mobile │               │              │ Android TV     │
└────────────────┘               │              └────────────────┘
                                 ▼
                        ┌──────────────────┐
                        │    lumo-api      │
                        │  Spring Boot     │
                        └────────┬─────────┘
                                 │
                    ┌────────────┴────────────┐
                    │                         │
              ┌───────────┐            ┌─────────────┐
              │ PostgreSQL│            │  Stripe     │
              └───────────┘            └─────────────┘

Flux vidéo : client ──────────────────────► serveur IPTV de l'utilisateur
             (direct, jamais via lumo-api)
```

**Point capital : le média ne transite jamais par notre infrastructure.** `lumo-api`
manipule des métadonnées (listes de chaînes, EPG, favoris, progression). Le lecteur
ouvre le flux en direct vers le serveur de l'utilisateur. Cela détermine notre coût
d'infrastructure (faible) et notre posture (nous ne sommes pas un diffuseur).

## 2. Backend — `lumo-api`

Architecture hexagonale légère, découpée par domaine plutôt que par couche technique :

```
tv.lumo.api/
├─ auth/          # comptes, JWT, refresh rotation, SSO, device code
├─ source/        # sources utilisateur, chiffrement des identifiants
├─ ingest/        # parsing M3U, client Xtream, parsing XMLTV
├─ catalog/       # chaînes, catégories, VOD, séries, recherche
├─ epg/           # normalisation et exposition du guide
├─ userdata/      # favoris, groupes, progression de lecture
├─ billing/       # Stripe, webhooks, Entitlement
└─ shared/        # crypto, erreurs, pagination, config
```

### Ingestion

Le parsing est **toujours** côté serveur, jamais sur le device. Un M3U de 15 000 chaînes
ou un XMLTV gzippé de 200 Mo tuent un téléphone d'entrée de gamme et une box TV encore plus.

- `M3U` : téléchargement, parsing des attributs `#EXTINF` (`tvg-id`, `tvg-logo`,
  `group-title`), normalisation, persistance.
- `Xtream Codes` : appels à `player_api.php` (`get_live_categories`, `get_live_streams`,
  `get_vod_streams`, `get_series`, `get_short_epg`), mapping vers notre modèle.
- `XMLTV` : parsing en streaming (SAX/StAX, jamais DOM), fenêtre glissante de J-1 à J+3.

L'ingestion est **asynchrone**. Créer une source retourne immédiatement en `PENDING` ;
le client poll ou reçoit une notification. Une source ne bloque jamais une requête HTTP.

### Modèle de concurrence : virtual threads, style bloquant

L'ingestion est massivement I/O-bound : récupérer un M3U, un XMLTV gzippé ou une
réponse `player_api.php` immobilise un thread des dizaines de secondes à ne rien faire
d'autre qu'attendre une socket. C'est le cas d'usage canonique des threads virtuels.

`spring.threads.virtual.enabled=true`, et **code bloquant séquentiel partout**. Pas de
WebFlux, pas de `Mono`/`Flux`. Les stack traces restent lisibles, le débogueur reste
utile.

**Le corollaire non négociable : la backpressure devient explicite.** Un pool de threads
borné jouait ce rôle gratuitement ; les threads virtuels suppriment ce plafond. Sans
garde-fou, dix mille synchronisations ouvrent dix mille connexions sortantes vers le
serveur d'un utilisateur et font bannir son compte. Ce n'est pas un problème de
performance, c'est un problème produit.

Donc, dans `ingest/` : un `Semaphore` par host de destination sur tout appel sortant,
et un plafond global de synchronisations concurrentes. HikariCP reste dimensionné pour
la base, jamais pour le nombre de threads. Détails et pièges dans `adr/0005`.

### Persistance : PostgreSQL

Choisi contre DynamoDB. L'EPG se requête par plage temporelle sur un ensemble de
chaînes (« que passe-t-il sur ces 40 chaînes entre 20h et 23h »), et le catalogue VOD
demande de la recherche plein texte. Deux patterns d'accès qui coûtent cher à modéliser
en clé-valeur. Voir `adr/0002`.

Isolation multi-tenant : chaque table portant des données utilisateur a une colonne
`user_id` indexée. Aucune requête ne s'écrit sans filtre sur `user_id`.

## 3. Android — un projet, deux applications

```
apps/android/
├─ app-mobile/            # applicationId tv.lumo.android
├─ app-tv/                # applicationId tv.lumo.androidtv
├─ core/
│  ├─ designsystem/       # tokens partagés, composants déclinés mobile/TV
│  ├─ network/            # client généré depuis openapi.yaml
│  ├─ database/           # Room : cache chaînes, EPG, favoris, offline-first
│  ├─ player/             # abstraction au-dessus de Media3
│  ├─ auth/               # stockage sécurisé des tokens, refresh
│  └─ common/
├─ feature/
│  ├─ onboarding/ · auth/ · source/ · live/ · vod/ · series/ · search/ · settings/
└─ build-logic/           # convention plugins Gradle
```

Domaine, réseau, cache et lecture sont partagés. **Seule la couche UI diverge, et elle
doit diverger.** Le TV n'est pas du mobile agrandi :

| | Mobile | TV |
|---|---|---|
| Entrée | tactile | D-pad, focus explicite |
| Distance de lecture | 30 cm | 3 m |
| Overscan | non | marges 5 % obligatoires |
| Navigation | bottom bar, retour geste | rail latéral, bouton BACK |
| Densité | listes verticales | grilles horizontales |

Chaque composant focusable en TV doit avoir un état de focus **visuellement évident**
(échelle + bordure + élévation), testé à la télécommande, jamais uniquement à la souris.

**Deux `applicationId`, deux fiches Play.** Isole le risque de review — la catégorie
IPTV passe en revue manuelle et l'app TV est plus scrutée. Permet de livrer le mobile
sans attendre. Conséquence : un achat sur une fiche n'est pas reconnu par l'autre, d'où
les droits d'accès côté serveur (§5).

## 4. Web — `lumo.tv`

Trois zones aux contraintes opposées, à ne pas mélanger :

| Zone | Rendu | Objectif |
|---|---|---|
| `/`, `/guides/*`, `/blog/*` | SSG / ISR | SEO, aucun JS bloquant, LCP < 2 s |
| `/app/*` | SSR authentifié | compte, sources, abonnement, appareils |
| `/activate` | SSR minimal | saisie du code d'activation TV |

Le contenu SEO est la porte d'entrée : guides « configurer une playlist M3U »,
« IPTV sur Android TV », « M3U ou Xtream Codes, quelle différence ». Contenu utile et
générique — jamais de nom de fournisseur.

## 5. Authentification et droits d'accès

### Sessions

Access token JWT court (15 min) + refresh token opaque à **rotation**, stocké haché en
base et lié à un `Device`. Web : cookies `httpOnly` + `SameSite=Lax`. Android :
DataStore chiffré adossé au Keystore.

### Activation TV — Device Authorization Grant

Modelé sur RFC 8628. Saisir un email et un mot de passe à la télécommande est une
expérience punitive ; c'est le premier point d'abandon d'une app TV.

```
TV                          lumo-api                      Téléphone
 │  POST /auth/device/code      │                              │
 │─────────────────────────────►│                              │
 │  { user_code: "LUMO-4X7B",   │                              │
 │    device_code, interval }   │                              │
 │◄─────────────────────────────│                              │
 │                              │                              │
 │  affiche LUMO-4X7B           │   utilisateur ouvre          │
 │                              │   lumo.tv/activate           │
 │                              │◄─────────────────────────────│
 │                              │   POST /auth/device/approve  │
 │  POST /auth/device/token     │   (session web authentifiée) │
 │──── poll (interval) ────────►│                              │
 │  { access, refresh }         │                              │
 │◄─────────────────────────────│                              │
```

`user_code` : 8 caractères d'un alphabet sans ambiguïté (pas de `0/O`, `1/I/L`),
durée de vie 10 min, usage unique, rate-limit strict sur `approve`.

### Droits d'accès

Table `entitlement`, source de vérité unique. Un client demande
`GET /v1/me/entitlement` — **jamais au store**. V1 : Stripe uniquement, depuis le web.
V2 : Play Billing alimentera la même table via RTDN. Voir `adr/0003`.

## 6. Cache mutualisé — v2, avec précautions

Idée : quand plusieurs utilisateurs pointent vers le même serveur IPTV, mutualiser le
travail d'ingestion.

Conception à respecter le jour où on l'implémente :
- clé de mutualisation = **hash du host**, jamais les identifiants ;
- ne mutualiser que l'**EPG** (données de programmation, largement publiques) et la
  structure de catégories ;
- ne **jamais** mutualiser une URL de flux, un identifiant, ni exposer à un utilisateur
  un catalogue auquel son propre compte ne donne pas accès ;
- opt-out utilisateur.

Sans ces garde-fous, on transforme un lecteur en annuaire — un tout autre produit, avec
un tout autre profil de risque.

## 7. Environnement local

`docker-compose.yml` monte PostgreSQL + `lumo-api`. Le web et Android tournent nativement
et pointent vers `http://localhost:8080` (`10.0.2.2:8080` depuis l'émulateur Android).
