# Exigences Google Play — état vérifié le 19/10/2026 (S16B-04)

Vérifié sur la branche `feat/S16B-play-release`, par lecture des manifestes
**fusionnés** de la variante `release` (ce que Play reçoit, pas seulement ce
qu'on a écrit) et par la construction des deux bundles.

Légende : ✅ conforme · ⚠️ à corriger ou à décider, ne bloque pas le test interne ·
❌ bloque l'envoi ou l'examen.

## Construction et signature

| Exigence | État | Preuve |
|---|---|---|
| Format AAB pour un nouveau titre | ✅ | `:app-mobile:bundleRelease` et `:app-tv:bundleRelease` produisent `app-mobile-release.aab` (≈ 9,6 Mo) et `app-tv-release.aab` (≈ 9,1 Mo) |
| Signés par une clé d'envoi lue hors dépôt (Play App Signing) | ✅ | construits avec une clé de **test** générée pour l'occasion dans un dossier temporaire hors dépôt ; `keytool -printcert -jarfile` : même propriétaire, même empreinte SHA-256 sur les deux bundles, pas de `CN=Android Debug`. Procédure : [`apps/android/RELEASE.md`](../../../../apps/android/RELEASE.md) |
| `versionCode` croissant, identique sur les deux applications | ✅ | `LUMO_VERSION_CODE=40001`, `LUMO_VERSION_NAME=0.4.0` → manifestes fusionnés `versionCode="40001"` `versionName="0.4.0"` pour `tv.lumo.android` **et** `tv.lumo.androidtv` |
| Un bundle non publiable n'est pas produit par erreur | ✅ | sans clé ou sans `LUMO_VERSION_CODE`, `bundleRelease` échoue à la configuration ; un `LUMO_VERSION_CODE` non entier échoue aussi (vérifié) |
| `targetSdk` exigé pour une nouvelle application | ✅ | `targetSdkVersion="37"` (exigence Play 2026 : 35 minimum), `minSdkVersion="24"` |
| Rien de secret dans le bundle | ✅ | aucun fichier de clé, aucun `.env` dans le dépôt ; `.jks`/`.keystore`/`.env` gitignorés |
| L'API de production existe à l'adresse compilée | ❌ **pour l'usage**, pas pour l'envoi | `LUMO_API_BASE_URL_RELEASE` vaut par défaut `https://api.lumo.tv/v1`, domaine non encore retenu (S16B-00) ni déployé (S16B-01). Un bundle envoyé avant s'installe mais ne crée aucun compte. À fixer au moment de construire le bundle d'envoi |

## Permissions et fonctionnalités déclarées

Identiques sur les deux applications (elles viennent des modules `core`) :
`INTERNET`, `ACCESS_NETWORK_STATE`, `WAKE_LOCK`, plus la permission interne
`DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION` ajoutée par AndroidX. Aucune
permission sensible (localisation, caméra, micro, contacts, stockage, liste des
applications, alarmes exactes, service de premier plan) : **aucune déclaration
de permission à remplir dans la Console**.

## Android TV (`tv.lumo.androidtv`)

| Exigence Play / qualité TV | État | Détail |
|---|---|---|
| `android.software.leanback` déclaré | ✅ | `required="true"` : l'application n'est proposée qu'aux téléviseurs |
| Écran tactile non requis | ✅ | `android.hardware.touchscreen` `required="false"` ; aucune autre fonctionnalité matérielle impliquée (pas de caméra, micro, GPS, téléphonie) |
| Activité `LEANBACK_LAUNCHER` | ✅ | `TvActivity`, `MAIN` + `LEANBACK_LAUNCHER` |
| Bannière d'accueil 320 × 180 dp | ✅ présente · ⚠️ contenu | `android:banner="@drawable/tv_banner"` sur l'application et l'activité. La bannière ne montre que le logo, **sans le nom de l'application** ; les recommandations Android TV demandent le nom, en texte lisible. Ne bloque pas le test interne ; à corriger avant la production (proposition : reprendre [`graphismes/banniere-tv-1280x720.png`](graphismes/banniere-tv-1280x720.png), qui porte « Lumo TV », en ressource `drawable-xhdpi` 640 × 360) |
| Orientation paysage | ✅ | `screenOrientation="landscape"` |
| Navigation entièrement à la croix directionnelle | ⚠️ non vérifié ici | règles et tests instrumentés de focus en place (`apps/android/AGENTS.md` §6), mais l'application n'a **jamais été pilotée à la télécommande sur un vrai téléviseur** : c'est le premier cas de la recette S17 |
| Bannière de fiche 1280 × 720 | ✅ | [`graphismes/banniere-tv-1280x720.png`](graphismes/banniere-tv-1280x720.png) |
| Captures TV 1920 × 1080 | voir [`captures/`](captures/) | |
| Facteur de forme TV accepté dans la Console | ❌ à faire par Hamza | Configuration avancée → Facteurs de forme → Android TV. Sans cela la fiche TV n'est pas examinée pour Google TV ; le test interne fonctionne quand même |

## Téléphone et tablette (`tv.lumo.android`)

| Exigence | État | Détail |
|---|---|---|
| Activité `LAUNCHER` | ✅ | `MainActivity` |
| N'apparaît pas dans le catalogue TV | ✅ | ni `leanback` ni `LEANBACK_LAUNCHER` : c'est voulu, la TV a sa fiche (ADR 0004) |
| Image dans l'image | ✅ | `supportsPictureInPicture="true"`, aucune permission ni déclaration Play requise |
| Icône adaptative | ⚠️ | `android:icon="@drawable/ic_lumo"` est un vecteur simple, pas une `adaptive-icon` : sur Android 8+ le lanceur l'affiche réduite dans une pastille. Ne bloque rien ; à corriger avant la production (le vecteur est déjà dessiné sur la grille 108 dp d'une icône adaptative) |

## Fiche et règlement

| Point | État | Détail |
|---|---|---|
| Mention « aucun contenu fourni » | ✅ | dans la description courte et en tête de la longue, sur les deux fiches et les deux langues ; aussi sur l'image de présentation |
| Captures sans contenu réel | ✅ | banc de test uniquement (« Chaîne 001 »…, guide généré), voir [`captures/README.md`](captures/README.md) |
| Ressemblance du logo | ⚠️ **à décider par Hamza** | le logo (rectangle arrondi plein + triangle de lecture) rappelle celui d'une grande plateforme vidéo. Le règlement « Usurpation d'identité » vise les icônes qui imitent une marque connue ; le risque est faible avec la couleur bleue, mais un vérificateur sévère peut le relever. Décision de marque, hors de ce lot |
| Suppression de compte dans l'application et par le web | ❌ en cours | S16B-02 (autre session) ; obligatoire pour toute application qui crée des comptes, demandé dans le formulaire « Sécurité des données » |
| Politique de confidentialité publiée | ❌ en cours | S16B-03 (autre session) ; URL obligatoire dans la Console |
| Accès pour le vérificateur | ❌ à préparer | un compte de démonstration **et** une source du banc joignable sur Internet ; voir [`checklist-play-console.md`](checklist-play-console.md) §5 |
