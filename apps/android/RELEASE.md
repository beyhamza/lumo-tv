# Construire les bundles Google Play (S16B-04)

Complète [`AGENTS.md`](AGENTS.md). Ce document décrit comment produire les deux
fichiers AAB envoyés à la Play Console — un par fiche (ADR 0004) — sans qu'aucun
secret n'entre dans le dépôt. Il ne contient **aucune valeur réelle** : ni mot de
passe, ni empreinte, ni chemin propre à une machine.

| Module | `applicationId` | Bundle produit |
|---|---|---|
| `app-mobile` | `tv.lumo.android` | `app-mobile/build/outputs/bundle/release/app-mobile-release.aab` |
| `app-tv` | `tv.lumo.androidtv` | `app-tv/build/outputs/bundle/release/app-tv-release.aab` |

---

## 1. Les deux clés : Play App Signing

Google Play signe lui-même ce qu'il distribue (**Play App Signing**, obligatoire
pour un nouveau bundle). Il y a donc deux clés, et une seule est chez nous :

| Clé | Qui la garde | À quoi elle sert |
|---|---|---|
| **Clé de signature de l'application** | Google, générée par la Play Console à la création de chaque application | signe les APK installés sur les appareils |
| **Clé d'envoi** (*upload key*) | Hamza, **hors dépôt** | prouve à Google qu'un bundle vient bien de nous |

Conséquence pratique : perdre la clé d'envoi n'est pas fatal (le support Play la
réinitialise), la laisser fuiter l'est presque — n'importe qui pourrait envoyer
une version. Elle ne va donc ni dans le dépôt, ni dans un ticket, ni dans une
capture d'écran.

**Une clé d'envoi pour les deux applications.** Rien ne l'interdit et le build
le suppose : les deux modules lisent les mêmes variables. Google enregistre
l'empreinte de la clé d'envoi séparément pour chaque application, au premier
envoi.

## 2. Créer la clé d'envoi (une fois)

Sur la machine qui construira les versions, **en dehors du dépôt** — par
exemple dans un dossier personnel sauvegardé :

```bash
keytool -genkeypair -v \
  -keystore <dossier hors dépôt>/lumo-upload.jks \
  -storetype PKCS12 \
  -alias upload \
  -keyalg RSA -keysize 4096 \
  -validity 9125
```

`keytool` est livré avec le JDK (et avec Android Studio, dans `jbr/bin/`). Il
demande un mot de passe et un nom distinctif (nom, organisation, pays) : ces
informations ne sont pas affichées aux utilisateurs. Avec un magasin PKCS12, le
mot de passe de la clé est celui du magasin.

Ensuite :

1. sauvegarder le fichier `.jks` **et** son mot de passe dans un gestionnaire de
   mots de passe (deux endroits distincts de la machine de build) ;
2. ne jamais le copier sous `C:\dev\lumo-tv` : `*.jks` et `*.keystore` sont
   gitignorés, mais un fichier ignoré reste un fichier qu'un `git add -f` ou une
   archive du dossier emporte.

## 3. Les variables

Lues par `build-logic/…/LumoEnv.kt` : d'abord `apps/android/.env` (gitignoré),
sinon une variable d'environnement. Toutes sont documentées dans
[`.env.example`](.env.example).

| Variable | Rôle | Valeur par défaut |
|---|---|---|
| `LUMO_VERSION_CODE` | entier, **strictement croissant à chaque envoi**, identique pour les deux bundles d'une même version | `1` (développement) |
| `LUMO_VERSION_NAME` | version affichée aux utilisateurs | `0.4.0` |
| `LUMO_KEYSTORE_PATH` | chemin du `.jks`, absolu ou relatif à `apps/android/` | vide |
| `LUMO_KEYSTORE_PASSWORD` | mot de passe du magasin | vide |
| `LUMO_KEY_ALIAS` | alias de la clé (`upload` ci-dessus) | vide |
| `LUMO_KEY_PASSWORD` | mot de passe de la clé (= celui du magasin en PKCS12) | vide |
| `LUMO_API_BASE_URL_RELEASE` | API appelée par la version publiée | `https://api.lumo.tv/v1` |
| `LUMO_ACTIVATION_URL` | page d'activation de la TV, et origine des guides web | `https://lumo.tv/activate` |

Les deux dernières dépendent du domaine retenu en S16B-00 : **un bundle construit
avant que l'API soit en ligne à cette adresse s'installe mais ne peut créer aucun
compte.** Vérifier qu'elles pointent vers la pile de S16B-01 avant d'envoyer.

**Le numéro de version.** Google refuse tout `versionCode` déjà reçu par la
fiche, même pour une version retirée. Proposition : `MMmmppnn`, soit majeure,
mineure, correctif sur deux chiffres chacun puis le numéro d'envoi —
`0.4.0`, premier envoi → `40001` ; deuxième envoi de la même version → `40002` ;
`0.4.1` → `40101`. Les deux bundles d'un même envoi portent **le même** code :
c'est automatique, ils sont construits par la même commande.

**Garde-fou.** `bundleRelease` échoue tout de suite si `LUMO_KEYSTORE_PATH` ou
`LUMO_VERSION_CODE` manque : sans eux, le bundle serait signé par la clé de
debug ou porterait le code `1`, et la Play Console le refuserait. `assembleRelease`
et `build` gardent le repli sur la clé de debug, pour qu'un poste sans secret
puisse toujours compiler. Un `LUMO_VERSION_CODE` qui n'est pas un entier entre 1
et 2 100 000 000 fait échouer la configuration elle-même.

## 4. Construire

Dans `apps/android/` (PowerShell ou Git Bash), les secrets étant dans `.env` ou
dans l'environnement de la session :

```bash
./gradlew --no-daemon testDebugUnitTest lint :app-mobile:bundleRelease :app-tv:bundleRelease
```

Pour donner les numéros sur la ligne de commande plutôt que dans `.env`
(Git Bash) :

```bash
LUMO_VERSION_CODE=40001 LUMO_VERSION_NAME=0.4.0 ./gradlew --no-daemon :app-mobile:bundleRelease :app-tv:bundleRelease
```

Attention : une valeur présente dans `.env` **l'emporte** sur la variable
d'environnement (LumoEnv lit le fichier d'abord). Laisser `LUMO_VERSION_CODE`
vide dans `.env` si on le passe ainsi.

## 5. Vérifier avant d'envoyer

```bash
keytool -printcert -jarfile app-mobile/build/outputs/bundle/release/app-mobile-release.aab
keytool -printcert -jarfile app-tv/build/outputs/bundle/release/app-tv-release.aab
```

Les deux doivent afficher le **même** propriétaire et la même empreinte SHA-256,
celle de la clé d'envoi — jamais `CN=Android Debug`. Au premier envoi, la Play
Console affiche l'empreinte de la clé d'envoi qu'elle a enregistrée (Configuration
→ Intégrité de l'application) : elle doit correspondre.

Le numéro de version d'un AAB ne se lit pas avec `aapt2 dump badging` (son
manifeste est au format protobuf). Deux moyens simples : la Play Console, qui
affiche `versionCode` et `versionName` dès l'envoi, ou le manifeste fusionné
avant l'envoi :

```bash
grep -o 'android:version[A-Za-z]*="[^"]*"' app-tv/build/intermediates/merged_manifest/release/processReleaseMainManifest/AndroidManifest.xml
```

## 6. Ce que le build vérifie déjà pour la TV

`app-tv/src/main/AndroidManifest.xml` déclare `android.software.leanback`
requis, `android.hardware.touchscreen` **non** requis, l'activité
`LEANBACK_LAUNCHER` et une bannière 320 × 180 (`@drawable/tv_banner`). Aucune
permission ne fait sous-entendre une caméra, un micro ou un GPS. L'état détaillé
des exigences Play (et ce qui manque) est dans
[`docs/releases/0.4.0/play/exigences-play.md`](../../docs/releases/0.4.0/play/exigences-play.md).
