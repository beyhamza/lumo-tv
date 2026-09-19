# Lot C4 — Consulter l'ancien catalogue et supprimer une source

Tâche : [S8-01](../../backlog/sprint-08.md). Date : 19 septembre 2026.
Statut : **lot validé par l'utilisateur le 19 septembre 2026** (D1 à D5, §4).
S8-02 l'implémente : contrat, régénération des trois clients, serveur et tests.

Ce document ferme le cadrage demandé par C4 et Q4 dans le
[registre](open-questions.md). Il ne crée **aucun endpoint**, ne touche ni au
chiffrement ni aux droits d'accès, et ne demande **aucun ADR** : ni la persistance
ni le cache ne changent.

## 1. Ce que le code fait aujourd'hui

Constats du 19 septembre, relevés dans le code et non supposés.

| Sujet | Constat | Où |
|---|---|---|
| L'ancien catalogue existe-t-il pendant une actualisation ? | **Oui.** L'ingestion fait un *upsert* sur `(source_id, external_id)`, puis supprime ce qui n'a pas été revu, et seulement après une passe réussie. Les lignes ne sont jamais vidées | `CatalogWriteRepository`, `IngestionService` |
| Et après un échec ? | Oui aussi. Les lignes déjà réécrites portent leurs nouvelles valeurs, les autres les anciennes, aucune n'est supprimée. Le catalogue est un **sur-ensemble cohérent**, sans référence orpheline | idem |
| Pourquoi le client ne le voit-il pas ? | Une garde refuse les listes dès que `status != READY` : `409 SOURCE_NOT_READY` | `CatalogController.requireReadableSource` |
| Quelles lectures sont concernées ? | Catégories, chaînes, films, séries, épisodes (et donc la recherche, qui est le paramètre `q` de ces listes). Les fiches `GET /vod/{id}`, `GET /series/{id}` et l'EPG d'une chaîne ne sont **pas** gardés | `CatalogController` |
| La lecture des flux | Garde **distincte**, `requirePlayableSource` : `READY` exigé, puis expiration | `CatalogController` |
| Comment savoir qu'un catalogue existe ? | `last_synced_at` : posé à la première ingestion réussie, jamais effacé ensuite — ni par un échec, ni par une actualisation, ni par une correction d'identifiants qui renvoie la source en `PENDING` | `SourceRepository.markReady`, `.update` |
| Compteurs de `Source` | `channel_count` et `category_count` ne sont calculés que si `status == READY` : ils **disparaissent** pendant une actualisation | `SourceService.toApi` |
| Cascades de suppression | Les neuf tables liées à `source` sont en `ON DELETE CASCADE` : catégories, chaînes, EPG, films, séries (et saisons, épisodes), favoris, chaînes récentes, **progressions** | changesets `0005`, `0006`, `0007`, `0013`, `0015`, `0016` |
| Ce que le contrat en dit | La description de `DELETE /sources/{id}` ne cite que catégories, chaînes, EPG et favoris. Elle date d'avant les films et les séries | `openapi.yaml` |
| Délai avant nouvelle tentative | `429 SOURCE_SYNC_RATE_LIMITED` et `Retry-After` sont au contrat. **Rien ne les émet** : aucune limite n'existe sur `POST /sources/{id}/sync` | `SourceService.sync` |

Trois écarts entre le contrat et le serveur, trouvés en chemin :

1. `/series/{id}` déclare un `409 SOURCE_NOT_READY` que le contrôleur n'émet jamais.
2. `/sources/{id}/episodes` peut répondre `409 SOURCE_NOT_READY`, non déclaré.
3. `PUT /me/progress` répond `404 SOURCE_NOT_FOUND` pour une source disparue, non déclaré.

## 2. Proposition

### P1 — La consultation dépend de l'existence d'un catalogue, plus du statut

Une source est **consultable** dès que `last_synced_at` est non nul, quel que soit
son statut. Sinon — première ingestion en attente, en cours ou échouée — la réponse
reste `409 SOURCE_NOT_READY`, et le client continue d'interroger `GET /sources/{id}`.

| Statut | `last_synced_at` | Listes du catalogue | Lecture d'un flux |
|---|---|---|---|
| `READY` | non nul | 200 | autorisée |
| `SYNCING`, `PENDING` | non nul | **200, catalogue précédent** | refusée, `SOURCE_NOT_READY` |
| `ERROR` | non nul | **200, catalogue précédent** | **autorisée**, sauf `SOURCE_AUTH_FAILED` et `SOURCE_EXPIRED` (D2) |
| tout statut | nul | 409 `SOURCE_NOT_READY` | refusée |

Le client sait déjà distinguer ces cas sans nouveau champ : `status`, `sync_step`,
`error_code`, `last_error_at` et `last_synced_at` sont dans `Source`. C'est lui qui
affiche « actualisation en cours » ou « ce catalogue peut être ancien ».

### P2 — La lecture reste fermée pendant une ingestion, plus après un échec passager

Consulter n'est pas lire. Décisions utilisateur du 19 septembre :

- la lecture reste fermée en `PENDING` et `SYNCING`, comme aujourd'hui ;
- en `ERROR`, **avec** un catalogue précédent, la lecture est autorisée : un
  `auto_sync` nocturne qui échoue sur une panne passagère du fournisseur ne doit
  plus bloquer toute lecture jusqu'à la synchronisation réussie suivante (D2) ;
- elle reste refusée en `ERROR` quand `error_code` vaut `SOURCE_AUTH_FAILED` ou
  `SOURCE_EXPIRED` : les identifiants ou l'abonnement sont en cause, le flux
  échouerait de toute façon, et le message utile est celui de l'erreur. Le refus
  porte ce code-là — `SOURCE_EXPIRED` est déjà un code de refus de lecture ;
  `SOURCE_AUTH_FAILED` s'y ajoute dans la description des trois `409` de lecture.

La garde d'expiration existante (`expires_at` dépassé) ne change pas. Les droits
d'accès (`Entitlement`) ne sont pas concernés : la règle porte sur l'état de la
source, pas sur l'offre de l'utilisateur.

### P3 — Les compteurs suivent la même règle

`channel_count` et `category_count` sont renseignés dès que `last_synced_at` est non
nul, et non plus seulement en `READY`. Mes sources peut ainsi afficher « 1 248
chaînes » pendant une actualisation au lieu d'une valeur inconnue.

Aucun compteur films/séries n'est ajouté à `Source`. Les clients lisent
`total_elements` des listes paginées avec `size=1`, désormais accessibles pendant
une actualisation grâce à P1. Pour une source M3U, qui n'a pas de séries
(`adr/0010`), le client n'affiche pas de compteur de séries plutôt qu'un zéro.

### P4 — Le contrat dit tout ce que la suppression emporte

Réécrire la description de `DELETE /sources/{id}` : catégories, chaînes, guide,
films, séries avec leurs saisons et épisodes, favoris, chaînes récentes et
progressions de lecture. Les données des autres sources ne sont pas touchées.
La liste À regarder s'y ajoutera avec son propre lot (C2, S11).

Un test d'intégration prouve la cascade table par table, y compris que rien d'une
seconde source n'a bougé.

### P5 — Le délai serveur existe vraiment

Implémenter la limite déjà décrite au contrat, sans le modifier : une
synchronisation **manuelle** par source toutes les **5 minutes** (valeur
configurable, `lumo.rate-limit`). Au-delà : `429 SOURCE_SYNC_RATE_LIMITED` avec
`Retry-After` en secondes. L'ordre des refus est `404`, puis `409
SOURCE_SYNC_IN_PROGRESS`, puis `429`.

Ne comptent pas : le balayage `auto_sync`, et la réingestion déclenchée par une
correction d'identifiants (`PATCH`) — corriger son mot de passe ne doit jamais
faire attendre. Le limiteur existant est par instance ; c'est acceptable en v1
(une seule instance), comme pour la connexion.

### P6 — Constater une suppression faite ailleurs

Aucun changement de contrat. La preuve d'une suppression est une réponse
**`404 SOURCE_NOT_FOUND`** du serveur, ou l'absence de la source dans un
`GET /sources` réussi. Une erreur réseau, un délai dépassé ou un `5xx` ne prouvent
rien (US-018, US-024).

Les clients vérifient : au retour au premier plan, après une reconnexion, et toutes
les 60 secondes pendant une lecture — seul moment où l'application ne parle pas à
l'API d'elle-même, puisque le flux vient du serveur de l'utilisateur.

### P7 — Aligner les trois écarts

Retirer le `409` de `/series/{id}` ; déclarer le `409` de `/sources/{id}/episodes`
et le `404` de `PUT /me/progress`. Ce sont des corrections de description : le
comportement du serveur ne change pas.

## 3. Ce que le lot change dans `openapi.yaml`

| Endroit | Changement | Nature |
|---|---|---|
| `components/responses/SourceNotReady` | « aucun catalogue n'a encore été ingéré », au lieu de « ingestion non terminée » | description |
| `SourceStatus` | préciser qu'un catalogue précédent reste consultable hors `READY`, et que la lecture exige `READY` | description |
| `Source.channel_count`, `category_count` | « null tant que `last_synced_at` est null » | description, comportement P3 |
| `DELETE /sources/{id}` | liste complète des cascades | description |
| `/series/{id}`, `/sources/{id}/episodes`, `PUT /me/progress` | P7 | réponses déclarées |
| `409` des trois lectures (chaîne, film, épisode) | P2 : lecture possible en `ERROR` avec catalogue ; `SOURCE_AUTH_FAILED` parmi les codes de refus | description |

Aucun schéma, aucun champ, aucun chemin, aucun code d'erreur nouveau. Les trois
clients sont régénérés ; le diff attendu se limite aux commentaires et aux réponses
déclarées. La PR annonce la modification du contrat (`AGENTS.md` §3).

## 4. Décisions — validées le 19 septembre 2026

| ID | Question | Décision |
|---|---|---|
| D1 | Consultation ouverte dès que `last_synced_at` est non nul (P1, P3) | **Oui** |
| D2 | Lecture d'une source en `ERROR` qui a un ancien catalogue | **Autorisée**, sauf `SOURCE_AUTH_FAILED` et `SOURCE_EXPIRED`. Fermée en `PENDING` et `SYNCING` |
| D3 | Après une correction d'URL ou d'identifiants, le catalogue affiché pendant la réingestion est celui de l'ancienne adresse | **Accepté**, avec le message « peut être ancien » |
| D4 | Limite des synchronisations manuelles | **1 par source toutes les 5 minutes**, configurable ; `auto_sync` et `PATCH` non comptés |
| D5 | Vérification de l'existence de la source pendant une lecture | **Toutes les 60 secondes**, plus premier plan et reconnexion |

## 5. Tests attendus en S8-02

- Listes en `SYNCING`, `PENDING` et `ERROR` avec `last_synced_at` non nul : 200 et
  contenu précédent. Avec `last_synced_at` nul : 409.
- Lecture d'un flux : refusée en `SYNCING` et `PENDING` ; autorisée en `ERROR` avec
  catalogue ; refusée en `ERROR` pour `SOURCE_AUTH_FAILED` et `SOURCE_EXPIRED`, et
  en `ERROR` sans catalogue.
- Compteurs présents pendant une actualisation, absents avant la première réussite.
- Échec à mi-parcours d'une actualisation : le catalogue reste listable et complet.
- Cascade de suppression, table par table, seconde source intacte.
- Limite : deuxième `POST /sync` refusé avec `Retry-After` ; `auto_sync` et `PATCH`
  non comptés ; ordre 404 → 409 → 429.

## 6. Avancement de S8-01

- [x] Relever le comportement réel du serveur (gardes, ingestion, cascades, limite)
- [x] Relever les écarts contrat / serveur
- [x] Rédiger la proposition contractuelle
- [x] Validation de D1 à D5 par l'utilisateur (19 septembre 2026)
- [x] Reporter les décisions dans `open-questions.md`, `decisions.md` et US-024

S8-01 : **100 %**. La suite est S8-02.
