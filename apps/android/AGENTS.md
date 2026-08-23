# AGENTS.md — `iptv-lumo` / `iptv-lumo-tv`

Complète l'[`AGENTS.md` racine](../../AGENTS.md), ne le contredit jamais. Lis
celui-ci **en plus**, pas à la place.

Un seul projet Gradle produit les **deux** applications (ADR 0004) :

| Module | `applicationId` | Fiche Play |
|---|---|---|
| `app-mobile` | `tv.lumo.android` | téléphone et tablette |
| `app-tv` | `tv.lumo.androidtv` | Android TV |

---

## 1. Commandes

```bash
./gradlew build
```

| But | Commande |
|---|---|
| Compiler les deux apps | `./gradlew assembleDebug` |
| Build complet (lint + tests + release) | `./gradlew build` |
| Tous les tests unitaires | `./gradlew testDebugUnitTest` |
| Un seul test | `./gradlew :core:auth:testDebugUnitTest --tests '*SessionManagerTest*'` |
| Lint | `./gradlew lint` |
| Installer sur un appareil / émulateur | `./gradlew :app-mobile:installDebug` |
| Installer la TV | `./gradlew :app-tv:installDebug` |
| Régénérer le client du contrat | `./gradlew :core:network:openApiGenerate` |

**Prérequis.** Le SDK Android doit être trouvable, soit par `ANDROID_HOME`, soit
par un `local.properties` avec `sdk.dir=…`. Ce fichier est gitignoré et ne se
commite jamais : il contient un chemin propre à ta machine.

**Configuration.** `cp .env.example .env`. Le build lit ce fichier (voir
`build-logic/…/LumoEnv.kt`) et retombe sur les variables d'environnement, ce qui
couvre à la fois un poste de dev et une CI. Sans `.env`, les valeurs par défaut
documentées dans `.env.example` s'appliquent et l'app pointe vers
`http://10.0.2.2:8080/v1` — l'adresse par laquelle l'**émulateur** joint la
machine hôte. `localhost` depuis l'émulateur désigne l'émulateur lui-même : c'est
la première cause de « mon app n'atteint pas l'API ».

Lancer un émulateur TV plutôt qu'un téléphone : il faut une image système
Android TV (`system-images;android-XX;android-tv;x86_64`). À défaut, `app-tv`
s'installe sur un émulateur téléphone et se lance explicitement — c'est utile
pour vérifier qu'elle démarre, ça ne remplace **pas** un test à la télécommande
(§6).

---

## 2. Découpage en modules

```
app-mobile/  app-tv/      UI seulement. Aucune logique.
core/
  common/        dispatchers, redaction des logs, contrat de navigation
  designsystem/  tokens partagés, thème mobile, thème TV, focus, overscan
  network/       client généré + intercepteur d'auth + refresh
  auth/          stockage chiffré des tokens, un seul refresh en vol
  database/      Room, DAO, Paging 3
  player/        abstraction au-dessus de Media3
feature/
  onboarding/ auth/ source/ live/ vod/ series/ search/ settings/
build-logic/     convention plugins
```

**La règle qui structure tout le reste : tout ce qui n'est pas de l'UI est
partagé.** Une logique métier présente dans `app-mobile` et absente d'`app-tv`
est un défaut, pas un choix (docs/architecture.md §3). En pratique :

- une `feature` ne dépend **jamais** d'une autre `feature`. Ce qui est commun
  descend dans `core/` ;
- `app-mobile` et `app-tv` n'ont que : un `Application`, une `Activity`, un
  `NavHost`, un thème. Si tu écris un `if` dans un de ces modules, demande-toi
  d'abord pourquoi il n'est pas dans un module partagé ;
- un module `feature` contient **les deux** surfaces, `XMobileScreen` et
  `XTvScreen`, autour d'un état commun. Le jour où la divergence d'UI devient
  trop lourde, le découpage suivant est `:feature:x:mobile` / `:feature:x:tv` —
  mais pas avant, parce que ça double le nombre de modules.

### Ajouter un module `feature`

1. `settings.gradle.kts` : `include(":feature:mon-truc")`.
2. `feature/mon-truc/build.gradle.kts` :
   ```kotlin
   plugins { alias(libs.plugins.lumo.android.feature) }
   android { namespace = "tv.lumo.android.feature.montruc" }
   ```
   Le convention plugin apporte déjà library + Compose + Hilt + `core:common` +
   `core:designsystem`. Tout le reste (`core:network`, `core:database`…) se
   déclare explicitement : le build file d'un module doit dire ce qu'il touche.
3. `consumer-rules.pro` — même vide, le build échoue sans lui.
4. `MonTrucDestination` (implémente `LumoDestination`), `MonTrucMobileScreen`,
   `MonTrucTvScreen`, `navigation/MonTrucNavigation.kt`.
5. `res/values/strings.xml` **et** `res/values-fr/strings.xml`.
6. Brancher les deux `NavHost` (`app-mobile/navigation/`, `app-tv/navigation/`)
   et ajouter la destination aux listes si elle est de premier niveau.

---

## 3. Contract-first (ADR 0001)

Le client Retrofit est **généré** depuis `packages/contracts/openapi.yaml`
pendant le build, dans `core/network/build/generated/`. Il n'est pas commité ici.

- On n'écrit **jamais** à la main une classe de requête ou de réponse. On modifie
  le contrat, on régénère, on adapte.
- Le fichier d'options est le même que celui de la pipeline npm
  (`packages/contracts/config/kotlin.yaml`) : `./gradlew assemble` et
  `npm run generate` ne peuvent pas produire deux clients différents.
- Un besoin non couvert par le contrat → **arrête-toi et demande** (AGENTS.md §9).

Les modèles générés portent `@Json` mais pas `@JsonClass(generateAdapter = true)` :
Moshi construit leurs adaptateurs **par réflexion**. Les règles qui gardent ça
vivant en release sont dans `core/network/consumer-rules.pro`. Si tu vois du JSON
parser correctement en debug et échouer en release, c'est là qu'il faut regarder.

---

## 4. Réseau et session

Le piège que ce code évite explicitement, parce qu'il est classique et coûteux
(US-04, note du backlog) :

> Trois écrans démarrent, trois requêtes partent, trois 401 reviennent, trois
> refresh partent. Le deuxième présente un refresh token que le premier vient de
> faire tourner. Le serveur voit un token consommé, en déduit un vol, révoque
> toute la chaîne de l'appareil — et l'utilisateur est déconnecté pour avoir
> ouvert l'application.

Deux choses l'empêchent, et il faut les deux :

1. `SessionManager.refresh` prend un `Mutex` : les appels se sérialisent.
2. **Dans** le verrou, le token périmé de l'appelant est comparé à celui qui est
   stocké. S'ils diffèrent, quelqu'un a déjà rafraîchi pendant l'attente et on
   rend le nouveau token au lieu d'en demander un deuxième.

Sans le point 2, le mutex transforme un double refresh simultané en double
refresh séquentiel : même bug, plus fiable. `SessionManagerTest` et
`TokenAuthenticatorTest` verrouillent les deux.

Autres invariants du module :

- le refresh passe par le client `@Unauthenticated`. S'il passait par le client
  authentifié, un 401 sur le refresh déclencherait un refresh, indéfiniment ;
- les tokens sont dans un DataStore **chiffré** par une clé du Keystore
  (`core:auth`). `androidx.security:security-crypto` n'est pas utilisé : il est
  déprécié et sans remplacement ;
- une session illisible (clé invalidée, sauvegarde restaurée) n'est pas une
  erreur à remonter : le fichier est remplacé, l'utilisateur se reconnecte.

---

## 5. Logs et secrets

AGENTS.md §5 s'applique intégralement, et sur Android il a des dents : `logcat`
est lisible par `adb` depuis n'importe quelle machine branchée en USB.

- Le logging HTTP est en `BASIC`, en **debug uniquement**, avec `Authorization`
  masqué. Jamais `BODY` : ça imprimerait le mot de passe à l'inscription, la
  paire de tokens, et `stream_url` en entier.
- C'est aussi pourquoi l'`ApiClient` généré n'est pas utilisé pour construire
  Retrofit : il installe un logger `BODY` par défaut.
- Une URL de flux qui doit apparaître dans un log passe par `Redact.url(…)`.
- `SessionTokens` et `PlaybackRequest` redéfinissent `toString()`. Une `data
  class` finit toujours par atterrir dans un rapport de crash.
- La `stream_url` n'est **pas** en cache dans Room, volontairement : elle porte
  les identifiants Xtream dans son chemin. Elle est demandée à la lecture, à
  `GET /channels/{id}/playback`, et jetée ensuite.

---

## 6. Compose for TV

`androidx.tv.material3`, pas `androidx.compose.material3`. Les composants TV
portent un état de focus ; les composants mobiles n'en ont pas. Un `Card`
Material 3 sur une télévision est focusable par accident au mieux, invisible
quand il a le focus au pire.

**Signature de focus obligatoire : échelle + bordure + élévation, ensemble**
(`Modifier.lumoTvFocus`, docs/architecture.md §3). Un seul canal ne suffit pas :
la couleur disparaît sur une dalle mal calibrée ou pour un daltonien, l'échelle
se remarque mal dans une grille dense, l'ombre s'efface sur une affiche claire.

**Overscan : 5 % sur chaque bord** (`Modifier.tvOverscan`), appliqué au conteneur
le plus externe de chaque écran TV. Ce qui est en dehors peut être physiquement
invisible chez un utilisateur, et il n'y a pas de réglage à lui faire changer.

**Aucun élément interactif inatteignable à la D-pad** (US-10). En pratique :

- un écran TV a **toujours** au moins une cible focusable, sinon `BACK` devient
  la seule touche qui fait quelque chose ;
- la navigation par focus suit la disposition visuelle. Le rail est à gauche
  dans une `Row`, donc `RIGHT` entre dans le contenu et `LEFT` en ressort. Une
  barre en bas d'écran serait atteignable seulement après avoir traversé tout le
  contenu vers le bas ;
- `clickable` rend déjà focusable et lie la touche centrale. Ajouter `focusable()`
  en plus crée deux cibles sur un seul élément ;
- focus et sélection se dessinent différemment. Le focus, c'est où est la
  télécommande ; la sélection, c'est l'écran ouvert. Les confondre donne
  l'impression que l'app a navigué alors qu'on ne fait que parcourir.

**Le test se fait à la télécommande, sur un appareil réel** (Definition of Done
du sprint). Une souris produit du *hover*, pas du focus, et le hover masque
exactement les bugs que ces règles évitent. `adb shell input keyevent 19/20/21/22/23`
sur un émulateur est un filet de sécurité, pas une validation.

---

## 7. i18n

Aucune chaîne en dur dans l'UI, jamais (AGENTS.md §4). FR et EN livrées
ensemble, dès le premier écran.

- Les libellés vivent dans le module qui les affiche, pas dans l'application.
- `values/` = anglais (défaut), `values-fr/` = français.
- `MissingTranslation` est une **erreur** de lint, pas un avertissement : le
  build échoue si une chaîne n'a pas sa traduction.
- Une apostrophe dans une chaîne XML s'échappe : `dès qu\'une source`.
- Un nom de marque n'est pas traduit : `translatable="false"`.

---

## 8. Tests

AGENTS.md §5 : le domaine arrive avec ses tests, l'UI n'a pas besoin d'une
couverture exhaustive. Ici, ce qui compte :

| Module | Ce qui est testé | Pourquoi |
|---|---|---|
| `core:auth` | rotation, un seul refresh en vol, rejet vs panne réseau | déconnecte tous les utilisateurs si c'est faux |
| `core:network` | 401 → refresh → rejeu, endpoints publics sans bearer | le câblage, avec un vrai serveur HTTP (MockWebServer) |
| `core:common` | redaction des URL et des secrets | c'est une règle de sécurité, pas du confort |

Les tests sont des tests unitaires JVM. Aucun émulateur n'est nécessaire pour
`./gradlew build`, et c'est délibéré : une campagne qui exige un émulateur est
une campagne qu'on finit par ne plus lancer.

---

## 9. Pièges AGP 9 / Gradle 9

Ce projet est sur AGP 9, qui a supprimé des API que tout tutoriel plus ancien
utilise encore.

| Symptôme | Cause | Ce qu'il faut faire |
|---|---|---|
| `The 'org.jetbrains.kotlin.android' plugin is no longer required` | AGP 9 compile Kotlin lui-même | ne pas appliquer le plugin Kotlin. Le `jvmTarget` suit `compileOptions.targetCompatibility` |
| `No type arguments expected for CommonExtension` | AGP 9 a retiré les paramètres de type | `CommonExtension` sans générique, et accès **par propriété** (`defaultConfig.minSdk = …`) : les formes en bloc ne sont plus sur le type commun |
| `Unresolved reference 'targetSdk'` dans un module library | AGP 9 l'a supprimé des libraries | ne rien mettre : c'est l'application qui décide |
| `DefaultAndroidLibrarySourceSet_Decorated cannot be cast` | l'accesseur `android.sourceSets` du DSL Kotlin | passer par l'API variant (`addGeneratedSourceDirectory`), cf. `core/network/build.gradle.kts` |
| `Supplied consumer proguard configuration does not exist` | AGP 9 exige que le fichier existe | créer `consumer-rules.pro`, même vide |
| `test sources present … did not discover any tests` | KSP (Hilt) génère des sources de test | déjà neutralisé dans le convention plugin `configureUnitTests` |
| `Syntax error: Unclosed comment` | Kotlin imbrique les commentaires de bloc : un `/*` **dans** une KDoc en ouvre un second | ne pas écrire `/auth/*` dans un commentaire |

Autre chose à savoir : `tag(Class, T)` d'OkHttp est masqué en Kotlin depuis
OkHttp 5, il faut la surcharge `KClass`.

---

## 10. Ce qui n'est pas encore fait

Le scaffolding s'arrête volontairement là où le sprint 1 commence.

- Aucun écran réel : chaque feature affiche un placeholder. Aucun appel API n'est
  déclenché par l'UI.
- `core:database` n'a pas de `RemoteMediator` : le cache existe, ce qui le
  remplit reste à écrire (US-08).
- `LUMO_GOOGLE_WEB_CLIENT_ID` et `LUMO_ACTIVATION_URL` sont documentés dans
  `.env.example` mais pas encore lus par un module (US-03, US-05).
- Aucun test instrumenté ni test d'UI Compose. La dépendance est en place.
- L'app TV n'a pas encore été pilotée à la télécommande sur un appareil réel.
