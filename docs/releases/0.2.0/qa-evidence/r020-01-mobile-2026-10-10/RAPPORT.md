# R020-01 — mobile (§3.3 W-3) et rejoue mobile de BUG-R020-01-01 — 10/10/2026

- Objet : `W-3` du plan [`r020-01-isolation-matrix.md`](../../r020-01-isolation-matrix.md)
  (« A connecté, déconnexion, connexion B : aucune donnée de A ») et rejoue **mobile** du
  correctif `BUG-R020-01-01`.
- Build : APK mobile debug depuis `fix/BUG-R020-01-01-signout-purge` ; émulateur `Pixel_10` ;
  API locale reconstruite depuis `dev` ; `adb reverse tcp:8080`.
- Mêmes comptes de recette et sources neutres du banc que le passage TV
  ([`r020-01-tv-2026-10-10`](../r020-01-tv-2026-10-10/RAPPORT.md)). Connexion par
  email/mot de passe, saisis par `adb input` depuis `apps/api/.env.recette` (hors dépôt).

## Déroulé

| Étape | Preuve | Base locale (`run-as tv.lumo.android.debug`) |
|---|---|---|
| Installation vierge (`pm clear`), connexion A | `ecrans/01-A-connecte.png` | `channel` 2, `favorite` 1, `favorite_group` 1, `recent_channel` 1 ; `active_source` 94 o, `session.bin` 429 o |
| Réglages de A | `ecrans/02-A-reglages.png` | profil et appareils de A uniquement |
| « Sign out of this device » confirmé | `ecrans/03-confirmation.png`, `ecrans/04-connexion-apres-sign-out.png` | **toutes les tables à 0**, `active_source` **0 o**, `session.bin` 0 o ; champ email **vide** |
| Connexion B | `ecrans/05-B-connecte.png` | source de B, accueil vide ; **aucune ligne de A** |
| Réglages de B | `ecrans/06-B-reglages.png` | profil de B ; appareils de B uniquement (ses activations TV de la recette, `recette`, `recette-curl`) |

## Verdict

- `W-3` : ✅ **conforme** — rien de A à l'écran ni sur l'appareil après la connexion de B.
- `BUG-R020-01-01`, mobile : ✅ **conforme** — même purge que la TV (code partagé `core:auth` /
  `core:data`), vérifiée sur l'appareil.

## Non joué

- `W-1`, `W-2` (web) : le front web n'a pas été lancé pendant cette séance.
- `W-4` (jeton A rejoué après déconnexion) : couvert côté API par A-7/A-8 pour le refresh ; le
  jeton d'accès reste accepté jusqu'à son expiration (≤ 15 min), observation toujours à arbitrer.
