# Recette instrumentée — correctif GD-07 TV (BUG-S9-06-01-01)

- **Auteur** : QA
- **Date** : 2026-09-26
- **Lot** : volontairement unique — re-vérification du correctif GD-07 sur appareil TV.
- **Artefact testé** : branche `fix/S9-06-01-tv-sheet-focus` @ **c91eedd** (base `origin/main` **7d3e6d2**).
- **Verdict global** : ✅ **GD-07 / QA-06-01-04 conforme** — le correctif est prouvé par sortie
  instrumentée verte sur la TV 1080p. Le scénario vidéo réel avec dump uiautomator n'a pas abouti
  (voir priorité 2) mais n'est plus nécessaire pour fermer le point.

---

## 1. Environnement

| Élément | Valeur |
|---|---|
| Appareil | `emulator-5554`, AVD **Television_1080p**, API **36** (AOSP TV on x86), 1920×1080 |
| Autre appareil présent | `emulator-5556` (téléphone) — non utilisé |
| Branche testée | `c91eedd` (`fix/S9-06-01-tv-sheet-focus`) |
| JDK | Temurin 21.0.7 (`~/.gradle/jdks/eclipse_adoptium-21-amd64-windows.2`), Gradle 9.7.1 |
| Fixture guide | `guide.xml` (T=16:45:19Z, « QA courant » 16:40:19→16:49:49Z) |

## 2. Priorité 1 — instrumenté sur la TV (`ProgrammeSheetTvFocusTest`)

Commande exacte jouée depuis `apps/android` :

```
ANDROID_SERIAL=emulator-5554 \
JAVA_HOME="C:/Users/hamza/.gradle/jdks/eclipse_adoptium-21-amd64-windows.2" \
./gradlew :feature:live:connectedDebugAndroidTest --console=plain
```

Sortie brute (fin, `connectedDebugAndroidTest.log`) :

```
Starting 4 tests on Television_1080p(AVD) - 16
Finished 4 tests on Television_1080p(AVD) - 16
> Task :feature:live:connectedDebugAndroidTest
BUILD SUCCESSFUL in 16s
217 actionable tasks: 1 executed, 216 up-to-date
```

Résultat brut (`connectedDebugAndroidTest-results.xml`) :

```
<testsuites tests="4" failures="0" errors="0" skipped="0" time="8.248" timestamp="2026-09-26T16:43:03">
  <testsuite name="tv.lumo.android.feature.live.ProgrammeSheetTvFocusTest" tests="4" failures="0" errors="0" skipped="0">
    <testcase name="whenTheFocusSystemClearsTheFocus_thePanelReclaimsIt" time="2.392"/>
    <testcase name="whenTheActionDisappears_theFocusJoinsClose" time="0.906"/>
    <testcase name="theFocusCannotEscapeThePanel" time="1.731"/>
    <testcase name="afterTheActionDisappears_theFocusStaysOnClose" time="1.028"/>
```

### Verdicts par cas

| Cas | Attendu | Verdict |
|---|---|---|
| `whenTheFocusSystemClearsTheFocus_thePanelReclaimsIt` | le focus système effacé est repris par le panneau (Fermer) | ✅ **VERT** — c'est exactement le scénario du rouge d'origine (focus tombé sur la grille) |
| `whenTheActionDisappears_theFocusJoinsClose` (**GD-07 / QA-06-01-04**) | à `endsAt`, l'action disparaît, la fiche reste ouverte, le focus est sur **Fermer** | ✅ **VERT** |
| `afterTheActionDisappears_theFocusStaysOnClose` | après la fin, `LEFT` ne fait pas sortir le focus vers la grille | ✅ **VERT** |
| `theFocusCannotEscapeThePanel` | les 4 directions depuis Watch/Fermer restent dans le panneau | ✅ **VERT** |

→ **Réponse à la question posée : OUI, le cas « le panneau reprend Fermer quand le focus est
effacé » est VERT sur `c91eedd`**, sur la TV 1080p (`emulator-5554`).

## 3. Règle pure `sheetFocusAfter` — vérification unitaire indépendante

```
./gradlew :feature:live:testDebugUnitTest --rerun-tasks --console=plain
```

`BUILD SUCCESSFUL`, **11 classes / 91 tests / 0 échec / 0 skip** (log : `unit-testDebugUnitTest.log`).
`ProgrammeSheetTest` (10 cas) confirme la règle pure, dont :

- `a focus on the action joins Fermer when the programme ends` ✅
- `the action is read again at the end, so a sheet left open cannot play` ✅
- `a future programme that starts gains the action without taking the focus` ✅
- `a focus already on Fermer is never moved` ✅

## 4. Priorité 2 — scénario GD-07 réel avec dump uiautomator : NON ABOUTI (daté)

Ce qui a été fait :
1. régénération de `guide.xml` (`s90601-guide.mjs`, courant 16:40:19→16:49:49Z) ;
2. `docker cp` vers `lumo-bench` (servi en 200 sur `http://bench/guide.xml`) ;
3. sync de la source `c4e967b1-c427-4058-8007-3ea54464ec83` → **READY** à 16:45:22Z ;
4. EPG API confirmée : Chaîne 01 FHD `MAPPED`, 8 programmes dont « QA courant » ;
5. relance de l'app : l'accueil affiche la source, le Guide se peuple (`priority2-guide-populated.png`).

Blocage : **impossible de poser le focus sur une cellule du guide via adb**.
- Le focus passe d'un pilier latéral au champ « Search a channel », qui **piège les 4 directions
  D-pad** (`UP/DOWN/LEFT/RIGHT` n'en sortent pas) — point déjà connu.
- La cellule courante est réduite à un **sliver** à l'extrémité gauche (nœud texte large de ~2 px).
- Un tap direct sur la zone a déclenché une **lecture de chaîne** puis l'erreur « Stream interrupted »,
  pas l'ouverture de la fiche programme.

Aucun dump `focused="true"` sur **Fermer** n'a donc pu être produit côté app réelle.
**La fermeture de GD-07 repose donc sur la sortie instrumentée verte de la priorité 1**, pas sur un
dump appareil. (`priority2-guide-before.png` = guide vide avant sync ; `priority2-guide-populated.png` = après.)

## 5. Limites / honnêteté

- Preuve sur **émulateur TV 1080p**, pas sur matériel physique.
- Le **rouge** (échec avant correctif) est la mesure de Dev sur `7d3e6d2` ; je ne l'ai **pas**
  reproduit moi-même (le fichier `ProgrammeSheetTvFocusTest.kt` n'existe que sur `c91eedd`).
  Mon verdict porte sur le **vert de `c91eedd`**.
- Le guide du banc contenait des programmes **dupliqués** (l'ancien et le nouveau `guide.xml` ont
  tous deux été ingérés) ; sans effet sur le cas instrumenté, mais à noter pour une passe manuelle.
- Aucun autre cas S9-06-01 / S9-06-02/03/04 rejoué (lot séparé, conformément à la consigne).

## 6. Conclusion

**BUG-S9-06-01-01 fermable sur cette base** : le correctif `c91eedd` maintient le focus dans le
panneau et le repose sur **Fermer** quand le système l'efface, prouvé par 4 tests instrumentés verts
sur la TV 1080p + la règle pure verte. Le scénario vidéo réel avec dump reste à rejouer uniquement
si le PO exige une garantie matérielle supplémentaire.

Fichiers de preuve : `connectedDebugAndroidTest.log`,
`connectedDebugAndroidTest-results.xml`, `androidTest-report/`, `unit-testDebugUnitTest.log`,
`guide.xml` + scripts `s90601-*.mjs`, `epg-probe.mjs`, `priority2-guide-*.png`.
