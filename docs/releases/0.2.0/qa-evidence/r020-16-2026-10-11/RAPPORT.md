# R020-16 — mise à jour 0.1.0 → 0.2.0 (drill local) — 11/10/2026

- Objet : ligne `R020-16` de [`acceptance.md`](../../acceptance.md) — « mise à jour depuis version
  précédente, migrations, données conservées, catalogue et lecture toujours accessibles ».
- Forme : **drill local**, comme prévu par l'[état du 08/10](../ETAT-RELEASE-0.2.0-2026-10-08.md) §3.
  Il n'existe pas de canal de distribution : « mise à jour » = même base PostgreSQL redémarrée sous la
  nouvelle API, et APK installé par-dessus (`adb install -r`, même clé de debug).
- Versions : précédente = tag `v0.1.0` (`319ae3e`, worktree séparé) ; candidate = `dev` @ `720f845`.
  APK **debug** des deux côtés (`*.debug`), émulateurs `Television_1080p` (API 36) et `Pixel_10` (API 37).
- Banc : `lumo-bench` (`mixed.m3u` et Xtream factice). Compte de recette créé pour l'occasion, stocké
  hors dépôt (`apps/api/.env.recette`, ignoré).

## Verdict

| Volet | Résultat |
|---|---|
| Migration serveur (Liquibase, base 0.1.0 → 0.2.0) | ✅ conforme |
| Données serveur conservées (compte, mot de passe, sources, identifiants chiffrés, catalogue, droits) | ✅ conforme |
| Mise à jour en place des APK TV et mobile | ✅ conforme |
| Base Room après mise à jour | ✅ sans objet sur appareil (la 0.1.0 n'en crée pas), chemin 1 → 7 couvert par `LumoDatabaseMigrationTest` (S10B-06) |
| Lecture après mise à jour (TV et mobile) | ✅ conforme |
| Catalogue accessible après mise à jour | ⚠️ **accessible, mais deux écarts Android** au premier affichage, **indépendants de la mise à jour** (reproduits sur installation propre) : `BUG-R020-16-01`, `BUG-R020-16-02` |

## 1. Serveur

1. API `v0.1.0` (image construite depuis le tag) sur une base neuve `lumo_r02016` : 44 changesets
   (`0001` → `0008`).
2. Sur la 0.1.0 : inscription d'un compte, source **M3U** (`mixed.m3u`) → `READY`, 7 entrées ; source
   **Xtream** → authentifiée (expiration et connexions lues) mais `SOURCE_INVALID_FORMAT` : le banc
   Xtream sert un format postérieur à la 0.1.0. Lecture d'une chaîne M3U : `200`.
   La 0.1.0 n'expose ni `/me`, ni favoris, ni progression (404) : il n'y a pas de données utilisateur
   à conserver au-delà du compte, des appareils et des sources.
3. Arrêt de la 0.1.0, démarrage de l'API `dev` sur **la même base** : Liquibase applique les
   changesets `0009` → `0020`, **46 exécutés, 44 déjà passés, 90 au total**, aucune erreur de somme de
   contrôle (`0001`–`0008` identiques au tag). Extrait : [`liquibase-0.1.0-vers-0.2.0.log`](liquibase-0.1.0-vers-0.2.0.log).
4. Comparaison avant / après (instantanés `GET` sous le même compte) :

| Donnée | 0.1.0 | 0.2.0 sur la base migrée |
|---|---|---|
| Connexion avec le mot de passe créé en 0.1.0 | ✅ | ✅ |
| `/me` | 404 (absent) | ✅ profil, date de création d'origine |
| `/me/entitlement` | 404 | `FREE` / `MANUAL` / `ACTIVE` — plafonds calculés à la lecture (`EntitlementService.limitsFor`), donc ceux d'un compte neuf |
| Sources (identifiants, libellés, type, statut) | M3U `READY`, Xtream `ERROR` | **identiques** |
| Chaînes M3U (identifiants et noms) | 7 | **identiques** avant resynchro |
| Lecture de la première chaîne M3U | `200` | `200` |
| Resynchro Xtream | — | ✅ `READY` : 3 chaînes, 2 films, 2 séries — **le mot de passe chiffré en 0.1.0 se déchiffre** |
| Resynchro M3U | — | 3 chaînes + 4 films : le classement ADR 0009 sort les films de la liste ; les 3 chaînes restantes **gardent leur identifiant**, aucune entrée perdue (« Chaîne 06 en continu » part en films, comme le banc l'annonce) |

## 2. Android

| Étape | TV (`tv.lumo.androidtv.debug`) | Mobile (`tv.lumo.android.debug`) |
|---|---|---|
| 0.1.0 installée et lancée | ✅ `01-tv-v010-installee.png` (coquilles d'écran, pas de connexion) | ✅ installée et lancée (`versionName=0.1.0-debug` ; capture inexploitable, écran en veille) |
| Fichiers privés laissés par la 0.1.0 | `files/profileInstalled` seul : ni session, ni base Room (déclarée v1, jamais ouverte) | aucun |
| `adb install -r` de la 0.2.0 | ✅ `firstInstallTime` conservé, `lastUpdateTime` changé, `versionName` 0.1.0 → 0.2.0 | ✅ idem |
| Premier lancement | ✅ écran d'activation, aucun crash (`02-…`) | ✅ connexion, aucun crash |
| Connexion du compte migré | ✅ activation approuvée, les deux sources `Ready` (`03-choix-source.png`) | ✅ email + mot de passe 0.1.0 (`12-mobile-connecte.png`) |
| Catalogue | ⚠️ voir écarts | ✅ Xtream complet (`13-…`) ; ⚠️ M3U tronqué (`15-…`) jusqu'à ↻ (`16-…`) |
| Lecture | ✅ `06-lecture-apres-maj.png` (flux du banc : VOD HLS de 6 s, image finale à 00:00:05.867) | ✅ `17-mobile-lecture-m3u.png` |

La lecture Xtream échoue sur l'émulateur (`UnknownHostException`) : l'URL est construite sur l'hôte de
la source, `host.docker.internal`, que l'émulateur ne résout pas. **Artefact du banc**, pas de l'app ;
la lecture est prouvée sur la source M3U, dont l'URL publique est `localhost:18081` relayé par
`adb reverse`.

**`versionCode`** : `1` en 0.1.0 et en 0.2.0. `adb install -r` l'accepte (égal n'est pas inférieur) ;
**un store refuserait la mise à jour**. Sans conséquence pour ce drill, à régler avant toute
distribution réelle (observation, pas de lot ouvert).

## 3. Écarts

### `BUG-R020-16-01` — catalogue tronqué au premier affichage quand le compte a des favoris ou des récentes (TV et mobile)

- **Constat** : après connexion, la grille du Direct d'une source ne montre **que** les chaînes
  favorites ou récentes du compte, marquées « From your device », sans catégories ; aucun appel
  catalogue n'est fait (15 s d'observation, logcat). ↻ rétablit le catalogue complet.
  Vu sur TV (Room : 1 chaîne, 0 catégorie) et sur mobile (`15-…` puis `16-…`).
- **Cause (lecture de code)** : `LiveViewModel` ne lance la synchro initiale que si
  `catalogue.cachedChannelCount(sourceId) == 0`. Or l'accueil résout favoris et récentes via
  `ChannelResolver`, qui **insère** ces chaînes dans la table `channel` (`GET …/channels?ids=`).
  Le compteur vaut alors ≥ 1 et le catalogue n'est jamais tiré.
- **Portée** : tout nouvel appareil (ou réinstallation) d'un compte qui a déjà une favorite ou une
  récente. Indépendant de la mise à jour. Présent depuis l'arrivée de `ChannelResolver` (28/08).
- **Gravité proposée** : haute — sur une TV fraîchement activée, l'utilisateur croit que sa source n'a
  que ses favoris.

### `BUG-R020-16-02` — TV : grille « All » vide après la première synchro alors que Room est rempli

- **Constat** : premier passage TV (avant toute lecture, donc sans récente), source M3U : catégories
  affichées avec leurs compteurs (`Généralistes (1)`, `Sport (1)`, `Films (1)`), **grille vide**
  (`20-tv-grille-all-vide-room-3-chaines.png`) alors que la base contient les 3 chaînes de la source
  (dump Room). Quitter et rouvrir le Direct ne change rien ; une recherche puis son effacement font
  apparaître les 3 chaînes (`21-…`). Reproduit sur installation propre (témoin). La même grille vide
  figure déjà dans une preuve du 26/09 (`s9-04-04-217-cold-entries-android-2026-09-26/qa217-tv-04-all-channels.png`).
- **Hypothèse** : la page `PagingData` vide chargée avant l'écriture du catalogue n'est pas invalidée
  par `replaceForSource`, et `cachedIn` + `distinctUntilChanged` la resservent tant que la requête ne
  change pas. Non démontré ; non reproduit sur mobile (source Xtream).
- **Gravité proposée** : moyenne — contournable, mais l'écran d'arrivée du Direct paraît vide.

Classement bloquant / non bloquant pour `v0.2.0` : **à trancher par Hamza**.

## Non joué

- Mise à jour depuis `v0.2.0-rc.1` (base Room v7 déjà remplie, session conservée) : rc.1 n'a pas été
  distribuée ; la question deviendra réelle à la première mise à jour 0.2.x → 0.3.0.
- Web : sans état local hors cookies ; la 0.2.0 web n'a rien à migrer côté client.
- Retour arrière (downgrade) : non prévu par le produit ; Liquibase n'a pas de `rollback` écrit.
