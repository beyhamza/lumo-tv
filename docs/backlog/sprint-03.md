# Backlog — Sprint 3

## Le web devient utilisable : source, catalogue, lecture

> Un utilisateur enregistre sa source depuis `lumo.tv`, parcourt ses chaînes, et en
> lance une **dans son navigateur**.

Aujourd'hui `/app` ne fait que lire. Il n'existe aucun formulaire d'ajout de source,
aucun écran de catalogue, aucun lecteur — ni sur le web, ni ailleurs : les deux
applications Android sont encore des placeholders (sprint 2).

Ce sprint est **parallèle au sprint 2**, pas séquentiel : l'un fait les clients
Android, l'autre le client web. Ils ne partagent qu'une tâche d'outillage, et aucune
ligne de `apps/api`.

---

## Trois constats qui commandent l'ordre des tâches

### Aucune API ne manque, cette fois pour de bon

Tout ce que ce sprint demande est servi : `POST /sources` avec ses sept codes
d'erreur, `GET /sources/{id}` avec `status`, `sync_step` et `category_count`,
`GET /sources/{id}/categories` et `/channels` paginé, `GET /channels/{id}/playback`,
les favoris, les chaînes récentes et `GET /me/entitlement` avec ses plafonds. Le lot
serveur du sprint 2 a fermé les treize manques relevés sur les maquettes.

**Ce sprint ne touche ni `openapi.yaml`, ni `apps/api`.** Si une tâche vous y emmène,
c'est le signe qu'elle a dérivé.

### La lecture dans un navigateur se heurte à une règle d'architecture

Et c'est la seule vraie inconnue de ce sprint, donc elle se tranche en premier.

`docs/architecture.md` §1 pose un **point capital** : *le média ne transite jamais par
notre infrastructure*. Il fixe notre coût (faible) et notre posture (nous ne sommes
pas un diffuseur). Un lecteur mobile ou TV le respecte sans effort — l'application
ouvre le flux en direct vers le serveur de l'utilisateur.

Un navigateur, non :

| Obstacle | Conséquence |
|---|---|
| `https://lumo.tv` chargeant `http://panel…` | **contenu mixte, bloqué**, sans contournement possible |
| `hls.js` lit le manifeste et les segments en XHR | il faut `Access-Control-Allow-Origin` sur le panel |
| Safari lit HLS nativement via `<video src>` | pas de CORS exigé — mais toujours pas de `http://` |

La plupart des panels Xtream servent en clair, sur un port haut, sans en-tête CORS.
**La lecture web fonctionnera donc chez une minorité d'utilisateurs**, sauf à relayer
le flux par nos serveurs — ce que la règle ci-dessus interdit.

C'est une décision de produit, pas un détail technique, et elle se prend avant
d'écrire une ligne de lecteur : **S3-00**.

### La zone `/app` n'a aucun composant client aujourd'hui

Son `layout.tsx` le dit : rien sous `/app` n'est un composant client, et la
déconnexion est un formulaire vers une Server Action. Un lecteur vidéo et une grille
paginée changent cela — ce sont les premiers `"use client"` de la zone.

Deux règles de `apps/web/AGENTS.md` §3 et §4 tiennent malgré ce changement, et ce
sont elles qui rendent les tâches un peu moins directes qu'ailleurs :

- **les formulaires passent par des Server Actions**, jamais par un `fetch`
  navigateur vers l'API — c'est ce qui garde le token hors du JavaScript client, et
  ce qui les fait fonctionner sans JavaScript du tout ;
- `NextIntlClientProvider` se monte **par zone**, avec les seuls namespaces utiles.

---

## Ce qui existe déjà, et qu'on ne réécrit pas

| Module | Ce qu'il fournit |
|---|---|
| `lib/api/client.ts` | client typé depuis le contrat, `server-only`, jamais de types écrits à la main |
| `lib/api/fetched.ts` | les trois issues d'un appel : donnée, non implémenté, indisponible |
| `lib/api/error-message.ts` | traduction d'un `code` RFC 7807 en message, FR et EN |
| `lib/session/*` | cookie httpOnly chiffré, rafraîchissement dans `proxy.ts` |
| `actions/*` | trois Server Actions à copier comme modèle (`auth`, `password`, `activate`) |
| `components/app/Unavailable.tsx` | `EmptyState`, `Unavailable`, `NotBuiltYet` |
| `components/ui/*` | `button`, `card`, `field`, `input`, `label`, `separator` |

---

## Definition of Ready

Celle du sprint 1, plus deux conditions propres au web :

- les libellés existent en FR **et** en EN — un test unitaire échoue dès qu'une clé
  n'est présente que d'un côté ;
- la tâche dit ce qu'elle rend **sans JavaScript**. Une page de compte qui exige JS
  pour afficher une liste est un défaut, pas un choix.

## Definition of Done

Celle du sprint 1, sans allègement, plus :

- **la démo se fait sur une source réelle de l'auteur**, pas sur le banc d'essai —
  le banc prouve les erreurs, il ne prouve pas que ça marche ;
- aucune page marketing ne bascule de `●` à `ƒ` dans `next build` (CI `web`) ;
- aucune URL de flux, aucun identifiant de fournisseur dans le HTML rendu, dans un
  log, ou dans une capture d'écran versionnée (AGENTS.md §1 et §5).

---

## Une story ajoutée — **US-11, acceptée**

Aucune story du sprint 1 ne couvrait la lecture dans un navigateur : US-09 dit
« (mobile) », US-10 dit « (Android TV) ». Et le tableau d'AGENTS.md §1 assignait au
site « SEO, compte, abonnement, activation des TV » — pas la lecture.

**US-11 — Regarder une chaîne (navigateur)** est retenue, donc c'est une extension de
périmètre assumée. Les deux documents ont été mis à jour : `AGENTS.md` §1 et
`docs/architecture.md` §4 nomment maintenant la lecture parmi les rôles du site, et
§1 renvoie vers `adr/0007` pour la limite qui l'encadre.

US-06, US-07 et US-08 ne changent pas : ces tâches leur ajoutent un second client,
elles ne modifient aucun critère d'acceptation et ne ferment rien.

---

## Tâches

Une case par tâche, un pourcentage dès que l'avancement est partiel. La checklist se
met à jour **dans le commit qui livre le travail**, pas après.

| | Id | Tâche | Lot | Points | Avancement |
|---|---|---|---|---|---|
| ☑ | S3-00 | ADR 0007 — la lecture dans un navigateur | décision | 2 | 100 % |
| ☑ | S3-01 | Banc d'essai : sources en erreur **et** flux jouable | outillage | 3 | 100 % |
| ☑ | S3-02 | Ajouter une source : M3U et Xtream | sources | 5 | 100 % |
| ☑ | S3-03 | Suivi de l'ingestion : étapes, attente, succès chiffré | sources | 5 | 100 % |
| ☑ | S3-04 | Les quatre erreurs de source, et quoi faire | sources | 3 | 100 % |
| ☑ | S3-05 | Gérer une source : renommer, resynchroniser, supprimer | sources | 3 | 100 % |
| ☑ | S3-06 | Plafonds lus, jamais devinés | sources | 2 | 100 % |
| ☑ | S3-07 | Catalogue : catégories, chaînes paginées, recherche | catalogue | 8 | 100 % |
| ◩ | S3-08 | Favoris et chaînes récentes | catalogue | 3 | 30 % |
| ☑ | S3-09 | Route Handler de lecture : l'URL hors du HTML | lecture | 3 | 100 % |
| ☑ | S3-10 | Lecteur HLS | lecture | 8 | 100 % |
| ☑ | S3-11 | Échecs de lecture nommés | lecture | 5 | 100 % |
| ☑ | S3-12 | Parcours e2e : compte → source → chaîne → image | vérif | 5 | 100 % |

**Avancement du sprint : 96 % de 55 points.** Une chaîne se lance et s'affiche dans
le navigateur, vérifié sur une image décodée et non sur la présence d'une balise.

Reste **S3-08 à 30 %** : les chaînes récentes sont enregistrées au démarrage de la
lecture — jamais au survol, comme le contrat l'exige — mais ni les favoris ni les
deux rails ne sont écrits.

Sans US-11 : S3-00, S3-09, S3-10 et S3-11 tombent — **39 points**.

---

### S3-00 — ADR 0007, la lecture dans un navigateur · **2** · ☑ tranché

> **Décision : option A.** Écrite dans [`adr/0007`](../adr/0007-web-playback-direct-only.md),
> avec le refus du relais comme substance principale. `architecture.md` §1 et §4 et
> `AGENTS.md` §1 renvoient dessus.

Trancher, écrire, et s'y tenir. Trois options, et le coût de chacune :

**A — Lecture directe, au mieux.** Le navigateur ouvre le flux du panel. Coût
d'infrastructure nul, posture inchangée. Ne marche que si le panel sert en HTTPS avec
CORS — donc pas chez la majorité. Les échecs sont nommés (S3-11) et renvoient vers les
applications.

**B — Relais same-origin.** Un Route Handler Next relaie manifeste et segments en
réécrivant les URI. Marche partout, résout contenu mixte et CORS d'un coup, et garde
l'URL du flux entièrement hors du navigateur. **Contredit `architecture.md` §1** : le
média transite par notre infrastructure, notre coût suit la bande passante de nos
utilisateurs, et notre posture passe de « nous ne sommes pas un diffuseur » à « nous
relayons des flux ». C'est le chemin tentant, et c'est pour ça qu'il doit être décidé
et pas glissé dans une tâche.

**C — Le web ne lit pas.** Le site garde son rôle : compte, sources, catalogue,
activation. La lecture reste aux applications.

> **Recommandation : A**, avec le message de C quand la lecture est impossible. On
> obtient un lecteur qui marche là où c'est possible, un refus explicite ailleurs, et
> aucune décision d'architecture prise par accident. B reste ouvert si l'usage prouve
> que A échoue trop souvent — mais ce sera alors un ADR à part, avec un chiffrage de
> bande passante devant les yeux.

L'ADR doit dire, noir sur blanc, ce qu'on refuse de devenir. C'est la moitié qui
manque le jour où quelqu'un ajoute « juste un petit proxy ».

---

### S3-01 — Banc d'essai : sources en erreur et flux jouable · **3** · ☑

> **Livré.** Le conteneur `bench` sert désormais aussi un **flux HLS décodable** —
> six secondes de mire et un sinus générés par ffmpeg, 190 Ko, rien que personne ne
> possède — en deux variantes : `/stream/` avec en-tête CORS, `/stream-nocors/` sans.
> C'est la seconde qui compte : elle reproduit le cas majoritaire, et c'est elle qui a
> prouvé que le lecteur restait muet douze secondes.
>
> Les segments portent l'extension `.ts`, que TypeScript et ESLint prenaient pour du
> code. Exclus dans les deux configurations, avec la raison écrite.

Reprend **S2-03** et l'étend. Si le sprint 2 l'a déjà fait, cette tâche se limite à
l'extension et vaut 1 point.

Ce que S2-03 fournit : playlist valide, playlist vide, réponse qui n'est pas du M3U,
panel Xtream en 401, hôte muet, réponse au-delà du plafond.

Ce que le web ajoute, et sans quoi S3-10 n'est pas démontrable : **un flux HLS servi
en HTTPS avec `Access-Control-Allow-Origin`**, pointant un média libre de droits (Big
Buck Bunny, flux de test Apple HLS). Sans lui, la seule façon de tester le lecteur est
une vraie source, ce qui n'est ni reproductible ni committable.

Et le pendant : **un flux servi en `http://` sans CORS**, qui est le cas réel de la
plupart des panels. C'est celui qui fait passer S3-11 de « message d'erreur écrit à la
main » à « comportement vérifié ».

Interdit ici comme ailleurs : aucune source réelle, aucun nom de chaîne, aucun logo de
bouquet (AGENTS.md §1). Le job `no-content` refuse le dépôt sinon.

---

### S3-02 — Ajouter une source : M3U et Xtream · **5** · ☑

> **Livré.** `app/sources/new`, Server Action `createSource`, bascule M3U / Xtream
> en CSS pure (`:has()`), donc fonctionnelle sans JavaScript. Erreurs rendues au champ
> concerné à partir de `Problem.errors[]`.

Le formulaire qui manque depuis le début. Server Action, pas de `fetch` navigateur :
le token reste hors du JavaScript client, et le formulaire marche sans JavaScript.

Deux formes derrière un choix : `M3U_URL` demande une URL de playlist et une URL EPG
facultative ; `XTREAM` demande hôte, identifiant et mot de passe. Le contrat refuse
déjà les combinaisons incohérentes par champ (`FieldError` sur `/host`, `/username`,
`/password`, `/m3u_url`) — les rendre **au bon champ**, pas dans un bandeau global.

Trois points qui distinguent cet écran d'un formulaire ordinaire :

- **l'hôte est accepté avec ou sans schéma, port ou barre finale.** Le serveur
  normalise ; le formulaire ne doit pas refuser en amont ce que l'API accepte.
- **le mot de passe Xtream ne revient jamais.** L'API ne le renvoie pas, aucun écran
  ne l'affiche, et un formulaire d'édition pré-rempli garde hôte et identifiant en
  laissant le mot de passe vide (`apps/web/AGENTS.md` §4).
- **la réponse est un `202`, pas un `201`.** La source existe, le catalogue non. La
  suite est S3-03.

---

### S3-03 — Suivi de l'ingestion : étapes, attente, succès chiffré · **5** · ☑

> **Livré.** `app/sources/[id]`, checklist des étapes réelles — `AUTHENTICATED` est
> absent pour une playlist, qui n'authentifie rien — et rafraîchissement par
> `<meta http-equiv="refresh">` tant que l'ingestion tourne, donc sans une ligne de
> JavaScript. L'écran d'attente n'est pas couvert par un test : cinq chaînes
> s'importent trop vite pour qu'un navigateur l'observe.

Une grosse playlist prend jusqu'à une minute, et une minute de silence est l'endroit
où l'utilisateur conclut que c'est cassé.

`GET /sources/{id}` répond `status` **et** `sync_step` — `CONNECTING`,
`AUTHENTICATED`, `PARSING_CHANNELS`, `FETCHING_EPG`. Rendus en liste cochée, pas en
barre indéterminée : une étape nommée dit deux choses qu'un spinner ne dit pas, qu'il
avance et jusqu'où il était allé en cas d'échec.

Un `M3U_URL` ne passe jamais par `AUTHENTICATED` — il n'authentifie rien. La liste
doit s'en accommoder sans afficher une étape morte.

Le polling s'arrête sur `READY` ou `ERROR`, les deux seuls états terminaux, et se
donne un plafond : une source coincée en `SYNCING` est reprise côté serveur au bout de
trente minutes, l'écran n'a pas à interroger l'API pendant ce temps.

L'écran de succès affiche les deux moitiés de la phrase : **`channel_count` chaînes ·
`category_count` catégories**, plus `expires_at` et `max_connections` quand le panel
les rapporte.

---

### S3-04 — Les quatre erreurs de source, et quoi faire · **3** · ☑

> **Livré.** Les onze codes ont un message FR et EN, rendus avec `last_error_at`
> pour l'âge, et un bouton par situation. Le cas « page HTML servie en 200 » est
> vérifié de bout en bout.

`SOURCE_UNREACHABLE`, `SOURCE_AUTH_FAILED`, `SOURCE_EXPIRED`, `SOURCE_INVALID_FORMAT`
— plus `SOURCE_EMPTY`, `SOURCE_TOO_LARGE` et `SOURCE_MAX_CONNECTIONS`.

Les fondre en « une erreur est survenue » est la seule chose que cet écran ne doit pas
faire : l'action suivante diffère complètement entre « le serveur ne répond pas » et
« votre mot de passe est refusé ». Les messages existent déjà en FR et EN dans
`error-message.ts` ; ce qui manque, c'est l'écran qui les montre et le bouton qui
correspond — réessayer, corriger les identifiants, renouveler l'abonnement.

`last_error_at` donne l'âge, et c'est lui qui rend le message actionnable :
« identifiants refusés **depuis hier** » ne dit pas la même chose que « identifiants
refusés ».

Testable grâce à S3-01, et seulement grâce à lui.

---

### S3-05 — Gérer une source : renommer, resynchroniser, supprimer · **3** · ☑

> **Livré.** Renommer, basculer `auto_sync`, actualiser, supprimer — la confirmation
> de suppression passe par un paramètre d'URL, donc elle survit à un rechargement et
> marche sans JavaScript.

`PATCH /sources/{id}`, `POST /sources/{id}/sync`, `DELETE /sources/{id}`.

Quatre détails qui se voient à l'usage :

- modifier ce qui change **ce qui est récupéré** relance une ingestion ; renommer, non.
  L'écran doit le dire avant de valider, pas après ;
- `auto_sync` est une bascule par source, et le libellé doit dire ce qu'elle fait
  vraiment : le serveur resynchronise seul, même appareils éteints ;
- une resynchronisation déjà en cours répond `409 SOURCE_SYNC_IN_PROGRESS` — ce n'est
  pas une erreur à afficher en rouge, c'est « c'est déjà parti » ;
- la suppression emporte catégories, chaînes, EPG et **favoris**. La confirmation doit
  le nommer.

---

### S3-06 — Plafonds lus, jamais devinés · **2** · ☑

> **Livré.** `max_sources` lu sur `GET /me/entitlement`, le bouton d'ajout cède la
> place au plafond, et le `409` reste géré pour le cas des deux onglets.

`GET /me/entitlement` renvoie `max_sources` et `max_devices`. `null` veut dire
illimité, pas inconnu.

Le bouton « Ajouter une source » se désactive **avant** que l'utilisateur remplisse un
formulaire qui va être refusé, et `409 SOURCE_LIMIT_REACHED` reste géré : deux onglets
ouverts, et le premier a consommé la place.

Écrire « FREE = 1 source » dans le web serait un droit d'accès calculé côté client,
que l'AGENTS.md §1 interdit sans réserve. Le nombre vient de l'API, à chaque fois.
Même règle pour les appareils, sur `/app/devices`.

---

### S3-07 — Catalogue : catégories, chaînes paginées, recherche · **8** · ☑

> **Livré.** `app/sources/[id]/channels`. Catégorie, page et recherche vivent dans
> l'URL : tout est lien ou formulaire GET, donc partageable, compatible avec le
> bouton retour, et fonctionnel sans JavaScript. Numéro et qualité rendus tels que
> la source les écrit ; aucun visuel de repli pour une chaîne sans logo.

Le plus gros écran du sprint, et le seul qui ait un vrai enjeu de volume : une source
courante fait quinze mille chaînes.

`GET /sources/{id}/categories` à gauche, `GET /sources/{id}/channels` paginé au
centre, `?q=` pour la recherche.

Une précision qui change ce que le champ a le droit de promettre : la recherche est
une **sous-chaîne insensible à la casse**, pas une recherche tolérante aux fautes.
L'index trigram accélère ce `ILIKE '%q%'`, il ne le rend pas approximatif — le
commentaire de `CatalogReadRepository` confond les deux. Un placeholder qui suggère
« cherchez même avec une faute » serait faux.

Ce qui se rend, et qui vient d'arriver dans le contrat : **`number`** (le numéro du
fournisseur, pas `position`) et **`quality`**, une chaîne libre affichée en badge et
**jamais réinterprétée** — si la source écrit `fhd` en minuscules, le badge dit `fhd`.
Une valeur inconnue s'affiche telle quelle ou ne s'affiche pas ; elle ne se range pas
dans une case.

`logo_url` vient de la playlist de l'utilisateur. Lumo n'embarque **aucun logo de
repli** : pas d'image, une initiale ou rien.

Deux pièges de la zone :

- la pagination doit marcher **sans JavaScript** — des liens `?page=`, pas seulement
  un défilement infini ;
- la source doit être `READY`. `409 SOURCE_NOT_READY` renvoie vers l'écran d'attente
  de S3-03, il ne s'affiche pas comme une panne.

---

### S3-08 — Favoris et chaînes récentes · **3**

`GET`/`POST /me/favorites`, `DELETE /me/favorites/{id}`, `GET`/`POST
/me/favorite-groups`, `GET`/`PUT /me/recent-channels`.

Deux rails en haut du catalogue, et une étoile sur chaque chaîne. Rien de subtil, sauf
deux choses :

- le groupe par défaut est créé au premier ajout et le serveur le nomme `Favorites`,
  en anglais, faute de pouvoir faire autrement — `FavoriteGroup` ne porte aucun
  identifiant stable sur lequel traduire. Le web ne doit pas le traduire à l'aveugle :
  c'est un nom que l'utilisateur peut changer. Voir la décision ouverte dans
  [`design/api-gaps.md`](../design/api-gaps.md) ;
- `PUT /me/recent-channels` s'envoie **quand la lecture démarre**, jamais au survol ni
  au focus. Un rail construit sur ce que le curseur a effleuré est du bruit, et c'est
  l'historique de l'utilisateur qu'on abîme.

Dépend de S3-10 pour le second point ; le premier est autonome.

---

### S3-09 — Route Handler de lecture : l'URL hors du HTML · **3** · dépend de S3-00 · ☑

> **Livré.** `/api/playback/[channelId]`, `Cache-Control: no-store`, l'URL n'entre
> jamais dans le HTML — vérifié par un test qui relit le document rendu.

`GET /channels/{id}/playback` renvoie `stream_url`, et pour une source Xtream cette
URL **contient l'identifiant et le mot de passe du panel**. C'est la réponse la plus
sensible que l'API produise, et le contrat la type `format: password` pour cette
raison.

Elle ne doit donc pas être rendue dans le HTML : ni dans un attribut, ni dans un
`<script>` d'hydratation, ni dans un state serveur sérialisé. Un Route Handler
same-origin, appelé par le lecteur au moment de lancer, la récupère avec le token du
cookie et la renvoie au seul JavaScript qui en a besoin.

Ce que ça ne résout pas, et qu'il ne faut pas croire résolu : l'URL est alors dans la
mémoire du navigateur et dans l'onglet réseau des outils de développement. Ce sont les
identifiants de l'utilisateur, sur sa propre machine — acceptable — mais cela veut
dire qu'une XSS sur ce site les emporte. C'est un argument de plus pour que rien de ce
lecteur ne soit rendu côté serveur.

Le handler traite aussi les deux refus du contrat : `409 SOURCE_NOT_READY` et
`409 SOURCE_EXPIRED`.

---

### S3-10 — Lecteur HLS · **8** · dépend de S3-00, S3-09 · ☑

> **Livré.** `ChannelPlayer`, hls.js chargé à la demande, lecture prouvée sur une
> frame décodée (`readyState >= 2` et `currentTime > 0`).
>
> **Le choix du chemin de lecture a été inversé en cours de route.** Prendre le
> lecteur natif quand `canPlayType` répond quelque chose est faux : Chromium répond
> `"maybe"` pour HLS et ne le lit pas — il télécharge tous les segments, n'en décode
> aucun, et ne lève aucune erreur. Le lecteur utilise donc MSE partout où MSE existe,
> et le natif seulement là où il n'existe pas. `adr/0007` est amendé en
> conséquence : desktop Safari passe par hls.js et réclame CORS comme Chrome.

Un composant client, aussi bas que possible dans l'arbre, et le premier de la zone.

`hls.js` là où MSE est disponible, `<video src>` natif sur Safari — l'asymétrie
compte : Safari lit un manifeste HLS sans exiger CORS, `hls.js` l'exige toujours,
parce qu'il va chercher manifeste et segments lui-même. Un même flux peut donc marcher
sur Safari et échouer sur Chrome, et le diagnostic de S3-11 doit le dire.

Le nécessaire, et rien de plus : lecture, pause, volume, plein écran, et les raccourcis
clavier qu'on attend (espace, flèches, `f`, `m`). Pas de sélecteur de qualité ni de
piste écrite à la main : ce sont les pistes du manifeste, `hls.js` les expose, et
l'API n'a pas à les connaître — les faire transiter par le serveur supposerait de
télécharger le flux, ce que `architecture.md` §1 interdit.

Sur mobile, le lecteur doit tenir dans un navigateur mobile ou dire qu'il ne tient pas.
Picture-in-Picture est **hors périmètre v1** (AGENTS.md §6).

---

### S3-11 — Échecs de lecture nommés · **5** · dépend de S3-00 · ☑

> **Livré.** Cinq causes nommées. Le point qui décidait de tout : hls.js signale un
> manifeste refusé d'abord en **non fatal**, et n'appelle l'échec fatal qu'après ses
> tentatives — douze secondes de rectangle noir pendant lesquelles le garde-fou
> couvrait le silence. En comptant les tentatives, le message tombe en 448 ms.
>
> Les options `manifestLoadingMaxRetry` et consorts n'y étaient pour rien : hls.js 1.x
> les a dépréciées et les ignore sans rien dire. C'est `manifestLoadPolicy` qui règle.

La tâche qui décide si le lecteur est utilisable ou mystérieux, et la seule dont le
travail est presque entièrement de la formulation.

| Cause | Ce que voit l'utilisateur aujourd'hui | Ce qu'il doit lire |
|---|---|---|
| Panel en `http://`, site en `https://` | rien, une vidéo noire | « Votre fournisseur ne permet pas la lecture dans un navigateur » + lien vers les applications |
| Pas d'en-tête CORS | erreur console, vidéo noire | même message, cause distincte dans les logs |
| `SOURCE_MAX_CONNECTIONS` | rien | « Trop de lectures simultanées sur votre abonnement » |
| `SOURCE_EXPIRED` | rien | « Votre abonnement chez ce fournisseur a expiré » |
| Segment qui n'arrive pas, coupure | gel | reprise automatique, puis message après N échecs |

Les deux premières lignes sont l'essentiel : ce sont les cas **majoritaires** si
l'option A est retenue en S3-00, et une vidéo noire sans explication est exactement ce
qui fait conclure que le produit est cassé alors que c'est le panel qui refuse.

Le message ne doit jamais accuser l'utilisateur, ni promettre que réessayer va marcher
quand ça ne marchera pas. Il propose la sortie qui existe : le téléphone ou la
télévision.

---

### S3-12 — Parcours e2e : compte → source → chaîne → image · **5** · ☑

> **Livré.** Le parcours va de la création de compte à une image qui bouge, et son
> pendant vérifie qu'un flux sans CORS produit un message nommé plutôt qu'un carré
> noir. 32 tests e2e au total.

Le parcours s'arrête aujourd'hui à la création de compte et à l'activation. Ce qui
manquait n'était plus l'environnement — la pile Docker est branchée depuis le
sprint 2 — c'étaient les écrans. Ils existent à la fin de ce sprint.

Un test qui traverse tout, contre le banc de S3-01 : créer un compte, enregistrer une
source M3U pointant le banc, attendre `READY`, ouvrir le catalogue, lancer une chaîne,
et **vérifier qu'une image arrive** — `readyState`, une frame décodée, pas la seule
présence d'un `<video>`.

Plus le pendant, qui vaut autant : la source `http://` sans CORS produit le message de
S3-11 et non une vidéo noire.

Le projet `journey` de `playwright.config.ts` accueille les deux ; la pile est déjà
adoptée ou construite par `global-setup.ts`.

---

## Ce qui reste dehors, et pourquoi

| Écarté | Raison |
|---|---|
| Relais du flux par nos serveurs | Option B de S3-00 : contredit `architecture.md` §1, et se décide dans un ADR à part, pas dans une tâche de lecteur |
| Picture-in-Picture, Chromecast, timeshift | v2 (AGENTS.md §6) |
| VOD et séries | Ingérés et exposés, aucun endpoint dédié en v1 |
| EPG dans la grille web | `GET /channels/{id}/epg` existe et marche ; l'écran n'est pas nécessaire pour lancer une chaîne. À chiffrer au sprint suivant |
| Paiement Stripe depuis la page abonnement | Ouvrir une session marche, mais **aucun paiement n'accorde encore rien** : le webhook manque au contrat. Décision ouverte, `design/api-gaps.md` |

---

## Documents associés

Une fois les tâches acceptées, deux documents suivent, sur le modèle du sprint 2 :
`sprint-03-recette.md` (le plan de qualification, cas par cas) et `sprint-03-demo.md`
(le déroulé). Ils ne sont pas écrits d'avance : la recette d'un lecteur dépend de ce
que S3-00 tranche.
