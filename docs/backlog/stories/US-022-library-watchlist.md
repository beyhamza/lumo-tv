# US-022 — Retrouver ma bibliothèque et ma liste à regarder

Statut : besoin validé le 17 septembre 2026 ; liste à regarder en attente de
couverture contractuelle. Version cible : 0.2.0.
Surfaces : web, Android mobile, Android TV, même priorité.

[Proposition visuelle](../../design/0.2.0/library.md) préparée le 19 septembre
2026 ; les états et interactions simulés ne remplacent pas les arbitrages C2/Q3/Q8.

## Besoin

Planification proposée : S11.
Voir le [plan 0.2.0](../../roadmap/0.2.0/delivery-plan.md) ; réalisation non commencée.

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

### Ajout et retrait

- Ajouter une chaîne aux favoris l'ajoute immédiatement au groupe par défaut ;
  Organiser permet ensuite de choisir ses groupes.
- Retirer depuis un groupe supprime uniquement cette appartenance.
- Depuis Tous les favoris ou l'accueil, Retirer de tous mes favoris demande une
  confirmation puis retire la chaîne de tous ses groupes.
- Supprimer un groupe personnalisé conserve ses chaînes favorites et les transfère
  au groupe par défaut ; la confirmation explique cette conséquence.
- Le groupe par défaut est renommable, mais non supprimable.
- Après ajout d'un film ou d'une série, Ajouter à ma liste devient Dans ma liste.
  Sélectionner à nouveau le bouton retire l'élément sans toucher à sa progression.

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
qui n'est lui-même pas supprimable. Les gestes validés ci-dessus reprennent ces règles.
Le retrait de tous les groupes correspond à plusieurs appartenances : vérifier
la gestion des échecs partiels avec les opérations existantes avant implémentation.

## Ordre par source et retrait partiel — validés le 19 septembre 2026

- Réordonner les chaînes de la source affichée modifie uniquement les places
  occupées par cette source dans le groupe complet. Les chaînes des autres
  sources conservent leur place : A1, B1, A2 devient A2, B1, A1.
- Lors d’un retrait global partiellement réussi, conserver les retraits réussis
  et proposer de réessayer les appartenances restantes. Ne pas rétablir celles
  déjà retirées ni annoncer un retrait global réussi.
- Tant qu’une appartenance subsiste, la chaîne reste favorite et figure dans
  l’agrégation, selon sa première occurrence restante définie dans US-020.

Voir les [cas FO-01 à FO-12](../../design/0.2.0/favorite-organization-cases.md).
Le déplacement unitaire du contrat décale les positions dans le groupe complet ;
vérifier la réalisation de la permutation filtrée et ses échecs intermédiaires.
La validation produit ne promet pas une mutation atomique ni une nouvelle API.

## Avant planification

### À regarder : contenu indisponible — décision du 19 septembre 2026

Un film ou une série disparu du catalogue après actualisation conserve sa carte
dans À regarder, marquée Indisponible, avec retrait manuel. Une erreur réseau
ne suffit pas à prouver cette disparition. La suppression confirmée de la source
continue d’appliquer la cascade prévue dans US-024 ; elle ne laisse pas ces cartes.

Voir [Q8 : états de la liste et cas WL-01 à WL-14](../../design/0.2.0/watchlist-states.md).
Le retrait d’un élément indisponible doit rester possible sans dépendre du
chargement de sa fiche ; cette couverture fait partie de C2 à définir.

### Préparation restante

- Spécifier les états vide, chargement, erreur et hors ligne.
- Préciser les retours d’action en cours, résultat inconnu et réessai, en appliquant
  la conservation des retraits réussis désormais validée.
- Définir les données affichables d’un élément indisponible et son éventuel
  retour au catalogue ; la conservation de sa carte et le retrait manuel sont validés.
- Définir les modifications concurrentes et la propagation aux appareils hors ligne.
- Préciser les filtres lorsque la source ne propose qu'un seul type de contenu.
- Vérifier la permutation dans une liste filtrée avec les opérations existantes,
  les échecs intermédiaires et la concurrence, sans déplacer les autres sources
  dans le résultat final. Soumettre toute évolution contractuelle nécessaire.

## Recette à préparer

Sur deux appareils et deux sources de banc, vérifier ajout d'un film et d'une
série, filtrage par source et par type, ordre des ajouts, ouverture des fiches et
retrait partagé. Vérifier que lecture et fin de lecture ne retirent pas l'élément.
Confirmer que la série apparaît comme une seule entrée. Prévoir les trois surfaces,
une session TV à la télécommande et les libellés FR/EN.
Vérifier aussi l'ajout au groupe par défaut, Organiser, le retrait d'un seul groupe,
le retrait confirmé de tous les groupes et la suppression d'un groupe sans perte
de favoris. Vérifier le bouton Dans ma liste et la conservation de la progression.
Ajouter les permutations avec sources entrelacées et vérifier les positions des
autres sources. Simuler deux retraits réussis sur trois : seule l’appartenance
restante est à réessayer, la chaîne reste agrégée jusqu’au retrait complet.
Pour À regarder, ajouter la disparition confirmée d’un film et d’une série,
le retrait sans fiche, l’erreur réseau qui ne prouve pas une disparition et la
suppression de source. Les cas WL restent un plan de recette non exécuté.

Référence : [décisions produit](../../roadmap/0.2.0/decisions.md).
