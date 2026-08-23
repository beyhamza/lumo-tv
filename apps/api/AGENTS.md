# AGENTS.md — `lumo-api`

Complète le [`AGENTS.md` racine](../../AGENTS.md), ne le contredit jamais. Lis
celui-ci **en plus**, pas à la place.

---

## 1. Commandes

```bash
./gradlew build
```

| But | Commande |
|---|---|
| Compiler | `./gradlew compileJava` |
| Tous les tests | `./gradlew test` |
| Un seul test | `./gradlew test --tests '*M3uStreamParserTest*'` |
| Build complet | `./gradlew build` |
| Lancer en local | `./gradlew bootRun` |
| Régénérer les interfaces du contrat | `./gradlew openApiGenerate` |

`./gradlew test` démarre un **vrai PostgreSQL 16** via Testcontainers. Le démon
Docker doit tourner. Le conteneur est démarré une fois pour toute la campagne,
pas une fois par classe.

Pour `bootRun`, la base et les deux secrets doivent exister :

```bash
docker compose --env-file apps/api/.env up -d postgres
```

`LUMO_JWT_SECRET` et `LUMO_ENCRYPTION_MASTER_KEY` n'ont **aucune valeur par
défaut** : l'application refuse de démarrer plutôt que de tourner sur une clé
connue de tous. Génère-les avec `openssl rand -base64 48` et `openssl rand
-base64 32`.

---

## 2. Découpage : par domaine, pas par couche

```
tv.lumo.api/
├─ auth/       # comptes, JWT, rotation, SSO, activation TV
├─ source/     # sources utilisateur
├─ ingest/     # M3U, Xtream, XMLTV — tout le réseau sortant
│  ├─ m3u/ · xtream/ · xmltv/
├─ catalog/    # catégories, chaînes, lecture, EPG (lecture + écriture bulk)
├─ epg/        # (vide en v1, voir package-info)
├─ userdata/   # (non implémenté en v1)
├─ billing/    # (non implémenté en v1)
└─ shared/     # error, crypto, ratelimit, config, web
```

**Il n'y a pas de package `controller`, `service` ou `repository` global, et il
ne doit pas y en avoir.** Un domaine contient son controller, ses services et
ses repositories. Si tu cherches où mettre une classe, demande-toi de quel
domaine elle parle, jamais de quelle couche technique elle relève.

Ce qui est déjà implémenté : **auth, sources, catalogue** — les endpoints du
sprint 1. `userdata`, `billing` et `AccountApi` (`/me`, `/me/entitlement`,
`/me/devices`) ne le sont pas, délibérément. Ces chemins renvoient 404 plutôt
qu'un stub de données inventées. Leurs tables existent déjà.

---

## 3. Contrat d'abord

`packages/contracts/openapi.yaml` fait foi (ADR 0001). Le build Gradle génère
les interfaces serveur dans `build/generated/openapi` en lisant
`packages/contracts/config/spring.yaml` — **le même fichier d'options que le
pipeline npm**, pour que les deux ne puissent pas diverger.

`interfaceOnly` + `skipDefaultInterface` : les interfaces générées n'ont
**aucune méthode par défaut**. Un controller qui ne colle pas au contrat **ne
compile pas**. La contrepartie : toutes les opérations d'un tag doivent être
implémentées pour que ce tag compile. Un tag entier hors périmètre n'a
simplement pas de controller.

### Ajouter un endpoint

1. Modifier `packages/contracts/openapi.yaml`.
2. `npm --prefix packages/contracts run generate` et **committer le résultat**.
3. `./gradlew compileJava` — l'erreur de compilation te dit quoi implémenter.
4. Implémenter dans le domaine concerné.
5. L'annoncer explicitement dans la description de la PR : trois apps sont
   impactées.

Ne modifie **jamais** `build/generated/`. Si le contrat ne couvre pas ton
besoin, **arrête-toi et demande**.

---

## 4. Migrations

Liquibase, changesets en **SQL formaté** (ADR 0006). Un seul YAML dans tout le
projet.

```
src/main/resources/db/
├─ db.changelog-master.yaml     # includeAll, rien d'autre
└─ changelog/000N-domaine.sql
```

### Ajouter une migration

1. Nouveau fichier `changelog/0009-<sujet>.sql`, numéroté à la suite.
   `includeAll` trie alphabétiquement : **le nom du fichier EST l'ordre
   d'exécution**.
2. En-tête `--liquibase formatted sql`.
3. Un changement logique par `--changeset <auteur>:<id>`.
4. **Un `--rollback` explicite à chaque changeset**, sans exception.
   `--rollback empty` quand c'est vraiment un no-op, pour que l'intention soit
   écrite plutôt qu'oubliée.
5. `./gradlew test` : le test d'intégration applique tout le changelog sur un
   PostgreSQL neuf.

**Ne modifie jamais un changeset déjà appliqué.** Ajoutes-en un nouveau.

---

## 5. Règles locales non négociables

### Isolation multi-tenant

> Aucune requête sur une table portant `user_id` ne s'écrit sans filtre sur
> `user_id`.

Appliqué structurellement : aucune méthode de `SourceRepository` ne lit une
source sans paramètre `userId` — **il n'y a pas de `findById(UUID)` à portée de
main**. Les deux lectures non scopées portent un nom qui le dit
(`findForIngestion`, `markSyncing`) et ne sont atteignables que depuis le worker
d'ingestion, qui a reçu son id d'un appelant ayant déjà prouvé la propriété.

Les requêtes du catalogue joignent `source` et filtrent sur `source.user_id`. Un
id venu d'une URL n'est jamais suffisant.

### `stream_url`

**Une seule requête de tout le codebase nomme cette colonne** :
`CatalogReadRepository.findStreamUrlOwnedBy`, qui sert
`GET /channels/{id}/playback`. Un listing ne peut pas fuiter mille URL de flux
si le SQL ne les demande jamais. Ne l'ajoute à aucune projection.

### Logs

Jamais d'URL de flux, de mot de passe Xtream ni de token, **à aucun niveau,
`DEBUG` compris**. Concrètement :

- Le contrat type en `format: password` tout champ porteur de secret, ce qui
  fait masquer la propriété dans les `toString()` générés.
- `GlobalExceptionHandler` loggue les 4xx sans l'exception : le message peut
  citer le JSON fautif, qui pour un login est le mot de passe.
- `IngestionHttpClient` ne loggue que le host, jamais l'URL : une URL Xtream
  contient les identifiants dans son chemin.
- `JdbcTemplate` et `StatementCreatorUtils` sont épinglés à `INFO` dans
  `application.yml` — en `DEBUG` ils impriment les paramètres liés.

### Concurrence

Threads virtuels, **style bloquant séquentiel** (ADR 0005). Pas de WebFlux, pas
de `Mono`/`Flux`, pas de `CompletableFuture` chaîné. Pas de `ThreadLocal` pour
du contexte de requête.

**Tout appel sortant passe par `HostConcurrencyLimiter`.** Ce n'est pas une
optimisation : les threads virtuels ont supprimé le plafond qu'un pool borné
donnait gratuitement, et sans ce garde-fou une rafale de synchronisations ouvre
des centaines de connexions vers le panel d'**un utilisateur** et fait bannir
**son** compte.

Ne mets jamais un thread virtuel dans un pool.

### Parsing

M3U, XMLTV et les réponses Xtream se lisent **en streaming**, jamais chargés
entièrement en mémoire. Les corps de réponse sont plafonnés à la lecture, pas
sur `Content-Length` — un serveur peut mentir ou omettre l'en-tête.

---

## 6. Persistance : JdbcClient, pas de JPA

SQL écrit à la main via `JdbcClient` (lectures) et `JdbcTemplate` (écritures
batch). Pas d'Hibernate.

Deux raisons : l'ingestion remplace un catalogue entier en upserts batchés, ce
que JPA rend pénible ; et la règle multi-tenant n'est auditable que si le SQL
est visible, pas généré derrière un proxy de repository.

Les écritures du catalogue sont des **upserts** sur `(source_id, external_id)`,
jamais un delete-puis-réinsert : tronquer donnerait un nouvel id à chaque chaîne
à chaque sync et orphelinerait tous les favoris.

---

## 7. Pièges Spring Boot 4 déjà rencontrés

Ceux-ci ont coûté du temps ; ils sont documentés pour que ça n'arrive qu'une fois.

| Piège | Ce qu'il faut savoir |
|---|---|
| **Jackson 3** | `ObjectMapper` est dans `tools.jackson.databind`, pas `com.fasterxml.jackson.databind`. Les **annotations** n'ont PAS bougé et restent `com.fasterxml.jackson.annotation` — c'est pourquoi le code généré compile. |
| **Modularisation** | `liquibase-core` seul n'exécute **aucune** migration : l'autoconfiguration est dans `spring-boot-liquibase`, via `spring-boot-starter-liquibase`. L'app démarre parfaitement avec un schéma vide. Même logique pour le mail. |
| **`TestRestTemplate`** | Supprimé. Utiliser `RestClient` + `exchange()`, qui rend la réponse brute sans lever sur un 4xx. |
| **`CorsConfigurationSource`** | Ambigu à l'injection par type : `MvcHandlerMappingIntrospector` l'implémente aussi. Utiliser `Customizer.withDefaults()`, qui résout par **nom** de bean. |
| **Nom de bean** | Un `@Component MailSender` entre en collision avec le `mailSender` autoconfiguré. D'où `AccountMailer`. |
| **Enums générés** | `M3U_URL` devient la constante Java `M3_U_URL` (le camelizer d'openapi-generator coupe à la frontière chiffre-lettre). Les valeurs **sur le fil** sont correctes. Corrigeable par `x-enum-varnames` dans le contrat, mais ça renomme les constantes des trois clients d'un coup. |
| **`@Transactional`** | Auto-invocation = annotation ignorée. Appeler une méthode transactionnelle depuis la même classe ne fait rien du tout. |

---

## 8. Tests

JUnit 5 + Testcontainers. `PostgresIntegrationTest` + `@Import(PostgresContainerInitializer.class)`
pour tout ce qui a besoin du contexte et d'une base.

**PostgreSQL en conteneur, jamais H2.** Le schéma utilise `citext`, des index
GIN trigram, des index uniques partiels et `ON CONFLICT ... WHERE` : une suite
qui passe sur H2 et casse sur la vraie base est pire que pas de suite.

Ce qui doit arriver avec ses tests (AGENTS.md racine §5) : parsing M3U, mapping
Xtream, calcul de droits, rotation de tokens, chiffrement. L'UI n'existe pas
ici ; le domaine, si.

**Aucune chaîne, aucun logo, aucune URL de flux réelle dans une fixture.** Flux
libres de droits uniquement.
