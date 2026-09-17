# US-018 — Parcourir une source à la fois

Statut : besoin et remplacement après suppression validés. Version cible : 0.2.0.
Surfaces : web, Android mobile, Android TV.

## Besoin

Planification proposée : S8.
Voir le [plan 0.2.0](../../roadmap/0.2.0/delivery-plan.md) ; réalisation non commencée.

En tant qu'utilisateur ayant plusieurs sources, je veux choisir celle que je
parcours, afin de garder un catalogue et un accueil compréhensibles.

## Critères d'acceptation

- Le nom de la source active est visible près du sélecteur.
- Avec une seule source, son nom est affiché simplement.
- Changer de source actualise accueil, recherche, Direct, Films et Séries.
- Continuer et Favoris ne montrent que les éléments de la source active.
- Revenir à une source conserve ses favoris et progressions.
- Le choix est mémorisé sur l'appareil et retrouvé à la prochaine ouverture.
- Changer de source sur un appareil ne change pas le choix d'un autre appareil.
- Si la source active est supprimée : sélectionner l'unique source restante,
  proposer un choix s'il en reste plusieurs, ou revenir à Ajouter une source
  s'il n'en reste aucune.
- Appliquer aussi cette règle lorsqu'un appareil constate une suppression effectuée
  ailleurs. Une simple indisponibilité réseau ne prouve pas une suppression.
- Le choix de source reste accessible au clavier et à la télécommande, avec des
  libellés FR/EN.

## API et dépendances

Le contrat expose les sources et les catalogues par source. La progression accepte
`sourceId` ; les favoris portent `source_id`, utilisable pour le filtrage client.
La persistance du choix local sera confrontée aux mécanismes existants avant
implémentation ; aucune nouvelle stratégie de stockage n'est décidée ici.

## Avant planification

Vérifier le périmètre par compte du choix mémorisé et la détection de suppression
sur un autre appareil. Tester le changement avec des sources de banc et deux
appareils, puis les cas zéro, une et plusieurs sources restantes, sans contenu réel.

Référence : [décisions produit](../../roadmap/0.2.0/decisions.md).
