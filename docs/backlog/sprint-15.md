# Sprint 15 — Picture-in-Picture et essai Cast

Statut : **ouvert le 19 octobre 2026**, version **0.4.0**, en mode rapide (tests automatisés et
vérification rapide du chemin principal ; recette approfondie groupée en S17). Taille relative : M.
Aucun changement de contrat prévu. Référence : [roadmap 0.4.0](../roadmap/0.4.0/README.md),
[ADR 0013](../adr/0013-advanced-playback-pip-cast.md).

## Avancement

- [ ] S15-00 — essai Cast sur le récepteur par défaut — 0 %
- [ ] S15-01 — PiP Android mobile — 0 %
- [ ] S15-02 — préférence « Fenêtre flottante en quittant l'application » — 0 %
- [ ] S15-03 — PiP web — 90 % : bouton « Fenêtre flottante » / « Revenir dans la page » sous les
  lecteurs de film, d'épisode et de chaîne, absent sans l'API ; refus du navigateur nommé. Vérifié
  dans Chromium sur le banc (film et chaîne). Reste : Firefox et Safari en recette S17.
- [ ] S15-04 — progression, temps regardé et épisode suivant en fenêtre flottante — 40 % : web fait
  (la fenêtre est l'élément `<video>` lui-même, sauvegardes et compteur inchangés ; vérifié sur le
  banc). Limite web : passer à l'épisode suivant ou changer de chaîne ferme la fenêtre, le navigateur
  exige un geste pour la rouvrir. Android à faire.
- [ ] S15-05 — petits sujets embarqués — 90 % : rail TV français sur une ligne, `BUG-S11-06-09`
  corrigé dans l'API (durée lue deux fois en minutes), « Ma liste » partout, médias de banc
  multipistes (`/multitrack.m3u`) vérifiés sur le web. Reste : changement de piste réel sur Android
  avec ces médias (recette S17). [Rapport](../releases/0.4.0/qa-evidence/s15-05-2026-10-19/RAPPORT.md).

**Avancement global ≈ 37 %**.

Branches : `feat/S15-web-pip` (web), `feat/S15-05-small` (petits sujets).

## Objectif

Continuer à regarder dans une fenêtre flottante en quittant l'application ou l'onglet, et savoir
avant S16 quels flux un Chromecast accepte vraiment.
Stories : [US-026](stories/US-026-picture-in-picture.md) et l'essai préalable d'[US-027](stories/US-027-cast.md).

## Tâches proposées

| ID | Travail | Surface/dépendance |
|---|---|---|
| S15-00 | Essai technique Cast : ce que le Default Media Receiver lit avec les flux libres de droits du banc et les formats courants (HLS, MP4, MKV, MPEG-TS, `http` sans CORS) ; rapport et recommandation de périmètre pour S16 | Web (Chrome) et Android ; avant S16 |
| S15-01 | PiP Android mobile : entrée en quittant l'app pendant une lecture et par un bouton « Fenêtre flottante » ; actions lecture/pause (et Direct pour une chaîne) ; fermer la fenêtre arrête et sauvegarde ; l'agrandir revient au lecteur ; sans PiP, pause en arrière-plan (S10B-03) | Android mobile ; pas TV |
| S15-02 | Réglages → Lecture : « Fenêtre flottante en quittant l'application », activée par défaut, locale à l'appareil | Android mobile ; S13-04 |
| S15-03 | PiP web : bouton « Fenêtre flottante » sur les lecteurs de film, d'épisode et de chaîne quand l'API existe, absent sinon ; clavier et libellés | Web |
| S15-04 | En fenêtre flottante, la progression et le temps réellement regardé sont comptés (ADR 0012) ; l'épisode suivant s'enchaîne selon S13-03 | Android mobile, web ; S12, S13 |
| S15-05 | Petits sujets : libellés du rail TV en français sur une ligne ; `BUG-S11-06-09` (durée d'épisode) ; nom « À regarder » / « Ma liste » / « My list » harmonisé ; médias de banc multipistes (reste de S13-00) | Toutes |

## Démo et sortie

Sur téléphone : lancer un film, appuyer sur Accueil, la vidéo continue en fenêtre ; pause depuis
la fenêtre ; fermer la fenêtre puis relancer depuis « Continuer » à la bonne position. Désactiver la
préférence : la lecture se met en pause en arrière-plan. Sur le web : bouton présent dans Chrome,
absent là où l'API manque. Rapport S15-00 versé dans `docs/releases/0.4.0/qa-evidence/` avec une
recommandation pour S16 ; aucun flux réel ni URL de fournisseur dans le dépôt (règle 2).
FR/EN sur chaque libellé. Bugs trouvés : au backlog, sans bloquer.
