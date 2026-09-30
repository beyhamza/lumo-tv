# S9-07 — passage automatisé (reprise après coupure) — 2026-09-30

Rapport QA. La campagne S9-07 lancée le 27/09 a été **coupée par un dépassement
de temps** : les specs et journaux étaient sur disque, rien n'était commité. Ce
passage **reprend ce qui a réellement tourné** ; il ne relance rien et n'invente
aucun verdict. Les cas qui n'ont pas tourné sont marqués **non joué** avec leur
raison.

- **Base** : `origin/main` @ **`3eaa731`**, branche `qa/S9-07-automatise`
  (portée par merge sans force depuis `ae8ba40`).
- **Protocole de référence** : [`docs/releases/0.2.0/s9-07-recette.md`](../../s9-07-recette.md)
  (§4 cas GD-01→GD-14, §5 preuve réseau, §7 critère de sortie).
- **Aucun code produit touché.** Les seuls fichiers ajoutés par ce passage sont
  les trois specs S9-07, les journaux tels qu'exécutés et les deux scripts de
  banc (`fault-proxy.mjs`, `recreate-api-epg-logging.sh`).
- **Règle tenue** : aucune case non exécutée n'est comptée verte ; un « conforme
  par lecture » n'est pas un verdict de recette. Le critère de sortie §7 (« aucun
  cas vu sur émulateur tenu pour vert ») n'est **pas** atteint : aucun matériel
  réel (téléphone, TV/box + télécommande) n'était disponible.
- **Hors périmètre** : les cas `SR-01`→`SR-15` (scénarios de recherche) vivent
  dans `docs/design/0.2.0/search-interactions.md` et relèvent du sprint **S10**,
  non commencé. Ils ne sont donc pas dans ce passage.

## Environnement

| Élément | Valeur |
|---|---|
| Ancre banc A (guide/FR-EN/réseau) | `2026-09-30T11:38:59Z` (`logs/anchor.txt`, `logs/phase0-provision.log`) |
| Ancre banc B (minuit GD-12) | `2026-09-30T21:50:00Z` (`logs/phase0-provision-midnight.log`) |
| Banc | `lumo-e2e-bench` (compose `-p lumo-e2e`), 9 URLs XMLTV/playlist en 200 (`logs/phase0-provision.log`) |
| API web e2e | `lumo-e2e-api` 18080, profil `dev,epg-logging` (`logs/phase0-recreate-api-logging.log`) |
| Pile web | build prod Playwright sur 3100 (`E2E_KEEP_STACK=1`) |
| Android | émulateur **`Television_1080p`** (AVD) - API 16, `logs/emulator-boot.log` — **pas** de matériel réel |
| Panne EPG web | proxy `503` sur `/epg`, `18082 → 18080` (`logs/fault-proxy.log`) |

## 1. Verdict par cas

Chaque verdict est adossé au journal nommé. « Commande exacte » est la ligne
imprimée en tête du journal.

| Cas | Surface / méthode | Commande exacte | Journal | Verdict |
|---|---|---|---|---|
| **GD-07** (fin de programme, fiche ouverte, focus) | web SSR, horloge contrôlée (`QA-06-04-04`) | `playwright test "--project=journey" "s9-06-04-web.journey.spec.ts" "s9-06-04-partial.journey.spec.ts" "guide-grid-density.journey.spec.ts" "s9-06-04-past-source.journey.spec.ts"` | [`logs/web-normal-gd07-08-11.log`](logs/web-normal-gd07-08-11.log) | ✅ **vert** (rejeu web) |
| **GD-08** (programme futur devenu courant, sans vol de focus) | web SSR, horloge contrôlée (`QA-06-04-05`) | idem | [`logs/web-normal-gd07-08-11.log`](logs/web-normal-gd07-08-11.log) | ✅ **vert** (rejeu web) |
| **GD-11** (guide partiel : créneau vide annoncé, aucune erreur) | web (`QA-06-04-08`) | idem | [`logs/web-normal-gd07-08-11.log`](logs/web-normal-gd07-08-11.log) | ✅ **vert** (partie web) |
| **GD-10** — erreur initiale | web, proxy `/epg` `503` (`QA-06-04-06`) | `playwright test "--project=journey" "s9-06-04-fault.journey.spec.ts"` | [`logs/web-fault-gd10.log`](logs/web-fault-gd10.log) | ✅ **vert** (partie web) |
| **GD-10** — erreur initiale | Android TV, instrumenté, émulateur 1080p | `./gradlew :feature:live:connectedDebugAndroidTest` (depuis `apps/android`) | [`logs/android-instrumented-gd10-focus.log`](logs/android-instrumented-gd10-focus.log) | ✅ **vert sur émulateur** — `GuideInitialErrorTvTest` 4/4 (`theInitialErrorScreenPaintsItsTitleBodyAndBothActions`, `oneRightFromTheRailReachesTheRetryAction`, `aRetryThatFallsBackToTheInitialErrorKeepsTheFocusOnRetry`, `theSearchEmptyActionsPaintInTheChannelsArea`) + `ProgrammeSheetTvFocusTest` 4/4. **Pas** du matériel réel |
| **GD-12** — minuit | web SSR sous `LUMO_NOW` = ancre B | `playwright test "--project=journey" "s9-07-gd12-midnight.journey.spec.ts"` | [`logs/web-gd12-midnight.log`](logs/web-gd12-midnight.log) | ✅ **vert** (minuit seulement ; le 3/3 inclut le setup) |
| **GD-14** — FR/EN et clavier web | web, 2 langues + Tab/Entrée/Échap | `playwright test "--project=journey" "s9-07-gd14-locales-keyboard.journey.spec.ts"` | [`logs/web-gd14-locales-keyboard.log`](logs/web-gd14-locales-keyboard.log) | ✅ **vert** (web : FR, EN sans français résiduel, horaires 24 h/12 h, clavier) |
| **§5.2 / I-4** — volume réseau web borné | web 3 / 50 / 100 chaînes, comptage côté API | `playwright test "--project=journey" "s9-07-network-bound.journey.spec.ts"` | [`logs/web-network-bound-i4.log`](logs/web-network-bound-i4.log) | ✅ **vert** — `[I-4] 3 chaînes → 1 appel`, `50 → 1`, `100 → 1` |
| **§5.1** — contrat serveur EPG groupé | API, intégration | `./gradlew test --tests *GroupedEpgIntegrationTest*` (depuis `apps/api`) | [`logs/phase0-api-grouped-epg.log`](logs/phase0-api-grouped-epg.log) | ✅ **vert** — 16 tests PASSED |
| **§5.1** — client Android borné (split < N) | Android, unitaire | `./gradlew :core:data:testDebugUnitTest --tests *EpgRepositoryTest*` (depuis `apps/android`) | [`logs/phase0-android-epgrepository.log`](logs/phase0-android-epgrepository.log) | ✅ **vert** — 11 tests |
| §5.1 — garde-fous guide/Direct/TV (focus, états, reprise) | Android, unitaire | `./gradlew :feature:live:testDebugUnitTest` (depuis `apps/android`) | [`logs/android-feature-live-unit.log`](logs/android-feature-live-unit.log) | ✅ **vert** — 12 classes / 119 tests / 0 échec |

> Les résultats par test des suites Android sont lisibles dans les XML JUnit du
> build (noms exacts) ; les journaux Gradle ci-dessus prouvent l'exécution
> (`BUILD SUCCESSFUL`) et le compte.

## 2. Cas non joués (avec la raison)

Aucun de ces cas n'est « vert par déduction ».

| Cas | Raison du non-jeu |
|---|---|
| **GD-01** (Chaînes → Guide conserve recherche/filtre) | Passe manuelle intersurfaces non exécutée ; aucun test automatisé ne la couvre à ce jour. |
| **GD-02** (quitter/rouvrir, accès d'accueil priment) | Idem GD-01. |
| **GD-03** (changement de source pendant une requête) | Fenêtre « réponse en vol » **non reprochable localement** (l'API locale répond trop vite) — couvert par le code et `LiveSourceSwitchTest` (unitaire), pas par un test d'écran. |
| **GD-04, GD-05, GD-06** (croix D-pad, durées, lacune, bords) | Exigent une **télécommande réelle** (protocole §4 et §6.3) ; absent. |
| **GD-09** (retour lecteur après MAJ du guide) | Passe manuelle non exécutée ; aucune automatisation d'écran sur ce passage. |
| **GD-10 — erreur avec données** (Android, 2ᵉ partie) | Non joué : il faut un guide déjà chargé **puis** une panne ; seul le cas « erreur initiale » a tourné. La partie web « erreur avec données » (I-5) n'a pas été rejouée ici. |
| **GD-12 — changement d'heure** (25/10/2026, `Europe/Paris`) | **Non automatisable ici** : le parseur XMLTV de l'API ne retient que `[maintenant − 1 j, maintenant + 3 j]` (`XmltvStreamParser`), un banc ancré à cette date n'importerait aucun programme. Repli : unitaire `src/lib/epg/day-window.test.ts` (jour de 23 h / 25 h) ; **cas matériel** (voir `s9-07-manuel.md`). |
| **GD-12 — fuseau appareil ≠ serveur** | Exige un appareil réglé sur un autre fuseau (§6.4) ; écart web connu à consigner, pas à maquiller. |
| **GD-13** (mobile : fiche → journée → En ce moment) | Passe manuelle Android mobile non exécutée. |
| **GD-14 — D-pad réel, troncature 320 px, glissement clavier** | Résidu matériel/visuel du cas ; seul le volet web FR/EN + Tab/Entrée/Échap a tourné. |
| **Android mobile réel / TV réelle** | Aucun téléphone ni box + télécommande disponible. L'émulateur `Television_1080p` est un **repli explicitement non « vert »** (§7 du protocole). |
| **SR-01 → SR-15** | Hors S9-07 : scénarios de recherche du sprint **S10**, non commencé. |

## 3. Éléments observés lors du rejeu

- **1 spec sautée** dans `web-normal` : `guide-grid-density.journey.spec.ts` →
  « la grille défile dans sa cellule, la page ne défile pas ». Ce n'est **pas**
  un cas GD-01→GD-14 (régression `BUG-S9-05-02-01`) ; elle se saute parce que
  la source qu'elle crée n'a **pas** d'`epg_url`, donc `EpgGrid` ne dessine rien.
  Le commentaire de la spec justifie ce saut par « le banc ne sert pas d'XMLTV
  (S9-07-03) » — **obsolète** depuis la fusion du harnais, mais la cause réelle
  (source sans guide) demeure. **Observation hors critères**, pas un échec S9-07.
- **GD-07/08/11 web** : ce sont des **rejeux** des specs `s9-06-04-*` déjà
  recettées le 26/09, ici rejouées sur `origin/main` @ `3eaa731` ; 12 passées,
  1 sautée.
- **Ancre GD-12** : le banc a été **re-provisionné** sur `2026-09-30T21:50:00Z`
  (`logs/phase0-provision-midnight.log`) juste avant la spec minuit ; les autres
  specs ont tourné sous l'ancre A `2026-09-30T11:38:59Z`.

## 4. Ce que ce passage ne prouve pas

- Aucun cas sur **matériel réel** (téléphone Android, TV/box + télécommande).
- Aucun cas **GD-01/02/03/09/13** joué, ni la croix D-pad.
- **GD-10 « erreur avec données »**, **GD-11 guide ancien**, **GD-12 changement
  d'heure** : non joués.

Le critère de sortie §7 de la recette reste donc **non atteint**. Le résidu
matériel est listé dans [`docs/releases/0.2.0/s9-07-manuel.md`](../../s9-07-manuel.md).

## 5. Traçabilité

| Sous-issue Plane | Contenu | Livrable | État |
|---|---|---|---|
| S9-07-01 | Protocole GD-01→GD-14, horloge, fixtures, preuve réseau | `s9-07-recette.md` | protocole écrit ; **automatisé partiel** (ce rapport), **matériel non joué** |
| S9-07-02 | Annexe FR/EN, clavier, D-pad, fuseau | §6 de `s9-07-recette.md` | web FR/EN + clavier ✅ ; D-pad + 320 px + fuseau **non joués** |

Aucune écriture Plane n'a été faite depuis ce passage. Aucun `BUG-*` nouveau :
ce passage n'a relevé aucun défaut produit (l'unique observation est un saut de
test à commentaire obsolète, hors critères).
