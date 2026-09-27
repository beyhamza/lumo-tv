# Index des passages de recette QA — 0.2.0

Une ligne par passage. Les règles du dossier sont dans
[`README.md`](README.md) (apporté par la branche `docs/S9-05-qa-evidence`).
Tenue de l'index : @QA. Il ne se substitue pas aux rapports : il dit quel
passage fait foi pour quel correctif, et ce qui a été écarté.

## Passages conservés

| Passage | Objet recetté | SHA | Verdict | Rapport | Preuves décisives |
|---|---|---|---|---|---|
| [`s9-06-04-web-2026-09-26/`](s9-06-04-web-2026-09-26/) | S9-06-04 — fiche programme **web** (GD-07/08/10/11), cas `QA-06-04-01→12` | `feat/S9-06-04-web-programme-sheet` @ `943d27e` (base `7d3e6d2`) | ⚠️ **partiel** : ✅ 01/02/03/04/08/09/10/12, ✅ 05 (1<sup>re</sup> moitié), ❌ 07 non livré (natif Android/TV), ⚠️ 05-2<sup>e</sup> non prouvé, ⚠️ 11 **non applicable au web** (choix de source par appareil) | [`RECETTE-S9-06-04-2026-09-26.md`](s9-06-04-web-2026-09-26/RECETTE-S9-06-04-2026-09-26.md) | [`run1-web-chromium-journey.log`](s9-06-04-web-2026-09-26/run1-web-chromium-journey.log) (9 passed), [`run2-web-fault-503.log`](s9-06-04-web-2026-09-26/run2-web-fault-503.log) (GD-10), [`run3-web-guide-partial.log`](s9-06-04-web-2026-09-26/run3-web-guide-partial.log) (GD-11), [`run4-past-source.log`](s9-06-04-web-2026-09-26/run4-past-source.log) (03 ✅ + 11 portée par appareil, 3 passed), [`fault-proxy.mjs`](s9-06-04-web-2026-09-26/fault-proxy.mjs) |
| [`s9-07-passe-initiale-main-2026-09-26/`](s9-07-passe-initiale-main-2026-09-26/) | Passe manuelle initiale web + TV sur `main` (a relevé les défauts #213→#216 et le cosmétique « 1 chaînes ») | `main` @ `76c2c42` | 5 observations, 4 promues en `BUG-*` | [`PASS-2026-09-26.md`](s9-07-passe-initiale-main-2026-09-26/PASS-2026-09-26.md) | banc généré : `make-guide.mjs`, `setup-stack.mjs`, `guide.xml` |
| [`s9-04-07-cold-home-web-2026-09-26/`](s9-04-07-cold-home-web-2026-09-26/) | #213 `BUG-S9-04-07-01` — accès « Toutes les chaînes »/« Guide TV » à froid, **web** | `fix/S9-04-07-01-home-cold-entries` @ `3571f69` (base `76c2c42`) | ✅ **rouge → vert** (test d'acceptation écrit depuis le critère PO) | [`RECETTE-FIXES-2026-09-26.md`](s9-04-07-cold-home-web-2026-09-26/RECETTE-FIXES-2026-09-26.md) | [`cold-home-journey.spec.ts`](s9-04-07-cold-home-web-2026-09-26/cold-home-journey.spec.ts), logs `2026-09-26-red-main-cold-home.log` / `…green-fix213…`, rejeu `e2e213-replay-2.log` |
| [`s9-04-04-cold-home-android-2026-09-26/`](s9-04-04-cold-home-android-2026-09-26/) | S9-04-04 — accueil à froid **Android mobile + TV** : défaut constaté (ni « Toutes les chaînes » ni « Guide TV ») | `main` @ `76c2c42` | ❌ **défaut** (corrigé par #217) | [`RECETTE-S9-04-04-2026-09-26.md`](s9-04-04-cold-home-android-2026-09-26/RECETTE-S9-04-04-2026-09-26.md) | [`mob01-cold-home.png`](s9-04-04-cold-home-android-2026-09-26/mob01-cold-home.png), [`tv-cold01-home.png`](s9-04-04-cold-home-android-2026-09-26/tv-cold01-home.png), [`setup-cold-source.mjs`](s9-04-04-cold-home-android-2026-09-26/setup-cold-source.mjs) |
| [`s9-04-04-217-cold-entries-android-2026-09-26/`](s9-04-04-217-cold-entries-android-2026-09-26/) | #217 `BUG-S9-04-04-01` — accès à froid **Android mobile + TV** (3 scénarios : froid, favoris sans récentes, froid sans rail) | `fix/S9-04-04-android-cold-entries` @ `3c17162` (base `76c2c42`) | ✅ **conforme** | [`RECETTE-217-2026-09-26.md`](s9-04-04-217-cold-entries-android-2026-09-26/RECETTE-217-2026-09-26.md) | [`qa217-mob-01-cold-home.png`](s9-04-04-217-cold-entries-android-2026-09-26/qa217-mob-01-cold-home.png), [`qa217-tv-03-cold-home.png`](s9-04-04-217-cold-entries-android-2026-09-26/qa217-tv-03-cold-home.png), [`qa217-tv-07-after-center.xml`](s9-04-04-217-cold-entries-android-2026-09-26/qa217-tv-07-after-center.xml) |
| [`s9-05-02-214-grid-web-2026-09-26/`](s9-05-02-214-grid-web-2026-09-26/) | #214 `BUG-S9-05-02-01` — densité/lisibilité de la grille du guide, **web 1440 px** | `fix/S9-05-02-01-web-grid-density` @ `ee9bb1e` (❌) puis corrigée @ `ed3680a` (`13bbde2`) | ✅ **conforme après `min-w-0`** (défilement borné à la grille, « Maintenant » revenu à x≈1145) | [`RECETTE-214-215-216-2026-09-26.md`](s9-05-02-214-grid-web-2026-09-26/RECETTE-214-215-216-2026-09-26.md) | [`web-pass-214-main/g214-01-guide-1440.png`](s9-05-02-214-grid-web-2026-09-26/web-pass-214-main/g214-01-guide-1440.png) (rouge), [`web-pass-214/g214-03-doc-scrolled.png`](s9-05-02-214-grid-web-2026-09-26/web-pass-214/g214-03-doc-scrolled.png), [`web-pass-214-new/g214-new-01-guide-1440.png`](s9-05-02-214-grid-web-2026-09-26/web-pass-214-new/g214-new-01-guide-1440.png) (après) |
| [`s9-05-03-215-216-grid-tv-2026-09-26/`](s9-05-03-215-216-grid-tv-2026-09-26/) | #215 `BUG-S9-05-03-01` + #216 `BUG-S9-05-03-02` — densité de la grille et en-tête du Guide, **TV 1080p** | `fix/S9-05-03-tv-grid-density` @ `7b15dfd` | ✅ **conforme** (12 h ET 24 h ; en-tête, filtres, 1 `/epg` groupé, `LEFT`) | [`RECETTE-215-r2-2026-09-26.md`](s9-05-03-215-216-grid-tv-2026-09-26/RECETTE-215-r2-2026-09-26.md) | [`01-cell-zoom-12h.png`](s9-05-03-215-216-grid-tv-2026-09-26/01-cell-zoom-12h.png), [`02-cell-zoom-24h.png`](s9-05-03-215-216-grid-tv-2026-09-26/02-cell-zoom-24h.png), [`04-guide-header.png`](s9-05-03-215-216-grid-tv-2026-09-26/04-guide-header.png), logs `11-daynav-lumolog.txt` / `15-cellnav-lumolog.txt` |
| [`s9-05-04-mobile-channel-day-2026-09-26/`](s9-05-04-mobile-channel-day-2026-09-26/) | S9-05-04 — journée d'une chaîne **Android mobile** + correctif Retour système `f77930e` (GD-13, GD-03, critère réseau, cas limites) | `feat/S9-05-04-mobile-channel-day` @ `f77930e` (base `1d70871`) | ✅ **conforme** (7/7 ; « Retour pendant le chargement » observé au segment précédent, non re-joué) | [`RECETTE-S9-05-04-2026-09-26.md`](s9-05-04-mobile-channel-day-2026-09-26/RECETTE-S9-05-04-2026-09-26.md) | [`gd13-md5.txt`](s9-05-04-mobile-channel-day-2026-09-26/gd13-md5.txt) (position identique, md5 `a85be296…`), [`gd13-after-system-back.png`](s9-05-04-mobile-channel-day-2026-09-26/gd13-after-system-back.png) (Retour système), [`crossing-01-today-2330-clipped.png`](s9-05-04-mobile-channel-day-2026-09-26/crossing-01-today-2330-clipped.png) + [`crossing-02-tomorrow-0000-clipped.png`](s9-05-04-mobile-channel-day-2026-09-26/crossing-02-tomorrow-0000-clipped.png) (nuit à cheval), [`gd03-after-switch.png`](s9-05-04-mobile-channel-day-2026-09-26/gd03-after-switch.png) |

### Notes de lecture

- **S9-05-04** : recette faite sur l'AVD **Pixel_10** (APK debug md5 `4a35c25c2dc6512199c8e1991ee4767c`, build `f77930e`), comptage réseau via `LumoHttp`. Le banc a été remis à la playlist **canonique 5 chaînes** après la passe (la liste avait été allongée à 30 chaînes pour GD-13). La **fixture XMLTV** de la passe (`guide-crossing.xml`, dates relatives + programme `23:30 → 00:30` pour `bench.1`) est versionnée dans le passage : le harnais S9-07-03 n'existe pas encore. **Non prouvé sur appareil** : la fenêtre « réponse en vol » de GD-03 (API locale trop rapide) — couverte par le code et `LiveSourceSwitchTest`. **Reste en place** : la source QA secondaire « Banc QA secondaire » (30 chaînes, état du test GD-03), non supprimée.
- **#214** : la passe finale a été faite sur la branche corrigée (`ed3680a`)
  avec la sonde DOM `probe-214-visual.mjs` (`gridMinWidth0 = true`, plus de
  débordement de page) et la capture `g214-new-01-guide-1440.png`. La sortie
  console de cette sonde n'a pas été journalisée ; le rouge/avant est mesuré
  dans `web-pass-214-main/main-214-layout.log` et `branch-214.log`.
- **#215/#216** : `RECETTE-215-r2` est postérieure à `RECETTE-215-216`
  (`37fe019`) ; seule la première fait foi.
- **Sécurité** : les mots de passe des comptes de test QA ont été retirés des
  scripts et du journal avant versionnement (remplacés par `process.env.QA_PASS`
  / `<redacted>`). Ces comptes restent locaux ; à faire tourner côté API.

## Fichiers écartés (essais intermédiaires remplacés)

Rien n'est supprimé en silence : chaque écart est listé ici avec sa raison.

| Écarté | Taille | Raison |
|---|---|---|
| `qa215/` | 2,3 Mio | Passe #215/#216 sur `37fe019` ; remplacée par `qa215b` (`7b15dfd`), qui re-vérifie aussi #216. |
| `tv-pass-215-216/`, `tv-pass-215-216-new/` | 1,7 Mio | Passes TV intermédiaires avant `7b15dfd` ; remplacées par `qa215b`. |
| `tv-pass/` | 2,3 Mio | Première passe TV (20 PNG) sur `main` ; remplacée par `qa215b`. |
| `web-pass/` (les 12 PNG) | ≈ 900 Kio | Première passe web ; remplacée par les passes #213/#214. Seuls `web-cold-check.mjs` et `web-cold-main.log` sont conservés (dans le passage #213). |
| `mobile-pass/mob02-*.png`, `mob03-*.png` | ≈ 330 Kio | Non décisives ; `mob01-cold-home.png` + script + log conservés. |
| `tv-app-01.png`, `tv-home.png`, `tv-app-02/03`, `nav-01..03`, `live-01/02`, `tv-pass$1.png` | ≈ 3 Mio | Captures brutes de la première passe, remplacées par les captures de verdict. |
| `web-pass-214/g214-01/02-guide-1440.png`, `web-pass-214-main/g214-03-doc-scrolled.png` | ≈ 250 Kio | Doublons bit-à-bit (`g214-01` = `g214-02`). |
| `qa215/RECETTE-215-216-2026-09-26.md` | 5,6 Kio | Verdict intermédiaire #216 ; la version finale est `RECETTE-215-r2`. |
| `qa217/e2e213-replay-2.log` (déplacé) | — | Rejeu #213 : classé dans le passage #213, pas dupliqué dans #217. |
| `qa217-mob-00-current.png`, `qa217-mob-01-home.png`, `qa217-tv-01-current.png` | ≈ 90 Kio | Captures d'état avant recette, non décisives. |
| `qa217-tv-02-activation.xml` | 12 Kio | Écran d'appairage TV (code d'activation) : écarté par principe, sans valeur pour le verdict. |
