# Web — écrans du sprint 1

Maquette : `Lumo - Web Sprint 1.dc.html`, quatre artboards larges de 1280 px.
Correspond à la passe 2 du prompt d'initialisation
(`docs/prompts/01-init-claude-design.md`), écrans 9 à 12.

Rappel du découpage en zones (`docs/architecture.md` §4, `apps/web/AGENTS.md` §2) —
il conditionne ce que chaque écran a le droit de faire :

| Écran | Route | Zone | Rendu |
|---|---|---|---|
| W1 Landing | `/[locale]` | Marketing | SSG + ISR — **aucun composant client, aucun cookie, aucun appel authentifié** |
| W2 Gabarit de guide | `/[locale]/guides/[slug]` | Marketing | SSG + ISR |
| W3 Espace compte | `/[locale]/app/*` | Compte | SSR authentifié |
| W4 Activation | `/[locale]/activate` | Activation | SSR minimal, `noindex` |

---

## W1 — Landing `lumo.tv`

Thème **clair marketing**. Un seul `<h1>`. Objectif : LCP < 2 s, donc zéro
hydratation.

### Structure

1. **Barre de navigation** — logotype, `Fonctionnalités` · `Tarifs` · `Guides`,
   puis `Se connecter` et le CTA plein `Essayer gratuitement`.
2. **Hero** — surtitre en dégradé `LECTEUR IPTV — ANDROID, TV & WEB`, titre
   52 px, sous-titre 18 px en graisse 300, deux CTA (plein + contour), et la
   mention qui n'est pas décorative : « Aucun contenu fourni. Vous apportez
   votre source, on s'occupe du reste. » À droite, une capture produit dans un
   châssis sombre.
3. **Trois étapes** — « Trois minutes, montre en main » : créer un compte,
   coller sa source, regarder.
4. **Tarifs** — deux cartes. `Gratuit` à 0 € (1 source, 2 appareils, toutes les
   chaînes, les trois applications) et `Plus` à 3,99 €/mois, carte sombre,
   badge `RECOMMANDÉ` en dégradé (sources et appareils illimités, EPG enrichi,
   reprise de lecture multi-écrans, assistance prioritaire), CTA
   `Essayer 14 jours`.
5. **FAQ** — quatre entrées, la première dépliée. Un accordéon **sans
   JavaScript** : `<details>` / `<summary>`, pas de `useState`.
6. **Pied de page** — sombre, logotype, `Guides` · `Tarifs` · `Confidentialité`
   · `Conditions` · `Contact`.

### Contraintes

- **Le châssis produit du hero est un placeholder hachuré.** La capture réelle
  devra être prise sur un jeu de test libre de droits (AGENTS.md §1). Une
  capture d'écran contenant un vrai bouquet est une violation au même titre
  qu'une fixture.
- La FAQ répond « Non » à « Lumo fournit-il des chaînes ? ». Ce texte est de la
  conformité, pas du marketing : il ne se réécrit pas sans relecture.
- Les liens sont des `<a href={hrefFor(locale, "…")}>`, jamais le `Link` de
  next-intl (composant client → bundle sur toutes les pages prérendues).

### Delta avec la landing livrée

La landing actuelle a un hero, trois arguments, trois étapes et un teaser de
guides. Elle **n'a ni grille de tarifs ni FAQ**, et le namespace `Landing` de
`fr.json` / `en.json` ne contient aucune clé correspondante. Ce sont deux
sections à créer, avec leurs libellés dans les deux langues.

Le footer livré expose `Mentions légales` et `Confidentialité` ; la maquette
demande en plus `Tarifs`, `Conditions` et `Contact`. Les routes légales
n'existent pas encore sous `(marketing)`.

### Ce que la section Tarifs implique côté API

Les prix eux-mêmes n'ont **pas** à venir de l'API : la zone marketing est
statique et ne peut pas appeler un endpoint authentifié. Ils vivent dans les
messages i18n.

Les **quotas**, eux, sont des droits d'accès, et un droit d'accès se calcule
côté serveur (AGENTS.md §1.3). Voir [`api-gaps.md`](./api-gaps.md) G1 et G2.

---

## W2 — Gabarit d'article de guide

Thème clair. C'est le moteur SEO : la lisibilité prime sur tout le reste.

### Structure

- Barre de navigation allégée : logotype, `Guides`, CTA `Essayer Lumo`.
- **Sommaire collant** à gauche (240 px), entrée courante marquée par une barre
  violette de 2 px et non par la seule couleur.
- **Colonne de lecture limitée à 68 ch**, 17 px, graisse 300, interlignage 1,7.
- Fil d'Ariane, `<h1>` équilibré (`text-wrap: balance`), méta « Mis à jour en
  … · 4 min de lecture ».
- Blocs disponibles : paragraphe, `<h2>`, bloc de code monospace (l'exemple
  d'URL M3U), **callout violet** (l'avertissement « le lien contient parfois
  votre mot de passe »), image annotée.
- Navigation précédent / suivant en bas.

### Contraintes

- Un `<h1>` par page, hiérarchie de titres continue, `aria-label` sur chaque
  `<nav>` — y compris le sommaire.
- `generateMetadata` → `pageMetadata(...)` : canonical + `hreflang` +
  `x-default` d'un bloc (`apps/web/AGENTS.md` §6).
- La date passe par `getFormatter`, jamais `toLocaleDateString` — sinon rendu
  serveur et rendu client divergent et l'hydratation casse.
- L'URL d'exemple du bloc de code est un domaine d'exemple, jamais un vrai
  panel.
- « 4 min de lecture » et le sommaire se dérivent du contenu au build.

### Modèle de contenu

Aujourd'hui `apps/web/src/content/guides.ts`, trois entrées statiques. Le
gabarit ajoute quatre besoins que ce module ne couvre pas : sommaire, date de
mise à jour, temps de lecture, chaînage précédent / suivant. **Aucun de ces
besoins n'est un besoin d'API** — c'est du contenu, il reste au build.

---

## W3 — Espace compte

Thème **sombre applicatif**. SSR authentifié. C'est le seul des quatre écrans
qui lit l'API avec un token.

### Structure

- **Rail latéral** 250 px : logotype, `Sources` (actif) · `Appareils` ·
  `Abonnement`, et en bas la carte utilisateur — pastille à dégradé portant
  l'initiale, nom d'affichage, email tronqué.
- **En-tête de section** : titre, sous-titre explicatif, CTA clair
  `Ajouter une source`.
- **Liste des sources** — une ligne par source :
  - badge de type monospace (`m3u` cyan / `xtr` violet),
  - libellé,
  - ligne de métadonnées `1 248 chaînes · vérifiée il y a 2 h`,
  - pastille d'état,
  - menu `⋯`.
- **Ligne en erreur** — contour `danger`, métadonnée remplacée par la cause en
  clair (« Identifiants refusés depuis hier — le mot de passe a peut-être
  changé »), pastille `Erreur`, et un bouton `Corriger` **à la place** du menu
  `⋯`. La sortie d'erreur est un bouton, pas une entrée de menu.
- **Invite d'ajout** — carte en pointillés, ton non culpabilisant
  (« Une deuxième résidence, un autre abonnement ? »).
- **Aperçu des appareils** — panneau de 380 px, trois lignes, lien
  `Gérer les appareils →`.

### États

| État | Rendu |
|---|---|
| Vide (aucune source) | La carte en pointillés occupe toute la largeur ; le CTA d'en-tête reste le chemin principal |
| `PENDING` / `SYNCING` | Pastille neutre, métadonnées remplacées par une progression indéterminée ; on **poll** `GET /sources/{id}` |
| `READY` | Pastille cyan, `channel_count` + `last_synced_at` |
| `ERROR` | Ligne en erreur ci-dessus, message dérivé de `error_code` |
| API injoignable | Bandeau `unavailableTitle`, déjà présent dans les messages |

### Correspondance maquette → contrat

| Élément | Opération / champ |
|---|---|
| Carte utilisateur | `GET /me` → `display_name`, `email` |
| Badge `m3u` / `xtr` | `Source.kind` (`M3U_URL` / `XTREAM`) |
| Libellé | `Source.label` |
| `1 248 chaînes` | `Source.channel_count` |
| `vérifiée il y a 2 h` | `Source.last_synced_at` |
| Pastille d'état | `Source.status` |
| Cause de l'erreur | `Source.error_code` (`IngestionErrorCode`) |
| `depuis hier` | **manquant** — `api-gaps.md` G5 |
| `Corriger` | `PATCH /sources/{id}` |
| `Ajouter une source` | `POST /sources` (202 `PENDING`) puis polling |
| Menu `⋯` | `POST /sources/{id}/sync`, `DELETE /sources/{id}` |
| Lignes d'appareils | `GET /me/devices` → `name`, `platform`, `last_seen_at` |
| `ce poste` | **manquant** — `api-gaps.md` G6 |
| Onglet `Abonnement` | `GET /me/entitlement` |
| Quotas de l'offre | **manquant** — `api-gaps.md` G1 |

### Décisions de rendu à ne pas transformer en champs d'API

La maquette affiche trois libellés distincts dans le panneau appareils :
`actif`, `il y a 3 j`, `en ligne`. Ce sont **deux** informations, pas trois :

- `en ligne` = « c'est l'appareil qui regarde cette page » → `is_current` (G6) ;
- `actif` vs `il y a 3 j` = un seuil de rendu sur `last_seen_at`. Retenu :
  **moins de 24 h → `actif`**, au-delà → date relative.

Aucun champ `is_online` : rien ne notifie une déconnexion, un tel champ serait
faux la moitié du temps.

### Notes de sécurité

Le mot de passe Xtream n'apparaît nulle part, et l'écran d'édition ne le
pré-remplit pas : `Source` n'a pas de propriété `password` et n'en aura jamais.
`host` et `username` sont renvoyés précisément pour que l'utilisateur ne retape
que ce qui est faux.

---

## W4 — `lumo.tv/activate`

Thème sombre, largeur 420 px, trois états sur la même page. Contexte d'usage
explicite dans la maquette : **debout, téléphone en main, face à sa télé**. Tout
en découle — gros caractères, un seul champ, feedback immédiat.

### État 1 — saisie

Logotype, titre « Entrez le code affiché sur votre télé », cases de code en
Space Mono 30 px, groupées **4 + 4 avec un tiret automatique**, la case active
soulignée en `accent-cyan`. Aide sous le champ : « Huit caractères. Le tiret se
place tout seul, et la casse n'a aucune importance. »

Huit cases dans une carte de 420 px imposent un dimensionnement serré :
cases de 40 × 60 px, gouttières de 5 px, marges latérales de la carte ramenées
à 20 px. Le corps de 30 px reste largement au-dessus de ce qu'exige une lecture
à bout de bras — c'est cet écran-ci qu'on tient en main, pas celui de la télé.

### État 2 — succès

Pastille à dégradé, coche, « Télé associée », puis : « "TV du salon" est
maintenant reliée à votre compte. Vos chaînes s'y chargent — regardez l'écran. »
Bouton secondaire `Gérer mes appareils`.

Deux intentions à préserver : **le nom de l'appareil est cité**, et le regard
est explicitement renvoyé vers la télévision. Le nom exige une donnée que
`POST /auth/device/approve` ne renvoie pas — `api-gaps.md` G4.

### État 3 — erreur (code expiré)

Les cases repassent en `danger`, le code saisi reste lisible (l'utilisateur voit
ce qu'il a tapé), titre `Code expiré`, explication, et un CTA plein
`Saisir le nouveau code`. La sortie est une action, pas un lien discret.

Les autres erreurs du contrat réutilisent ce gabarit :

| Réponse | `code` | Message |
|---|---|---|
| 404 | `DEVICE_CODE_NOT_FOUND` | Code inconnu — vérifiez la saisie |
| 409 | `DEVICE_CODE_ALREADY_USED` | Code déjà utilisé — la télé en affiche un nouveau |
| 410 | `DEVICE_CODE_EXPIRED` | Code expiré (état 3) |
| 429 | `RATE_LIMITED` | Trop de tentatives — patientez |

### Contraintes

- **Zéro composant client**, comme aujourd'hui : formulaire → Server Action →
  redirection avec paramètre de statut. La page fonctionne sans JavaScript, sur
  une mauvaise connexion. Le formatage groupé est donc du CSS et de
  l'`inputMode`, pas un contrôleur de saisie.
- `noindex` + `robots.ts` bloque `/activate` : l'URL porte un code à usage
  unique.
- Arrivée non connectée : rediriger vers `login?next=…` en **conservant le
  `code`**, et valider `next` (garde anti-redirection ouverte, `nextPath`).
- Le QR affiché par la télé pointe vers `verification_uri_complete`, donc
  `/activate?code=…` : le chemin nominal ne comporte aucune saisie.

---

## Divergences maquette ↔ contrat — **résolues**

Les deux divergences ci-dessous ont été **tranchées en faveur du contrat**, et
la maquette a été corrigée en conséquence dans le projet Claude Design. Elles
restent documentées : la question se reposera au prochain coup d'œil sur un
ancien export, et la réponse est ici.

### D1 — Longueur du code d'activation : 6 vs 8 → **8, en 4 + 4**

La maquette montrait **6 caractères** en 3 + 3, dans l'état de saisie comme dans
l'état d'erreur. Le contrat impose 8 : `user_code` a `minLength: 8`,
`maxLength: 8`, `pattern ^[…]{8}$`, et l'exemple de `verification_uri_complete`
porte un code de 8 caractères. `ApproveDeviceRequest.user_code` a lui aussi
`minLength: 8` : une saisie de 6 caractères aurait été **rejetée à la
validation**, avant d'atteindre la moindre logique serveur. L'implémentation
livrée annonçait d'ailleurs déjà « Huit caractères » (`Activate.codeHint`).

L'enjeu n'est pas cosmétique. Avec l'alphabet non ambigu du contrat
(31 caractères : 23 lettres sans `I`/`L`/`O`, 8 chiffres sans `0`/`1`) :

| Longueur | Espace de codes |
|---|---|
| 6 | ≈ 8,9 × 10⁸ |
| 8 | ≈ 8,5 × 10¹¹ |

Un facteur d'environ mille. **Décision : la maquette s'aligne sur le contrat,
en 4 + 4.** Le tiret automatique et les grandes cases, qui étaient le vrai
apport de la maquette, tiennent aussi bien en 4 + 4 ; la force de
l'autorisation, elle, ne se renégocie pas pour deux cases.

### D2 — Durée de vie du code : 5 min vs 10 min → **10 minutes**

L'état d'erreur affichait « Les codes ne durent que 5 minutes ». Le contrat dit
10 minutes (`user_code` : *valid 10 minutes*, `expires_in` : 600), US-05 aussi,
et `Activate.codeHint` aussi. **La maquette était seule contre trois** : c'est
elle qui a bougé.

Accessoirement, `/activate` ne reçoit jamais `expires_in` — il n'est renvoyé
qu'à la télévision par `POST /auth/device/code`. La durée reste donc une
constante produit, écrite dans les messages FR et EN. Ce n'est pas un manque de
contrat : ajouter un endpoint pour lire une constante coûterait plus qu'il ne
rapporte.

### D3 — Vocabulaire d'état d'une source

La maquette écrit `Active`, le contrat dit `READY`, les messages livrés disent
`Prête`. Purement rédactionnel, aucun changement de contrat. **Retenu :
`Prête` / `Ready`**, déjà traduit dans les deux langues.
