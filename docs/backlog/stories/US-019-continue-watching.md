# US-019 — Reprendre un film ou une série depuis l'accueil

Statut : besoin validé, complété le 17 septembre 2026 ; retrait de la rangée en
attente de couverture contractuelle. Version cible : 0.2.0.
Surfaces : web, Android mobile, Android TV.

## Besoin

Proposition de présentation : [écrans Reprise et lecteur](../../design/0.2.0/resume-player.md),
S12-E01 à E03, présentation générale retenue. Les
[cas de référence](../../design/0.2.0/continue-watching-cases.md) précisent la
préparation des seuils, de la réapparition et de la recette. Q2 est tranché côté
produit ; les règles principales de Q1 sont acquises. Les garanties d’ordre,
de réessai et de propagation hors ligne restent à définir dans C3.

Planification proposée : S12 ; rails existants réutilisés en S8, sans clôture anticipée.
Voir le [plan 0.2.0](../../roadmap/0.2.0/delivery-plan.md) ; réalisation non commencée.

En tant qu'utilisateur, je veux retrouver mes lectures commencées sur l'accueil,
afin de les reprendre rapidement.

## Critères d'acceptation validés

- Continuer présente les films et épisodes commencés dans la source active.
- Une série occupe une seule carte, indiquant l'épisode à reprendre et sa progression.
- L'action principale reprend immédiatement la lecture à la progression conservée.
- Voir la fiche ouvre les détails du film ou permet de choisir un épisode.
- Retirer de Continuer masque la carte sans effacer sa progression.
- Le retrait s'applique à tous les appareils du compte.
- Si une lecture est déjà en cours sur un autre appareil au moment du retrait,
  ses sauvegardes ne réaffichent pas la carte. Il faut un nouveau démarrage réel
  après le retrait ; les sauvegardes de progression restent possibles.
- Si deux appareils ont des positions différentes, la prochaine reprise utilise
  la lecture la plus récente, même moins avancée, plutôt que le maximum des positions.
- Relancer le contenu fait réapparaître sa carte dans Continuer.
- Complément du 19 septembre 2026 : cette réapparition a lieu dès le démarrage
  réel de la lecture, sans attendre 30 nouvelles secondes ; une tentative échouée
  ne suffit pas.
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
- Complément du 19 septembre 2026 : Recommencer repart à zéro et conserve
  l’éligibilité acquise de la carte. Elle est retrouvée dès le démarrage réel,
  sans attendre 30 nouvelles secondes, y compris après retrait.
- Si l’épisode suivant proposé est déjà terminé, le relire depuis le début pour
  conserver l’ordre des épisodes ; ne pas sauter à un épisode non terminé.
- L'accueil Continuer conserve sa reprise immédiate, sans question intermédiaire.

Ce complément précise la transition entre l'accueil et les fiches des sprints
5 et 6.

## Apparition et fin de lecture — règles validées

- Un contenu apparaît dans Continuer après 30 secondes de lecture effective.
  Sa progression reste conservée dès le début, même avant son apparition.
- Complément du 19 septembre 2026 : ces 30 secondes se cumulent entre sessions
  et appareils, pour le même film ou épisode. Les secondes regardées simultanément
  sur plusieurs appareils ne comptent qu’une fois (décision du 19 septembre).
- Au-delà de 95 % de sa durée connue, un film quitte Continuer et sa fiche propose Revoir.
- Au-delà de 95 %, un épisode est considéré comme terminé pour la reprise : la
  série propose le suivant s'il existe. Après le dernier épisode disponible terminé,
  la série quitte Continuer.
- Complément du 19 septembre 2026 : si le suivant n’a jamais été commencé,
  conserver la série dans Continuer et proposer directement ce suivant, sans
  attendre 30 secondes lues sur celui-ci et sans inventer de progression.
- Sans durée connue, conserver la reprise ; la carte peut être retirée manuellement.
- Contenu de moins de 30 secondes : aucune exception au seuil d’apparition.
  La progression est sauvegardée, mais le contenu terminé n’apparaît pas dans
  Continuer. Une série peut néanmoins proposer le suivant selon la règle ci-dessus.
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
- Comment propager le retrait aux appareils temporairement hors ligne et ordonner
  les événements retardés ? La lecture antérieure au retrait ne réaffiche pas la carte.
- Définir l’identification de la lecture la plus récente, les réessais et les
  écritures retardées dans C3. Le temps simultané ne compte qu’une fois ; la position
  la plus avancée n’est pas un critère de priorité. Départager les événements dont
  l’ordre ne peut être établi sans supposer que les horloges clientes sont fiables.

La recette vérifiera sur deux appareils que retirer une carte la masque sur les
deux, conserve la position retrouvée depuis la fiche, puis qu'une nouvelle lecture
la fait réapparaître. Les erreurs de résolution et les contenus supprimés restent
à spécifier.
Tester les limites à 30 secondes et à 95 % (la fin est strictement au-delà), une
avance manuelle sans 30 secondes lues, une durée inconnue et la poursuite de lecture
au-delà du seuil sans déclenchement prématuré de l'épisode suivant.
Ajouter les cas 20 s puis 10 s sur deux appareils, relance après retrait sans
attente supplémentaire et proposition d’un suivant non commencé (CW-17 à CW-19).
Ajouter un film de 20 s terminé sans apparition, un suivant déjà terminé relu
depuis zéro sans saut d’épisode, et Recommencer sur une carte visible puis retirée,
avec retour immédiat au démarrage réel et progressions des autres épisodes intactes
(CW-20 à CW-22). Recette non exécutée.
Vérifier deux appareils lisant pendant les mêmes 20 s : cumul de 20 s, sans carte.
Vérifier aussi un chevauchement partiel : lecture A de t=0 à 20 s et lecture B
de t=10 à 30 s donnent 30 s cumulées, pas 40 s (CW-29).
Vérifier 40 min puis une lecture plus récente à 12 min : reprise à 12 min (CW-30).
Retirer une carte pendant une lecture sur un autre appareil : elle reste masquée
malgré ses sauvegardes, puis revient après un nouveau démarrage postérieur au retrait
(CW-31). Ces cas préparent la recette ; ils ne sont pas exécutés.

Référence : [décisions produit](../../roadmap/0.2.0/decisions.md).
