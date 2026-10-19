# Roadmap 0.4.0 — Lecteur avancé

Date de cadrage : 18 octobre 2026. Statut : **validée par Hamza le 18 octobre 2026**.
Mode de travail : rapide (décision du 15/10) — tests automatisés et vérification du chemin principal
par lot, recette approfondie groupée avant la sortie.

## Objectif

Continuer à regarder sans rester devant l'écran de l'application : dans une petite fenêtre au-dessus
des autres applications (Picture-in-Picture), ou sur le téléviseur du salon depuis le téléphone ou le
navigateur (Chromecast).

Les deux fonctions sont **hors périmètre v1** dans `AGENTS.md` §6. Elles sont ouvertes par
l'[ADR 0013](../../adr/0013-advanced-playback-pip-cast.md), acceptée le 18 octobre 2026.

## Stories

| Story | Besoin | Surfaces |
|---|---|---|
| [US-026](../../backlog/stories/US-026-picture-in-picture.md) | Garder la vidéo dans une fenêtre flottante en quittant l'application ou l'onglet | Android mobile, web |
| [US-027](../../backlog/stories/US-027-cast.md) | Envoyer la lecture vers un Chromecast et la piloter depuis le téléphone ou le navigateur | Android mobile, web (Chrome) |

Android TV n'est concerné par aucune des deux : la TV est l'écran de destination, pas l'émetteur, et
le Picture-in-Picture y est marginal.

## Découpage proposé

| Sprint | Contenu | Taille |
|---|---|---|
| [S15](../../backlog/sprint-15.md) — Picture-in-Picture et essai Cast | US-026 complet (mobile, web) ; `S15-00` essai technique Cast : quels flux un récepteur Chromecast accepte vraiment (HLS, MP4, MKV, `http`, CORS) | M |
| [S16](../../backlog/sprint-16.md) — Chromecast | US-027 sur Android mobile et le web, selon le résultat de l'essai | L |
| [S17](../../backlog/sprint-17.md) — Qualification 0.4.0 | Recette groupée 0.3.0 + 0.4.0 sur appareils réels, puis sortie | M |

## Risque principal : les flux IPTV sur un récepteur Cast

Un Chromecast lit l'URL du flux lui-même : la lecture ne passe pas par Lumo (ADR 0007, pas de relais).
Beaucoup de fournisseurs servent en `http`, sans en-têtes CORS, parfois en MPEG-TS brut ou MKV — des
formats que le récepteur par défaut de Google ne lit pas toujours. L'essai `S15-00` mesure ce qui
passe avant de promettre quoi que ce soit ; un flux refusé est nommé, jamais masqué.

## Hors périmètre de la 0.4.0

Abonnement Premium et connexion Google (dettes 1 et 2, non retenues le 18/10), AirPlay et iOS,
Chromecast depuis la TV, relais ou transcodage de flux (contraire à l'ADR 0007).

## Petits sujets à embarquer au fil de l'eau

Regroupés dans la tâche `S15-05`.

- Rail TV en français : « Ma bibliothèque » et « À regarder » passent sur deux lignes.
- `BUG-S11-06-09` : durée d'épisode affichée en secondes comme des minutes (mobile).
- Médias de banc autorisés multipistes, sous-titrés et multi-qualités (S13-00), pour vérifier les
  changements de piste.
- Nom de la section « À regarder » / « Ma liste » / « My list » à harmoniser entre surfaces.
