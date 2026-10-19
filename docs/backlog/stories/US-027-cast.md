# US-027 — Envoyer la lecture sur un Chromecast

Statut : validée le 18 octobre 2026. Version cible : 0.4.0 (essai en sprint 15, réalisation
en sprint 16). Surfaces : Android mobile, web (navigateurs Chromium). Ouverture du périmètre :
[ADR 0013](../../adr/0013-advanced-playback-pip-cast.md).

## Besoin

En tant qu'utilisateur, je veux envoyer une chaîne, un film ou un épisode sur le Chromecast de mon
salon et le piloter depuis mon téléphone ou mon navigateur, afin de regarder sur grand écran sans
installer l'application TV.

## Critères d'acceptation proposés

- Un bouton Cast apparaît dans le lecteur quand un appareil Cast est détecté sur le réseau, et seulement
  dans ce cas.
- Choisir un appareil y lance le contenu en cours, à la même position ; le téléphone ou l'onglet devient
  une télécommande : lecture/pause, avance/recul (si le contenu le permet), volume, arrêt, titre et
  affiche.
- **Un flux que le Chromecast ne peut pas lire est nommé** (« Ce flux ne peut pas être envoyé sur ce
  Chromecast ») et la lecture reste sur l'appareil ; jamais d'écran noir silencieux.
- Pendant la diffusion, la progression, le temps réellement regardé et « Continuer » fonctionnent comme
  en lecture locale (ADR 0012) ; arrêter la diffusion reprend localement à la position atteinte, sur
  demande.
- L'épisode suivant s'enchaîne sur le Chromecast selon la préférence S13-03.
- La lecture reste directe : Lumo ne relaie ni ne transcode aucun flux (ADR 0007).
- FR/EN.

## Essai préalable — S15-00

Mesurer, avec les flux libres de droits du banc et les formats courants des fournisseurs (HLS, MP4,
MKV, MPEG-TS, `http` sans CORS), ce que le récepteur par défaut de Google accepte. Le résultat décide
du périmètre exact de S16, ou d'un récepteur personnalisé (nouvel ADR).

## Hors périmètre

AirPlay, iOS, diffusion depuis l'application Android TV, file d'attente de plusieurs contenus.
