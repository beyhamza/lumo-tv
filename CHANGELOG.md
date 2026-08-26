# Journal des versions

Format inspiré de [Keep a Changelog](https://keepachangelog.com/fr/1.1.0/).
Versionnage sémantique : tant que la version majeure est `0`, rien n'est stable.

**Deux numéros à ne pas confondre.** La version de ce dépôt — applications, contrat,
paquets — est `0.1.0`. Le préfixe d'URL de l'API reste `/v1` : c'est la version
majeure de la surface HTTP, elle ne bouge que sur rupture, et elle n'a rien à voir
avec la maturité du produit.

---

## [0.1.0] — 2026-08-26

Première version numérotée. Elle marque un socle complet et une verticale **non
terminée** : le serveur tient le parcours de bout en bout, les clients ne l'exposent
pas encore.

### Ajouté

**Contrat** — `packages/contracts/openapi.yaml`, écrit à la main, source de vérité
unique. Trois clients générés et commités : interfaces Spring, client Kotlin
Retrofit, types TypeScript. Un workflow CI régénère et refuse toute divergence.

**lumo-api** — Comptes par email et par Google, sessions à rotation de token avec
détection de réutilisation, activation de téléviseur (RFC 8628), enregistrement de
sources M3U et Xtream avec ingestion asynchrone et sept codes d'erreur actionnables,
catalogue paginé, guide des programmes, URL de lecture délivrée à la demande.

**lumo.tv** — Pages marketing servies statiquement, guides, inscription, connexion,
réinitialisation et vérification d'email, activation d'un téléviseur, espace compte
en lecture seule.

**Android** — Deux applications échafaudées, avec leurs couches basses réelles et
testées : session chiffrée par le Keystore, rotation de token sous mutex, cache Room,
lecteur Media3, design system décliné mobile et TV, navigation.

**Documentation** — Architecture, modèle de domaine, six ADR, design system et
spécifications d'écran, backlog des sprints 1 et 2, recette et plan de démo.

**Outillage** — `docker-compose` (PostgreSQL + API), quatre workflows CI dont un qui
refuse toute chaîne, tout logo de bouquet et toute URL de flux réelle dans le dépôt.

### Sécurité

- Le mot de passe Xtream est chiffré en AES-256-GCM avant persistance et n'est
  renvoyé par aucune opération de l'API — **pas même à son propriétaire**.
- Les URL de flux et les tokens sont typés de sorte que les générateurs les masquent
  dans `toString()` : ils ne peuvent pas atteindre un log par accident, y compris en
  `DEBUG`.
- Les flux ne transitent jamais par notre infrastructure : le lecteur ouvre le serveur
  de l'utilisateur en direct.
- Les droits d'accès sont calculés côté serveur. Aucun client ne demande à un store
  s'il est premium.

### Ce que cette version ne fait pas

Écrit ici plutôt que laissé à découvrir.

- **Les deux applications Android sont des placeholders.** Chaque écran affiche un
  gabarit d'attente ; aucune des dix stories du sprint 1 n'est utilisable sur un
  téléphone ou un téléviseur. Le travail est décrit dans
  [`docs/backlog/sprint-02.md`](./docs/backlog/sprint-02.md).
- `/me`, `/me/entitlement`, `/me/devices`, les favoris et la progression existent dans
  le contrat et **n'ont aucun contrôleur**. L'API répond 404 avec le code générique
  `NOT_FOUND`, que le web affiche comme « écran en cours de construction ».
- Les quotas d'offre, l'essai et les sessions Stripe sont **dans le contrat
  seulement**. Aucun paiement n'est possible.
- Les écrans web dessinés au sprint de design — tarifs, FAQ, espace compte en
  écriture — ne sont pas construits. Voir
  [`docs/design/web-sprint-1.md`](./docs/design/web-sprint-1.md).
- VOD, séries et recherche : hors périmètre de la verticale.
- Hors périmètre v1 et inchangé : Chromecast, PiP, timeshift, enregistrement,
  multi-profils, contrôle parental, Stalker, application iOS, Play Billing.

### Note de conformité

Lumo ne fournit, n'héberge et ne revend aucun contenu. Les jeux de test utilisent
exclusivement des flux libres de droits.
