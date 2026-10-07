# S10-05 — Re-recette TV après correctif D-pad (`fix/S10-04-tv-search-dpad` @ `93fff2f`)

- Date : 2026-10-04 ; run live + instrumenté le 2026-10-04 (soir).
- Branche de recette : `qa/S10-05-tv-dpad-recette` @ `93fff2f` (parent `dc0f9d2`),
  worktree `C:/dev/lumo-tv-qa-s1005`. Aucun merge, aucune modification produit.
- Surface : Android TV debug `tv.lumo.androidtv.debug`, AVD `Television_1080p`
  (android-tv android-36, 1920x1080), `emulator-5554`.
- APK : md5 `1fc50da3c0578bc7f7d6562e92eec6f9`, **bit-à-bit identique** à
  `apps/android/app-tv/build/outputs/apk/debug/app-tv-debug.apk` construit depuis
  `93fff2f`. Le dex embarqué contient bien le correctif
  (`SearchTvScreenKt$TvSearchField...onPreviewKeyEvent`, `filtersFocus`,
  `onNavigateDown` dans `classes7.dex`) — ce n'est donc pas un build périmé.
- Objet : BUG-S10-05-01 (joignabilité D-pad), SR-10, SR-12, sous-cas
  « espaces seuls » de SR-04.

> Statuts : ✅ CONFORME · ❌ NON CONFORME · ⏸️ NON EXÉCUTÉ · ➖ NON APPLICABLE.

## Verdict

| Cas | Verdict | Preuve décisive |
|---|---|---|
| **BUG-S10-05-01** — DOWN quitte le champ, onglets / « Voir les résultats » / « Effacer » joignables | ❌ **NON CONFORME** | `SearchTvFocusTest` instrumenté **4 échecs / 5** ; repro live DOWN sans effet |
| **SR-10** — une section échoue, les autres conservées + réessai local | ✅ CONFORME | OkHttp : le réessai ne rejoue que `/vod` |
| **SR-12** — retour de fiche : contexte **et position** restitués | ❌ NON CONFORME (contexte ✅, position ❌) | repro live `sr12-*` |
| **SR-04** — espaces seuls | ✅ CONFORME | `live-sr04-spaces-only.xml` |

---

## 1. BUG-S10-05-01 — ❌ NON CONFORME (le correctif ne déplace pas le focus)

**Critère** : champ TV focalisé, clavier fermé, DOWN atteint « Voir les
résultats » (ou l'onglet de filtre) puis le bouton « Effacer » de l'état global
sans résultat ; LEFT/RIGHT laissés au curseur ; focus stable (SR-13).

**Résultat** : DOWN **ne quitte pas le champ**. Seul **TAB** fait sortir le
focus. Le correctif `onPreviewKeyEvent` de `93fff2f` est présent dans l'APK mais
ne se déclenche pas.

### 1.1 Preuve déterministe — le banc instrumenté du correctif est ROUGE

`SearchTvFocusTest` (ajouté par le correctif lui-même), lancé sur
`Television_1080p` / `emulator-5554` :

```
ANDROID_SERIAL=emulator-5554 ./gradlew :feature:search:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=tv.lumo.android.feature.search.SearchTvFocusTest
```

Résultat : **tests=5, failures=4, errors=0, skipped=0**.

| Test | Verdict |
|---|---|
| `onArrivalOnlyTheFieldHasTheFocus` | ✅ passe |
| `downFromTheFieldReachesTheFirstFilterTab` | ❌ échec |
| `downFromTheFirstFilterReachesClearTheSearch` | ❌ échec |
| `upFromTheFirstFilterReturnsToTheField` | ❌ échec |
| `okOnClearEmptiesTheQueryAndReturnsToTheField` | ❌ échec |

Échec type (`downFromTheFieldReachesTheFirstFilterTab`, `SearchTvFocusTest.kt:88`) :
après `pressKey(Key.DirectionDown)`, le nœud `Text="[All]"` existe mais reste
`Focused='false'`. XML brut : `repro/SearchTvFocusTest-Television_1080p-2026-10-04.xml`.
C'est **le banc même du correctif** qui est rouge sur `93fff2f`.

### 1.2 Preuve live — DOWN sans effet, TAB seul sort du champ

Champ focalisé (clavier fermé), `emulator-5554` :

```
adb -s emulator-5554 shell input keyevent KEYCODE_DPAD_DOWN
adb -s emulator-5554 shell input keyevent KEYCODE_DPAD_DOWN
adb -s emulator-5554 shell input keyevent KEYCODE_DPAD_DOWN
adb -s emulator-5554 shell uiautomator dump /sdcard/d.xml && adb pull ...
```

Les trois dumps sont **identiques** (md5 `e307c8c52ce9298d2db4e18f19d28a3e`,
`repro/live-dpad-down-noop-1.xml`, `-2.xml`) et gardent
`class="android.widget.EditText" focused="true" bounds="[542,121][1786,224]"`.
Même résultat avec `input dpad keyevent KEYCODE_DPAD_DOWN`.

En contraste, sur le **même écran** :

```
adb -s emulator-5554 shell input keyevent KEYCODE_TAB
```

déplace le focus sur `text="All" bounds="[488,292][656,397]"`
(`repro/live-tab-all-focused.xml`). **TAB est donc la seule sortie** — exactement
le comportement d'avant le correctif.

### 1.3 Les artefacts `dpad/` du run coupé ne sont pas reproductibles

`dpad/dpad-02-down1-filter.xml` (« All » focalisé) et
`dpad/dpad-06-down2.xml` (« Clear the search » focalisé) présentent un état
identique à celui que produit **TAB** (le seul état atteignable sur ce build).
Le run coupé par le timeout les annote « down1 »/« down2 », mais sur l'APK
courant DOWN ne quitte pas le champ. Les libellés de touches de ce run sont donc
**non fiables** pour le parcours D-pad ; la preuve déterministe
(§1.1, le banc du correctif) et la repro live (§1.2) font foi.

### 1.4 Piste pour le dev (observation QA, pas un correctif)

`onPreviewKeyEvent` est posé sur le modificateur du `BasicTextField` lui-même
(`SearchTvScreen.kt` ~l.365-379). Il ne se déclenche pas avant le gestionnaire
interne du champ. À corriger côté dev, p. ex. en lisant `DirectionDown` sur un
parent/focus-owner du champ, ou en interceptant dans un `onKeyEvent` enveloppant.

---

## 2. SR-10 TV — ✅ CONFORME

Panne armée : proxy 503 sur tout chemin `/vod` (`dpad/search-fault-proxy.mjs`),
relais du reste vers l'API dev `8080`. Requête « 01 » :

- **Sections réussies conservées** : l'accueil de recherche affiche
  `Chaîne 01 FHD` (Channels 200) **et** la section Films en échec
  « The search did not complete. » + `Try again`
  (`dpad/sr10-tv-armed.xml`, `dpad/sr10-tv-tryagain-focused.xml`,
  `dpad/sr10-tv-after-retry.xml`).
- **Réessai local** — preuve OkHttp (`LumoHttp`, `repro/SR10-okhttp-logcat.txt`) :
  - recherche initiale `19:08:11.026` → `channels?q=01&page=0&size=4` **et**
    `vod?q=01&page=0&size=4` (503) ;
  - réessai `19:08:44.978` → **uniquement** `vod?q=01&page=0&size=4` (503),
    **aucun** appel `channels` ;
  - une nouvelle recherche complète n'a lieu qu'à `19:09:47`.

Le réessai ne relance donc que la section en échec. CONFORME.

---

## 3. SR-12 TV — ❌ NON CONFORME (position non restituée)

Contrat (`SearchTvScreen.kt` l.83-86) : « un retour remet le focus sur la carte
quittée ».

Repro live (mêmes commandes que le harnais, `emulator-5554`) :

1. recherche « Voyage » puis soumission → résultats ; la carte `Le Voyage` est
   focalisée (`repro/live-sr12-results-card-focused.xml`,
   `class=View focused="true" bounds="[480,403][848,978]"`) ;
2. CENTER ouvre la fiche (`Le Voyage` / `Play` / « This source gives no synopsis
   for this film. », `repro/live-sr12-fiche.xml`) ;
3. **BACK** → l'écran **de saisie** revient : `Voyage` est conservé et le champ
   est focalisé, mais **les résultats et la carte ont disparu**
   (`repro/live-sr12-back-input.xml`, `EditText focused="true"`, `See results`
   présent).

- **Contexte** (requête `Voyage`) : ✅ restitué.
- **Position** (focus sur la carte quittée) : ❌ non restituée — le retour
  atterrit sur le champ de saisie, la liste de résultats est perdue.

---

## 4. SR-04 TV — sous-cas « espaces seuls » — ✅ CONFORME

Champ contenant trois espaces (`text="   "`) : l'écran affiche l'invitation
« Search your channels, films and series. », **aucun résultat**, et aucun appel
de recherche n'est émis (le journal OkHttp ne montre aucune requête après
l'ouverture de la fiche). `repro/live-sr04-spaces-only.xml`.

---

## 5. Limites et réserves

- Recette sur **émulateur** uniquement (`Television_1080p`), pas de matériel réel.
- En début de session live, `dumpsys input` ne listait **aucune fenêtre
  focalisée** (les touches n'étaient pas délivrées). Après
  `am force-stop` + relance de `TvActivity`, la navigation D-pad fonctionne
  (TAB/LEFT/UP/CENTER déplacent bien le focus) : le DOWN sans effet du §1.2 est
  donc réel, pas un artefact d'injection.
- SR-11/SR-13 (stabilité littérale) : déjà ✅ en phase 2, non rejoués ici.

## 6. Conclusion

**BUG-S10-05-01 : NON fermé au critère d'acceptation.** Le correctif
`93fff2f` ne déplace pas le focus à la télécommande ; le banc instrumenté du
correctif lui-même est rouge (4/5) et la repro live confirme que DOWN laisse le
focus sur le champ, seul TAB en sort. **S10-05 n'est donc pas terminal.**

- SR-10 : ✅ CONFORME · SR-12 : ❌ NON CONFORME (position) · SR-04 espaces seuls : ✅ CONFORME.
