# Sprint 11 — Bibliothèque et liste à regarder

Statut : **reprise le 15 octobre 2026** (15 → 21/10), version **0.3.0**, après la sortie de la 0.2.0 le 14/10. Le cycle, ouvert le 8 octobre, avait été mis en pause le 10/10 au profit du [sprint 10-bis](sprint-10-bis.md), clos avec la sortie. La 0.3.0 reprend aussi les limites connues de la 0.2.0 (lignes R020-02/04/05/13, preuves sur appareil de S10B-03/04) : voir les [notes de sortie](../releases/0.2.0/RELEASE-NOTES.md). Taille relative : L.
Référence : [plan et DoD commune](../roadmap/0.2.0/delivery-plan.md).

[Première proposition d’écrans](../design/0.2.0/library.md) préparée le
19 septembre 2026 pour web, mobile et TV ; présentation générale retenue.
Complément de cadrage : [ordre par source et retraits partiels](../design/0.2.0/favorite-organization-cases.md),
pour S11-00/03/05/06 ; règles produit Q3 validées, garanties à vérifier, recette non exécutée.
Suite : [À regarder — indisponibilité, hors ligne et conflits](../design/0.2.0/watchlist-states.md),
pour S11-00/01/04/05/06. La carte d’un contenu disparu est conservée avec
Indisponible et retrait manuel. Consultation seule hors ligne et dernière nouvelle
action acceptée par le serveur retenues ; garanties de C2 à définir.

## Cycle 11 — proposition d'ouverture (7 octobre 2026)

Objectif proposé à Hamza, en une phrase : **organiser ses chaînes et retrouver sur
un autre appareil ses films/séries enregistrés.**

Sélection `Todo` proposée au cycle Plane « Sprint 11 — Bibliothèque » : les lots
`S11-00` → `S11-06` et la story `US-022`. Ordre porté par le @Tech Lead :

1. `S11-00` (garanties Q3/Q8 : états, écritures concurrentes, contenus disparus,
   permutation filtrée) **avant tout code** ;
2. `S11-01` (contrat de la liste À regarder, serveur, migrations, génération des
   clients) **avant le branchement `S11-04`** — structurant : **ADR** si le protocole
   client/serveur bouge.

Reste rattaché à la story, hors découpe initiale : `fin US-020 hors EPG` et la
`cascade finale US-024` (dépendances de clôture, pas des lots de code de ce cycle).

Tranché le 08/10/2026 : la 0.2.0 est **gelée sur S8+S9+S10** (`v0.2.0-rc.1` posé) et
S11→S13 poursuivent en **0.3.0**. S11 est la prochaine ligne de code, mais `@Dev`
n'attaque qu'après `S11-00` (garanties Q3/Q8 **testables**) et l'ADR + approbation C2
de `S11-01`. Branche S11 depuis `dev`.
Détail : [`DECISIONS-PRODUIT.md`](DECISIONS-PRODUIT.md).

## Avancement du cycle

### Checklist — mise à jour du 14 octobre 2026 (reprise)

Une case cochée signifie **recetté**, pas seulement écrit.

- [ ] S11-00 — garanties Q3/Q8 — 80 % : référentiel normatif écrit, fixture multi-sources
  et tests G1, G2, G3, G5 fusionnés (PR #42) ; G6 → G8 (réessai ≠ nouvelle intention)
  ajoutées avec S11-01. Reste G4 (disparu, panne, hors ligne, suppression de source) sur
  appareil, qui attend les écrans de S11-04.
- [x] S11-01 — contrat À regarder, serveur, migration, clients — fusionné (PR #56), preuve
  automatisée = DoD : ADR 0011 **acceptée** par Hamza le 14/10 ; `/me/watchlist` au
  contrat, migration `0021`, `WatchlistService`, clients régénérés ; `WatchlistIntegrationTest`
  19/19, suite API 380/380.
- [ ] S11-02 — agrégation des favoris sans doublon — 90 % : l'agrégation US-020 existait déjà
  (accueil web et Android, vue « Tous » Android) ; la vue « Tous les favoris » de la page web
  Favoris la reprend désormais (PR #60). Reste la recette sur appareil.
- [ ] S11-03 — gestes Ajouter / Organiser, retraits, réordonnancement — 85 % :
  - **Web** fusionné (PR #60) : Organiser, retrait d'un groupe, « Retirer de tous mes
    favoris » avec confirmation sans JavaScript et réessai limité aux appartenances restantes,
    Monter / Descendre traduits en index du groupe complet (G1), « Ordre non entièrement
    appliqué » en cas d'interruption (G2), groupes réordonnables. Vitest 427/427.
  - **Android** (`feat/S11-03-android-favorites`) : mêmes règles, glisser-déposer et menu sur
    mobile, « Déplacer avant / après » et options par appui long OK sur TV, focus rendu à la
    carte ou à sa voisine. Tests 719/719, `FavoritesTvRemovalFocusTest` 2/2.
  - **API** : positions de favoris contiguës et déplacement à l'index exact (PR #59) ; même
    défaut corrigé pour les groupes (`fix/S11-03-group-positions`).
  - Reste : échec partiel réel en cours de séquence (tests seulement), hors ligne sur appareil,
    clavier TV pour renommer, FR sur appareil, glisser-déposer web (non fait, boutons
    accessibles à la place), « Retirer de tous mes favoris » depuis l'accueil (non proposé).
- [ ] S11-04 — écrans À regarder (trois clients), clé d'idempotence par intention — 90 %, en recette
  (web PR #57, Android PR #58) :
  - **Web** fusionné (PR #57) : `/app/watchlist`, filtres depuis `content_types`, carte
    `Indisponible` retirable sans fiche, bouton sur les fiches film et série ; clé créée au
    rendu du formulaire (une double soumission ne change rien), « Modification non
    confirmée » puis relecture. Vitest 382/382, build : pages marketing toujours statiques.
  - **Android** (`feat/S11-04-android-watchlist`) : `WatchlistRepository` sans cache
    persistant, `WatchlistIntentions` (même clé pour chaque réessai d'une intention),
    module `feature:watchlist`, section « Ma liste » de la Bibliothèque sur mobile, entrée
    « Ma liste » du rail TV, panneau compact pour une carte indisponible, bouton sur les
    fiches. Tests 676/676, lint et build verts, `WatchlistTvPanelFocusTest` 3/3 sur
    `Television_1080p` ; parcours contrôlés sur les deux émulateurs.
  - Reste : recette FR, retour réseau réel (relecture automatique), « charger plus » au-delà
    du banc ; rendu web des états indisponible et échec non revus à l'écran.
- [ ] S11-05 — propagation, erreurs partielles, suppression de source — 0 %
- [ ] S11-06 — recette entre appareils — 0 %

**Avancement global ≈ 62 %** (moyenne des sept lots).

### Point du 8 octobre 2026

Vérifié dans Plane (API) et dans le dépôt le 8 octobre 2026 en fin de journée :

- **Cycle ouvert dans Plane** (`Sprint 11 — Bibliothèque`, 8 → 14 octobre, version 0.3.0) ;
  `S11-00` → `S11-06` et `US-022` sont en `Todo` et **tous rattachés à `US-022`**
  (`parent=11f590a9…`), le trou structurel signalé par `@Dev` est fermé.
- **`S11-00` en `In Progress`**, porté par le `@Tech Lead` — garanties Q3/Q8 **normatives** :
  permutation filtrée = fonction pure `visibleSlot → fullIndex` ; échec intermédiaire = dernier
  move réussi conservé ; concurrence = dernière action **acceptée serveur** gagne ; disparu ≠
  panne, hors ligne sans file différée. **« Réessai ≠ nouvelle intention » (WL-10/11/12) n'est pas
  garanti par le contrat actuel → ADR dans `S11-01`.**
- **Fixture multi-sources** : `@Dev` la pose côté API, `@QA` l'étend (matrice d'isolation 2 comptes /
  jetons, disparition ≠ panne, comptage des écritures serveur). Non gatée.
- **Base des branches S11** : `dev`. `S11-04` n'attaque pas avant l'ADR (approbation C2).
- **CI** : PR #32 mergée — les 4 workflows (contract/api/android/web) se déclenchent désormais
  sur un push `dev` ; plus de « dev zéro-vérifié ».
- **Hors cycle — 0.2.0** : `v0.2.0-rc.1` (tag annoté, pré-release sur `b4cb105`), `release/0.2.0` →
  `main` (#33) puis report `main` → `dev` (#34). Verrous de sortie encore ouverts : `R020-01`
  (fuite entre comptes) et `R020-16` (migration).

## Objectif

Organiser ses chaînes et retrouver sur un autre appareil ses films/séries enregistrés.
Stories : US-022, fin US-020 hors EPG, cascade finale US-024. Dépend de S8 ; C2, Q3/Q8
à arbitrer. S9 apporte l'EPG nécessaire à la clôture complète US-020.

## Tâches proposées

| ID | Travail | Surface/dépendance |
|---|---|---|
| S11-00 | Préciser états, écritures concurrentes et contenus disparus ; vérifier la permutation filtrée et ses échecs avec le contrat unitaire selon Q3 validé | Toutes, garanties Q3 et Q8 |
| S11-01 | Faire approuver le contrat de liste À regarder, puis serveur, migrations et génération des clients | C2 ; avant branchement |
| S11-02 | Réutiliser les groupes/favoris ; agréger sans doublon selon première occurrence et ordre partagé | Android/web |
| S11-03 | Gestes Ajouter/Organiser, retrait local/global confirmé, suppression de groupe, réordonnancement accessible | Trois clients |
| S11-04 | Liste films/séries, filtres, ordre par ajout, bouton Dans ma liste et fiche | Trois clients ; après S11-01 |
| S11-05 | Propagation partagée, erreurs partielles et suppression de source y compris liste, progression et caches | API/clients |
| S11-06 | Recette entre appareils ; vérifier favoris de source distincte et absence d'effet de la lecture sur la liste | Toutes |

## Démo et sortie

Créer deux groupes, placer une chaîne dans les deux : une seule carte agrégée,
ordre actualisé après déplacement. Retirer une appartenance puis toutes avec
confirmation. Supprimer un groupe sans perdre les favoris. Ajouter film et série
sur téléphone, les retrouver sur web/TV ; la lecture ne retire rien. Supprimer une
source de test et conserver les données de l'autre.
Dans un groupe A1, B1, A2, déplacer A2 avant A1 : obtenir A2, B1, A1.
Faire réussir deux retraits sur trois : conserver les réussites, réessayer le
reste et garder la carte agrégée tant qu’une appartenance subsiste. Voir FO-01 à FO-12.
Faire disparaître un film ou une série du catalogue après actualisation : garder
sa carte À regarder marquée Indisponible et permettre son retrait sans fiche.
Ne pas confondre disparition d’un contenu, erreur réseau et suppression de source.

Tests : identité/dédoublonnage, ordre, limites/isolation de compte, répétition des
écritures, suppression et erreurs partielles. Fixtures neutres multi-sources et
élément disparu. Checks contrat/API/Android/web. Clôture US-022 et compléments cités.
