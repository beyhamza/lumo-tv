# S10-05 — Phase 2 : recette TV (verdict final)

- Date du run : 2026-10-04 ; verdict consolidé le 2026-10-04T18:45Z.
- Branche de recette : `qa/S10-05-recette` @ `73e39bb` (base `dc0f9d2` +
  test-only `e9cfa97`/`546dbdd` pour l'ancre du banc e2e).
- Surface : Android TV debug (`tv.lumo.androidtv.debug`), AVD `Television_1080p`
  (android-tv android-36, 1920x1080), `emulator-5554`.
- Source : « Banc S10-05 TV », READY, joignant le banc e2e
  (`host.docker.internal:18081` vu depuis `lumo-api`).
- Objet : SR-04, SR-10, SR-11, SR-12, SR-13 et **le point ouvert** :
  joignabilité D-pad du bouton `feature_search_clear` de l'état global sans
  résultat.

> Statuts : ✅ CONFORME · ❌ NON CONFORME · ⏸️ NON EXÉCUTÉ (preuve insuffisante)
> · ➖ NON APPLICABLE à la surface TV.

## Le point ouvert — joignabilité D-pad depuis le champ TV : ❌ NON CONFORME

**Défaut (neuf, hors critère SR-13 littéral).** Clavier fermé, champ focalisé,
les **quatre directions D-pad sont consommées par le champ** : les onglets de
filtre, le bouton « Voir les résultats » et le bouton « Effacer »
(`feature_search_clear`) de l'état global sans résultat ne sont pas atteignables
à la télécommande. Seul **TAB** fait sortir le focus du champ. Un utilisateur
télécommande est donc bloqué sur le champ dès l'arrivée, sans pouvoir valider ni
effacer.

Preuves instrumentées (run du 2026-10-04) :

- `focus/ui-noresult.xml` et `focus/ui-after-down.xml` sont **identiques
  bit-à-bit** (md5 `96925cb9cbf7fd569f3573317181a725`) : après un DOWN clavier
  fermé, le nœud focalisé reste
  `class="android.widget.EditText" text="Voyagezzzz" bounds="[542,129][1687,232]"`.
- Dans les deux dumps, le bouton est présent et focusable mais **jamais
  focalisé** : `content-desc="Clear the search" bounds="[1694,120][1797,240]`
  `focusable="true" focused="false"`.
- `focus/ui-after-tab1.xml` : le focus passe bien à un `TextView` via TAB → TAB
  est la seule sortie ; `focus/ui-rail-settings-stray.xml` = dump de dérive de
  navigation (sans valeur de verdict).
- Captures : `s1005-20-kbclosed-down1.png` et `s1005-20-kbclosed-down2.png`
  (DOWN répétés, focus inchangé), `s1005-25-noresult-final.png` (état sans
  résultat complet, bouton visible non focalisé),
  `s1005-26-tab-button-focused.png` (sortie par TAB), `s1005-21-fresh-arrival.png`
  et `s1005-22-fresh-down.png` (arrivée puis DOWN : le DOWN part dans le clavier
  IME tant qu'il est ouvert).
- Commande utilisée : `MSYS_NO_PATHCONV=1 adb shell uiautomator dump
  /sdcard/ui.xml` puis `adb pull /sdcard/ui.xml` (émulateur `emulator-5554`).

## SR-13 — TV : champ, clavier, validation, résultats, Retour : ✅ CONFORME (critère littéral)

Critère Q9 : « Focus stable, aucune prise de focus par une réponse tardive ».

- Arrivée sur Recherche : seul le champ a le focus, invitation affichée
  (`s1005-15-arrival.png`, `s1005-21-fresh-arrival.png`).
- Frappe puis résultats : le champ conserve le focus, aucune réponse
  (résultats ou état sans résultat) ne le vole (`s1005-16-results-kb.png`,
  `s1005-17-noresult.png`, `s1005-19-afterback.png` ; BACK ferme le clavier et
  laisse le champ focalisé).

**Note de traçabilité** : le défaut D-pad ci-dessus a été groupé sous « SR-13 »
par le Tech Lead pour le dispatch. La phrase SR-13 du design porte sur la
**stabilité** du focus, pas sur l'accessibilité des contrôles. Le défaut D-pad
est donc le **bug neuf** à corriger (périmètre `fix/S10-04-tv-search-dpad`) ; la
stabilité du focus (SR-13) reste conforme.

## SR-04 — espaces seuls ou effacement : ✅ CONFORME (état vide) ; sous-cas « espaces seuls » non joué

- Requête vide : invitation « Search your channels, films and series. », **aucun
  chargement du catalogue** (`s1005-21-fresh-arrival.png`).
- Réserve : le sous-cas « espaces seuls » (chaîne d'espaces) n'a pas été piloté
  séparément ; à couvrir si l'on veut la conformité stricte du critère.

## SR-10 — une section échoue, les autres réussissent : ⏸️ NON EXÉCUTÉ (preuve insuffisante)

- Panne armée : proxy `search-fault-proxy.mjs` → `503` sur tout chemin `/vod`,
  relais du reste vers l'API dev `8080` ; `proxy.log` journalise
  `503 /v1/sources/x/vod` (une requête fautée).
- **Mais l'état rendu n'a pas été capturé** : `s1005-27-sr10.png` montre
  l'accueil (la navigation a dérivé vers Home avant la capture). On ne peut donc
  pas statuer sur « sections réussies conservées + réessai local ». À rejouer.

## SR-11 — type absent puis type présent sans correspondance : ✅ CONFORME

- Requête « Voyage » : rangée d'onglets `All / Channels / Films`, **onglet
  `Series` absent** (type absent) ; `Channels` **présent avec état sans
  résultat** « No result for “Voyage”. » ; `Films` présent avec une carte
  (`s1005-23-sr11.png`, `s1005-24-sr11-settled.png`).
- Réserve : l'absence du type est déduite de l'onglet `Series` manquant dans le
  rendu (pas de trace API jointe) ; à confirmer par le harnais si l'on veut la
  preuve stricte.

## SR-12 — retour de fiche ou lecteur : ⏸️ NON EXÉCUTÉ

Aucune capture de retour de fiche/lecteur dans ce passage. À jouer.

## SR-14 — catalogue local, puis aucun catalogue local : ➖ NON APPLICABLE à la TV

Le repli hors ligne (Room) est un comportement **mobile** ; la TV ne tient pas
de catalogue local. À couvrir en phase mobile.

## Récapitulatif TV

| Cas | Verdict | Preuve décisive |
|---|---|---|
| Point ouvert : D-pad → `feature_search_clear` | ❌ NON CONFORME | `focus/ui-noresult.xml` == `focus/ui-after-down.xml` (md5), `s1005-25-noresult-final.png` |
| SR-13 (stabilité du focus) | ✅ CONFORME | `s1005-15-arrival.png`, `s1005-16/17/19` |
| SR-04 (état vide) | ✅ CONFORME (réserve : espaces seuls non joués) | `s1005-21-fresh-arrival.png` |
| SR-10 (section en échec) | ⏸️ NON EXÉCUTÉ | `s1005-27-sr10.png` = accueil, `proxy.log` |
| SR-11 (type absent / présent vide) | ✅ CONFORME (réserve : absence du type déduite) | `s1005-23-sr11.png`, `s1005-24-sr11-settled.png` |
| SR-12 (retour de fiche/lecteur) | ⏸️ NON EXÉCUTÉ | — |
| SR-14 (catalogue local) | ➖ NON APPLICABLE (mobile) | — |

## Hors phase TV

SR-01, SR-02, SR-03, SR-05, SR-06, SR-07, SR-08, SR-09, SR-15 : non couverts par
cette phase (saisie rapide, délai, pagination, course de requêtes, changement de
source, changement de compte).

## Suites

- **Re-recette TV après correctif** : au 2026-10-04, `fix/S10-04-tv-search-dpad`
  n'existe **pas sur `origin`** (le worktree `C:/dev/lumo-tv-s1004tv` porte des
  modifications non commitées de `SearchTvScreen.kt` et du `build.gradle.kts`
  + un nouveau `androidTest/`). La re-recette attend un HEAD poussé, puis
  re-vérifie : DOWN champ→onglets→« Effacer »/« Voir les résultats », UP retour
  au champ, LEFT/RIGHT curseur, OK ouvre le clavier, OK sur « Effacer » vide et
  refocalise le champ, arrivée seul-le-champ (SR-13), restauration S10-03.
- **SR-10** à rejouer avec capture de l'état mixte (sections réussies + section
  en échec + réessai local).
- **Phase mobile** : SR-04, SR-10, SR-11, SR-14 (repli Room) + non-régression
  S10-03 — non jouée à ce stade.
