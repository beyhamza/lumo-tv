# Sprint 11 — Bibliothèque et liste à regarder

Statut : proposé, non commencé. Taille relative : L.
Référence : [plan et DoD commune](../roadmap/0.2.0/delivery-plan.md).

[Première proposition d’écrans](../design/0.2.0/library.md) préparée le
19 septembre 2026 pour web, mobile et TV ; présentation générale retenue.
Complément de cadrage : [ordre par source et retraits partiels](../design/0.2.0/favorite-organization-cases.md),
pour S11-00/03/05/06 ; règles produit Q3 validées, garanties à vérifier, recette non exécutée.
Suite : [À regarder — indisponibilité, hors ligne et conflits](../design/0.2.0/watchlist-states.md),
pour S11-00/01/04/05/06. La carte d’un contenu disparu est conservée avec
Indisponible et retrait manuel ; les autres arbitrages Q8 restent à compléter.

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
