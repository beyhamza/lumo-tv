# S15-05 — petits sujets embarqués — 19 octobre 2026

Branche `feat/S15-05-small`. Vérification rapide du chemin principal (mode rapide du 15/10).

| Sujet | Correctif | Vérifié |
|---|---|---|
| Rail TV en français sur deux lignes | « À regarder » devient « Ma liste » (voir ci-dessous). « Ma bibliothèque » tient sur une ligne : rail 244 dp au lieu de 220, marges `sm` dans le rail, interlettrage 0,04 em au lieu de 0,1 | Émulateur `Television_1080p`, app en français : [avant](tv-rail-fr-avant.png), [après](tv-rail-fr-apres.png), focus compris. Le nom de la source active n'est plus tronqué non plus |
| `BUG-S11-06-09` — « 2700 min » par épisode | Le défaut était dans l'API, pas sur le mobile : `duration_secs` (déjà en secondes) était multiplié par 60 à l'ingestion Xtream. Touche toutes les surfaces | Test `XtreamClientTest.readsEpisodeDurationInSeconds`. Les arbres de séries en cache expirent : les durées déjà enregistrées se corrigent à la prochaine ouverture. Non revu à l'écran |
| Nom « À regarder » / « Ma liste » / « My list » | « Ma liste » / « My list » partout : écran et entrée Android en français, phrase d'en-tête du web. Le bouton de fiche et le web disaient déjà « Ma liste » | Rail TV (capture). Mobile non revu à l'écran |
| Médias de banc multipistes (reste de S13-00) | `/multitrack.m3u` sur le banc : une chaîne HLS (240p et 480p, audio fr/en, sous-titres WebVTT fr/en) et un film MKV (audio fr/en, sous-titres SRT fr/en). 440 Hz = français, 880 Hz = anglais. Généré par `apps/web/e2e/bench/make-multitrack.sh`, ~1 Mo, rien de réel | Web (Chromium) : menus à 2 audios, 2 sous-titres, 2 qualités ; passage à l'audio anglais, sous-titres français affichés, 240p donne une image 426×240. Android : non joué, médias prêts pour la recette S17 |

## Vu en passant

- Le flux de banc dure 12 s. Arrivée à la fin, une chaîne affiche « Votre fournisseur n'autorise pas
  la lecture depuis un navigateur » : la fin d'un flux en direct est prise pour un refus. Sans effet
  sur un vrai direct, mais le message est faux. Au backlog : `BUG-S15-05-01` (mineur, web).
