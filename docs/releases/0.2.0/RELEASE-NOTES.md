# Notes de sortie — 0.2.0

Date de sortie : 14 octobre 2026. Statut : **version `v0.2.0`, sortie sur recette
partielle assumée** (décision de Hamza du 14/10/2026). Aucune publication : ni store,
ni déploiement. Ce document décrit ce qui est réellement livré et ce qui ne l'est pas ;
il ne remplace ni le [changelog](../../../CHANGELOG.md) ni la
[matrice de recette](acceptance.md).

## Identité de la candidate

| Champ | Valeur |
|---|---|
| Version | `0.2.0` |
| Base | `release/0.2.0` = `dev` @ `f4908be` + ces notes ; `v0.2.0-rc.1` était sur `b4cb105` |
| Préfixe d'API | `/v1` (inchangé) |
| Pré-release | `v0.2.0-rc.1` (08/10/2026) ; pas de `rc.2` : aucun testeur externe à qui la donner |
| Sortie | tag annoté `v0.2.0` sur la fusion `release/0.2.0 → main` ; **aucune publication** |

## Périmètre livré

Trois surfaces (web, Android mobile, Android TV) :

- **S8 — accueil, navigation, sources** : source active par appareil, accueil
  (Continuer / Favoris / Direct), réglages et gestion des sources.
- **S9 — Direct et Guide** : grille horaire TV/web, liste mobile, fiche programme
  et états (vide, aucun résultat, erreur, chargement), `GET /sources/{id}/epg`
  groupé (lot C1) et fiche d'import EPG par source.
- **S10 — recherche unifiée** : une saisie retrouve chaînes, films et séries de
  la source active, filtres par type, fiches et retour avec position/focus
  conservés, états vide / sans résultat / erreur partielle / hors ligne, sur les
  trois surfaces.

Détail par sprint : [`sprint-08.md`](../../backlog/sprint-08.md),
[`sprint-09.md`](../../backlog/sprint-09.md),
[`sprint-10.md`](../../backlog/sprint-10.md).


**Version gratuite, sans plafond.** Le plan FREE n'a aucune limite de sources ni
d'appareils, comme la 0.1.0 : la mise à jour ne retire rien aux comptes existants
(`R020-15`, `BUG-R020-15-01`, décision de Hamza du 14/10/2026). Un déploiement peut en
fixer par configuration (`LUMO_PLAN_FREE_MAX_SOURCES`, `LUMO_PLAN_FREE_MAX_DEVICES`) ;
aucun ne l'est par défaut. Pas de paiement ni de connexion Google : les routes
correspondantes de l'API répondent `503` « non configuré ».

## Preuves

Les preuves de recette QA sont dans
[`qa-evidence/`](qa-evidence/INDEX.md). Points décisifs :

- S10-05 web + mobile (04/10) ; S10-05 TV et D-pad rejoué (07/10).
- SR-12 (retour de fiche TV) : preuve instrumentée `SearchTvReturnFocusTest`
  (6/6, 0 skip, `Television_1080p`) et rejoué QA indépendante, rouge → vert
  reproduit — `s10-05-03-sr12-2026-10-07/`.

## Recette — ce qui est joué

[Matrice R020-01 → R020-16](acceptance.md). Dans le périmètre 0.2.0 :

| Ligne | État |
|---|---|
| R020-01 isolation entre comptes (verrou) | ✅ conforme, 10/10, trois surfaces |
| R020-16 mise à jour depuis la 0.1.0 (verrou) | ✅ conforme, 11/10 ; deux écarts catalogue Android corrigés et rejoués |
| R020-03 sources | ✅ conforme, 14/10, trois surfaces ; trois écarts corrigés |
| R020-14 aucun contenu réel ni secret | ✅ conforme, 14/10 ; deux écarts corrigés |
| R020-15 pas de Google ni paiement exposé, quotas inchangés | ✅ conforme, 14/10 ; FREE sans plafond |
| R020-02, 04, 05, 13 | ⚠️ partiel — limites connues ci-dessous |

Le sprint 10-bis (sécurité et qualité) est fusionné en entier : SSRF par redirection,
`X-Forwarded-For` et plafond de connexion par email, pause en arrière-plan, pile réseau
du lecteur, session web concurrente, tests de migration Room.

## Limites connues et assumées

Sortir avec ces limites est une décision, pas un oubli. Elles sont reportées en 0.3.0.

**Recette non close**

- **R020-02** (source active, accueil, navigation, retour/focus) : fragments seulement
  (accueil à froid, focus de grille), pas le parcours vertical S8 sur appareil.
- **R020-04** (Direct et Guide) : joué sur le web et sur émulateur (S9-07 automatisé) ;
  la matrice intersurfaces trois surfaces / deux sources et le résidu matériel ne le sont pas.
- **R020-05** (recherche) : joué sur les trois surfaces ; saisie rapide (SR-01/02/03) et
  « deux sources » non joués.
- **R020-13** (FR/EN, focus TV, lisibilité) : FR/EN web et grilles ; D-pad sur une vraie
  télécommande et troncature à 320 px non joués.
- **S10B-03 / S10B-04** : pause en arrière-plan et redirections du lecteur couvertes par
  des tests unitaires, **pas prouvées à la télécommande ni sur appareil**.
  `MediaSession` reportée : les touches média dédiées de la télécommande ne pilotent pas
  le lecteur.
- **S10B-05** : l'e2e « jeton d'accès expiré → Lecture » n'est pas écrit ; la
  déduplication des rafraîchissements web vaut pour une instance.

**Comportements connus**

- **Déconnexion : le jeton d'accès reste valable jusqu'à son expiration**, 15 minutes
  au plus. La chaîne de refresh de l'appareil est révoquée immédiatement ; un jeton
  d'accès déjà émis (JWT sans état) reste accepté jusqu'à son échéance. Limite
  **acceptée** par Hamza le 10/10/2026 ; la changer demanderait un ADR.
- **`BUG-R020-03-02`** : `POST /sources` ignore `auto_sync` à la création (faible,
  `Backlog`) ; on le change ensuite dans les réglages de la source.
- **`BUG-S10-05-02`** (onglet « Séries » affiché hors ligne) : cosmétique, `Backlog`.
- **Clavier Gboard ouvert** : DOWN est consommé par l'IME et n'atteint pas le champ de
  recherche TV.
- **Web / GD-10 et GD-11** : décisions produit documentées (pas de grille précédente à
  conserver côté web ; pas de source active de compte).
- **Recherche `q`** : insensibilité aux accents non promise ; insensibilité à la casse
  garantie en ASCII sur Room (SQLite) et selon la collation PostgreSQL.
- **Banc** : l'échec « identifiants refusés » d'une source Xtream ne se produit pas sur le
  banc (le chemin `/xtream-401/` est retiré par la normalisation de l'hôte) ; il a été
  forcé en base pour R020-03.

**Avant toute publication** (rien de tout cela ne bloque une sortie non publiée)

- `versionCode` Android vaut toujours `1`, comme en 0.1.0 : un store refuserait la mise à
  jour. À monter avant la première publication.
- `POSTGRES_PASSWORD` a une valeur par défaut dans la pile Docker : un déploiement doit la
  fixer.
- Le BFF web ne transmet pas l'IP du navigateur à l'API : les limites par IP de
  l'inscription et du mot de passe oublié valent pour tout le web. À trancher avec la
  topologie de production (`LUMO_TRUSTED_PROXIES`).
- **CI** : `android.yml` ne joue pas les `androidTest` (focus TV, migrations Room) ; ils
  sont prouvés par les rejeux QA sur émulateur, pas par le CI.

## Hors périmètre de la 0.2.0 (repris en 0.3.0)

S11 bibliothèque / liste à regarder (`US-022`), S12 reprise, S13 lecteur et
réglages, cascade finale `US-024`, fin `US-020` hors EPG. Google OAuth et les
paiements Stripe restent hors périmètre, leurs dettes tracées.

## Mécanique de sortie

1. Versions `0.2.0` posées pour `v0.2.0-rc.1` (web, contrat, API, Android
   `versionName`, `app_version` du device web) ; inchangées depuis.
2. Les correctifs de recette sont arrivés sur `dev` (PR #35 → #53) ; `release/0.2.0`
   est avancée à `dev` @ `f4908be`, plus ces notes.
3. `release/0.2.0 → main`, CI vert, puis tag annoté **`v0.2.0`** sur ce commit.
4. `main` est reportée sur `dev`. La 0.3.0 repart de `dev` avec S11.
