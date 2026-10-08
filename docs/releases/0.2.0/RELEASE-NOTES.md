# Notes de sortie — 0.2.0

Date de préparation : 8 octobre 2026. Statut : **pré-release `v0.2.0-rc.1`,
recette de sortie non jouée.** Ce document décrit ce qui est réellement livré et ce qui ne l'est pas ;
il ne remplace ni le [changelog](../../../CHANGELOG.md) ni la
[matrice de recette](acceptance.md).

## Identité de la candidate

| Champ | Valeur |
|---|---|
| Version | `0.2.0` |
| Base | `dev` = `main` @ `b4cb105` (preuve SR-12 entrée par la PR #31) |
| Préfixe d'API | `/v1` (inchangé) |
| Pré-release | tag annoté `v0.2.0-rc.1` sur `b4cb105` (08/10/2026), CI 4/4 vert ; **aucune publication** (pas de store, pas de déploiement) |
| Sortie finale | tag `v0.2.0` sur `release/0.2.0`, après la séance ciblée S8/S9 |

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

## Preuves

Les preuves de recette QA sont dans
[`qa-evidence/`](qa-evidence/INDEX.md). Points décisifs :

- S10-05 web + mobile (04/10) ; S10-05 TV et D-pad rejoué (07/10).
- SR-12 (retour de fiche TV) : preuve instrumentée `SearchTvReturnFocusTest`
  (6/6, 0 skip, `Television_1080p`) et rejoué QA indépendante, rouge → vert
  reproduit — `s10-05-03-sr12-2026-10-07/`.

## Recette partielle — limites connues et assumées

La recette de sortie S14 ([matrice R020-01 → R020-16](acceptance.md)) n'est
**pas jouée** ; toutes ses lignes sont à l'état `Non joué`. En particulier :

- **S9-07 intersurfaces** (matrice trois surfaces / deux sources) non jouée ;
  **recette appareil S8** non close. Seul S10 a une recette appareil complète.
- **`BUG-S10-05-02`** (onglet « Séries » affiché hors ligne) : cosmétique,
  parqué en `Backlog`.
- **Clavier Gboard ouvert** : DOWN est consommé par l'IME et n'atteint pas le
  champ de recherche TV. Hors du critère écrit (rejoué clavier fermé), aucun lot
  ouvert.
- **Web / GD-10 et GD-11** : décisions produit documentées (pas de grille
  précédente à conserver côté web ; pas de source active de compte). Aucun lot
  « cache client web » ouvert.
- **Recherche `q`** : insensibilité aux accents non promise ; insensibilité à la
  casse garantie en ASCII sur Room (SQLite) et selon la collation PostgreSQL.
- **SR-12 TV** : retour de fiche prouvé sur émulateur 1080p avec retour
  **modélisé** (`SaveableStateProvider`, pas un vrai `NavHost`) ; filtre non
  exercé côté TV (couvert par la recette mobile du 04/10).
- **CI** : `b4cb105` (= `dev` = `main`) est vert sur les quatre workflows ; la
  pré-release `v0.2.0-rc.1` est posée sur ce commit. `android.yml` ne joue pas les
  `androidTest` : ce vert ne prouve ni SR-12 ni le D-pad, couverts par les rejeux
  QA appareil, pas par le CI.

## Hors périmètre de la 0.2.0 (repris en 0.3.0)

S11 bibliothèque / liste à regarder (`US-022`), S12 reprise, S13 lecteur et
réglages, cascade finale `US-024`, fin `US-020` hors EPG. Google OAuth et les
paiements Stripe restent hors périmètre, leurs dettes tracées.

## Mécanique de sortie

1. `release/0.2.0` est coupée de `dev` (= `main` @ `b4cb105`) ; le contenu 0.2.0
   (preuve SR-12 + dossier QA) y est déjà, absorbé par la PR #31.
2. Versions `0.1.0 → 0.2.0` : `apps/web/package.json`,
   `packages/contracts/package.json`, `packages/contracts/openapi.yaml`
   (`info.version`), Android (`AndroidApplicationConventionPlugin.kt`
   `versionName` ; `versionCode` reste `1`, aucune publication store),
   `apps/api/build.gradle.kts` (`version`) et `apps/web/src/lib/auth/web-device.ts`
   (`app_version` — identité de device annoncée à l'API).
3. Tag annoté **`v0.2.0-rc.1`** sur `b4cb105` (pré-release, pas une publication).
   Il ouvre la séance ciblée S8/S9 sur `release/0.2.0`.
4. Les correctifs de recette vont sur `release/0.2.0`, puis `release/0.2.0 → main`
   après recette suffisante et CI vert ; tag final **`v0.2.0`** sur ce commit.
5. `main` ne reçoit **aucun** tag de version directement ; `main` est reportée sur
   `dev` après chaque sortie.
