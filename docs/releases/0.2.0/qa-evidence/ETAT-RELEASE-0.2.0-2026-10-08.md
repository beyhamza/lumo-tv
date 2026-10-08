# État réel de recette — release 0.2.0

- Date : **2026-10-08**
- Auteur : **@QA**
- Base examinée : `main` @ `73176d8` ; candidate `release/0.2.0` @ `a1ac916`
  (poussée sur `origin`), seul tag existant `v0.1.0`.
- Méthode : lecture du dépôt (`docs/backlog/`, `docs/releases/0.2.0/`,
  `qa-evidence/`, migrations, CI) et des preuves **versionnées** ; CI relue via
  `api.github.com` le 2026-10-08.
- Règle tenue : **aucune case verte sans chemin de preuve versionné** sous
  `docs/releases/0.2.0/qa-evidence/`. Un cas non rejoué est `NON JOUÉ` avec sa
  raison. Un « vu sur émulateur » n'est jamais compté vert (protocole S9-07 §7).
- Portée de ce document : les lignes R020 demandées par le @PO. Il **ne remplace
  pas** [`acceptance.md`](../acceptance.md) (matrice 16 lignes) ni
  [`INDEX.md`](INDEX.md).

Statuts : ✅ **CONFORME** (preuve versionnée) · ⚠️ **PARTIEL** · ❌ **ROUGE** ·
⏸️ **NON JOUÉ** · ➖ **NON APPLICABLE**.

---

## 1. Questions tranchées

| Question | Verdict | Raison / preuve |
|---|---|---|
| **S8-07 a-t-elle été jouée ?** | ❌ **NON JOUÉE** | Le plan existe (`docs/backlog/sprint-08-recette.md`, rédigé le 24/09) mais `docs/backlog/sprint-08.md` porte toujours « Statut : **en cours** » et aucun rapport de session versionné n'existe. L'unique passe existante est la soirée **émulateur TV du 20/09**, marquée dans `sprint-08.md` : « Ce n'est pas la recette de S8-07 : ni télécommande réelle, ni téléphone, ni navigateur. » |
| **S9-07 a-t-elle été jouée ?** | ❌ **NON JOUÉE** (intersurfaces réelles) | Décision du 27/09 (Hamza sans créneau matériel). Seul passage versionné : **automatisé partiel** `s9-07-automatise-2026-09-27/RAPPORT.md` (GD-07/08/11 + GD-10 initiale web, GD-12 minuit, GD-14 FR/EN web, bornage réseau, contrat API) ; GD-01/02/03/04/05/06/09/10-avec-données/11-ancien/13 et les deux volets matériels **non joués**. Le critère de sortie §7 (« aucun cas vu sur émulateur tenu pour vert ») reste **non atteint**. Résidu listé dans `docs/releases/0.2.0/s9-07-manuel.md`. |
| **S10-05 — état réel par surface** | ⚠️ **PARTIEL** (voir tableau R020-05) | Web : conforme sur le périmètre joué (`s10-05-web-2026-10-04/`). Mobile : conforme sur le périmètre joué (`s10-05-mobile-2026-10-04/`). TV : conforme sur le périmètre joué — **`SR-10` réessai local ✅** (`s10-05-tv-2026-10-04/RAPPORT-dpad.md` + preuve OkHttp `repro/SR10-okhttp-logcat.txt`) — et rejoue D-pad / focus / `SR-12` (`s10-05-dpad-rejoue-2026-10-07/`, `s10-05-03-sr12-2026-10-07/`), mais **« deux sources » jamais jouées**. |

### Écarts QA relevés à cette lecture (à ne pas maquiller)

1. **`SR-10` côté TV — renvoi de backlog corrigé, cas `✅ CONFORME`.** Le résumé de
   `docs/backlog/sprint-10.md` attribuait « ✅ SR-10 (réessai local) » à la passe du
   **07/10** ; c'était un **renvoi faux**, corrigé sur `main` @ `e056aa3` (retiré de
   l'écart lui-même, la preuve existe bel et bien ailleurs). La preuve est versionnée
   dans la passe TV du **04/10** : `s10-05-tv-2026-10-04/RAPPORT-dpad.md` §2
   « SR-10 TV — ✅ CONFORME », appuyé sur la preuve OkHttp
   `repro/SR10-okhttp-logcat.txt` (le réessai ne rejoue que `/vod`, aucun appel
   `channels`) et les dumps `dpad/sr10-tv-armed.xml`,
   `dpad/sr10-tv-tryagain-focused.xml`, `dpad/sr10-tv-after-retry.xml`. La ligne de
   `INDEX.md` pour ce rapport porte déjà « ✅ SR-10 ». **`SR-10` TV = CONFORME**,
   pas `NON JOUÉ`.
2. **`SR-01/02/03` (saisie rapide, debounce 350 ms)** : classés « N/A web — couvert
   Android » (`s10-05-web-2026-10-04/RAPPORT.md`), mais la phase **mobile** les
   exclut (« hors du périmètre de cette phase ») et la phase **TV** aussi
   (« non couverts »). **Aucune preuve versionnée** ne les couvre : la « saisie
   rapide » de `S10-05` n'est pas jouée.
3. **`SR-09` (source changée pendant la saisie)** : `non joué` web, non cité par
   les phases mobile/TV. Non couvert.
4. **« deux sources »** du libellé `S10-05` : toutes les passes S10-05 utilisent
   **une seule source active** (mobile : « Banc S10-05 mobile » ; TV : « Banc
   S10-05 TV »). Non joué.

---

## 2. Lignes R020 demandées

Chemins relatifs à `docs/releases/0.2.0/qa-evidence/` sauf mention contraire.

| ID | Parcours | Lots | Surface | État | Date | Preuve versionnée |
|---|---|---|---|---|---|---|
| **R020-01** | Email, session persistante, déconnexion, activation TV, absence de fuite entre comptes | S8/S14 | web + Android mobile + TV | ⏸️ **NON JOUÉ** | — | **Aucune.** L'activation TV et la session apparaissent en **support** de passes S9/S10, jamais comme cas recetté. Aucun dossier d'evidence auth/session. |
| **R020-02** | Source active locale, accueil et navigation, retour/focus | S8/S12 | 3 surfaces | ⏸️ **NON JOUÉ** (fragments) | — | Fragments versionnés seulement : accueil à froid web `s9-04-07-cold-home-web-2026-09-26/RECETTE-FIXES-2026-09-26.md`, accueil à froid Android/TV `s9-04-04-217-cold-entries-android-2026-09-26/RECETTE-217-2026-09-26.md`, grilles/focus `s9-05-03-215-216-grid-tv-2026-09-26/RECETTE-215-r2-2026-09-26.md`, journée mobile `s9-05-04-mobile-channel-day-2026-09-26/RECETTE-S9-05-04-2026-09-26.md`. **Aucune recette du parcours vertical S8.** |
| **R020-03** | Sources : ajout, états, ancienne donnée pendant sync, retry, suppression et remplacement | S8/S11 | 3 surfaces | ⏸️ **NON JOUÉ** | — | **Aucune.** Plan `sprint-08-recette.md` §5 (R-420→R-428), jamais exécuté. |
| **R020-04** | Direct/Guide, filtres, dates, fiche, absence/ancienneté/erreurs, lecture groupée | S9 | TV + web + mobile | ⚠️ **PARTIEL** | 26–30/09 | ✅ automatisé web + Android **émulateur** : `s9-07-automatise-2026-09-27/RAPPORT.md` (GD-07/08/11, GD-10 initiale, GD-12 minuit, GD-14 web, I-4). ✅ story-level : `s9-05-02-214-grid-web-2026-09-26/`, `s9-05-03-215-216-grid-tv-2026-09-26/`, `s9-05-04-mobile-channel-day-2026-09-26/`, `s9-06-04-web-2026-09-26/`. ⏸️ **Non joués** : GD-01/02/03/09/10-avec-données/11-ancien/13, D-pad télécommande réelle, FR/EN visuel, volume réseau Android à l'écran (`s9-07-manuel.md`). |
| **R020-05** | Recherche par type, saisie rapide, source changée, erreur partielle et retour | S10 | 3 surfaces | ⚠️ **PARTIEL** | 04–07/10 | ✅ web `s10-05-web-2026-10-04/RAPPORT.md` (SR-04/05/06/10/11-absent/12/14) ; ✅ mobile `s10-05-mobile-2026-10-04/RAPPORT.md` (SR-04/10/11/14 + S10-03) ; ✅ TV `s10-05-tv-2026-10-04/RAPPORT-dpad.md` (**SR-10** réessai local, SR-04 espaces seuls), puis `s10-05-tv-2026-10-04/RAPPORT.md` (SR-04/11/13), `s10-05-dpad-rejoue-2026-10-07/RAPPORT.md` (BUG-S10-05-01), `s10-05-03-sr12-2026-10-07/RAPPORT.md` (SR-12) ; ⚠️ contrat `q` `s10-05-q-contract-2026-10-04/RAPPORT.md`. ⏸️ **Non joués** : saisie rapide SR-01/02/03, SR-07 échec de page, SR-09 source changée, **deux sources**, 2ᵉ moitié SR-11 web. Voir §1 (écarts). |
| **R020-13** | FR/EN, clavier/focus TV, lisibilité, contenu volumineux, erreurs réseau | Tous | 3 surfaces | ⚠️ **PARTIEL** | 26–30/09 | ✅ web FR/EN + clavier + horloge : `s9-07-automatise-2026-09-27/RAPPORT.md` (GD-14, GD-12 minuit) ; ✅ lisibilité grilles web/TV : `s9-05-02-214-grid-web-2026-09-26/`, `s9-05-03-215-216-grid-tv-2026-09-26/` ; ✅ bornage réseau web + contrat API : `s9-07-automatise-2026-09-27/logs/web-network-bound-i4.log`, `phase0-api-grouped-epg.log` ; ⚠️ clavier Gboard ouvert = limite connue (`s10-05-dpad-rejoue-2026-10-07/RAPPORT.md` §2). ⏸️ **Non joués** : D-pad réel, troncature 320 px FR/EN, changement d'heure 25/10, appareil sur autre fuseau. |
| **R020-14** | Aucun contenu réel ajouté, aucun secret/URL sensible dans les preuves et logs | Tous | livrable | ⏸️ **NON JOUÉ** (audit final) | — | Pas d'audit du livrable assemblé. **Garde-fous CI partiels** : `.github/workflows/contract.yml` rejette les `.env` commités **et** les URLs Xtream réelles (`player_api.php`, `m3u_plus`), vert sur `main` @ `73176d8` ; consigne de caviardage dans `INDEX.md` (§Sécurité : mots de passe QA retirés/redacted). **Le commit candidat n'a aucun run CI** (§4). |
| **R020-15** | Pas de parcours Google/paiement non opérationnel exposé ; quotas existants non modifiés implicitement | S8/S14 | web + Android + TV | ⏸️ **NON JOUÉ** (audit final) | — | Pas de recette dédiée. **Garde-fous partiels** : S8-06 a retiré Google/Abonnement/Tarifs (livré) ; `.github/workflows/web.yml` vérifie que la zone marketing reste prérendue (« The marketing zone must still be prerendered »), vert sur `main` @ `73176d8`. La non-modification implicite des quotas n'est **pas** jouée. |
| **R020-16** | Mise à jour depuis version précédente, migrations, données conservées, catalogue et lecture toujours accessibles | S14 | API + DB + Android | ⏸️ **NON JOUÉ** — jouable en **drill local**, pas comme vraie MAJ | — | Détail §3. |

---

## 3. R020-16 — jouable aujourd'hui ?

**Réponse courte : non jouée ; techniquement jouable en drill local, pas comme mise à jour réelle de distribution.**

Ce qui existe :

- Une version précédente identifiée : tag `v0.1.0` (2026-08-26), avec 8 changelogs API
  (`apps/api/src/main/resources/db/changelog/0001..0008`).
- Migrations serveur automatiques : Liquibase au démarrage,
  `application.yml` → `change-log: classpath:db/db.changelog-master.yaml`
  (`SPRING_LIQUIBASE_ENABLED:true` par défaut) ; le jeu courant va jusqu'à `0020`.
  Un ancien schéma 0.1.0 peut donc être migré en démarrant la 0.2.0 sur la même base.
- Migrations Android **explicites** : `LumoDatabase` version `7`, `MIGRATION_1_2` →
  `MIGRATION_6_7` (`apps/android/core/database/.../LumoDatabase.kt`), sans
  `fallbackToDestructiveMigration`.

Ce qui bloque une vraie recette « mise à jour » :

- **Aucun artefact de distribution** : pas de store, pas de déploiement, pas de
  pipeline de publication ; `versionCode` reste `1` (`AndroidApplicationConventionPlugin.kt`).
  Il n'y a donc **pas de version installée antérieure** par le canal réel à mettre à jour.
- **Aucun test de migration Room** (`MigrationTestHelper` absent ; seul l'impl
  généré est présent) : la `MIGRATION_6_7` (EPG, S9-03) n'est couverte que par
  lecture de code — déjà noté dans `sprint-09.md` (« migration Room non testée
  faute de harnais »).
- **Ni la rétention des données, ni l'accès catalogue/lecture après migration ne
  sont exercés** par une preuve.

Verdict : `R020-16` = **NON JOUÉ**. Jouable dès qu'on accepte un **drill local**
(`v0.1.0` → `a1ac916`, base PostgreSQL conservée + base Room v6 → v7), mais ce
drill ne vaut pas « mise à jour depuis version précédente » tant qu'il n'existe
pas de canal de distribution.

---

## 4. Listes de sortie

### (a) Cas bloquants pour une sortie « recettée »

1. **Aucun run CI sur le commit de la candidate.** `release/0.2.0` @ `a1ac916` = **0 run**.
   Les 4 workflows (`contract`, `api`, `android`, `web`) ne se déclenchent que sur
   `pull_request`, push `main` ou `workflow_dispatch`. `main` @ `73176d8` est **4/4 vert**
   (vérifié ce jour). Le vert sur `main` ne couvre **pas** les 2 commits du bump
   (`2bab596`, `a1ac916`) ni le dossier SR-12.
2. **R020-01 non joué** — session, déconnexion, activation TV et **absence de fuite
   entre comptes** : critère de sécurité de données, aucune preuve.
3. **R020-16 non joué** — aucune recette de mise à jour ni de migration.
4. **R020-02 et R020-03 non joués** — le socle S8 (source active par appareil,
   gestion des sources) n'a jamais été recetté ; seul l'automatisé S9/S10 l'a
   effleuré.
5. **R020-04 / R020-05 / R020-13 partiels** au sens du §2 : `S9-07` intersurfaces
   non jouée (critère §7 non atteint), `S10-05` incomplet (saisie rapide, deux
   sources, SR-07/09).
6. **Trou CI sur la preuve appareil** : `android.yml` ne joue que
   `testDebugUnitTest`, jamais les `androidTest` (`SearchTvFocusTest`,
   `SearchTvReturnFocusTest`). Le vert du tag ne prouvera donc **pas** SR-12 ni le
   D-pad TV.

> `acceptance.md` §« Autorisation de sortie » : « Aucun cas obligatoire rouge **ou
> non joué** ». En l'état, **toutes** les lignes R020 demandées sont non jouées ou
> partielles → une sortie `v0.2.0` **finale** ne peut pas être déclarée « recettée ».

### (b) Cas documentables en limite connue (hors clôture)

- `BUG-S10-05-02` — onglet « Séries » affiché hors ligne (cosmétique, `Backlog`).
- Clavier Gboard ouvert : l'IME consomme `DOWN` sur TV ; le critère écrit (clavier
  fermé) est conforme (`s10-05-dpad-rejoue-2026-10-07/`).
- Web `GD-10`/`GD-11` : décisions produit documentées (pas de cache client web ;
  source active par appareil), aucun lot ouvert.
- `q` : insensibilité aux accents non promise ; casse ASCII sur Room/PostgreSQL.
- Contrat `q` : comptage **points de code** (clients) vs **UTF-16** `@Size` serveur
  au-delà de 100 points de code — **observation non exécutée** (aucun appel API),
  à ne pas présenter comme un 400 confirmé (`s10-05-q-contract-2026-10-04/`).
- SR-12 TV : retour **modélisé** (`SaveableStateProvider`, pas de vrai `NavHost`) ;
  filtre non exercé côté TV (couvert mobile).
- Harnais : flake `QA-06-04-04` (1/3 campagnes) et banc orphelin au teardown
  (`s10-bench-provisioning-fresh-2026-09-30/RAPPORT.md`) — défauts de harnais, pas
  produit.

---

## 5. Recommandation QA (recommandation, pas décision produit)

1. **Ne pas poser `v0.2.0` final aujourd'hui.** Sur le fond, `R020-01`, `R020-02`,
   `R020-03`, `R020-16` sont non joués et `R020-04/05/13` partiels : ce serait un
   `0.2.0` à recette partielle, pas un `0.2.0` recetté.
2. **Voie recommandée** : couper **`v0.2.0-rc.1`** sur `release/0.2.0` **après** un
   run CI vert obtenu via la PR `release/0.2.0` → `main`, puis jouer une **séance
   ciblée S8/S9** (pas la matrice S14 entière) et tagger `v0.2.0` final.
3. **Si Hamza assume explicitement la recette partielle** : autoriser `v0.2.0`
   quand même, mais les notes doivent dire « recette partielle, limites connues »
   et lister (a) ci-dessus — jamais « 0.2.0 recettée ».
4. **Avant tout tag** : (i) `open PR` de `release/0.2.0` → `main` et vert des 4
   workflows sur le commit taggé ; (ii) trancher les 2 versions corrigées par
   @Tech Lead (`apps/api/build.gradle.kts`, `apps/web/src/lib/auth/web-device.ts`) ;
   (iii) décider si l'on instrumente `SR-10` TV et `SR-01/02/03`.
5. **0.3.0** : reprendre S11→S13 + `US-022` ; y reporter l'audit final `R020-14/15`
   s'il n'est pas fait en 0.2.0.

---

## 6. Traçabilité

- Ce document est la réponse à la demande @PO du 2026-10-08.
- **Révision du 2026-10-08 (rework @PO)** : l'écart #1 « `SR-10` TV `NON JOUÉ` »
  était faux — la preuve versionnée existe dans la passe TV du 04/10
  (`s10-05-tv-2026-10-04/RAPPORT-dpad.md` §2), seul le renvoi de `sprint-10.md`
  était erroné (corrigé sur `main` @ `e056aa3`). `SR-10` TV passe de `NON JOUÉ` à
  `✅ CONFORME` ; le tableau §1, la ligne `R020-05` et le point (a)5 sont alignés sur
  `INDEX.md`. Chaque case verte reste adossée à un chemin de preuve versionné.
- Aucune écriture Plane dans ce passage.
- Preuves citées : voir `INDEX.md` (passages conservés) ; plans :
  `docs/backlog/sprint-08-recette.md`, `docs/releases/0.2.0/s9-07-manuel.md`,
  `docs/releases/0.2.0/acceptance.md`.
