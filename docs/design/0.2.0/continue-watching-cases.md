# Continuer — cas de référence avant réalisation

Date : 19 septembre 2026. Statut : préparation produit et recette, non exécutée.
Références : [US-019](../../backlog/stories/US-019-continue-watching.md),
[écrans](resume-player.md), [enchaînement](episode-continuation.md),
[arbitrages Q1/Q2 et C3](../../roadmap/0.2.0/open-questions.md).

## Trois notions distinctes

- **Progression** : position où reprendre, conservée dès le début.
- **Présence dans Continuer** : éligibilité et éventuel retrait demandé par l’utilisateur.
- **Fin du lecteur** : événement de lecture qui autorise le décompte du suivant.

Une avance dans le média change la progression sans prouver du temps regardé.
Dépasser 95 % change la proposition de reprise sans interrompre la vidéo.
Retirer une carte ne change aucune position sauvegardée.

## Cas issus des règles déjà validées

Les identifiants CW servent aux tests et aux rapports de S12 ; ils ne créent pas
de nouvelles stories. Durées et contenus sont des exemples fictifs, sans flux.

| ID | Situation | Résultat attendu |
|---|---|---|
| CW-01 | Première lecture, 10 secondes réellement lues | Progression conservée ; pas encore de carte Continuer |
| CW-02 | Première lecture, 29 secondes réellement lues | Toujours pas de carte |
| CW-03 | Première lecture, seuil de 30 secondes réellement lues atteint | Carte éligible ; une carte par série, dans la source active |
| CW-04 | 5 secondes lues puis avance à 20 minutes | La position permet la reprise, mais l’avance ne compte pas comme 20 minutes regardées |
| CW-05 | Pause ou mise en mémoire tampon | Ne pas les compter comme lecture effective |
| CW-06 | Film de 100 minutes, position exactement à 95 minutes | Pas encore terminé au sens du seuil strictement supérieur à 95 % |
| CW-07 | Même film, position au-delà de 95 minutes | Quitte Continuer ; la fiche propose Revoir ; la lecture en cours continue |
| CW-08 | Épisode au-delà de 95 %, mais pas à sa fin réelle | Peut proposer le suivant pour la reprise ; aucun décompte dans le lecteur |
| CW-09 | Durée inconnue, lecture éligible | Conserver la reprise, permettre le retrait manuel ; ne pas inventer un pourcentage |
| CW-10 | Retirer une carte éligible | Masquage partagé, progression conservée ; la fiche permet de retrouver la position |
| CW-11 | Recommencer un épisode depuis sa fiche | Lecture depuis le début de cet épisode ; progressions des autres épisodes conservées |
| CW-12 | Suivant déjà commencé, non terminé | Reprise à sa position avec message Reprise à… |
| CW-13 | Fin réellement atteinte avec un suivant, autoplay actif | Décompte 10 s TV / 5 s mobile-web ; interaction avec les commandes l’annule |
| CW-14 | Fin réellement atteinte avec un suivant, autoplay inactif | Proposition sans décompte ; lancement seulement par Lire maintenant |
| CW-15 | Dernier épisode disponible terminé | Série retirée de Continuer ; à la fin réelle, retour à sa fiche sans décompte |
| CW-16 | Changer la source active | Ne montrer que les cartes de cette source ; conserver les progressions des autres |

CW-01 à CW-05 supposent un contenu neuf suffisamment long, sans masque préalable
ni proposition du suivant. Les exceptions et interactions sont isolées ci-dessous.

## Compléments validés le 19 septembre 2026

| ID | Situation | Décision utilisateur |
|---|---|---|
| CW-17 | 20 s sur un appareil puis 10 s sur un autre, même film ou épisode | Les secondes effectivement lues s’additionnent entre sessions et appareils ; la carte devient éligible à 30 s cumulées |
| CW-18 | Carte retirée, puis lecture relancée depuis la fiche | Réapparition dès le démarrage réel, sans attendre 30 nouvelles secondes |
| CW-19 | Épisode terminé, suivant disponible mais jamais commencé | Garder la série dans Continuer et proposer directement ce suivant ; pas de seuil de lecture préalable pour lui |

Le cumul de CW-17 est par film ou épisode, pas à l’échelle de toute une série.
CW-19 est une exception explicite au seuil d’apparition du suivant : ne pas
fabriquer une progression déjà lue pour afficher sa carte. Les critères de fin
restent applicables ; CW-18 ne décide pas des priorités pour un contenu déjà terminé.

## Arbitrages Q1/Q2 restants

| ID | Situation | Décision à fixer |
|---|---|---|
| CW-20 | Contenu de moins de 30 s terminé avant le seuil d’apparition | Priorité de la règle de fin et éventuelle exception à l’apparition |
| CW-21 | Épisode suivant déjà terminé | Revoir ce suivant ou chercher un épisode non terminé ; distinguer enchaînement et reprise depuis l’accueil |
| CW-22 | Recommencer un contenu éligible ou retiré | Effet sur l’éligibilité acquise, le masque et le calcul de lecture effective |
| CW-23 | Deux appareils lisent simultanément le même contenu | Définir le cumul temporel et la position retenue, sans additionner implicitement des doublons |

Le masquage concurrent avec une lecture, les écritures retardées et le retour
d’un appareil hors ligne doivent être spécifiés avec C3. Une nouvelle position
sauvegardée n’est pas, à elle seule, la preuve d’une nouvelle session de lecture.
Une tentative de lancement échouée n’est pas un démarrage réel.

## États d’échec — propositions à relire

Ces cas complètent la présentation générale ; ils ne sont pas encore validés.

| ID | Situation | Proposition de présentation |
|---|---|---|
| CW-24 | Échec de chargement de Continuer | Conserver les cartes déjà disponibles et proposer Réessayer ; ne pas présenter l’échec comme une absence d’historique |
| CW-25 | Échec du lancement depuis une carte | Garder la carte et sa progression ; message avec Réessayer et Retour |
| CW-26 | Retrait refusé ou non confirmé par le serveur | Garder ou rétablir la carte et expliquer que le retrait n’a pas abouti ; ne pas annoncer de succès partagé |
| CW-27 | Épisode suivant indisponible au lancement | Arrêter l’enchaînement automatique ; proposer Retour à la série, sans chercher silencieusement un autre épisode |
| CW-28 | Contenu introuvable après actualisation | Indiquer l’indisponibilité, permettre le retour au catalogue ; aucune suppression de progression déduite d’une simple erreur réseau |

Le traitement d’une suppression confirmée de source suit US-024 ; il ne se
confond pas avec l’indisponibilité temporaire d’un contenu ou d’un serveur.
Les mécanismes de cache et de détection ne sont pas décidés par ce document.

## Entrées attendues pour C3

Le contrat existant porte position et durée, filtrées par source/type/référence.
Il ne porte pas le masque partagé ni la mesure de lecture effective. Les décisions
CW-17 à CW-19 sont acquises. Avant tout schéma, fermer CW-20 à CW-23 et les règles
de concurrence/hors ligne nécessaires au lot.

Le lot contractuel devra ensuite expliciter et faire approuver la lecture des
cartes masquées, le retrait, la réapparition, l’éligibilité et la reprise des
écritures après reconnexion. Prévoir l’idempotence des réessais et les données
déjà présentes avant la mise à jour, sans déduire leur temps regardé de leur position.
Cette liste décrit les garanties à couvrir ; elle ne définit aucun endpoint,
champ, migration, nouvelle stratégie de cache ou architecture.

La recette utilisera deux appareils, deux comptes et deux sources autorisées.
Elle distinguera résultat visible et données conservées. Les cas encore ouverts
restent non exécutables avec une attente définitive jusqu’à leur arbitrage.
