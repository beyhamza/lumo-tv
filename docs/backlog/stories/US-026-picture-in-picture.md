# US-026 — Regarder dans une fenêtre flottante (Picture-in-Picture)

Statut : validée le 18 octobre 2026. Version cible : 0.4.0 (sprint 15).
Surfaces : Android mobile, web. Ouverture du périmètre : [ADR 0013](../../adr/0013-advanced-playback-pip-cast.md).

## Besoin

En tant qu'utilisateur, je veux continuer à voir une chaîne, un film ou un épisode dans une petite
fenêtre quand je passe à une autre application ou à un autre onglet, afin de ne pas interrompre ce
que je regarde.

## Critères d'acceptation proposés

- **Android mobile** : quitter l'application pendant une lecture (bouton Accueil, geste) passe la vidéo
  en fenêtre flottante si le système l'autorise ; un bouton « Fenêtre flottante » dans le lecteur fait
  de même. La fenêtre propose lecture/pause (et Direct pour une chaîne). Fermer la fenêtre arrête la
  lecture et sauvegarde la position ; l'agrandir revient au lecteur.
- Sans Picture-in-Picture (désactivé par l'utilisateur dans le système), la règle actuelle s'applique :
  la lecture se met en pause en arrière-plan (S10B-03).
- Préférence « Fenêtre flottante en quittant l'application » dans Réglages → Lecture, activée par
  défaut.
- **Web** : un bouton « Fenêtre flottante » sur les lecteurs de film, d'épisode et de chaîne, quand le
  navigateur prend en charge l'API ; absent sinon (pas de bouton inutile).
- La progression et le temps réellement regardé continuent d'être comptés dans la fenêtre (ADR 0012) ;
  l'épisode suivant s'enchaîne selon la préférence S13-03.
- FR/EN, accessible (libellés, clavier sur le web).

## Hors périmètre

Android TV. Plusieurs fenêtres simultanées.
