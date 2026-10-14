# R020-14 et R020-15 — audits du livrable — 11 et 14/10/2026

- Objet : lignes `R020-14` (aucun contenu réel, aucun secret ni URL sensible dans les preuves et
  logs) et `R020-15` (pas de parcours Google ou paiement non opérationnel exposé ; quotas existants
  non modifiés implicitement) de [`acceptance.md`](../../acceptance.md).
- Base auditée : `dev` @ `ccf4237` (après les PR #49 et #50), 1 548 fichiers suivis, 432 commits.
- Artefacts : APK debug TV et mobile de ce commit, build web de production (`pnpm build`), configuration
  embarquée de l'API (`application*.yml`), API locale `dev` et une API lancée **sans profil `dev`**.

## Verdict

| Ligne | Résultat |
|---|---|
| `R020-14` | ⚠️ **Conforme hors deux écarts faibles** : `BUG-R020-14-01` (noms de vraies chaînes dans des fixtures de test), `BUG-R020-14-02` (mot de passe de recette en clair dans deux scripts de preuve). Aucun secret réel, aucune URL de fournisseur, aucune image non conforme |
| `R020-15` | ⚠️ **Google et paiement conformes ; quotas non conformes** : `BUG-R020-15-01` — la configuration par défaut de la 0.2.0 plafonne FREE à 1 source et 2 appareils, la 0.1.0 n'en avait aucun |

## R020-14 — contenu réel, secrets, URL

### Captures (147 images suivies, toutes dans `qa-evidence/`)

Revue visuelle de chaque image : **147/147 conformes**. Uniquement des fixtures du banc (« Chaîne 01
FHD », « Le Voyage », « Banc … », « Chaîne 001 … »), des mires de test, des codes d'activation et
QR vers `localhost:3000`, des emails `@test.example`. Aucune vraie chaîne, aucun logo, aucun titre de
catalogue réel, aucune URL de fournisseur, aucun secret. La source réelle utilisée en septembre
(« test chine », playlist publique de deux chaînes) n'apparaît dans aucune capture conservée.

### Fichiers suivis (scan texte)

| Forme cherchée | Résultat |
|---|---|
| Clé privée, JWT, JWE, clé Stripe, clé cloud/GitHub, clé Plane, `Bearer …` | **aucune** |
| Valeurs assignées à `password`, `secret`, `token`, `key` | valeurs de test explicites (`test-only-…`, `ci-build-only-…`, `sup3r-s3cret-xtream-pw` dans un test de chiffrement) et code qui manipule des jetons sans en contenir — **sauf `BUG-R020-14-02`** |
| URL externes | infrastructure et documentation (npm, GitHub, Gradle, Stripe API, Google OAuth, W3C…), `169.254.169.254` des tests anti-SSRF ; URL Xtream uniquement en `panel.example…` / `user:pass` |
| Emails | `@test.example`, `exemple.com`, `lumo.tv`, un `foo@x.com` dans un commentaire SQL et l'auteur d'un paquet npm (`package-lock.json`) |
| Noms de vraies chaînes | **`BUG-R020-14-01`** ; « M6 » n'est qu'un identifiant de lot du contrat, « Netflix »/« Disney+ » une référence de style dans un prompt de design |

### Historique git (432 commits, tous les diffs)

- Jamais commités : aucun `.env` réel (seulement `.env.example`), aucun keystore.
- Les 14 valeurs longues des fichiers locaux `.env.plane`, `apps/api/.env`, `apps/api/.env.recette`,
  `apps/web/.env.local` ont été cherchées dans tout l'historique (`git log -S`) : **aucune n'y figure**
  (seuls l'identifiant de projet Plane et le nom du cookie de session, qui ne sont pas des secrets).
- Formes de secrets dans les diffs : 11 occurrences distinctes, toutes fictives (`/live/user/pass/1.ts`,
  `panel.example…`, `username=u&password=p`).

### Artefacts construits

| Artefact | Résultat |
|---|---|
| APK debug TV et mobile | aucun secret, aucune trace de compte de recette ; hôtes : bibliothèques, `lumo.tv`, API locale |
| Build web de production, `.next/static` (ce qui part au navigateur) | ni la valeur de `SESSION_SECRET`, ni aucun nom de variable serveur ; hôtes : documentation de bibliothèques |
| `application.yml` de l'API | `LUMO_JWT_SECRET` et `LUMO_ENCRYPTION_MASTER_KEY` **sans valeur par défaut** (démarrage refusé sans eux) ; Stripe vide par défaut |

### Écarts

- **`BUG-R020-14-01` (faible)** — noms de vraies chaînes dans des fixtures et une Javadoc, contraire à la
  règle 2 de `CLAUDE.md` (« fixtures comprises ») : `ChannelQualityTest.java` (« TF1 FHD »,
  « Canal+ Sport 4K », « France 2 SD », « Sky Sports UHD », « Arte fhd », « Nickelodeon »,
  « France 3 Nord »), `ChannelQuality.java` (Javadoc, « TF1 FHD », « Canal+ Sport 4K »),
  `FavoritesStateTest.kt` (« Arte »), `LiveDirectViewsTest.kt` (recherches « tf1 », « arte »).
  Ni logo, ni URL, ni flux. Correctif : noms fictifs.
- **`BUG-R020-14-02` (faible)** — mot de passe d'un compte de recette local en clair, en valeur de repli
  (`process.env.QA_PASS ?? "…"`), dans `s9-05-04-mobile-channel-day-2026-09-26/resync-a.mjs` et
  `s9-06-01-gd07-fix-2026-09-26/epg-probe.mjs`, contraire à la consigne de caviardage de `INDEX.md`.
  Compte `@test.example` d'une API locale, mais le mot de passe est aussi dans l'historique.
  Correctif : exiger `QA_PASS` ; changer ou supprimer ce compte de recette.

### Observations sans lot

- `EpgBenchFixtures.java` cite l'URL d'une mire publique de Mux (`test-streams.mux.dev`), jamais
  appelée par les tests (le commentaire le dit). Contenu de test, pas de catalogue.
- `POSTGRES_PASSWORD` a `lumo` pour valeur par défaut (commenté « dev-only ») : un déploiement qui
  oublie la variable tournerait avec. À couvrir par la procédure de déploiement.

## R020-15 — Google, paiement, quotas

### Parcours Google et paiement : conformes

| Surface | Constat |
|---|---|
| Web | aucune route `pricing`, `billing`, `checkout`, `google` ; aucune clé de traduction de prix ou de connexion Google ; les seules mentions sont des commentaires qui documentent le retrait (S8-06). « Abonnement » ne désigne que l'abonnement de l'utilisateur **chez son fournisseur** |
| Android mobile et TV | aucun écran Google ni de paiement ; aucune dépendance Google Sign-In ou Billing ; `AuthRepository.signInWithGoogle` existe mais **n'est appelé par aucun écran** (code mort, `dette.md` §1) |
| API | `POST /auth/oauth/google` → `503` « Google sign-in is not configured » ; `POST /billing/checkout-session` (`PREMIUM` ou `FREE`) et `/billing/portal-session` → `503` « No payment provider is configured » ; sans jeton → `401`. Le droit d'accès est **identique** avant et après ces appels. Le webhook n'existe pas ; le seul code qui écrit `entitlement` (`saveBillingCustomer`) garde `plan = FREE` |
| Compte neuf | `FREE` / `MANUAL` / `ACTIVE`, sans essai (`trial_ends_at` nul, `STRIPE_TRIAL_DAYS=0`) |

### Quotas : `BUG-R020-15-01` (élevé — décision produit)

| | 0.1.0 | 0.2.0, configuration par défaut | 0.2.0, profil `dev` |
|---|---|---|---|
| Plafond sources FREE | aucun (ni config, ni contrôle) | **1** (`LUMO_PLAN_FREE_MAX_SOURCES:1`) | 1000 |
| Plafond appareils FREE | aucun | **2** (`LUMO_PLAN_FREE_MAX_DEVICES:2`) | 1000 |

Les plafonds par défaut sont arrivés après le tag `v0.1.0` (`e9cd6c9`, 26/08). Le profil `dev` les
porte à 1000 : c'est pourquoi aucune recette locale, ni le drill R020-16, ne les a vus. Pourtant
`dette.md` écrit « tout le monde est `FREE` et illimité aujourd'hui », et les notes de version n'en
disent rien.

Essai sur la base migrée du drill R020-16 (compte créé en 0.1.0, 2 sources), API 0.2.0 lancée **sans
profil** (`SPRING_PROFILES_ACTIVE` vide) :

- connexion d'un nouvel appareil avec 5 appareils liés → **`409 DEVICE_LIMIT_REACHED`** « This plan
  allows 2 device(s) » ;
- après révocation de 4 appareils (base de test) : connexion OK ; droit `FREE`, `max_sources = 1`,
  `max_devices = 2` ; **les 2 sources restent intactes** (liste, chaînes, lecture, resynchro en
  `200`/`202`) ; ajout d'une source → **`409 SOURCE_LIMIT_REACHED`** « This plan allows 1 source(s) ».

Rien n'est détruit, mais un compte 0.1.0 passe d'illimité à 1 source / 2 appareils par la seule mise à
jour. À trancher par Hamza :

- **(a)** 0.2.0 gratuite **sans plafond** : valeurs par défaut vides (le contrat type « absent » comme
  illimité), comme le dit `dette.md` ;
- **(b)** garder 1 source / 2 appareils : l'écrire dans les notes de version et la décision produit,
  et assumer qu'un compte au-delà garde ses données sans pouvoir en ajouter.

## Hors périmètre de cet audit

- Les logs d'exécution d'un vrai déploiement (il n'y en a pas) ; seuls les logs conservés dans
  `qa-evidence/` ont été scannés.
- Un APK de **release** (signature et URL `https://api.lumo.tv/v1`) : non construit ; même code que le
  debug, hors `BuildConfig`.

## Correctifs (14/10/2026)

Branche `fix/BUG-R020-15-01-free-unlimited` (sur `qa/R020-14-15-audits`), à la demande de Hamza
(« on part sur (a), corrige aussi les deux écarts R020-14 »).

- **`BUG-R020-15-01`** — FREE sans plafond par défaut : `LUMO_PLAN_FREE_MAX_SOURCES` et
  `LUMO_PLAN_FREE_MAX_DEVICES` vides dans `application.yml` et `.env.example` ; la surcharge 1000 du
  profil `dev` est retirée, pour que le développement voie ce que voit la production. Les tests ont
  révélé un piège : un bloc dont toutes les valeurs sont vides est lié à `null`, et `limitsFor`
  levait une `NullPointerException` (`/me/entitlement` en `500`, activation TV cassée). `Plans`
  normalise désormais un plan absent en `Limits.UNLIMITED`. C'était déjà le cas pour qui suivait le
  conseil de `.env.example` (« laisser vide pour illimité »).
  - `FreePlanDefaultsIntegrationTest` (nouveau) : FREE tel que livré → `max_sources` et
    `max_devices` nuls, 2 sources et 3 appareils acceptés. **Rouge** avec les anciens défauts (2/2
    échouent), vert après.
  - `PlanLimitsIntegrationTest` fixe désormais 1 / 2 lui-même : le mécanisme de plafond reste prouvé.
  - API complète : 361 tests, 0 échec. API locale reconstruite : compte de recette `FREE`,
    plafonds `null`, 3ᵉ source acceptée (`202`).
- **`BUG-R020-14-01`** — noms fictifs dans `ChannelQualityTest` (« Chaîne 01 FHD », « Chaîne+ Sport
  4K », « Découverte HD », « Chaîne 2 SD », « Ciel Sports UHD », « Aube fhd », « Jeunesse »,
  « Chaîne 3 Nord »), la Javadoc de `ChannelQuality.java`, `FavoritesStateTest` (« Aube »,
  « Rivage ») et `LiveDirectViewsTest` (« voyage », « aube »). Un second scan, avec une liste élargie
  de chaînes et de marques, a trouvé « Discovery », « Nickelodeon » et « RMC », corrigés aussi ; il ne
  reste que « TV5 », identifiant d'écran de maquette. Tests : `ChannelQualityTest` 27/27,
  `feature:favorites` 11/11, `feature:live` 119/119, lint vert.
- **`BUG-R020-14-02`** — les deux scripts exigent `QA_PASS` (même forme que `s90601-source.mjs`). Le
  mot de passe restant dans l'historique git, le compte local `qa.mobile.cold.20260926@test.example`
  a reçu un nouveau mot de passe (hors dépôt, `apps/api/.env.recette`) : l'ancien est refusé (`401`),
  le nouveau accepté ; ses sessions ont été révoquées.

