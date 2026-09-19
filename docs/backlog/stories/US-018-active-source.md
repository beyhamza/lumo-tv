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
- Avec plusieurs sources, le sélecteur affiche leur nom, leur état et une coche
  pour la source active. Le changement s’applique immédiatement, sans confirmation.
- Changer de source actualise accueil, recherche, Direct, Films et Séries.
- Conserver la rubrique ouverte lors du changement : depuis Films, afficher les
  films de la nouvelle source. Réinitialiser les filtres propres à l’ancienne source.
- Depuis une fiche de contenu, revenir au catalogue correspondant de la nouvelle
  source ; ne pas conserver une fiche appartenant à l’ancienne source.
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

## Règles de réalisation — arrêtées le 19 septembre 2026 (S8-03)

Les points laissés à vérifier ci-dessous sont tranchés ainsi, à l'identique sur
les trois surfaces. Aucune donnée nouvelle côté serveur : le choix ne quitte
jamais l'appareil.

| Situation, après un `GET /sources` **réussi** | Résultat |
|---|---|
| Aucune source | Parcours Ajouter une source |
| Le choix mémorisé existe encore | Cette source |
| Pas de choix valide, une seule source | Cette source, sans question |
| Pas de choix valide, plusieurs sources | Demander ; ne jamais en choisir une en silence |

- **Portée** : par appareil **et par compte**. Android : DataStore de préférences,
  clé par identifiant de compte. Web : cookie httpOnly du navigateur, propre au
  compte, écrit uniquement par une action serveur. Un second compte sur le même
  appareil a son propre choix.
- **Une panne ne prouve rien** : un `GET /sources` en échec conserve le choix. Seuls
  une liste réussie où la source manque, ou un `404 SOURCE_NOT_FOUND` qui la nomme,
  prouvent une suppression ([C4, P6](../../roadmap/0.2.0/c4-previous-catalogue.md)).
- **Après ajout** (US-024) : la première source devient active sur l'appareil ;
  une source supplémentaire ne prend pas la sélection.
- **Au changement** : la rubrique ouverte est conservée, les filtres de l'ancienne
  source sont remis à zéro, une fiche de l'ancienne source ramène au catalogue
  correspondant de la nouvelle.
- La vérification toutes les 60 secondes pendant une lecture (C4, D5) est réalisée
  avec les lecteurs, en S8-05.

## Avant planification

Vérifier le périmètre par compte du choix mémorisé et la détection de suppression
sur un autre appareil. Tester le changement avec des sources de banc et deux
appareils, puis les cas zéro, une et plusieurs sources restantes, sans contenu réel.

Référence : [décisions produit](../../roadmap/0.2.0/decisions.md).
