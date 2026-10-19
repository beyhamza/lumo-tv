# Sprint 15 — Picture-in-Picture et essai Cast

Statut : **ouvert le 19 octobre 2026**, version **0.4.0**, en mode rapide (tests automatisés et
vérification rapide du chemin principal ; recette approfondie groupée en S17). Taille relative : M.
Aucun changement de contrat prévu. Référence : [roadmap 0.4.0](../roadmap/0.4.0/README.md),
[ADR 0013](../adr/0013-advanced-playback-pip-cast.md).

## Avancement

- [ ] S15-00 — essai Cast sur le récepteur par défaut — 80 % : [rapport](../releases/0.4.0/qa-evidence/s15-00-cast-spike-2026-10-19/RAPPORT.md) versé
  (tableau format × condition, détection des refus, avis sur le contrat), recommandation : récepteur
  par défaut, pas de nouvel ADR. Aucun Chromecast disponible : tableau documenté et sondé dans
  Chromium, pas mesuré sur récepteur. Reste : rejouer le prototype sur un appareil réel (proposé en
  S16-00, bloquant pour S16).
- [ ] S15-01 — PiP Android mobile — 85 % : fenêtre flottante des lecteurs de film, d'épisode et de
  chaîne (téléphone seulement, `supportsPictureInPicture` dans `app-mobile`). Entrée automatique en
  quittant l'app pendant une lecture (Android 12+ : `setAutoEnterEnabled` ; avant : `onUserLeaveHint`)
  et bouton « Fenêtre flottante » en haut à droite du lecteur, absent si l'appareil n'a pas de PiP ;
  refus du système nommé (toast). Actions : lecture/pause, plus Direct pour une chaîne. Fermer =
  lecture arrêtée, position sauvegardée, retour à l'écran précédent ; agrandir = lecteur avec ses
  contrôles. Règles dans `FloatingWindowRules` (tests JVM). Vérifié sur l'émulateur Pixel (API 37)
  avec `mixed.m3u` : Accueil → fenêtre ; bouton → fenêtre ; pause depuis la fenêtre ; Pause + Direct
  sur une chaîne ; agrandir ; fermer → « Reprendre à 0:03 ». Reste : chemin Android 8–11
  (`onUserLeaveHint`) non joué, vrai téléphone en S17. `BUG-S15-01-01` (retour au lecteur sans
  fenêtre) corrigé, en recette.
- [ ] S15-02 — préférence « Fenêtre flottante en quittant l'application » — 95 % : Réglages →
  Lecture, interrupteur activé par défaut, par compte sur l'appareil (DataStore S13), ligne absente
  sur TV et sur un téléphone sans PiP ; FR/EN. Tests `PlaybackPreferencesTest`, `SettingsSectionsTest`.
  Vérifié sur l'émulateur : interrupteur présent, désactivé → Accueil ne crée pas de fenêtre et la
  lecture se met en pause (S10B-03). Reste : recette S17.
- [ ] S15-03 — PiP web — 90 % : bouton « Fenêtre flottante » / « Revenir dans la page » sous les
  lecteurs de film, d'épisode et de chaîne, absent sans l'API ; refus du navigateur nommé. Vérifié
  dans Chromium sur le banc (film et chaîne). Reste : Firefox et Safari en recette S17.
- [ ] S15-04 — progression, temps regardé et épisode suivant en fenêtre flottante — 85 % : web fait
  (la fenêtre est l'élément `<video>` lui-même, sauvegardes et compteur inchangés ; vérifié sur le
  banc). Limite web : passer à l'épisode suivant ou changer de chaîne ferme la fenêtre, le navigateur
  exige un geste pour la rouvrir. Android : 80 % — en fenêtre l'activité reste démarrée (en pause,
  pas arrêtée), donc la boucle de sauvegarde de 30 s, les segments regardés (ADR 0012) et l'enchaînement
  d'épisode (compte à rebours du view model, préférence S13-03) continuent ; la carte « Épisode suivant »
  n'est pas dessinée dans la fenêtre. Vérifié : position sauvegardée après pause dans la fenêtre puis
  fermeture. Non vérifié sur appareil : l'enchaînement d'épisode en fenêtre (le banc M3U n'a pas de
  séries, les flux Xtream du banc ne sont pas joignables depuis l'émulateur).
- [ ] S15-05 — petits sujets embarqués — 90 % : rail TV français sur une ligne, `BUG-S11-06-09`
  corrigé dans l'API (durée lue deux fois en minutes), « Ma liste » partout, médias de banc
  multipistes (`/multitrack.m3u`) vérifiés sur le web. Reste : changement de piste réel sur Android
  avec ces médias (recette S17). [Rapport](../releases/0.4.0/qa-evidence/s15-05-2026-10-19/RAPPORT.md).

**Avancement global ≈ 88 %**.

Branches : `feat/S15-web-pip` (web), `feat/S15-android-pip` (Android), `feat/S15-05-small`
(petits sujets), `docs/S15-00-cast-spike` (essai Cast) ; réunies dans `chore/merge-dev-s15`.

## Bugs trouvés (au backlog, sans bloquer)

- **BUG-S15-01-01 — Android mobile : revenir dans l'app arrête le lecteur.** Préexistant (pas lié au
  PiP). `MainActivity` collecte `AppStartDecision.stream` avec `collectAsStateWithLifecycle` ; à chaque
  retour au premier plan le flux redémarre, `onStart { emit(Loading) }` retire le `NavHost` une frame,
  le lecteur est disposé (`stop()` : sauvegarde puis arrêt du flux) puis recomposé, et `start()` ne
  relance rien (même identifiant). Effet : avec la fenêtre flottante désactivée ou refusée, Accueil
  puis retour laisse un lecteur vide sur « 0:00 / -- » dont « Lecture » ne fait rien. Constaté sur
  l'émulateur Pixel (API 37), film du banc. Piste : ne pas réémettre `Loading` au redémarrage de la
  collecte (état conservé, ou `collectAsState`), à vérifier aussi sur TV.
  **Corrigé, en recette** (branche `fix/BUG-S15-01-01-resume-player`) : `AppStartDecision` ne dit
  `Loading` qu'une fois par processus, une nouvelle collecte repart du dernier état atteint. La TV
  (`TvActivity`) avait le même défaut, même collecte : corrigée par le même changement, dans
  `core:data`. Tests `AppStartDecisionTest` (retour sans `Loading`, session perdue pendant
  l'absence). Vérifié sur l'émulateur Pixel_10 (API 37), fenêtre désactivée, `mixed.m3u` : film
  lancé, Accueil, retour → lecteur en pause à 0:04 avec l'image, « Lecture » reprend jusqu'à la
  fin. Reste : la même vérification sur TV (non jouée sur l'émulateur), recette S17.
- **BUG-S15-05-01 — web : la fin d'un flux en direct est annoncée comme un refus du fournisseur.**
  Mineur, vu sur le flux de banc de 12 s ([rapport S15-05](../releases/0.4.0/qa-evidence/s15-05-2026-10-19/RAPPORT.md)).

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
