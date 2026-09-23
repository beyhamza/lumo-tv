# C1 — Proposition de lecture groupée et fraîcheur EPG

Date : 23 septembre 2026. **Cadrage D1–D5 validé par l’utilisateur le 24 septembre ; non implémenté.**
Le [banc S9-00 exécuté](../../releases/0.2.0/s9-00-epg-bench.md) recommande de conserver
les plafonds proposés ; leur gel final appartient à S9-01. Cette validation autorise
le banc, pas une déclaration de livraison du contrat ou des clients.
Préparation de [S9-01](../../backlog/sprint-09.md), après les preuves de S9-00.
Le contrat OpenAPI reste inchangé. Les noms de route, champs et codes ci-dessous
sont des candidats à relire, jamais des capacités déjà disponibles.

## 1. Constats vérifiés dans le dépôt

| Sujet | Constat au 23 septembre | Référence |
|---|---|---|
| Lecture actuelle | Une chaîne par appel ; fenêtre par défaut 24 h, maximum 4 jours ; recouvrement `ends_at > from` et `starts_at < to` | `getChannelEpg`, OpenAPI et `CatalogController` |
| Isolation | Jointure chaîne → source → propriétaire, association EPG par `(source_id, tvg_id)` | `CatalogReadRepository.findProgrammes` |
| Absence | Sans `tvg_id`, résultat vide ; une liste vide n’en révèle pas la cause | Contrat et requête actuelle |
| Ingestion | EPG tenté si `epgUrl` renseigné ; une erreur d’ingestion EPG attrapée n’empêche pas `markReady` | `IngestionService.ingest` et `ingestEpg` |
| Écriture | Upserts par lots ; une erreur après plusieurs lots peut laisser des programmes mis à jour et d’autres anciens | `CatalogWriteRepository.upsertProgrammes` |
| Identité | Clé naturelle `(source_id, tvg_id, starts_at)` ; UUID préservé lors d’un conflit | changeset `0006-epg.sql` |
| Fraîcheur | Pas de date dédiée au dernier import EPG réussi dans le modèle actuel ; la date de synchronisation de source ne la remplace pas | `SourceRepository`, schéma EPG |
| Rétention | Parseur filtre les événements recouvrant maintenant −1 jour / +3 jours ; purge périodique des programmes terminés avant −1 jour | `XmltvStreamParser`, `housekeeping` |

Ces constats sont une lecture de code, pas une recette exécutée. Le contrôle égalité
`from == to` est déjà refusé par le serveur ; le libellé contractuel actuel devra
être clarifié lors du lot pour expliciter une durée strictement positive.

## 2. D1 — Requête groupée bornée, par source

Reprendre le candidat historique de S7-01 :
`GET /sources/{id}/epg?channelIds=…&from=…&to=…`.

- Une seule source appartenant au compte ; `channelIds` obligatoire, liste CSV de
  1 à 100 UUID distincts. Doublons, valeur vide ou UUID mal formé : erreur de validation.
- `from` inclusif, `to` exclusif, RFC 3339 avec fuseau. Sans `from`, instant serveur
  capturé une fois ; sans `to`, `from + 24 h`. Exiger `0 < to - from <= 96 h`.
- Chaque programme recouvrant la fenêtre est rendu avec ses horaires complets,
  même s’il commence avant `from` ou finit après `to`.
- Pas de pagination implicite ni de paramètre `size` sur les chaînes : une entrée
  par identifiant demandé, dans l’ordre demandé, y compris quand ses programmes
  sont vides. Chaque liste est triée par début puis UUID, ordre total déterministe.
- La grille charge les chaînes visibles en lot ; une fenêtre courante de 3 h est
  la cible de démonstration. La journée mobile utilise ses bornes locales converties
  en instants : elle peut durer 23 ou 25 h.
- Les lectures utilisent les données stockées ; aucun appel au fournisseur au
  chargement de la grille. Réessayer recharge le guide stocké, sans déclencher une
  ingestion de source. L’actualisation de source conserve son parcours existant.

L’opération par chaîne existante est conservée pour compatibilité. Ajouter ses
métadonnées de fraîcheur de façon additive afin que la journée mobile et la grille
partagent les mêmes règles. Ses programmes et règles d’accès ne changent pas.

## 3. D2 — Réponse exhaustive ou erreur explicite

Forme candidate, sans JSON d’exemple contenant une vraie chaîne :

| Champ proposé | Sens |
|---|---|
| `source_id` | Source vérifiée |
| `from`, `to` | Bornes effectives de la requête |
| `generated_at` | Instant serveur de production de la réponse, pas âge du guide |
| `epg` | Métadonnées d’import décrites dans D3 |
| `channels[]` | Une entrée par identifiant demandé, ordre préservé |
| `channels[].channel_id` | UUID catalogue, jamais le nom comme identité |
| `channels[].mapping_status` | `MAPPED` ou `NO_TVG_ID` ; MAPPED indique seulement un identifiant renseigné, pas l’existence de programmes |
| `channels[].programmes[]` | Schéma `EpgProgramme` existant, sans URL de lecture ajoutée |

Deux chaînes du même lot qui partagent un `tvg_id` reçoivent chacune leur entrée.
Le même UUID de programme peut donc être présent sous plusieurs chaînes : ne pas
supprimer une ligne de grille au nom d’un dédoublonnage global.

Proposition à mesurer : plafond de **5 000 occurrences de programmes** par réponse
et **4 Mio de JSON UTF-8 non compressé**, enveloppe comprise. Une occurrence compte
à chaque apparition dans une entrée de chaîne, y compris les programmes partagés.
Aucun résumé tronqué ni programme omis silencieusement pour tenir sous le plafond.
Détecter le dépassement avant de commencer à envoyer un `200`, limiter la lecture
SQL à plafond + 1 et borner aussi la sérialisation. Ces deux plafonds sont des
**hypothèses de banc**, pas des limites déjà validées pour la production.

En dépassement, proposer au client de réduire le nombre de chaînes ou la fenêtre.
Découpage borné : d’abord les lots de chaînes, puis la fenêtre temporelle ; au plus
deux requêtes simultanées et trois niveaux de découpage par chargement initial.
Au-delà, afficher l’erreur et une action de réduction, sans récursion infinie.
Un programme recouvrant deux sous-fenêtres est fusionné par UUID dans la même ligne.
Un événement isolé trop volumineux reste une erreur explicite, pas un succès partiel.

| Réponse candidate | Condition et effet |
|---|---|
| 200 | Tout le lot valide est rendu, y compris les listes vides |
| 400 `VALIDATION_FAILED` | Identifiants mal formés/dupliqués, >100, fenêtre invalide ; rien tronqué |
| 401 | Authentification manquante/invalide, comportement existant |
| 404 `SOURCE_NOT_FOUND` | Source absente ou non détenue ; aucune distinction exposée |
| 404 `CHANNEL_NOT_FOUND` | Au moins une chaîne absente ou hors de cette source, même si elle appartient au même compte ; lot entier refusé, aucun détail sur une chaîne étrangère |
| 422 `EPG_WINDOW_TOO_LARGE` | Nouveau code proposé pour le volume ; aucun programme retourné |
| 5xx | Erreur de lecture locale ; le client garde ses données précédentes avec un message d’erreur |

Authentifier et vérifier source et appartenance de toutes les chaînes avant de
calculer le volume ou d’exposer des métadonnées. Ne pas changer les droits de lecture
des flux. Comme la lecture EPG unitaire actuelle, la lecture groupée ne dépend pas
de READY : un catalogue existant reste consultable pendant une synchronisation.
Une chaîne inconnue ne devient pas un guide vide. Les erreurs utilisent `Problem`.

## 4. D3 — Mesurer l’import, sans prétendre dater le contenu du fournisseur

Métadonnées candidates de niveau source, stockées côté serveur :

| Champ | Sémantique proposée |
|---|---|
| `configured` | Une source EPG est configurée selon les règles d’ingestion existantes ; aucune URL exposée |
| `last_successful_import_at` | Fin du dernier import EPG terminé avec succès, après écriture du dernier lot ; nullable |
| `last_attempt_started_at`, `last_attempt_finished_at` | Bornes de la tentative EPG, distinctes de celles du catalogue ; nullable |
| `last_attempt_status` | `UNKNOWN`, `RUNNING`, `SUCCEEDED`, `FAILED` ou `INTERRUPTED` |

Ne pas inventer de date de publication XMLTV : un import réussi aujourd’hui peut
contenir des horaires obsolètes. Le texte de l’interface dit donc **« Dernier import
du guide »**, pas « Programmes à jour ». `generated_at` et la date locale de
récupération ne remplacent jamais `last_successful_import_at`.

L’état RUNNING est écrit avant le premier lot ; SUCCEEDED et la date de succès
après la fin des écritures. Une tentative échouée ou interrompue ne rafraîchit pas
la date de succès. Une passe XMLTV terminée avec zéro programme retenu est un succès
d’import, pas une preuve de couverture. Les entrées invalides ignorées par le parseur
ne deviennent pas des programmes valides par cette réussite.

La proposition garde l’upsert par lots existant. Elle **ne promet pas de snapshot
atomique du guide** : pendant/après une tentative incomplète, les données peuvent
être mélangées. Présenter « Import en cours » ou « Dernier import incomplet » en
priorité, même si la date du dernier succès est récente. Les programmes absents du
nouveau XMLTV ne sont pas actuellement supprimés par réconciliation : ne pas
présenter le résultat comme une copie exacte du dernier fichier fournisseur.

Une lecture doit observer programmes et métadonnées dans une vue transactionnelle
cohérente : ne pas associer artificiellement une réussite lue avant les écritures
à des lignes lues pendant une tentative suivante. Détailler et tester cette garantie
dans S9-02, sans changer l’architecture de persistance ou introduire un cache partagé.

Migration : anciennes données → dates inconnues, état UNKNOWN ; ne pas recopier
`last_synced_at`. Un changement de configuration influençant le guide invalide ces
métadonnées (pas de secret ou d’URL stocké dedans). Les anciens programmes éventuellement
conservés sont alors signalés comme non vérifiés. L’absence de configuration ne doit
pas rendre d’anciens programmes trompeurs : le nouveau résultat retourne des listes
vides et `configured=false`. Documenter le même cas dans l’opération unitaire.
Une tentative ne peut pas publier son succès si la configuration a changé entre-temps.

Aucun historique d’import complet ni nouvelle architecture de cache n’est ajouté.
Prévoir un changeset SQL Liquibase additif avec rollback, sans modifier un changeset
appliqué. Si la réalisation exige finalement snapshot/versionnement ou une autre
stratégie de stockage, préparer un ADR séparé avant cette extension.

## 5. D4 — Seuil de 24 h et comportement client

Reprendre le seuil produit déjà décrit dans US-16 : **strictement plus de 24 h**
depuis le dernier import réussi rend l’information ancienne. À 24 h exactes, elle
ne l’est pas encore. Ce seuil informe ; il ne bloque ni la grille ni la chaîne.

| Situation | Présentation |
|---|---|
| Pas de configuration ou `NO_TVG_ID` | Explication factuelle dans le Guide ; accès Chaînes conservé |
| Configuration présente, import inconnu, liste vide | Aucun programme disponible sur ce créneau ; fraîcheur inconnue |
| Import réussi récent, liste vide | Créneau sans données, sans attribuer une panne au fournisseur |
| Dernier succès >24 h | Programmes conservés, date du dernier import affichée |
| Tentative en cours/échouée/interrompue | Message spécifique, programmes disponibles conservés, aucune fausse fraîcheur |
| Hors ligne | Données locales et date de leur récupération ; date d’import séparée si connue |

L’âge de départ se calcule avec `generated_at - last_successful_import_at`, puis
progresse avec le temps écoulé côté client ; ne pas masquer une incohérence d’horloge
par une mention « à jour ». Sur redémarrage hors ligne si l’âge n’est pas fiable,
afficher les dates connues sans garantie de fraîcheur. La sélection « En ce moment »
reste calculée depuis les horaires et l’horloge locale, sans requête à chaque seconde.

La fenêtre D−1/J+3 n’est pas une promesse de journées calendaires complètes. En
particulier le parseur filtre lors de l’ingestion ; l’absence d’une nouvelle ingestion
ne remplit pas automatiquement le nouveau J+3. Les clients affichent seulement les
données disponibles et annulent/ignorent les réponses d’une source ou fenêtre quittée.

## 6. D5 — Preuves avant gel du contrat et découpage

| Lot | Livrable attendu |
|---|---|
| S9-00 | Banc XMLTV relatif à l’horloge, gzip, vide, mal formé avant/après un lot écrit, données anciennes et gros volumes ; rapport observé |
| S9-01 / C1 | Accord sur D1–D4 ; fixer les plafonds définitifs après mesures ; finaliser noms et schémas proposés dans ce document |
| S9-02 | Contrat et implémentation cohérents, trois clients régénérés, migration et tests d’intégration d’appartenance, limites, métadonnées, concurrence |
| S9-03 | Modèle client distinguant import et récupération, cache borné et invalidation de source ; aucun changement de stratégie sans ADR |
| S9-07 | Grille de 50 chaînes sur 3 h en un appel normal, payload mesuré, scénarios dégradés et recette réelle des trois surfaces |

Mesurer au minimum 1/50/100 chaînes × 3/24/96 h, des programmes de durée variable,
100 chaînes partageant un identifiant EPG, descriptions volumineuses et cas juste
au-dessus de chaque plafond. Relever nombre d’occurrences, octets UTF-8 avant/après
compression, temps API, mémoire maximale et nombre de requêtes SQL. Aucun chiffre
de performance n’est annoncé atteint avant ce rapport.

## 7. Cas d’acceptation C1 à préparer

| ID | Preuve attendue |
|---|---|
| C1-01 | 100 chaînes valides → 100 entrées, ordre demandé, listes vides incluses ; 101 → 400 |
| C1-02 | Liste vide, doublon, UUID invalide, durée nulle/négative/>96 h → 400 |
| C1-03 | Programme finissant à `from` ou commençant à `to` exclu ; événement recouvrant une borne inclus |
| C1-04 | Source étrangère et identifiant étranger → 404 sans données ou détails révélateurs ; lot mixte entièrement refusé |
| C1-05 | Deux chaînes partageant `tvg_id`, deux sources partageant ce texte : association correcte, aucun mélange entre sources |
| C1-06 | Limites programmes/octets : réponse complète sous limite, erreur explicite au-dessus ; aucun 200 tronqué |
| C1-07 | Succès catalogue et échec EPG avant/après des écritures : date EPG non rafraîchie, état honnête, catalogue utilisable |
| C1-08 | Import réussi vide, ancien import, migration UNKNOWN : aucune fraîcheur ni couverture inventée |
| C1-09 | 24 h exactes puis dépassement ; changement d’heure et horloge locale décalée ; lecture jamais bloquée par l’âge |
| C1-10 | Source reconfigurée/supprimée pendant import ou lecture ; succès obsolète non publié et caches invalidés |
| C1-11 | Dépassement puis découpage client borné : pas de doublon de programme par ligne ni boucle infinie |
| C1-12 | Coupure réseau avec cache ; retour réseau ; lecteur et navigation du Guide restent distincts de l’ingestion |

## Validation finale de S9-01 à obtenir

Le cadrage D1 (lot de 100, fenêtre de 96 h), D2 (réponse exhaustive, erreur de volume),
D3 (métadonnées d’import et transparence sur les écritures partielles), D4 (seuil
24 h sans blocage) et D5 (mesures S9-00 avant plafonds définitifs) est validé.
Le rapport S9-00 est disponible ; confirmer le gel des plafonds et des schémas dans
S9-01 avant de passer au contrat et à l’implémentation S9-02.

L’écriture dans `openapi.yaml` et l’implémentation restent en attente conformément
à AGENTS.md §§3 et 9. Aucun changement du sprint 8 n’est inclus.
