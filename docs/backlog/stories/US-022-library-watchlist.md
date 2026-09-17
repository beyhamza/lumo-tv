# US-022 — Retrouver ma bibliothèque et ma liste à regarder

Statut : besoin validé le 17 septembre 2026 ; liste à regarder en attente de
couverture contractuelle. Version cible : 0.2.0.
Surfaces : web, Android mobile, Android TV, même priorité.

## Besoin

En tant qu'utilisateur, je veux retrouver mes chaînes favorites et les films ou
séries que j'ai enregistrés, afin de choisir facilement quoi regarder plus tard.

## Critères d'acceptation validés

- Ma bibliothèque présente deux sections : Chaînes favorites et À regarder.
- Les deux sections affichent uniquement les éléments de la source active.
- Chaînes favorites donne accès à toutes les chaînes favorites et à leurs groupes
  personnalisés. Réutiliser US-12 et le travail des sprints 4 et 6.
- Créer, renommer, supprimer et réordonner les groupes personnalisés ; ajouter,
  retirer et réordonner les chaînes dans un groupe.
- Une chaîne peut appartenir à plusieurs groupes. Tous les favoris et l'accueil
  affichent chaque chaîne une seule fois : ordre des groupes puis des chaînes,
  première occurrence retenue, selon US-020.
- Les groupes sont partagés entre appareils et leur contenu affiché est filtré
  sur la source active.
- Mobile et web : glisser-déposer avec commandes accessibles de réordonnancement.
  TV : commandes Déplacer avant / après accessibles à la télécommande.
- À regarder contient les films et séries enregistrés, avec filtres Tous, Films,
  Séries.
- Un bouton Ajouter à ma liste est disponible sur les fiches de films et séries.
- Une série est enregistrée dans son ensemble, pas épisode par épisode.
- La liste est partagée entre les appareils du compte.
- Les ajouts les plus récents apparaissent en premier.
- Sélectionner une carte ouvre la fiche du film ou de la série.
- Le retrait est manuel. Commencer ou terminer une lecture ne retire pas l'élément.
- Continuer reste alimenté par les lectures ; À regarder reste une sélection
  volontaire. Les deux fonctions ont des comportements distincts.
- Les libellés sont disponibles en FR/EN et les actions accessibles au clavier,
  au tactile ou au D-pad selon la surface.

## Couverture API et dépendances

Le contrat actuel expose des favoris de chaînes et leurs groupes. `Favorite`
référence une chaîne ; il ne représente ni un film ni une série. Aucun mécanisme
de liste à regarder partagée n'est couvert actuellement.

Le besoin produit est validé. Sa réalisation attend un lot contractuel explicite,
conformément à `AGENTS.md` §3 ; aucun endpoint ni modèle de stockage n'est inventé
ici. Ne pas détourner les favoris de chaînes ou la progression pour stocker cette
liste. Vérifier le contrat avant implémentation et régénérer les clients après
toute évolution approuvée.

Le réordonnancement des groupes et de leurs favoris est couvert par le contrat.
Les groupes appartiennent au compte, pas à une source. Le contrat prévoit qu'une
suppression de groupe conserve ses favoris en les transférant au groupe par défaut,
qui n'est lui-même pas supprimable. Préciser la présentation de ces règles lors
du cadrage des actions, sans modifier leur sémantique implicitement.

## Avant planification

- Spécifier les états vide, chargement, erreur et hors ligne.
- Préciser l'état du bouton après ajout, la suppression et les retours d'action.
- Définir le comportement d'un élément retiré du catalogue après synchronisation.
- Définir les modifications concurrentes et la propagation aux appareils hors ligne.
- Préciser les filtres lorsque la source ne propose qu'un seul type de contenu.
- Préciser les actions de retrait d'une chaîne depuis un groupe et depuis la vue
  agrégée, ainsi que le message de suppression d'un groupe.
- Préciser le déplacement dans une liste filtrée par source, lorsque des favoris
  d'autres sources occupent des positions intermédiaires dans le groupe partagé.

## Recette à préparer

Sur deux appareils et deux sources de banc, vérifier ajout d'un film et d'une
série, filtrage par source et par type, ordre des ajouts, ouverture des fiches et
retrait partagé. Vérifier que lecture et fin de lecture ne retirent pas l'élément.
Confirmer que la série apparaît comme une seule entrée. Prévoir les trois surfaces,
une session TV à la télécommande et les libellés FR/EN.

Référence : [décisions produit](../../roadmap/0.2.0/decisions.md).
