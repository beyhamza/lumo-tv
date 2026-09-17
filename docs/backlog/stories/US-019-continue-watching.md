# US-019 — Reprendre un film ou une série depuis l'accueil

Statut : besoin validé, complété le 17 septembre 2026 ; retrait de la rangée en
attente de couverture contractuelle. Version cible : 0.2.0.
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
- Le retrait s'applique à tous les appareils du compte.
- Relancer le contenu fait réapparaître sa carte dans Continuer.
- La section disparaît lorsqu'elle ne contient aucun élément.
- Les actions sont accessibles sur les trois surfaces, y compris au D-pad, en FR/EN.

## Parcours depuis les fiches — complément validé le 17 septembre 2026

- Film jamais commencé : Regarder.
- Film commencé : Reprendre avec progression, et action secondaire Recommencer.
- Film terminé : Revoir lance depuis le début.
- Série avec épisode en cours : Reprendre indique la saison et l'épisode.
- Si l'épisode précédent est terminé : proposer Regarder l'épisode suivant,
  lorsqu'un suivant est disponible.
- Chaque épisode reste sélectionnable ; un épisode commencé propose Reprendre
  ou Recommencer cet épisode.
- Recommencer un épisode conserve les progressions des autres épisodes.
- L'accueil Continuer conserve sa reprise immédiate, sans question intermédiaire.

Ce complément précise la transition entre l'accueil et les fiches des sprints
5 et 6.

## Apparition et fin de lecture — règles validées

- Un contenu apparaît dans Continuer après 30 secondes de lecture effective.
  Sa progression reste conservée dès le début, même avant son apparition.
- Au-delà de 95 % de sa durée connue, un film quitte Continuer et sa fiche propose Revoir.
- Au-delà de 95 %, un épisode est considéré comme terminé pour la reprise : la
  série propose le suivant s'il existe. Après le dernier épisode disponible terminé,
  la série quitte Continuer.
- Sans durée connue, conserver la reprise ; la carte peut être retirée manuellement.
- Le seuil de 95 % ne coupe pas la lecture et ne déclenche pas le suivant.
  Le décompte d'enchaînement commence uniquement à la fin réellement atteinte par le lecteur.

Les 30 secondes mesurent une lecture effective, pas la position obtenue en avançant
dans le média. La règle de progression et celle de visibilité du rail sont distinctes.

## Couverture contractuelle

`GET /me/progress` et les résolutions de films et épisodes existent. Les sprints
5 et 6 décrivent la reprise entre appareils et le regroupement par série : partir
de cette implémentation, puis vérifier les écarts avec la cible.

Le contrat actuel ne porte aucun état de masquage de Continuer. Une progression
ne doit pas être remise à zéro pour simuler un retrait. Aucun endpoint n'est
inventé ici. Le masquage partagé et le retour après une nouvelle lecture ont été
validés le 17 septembre 2026. Conformément à `AGENTS.md` §3, l'implémentation de ce
retrait attend un lot contractuel explicite.

Le seuil de fin au-delà de 95 % figure déjà dans le contrat. En revanche, la position
et la durée seules ne prouvent pas 30 secondes de lecture effective. Auditer la
mesure et le partage de cette éligibilité entre appareils avant implémentation ;
si une donnée contractuelle manque, l'inclure dans un lot explicite sans inventer
d'endpoint dans cette story.

## Questions avant implémentation

- Faut-il une restauration manuelle ?
- Comment propager le retrait aux appareils temporairement hors ligne et traiter
  une lecture concurrente ?
- Les 30 secondes s'accumulent-elles entre sessions et appareils ? Préciser leur
  articulation avec la réapparition après retrait et avec Recommencer.
- Confirmer le comportement des contenus très courts et de l'épisode suivant
  proposé sans avoir encore été lu, au regard du seuil d'apparition.

La recette vérifiera sur deux appareils que retirer une carte la masque sur les
deux, conserve la position retrouvée depuis la fiche, puis qu'une nouvelle lecture
la fait réapparaître. Les erreurs de résolution et les contenus supprimés restent
à spécifier.
Tester les limites à 30 secondes et à 95 % (la fin est strictement au-delà), une
avance manuelle sans 30 secondes lues, une durée inconnue et la poursuite de lecture
au-delà du seuil sans déclenchement prématuré de l'épisode suivant.

Référence : [décisions produit](../../roadmap/0.2.0/decisions.md).
