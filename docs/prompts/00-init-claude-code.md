# Prompt d'initialisation — Claude Code

> **Mode d'emploi.** Ce prompt s'exécute en **quatre passes séparées**, dans l'ordre.
> Ne colle pas tout d'un coup : un agent à qui on demande dix choses en fait sept
> correctement et bâcle les trois autres. Valide et commite entre chaque passe.
>
> Prérequis dans le dépôt avant de commencer : `AGENTS.md`, `CLAUDE.md`,
> `docs/architecture.md`, `docs/domain-model.md`, `docs/adr/*`,
> `docs/backlog/sprint-01.md`.

---

## PASSE 1 — Squelette du monorepo et contrat d'API

```
Tu initialises le monorepo du projet Lumo TV.

Lis d'abord, intégralement et dans cet ordre : AGENTS.md, docs/architecture.md,
docs/domain-model.md, les quatre ADR de docs/adr/, puis docs/backlog/sprint-01.md.
Ils font autorité. Si tu constates une contradiction entre eux, signale-la et
arrête-toi plutôt que de trancher toi-même.

PÉRIMÈTRE DE CETTE PASSE — uniquement ceci, rien de plus :

1. L'arborescence de dossiers décrite dans AGENTS.md §2, avec un .gitkeep dans les
   dossiers encore vides.
2. .gitignore, .editorconfig, LICENSE (propriétaire), README.md racine.
3. packages/contracts/openapi.yaml, écrit à la main, couvrant EXACTEMENT les endpoints
   listés dans docs/domain-model.md §3. Pour chacun : schémas de requête et de réponse,
   codes de statut, exigence d'authentification, et le format d'erreur RFC 7807 avec
   l'énumération complète des codes d'erreur d'ingestion.
4. Le pipeline de génération des trois clients (Spring interfaceOnly, Kotlin retrofit2,
   openapi-typescript) sous forme de scripts exécutables, plus une vérification CI qui
   échoue si le code généré diverge du contrat committé.
5. docker-compose.yml : PostgreSQL 16 + lumo-api, avec healthchecks.
6. .env.example pour chacune des trois applications, chaque variable commentée.

CONTRAINTES

- Aucun code applicatif dans cette passe. Pas de controller, pas d'écran, pas d'entité.
- openapi.yaml est écrit à la main et devient la source de vérité. Prends-y le temps
  nécessaire : tout le sprint 1 en dépend.
- Utilise exactement les noms d'entités et de champs de docs/domain-model.md. Aucun
  synonyme, aucune variante de casse.
- Le mot de passe Xtream ne doit apparaître dans AUCUN schéma de réponse, nulle part.

LIVRABLE

Un commit `chore: scaffold monorepo and API contract`, puis un résumé listant :
les endpoints définis, les décisions de modélisation que tu as dû prendre, et les
points où le contrat t'a semblé ambigu.
```

---

## PASSE 2 — Backend

```
Tu scaffoldes apps/api. Le contrat packages/contracts/openapi.yaml fait foi.

PÉRIMÈTRE

1. Projet Spring Boot 4.1.x / Java 25 / Gradle 9, découpé par domaine selon
   docs/architecture.md §2 — surtout PAS en couches controller/service/repository
   globales. Toolchain Gradle épinglée sur le JDK 25.
2. Migrations Liquibase couvrant toutes les tables de docs/domain-model.md §2, avec
   index, contraintes et clés étrangères. Voir la section LIQUIBASE ci-dessous : le
   format est imposé et non négociable.
3. Génération des interfaces d'API depuis le contrat, branchée dans le build Gradle.
   Les controllers implémentent ces interfaces.
4. Sécurité : filtre JWT, rotation de refresh token avec détection de réutilisation,
   Argon2id, rate limiting sur les endpoints d'authentification.
5. Le service de chiffrement des identifiants Xtream : AES-256-GCM, clé de données
   chiffrée par une clé maître lue dans l'environnement. Interface propre pour brancher
   un KMS plus tard.
6. Gestionnaire d'exceptions global produisant du application/problem+json.
7. Setup de test : JUnit 5 + Testcontainers PostgreSQL, avec un test d'intégration qui
   démarre le contexte et vérifie le healthcheck.
8. apps/api/AGENTS.md : commandes de build et de test, conventions locales, comment
   ajouter une migration, comment ajouter un endpoint.

LIQUIBASE — FORMAT IMPOSÉ (lis docs/adr/0006 avant d'écrire une ligne)

- UN SEUL fichier non-SQL dans tout db/ : db.changelog-master.yaml, qui contient un
  includeAll et rien d'autre. C'est une contrainte de l'édition open source de
  Liquibase, pas une préférence — un changelog racine en SQL avec includeAll n'existe
  qu'en édition Secure.
- TOUS les changesets sont en SQL formaté (--liquibase formatted sql). Aucun changeset
  en YAML ou en XML, sous aucun prétexte.
- Un changement logique par changeset. Chaque changeset porte un --rollback explicite,
  ou --rollback empty quand le rollback est réellement vide.
- Noms de fichiers numérotés et zéro-paddés : includeAll trie alphabétiquement, donc le
  nommage EST l'ordre d'exécution.
- Utilise pleinement PostgreSQL : citext, index partiels, index GIN pg_trgm sur les noms
  de chaînes, bytea. C'est la raison d'être de ce choix de format.

VIRTUAL THREADS — lis docs/adr/0005, ces pièges sont ceux que ce projet rencontre

- spring.threads.virtual.enabled=true. Style bloquant partout : pas de WebFlux, pas de
  Mono/Flux, pas de chaînes de CompletableFuture dans la logique métier.
- N'active AUCUNE preview feature. La structured concurrency est encore en preview sur
  le JDK 25 : pour le fan-out, utilise Executors.newVirtualThreadPerTaskExecutor() dans
  un try-with-resources.
- BACKPRESSURE EXPLICITE, c'est le point le plus important de cette passe. Les threads
  virtuels suppriment le plafond que constituait le pool de threads. Sans garde-fou,
  10 000 synchronisations simultanées ouvrent 10 000 connexions sortantes vers le
  serveur IPTV d'un utilisateur et font bannir son compte. Donc : un Semaphore PAR HOST
  de destination sur tout appel sortant dans ingest/, plus un plafond global de
  synchronisations concurrentes. Ce n'est pas une optimisation, c'est une exigence.
- HikariCP reste dimensionné pour la base de données, pas pour le nombre de threads.
  Fixe et surveille le timeout d'acquisition de connexion.
- Aucun ThreadLocal pour du contexte de requête : utilise ScopedValue. Avec des threads
  virtuels, un ThreadLocal cachant un objet coûteux est une fuite mémoire par
  construction.
- Ne mets JAMAIS de threads virtuels dans un pool de taille fixe.
- Évite synchronized autour d'un appel bloquant. Préfère ReentrantLock, et demande-toi
  d'abord pourquoi un verrou entoure une I/O.

SPRING BOOT 4 — deux pièges qui font perdre des heures

- Jackson 3 est le défaut. Les comportements de sérialisation diffèrent subtilement de
  Jackson 2 ; écris les tests de sérialisation en conséquence plutôt que de supposer.
- Le codebase est modularisé en 70+ jars et certains starters ont été renommés. Vérifie
  les noms d'artefacts contre la doc 4.1, ne les déduis pas de tes souvenirs de 3.x.
- Vérifie que la version d'openapi-generator utilisée supporte Spring Boot 4 / Spring
  Framework 7. Si ce n'est pas le cas, ARRÊTE-TOI et signale-le : c'est un blocage
  structurant qui remet en cause l'ADR 0001, pas quelque chose à contourner
  silencieusement par du code écrit à la main.

CONTRAINTES

- Implémente les endpoints du sprint 1 uniquement (auth, sources, catalogue). Les
  autres restent non implémentés — n'écris pas de stub qui retourne des données fictives.
- Aucune requête sur une table portant user_id ne s'écrit sans filtre sur user_id.
- Aucun log ne contient de stream URL, de mot de passe ou de token, à aucun niveau.
- Le parsing M3U et XMLTV se fait en streaming. Ne charge jamais un fichier entier en
  mémoire.

LIVRABLE

Commits atomiques par domaine. Résumé de ce qui est implémenté, de ce qui reste, et de
tout écart que tu as dû prendre par rapport au contrat.
```

---

## PASSE 3 — Android

```
Tu scaffoldes apps/android.

PÉRIMÈTRE

1. Projet Gradle multi-modules exactement selon docs/architecture.md §3 :
   app-mobile, app-tv, core/*, feature/*, build-logic/.
2. build-logic/ avec convention plugins : configuration Android partagée, Compose,
   Hilt, Kotlin. Aucun bloc de configuration dupliqué entre modules.
3. libs.versions.toml complet.
4. core/network : client Retrofit généré depuis le contrat, intercepteur d'auth,
   refresh automatique protégé par mutex (un seul refresh en vol — c'est le bug
   classique de ce pattern, ne le reproduis pas).
5. core/auth : stockage des tokens en DataStore chiffré adossé au Keystore.
6. core/database : entités Room, DAO, setup Paging 3.
7. core/player : abstraction au-dessus de Media3, indépendante de la forme du device.
8. core/designsystem : tokens partagés, thème mobile et thème TV.
9. Navigation et un écran placeholder par feature, dans les deux applications.
10. apps/android/AGENTS.md : commandes, règles Compose for TV, règles de focus,
    comment ajouter un module feature.

CONTRAINTES

- Deux applicationId distincts, conformément à l'ADR 0004.
- Tout ce qui n'est pas UI est partagé. Une logique métier dupliquée entre app-mobile
  et app-tv est un défaut.
- Compose for TV : chaque élément focusable a un état de focus visuellement évident
  (échelle + bordure + élévation). Aucun élément interactif inatteignable au D-pad.
  Marges d'overscan de 5 %.
- Aucune chaîne de caractères en dur dans l'UI. strings.xml FR et EN dès maintenant.
- Aucune donnée de test contenant une chaîne, un logo de bouquet ou une URL de flux
  réelle. Utilise uniquement des flux de test libres de droits.

LIVRABLE

Le projet compile et les deux applications se lancent sur un écran placeholder.
Résumé du découpage en modules et des conventions posées.
```

---

## PASSE 4 — Web

```
Tu scaffoldes apps/web.

PÉRIMÈTRE

1. Next.js App Router / TypeScript / Tailwind / shadcn/ui / next-intl (FR + EN).
2. Les trois zones de docs/architecture.md §4, avec leurs stratégies de rendu
   respectives : SSG/ISR pour le marketing, SSR authentifié pour /app, SSR minimal
   pour /activate.
3. Client API typé, généré depuis le contrat via openapi-typescript + openapi-fetch.
4. Auth : cookies httpOnly, middleware de protection de /app, refresh côté serveur.
5. Structure SEO : sitemap.ts, robots.ts, métadonnées par route, JSON-LD, balises
   hreflang FR/EN.
6. Écrans placeholder : landing, inscription, connexion, /app (sources, appareils,
   abonnement), /activate.
7. apps/web/AGENTS.md : commandes, conventions server/client components, règles SEO,
   comment ajouter une page localisée.

CONTRAINTES

- Les pages marketing n'importent aucun composant client et n'appellent aucune API
  authentifiée. Elles doivent être servies statiquement.
- Le token n'est jamais accessible en JavaScript côté client.
- /activate est utilisable au clavier seul et lisible sur mobile : c'est un écran
  qu'on utilise debout, téléphone en main, devant sa télé.
- Aucune chaîne en dur. Tout passe par next-intl.

LIVRABLE

Le site démarre, les trois zones sont navigables, le build de production passe.
Résumé de la structure de routes et de la stratégie de rendu de chacune.
```

---

## PASSE 5 (optionnelle) — Relecture croisée

```
Relis le dépôt entier avec un œil critique et produis un rapport, SANS modifier de code.

Vérifie :
- cohérence entre openapi.yaml, les changesets Liquibase, les entités Room et les types TS ;
- respect du format Liquibase imposé (un seul YAML racine, tout le reste en SQL formaté) ;
- pièges virtual threads : ThreadLocal résiduels, appels sortants sans sémaphore,
  pooling manuel de threads virtuels, `synchronized` autour d'un appel bloquant ;
- absence de nom d'entité ou de champ divergent entre les trois applications ;
- absence de stream URL, de mot de passe et de token dans les logs ;
- absence de chaîne, de logo de bouquet ou d'URL de flux réelle, fixtures comprises ;
- couverture réelle des critères d'acceptation du sprint 1 ;
- écarts entre le code et les ADR.

Classe chaque constat en : bloquant / à corriger / cosmétique. Propose un plan de
correction ordonné. Ne corrige rien dans cette passe.
```
