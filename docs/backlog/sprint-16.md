# Sprint 16 — Chromecast

Statut : **ouvert le 19 octobre 2026**, version **0.4.0**, en mode rapide. Taille relative : L.
Essai préalable : [rapport S15-00](../releases/0.4.0/qa-evidence/s15-00-cast-spike-2026-10-19/RAPPORT.md). Référence : [roadmap 0.4.0](../roadmap/0.4.0/README.md),
[ADR 0013](../adr/0013-advanced-playback-pip-cast.md).

## Avancement

- [x] S16-00 — périmètre fixé d'après S15-00 (décisions ci-dessous)
- [ ] S16-01 — émetteur Cast Android — 0 %
- [ ] S16-02 — émetteur Cast web — 85 % : SDK Google chargé à la demande sur les lecteurs seulement
  (Chromium ; ailleurs rien), récepteur par défaut, bouton « Diffuser sur un appareil » visible
  seulement si un appareil est détecté, absent pour une source à User-Agent ; type de flux déduit de
  l'URL (`lib/playback/cast/media.ts`, testé). Vérifié dans Chromium avec le vrai SDK sans appareil
  (script chargé au lancement de la lecture, état `NO_DEVICES_AVAILABLE`, aucun bouton, lecture locale
  intacte) et avec un SDK simulé (film). Reste : vrai Chromecast en S17.
- [ ] S16-03 — télécommande pendant la diffusion — web 85 % : « En diffusion sur … », position,
  lecture/pause, ±10 s si le récepteur et le serveur le permettent, volume, arrêt, puis « Reprendre ici
  à mm:ss ». Lecture locale en pause avant l'envoi. Vérifié avec le SDK simulé sur un film ; chaîne
  (arrêt du chargement HLS, envoi en direct) non jouée. Android : à faire.
- [ ] S16-04 — progression et « Continuer » pendant la diffusion — web 70 % : la position et le
  compteur de temps regardé suivent le récepteur (segments seulement quand il lit), sauvegarde à la
  pause, à l'arrêt et toutes les 30 s ; un épisode suivant se charge sur le récepteur si une session
  tourne. Non vérifié de bout en bout (pas d'appareil). Android : à faire.
- [ ] S16-05 — flux refusé nommé, retour local — web 85 % : quatre signaux (chargement rejeté,
  erreur du récepteur, `IDLE` sur erreur, 15 s de silence), message unique, la lecture reprend ici à la
  position ; code journalisé sans l'URL. Vérifié avec le SDK simulé (`LOAD_FAILED` 104). Android : à faire.

**Avancement global ≈ 45 %** (web avancé, Android à venir).

Branches : `feat/S16-web-cast` (web).

## Décisions d'ouverture (19/10/2026)

1. **Récepteur par défaut de Google**, pas de récepteur personnalisé ni de nouvel ADR (rapport S15-00 :
   un récepteur à nous refuserait les mêmes flux, CORS et `.ts` brut).
2. **Pas de vérification sur un vrai Chromecast pendant S16** (Hamza : « on ne recette pas le cast pour
   l'instant ») : la reprise du tableau S15-00 sur appareil passe en recette S17 (S17-02). Le code est
   vérifié par tests et, sur le web, par le SDK émetteur sans appareil.
3. **Pas de changement de contrat en S16.** Le type de flux (`contentType`) est déduit côté client de
   l'extension du chemin de l'URL, requête ignorée : `.m3u8` → HLS, `.mp4`/`.m4v` → MP4, `.mkv` →
   Matroska (tenté), `.ts` → MPEG-TS (tenté), sinon HLS pour une chaîne et MP4 pour un film ou un
   épisode. L'indice `container` proposé par S15-00 sera rediscuté après la mesure sur appareil.
4. **Un seul message pour tous les refus** (« Ce flux ne peut pas être envoyé sur ce Chromecast »),
   déclenché par les quatre signaux du rapport : `LOAD` refusé, erreur détaillée du récepteur, passage
   à `IDLE` sur erreur, ou rien au bout de 15 s. `904`/`LOAD_CANCELLED` et la fermeture du sélecteur ne
   sont pas des refus. Le code détaillé est journalisé, jamais l'URL.
5. **La lecture locale s'arrête avant le `LOAD`** (abonnements à une connexion) et reprend à la
   position atteinte si le `LOAD` échoue.
6. **Source avec `user_agent`** : pas de diffusion proposée (le récepteur ne peut pas l'envoyer).
7. **Film sans Range** : avance et recul désactivés sur le Chromecast comme en local ; une reprise
   démarre à 0 et le dit.

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
