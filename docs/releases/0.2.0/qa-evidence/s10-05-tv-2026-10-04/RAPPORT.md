# S10-05 — Phase 2 : recette TV (rapport partiel, captures préservées)

- Date : 2026-10-04
- Branche de recette : `qa/S10-05-recette` @ `74ff0c1` (base `dc0f9d2` + test-only
  `e9cfa97`/`546dbdd` pour l'ancre du banc e2e).
- Surface : Android TV debug (`tv.lumo.androidtv.debug`), AVD `Television_1080p`
  (android-tv android-36, 1920x1080), `emulator-5554`.
- Objet : SR-13 (focus d'arrivée), SR-12 (retour de fiche/lecteur), SR-04,
  SR-10, SR-11 sur TV, et **le point ouvert** : joignabilité D-pad du bouton
  `feature_search_clear` de l'état global sans résultat.

> ⚠️ Ce rapport est un **instantané en cours de campagne**, poussé d'abord pour
> préserver les captures. Le verdict CONFORME / NON CONFORME de chaque cas est
> ajouté dans la section « Verdict » ci-dessous au fil de l'exécution.

## Environnement de recette

- APK TV construit et installé depuis ce worktree (`:app-tv:assembleDebug`).
- Session TV ré-approuvée via le flux device-code (`tv-provision.mjs` : register
  compte → source mixte M3U+EPG prête → POST `/v1/auth/device/approve`).
- Source « Banc S10-05 TV » READY, joignant le banc e2e (`host.docker.internal:18081`
  vu depuis `lumo-api` sur le réseau Docker).

## Captures

| Fichier | Contenu |
|---|---|
| `tv-current.png`, `s1004tv-01-launch.png` | état TV avant réinstallation |
| `s1005-01-connected.png` | session TV reconnectée avec la source prête |
| `s1005-02-left.png` | navigation D-pad vers Recherche |
| `s1005-03-search-arrival.png`, `s1005-04-search-arrival.png` | arrivée sur Recherche, champ focalisé + clavier plateforme |
| `s1005-03b-search.png`, `s1005-03c.png` | état intermédiaire |
| `s1005-05-afterback.png` | BACK : clavier fermé, champ toujours focalisé |
| `s1005-06-results.png`, `s1005-07-results.png`, `s1005-08-results-nokb.png` | résultats rendus derrière le clavier, focus champ conservé |
| `s1005-09-noresult.png`, `s1005-10-noresult.png` | état global sans résultat (requête + source + action Effacer) |
| `s1005-11-down1.png`, `s1005-12-down4.png` | DOWN répétés : focus reste sur le champ |
| `s1005-13-tab.png`, `s1005-14-right.png` | TAB / RIGHT : tentative d'atteindre l'action Effacer |

## Verdict (à compléter au fil de la campagne)

Voir section « Statut par cas SR » en bas de ce fichier (mise à jour en fin de phase).

## Statut par cas SR (TV)

_En cours._
