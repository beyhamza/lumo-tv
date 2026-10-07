# US-021 — Rechercher dans ma source active

Statut : **clôturée le 7 octobre 2026 (S10)** — réalisée sur les trois surfaces et recettée ; la réserve SR-12 TV est levée par la preuve appareil (`qa-evidence/s10-05-03-sr12-2026-10-07/`).
Version cible : 0.2.0. Surfaces : web, Android mobile, Android TV, même priorité.

Première [proposition visuelle](../../design/0.2.0/unified-search.md) disponible
depuis le 19 septembre 2026 ; ses choix de densité et de clavier restent à relire.

## Besoin

Planification proposée : S10.
Voir le [plan 0.2.0](../../roadmap/0.2.0/delivery-plan.md) ; réalisation livrée en S10, recettée le 7 octobre 2026.

En tant qu'utilisateur, je veux rechercher une chaîne, un film ou une série depuis
un seul champ, afin de retrouver rapidement un élément de ma source active.

## Critères d'acceptation validés

- La recherche porte sur le nom des chaînes et le titre des films et séries,
  uniquement dans la source active, dont le nom reste visible.
- Une partie du nom ou du titre suffit ; majuscules et minuscules sont équivalentes.
- Tous présente des résultats regroupés en Chaînes, Films et Séries.
- Les filtres Tous, Chaînes, Films et Séries sont proposés selon les types de contenu
  disponibles dans la source. Un type présent dans la source mais sans correspondance
  pour la requête ne doit pas être confondu avec un type absent du catalogue.
- Voir tous les résultats ouvre la liste du type concerné en conservant la recherche.
- Sélectionner une chaîne lance immédiatement le direct ; sélectionner un film ou
  une série ouvre sa fiche avec accès à la lecture ou à la reprise.
- Au retour d'une fiche ou du lecteur, conserver recherche, filtre et position.
- Les résultats s'actualisent automatiquement après une courte pause dans la saisie.
- Champ vide : afficher une invitation à rechercher, sans charger tout le catalogue.
- Sans résultat : rappeler le texte recherché et la source active, proposer d'effacer.
- En cas d'erreur partielle : garder les résultats disponibles, indiquer la section
  en erreur et proposer de réessayer cette section. Une erreur n'est pas un résultat vide.
- Aucun historique de recherches n'est conservé. Le retour de navigation conserve
  néanmoins la recherche en cours.
- Sur mobile, l'accès se situe en haut d'Explorer ; sur TV/web, dans la navigation
  principale. Sur TV, saisie au clavier à l'écran et accès aux résultats au D-pad.
- Libellés FR/EN, navigation au clavier sur le web et à la télécommande sur TV.

## Couverture contractuelle

Les opérations existantes de liste de chaînes, films et séries par source portent
le paramètre `q` et sont paginées dans `packages/contracts/openapi.yaml`.
La recherche est une correspondance partielle insensible à la casse ; elle ne
promet ni correction des fautes ni classement par pertinence.

Une présentation unifiée peut composer ces recherches existantes. Aucun endpoint
nouveau n'est défini ici. L'implémentation devra vérifier la parité des recherches
locales Android avec la sémantique contractuelle.

## Tâches (découpage Tech Lead, 30 septembre 2026)

Ordre imposé : S10-00 → S10-01 → (S10-02 → S10-03/S10-04) → S10-05. Le web dépend
de S10-00 une fois le contrat fixé ; Android dépend de S10-00 **et** S10-01. Un lot
qui couvre trois surfaces vaut **trois PR** (une par surface), jamais une.

| ID | Dépend de | Travail | Module(s) | PR |
|---|---|---|---|---|
| S10-00 | — | Fixer le contrat `q` : corriger la description `q` des chaînes, ajouter le test d'intégration de recherche séries manquant, épingler casse/accent et la limite 100 | `packages/contracts/openapi.yaml` ; `apps/api/src/test/java/tv/lumo/api/catalog/` ; `apps/android/core/database/src/test/` | 1 |
| S10-01 | S10-00 | Cœur de recherche Android : composer les trois dépôts, debounce 350 ms, identité de contexte (compte/source/texte/type/page/taille), réponses obsolètes ignorées, `trim` + limite 100, aperçu 4 / pages 20 | `apps/android/core/data` ; `apps/android/feature/search` | 1 |
| S10-02 | S10-00 (web) ; S10-01 (Android) | Sections Tous/Chaînes/Films/Séries, Voir tous, pagination 20 | web : route BFF `apps/web/src/app/api/sources/[id]/search/route.ts`, page `apps/web/src/app/[locale]/app/search/`, `apps/web/src/lib/search/`, `apps/web/src/messages/*.json`, navigation `apps/web/src/components/site/` et `app/layout.tsx` ; mobile/TV : `apps/android/feature/search` | 3 |
| S10-03 | S10-02 | Lecture d'une chaîne, fiche film/série, retour avec texte/filtre/pages/position | web : routes fiche et player existantes ; Android : `feature/search` → `feature/vod`, `feature/series`, `feature/live` | 3 |
| S10-04 | S10-02 ; S10-01 (erreur partielle Android) | Champ vide, aucun résultat, erreur partielle + réessai ciblé, hors ligne | 3 surfaces (web `lib/search`, `components/app` ; Android `feature/search`) | 3 |
| S10-05 | S10-00→04 | Recette banc SR-01→SR-15, deux sources, erreurs et latence ; la seule séance manuelle restante = clavier TV et D-pad réels | — | recette |

Points d'attention :

- S10-00 : changement de **description seule** — ne pas versionner, ne pas toucher aux
  changesets Liquibase mergés (`0005`, `0015`, `0016`) ni à la sémantique `q`. Le retrait
  des espaces et `blank → null` sont déjà dans `CatalogController` (l.79/112/180) et
  valent pour les trois listes.
- S10-01 : une seule vérité de contexte par requête ; ne jamais afficher une réponse
  d'une autre source, d'un autre compte ou d'un autre texte. Pas de pool de threads
  maison (Flow / virtual threads).
- S10-02 web : le client API est `server-only` (jeton httpOnly) — la recherche web ne
  peut pas appeler l'API depuis le navigateur. Il faut une route BFF qui compose les
  trois listes côté serveur et renvoie les statuts par section. Aucun endpoint backend
  nouveau.
- S10-02/03 : aperçu de 4 et page de 20 sont des requêtes distinctes ; ne pas réutiliser
  un index calculé pour `size=4` sur `size=20`. Les filtres ne listent que les types
  présents dans la source ; un type présent sans correspondance garde son filtre et son
  état vide.
- S10-03 : aucune persistance d'historique ; retour limité au parcours si compte et
  source inchangés.
- S10-04 : une erreur n'est jamais un ensemble vide ; le réessai n'atteint qu'une
  section ou une page ; pas de nouvelle stratégie de cache.

## Vérification contractuelle `q` (Tech Lead, 30 septembre 2026)

Vérifié dans le code, pas déduit du descriptif :

- Les trois lectures serveur appliquent la **même** condition
  `name ILIKE '%' || :search || '%'` (chaînes `ch.name`, films `v.name`, séries
  `sr.name`) dans `CatalogReadRepository.findChannels/findVod/findSeries` et leurs
  `count*`. `CatalogController` normalise les trois identiquement (`q` nul ou blanc →
  `null`, sinon `q.trim()`).
- L'index `pg_trgm` (GIN `gin_trgm_ops` sur les trois noms : `0005-catalog.sql`,
  `0015-vod.sql`, `0016-series.sql`) accélère `ILIKE '%q%'`. **Il n'apporte aucune
  tolérance aux fautes** : une saisie approchée ne correspond pas plus qu'une
  sous-chaîne exacte. La parité est donc réelle : sous-chaîne insensible à la casse,
  sans approximation.
- La description `q` des chaînes (`packages/contracts/openapi.yaml` l.848,
  « typo-tolerant (trigram) ») est **fausse** et contredit films (l.935-936) et séries
  (l.1371). Correction = description seule, en S10-00.
- Tests : `ChannelLookupIntegrationTest` et `VodCatalogIntegrationTest` couvrent `q` ;
  `SeriesCatalogIntegrationTest` **ne passe jamais de `search` non nul** à `findSeries`
  — le test manque, à ajouter en S10-00.
- Parité Android : les DAO Room (`ChannelDao`/`VodDao`/`SeriesDao.pagedBySearch`) font
  `name LIKE '%' || :query || '%'`. SQLite `LIKE` n'est insensible à la casse que pour
  l'ASCII ; PostgreSQL `ILIKE` suit la collation. Écart réel possible sur les majuscules
  non ASCII (« É » vs « é »). Seule question technique non tranchée : accepter le
  « au mieux » local ou normaliser. L'insensibilité aux **accents** n'est promise par
  aucune surface (test explicite dans `VodCatalogIntegrationTest`).
- Limite : `minLength: 1` / `maxLength: 100` sur les trois `q` ; l'unité de comptage
  (caractères Unicode) reste à aligner clients/validateur, vérification SR-05.

## Avant planification

Les [règles Q9 et cas SR-01 à SR-15](../../design/0.2.0/search-interactions.md)
précisent la suite confiée à l’agent : délai 350 ms, quatre résultats par type
dans Tous, pages de 20 sur demande, clavier de plateforme et focus stable.
Au changement de source, garder le texte mais revenir à Tous/page 0 ; ignorer
toute réponse de l’ancien contexte. Au changement de compte, vider le contexte.

Avant réalisation, vérifier la parité de recherche Android/API, les capacités
locales hors ligne, le comptage Unicode de la limite de 100 caractères et le
clavier TV réel. Le descriptif contractuel de `q` des chaînes mentionne la
tolérance aux fautes alors que les listes films/séries décrivent une sous-chaîne :
vérifier et résoudre cette divergence avant de promettre une sémantique identique.

## Recette à préparer

Avec des données de banc neutres, couvrir les trois types, le filtrage par source,
les recherches partielles et la casse. Vérifier le champ vide, aucun résultat,
une section en erreur, la saisie rapide, le retour du lecteur et d'une fiche.
Prévoir une recette sur les trois surfaces, avec télécommande réelle pour la TV.

## Hors périmètre de cette story

Recherche par description, acteur, épisode ou programme EPG ; recherche entre
plusieurs sources ; historique persistant. Toute extension demande un nouveau cadrage.

Référence : [décisions produit](../../roadmap/0.2.0/decisions.md).
