# Direct et guide — cadrage 0.2.0

Organisation validée le 17 septembre 2026. Les interactions encore ouvertes sont
listées explicitement. Les maquettes restent à produire à la demande de l'utilisateur.

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

## Lien avec le backlog

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

- Programme qui se termine pendant que sa fiche est ouverte : mise à jour des actions.
- Étendue temporelle consultable et gestion des jours sans données.
- Guide absent, partiel, périmé ou en erreur : présentation et actions.
- Filtres précis du Guide, conservation des filtres entre vues et vue par défaut.
- Focus initial TV, déplacement entre cases de durées différentes et retour lecteur.
- Chargement, pagination et affichage quand un logo manque.

Le replay, le timeshift et l'enregistrement restent hors périmètre selon `AGENTS.md`.

## Recette à préparer

Vérifier les filtres et la recherche sur la source active, la lecture depuis
Chaînes et le retour à la position précédente. Vérifier la navigation du guide
sur les trois surfaces, le bouton Maintenant et l'accès aux chaînes sans guide.
Vérifier les fiches des programmes passés, en cours et à venir, avec et sans
description, l'action Regarder en direct et le retour à l'élément sélectionné.
Prévoir une session TV à la télécommande et les libellés FR/EN.

Référence : [décisions produit](../../roadmap/0.2.0/decisions.md).
