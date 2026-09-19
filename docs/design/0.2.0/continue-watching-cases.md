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
| CW-20 | Contenu de moins de 30 s terminé avant le seuil d’apparition | Aucune exception au seuil ; progression sauvegardée, contenu terminé absent de Continuer |
| CW-21 | Épisode suivant déjà terminé | Le proposer et le relire depuis le début, sans sauter à un épisode non terminé ; conserver l’ordre des épisodes |
| CW-22 | Recommencer un contenu éligible ou retiré | Repartir à zéro et retrouver la carte dès le démarrage réel, sans attendre 30 nouvelles secondes ; conserver les progressions des autres épisodes |
| CW-29 | Deux appareils lisent un contenu neuf pendant les mêmes 20 secondes | Compter 20 s, pas 40 s ; les secondes simultanées ne comptent qu’une fois et la carte reste absente |
| CW-30 | Une lecture à 40 min est suivie d’une lecture plus récente arrêtée à 12 min | Reprendre à 12 min : la lecture la plus récente prime, même moins avancée |
| CW-31 | Une carte est retirée pendant une lecture déjà en cours sur un autre appareil | Rester masquée malgré les sauvegardes de cette session ; seul un nouveau démarrage réel après le retrait permet sa réapparition |

Le cumul de CW-17 est par film ou épisode, pas à l’échelle de toute une série.
CW-19 est une exception explicite au seuil d’apparition du suivant : ne pas
fabriquer une progression déjà lue pour afficher sa carte. Les critères de fin
restent applicables. CW-20 ne retire pas l’exception CW-19 : une série peut proposer
son suivant après un épisode court terminé. CW-21 s’applique au suivant proposé
depuis Continuer comme à l’enchaînement du lecteur ; son lancement automatique
attend toujours la fin réelle et le décompte. CW-22 conserve l’éligibilité déjà
acquise ; il ne crée pas une exception au premier seuil d’un contenu neuf.

## Synthèse de concurrence et garanties à définir dans C3

| ID | Situation | Règle acquise ou garantie à définir |
|---|---|---|
| CW-23 | Deux appareils lisent simultanément le même contenu | Temps simultané compté une fois et reprise depuis la lecture la plus récente ; CW-29/30 validés, identification de la récence à définir dans C3 |
| CW-32 | Un appareil reconnecté envoie une ancienne sauvegarde après une lecture plus récente | Distinguer ordre des lectures et ordre de réception ; définir la résolution sans confondre reconnexion et nouveau démarrage |
| CW-33 | Le même relevé de lecture est envoyé plusieurs fois après un délai réseau | Garantir qu’un réessai ne multiplie pas le temps réellement regardé ni les démarrages ; mécanisme à définir dans C3 |

CW-29 à CW-31 sont validés et rendent l’arbitrage de CW-23 observable.
Le retrait ne bloque pas la sauvegarde de progression : visibilité et position
restent distinctes. Une ancienne session qui envoie une nouvelle position ne
constitue pas un nouveau démarrage après retrait. CW-32/33 sont des cas de
réception à traiter dans C3, avec des écritures rejouées et des horloges d’appareils
décalées. La solution technique et les garanties en cas d’ordre indéterminable
restent à faire approuver ; aucun horodatage client n’est supposé fiable ici.

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
CW-17 à CW-22 et CW-29 à CW-31 sont acquises. Définir dans C3 les garanties
de CW-32/33, la récence entre appareils, le départage des événements indéterminables
et la propagation hors ligne avant implémentation, en respectant ces règles produit.

Le lot contractuel devra ensuite expliciter et faire approuver la lecture des
cartes masquées, le retrait, la réapparition, l’éligibilité et la reprise des
écritures après reconnexion. Prévoir l’idempotence des réessais et les données
déjà présentes avant la mise à jour, sans déduire leur temps regardé de leur position.
Cette liste décrit les garanties à couvrir ; elle ne définit aucun endpoint,
champ, migration, nouvelle stratégie de cache ou architecture.

La recette utilisera deux appareils, deux comptes et deux sources autorisées.
Elle distinguera résultat visible et données conservées. Les cas encore ouverts
restent non exécutables avec une attente définitive jusqu’à leur arbitrage.
