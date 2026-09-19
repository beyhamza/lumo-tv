# Direct et guide — cadrage 0.2.0

Organisation validée le 17 septembre 2026. Les interactions encore ouvertes sont
listées explicitement. Première proposition interactive préparée le 19 septembre
2026. Présentation générale retenue après relecture en conversation le même jour ;
le développement du sprint 9 n’a pas commencé. Cette validation ne clôt ni les
arbitrages C1/Q5 ni la recette sur appareils réels.

## Parcours validé

Une destination Direct rassemble deux vues, Chaînes et Guide, limitées à la source
active. Les trois surfaces ont la même priorité.

### Chaînes

- Filtres Toutes, Favoris et catégories de la source.
- Recherche par nom de chaîne.
- Nom, logo fourni par la source et programme en cours lorsqu'il est disponible.
- Sélection d'une chaîne : lancement immédiat de la lecture.
- Au retour du lecteur : retrouver sa position et son filtre.

Les logos éventuels sont des données de la source utilisateur : aucun logo réel
n'est ajouté aux maquettes, fixtures ou fichiers du dépôt.

### Guide

| Surface | Présentation validée |
|---|---|
| Android TV | Chaînes en lignes, horaires en colonnes ; navigation à la télécommande |
| Web | Grille horaire, filtres et déplacement dans le temps |
| Mobile | Liste des programmes en cours ; sélectionner une chaîne ouvre son programme de la journée |

Un bouton Maintenant ramène au créneau actuel. Les chaînes sans guide restent
accessibles dans Chaînes.

### Fiche de programme

Sélectionner un programme ouvre une fiche compacte sans quitter le guide. Sur
mobile, cette sélection intervient dans le programme de la journée de la chaîne.

- Programme en cours : titre, horaires, description si disponible et bouton
  Regarder en direct.
- Programme à venir : mêmes informations, sans bouton de lecture du programme.
- Programme passé : informations uniquement.
- Fermer la fiche ramène exactement à la case ou à l'élément sélectionné, avec
  restitution du focus sur TV.

Présentation retenue : panneau latéral sur TV/web, panneau depuis le bas sur
mobile. La vue Chaînes garde son lancement immédiat de la lecture ; la sélection
d'un programme dans le Guide ouvre d'abord ses informations.

### Jours et états du guide

- À l'ouverture du guide, arriver sur Maintenant et marquer le programme en cours.
- Proposer Aujourd'hui et les jours disponibles dans la fenêtre prévue par le
  projet, d'hier à J+3. Ne pas promettre des programmes que la source ne fournit pas.
- Le bouton Maintenant reste accessible pour revenir au créneau actuel.
- Créneau sans données : afficher « Aucun programme disponible sur ce créneau ».
- Aucun guide disponible : expliquer l'absence et proposer Voir les chaînes.
- Guide ancien : conserver les programmes disponibles et indiquer la date de mise
  à jour. Le seuil et la donnée permettant de mesurer cette fraîcheur restent à vérifier.
- Échec de chargement : garder les données déjà affichées et proposer Réessayer.
- Les programmes passés sont consultables à titre informatif, sans replay.

Ces libellés de cadrage seront traduits en FR/EN. Le message d'absence ne doit pas
attribuer une cause précise à une liste vide si les données ne permettent pas de
la distinguer d'une erreur ou d'un créneau non couvert.

### Filtres et ouverture — complément validé

- Chaînes et Guide partagent les filtres Toutes, Favoris et catégories de la source.
- Passer d'une vue à l'autre conserve le filtre actif.
- La recherche du Direct porte sur le nom des chaînes et conserve le filtre actif.
- Sans résultat, proposer d'effacer la recherche ou de revenir à Toutes.
- La première ouverture de Direct présente Chaînes. Les ouvertures suivantes
  retrouvent la dernière vue utilisée, mémorisée par appareil et par source.
- Depuis l'accueil, Toutes les chaînes ouvre Chaînes sans filtre ; Guide TV ouvre
  Guide sur Maintenant. Ces accès explicites priment sur la dernière vue mémorisée.
- Une nouvelle entrée dans Guide revient sur Maintenant. Un retour du lecteur
  conserve la position précédente ; ce retour n'est pas une nouvelle entrée.

## Articulation avec US-16

Ce cadrage complète [US-16 et le sprint 7](../../backlog/sprint-07.md), sans créer
une deuxième story EPG. Le sprint 7 décrit une grille horaire uniquement sur le
web ; la grille horaire TV validée ici constitue un ajout de périmètre à découper
et à estimer. Ses 34 points historiques ne chiffrent donc pas toute la cible 0.2.0.

Le catalogue et la recherche par nom existent dans le contrat. La lecture groupée
du guide reste le besoin contractuel identifié en S7-01 ; aucun endpoint nouveau
n'est défini par ce document. Vérifier le contrat avant implémentation.

La disponibilité d'un programme ne conditionne jamais l'accès à la chaîne.
Les critères de fraîcheur et les fixtures XMLTV du sprint 7 restent à intégrer à
la recette ; ce cadrage ne vaut pas validation de leur implémentation.

## Interactions encore à discuter

La proposition du 19 septembre illustre les choix déjà validés, sans les soumettre
à nouveau à approbation. Sa présentation générale est retenue ; les points ci-dessous
restent ouverts avant implémentation.

- Programme qui se termine pendant que sa fiche est ouverte : mise à jour des actions.
- Fraîcheur : seuil d'ancienneté et provenance de la date de mise à jour.
- Conservation du texte recherché entre vues et des filtres entre sessions.
- Focus initial TV, déplacement entre cases de durées différentes et retour lecteur.
- Chargement, pagination et affichage quand un logo manque.

Le replay, le timeshift et l'enregistrement restent hors périmètre selon `AGENTS.md`.

## Proposition visuelle du 19 septembre 2026

| Écran | Contenu | Tâche |
|---|---|---|
| S9-E01 Chaînes | Cartes avec nom, programme courant et progression ; sélection vers aperçu de lecture | S9-04 |
| S9-E02 Guide web/TV | Grille sur deux heures, durées différentes, jours et retour Maintenant | S9-05 |
| S9-E03 Guide mobile | Liste En ce moment, puis programmes de la journée d’une chaîne | S9-05 |
| S9-E04 Programme | Fiche latérale web/TV et panneau depuis le bas mobile ; lecture uniquement pour le programme courant | S9-06 |
| S9-E05 États | Guide absent, créneau incomplet, données anciennes, erreur conservant les données | S9-06 |

Les trois surfaces partagent recherche par nom et filtres Toutes/Favoris/catégories.
La maquette permet de les manipuler, de changer de jour, d’ouvrir une fiche et de
revenir à sa case après l’aperçu de lecture. Une variante de densité aérée/compacte
permet de comparer la présentation. La navigation hors Direct reste un contexte visuel.

Toutes les chaînes et tous les programmes sont fictifs, sans logo ni URL de flux.
L’horloge de démonstration est figée au 19 septembre 2026 à 20:25. Les jours sont
alimentés artificiellement pour montrer la fenêtre prévue ; l’application devra
respecter la disponibilité réelle. Les programmes longs sont tronqués visuellement
aux limites du créneau tout en conservant leurs horaires dans la fiche.

Le prototype est en français, sans API ni lecture réelle. La vue TV agrandie dans
la conversation ne remplace pas la validation à trois mètres, les dimensions
1920 × 1080, les marges de sécurité et la recette à la télécommande. À petite largeur,
la grille se réorganise pour rester lisible ; ce repli n’est pas un écran TV cible.
La journée mobile se déroule verticalement dans la conversation.

Contrôles du prototype : filtres partagés, recherche sans résultat, programme courant
avec action de lecture, programme futur sans lecture, retour du focus après aperçu,
parcours mobile et changement d’états. Absence d’erreur JavaScript et de débordement
horizontal contrôlée à 320, 390, 736 et 1024 px sur les trois variantes. Relecture
visuelle des grilles web/TV et du panneau mobile effectuée.

Restent notamment les tests applicatifs FR/EN, le lecteur réel, les gros catalogues,
le clavier TV, la conservation intersessions et la fraîcheur réelle. L’horloge figée
ne tranche pas le changement d’action lorsqu’un programme se termine. C1/Q5 restent
ouverts dans le [registre](../../roadmap/0.2.0/open-questions.md).

## Recette à préparer

Vérifier les filtres et la recherche sur la source active, la lecture depuis
Chaînes et le retour à la position précédente. Vérifier la navigation du guide
sur les trois surfaces, le bouton Maintenant et l'accès aux chaînes sans guide.
Vérifier les fiches des programmes passés, en cours et à venir, avec et sans
description, l'action Regarder en direct et le retour à l'élément sélectionné.
Vérifier l'arrivée sur Maintenant, les jours disponibles, un créneau vide, un guide
absent, un guide ancien et une erreur de chargement avec données déjà affichées.
Prévoir une session TV à la télécommande et les libellés FR/EN.

Référence : [décisions produit](../../roadmap/0.2.0/decisions.md).
