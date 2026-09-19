# Ma bibliothèque — proposition d’écrans 0.2.0

Date : 19 septembre 2026. Statut : présentation générale retenue en conversation ; développement non commencé.

Références : [US-022](../../backlog/stories/US-022-library-watchlist.md),
[sprint 11](../../backlog/sprint-11.md), [décisions](../../roadmap/0.2.0/decisions.md).

## Organisation proposée

Deux onglets : Chaînes favorites et À regarder. Le nom de la source active reste
visible. Web/TV : groupes à gauche et chaînes à droite, en cohérence avec la
disposition du Direct demandée. Mobile : groupes au-dessus de la liste.
À regarder présente une grille de films et séries, avec filtres Tous, Films, Séries.

| Écran | Contenu et interactions illustrées | Tâche |
|---|---|---|
| S11-E01 Favoris | Tous les favoris sans doublon, sélection d’un groupe, lecture directe | S11-02 |
| S11-E02 Organisation | Appartenances multiples, retrait d’un groupe, déplacement avant/après | S11-03 |
| S11-E03 Gestion des groupes | Créer, renommer, réordonner ; suppression confirmée avec transfert vers le groupe par défaut | S11-03 |
| S11-E04 Retrait global | Confirmation pour retirer une chaîne de tous les groupes | S11-03 |
| S11-E05 À regarder | Films/séries, ajouts récents en premier, accès à la fiche | S11-04 |
| S11-E06 Fiche simplifiée | Lire/Reprendre et Dans ma liste ; retrait indépendant de la progression | S11-04 |
| S11-E07 Vide / erreur | Accompagnement vers le catalogue ; erreur avec données conservées et réessai | S11-00/05 |

## Comportements déjà validés repris dans la proposition

- Une même chaîne peut appartenir à plusieurs groupes ; l’agrégation conserve
  sa première occurrence selon l’ordre des groupes et des chaînes.
- Ajouter un favori utilise le groupe par défaut. Organiser permet de modifier
  ses appartenances. Le retrait depuis un groupe ne touche qu’à cette appartenance.
- Supprimer un groupe personnalisé transfère ses chaînes vers le groupe par défaut.
  Ce dernier reste renommable et non supprimable.
- À regarder contient des séries entières ; lire ne retire pas la carte.
  Le bouton Dans ma liste permet un retrait manuel sans perte de progression.

## Portée de la maquette

Données fictives et modifications en mémoire, sans API, flux vidéo ou source réelle.
Changer la surface permet d’observer le même jeu de données ; cela ne constitue
pas une synchronisation entre appareils. Les choix de présentation peuvent être
retenus par la conversation, mais les mutations simulées ne sont pas sauvegardées.

Les exemples du catalogue et les fiches servent uniquement à tester l’ajout,
le retrait et le retour. Ils ne spécifient pas les écrans complets Films/Séries.
L’état vide est un scénario de démonstration ; ajouter un exemple initialise une
bibliothèque vide dans ce scénario.

Le réordonnancement est manipulable avec Avant/Après. Le glisser-déposer mobile/web,
déjà prévu dans US-022, n’est pas simulé ici et reste à réaliser. Les boutons TV
illustrent les commandes accessibles sans présumer de la carte finale de focus.
Organiser demande au moins un groupe ; le retrait global utilise sa confirmation dédiée.
Cette présentation du dialogue reste une proposition visuelle.

Le scénario d’erreur montre des données déjà disponibles et un réessai réussi
simulé. Les erreurs d’écriture partielles, conflits entre appareils, contenus
disparus et politique hors ligne ne sont pas tranchés par ce prototype : Q3/Q8
restent ouverts. Il ne présente qu’une source ; le déplacement dans un groupe
contenant plusieurs sources reste à préciser. C2 reste requis avant réalisation
de la liste À regarder ; aucun endpoint n’est défini par ces écrans.

## Vérifications

Parcours contrôlés : dédoublonnage, retrait local puis global confirmé,
appartenances multiples, déplacement dans un groupe, création/renommage,
suppression avec conservation des chaînes et protection du groupe par défaut.
À regarder : filtres, lecture sans retrait, retrait/réajout en tête de liste.

États avec contenu, vide et erreur contrôlés aux largeurs 320, 390, 736 et 1024 px,
sur web, mobile et TV, sans débordement horizontal ni erreur JavaScript observée.
Captures web et mobile relues. Une recette télécommande réelle, le partage entre
comptes/appareils, FR/EN et l’accessibilité complète restent à effectuer. Le rendu
TV dans la conversation n’est pas une validation physique à trois mètres.
