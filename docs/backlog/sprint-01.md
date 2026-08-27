# Backlog — Sprint 0 & Sprint 1

## Objectif de la verticale

> Un utilisateur crée un compte (email ou Google), enregistre une source M3U ou Xtream,
> et regarde sa première chaîne — sur mobile **et** sur TV.

C'est une **tranche verticale** : elle traverse les trois applications et prouve que
l'architecture tient. Rien d'autre n'est construit tant qu'elle n'est pas verte de bout
en bout. Pas de VOD, pas de séries, pas de recherche, pas d'EPG riche, pas de paiement.

---

## Definition of Ready

Une story est prête si : les critères d'acceptation sont écrits en Gherkin ; les
endpoints qu'elle utilise existent dans `openapi.yaml` (ou sa modification fait partie
de la story) ; les libellés FR et EN sont fournis ; les cas d'erreur sont énumérés.

## Definition of Done

Code mergé sur `main` · tests unitaires du domaine verts · build CI vert sur les trois
apps · aucune chaîne en dur dans l'UI · aucun secret ni URL de flux dans les logs ·
doc mise à jour si un comportement observable change · démo faite sur device réel
(téléphone **et** box/émulateur TV pilotés à la télécommande, pas à la souris).

---

## État au 27 août 2026 — **sprint 1 non clos, ~71 %**

**Convention de suivi, valable pour tous les sprints.** Une case par tâche, et un
pourcentage dès que l'avancement est partiel. Le pourcentage mesure
l'**implémentation** sur les surfaces que la story nomme, à parts égales. La
colonne **DoD** est séparée et vaut ce qu'elle dit : la Definition of Done exige
une démo sur device réel, télécommande en main pour la TV. Du code écrit et une
story finie sont deux états différents.

| | US | Avancement | Ce qui manque | DoD |
|---|---|---|---|---|
| ☐ | US-01 Compte email | 100 % | rien : l'écran mobile existe. Reste la démo sur device réel (DoD) | ☐ |
| ☐ | US-02 Connexion email | 100 % | rien : l'écran mobile existe. Reste la démo sur device réel, qui est la DoD et pas l'implémentation | ☐ |
| ☐ | US-03 Google | **33 %** | le bouton web, l'écran mobile | ☐ |
| ☐ | US-04 Session et rotation | **90 %** | le mobile ouvre et ferme une session, et la chaîne « refresh refusé → reconnexion » est prouvée en test (S2-07) ; la TV ne sait toujours pas en ouvrir une (S2-12), et la survie au redémarrage se vérifie sur un appareil | ☐ |
| ☐ | US-05 Activer une TV | **66 %** | l'écran TV — code, QR, polling | ☐ |
| ☐ | US-06 Source Xtream | 100 % | rien : formulaire, suivi de l'import, chaînes comptées, date d'expiration, et une sortie par erreur. Reste la démo sur device réel (DoD) | ☐ |
| ☐ | US-07 Playlist M3U | 100 % | idem. Le banc d'essai couvre cinq de ses six chemins (S2-03) | ☐ |
| ☐ | US-08 Chaînes par catégorie | **50 %** | l'écran : Room et `CataloguePager` existent, rien ne les affiche | ☐ |
| ☐ | US-09 Lecture mobile | **50 %** | le câblage : `Media3LumoPlayer` existe, aucun écran ne l'ouvre | ☐ |
| ☐ | US-10 Lecture TV | **33 %** | tout le client TV | ☐ |

**~71 % des 63 points**, et **0 story sur 10** au sens de la Definition of Done.

Le serveur tient la verticale entière. Le web couvre l'inscription, la connexion
et l'activation. **Les deux applications Android sont des placeholders de bout en
bout** — chaque `*MobileScreen` et `*TvScreen` appelle `LumoMobilePlaceholder` ou
`LumoTvPlaceholder`. Les couches basses, elles, sont réelles et testées : session
chiffrée, rotation de token, Room, Media3, design system, navigation.

Côté serveur, trois contrôleurs sur six : `auth`, `sources`, `catalog`. `/me`,
`/me/entitlement`, `/me/devices`, les favoris et la progression n'ont **aucun
contrôleur** — état figé par `NotImplementedEndpointsTest`, que le web lit comme
« écran en cours de construction ». Aucune de ces surfaces n'est nécessaire aux dix
stories ci-dessus.

Ce qui reste à faire est décrit dans **[`sprint-02.md`](./sprint-02.md)**, qui ne
réécrit aucune story : les critères d'acceptation Gherkin de ce document restent la
référence. Sprint 1 se ferme quand sprint 2 est vert.

---

# Sprint 0 — Fondations

> Aucune valeur utilisateur. Objectif : qu'un agent puisse démarrer une story
> le lendemain sans poser de question d'outillage.

| | ID | Tâche | App | Avancement |
|---|---|---|---|---|
| ☑ | S0-01 | Monorepo, `.gitignore`, `.editorconfig`, licence, `AGENTS.md` locaux | racine | 100 % |
| ☑ | S0-02 | `openapi.yaml` v0 couvrant les endpoints du sprint 1 | contracts | 100 % |
| ☑ | S0-03 | Pipeline de génération des trois clients + vérification CI de non-dérive | contracts | 100 % |
| ☑ | S0-04 | Scaffolding Spring Boot 4.1 / Java 25, virtual threads, Liquibase, `docker-compose` (Postgres + api), healthcheck | api | 100 % |
| ☑ | S0-05 | Scaffolding Gradle Android : `app-mobile`, `app-tv`, `core/*`, `build-logic`, version catalog | android | 100 % |
| ☑ | S0-06 | Scaffolding Next.js : App Router, Tailwind, shadcn/ui, `next-intl` FR/EN | web | 100 % |
| ☑ | S0-07 | Design system : tokens partagés, déclinaison mobile / TV / web | android + web | 100 % |
| ☑ | S0-08 | CI GitHub Actions : lint + test + build des trois apps | racine | 100 % |
| ☑ | S0-09 | `.env.example` documenté sur les trois apps | racine | 100 % |

**S0-07 mérite son explication**, parce qu'il était coché à tort. « Des tokens
existent » n'est pas « ce sont ceux de la charte ». Le web portait la palette
shadcn par défaut, et Android une palette inventée — un bleu froid `#4CB8FF`,
sans rapport avec la direction Spectre arrêtée en passe 1.

Le web est aligné depuis le 26 août : palette, Sora et Space Mono, rayons,
signature de focus cyan, thème clair réservé au marketing. **Android l'est depuis
le 27 août** (`S2-00`), avant le premier écran, ce qui était tout l'intérêt de la
tâche : palette Spectre, rayons 8/14/20, et les deux échelles typographiques
telles que la charte les dérive.

La typographie a été tranchée plutôt que reportée : **police système sur Android,
Sora sur le web**. La charte demande Sora et déclare elle-même `system-ui` comme
repli ; les applications prennent le repli, parce qu'embarquer une famille coûte
un téléchargement d'APK sur une box TV pour une différence invisible à trois
mètres. Le raisonnement complet est dans
[`design/design-system.md`](../design/design-system.md).

---

# Sprint 1 — Verticale connexion → source → lecture

## Épique A — Authentification

### US-01 — Créer un compte par email
> **En tant que** visiteur, **je veux** créer un compte avec mon email et un mot de passe,
> **afin de** retrouver mes sources et mes favoris sur tous mes appareils.

```gherkin
Scenario: Inscription réussie
  Given je suis sur l'écran d'inscription
  When je saisis un email valide et un mot de passe d'au moins 10 caractères
  Then mon compte est créé
  And je suis connecté immédiatement
  And un email de vérification m'est envoyé

Scenario: Email déjà utilisé
  When je saisis un email déjà enregistré
  Then je vois un message m'invitant à me connecter ou à réinitialiser mon mot de passe
  And la réponse ne permet pas de distinguer un email existant d'un email inexistant
    par son temps de réponse

Scenario: Mot de passe trop faible
  When je saisis un mot de passe de moins de 10 caractères
  Then le bouton de validation reste désactivé
  And la règle non respectée est affichée avant que je soumette
```

**Notes** — Argon2id. Mesure de force de mot de passe par entropie (zxcvbn), pas par
règles de composition. Rate limit : 5 tentatives / IP / minute. Écrans concernés :
web + mobile. **Pas de TV** : l'inscription sur TV passe par US-05.

**Points : 5**

---

### US-02 — Se connecter par email
> **En tant qu'** utilisateur enregistré, **je veux** me connecter, **afin d'**accéder à mes sources.

```gherkin
Scenario: Connexion réussie
  When je saisis des identifiants valides
  Then je suis redirigé vers l'écran d'accueil
  And ma session persiste après redémarrage de l'application

Scenario: Identifiants invalides
  Then je vois un message générique ne révélant pas si l'email existe
  And après 5 échecs consécutifs, une temporisation progressive s'applique
```

**Points : 3**

---

### US-03 — Se connecter avec Google
> **En tant que** visiteur, **je veux** utiliser mon compte Google, **afin d'**éviter un
> mot de passe de plus.

```gherkin
Scenario: Premier SSO
  When je m'authentifie avec Google
  Then un compte est créé à partir de mon email Google
  And je suis connecté

Scenario: Email déjà enregistré en mot de passe
  Given un compte existe déjà avec cet email
  When je m'authentifie avec Google
  Then l'identité Google est rattachée au compte existant
  And aucun compte en double n'est créé
```

**Notes** — Credential Manager sur Android, `@react-oauth/google` ou équivalent sur web.
Le backend vérifie l'`id_token` (signature, `aud`, `iss`, `exp`) — il ne fait **jamais**
confiance à un email transmis par le client. Apple Sign-In : v2.

**Points : 5**

---

### US-04 — Session persistante et rotation de token
> **En tant qu'** utilisateur, **je veux** rester connecté, **afin de** ne pas ressaisir
> mes identifiants à chaque ouverture.

```gherkin
Scenario: Rafraîchissement transparent
  Given mon access token a expiré
  When j'effectue une action nécessitant une authentification
  Then le token est rafraîchi sans que je le remarque
  And l'ancien refresh token est révoqué

Scenario: Réutilisation d'un token révoqué
  When un refresh token déjà consommé est présenté
  Then toute la chaîne de tokens de cet appareil est révoquée
  And l'appareil doit se reconnecter
```

**Notes** — Un seul refresh en vol à la fois (mutex côté client) : sans ça, deux appels
parallèles au démarrage se révoquent mutuellement et déconnectent l'utilisateur. C'est
le bug classique de ce pattern.

**Points : 5**

---

### US-05 — Activer une TV depuis le téléphone
> **En tant qu'** utilisateur de l'application TV, **je veux** m'authentifier via mon
> téléphone, **afin de** ne pas taper mon mot de passe à la télécommande.

```gherkin
Scenario: Activation nominale
  Given l'application TV affiche un code à 8 caractères et son QR code
  When je saisis ce code sur lumo.tv/activate en étant connecté
  Then la TV se connecte automatiquement en moins de 10 secondes
  And le code ne peut plus être réutilisé

Scenario: Code expiré
  Given plus de 10 minutes se sont écoulées
  Then la TV affiche un nouveau code sans intervention de ma part

Scenario: Code inconnu
  When je saisis un code invalide sur le web
  Then je vois un message d'erreur explicite
  And après 5 tentatives, la saisie est temporisée
```

**Notes** — RFC 8628. Alphabet sans caractères ambigus (`0/O`, `1/I/L`). QR code pointant
vers `lumo.tv/activate?code=XXXX`. Polling à l'`interval` renvoyé par le serveur, avec
respect de `slow_down`.

**Points : 8**

---

## Épique B — Sources

### US-06 — Enregistrer une source Xtream Codes
> **En tant qu'** utilisateur, **je veux** enregistrer mes identifiants Xtream, **afin d'**accéder
> à mon catalogue.

```gherkin
Scenario: Source valide
  When je saisis host, username et password valides
  Then la source est validée en moins de 10 secondes
  And je vois le nombre de chaînes trouvées et la date d'expiration de mon compte
  And la source passe au statut READY

Scenario: Identifiants refusés
  Then je vois "Vos identifiants ont été refusés par le serveur" (code SOURCE_AUTH_FAILED)
  And le formulaire conserve le host pour que je corrige seulement ce qui est faux

Scenario: Serveur injoignable
  Then je vois un message distinguant clairement ce cas d'un refus d'identifiants
  And un bouton "Réessayer" est proposé
```

**Notes** — Mot de passe chiffré AES-256-GCM avant persistance, **jamais** renvoyé par
l'API. Ingestion asynchrone : `POST /sources` retourne `PENDING`, le client poll
`GET /sources/{id}`. Tolérance aux hosts saisis avec ou sans schéma, avec ou sans port,
avec ou sans slash final — normaliser côté serveur plutôt que de rejeter.

**Points : 8**

---

### US-07 — Enregistrer une playlist M3U
> **En tant qu'** utilisateur, **je veux** enregistrer une URL M3U, **afin d'**accéder à mes chaînes.

```gherkin
Scenario: URL valide
  When je saisis une URL M3U accessible
  Then les chaînes sont importées avec leurs groupes et logos
  And je vois le nombre de chaînes trouvées

Scenario: Contenu non conforme
  Given l'URL retourne du contenu qui n'est pas une playlist M3U
  Then je vois SOURCE_INVALID_FORMAT avec une explication actionnable

Scenario: Playlist vide
  Then je vois SOURCE_EMPTY et non un écran de succès sur une liste vide
```

**Notes** — Parsing en streaming, jamais en mémoire complète. Plafond de taille
(`SOURCE_TOO_LARGE`) et timeout explicites. Les chaînes sans `group-title` tombent dans
une catégorie « Non classé ». URL EPG optionnelle en champ séparé.

**Points : 8**

---

### US-08 — Consulter mes chaînes par catégorie
> **En tant qu'** utilisateur, **je veux** parcourir mes chaînes classées, **afin de**
> trouver ce que je cherche.

```gherkin
Scenario: Affichage
  Given ma source est READY
  Then je vois mes catégories et le nombre de chaînes de chacune
  And la liste se pagine de façon fluide au-delà de 500 chaînes

Scenario: Hors ligne
  Given j'ai déjà synchronisé et je n'ai plus de réseau
  Then je vois ma liste depuis le cache local
  And un indicateur discret signale le mode hors ligne
```

**Notes** — Room + Paging 3 côté Android. Sur TV : grille horizontale, focus visible,
navigation D-pad intégrale.

**Points : 5**

---

## Épique C — Lecture

### US-09 — Regarder une chaîne (mobile)
> **En tant qu'** utilisateur mobile, **je veux** lancer une chaîne, **afin de** la regarder.

```gherkin
Scenario: Lecture réussie
  When je sélectionne une chaîne
  Then l'image apparaît en moins de 5 secondes sur une connexion normale
  And je peux passer en plein écran et pivoter sans interrompre la lecture

Scenario: Flux indisponible
  Then je vois un message d'erreur clair et un bouton "Réessayer"
  And l'application ne plante pas et ne reste pas sur un écran noir muet

Scenario: Limite de connexions atteinte
  Then je vois un message expliquant que mon abonnement limite les flux simultanés
```

**Notes** — Media3 / ExoPlayer. La stream URL est obtenue à la demande via
`GET /channels/{id}/playback`. Gestion du focus audio, des appels entrants, de la
coupure réseau. Wake lock pendant la lecture.

**Points : 8**

---

### US-10 — Regarder une chaîne (Android TV)
> **En tant qu'** utilisateur TV, **je veux** lancer une chaîne à la télécommande.

```gherkin
Scenario: Lecture à la télécommande
  When je navigue avec le D-pad et valide sur une chaîne
  Then la lecture démarre en plein écran
  And l'élément qui a le focus est visuellement évident à chaque instant

Scenario: Contrôles
  When j'appuie sur OK pendant la lecture
  Then une barre d'informations apparaît avec le nom de la chaîne
  And elle disparaît après 5 secondes d'inactivité

Scenario: Retour
  When j'appuie sur BACK
  Then je reviens à la liste, positionné sur la chaîne que je regardais
```

**Notes** — Aucun élément interactif ne doit être inatteignable au D-pad. Marges
d'overscan de 5 %. Test obligatoire à la télécommande sur device réel, pas à la souris
sur émulateur.

**Points : 8**

---

## Récapitulatif

| Épique | Stories | Points |
|---|---|---|
| A — Authentification | US-01 → US-05 | 26 |
| B — Sources | US-06 → US-08 | 21 |
| C — Lecture | US-09, US-10 | 16 |
| **Total** | **10 stories** | **63** |

**Ordre de réalisation** (les dépendances comptent plus que les priorités) :
US-01 → US-02 → US-04 → US-06 → US-08 → US-09 → US-03 → US-07 → US-05 → US-10.

US-04 avant toute story consommant l'API : sans rotation de token fonctionnelle, tout
le reste sera à reprendre. US-05 et US-10 en fin de sprint : ce sont les seules qui
exigent un device TV physique.
