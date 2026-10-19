# Sprint 16 — Chromecast

Statut : **prévu**, version **0.4.0**, en mode rapide. Taille relative : L, à réévaluer d'après
l'essai S15-00. Référence : [roadmap 0.4.0](../roadmap/0.4.0/README.md),
[ADR 0013](../adr/0013-advanced-playback-pip-cast.md).

## Avancement

- [ ] S16-00 — périmètre fixé d'après S15-00 — 0 %
- [ ] S16-01 — émetteur Cast Android — 0 %
- [ ] S16-02 — émetteur Cast web — 0 %
- [ ] S16-03 — télécommande pendant la diffusion — 0 %
- [ ] S16-04 — progression et « Continuer » pendant la diffusion — 0 %
- [ ] S16-05 — flux refusé nommé, retour local — 0 %

**Avancement global ≈ 0 %**.

## Objectif

Envoyer une chaîne, un film ou un épisode sur un Chromecast et le piloter depuis le téléphone ou
le navigateur. Story : [US-027](stories/US-027-cast.md). Dépend de S15-00.

## Tâches proposées

| ID | Travail | Surface/dépendance |
|---|---|---|
| S16-00 | Décider le périmètre d'après le rapport S15-00 ; nouvel ADR si un récepteur personnalisé est envisagé ; passage par `openapi.yaml` si un indice de type de contenu est nécessaire | Décision Hamza ; avant le code |
| S16-01 | Android : `play-services-cast-framework` + `mediarouter` ; bouton Cast visible seulement quand un appareil est détecté ; lancement du contenu en cours à la même position (URL de `GET /…/playback`, titre, affiche) | Android mobile |
| S16-02 | Web : émetteur Cast dans les navigateurs Chromium, script chargé à la demande sur les pages lecteur uniquement ; mêmes règles que S16-01 | Web |
| S16-03 | Télécommande : lecture/pause, avance/recul si le contenu le permet, volume, arrêt, titre et affiche ; arrêter la diffusion propose de reprendre localement à la position atteinte | Android mobile, web |
| S16-04 | Progression, temps réellement regardé (segments seulement quand le récepteur lit, ADR 0012) et « Continuer » ; épisode suivant enchaîné sur le récepteur selon S13-03 | Android mobile, web ; S12, S13 |
| S16-05 | Flux refusé (format, `http`, CORS) : « Ce flux ne peut pas être envoyé sur ce Chromecast », la lecture reste sur l'appareil ; jamais d'écran noir silencieux ; aucun relais (ADR 0007) | Android mobile, web |

## Démo et sortie

Avec un Chromecast sur le réseau : le bouton apparaît, un film se lance sur la TV à la position du
téléphone, pause et avance depuis le téléphone, arrêt puis reprise locale. Un flux refusé affiche
le message dédié. Sans appareil Cast : aucun bouton. FR/EN. Bugs trouvés : au backlog.
