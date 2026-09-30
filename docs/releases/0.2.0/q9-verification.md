# Vérification Q9 — Recherche (délai, aperçu, pagination, contexte, clavier)

Date : 30 septembre 2026. Rédigé par Dev dans le cadre de **S10-00**.
Statut : vérification de cadrage **partielle et honnête**. S10 n'est pas
implémenté à cette date : ce document établit ce qui est **vérifiable
maintenant** (contrat, parité Android, tests existants) et ce qui reste à
**jouer** dans S10-01 à S10-05 puis en recette SR-01 à SR-15.

Sources de vérité :

- [Règles d'interaction Q9 et recette SR-01 à SR-15](../../design/0.2.0/search-interactions.md) ;
- [US-021 — recherche unifiée](../../backlog/stories/US-021-unified-search.md) ;
- [Sprint 10](../../backlog/sprint-10.md) ;
- [Décisions produit du 30/09](../../backlog/DECISIONS-PRODUIT.md), ligne « Découpe S10 acceptée ».

Ce document ne remplace pas la recette. Un test unitaire vert prouve que le code
fait ce que le dev voulait ; il ne prouve pas que le cadrage est tenu sur un
appareil. Les cas qui exigent un clavier système, un D-pad ou une télécommande
réelle sont marqués **manuel**.

---

## 0. Corrections contractuelles livrées par S10-00

| # | Constat | Correctif | Preuve |
|---|---|---|---|
| C-1 | Le descriptif de `q` des chaînes promettait « typo-tolerant (trigram) », alors que films et séries décrivent une sous-chaîne insensible à la casse et que l'index trigramme n'est **pas** approximatif. C'est la divergence relevée en §« Couverture contractuelle » des règles Q9. | Descriptif réécrit sur le même modèle que films/séries : sous-chaîne insensible à la casse, index trigramme = accélérateur. **Description seule** : ni version d'API, ni Liquibase, aucune sémantique touchée. | `packages/contracts/openapi.yaml:845-853` (chaînes), `:934-942` (films), `:1370-1378` (séries) |
| C-2 | Les trois listes portaient un test `q` pour chaînes et films, mais **aucun** pour séries. | Test `searchAndCategoryCompose` ajouté à `SeriesCatalogIntegrationTest`, calqué sur `VodCatalogIntegrationTest.filtersCompose`. | `apps/api/src/test/java/tv/lumo/api/catalog/SeriesCatalogIntegrationTest.java` (nouveau test) ; modèles : `ChannelLookupIntegrationTest.java:109`, `VodCatalogIntegrationTest.java:116` |

**Note résiduelle (non corrigée, volontairement).** Le commentaire du changeset
**mergé** `0005-06` dit encore « typo-tolerant search (ADR 0002) »
(`apps/api/src/main/resources/db/changelog/0005-catalog.sql:53`), et
`0001-extensions.sql:9` parle de « typo-tolerant channel search ». Un changeset
mergé ne se modifie jamais : la divergence de **commentaire** est laissée telle
quelle et signalée ici plutôt que corrigée.

---

## 1. Ce qui est vérifié maintenant

### 1.1 Parité de recherche Android ↔ API — le point Q9 central

C'est le seul point de comportement que S10-00 peut vérifier sans UI, parce
qu'il vit déjà dans le dépôt.

**Verdict : parité approximative, au mieux locale. La casse est au mieux
ASCII côté Android ; les accents sont significatifs des deux côtés.**

| | Android (cache local) | API (source) |
|---|---|---|
| Requête | `name LIKE '%' \|\| :query \|\| '%'` | `name ILIKE '%' \|\| :search \|\| '%'` |
| Emplacement | `ChannelDao.kt:52`, `SeriesDao.kt:47`, `VodDao.kt:49` | `CatalogReadRepository.java:101/126` (chaînes), `:382/407` (films), `:523/548` (séries) |
| Casse | `LIKE` SQLite : insensible **ASCII uniquement** ; sensible à la casse hors ASCII | `ILIKE` PostgreSQL : suit la collation de la base |
| Accents | non repliés | non repliés — aucune extension `unaccent` (`0001-extensions.sql` ne crée que `citext` et `pg_trgm`) |
| Type de recherche | sous-chaîne | sous-chaîne ; l'index `pg_trgm` accélère, n'approxime pas |

Conséquences pratiques à ne pas transformer en promesse :

- une saisie `FALAISES` trouve `Les Falaises` sur les deux surfaces (ASCII) ;
- une saisie avec un caractère non-ASCII accentué en majuscule (`É`) peut
  diverger : SQLite `LIKE` est alors sensible à la casse, Postgres `ILIKE` peut
  plier selon la collation ;
- aucune des deux ne replie les accents : `cotes` ne trouve pas `Les Côtes`.
  La story ne promet qu'une « recherche partielle insensible à la casse » — c'est
  exactement ce que les deux implémentations tiennent, et rien de plus.

**Attention pagination locale.** Le pager Android local utilise une taille de
page de **60** (`CataloguePager.kt:51`), pas 20. Les « pages de 20 » de Q9 sont
la taille de la **liste de recherche réseau** : S10-01 doit fixer 20 pour la
recherche unifiée sans réutiliser `CataloguePager.PAGE_SIZE` par défaut. Ce n'est
pas un défaut en S10-00, c'est un raccord à ne pas manquer.

**Comment vérifier complètement la parité** (proposé) : un test unitaire Android
Room (`room-testing`, base en mémoire) insérant `Les Falaises` / `Été` / `Les
Côtes` et exécutant `pagedBySearch` sur `falaises`, `FALAISES`, `été`, `ÉTÉ`,
`cotes` ; le même jeu de chaînes côté API dans `SeriesCatalogIntegrationTest`.
Les deux tables de résultats réunies montrent la limite exacte.

### 1.2 Bornes contractuelles dont dépendent les règles Q9

| Règle Q9 | Vérifiable maintenant ? | Preuve |
|---|---|---|
| `q` de 1 à 100 caractères sur les trois listes | **Oui** | `openapi.yaml:845-853`, `:934-942`, `:1370-1378` (`minLength: 1`, `maxLength: 100`) |
| `page` à partir de zéro | **Oui** | `openapi.yaml:2097` (`minimum: 0`, `default: 0`) |
| `size` de 1 à 200 | **Oui** | `openapi.yaml:2107` (`minimum: 1`, `maximum: 200`, `default: 50`) |
| Les tailles 4 et 20 sont dans les bornes | **Oui** | 4 et 20 ∈ [1, 200] |
| Les réponses portent `total_elements` et `total_pages` | **Oui** | `ChannelPage` `openapi.yaml:3416`/`:3437`, `VodItemPage` `:3533`/`:3554`, `SeriesPage` `:3638`/`:3659` |
| Aucun endpoint nouveau demandé par le cadrage | **Oui** | Les trois listes existantes suffisent ; le BFF web (`app/api/sources/[id]/search/`) est une route Next, pas une opération API |

### 1.3 Un test `q` par liste

| Liste | Test existant | Statut |
|---|---|---|
| Chaînes | `ChannelLookupIntegrationTest.java:109` — `composeWithTheOtherFilters` (catégorie + `"02"` + `ids`) | vert |
| Films | `VodCatalogIntegrationTest.java:116` — `filtersCompose` (catégorie + `"travers"`, puis `"voyage"`) | vert |
| Séries | **ajouté par S10-00** — `searchAndCategoryCompose` (catégorie Drame/Comédie + `"falaises"`/`"FALAISES"`) | à valider par l'acceptation S10-00 |

---

## 2. Ce qui n'est pas encore vérifiable (et comment le vérifier)

S10-01 à S10-05 ne sont pas commencés ; `feature/search` ne contient que des
écrans d'attente (`SearchMobileScreen.kt`, `SearchTvScreen.kt`, chaînes FR/EN).
Les règles ci-dessous sont **spécifiées, non implémentées, non vérifiées**.
Chaque ligne propose la preuve à produire.

### 2.1 Anti-rebond 350 ms — règles Q9 l.14-18, SR-01/02/03

- **Verdict S10-00** : décision de cadrage confirmée, non implémentée.
- **À vérifier en** : S10-01 (Android) et S10-02 (web).
- **Preuve proposée** : test du ViewModel de recherche avec horloge virtuelle
  (`kotlinx-coroutines-test`, `TestDispatcher` + `advanceTimeBy`) : après
  plusieurs modifications, aucune requête avant 350 ms, une seule après ;
  `Entrée`/`Rechercher` déclenche immédiatement et **sans doublon** à
  l'échéance ; pendant une composition de texte non validée, aucune requête.
- **Web** : test du composant client avec `vi.useFakeTimers`.

### 2.2 Aperçu de 4 — règles Q9 l.26-28, SR-06

- **Verdict S10-00** : spécifié, non implémenté.
- **Preuve proposée** : fonction pure de requête (`size=4` pour l'aperçu de
  `Tous`, `size=20`/`page=0` pour « Voir tous ») + test ViewModel prouvant que
  « Voir tous » ne réutilise pas un index calculé pour la taille 4 et ne
  concatène pas les 4 cartes une seconde fois. `Voir tous` n'apparaît que si un
  type dépasse 4 ; l'ordre reste celui de l'API, sans score de pertinence.

### 2.3 Pages de 20 — règles Q9 l.29-37, SR-06/07

- **Verdict S10-00** : spécifié, non implémenté. `size=20` est dans les bornes
  du contrat (§1.2).
- **Preuve proposée** : test ViewModel « Afficher plus » → `page+1, size=20`,
  résultats précédents conservés pendant le chargement, échec de la page
  suivante → réessai ciblé sur cette page seulement, sans retour en tête.
  Instrumenté possible si l'écran expose un état de pagination testable.

### 2.4 Changement de source — règles Q9 l.51-54, SR-09

- **Verdict S10-00** : spécifié, non implémenté.
- **Preuve proposée** : test ViewModel avec deux sources ; à la bascule, le
  texte est conservé, le filtre revient à `Tous`, la page à 0, les résultats de
  l'ancienne source disparaissent, et la recherche repart sur la nouvelle si le
  texte est valide. Le test doit aussi émettre une réponse de l'**ancien**
  contexte **après** la bascule et vérifier qu'elle est ignorée.

### 2.5 Changement de compte — règles Q9 l.55, SR-15

- **Verdict S10-00** : spécifié, non implémenté.
- **Preuve proposée** : test ViewModel : changer de compte vide le contexte
  (texte, source, filtre, pages, résultats) ; aucune réponse ni requête du
  compte précédent n'est affichée une fois le nouveau compte actif.

### 2.6 Clavier et focus TV — règles Q9 l.65-81, SR-13 (partiellement manuel)

- **Verdict S10-00** : spécifié, non implémenté. Aucun clavier propriétaire n'est
  attendu : OK/Entrée ouvre le clavier **de la plateforme**.
- **Preuve proposée** : test instrumenté Compose
  (`createAndroidComposeRule`) — à l'entrée, focus sur le champ **sans** clavier
  ouvert ; une réponse tardive ne vole pas le focus ; « Voir les résultats » garde
  le focus pendant le chargement puis rejoint le premier résultat quand
  l'utilisateur le redemande. **Manuel** sur télécommande : touches système,
  Retour (ferme d'abord le clavier) et D-pad, sur appareil réel.

### 2.7 Contexte de réponse (réponses obsolètes) — règles Q9 l.39-49, SR-08/10

- **Verdict S10-00** : spécifié, non implémenté. C'est la règle qui interdit
  qu'une réponse d'une saisie précédente écrase le contexte courant.
- **Preuve proposée** : test ViewModel avec des réponses contrôlées (un
  `CompletableDeferred` par appel) libérées dans le désordre ; seule la réponse
  du contexte courant s'applique. Erreur partielle : les sections réussies de la
  **même** recherche sont conservées, le réessai ne relance que la section en
  échec.

### 2.8 Reste du cadrage

Les règles sur le texte vide / `trim` / limite 100 et message FR-EN (SR-04/05),
le type absent vs présent sans résultat (SR-11), la restitution après retour de
fiche ou du lecteur (SR-12), et la recherche locale honnête (SR-14) sont
également **spécifiées, non implémentées**. Elles se vérifient par les mêmes
tests ViewModel ciblés, plus la recette manuelle pour les cas réseau et
« données potentiellement anciennes ».

---

## 3. Verdict global

- **Contrat** : la divergence de descriptif `q` des chaînes est corrigée (C-1) ;
  les bornes dont dépend Q9 (`q` 1-100, `page` ≥ 0, `size` 1-200,
  `total_elements`/`total_pages`) sont présentes sur les trois listes.
- **Tests `q`** : chaînes, films et séries en ont un (C-2).
- **Parité Android** : `LIKE` local (casse ASCII seulement) vs `ILIKE` API
  (collation) ⇒ **casse/accents : au mieux local**. Aucune des deux ne replie
  les accents ; la promesse « insensible à la casse » est tenue, pas au-delà.
- **Règles d'interaction** (350 ms, 4, 20, changement de source/compte, clavier)
  : **spécifiées, non implémentées, donc non vérifiées en S10-00**. Chacune a sa
  preuve proposée ci-dessus et reste à couvrir par S10-01 à S10-05 puis SR-01 à
  SR-15.
- **Aucun défaut produit** relevé ; les deux écarts traités sont contractuels et
  de test.
