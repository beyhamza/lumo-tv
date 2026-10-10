# R020-01 — isolation entre comptes : API (§3.1) et TV (§3.2) — 10/10/2026

- Objet : verrou de sortie vie privée `R020-01`, plan
  [`r020-01-isolation-matrix.md`](../../r020-01-isolation-matrix.md).
- Tête : `dev` @ `b18c6c8` (S10-bis et correctif lint inclus), branche de passage
  `qa/R020-01-isolation-run`.
- API : conteneur `lumo-api` **reconstruit depuis cette tête** le 10/10 (l'image précédente
  datait du 26/09). TV : APK debug construit depuis la même tête, émulateur
  `Television_1080p` (API 36), `adb reverse tcp:8080`.
- Données : deux comptes de recette créés sur l'API locale (identifiants générés, gardés
  hors dépôt dans `apps/api/.env.recette`, ignoré par git), sources neutres du banc
  (`playlist.m3u` pour A, `mixed.m3u` pour B). A porte un favori (« Chaîne 05 ») et une
  chaîne récente (« Chaîne 03 HD »). Aucun contenu réel.

## Verdict

| Section | Verdict |
|---|---|
| §3.1 API, porteur de jeton (A-1 → A-9) | ✅ **Conforme** — automatisé, rouge vu |
| §3.2 TV, activation et session (T-1 → T-3) | ✅ **Conforme sur ce qui est visible** |
| §3.2 TV, déconnexion depuis l'app (hors plan, ajouté) | ⚠️ **Écart `BUG-R020-01-01`** : le cache local de A survit à la déconnexion |
| §3.2 T-4 (ancien jeton TV rejoué) | ➖ couvert par A-7/A-8 côté API, non rejoué depuis l'appareil |
| §3.3 web + mobile | ⏸️ **Non joué** |

`R020-01` passe de « non joué » à **partiel** : la sortie `v0.2.0` finale reste refusée tant
que §3.3 n'est pas jouée et que l'écart n'est pas arbitré.

## §3.1 — API (automatisé)

`apps/api/src/test/java/tv/lumo/api/auth/AccountIsolationIntegrationTest.java`, sur la
fixture partagée `MultiSourceAccountsFixture` (deux comptes, trois sources, favoris
entrelacés, historique, un appareil TV et une paire de jetons par compte). Appels HTTP
réels avec le jeton de chaque compte : filtre de sécurité, contrôleurs et dépôts.

| # | Cas | Résultat |
|---|---|---|
| A-1 | Le jeton de A lit ses données | ✅ |
| A-2 | Le jeton de B ne voit rien de A sur `/me`, favoris, groupes, récentes, sources, appareils | ✅ |
| A-3/A-4 | Source de A (fiche, chaînes, catégories) et lecture d'une chaîne de A avec le jeton de B | ✅ `404`, aucun contenu |
| A-5 | B déplace / retire un favori de A | ✅ `404`, favoris de A inchangés |
| A-6 | Refresh token de A présenté avec le jeton de B | ✅ la session rendue est celle de A, B reste B |
| A-7 | Déconnexion de A | ✅ chaîne de refresh de A révoquée, B intact |
| A-7 bis | B tente de déconnecter A avec le refresh token de A | ✅ `401`, A intact |
| A-8 | Rejeu d'un refresh de A déjà consommé | ✅ `401 REFRESH_TOKEN_REUSED`, chaîne de A révoquée, B intact |
| A-9 | B liste / retire l'appareil de A | ✅ absent, `404` |

**9/9 vert. Rouge vu :** en retirant le filtre de compte de `SourceRepository.findOwned`
(`AND (user_id = :userId OR true)`), A-3/A-4 tombe ; code restauré.

**Observation (pas une fuite entre comptes) :** après une déconnexion, le **jeton d'accès**
de A (JWT, 15 min) reste accepté jusqu'à son expiration ; seule la chaîne de refresh est
révoquée. Le plan attendait un `401` immédiat (A-7). C'est la conception actuelle (jetons
d'accès sans état), non documentée comme telle : à arbitrer, pas à corriger dans ce passage.

## §3.2 — TV (émulateur)

| # | Scénario | Preuve | Résultat |
|---|---|---|---|
| T-3 | `pm clear` puis ouverture | `01-activation-apres-pm-clear.png` | ✅ écran d'activation, aucune donnée |
| T-1 | Code approuvé sur A | `02-tv-activee-sur-A.png` | ✅ source de A, favori et récente de A, rien de B |
| T-2 | `pm clear`, code approuvé sur B | `04-tv-activee-sur-B.png` | ✅ source de B, accueil vide : ni favori ni récente de A |
| T-1′ | Réglages de A : sources, compte, appareils | `06-reglages-A.png` | ✅ seulement la source et les appareils de A |

### Ajouté au plan : déconnexion depuis l'app, puis compte B

Chemin réel d'une TV partagée : « Sign out of this television » (Réglages › Compte), puis
activation sur B, **sans** `pm clear`.

| Étape | Preuve | Constat |
|---|---|---|
| Confirmation de déconnexion | `09-apres-sign-out.png` | « Nothing is removed from your account » |
| Après déconnexion | `10-activation-apres-sign-out.png` + base copiée par `run-as` | écran d'activation ; **la base locale garde `favorite` 1, `favorite_group` 1, `recent_channel` 1, `channel` 2 (« Chaîne 03 HD », « Chaîne 05 », source de A)** ; DataStore `active_source` présent |
| Activation sur B | `11-B-apres-sign-out-A.png` + base | ✅ rien de A à l'écran ; favoris, groupes et récentes remplacés par ceux de B ; **mais les 2 chaînes de A restent en base** (source de A), invisibles |

**`BUG-R020-01-01`** — `AccountRepository.signOut()` (`core:data`) vide la session
(`SessionManager.signOut` → `store.clear()`) et n'efface ni la base Room
(`lumo-catalogue.db`) ni la source active. Conséquences :

- entre la déconnexion et la connexion suivante, l'appareil garde les favoris, l'historique
  et les noms de chaînes de A ;
- après la connexion de B, le catalogue en cache de la source de A reste sur l'appareil,
  invisible dans l'interface.

Le critère « aucune donnée de A **visible ni réutilisable** depuis B » est tenu à l'écran ;
« **sans reste** » ne l'est pas. Exposition limitée : `allowBackup="false"` sur les deux
apps, donc lecture seulement avec un appareil rooté ou débogable. Le module est partagé :
**le mobile a le même comportement** (non rejoué). Correctif probable : purger la base et
la source active dans `signOut()`, avant de rendre la main à l'écran d'activation.

## Non joué

- §3.3 web (W-1, W-2, W-4) et mobile (W-3) ;
- T-4 depuis l'appareil (jeton TV extrait puis rejoué) : couvert côté API par A-7/A-8 ;
- cas limites §4 (disparition ≠ panne, file différée, plafond d'appareils) : relèvent de S11.
