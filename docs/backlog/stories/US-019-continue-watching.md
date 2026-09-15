# US-019 — Reprendre un film ou une série depuis l'accueil

Statut : besoin validé ; retrait de la rangée en attente d'arbitrage de persistance
et de couverture contractuelle. Version cible : 0.2.0.
Surfaces : web, Android mobile, Android TV.

## Besoin

En tant qu'utilisateur, je veux retrouver mes lectures commencées sur l'accueil,
afin de les reprendre rapidement.

## Critères d'acceptation validés

- Continuer présente les films et épisodes commencés dans la source active.
- Une série occupe une seule carte, indiquant l'épisode à reprendre et sa progression.
- L'action principale reprend immédiatement la lecture à la progression conservée.
- Voir la fiche ouvre les détails du film ou permet de choisir un épisode.
- Retirer de Continuer masque la carte sans effacer sa progression.
- La section disparaît lorsqu'elle ne contient aucun élément.
- Les actions sont accessibles sur les trois surfaces, y compris au D-pad, en FR/EN.

## Couverture API et travail existant

`GET /me/progress` et les résolutions de films et épisodes existent. Les sprints
5 et 6 décrivent la reprise entre appareils et le regroupement par série : partir
de cette implémentation, puis vérifier les écarts avec la cible.

Le contrat actuel ne porte aucun état de masquage de Continuer. Une progression
ne doit pas être remise à zéro pour simuler un retrait. Aucun endpoint n'est
inventé ici. Conformément à `AGENTS.md` §3, l'implémentation de ce retrait attend
l'arbitrage utilisateur et, si nécessaire, un lot contractuel explicite.

## Questions avant implémentation

- Le masquage est-il propre à l'appareil ou partagé sur le compte ?
- Une nouvelle lecture fait-elle réapparaître la carte ?
- Faut-il une restauration manuelle ?
- Quelles règles existantes déterminent une lecture commencée ou terminée et
  l'épisode à reprendre ? Les confirmer avant de fixer les cas de recette.

La recette vérifiera notamment que retirer une carte conserve la position retrouvée
depuis la fiche. Les erreurs de résolution et les contenus supprimés restent à spécifier.

Référence : [décisions produit](../../roadmap/0.2.0/decisions.md).
