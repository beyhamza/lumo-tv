# Sprint 10-bis — Correctifs de qualité avant la 0.2.0

Statut : **cycle ouvert le 10 octobre 2026** (10 → 16 octobre 2026), cible **0.2.0**
(candidate `v0.2.0-rc.2`). Taille relative : M. Décidé par Hamza le 10/10/2026 :
**S11 est mis en pause** et reprend après ce cycle (voir [`sprint-11.md`](sprint-11.md)
et [`DECISIONS-PRODUIT.md`](DECISIONS-PRODUIT.md)).
Référence : [plan et DoD commune](../roadmap/0.2.0/delivery-plan.md).

## Origine

Revue de code transversale du 10 octobre 2026 (API, web, Android), demandée par
Hamza. Elle a relevé cinq défauts qu'un utilisateur ou un attaquant rencontre dès
la première utilisation, et un manque de tests qui touche directement le verrou de
sortie `R020-16` (mise à jour depuis une version précédente). Aucun n'est un
comportement voulu ; tous ont été revérifiés dans le code avant l'ouverture des tickets.

## Objectif

Sortir une candidate 0.2.0 sans faille d'ingestion ni d'authentification connue, dont
la lecture s'arrête quand on quitte l'application, qui lit les flux Xtream redirigés
et qui ne déconnecte pas l'utilisateur web au milieu d'une lecture.

## Tâches

| ID | Travail | Surface | Gravité |
|---|---|---|---|
| S10B-01 | Bloquer la SSRF par redirection lors de l'ingestion M3U/XMLTV/Xtream | API | Haute — sécurité |
| S10B-02 | Ne plus faire confiance à `X-Forwarded-For` sans proxy de confiance ; plafond de connexion par email | API | Haute — sécurité |
| S10B-03 | Arrêter (mettre en pause) la lecture quand l'app passe en arrière-plan ; décider de la `MediaSession` | Android mobile + TV | Haute — utilisateur |
| S10B-04 | Configurer la pile réseau du lecteur : redirections http↔https, `BEHIND_LIVE_WINDOW` | Android mobile + TV | Haute — utilisateur |
| S10B-05 | Rendre le rafraîchissement de session web robuste : requêtes concurrentes et routes `/api/*` | Web | Haute — utilisateur |
| S10B-06 | Tester les migrations Room 1 → 7 | Android | Moyenne — sert `R020-16` |

### S10B-01 — SSRF par redirection (API)

Constat : [`IngestionHttpClient.java:54`](../../apps/api/src/main/java/tv/lumo/api/ingest/IngestionHttpClient.java)
construit le client avec `followRedirects(HttpClient.Redirect.NORMAL)`, alors que
`PrivateAddressGuard.requirePublic` (`:86`) ne contrôle que l'hôte de l'URL saisie.
Une URL publique qui répond `302` vers une adresse privée, de lien local
(`169.254.169.254`) ou de boucle locale est suivie. La résolution DNS du garde est
aussi distincte de celle de la connexion (fenêtre de *DNS rebinding*). Les codes
d'erreur renvoyés permettent de sonder le réseau interne.

Attendu :
- redirections suivies **à la main**, en nombre borné, avec `requirePublic` à chaque saut ;
- un saut vers une adresse non publique échoue avec le même code qu'une URL privée saisie ;
- si possible, connexion à l'adresse déjà validée (ferme le *rebinding*) ; sinon, limite documentée.

Preuve : test d'intégration « URL publique → 302 vers adresse privée » rouge avant,
vert après ; test du nombre maximal de sauts.

### S10B-02 — IP client et limitation de débit (API)

Constat : [`ClientIp.java:23`](../../apps/api/src/main/java/tv/lumo/api/shared/web/ClientIp.java)
retient la première valeur de `X-Forwarded-For` sans vérifier que la requête vient
d'un proxy de confiance. La clé de limitation de `/auth/login` étant `IP + email`
(`AuthController`), changer l'en-tête à chaque essai lève la limite : force brute
illimitée sur un compte. Même effet sur l'inscription, le mot de passe oublié et
la réinitialisation.

Attendu :
- `X-Forwarded-For` n'est lu que si l'adresse distante appartient à une liste de
  proxies de confiance configurée ; sinon `getRemoteAddr()` ;
- un plafond **par email seul** sur la connexion, indépendant de l'IP ;
- aucune valeur par défaut qui fasse confiance à tout le monde.

Preuve : tests — en-tête forgé depuis une IP non fiable ignoré ; N échecs sur un même
email avec des IP différentes → limité.
Hors périmètre : limiteur partagé entre instances (une seule instance aujourd'hui).

### S10B-03 — Lecture en arrière-plan (Android)

Constat : les six écrans lecteur (`PlayerTvScreen`, `PlayerMobileScreen`,
`VodPlayer*Screen`, `EpisodePlayer*Screen`) n'observent que `ON_START` et n'arrêtent
le lecteur que dans un `DisposableEffect` au démontage, qui ne se déclenche pas sur
la touche HOME. Le lecteur est un singleton avec `WAKE_MODE_NETWORK`
([`Media3LumoPlayer.kt:112`](../../apps/android/core/player/src/main/kotlin/tv/lumo/android/core/player/Media3LumoPlayer.kt))
et aucune `MediaSession` n'existe : le son continue probablement derrière le launcher,
ce que les règles qualité Android TV refusent.

Attendu :
- pause sur `ON_STOP` dans un seul composable commun aux six écrans ;
- au retour : le direct reprend au direct, un film ou un épisode reprend en pause à sa position ;
- la position VOD/épisode est sauvegardée à `ON_STOP` ;
- décision tracée sur la `MediaSession` (touches média de la télécommande). Si elle est
  reportée, l'écrire dans les limites connues.

Preuve : reproduction à la télécommande avant correctif (son après HOME), puis test
instrumenté ou capture logcat après ; vérification sur mobile.

### S10B-04 — Flux Xtream redirigés et direct en retard (Android)

Constat : `ExoPlayer.Builder(context)` (`Media3LumoPlayer.kt:102`) garde la source
réseau par défaut, qui refuse les redirections entre `http` et `https`
(`allowCrossProtocolRedirects = false`). Les panels Xtream redirigent couramment vers
un autre hôte ou protocole : la chaîne échoue alors qu'elle se lit ailleurs.
`ERROR_CODE_BEHIND_LIVE_WINDOW` n'est pas traité dans `toLumoError` : en HLS live,
le lecteur affiche une erreur inconnue au lieu de revenir au direct.

Attendu :
- `DefaultHttpDataSource.Factory` explicite : redirections entre protocoles autorisées,
  délais de connexion et de lecture fixés, user-agent de l'app ;
- `BEHIND_LIVE_WINDOW` → `seekToDefaultPosition()` + `prepare()` sans écran d'erreur ;
- le texte en clair autorisé reste limité aux serveurs de l'utilisateur (ADR 0008 inchangé).

Preuve : test unitaire du mapping d'erreur ; banc local avec une fixture neutre servie
derrière une redirection `http → https` (aucune URL de flux réelle, CLAUDE.md règle 2).

### S10B-05 — Session web : rafraîchissement concurrent et routes `/api/*`

Constat :
- la déduplication `inFlight` de
  [`refresh.ts:44-54`](../../apps/web/src/lib/session/refresh.ts) est locale au processus
  et l'entrée disparaît dès la fin de la promesse. Une requête parallèle partie avec
  l'ancien cookie (prefetch RSC, deuxième onglet) rejoue l'ancien refresh token ; l'API
  répond `REFRESH_TOKEN_REUSED`, révoque la chaîne de l'appareil et l'utilisateur est
  déconnecté ;
- le matcher du proxy exclut `api` ([`proxy.ts:196`](../../apps/web/src/proxy.ts)) : les
  routes `/api/playback/*` et `/api/sources/[id]/*` utilisent un jeton d'accès jamais
  rafraîchi. Une page ouverte au-delà de sa durée de vie obtient une 401 sur Lecture.

Attendu :
- le résultat d'une rotation est réutilisable quelques secondes par l'ancien refresh
  token, côté web. Une fenêtre de grâce côté API changerait le modèle d'authentification :
  **ADR et décision humaine** (AGENTS.md §8/§9), pas dans ce lot sans accord ;
- les routes `/api/*` rafraîchissent la session et réécrivent le cookie, sans
  redirection vers la connexion (réponse 401 propre si le refresh est refusé) ;
- tests Vitest de `refresh.ts` et du proxy (aujourd'hui aucun).

Preuve : test « deux requêtes concurrentes avec l'ancien cookie → une seule rotation,
pas de révocation » ; e2e « jeton d'accès expiré → Lecture fonctionne ».

### S10B-06 — Tests des migrations Room (Android)

Constat : `core:database` porte six migrations écrites à la main (`MIGRATION_1_2` →
`MIGRATION_6_7`) et les sept schémas exportés, mais **aucun test**. Une migration
fausse fait planter l'app au démarrage chez tous les utilisateurs qui mettent à jour,
ce qui est exactement le verrou de sortie `R020-16`.

Attendu : `MigrationTestHelper` sur les schémas commités, chaque saut et le chemin
complet 1 → 7 avec des données ; exécutable par `testDebugUnitTest` (Robolectric) pour
tourner en CI, sinon signalé comme `androidTest` hors CI.

Preuve : tests verts ; une migration volontairement cassée les fait tomber.

## Démo et sortie

- URL de source publique redirigée vers une adresse privée : refusée.
- Vingt essais de connexion avec des `X-Forwarded-For` différents : limités.
- Lancer une chaîne sur TV, appuyer sur HOME : plus de son ; revenir : le direct reprend.
- Lire une fixture neutre derrière une redirection `http → https` sur Android.
- Laisser la page web ouverte au-delà du jeton d'accès, puis Lecture : ça lit, pas de
  déconnexion ; deux onglets qui rafraîchissent ensemble : la session survit.
- Migrations Room 1 → 7 vertes en CI.

Checks contract/API/Android/web verts. Les preuves de recette suivent la convention
`docs/releases/0.2.0/qa-evidence/<sprint>-<objet>-<date>/`. Une fois le cycle fermé :
candidate `v0.2.0-rc.2` puis séance ciblée `R020-01` / `R020-16` avant le tag final.

## Avancement

Mis à jour le 10 octobre 2026. Une case cochée signifie **recetté**, pas seulement écrit.

- [ ] S10B-01 — SSRF par redirection — 80 % : code et tests livrés sur `fix/S10B-01-ssrf-redirect`
  (redirections suivies à la main, garde à chaque saut, plafond de 5, pas de descente https → http,
  `IngestionHttpClientRedirectTest` 5/5, rouge vérifié avec l'ancien `Redirect.NORMAL`, suite API 329/329).
  Reste : revue et merge. Limite conservée et documentée : *DNS rebinding* (la connexion re-résout le nom).
- [ ] S10B-02 — IP client et plafond par email — 80 % : code et tests livrés sur `fix/S10B-02-client-ip`
  (`X-Forwarded-For` lu seulement depuis `LUMO_TRUSTED_PROXIES`, vide par défaut, et parcouru par la droite ;
  plafond de 10 essais/min par email, toutes adresses confondues ; `ClientIpTest` 8/8,
  `LoginRateLimitIntegrationTest` 2/2, rouge vérifié sans le plafond ; suite API 339/339).
  Reste : revue et merge. **Constat annexe, hors lot :** le BFF web (Next) appelle l'API côté serveur sans
  transmettre l'IP du navigateur. Tous les utilisateurs web partagent donc la clé IP du serveur Next :
  inscription et mot de passe oublié à 5/min **pour tout le web**. À traiter avec S10B-05 ou dans un lot dédié
  (le BFF transmet `X-Forwarded-For`, son adresse va dans `LUMO_TRUSTED_PROXIES`).
- [ ] S10B-03 — pause en arrière-plan, décision `MediaSession` — 60 % : code et tests livrés sur
  `fix/S10B-03-player-background`. Une règle commune `BackgroundPlayback` (`core:player`) est branchée sur les
  trois ViewModels lecteur, via `PlayerLifecycleEffect` (`ON_START`/`ON_STOP`) dans les six écrans.
  - En quittant l'app, ce qui joue ou charge est mis en pause et la position VOD/épisode est sauvegardée.
  - Le décompte vers l'épisode suivant est arrêté.
  - Au retour, le direct reprend au direct (`resumeAtLiveEdge`), alors qu'un film ou un épisode reste en pause.
  - Une pause décidée par le spectateur n'est jamais relancée.

  Tests : `BackgroundPlaybackTest` 6/6, tests unitaires Android 571/571, les deux APK compilent.
  **Reste :**
  - preuve à la télécommande (son coupé après HOME, reprise au direct). Non jouée le 10/10 : la source
    du banc de l'émulateur est en erreur et la liste de chaînes est vide ;
  - revue et merge.

  **Décision `MediaSession` : reportée** (limite connue 0.2.0). Les touches média dédiées de la
  télécommande (lecture/pause) ne pilotent pas le lecteur. La pause en arrière-plan ne dépend pas d'elle.
- [ ] S10B-04 — redirections et `BEHIND_LIVE_WINDOW` — 60 % : code et tests livrés sur
  `fix/S10B-04-player-network`. La source HTTP du lecteur est explicite : redirections entre protocoles
  suivies, user-agent `LumoTV/1.0`, délais 10 s / 15 s. `BEHIND_LIVE_WINDOW` en direct rejoint le direct
  (`seekToDefaultPosition` + `prepare`), au plus 3 fois de suite. `LiveWindowRecoveryTest` 4/4 ; tests
  unitaires Android verts ; les deux APK compilent. Reste : preuve sur appareil (fixture neutre du banc
  derrière une redirection `http → https`, chaîne mise en pause au-delà de la fenêtre), revue et merge.
- [ ] S10B-05 — session web concurrente et `/api/*` — 0 % (ticket ouvert)
- [ ] S10B-06 — tests de migration Room — 0 % (ticket ouvert)
