# R020-01 — matrice d'isolation entre comptes (fixture à deux)

- Date : **2026-10-08** · Auteur : **@QA** · Branche : `dev` @ `2ab8cc2`
- Objet : `R020-01` (« Email, session persistante, déconnexion, activation TV,
  **absence de fuite entre comptes** »). C'est un **verrou de sortie vie privée**
  porté par le @PO : aucun `v0.2.0` final tant qu'il n'est pas joué conforme.
- Statut : `⚠️ PARTIEL` (10/10/2026) — §3.1 API **conforme** (automatisé, 9/9, rouge vu),
  §3.2 TV **conforme à l'écran** avec un écart `BUG-R020-01-01` (le cache local de A survit à la
  déconnexion), §3.3 web/mobile **non joué**. Passage :
  [`qa-evidence/r020-01-tv-2026-10-10/RAPPORT.md`](qa-evidence/r020-01-tv-2026-10-10/RAPPORT.md).

Règle tenue : **aucun vert sans rouge d'abord**. Chaque assertion ci-dessous est
d'abord écrite comme test qui échoue sur la tête de référence (ou prouvée par
injection d'un état antérieur), puis rendue verte. Un test jamais vu rouge ne
vaut rien.

---

## 1. Ce qui est déjà couvert (et le trou)

Fragments existants côté API (`apps/api/src/test/java/tv/lumo/api/userdata/UserdataIntegrationTest.java`) :

- favoriser la chaîne d'un autre compte → `404` (pas `403`) ;
- retirer un favori qui n'est pas le sien → `404` ;
- le filtre multi-tenant au niveau **service**, avec un `userId` passé en clair.

**Trou :** ces tests appellent le service avec un `userId` explicite ; ils ne
prouvent **pas** que le porteur d'un **jeton** du compte A ne peut pas lire les
données du compte B à travers la couche HTTP/authentification. Or c'est le seul
chemin réel d'une fuite entre comptes. Aucun test non plus sur la **session TV**
(codes d'activation) ni sur la **rotation/logout** croisés entre comptes.

---

## 2. Contrat de la fixture `multi-sources` (à définir @Dev/@QA)

La fixture doit exposer, au niveau **API**, de quoi jouer la matrice les yeux
fermés. Minimum :

| Élément | Requis | Raison |
|---|---|---|
| Comptes | **A** et **B**, créés indépendamment | isolation |
| Sources chez A | **2 sources** (S1, S2), S1 avec guide + VOD, S2 seule | R020-01 + « deux sources » (S10-05 non joué) |
| Source chez B | **0 ou 1** source distincte | prouver que B ne voit pas S1/S2 |
| Données de A | historique (≥ 1 lecture), favoris (groupe nommé + défaut), source active | surface à fuiter |
| Jetons | **access + refresh** par compte/device, exposés au test | reuse cross-compte |
| Device TV | un device A (`ANDROID_TV`) et un device B | activation/flux device |
| Hook panne | proxy 503 existant (EPG/VOD) réutilisable | disparition ≠ panne |
| Compteur d'écritures | logs côté API compétables | prouver l'absence de file différée |

La fixture est posée par @Dev côté API ; @QA l'étend pour la façade compte/session
(jetons, activation TV, `pm clear`).

---

## 3. Matrice de test

Statuts : ⏸️ à jouer · ✅ conforme · ❌ échec.

### 3.1 API — porteur de jeton (automatisable, priorité 1)

| # | Cas | Attendu |
|---|---|---|
| A-1 | Jeton d'accès de A lit `GET /me`, `/me/favorites`, `/me/recent-channels` | renvoie **les données de A** |
| A-2 | Jeton d'accès de B lit les mêmes routes | **aucune** donnée de A (listes de B uniquement) |
| A-3 | B appelle `GET /sources/{S1_de_A}` avec le jeton de B | `404` (pas `403`, pas de contenu) |
| A-4 | B appelle `GET /sources/{S1_de_A}/channels` avec le jeton de B | `404` |
| A-5 | B appelle `PATCH /me/favorites/{favori_de_A}` | `404`, aucune mutation |
| A-6 | B présente le **refresh token de A** (`POST /auth/refresh`) | `401` (`REFRESH_TOKEN_INVALID`), **pas** de session A |
| A-7 | Jeton d'accès A après `POST /auth/logout` (device A) | `401` ; la chaîne A est révoquée, B intact |
| A-8 | Rejeu d'un refresh token A déjà consommé | `401` `REFRESH_TOKEN_REUSED`, chaîne A révoquée, B intact |
| A-9 | `GET /me/devices` de B | ne liste **pas** le device de A |

### 3.2 TV — activation et session (appareil/émulateur)

| # | Cas | Attendu |
|---|---|---|
| T-1 | TV approuvée sur A puis `pm clear` + réactivation | la TV prend la source active de A ; **rien** de B |
| T-2 | TV A réactivée avec un code approuvé sur **B** | la TV bascule sur B ; aucun historique/source de A |
| T-3 | Session TV A conservée, B ouvre l'app sur le même appareil après `pm clear` | écran d'activation, aucune donnée A |
| T-4 | Réutilisation d'un ancien jeton TV A après déconnexion de A | refusé |

Méthode : `adb shell pm clear tv.lumo.androidtv.debug`, `POST /auth/device/approve`
avec un jeton du compte voulu, `adb reverse tcp:8080 tcp:8080`. Preuve par
`uiautomator dump` + capture (jamais par le seul libellé de dump).

### 3.3 Web + mobile — session persistante et déconnexion

| # | Cas | Attendu |
|---|---|---|
| W-1 | A connecté (web), logout, login B | aucune donnée A en session (`localStorage`/cookies) |
| W-2 | Retour arrière après logout | pas de contenu A rejoué depuis un cache |
| W-3 | Mobile : A connecté, logout, login B | idem W-1 |
| W-4 | Jeton A conservé côté client après logout et rejoué | `401` |

Comptage réseau : côté web la preuve se compte **côté API** (le chargeur EPG est
server-only) ; côté Android via `adb logcat -s LumoHttp:D`.

---

## 4. Cas limites ajoutés par QA (pas dans le libellé PO)

- **Disparition ≠ panne** : contenu retiré de la source → carte `Indisponible`
  conservée ; **banc coupé** (proxy 503) → la carte ne doit **pas** devenir
  « supprimée ». Distinct du cascade de source supprimée (US-024).
- **Hors ligne sans file différée** : aucune écriture rejouée à la reconnexion —
  à prouver par comptage serveur, pas par observation UI.
- **Rotation parallèle** : deux refresh simultanés du **même** device A ne
  doivent pas créer une session pour B (contrat : un seul en vol).
- **Limite de devices** : atteindre le plafond sur A ne doit pas exposer/purouvoir
  un device de B.

---

## 5. Ordre d'exécution proposé

1. Fixture API (2 comptes, 2 sources, jetons) — @Dev, sur `feat/S11-fixture-multisources`.
2. Section **3.1** automatisée par QA (integration test + appels HTTP réels) :
   c'est le cœur du verrou, jouable sans matériel.
3. Section **3.2** sur `Television_1080p` (émulateur) puis TV/box réelle si dispo.
4. Section **3.3** web (Playwright) + mobile (Pixel_10).
5. Rapport de passage dans `docs/releases/0.2.0/qa-evidence/` + ligne `INDEX.md`.

Tant que 3.1 n'est pas verte, `R020-01` reste `⏸️ NON JOUÉ` et la sortie `v0.2.0`
finale reste refusée.
