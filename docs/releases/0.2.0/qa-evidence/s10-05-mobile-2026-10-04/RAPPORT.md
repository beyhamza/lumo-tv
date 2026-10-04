# S10-05 — Phase 3 : recette mobile

- Date du run : 2026-10-04.
- Branche de recette : `qa/S10-05-recette` @ `25eb92d` (base `dc0f9d2`).
- Surface : Android **mobile** debug `tv.lumo.android.debug` `0.1.0-debug`,
  AVD `Pixel_10` (`sdk_gphone64_x86_64`, API 37, `emulator-5556`).
  APK md5 `06951767eb6f7a6864799e04d3a19241`
  (`apps/android/app-mobile/build/outputs/apk/debug/app-mobile-debug.apk`).
- Source active : « Banc S10-05 mobile »,
  `kind=M3U_URL`, `m3u_url=http://host.docker.internal:18081/mixed.m3u`,
  état `READY`, id `b3851c0b-3dd6-4e47-b1c2-40b122da29a7`.
  Le banc `mixed.m3u` porte des **chaînes et des films, aucune série**.
- Compte : `qa.s1005.mobile@test.example` (mot de passe en `QA_PASS`, non
  versionné ; voir `provision-mobile.mjs`).
- Objet : **SR-04, SR-10, SR-11, SR-14** sur mobile + **non-régression S10-03**
  (retour de fiche : texte / filtre / position conservés).

> Statuts : ✅ CONFORME · ❌ NON CONFORME · ⏸️ NON EXÉCUTÉ · ➖ NON APPLICABLE.

## Commandes exactes

```bash
# Construction et installation (worktree C:/dev/lumo-tv-qa-s1005)
cd apps/android && ./gradlew :app-mobile:assembleDebug          # BUILD SUCCESSFUL
adb -s emulator-5556 install -r \
  'C:/dev/lumo-tv-qa-s1005/apps/android/app-mobile/build/outputs/apk/debug/app-mobile-debug.apk'
adb -s emulator-5556 reverse tcp:8080 tcp:8080
adb -s emulator-5556 shell pm clear tv.lumo.android.debug
adb -s emulator-5556 shell monkey -p tv.lumo.android.debug -c android.intent.category.LAUNCHER 1
# puis connexion e-mail / mot de passe dans l'écran « Welcome back »

# Provisionnement du compte + source
QA_PASS=… node provision-mobile.mjs        # login 401 -> register 201 -> source READY

# Injection de panne (SR-10) : 503 sur tout chemin /vod
PORT=18082 UPSTREAM=http://127.0.0.1:8080 node search-fault-proxy.mjs
adb -s emulator-5556 reverse --remove tcp:8080
adb -s emulator-5556 reverse tcp:8080 tcp:18082

# Relevés d'écran
MSYS_NO_PATHCONV=1 adb -s emulator-5556 shell uiautomator dump /sdcard/ui.xml
MSYS_NO_PATHCONV=1 adb -s emulator-5556 pull /sdcard/ui.xml <hôte>
```

## SR-04 — espaces seuls ou effacement : ✅ CONFORME

Critère : invitation à rechercher, **aucun chargement du catalogue complet**.

- **Champ vide** (arrivée sur Recherche) : `Search your channels, films and
  series.`, aucun résultat, aucun rafraîchissement de catalogue
  (`focus` : champ vide, onglets `All / Channels / Films`).
- **Effacement** (glyphe `✕`, `content-desc="Clear the search"`) : retour à
  l'invitation, aucun catalogue.
- **Espaces seuls** : `EditText` = `"   "`, écran = invitation
  (`Search your channels, films and series.`), aucune rangée de résultats →
  la requête est bien `trim()`-ée à vide côté ViewModel. Capture :
  `sr04-spaces.png`.

## SR-11 — type absent puis type présent sans correspondance : ✅ CONFORME

Critère : type absent → **filtre absent** ; type présent sans correspondance →
**filtre présent avec état sans résultat**.

- Requête `Voyage` : onglets `All / Channels / Films`, **`Series` absent**
  (la source ne porte aucune série — « type absent »).
- `Channels` **présent** avec « No result for “Voyage”. », tandis que la section
  `Films` rend une carte (`Le Voyage`) : un type peut être vide sans masquer les
  autres. Capture : `sr11-voyage.png`.
- Réserve : l'absence du type `Series` se lit sur le rendu de l'onglet, sans
  trace API jointe (même réserve que la phase TV).

## SR-10 — une section échoue, les autres réussissent : ✅ CONFORME

Critère : sections réussies conservées pour la même saisie, **réessai local**.

- Panne armée : proxy → `503` sur tout chemin `/vod`, relais du reste vers l'API
  dev `8080`. Journal `fault-proxy.log` :
  `503 /v1/vod`, `503 /v1/sources/<id>/vod?page=0&size=1`,
  `503 /v1/sources/<id>/vod?q=a&page=0&size=4`.
- Requête `a` : la section **`Films`** affiche « The search did not complete. »
  + bouton **`Try again`**, tandis que la section `Channels` **conserve ses
  résultats** (`Le Dernier Quai`, `Chaîne 01 FHD`, `Chaîne 02`). Aucun état
  « aucun résultat » global. Capture : `sr10-partial-failure.png`.
- **Réessai local** : un appui sur `Try again` n'émet **qu'un** appel, celui de
  la section en échec (`503 …/vod?q=a&page=0&size=4`) — aucune requête chaînes
  n'est rejouée. Capture : `sr10-local-retry.png`.

## SR-14 — catalogue local consultable, puis aucun catalogue local : ✅ CONFORME (réserve)

Critère : recherche locale **honnête** si disponible ; **sinon erreur explicite
et réessai**.

- Appareil mis hors ligne (`adb reverse --remove tcp:8080`), requête `Voyage`
  puis `zzzq` : bannière **« Saved results — they may be out of date. »**, la
  section `Channels` répond **depuis le cache local** (« No result for
  “Voyage”/“zzzq”. »), et les sections **non cachées** (`Films`, `Series`)
  affichent « The search did not complete. » + **`Try again`** — l'erreur
  explicite et le réessai exigés. Captures : `sr14-offline-cached.png`,
  `sr14-offline-nomatch.png`.
- L'accueil hors ligne confirme le repli cache (« Lumo could not be reached /
  This catalogue may be out of date. », `sr14-home-offline.png`), et l'onglet
  Explore affiche, quand rien n'est stocké, le message dédié « Your sources
  could not be fetched, and nothing of this catalogue is stored on this
  device. »
- **Réserve** : l'état « **aucun** catalogue local » a été obtenu **section par
  section** (Films/Series non cachés) ; un état globalement sans cache sur
  l'écran Recherche (compte n'ayant jamais synchronisé, hors ligne) n'a pas été
  atteint sans effacer la session. Non bloquant.

## Non-régression S10-03 — retour de fiche : ✅ CONFORME

Critère : au retour, texte / filtre / page / **position** conservés (compte et
source inchangés).

- Saisie `a` → filtre **Films** sélectionné → ouverture de la fiche
  `La Traversée` (`s1003-before-open.png`, `s1003-fiche.png`).
- Retour (BACK) : la barre de recherche garde `a`, l'onglet **`Films`** reste
  actif (on voit la page Films), et **les mêmes rangées réapparaissent aux mêmes
  ordonnées** qu'avant l'ouverture (comparaison des `bounds` du dump :
  `Le Voyage [207,969]`, `La Traversée [696,969]`, `Les Falaises [191,1807]`,
  `Chaîne 06 en continu [635,1807]`). Capture : `s1003-after-back.png`.
- **Réserve** : la « page » (`page=1` et suivantes) n'a pas pu être exercée —
  `mixed.m3u` ne contient que 6 entrées, aucune pagination. La position est
  vérifiée par l'identité des rangées/ordonnées, pas par un défilement
  enregistré (le `swipe` de contrôle n'a pas déplacé la liste, le contenu
  tenant dans l'écran).

## ⚠️ Observations hors critères

- **Onglets hors ligne** : hors ligne, l'écran Recherche affiche les **quatre**
  onglets `All / Channels / Films / Series`, alors qu'en ligne `Series` est absent
  (présence non connue = tous les onglets). Écart cosmétique entre les deux
  états, sans critère SR associé — à trancher par le PO si l'on veut la parité.
- **Classification de la source** : dans `mixed.m3u`, des entrées sans `tvg-id`
  (`Le Voyage`, `La Traversée`, `Les Falaises`, `Chaîne 06 en continu`) sont
  classées **Films** ; `Le Dernier Quai` apparaît sous `Channels` dans la vue
  `All`. Particularité du banc, pas un défaut produit.

## Non exécuté / non applicable

- **SR-14, branche « aucun catalogue local » globale** : voir réserve ci-dessus.
- **S10-03, pagination** : catalogue du banc trop court (aucune page > 0).
- SR-01/02/03/05/06/07/08/09/12/13/15 : hors du périmètre de cette phase
  (couverts ou listés dans les phases web/TV, ou non applicables mobile).

## Fichiers

| Fichier | Contenu |
|---|---|
| `provision-mobile.mjs` | préparation compte + source (mot de passe en `QA_PASS`) |
| `search-fault-proxy.mjs` | proxy 503 sur `/vod` (SR-10) |
| `fault-proxy.log` | appels fautés pendant SR-10 |
| `sr04-spaces.png` | SR-04 espaces seuls |
| `sr11-voyage.png` | SR-11 type absent / présent sans correspondance |
| `sr10-partial-failure.png` | SR-10 section Films en échec, Chaînes conservées |
| `sr10-local-retry.png` | SR-10 après `Try again` (réessai local) |
| `sr14-home-offline.png` | accueil hors ligne (repli cache) |
| `sr14-offline-cached.png` | SR-14 section Chaînes depuis le cache |
| `sr14-offline-nomatch.png` | SR-14 requête sans correspondance locale |
| `s1003-before-open.png` | S10-03 avant ouverture de fiche |
| `s1003-fiche.png` | S10-03 fiche `La Traversée` |
| `s1003-after-back.png` | S10-03 au retour (texte/filtre/position) |
