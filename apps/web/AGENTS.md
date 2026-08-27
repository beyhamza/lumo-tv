<!-- BEGIN:nextjs-agent-rules -->

# This is NOT the Next.js you know

This version has breaking changes — APIs, conventions, and file structure may all differ from your training data. Read the relevant guide in `node_modules/next/dist/docs/` (resolved from this file's directory; in monorepos the `next` package may not be visible from the repo root) before writing any code. Heed deprecation notices.

This block is written and re-added by `next dev` — verify at `node_modules/next/dist/server/lib/generate-agent-files.js`. Removing it from a diff only re-creates the uncommitted change; committing it with your work keeps the tree clean.

<!-- END:nextjs-agent-rules -->

# AGENTS.md — `lumo.tv`

Complète l'[`AGENTS.md` racine](../../AGENTS.md), ne le contredit jamais. Lis
celui-ci **en plus**, pas à la place.

Le bloc ci-dessus est écrit par Next.js lui-même, et son avertissement est
fondé : ce projet est sur **Next.js 16**, qui a renommé `middleware.ts` en
`proxy.ts`, supprimé l'option `eslint` de `next.config` et retiré `next lint`.
Vérifie dans `node_modules/next/dist/docs/` avant d'écrire de la configuration.

---

## 1. Commandes

```bash
pnpm dev
```

| But | Commande |
|---|---|
| Serveur de développement | `pnpm dev` |
| Build de production | `pnpm build` |
| Servir le build | `pnpm start` |
| Types | `pnpm typecheck` |
| Lint | `pnpm lint` |
| Tests unitaires | `pnpm test` |
| Tests end-to-end | `pnpm test:e2e` |

**pnpm**, pas npm (AGENTS.md §4). Il est fourni par corepack :
`corepack enable pnpm`.

**Configuration.** `cp .env.example .env.local`, puis génère au minimum le
secret de session :

```bash
openssl rand -base64 32
```

`SESSION_SECRET` n'a **aucune valeur par défaut**. Sans lui, se connecter
échoue — délibérément : une valeur par défaut signifierait que tout déploiement
l'ayant oubliée partage une clé publiée dans ce dépôt, et que n'importe qui peut
alors forger un cookie de session.

Le build de production, lui, n'a besoin d'aucun secret : il ne rend que des
pages marketing.

---

## 2. Les trois zones

`docs/architecture.md` §4 découpe le site en trois zones aux contraintes
opposées. Le découpage se lit dans l'arborescence des routes, et `pnpm build`
en affiche la preuve.

| Zone | Routes | Rendu | Marque dans `next build` |
|---|---|---|---|
| Marketing | `/[locale]`, `/[locale]/guides/*` | SSG + ISR (`revalidate = 3600`) | `●` |
| Compte | `/[locale]/app/*` | SSR authentifié | `ƒ` |
| Activation | `/[locale]/activate` | SSR minimal | `ƒ` |
| Auth | `/[locale]/login`, `/[locale]/register` | SSR / prérendu | `ƒ` / `●` |

**Si une route marketing apparaît en `ƒ`, c'est une régression**, pas un détail
de build : la page a cessé d'être servable depuis un CDN. La cause est toujours
la même — un `cookies()`, un `headers()`, un `searchParams` ou un appel d'API
authentifié quelque part dans l'arbre.

### Règles de la zone marketing

1. **Aucun composant client.** Pas un seul. Un `"use client"` dans ce sous-arbre
   ajoute un bundle d'hydratation à une page dont la cible est un LCP sous deux
   secondes.
2. **Aucun appel d'API authentifié, aucun cookie.**
3. **Les liens sont des `<a>`**, construits avec `hrefFor(locale, "/chemin")`.
   Le `Link` de next-intl est un composant client qui appelle `useLocale()` :
   l'utiliser dans un Server Component exigerait un `NextIntlClientProvider`
   au-dessus, donc un bundle client sur toutes les pages prérendues. Le prix
   payé — un chargement complet par navigation — est le bon prix sur des pages
   de contenu qu'on entre depuis un résultat de recherche.

Vérification rapide : `curl -s localhost:3000/fr | grep -c '<script src'` doit
donner le même nombre que `/fr/activate` (le socle Next), et un nombre
strictement plus grand sur `/fr/register`, qui a un formulaire client.

---

## 3. Server / client components

Par défaut, **tout est Server Component**. On n'ajoute `"use client"` que pour
un état local, un gestionnaire d'événement ou un hook React — et le plus bas
possible dans l'arbre.

- Les formulaires passent par des **Server Actions**, jamais par un `fetch`
  navigateur vers l'API. C'est ce qui garde les tokens hors du JavaScript client
  (§4), et ce qui les fait fonctionner sans JavaScript du tout.
- `NextIntlClientProvider` se monte **par zone**, avec les seuls namespaces dont
  cette zone a besoin (voir `(auth)/layout.tsx`). Jamais à la racine : ce serait
  une frontière client sur les pages prérendues, et l'intégralité des messages
  dans leur bundle.
- Un module qui ne doit jamais atteindre le navigateur importe `server-only`
  (`lib/env.ts`, `lib/api/client.ts`, `lib/session/*`). Le build échoue si un
  composant client l'importe — c'est la seule garantie mécanique que
  `SESSION_SECRET` ne fuit pas.

---

## 4. Session et sécurité

- La session vit dans un cookie **httpOnly, chiffré** (JWE, A256GCM). httpOnly
  est la règle : une XSS sur ce site ne peut pas lire le cookie, donc ne peut
  pas repartir avec un refresh token valable des semaines. Chiffré et pas
  seulement signé, pour que les tokens n'existent en clair que dans ce processus.
- **Le rafraîchissement se fait dans `proxy.ts`**, avant le rendu. Un Server
  Component ne peut pas écrire de cookie — Next lève une erreur s'il essaie —
  donc au moment où une page s'exécute, la session est déjà fraîche.
- Un refresh **refusé** (401/409) vide le cookie : côté serveur, la
  réutilisation d'un token a déjà révoqué toute la chaîne de l'appareil. Un
  refresh **indisponible** (API injoignable) ne touche à rien : déconnecter
  quelqu'un parce que l'API redémarre est précisément le bug que cette
  distinction évite.
- Tout paramètre `next` de redirection est validé (`nextPath`). Renvoyer un
  `next` arbitraire est une redirection ouverte, sur la page où l'utilisateur
  vient de taper son mot de passe.
- Le mot de passe Xtream n'apparaît nulle part : l'API ne le renvoie pas, et
  aucun écran ne l'affiche.

---

## 5. Contrat d'API (ADR 0001)

Le client est **typé depuis le contrat** :
`packages/contracts/generated/typescript/api.d.ts`, importé sous l'alias
`@lumo/contracts` et consommé par `openapi-fetch`.

- On n'écrit **jamais** un type de requête ou de réponse à la main. On modifie
  `packages/contracts/openapi.yaml`, on régénère
  (`npm --prefix packages/contracts run generate`), et le code compile ou non.
- `openapi-fetch` n'a pas de runtime propre : appeler un endpoint inexistant ou
  envoyer un corps non conforme est une **erreur de type**, pas un 404 découvert
  en production.
- Un besoin non couvert par le contrat → **arrête-toi et demande**
  (AGENTS.md §9).
- Seul `code` (RFC 7807) sert à brancher. Le contrat précise qu'un code inconnu
  doit se dégrader proprement : `Errors.generic` est là pour ça.

---

## 6. SEO

Le contenu marketing est la porte d'entrée du produit
(`docs/architecture.md` §4). Ce qui n'est pas négociable sur une page
indexable :

- `generateMetadata` renvoie `pageMetadata(...)`, qui produit **canonical +
  `hreflang` + `x-default`** d'un coup. Livrer l'un sans l'autre laisse un
  moteur choisir lui-même le canonique — souvent la mauvaise langue — ou traiter
  les deux traductions comme du contenu dupliqué en concurrence.
- Les pages hors marketing passent `index: false`, et `robots.ts` interdit
  `/app` et `/activate`. `/activate?code=…` porte un code à usage unique : il
  n'a rien à faire dans un index.
- Le JSON-LD est rendu par `<JsonLd>` — un `<script type="application/ld+json">`,
  donc du balisage inerte, aucun composant client.
- `sitemap.ts` ne liste que le marketing, avec les alternates par langue.
- Un `<h1>` par page, une hiérarchie de titres continue, un `aria-label` sur
  chaque `<nav>`.

---

## 7. i18n

Aucune chaîne en dur dans l'UI, jamais (AGENTS.md §4). FR et EN livrées
ensemble.

Ajouter une page localisée :

1. Créer la route sous `src/app/[locale]/…`.
2. Ajouter ses libellés dans **`src/messages/fr.json` et `src/messages/en.json`**,
   dans un namespace dédié.
3. Dans la page : `setRequestLocale(locale)` en premier — sans lui, next-intl lit
   la locale à l'exécution et la route devient dynamique, silencieusement.
4. `generateMetadata` avec `pageMetadata({ locale, href, title, description })`.
5. Si la page est statique, `generateStaticParams` sur le segment.
6. Lier depuis le reste du site avec `hrefFor(locale, "/chemin")`.

Traductions et formats : `getTranslations` / `getFormatter` côté serveur,
`useTranslations` dans un composant client sous provider. Les dates passent par
`getFormatter` — jamais `toLocaleDateString`, qui produit un rendu serveur et un
rendu client différents et casse l'hydratation.

---

## 8. Tests

- `pnpm test` — Vitest, sur la logique qui mérite des tests : le scellement du
  cookie de session, la garde anti-redirection ouverte, la construction des URL
  localisées. Pas de test de rendu de placeholder.
- `pnpm test:e2e` — Playwright, contre un **build de production** et non le
  serveur de dev : ce qui est testé, ce sont des propriétés de la sortie de
  production (une page marketing servie statiquement, la redirection du proxy,
  `/activate` sans JavaScript). La CI l'exécute sur toute PR touchant `apps/web`.

  **La commande démarre la pile complète.** `e2e/global-setup.ts` lance
  PostgreSQL et lumo-api via `docker-compose.e2e.yml`, attend leurs deux
  healthchecks, et `global-teardown.ts` les arrête. Il faut donc Docker.

  Quatre projets :

  | Projet | Ce qu'il couvre |
  |---|---|
  | `setup` | crée un compte via le formulaire et enregistre sa session |
  | `chromium`, `mobile` | `zones.spec.ts` — le web seul, sans session |
  | `journey` | `journey.spec.ts` — tout ce qui traverse jusqu'à l'API |

  Ports, secrets et invocation de compose sont décrits dans
  `e2e/support/stack.ts`. Trois choses à ne pas défaire :

  1. **La pile a ses propres ports** (55432, 18080, 3100) et son propre nom de
     projet compose. Une exécution ne doit pas pouvoir toucher — ni même
     atteindre — la pile de développement.
  2. **La base vit en RAM** (`tmpfs`), pas dans `docker-data/`. Elle naît et
     meurt avec la campagne, donc rien ne dépend de l'état laissé par la
     précédente.
  3. **Les secrets sont générés à chaque exécution** et ne sont écrits nulle
     part. Il n'y a pas de `.env` pour l'e2e et il ne doit pas y en avoir : le
     job CI `no-content` refuse tout `.env` commité, et une clé de test connue
     finit toujours par devenir une clé de production connue.

  **Travailler sur le front sans repayer la pile à chaque fois.** Deux leviers,
  et ils se combinent :

  ```bash
  E2E_KEEP_STACK=1 pnpm test:e2e   # la première fois : la pile reste debout
  ```

  ```bash
  pnpm test:e2e                    # ensuite : elle est adoptée, aucun démarrage
  ```

  Une pile adoptée n'est **jamais** arrêtée par la campagne : couper des
  conteneurs qu'on n'a pas démarrés est une mauvaise surprise. À toi de les
  retirer quand tu as fini.

  L'image, elle, n'est reconstruite que si les sources de l'API ont bougé. Une
  empreinte de `apps/api/src`, des fichiers Gradle, du `Dockerfile` et de
  `openapi.yaml` est gardée dans `.e2e/` ; identique et image présente, on passe
  `--build`. `E2E_API_BUILD=1` force la reconstruction.

  Ce n'est pas le cache Docker qu'on remplace — il fait déjà son travail, et le
  `Dockerfile` monte un cache BuildKit sur `/root/.gradle`. C'est l'invocation
  elle-même qu'on évite : empaqueter le contexte et parcourir les couches pour
  confirmer que rien n'a changé coûte une demi-minute, soit exactement ce qui
  dissuade de lancer la suite pendant qu'on travaille.

  **Lancer l'API depuis un IDE.** `E2E_STACK=external` : la suite ne touche pas
  à Docker et s'attend à trouver une API en écoute — sur 8080 par défaut,
  puisque c'est le port de `application.yml`, ou sur `E2E_API_PORT`. Si rien ne
  répond, la campagne s'arrête en le disant, plutôt que de dérouler vingt-trois
  échecs. `E2E_STACK=docker` force le chemin inverse.

  En cas d'échec, les logs de l'API sont capturés dans
  `test-results/lumo-api.log` **avant** le teardown — sans quoi la CI ne
  remonterait qu'une trace de navigateur montrant une page d'erreur, et rien sur
  sa cause.

  Les navigateurs ne sont pas installés par `pnpm install`. Une fois par
  machine : `pnpm exec playwright install chromium`.

---

## 9. Pièges Next.js 16

| Symptôme | Cause | Correctif |
|---|---|---|
| `middleware.ts` ignoré | renommé `proxy.ts` en 16, export `proxy` | `src/proxy.ts` |
| `'eslint' does not exist in type 'NextConfig'` | option supprimée avec `next lint` | lancer ESLint en CLI (`pnpm lint`) |
| `No intl context found` sur une page serveur | le `Link` de next-intl est un composant client | `<a href={hrefFor(...)}>` |
| Une page marketing passe en `ƒ` | `cookies()`, `headers()` ou `searchParams` dans l'arbre | les remonter dans une zone dynamique |
| `PageProps<"/x">` ne résout pas | types de routes générés par le build | lancer `next build` (ou `next dev`) une fois |
| `pnpm add` sort en code 1 | un paquet attend une décision de build | l'ajouter à `allowBuilds` dans `pnpm-workspace.yaml` |

Le proxy tourne sur le runtime Node.js par défaut depuis Next 16 : il peut
déchiffrer le cookie et appeler l'API, ce qui n'était pas possible en Edge sans
contorsions.

---

## 10. Ce qui n'est pas encore fait

- Stripe (ADR 0003) : la page abonnement lit `GET /me/entitlement` et n'ouvre
  aucune session de paiement.
- Google Sign-In (US-03) : le bouton existe sur `/login` et `/register`, et rien
  n'est dessiné tant que `NEXT_PUBLIC_GOOGLE_CLIENT_ID` est vide — l'état de ce
  dépôt. Personne ne l'a donc encore vu avec un vrai client OAuth.
- **La lecture web est directe ou refusée, jamais relayée** (`adr/0007`). Elle ne
  marche donc pas chez tous les fournisseurs, et l`échec est nommé plutôt que
  silencieux — c'est la moitié du travail, pas un détail.
- Les guides sont trois entrées statiques ; un CMS devra remplacer
  `src/content/guides.ts`.
- Le rafraîchissement dédoublonne dans **un** processus. Derrière plusieurs
  instances, deux requêtes simultanées peuvent encore rafraîchir en parallèle :
  il faudra un verrou partagé (voir le commentaire dans `lib/session/refresh.ts`).
- Les parcours e2e s'arrêtent à la création de compte et à l'activation. Ils
  traversent bien l'API depuis que la pile est branchée, mais aucun ne va
  jusqu'à la lecture — et ce qui manque n'est plus l'environnement, ce sont les
  écrans. Ils vont désormais jusqu'à une image décodée dans le lecteur, contre le
  banc d'essai du sprint 3.
