# Backlog — Sprint 5

## Les films

> Un utilisateur parcourt les films de sa source, ouvre une fiche, en lance un — et
> le retrouve où il l'avait laissé, sur n'importe lequel de ses écrans.

C'est le premier sprint depuis le sprint 1 qui ajoute un **type de contenu**. Tout ce
qui a été construit jusqu'ici — sources, ingestion, catalogue, lecteurs, favoris —
parle de chaînes en direct et seulement d'elles.

---

## Quatre constats qui commandent l'ordre des tâches

### Le contrat dit que les films sont ingérés. C'est faux.

`ContentType` porte cette phrase : *« `VOD` and `SERIES` are ingested and exposed but
have no dedicated endpoints in v1 »*. La seconde moitié est vraie, la première ne
l'est pas, et c'est vérifiable en trois endroits :

| Où | Ce qu'on y trouve |
|---|---|
| [`XtreamClient.java:103`](../../apps/api/src/main/java/tv/lumo/api/ingest/xtream/XtreamClient.java) | `get_live_categories` et `get_live_streams`. Jamais `get_vod_streams` |
| [`IngestionService.java:183`](../../apps/api/src/main/java/tv/lumo/api/ingest/IngestionService.java) | `"LIVE"` en dur — l'un des deux seuls endroits qui créent une catégorie |
| `db/changelog/0005-catalog.sql` | La table `channel` : ni affiche, ni synopsis, ni durée. Aucune table de films |

**Ce qui existe, c'est la place, et elle a de la valeur** : `category.content_type`
accepte déjà `VOD` et `SERIES` avec sa contrainte, et `playback_progress` a été
conçu pour eux — `ProgressItemType` vaut `VOD` ou `EPISODE`, jamais `LIVE`. La
reprise de lecture marchera le jour où il y aura quelque chose à reprendre. C'est ce
sprint.

**Corriger la phrase du contrat fait partie de S5-01**, et pas en note de bas de
page : une description fausse dans le document qui fait foi est plus dangereuse
qu'une description absente.

### Un film n'est pas une chaîne, et le modèle le dit mal

Une chaîne se joue, point. Un film se **choisit** — et on ne choisit pas sans une
affiche, un synopsis, une année et une durée. La table `channel` n'en porte aucun,
et l'y ajouter mettrait six colonnes nulles sur quinze mille lignes qui n'en veulent
pas.

Une différence technique compte autant que les cinq autres réunies :

| | Chaîne en direct | Film |
|---|---|---|
| URL Xtream | `/live/user/pass/{id}.m3u8` — extension connue d'avance | `/movie/user/pass/{id}.{container_extension}` |
| Nature du flux | HLS, segmenté, sans fin | Un fichier progressif, MP4 ou MKV |
| Déplacement dans le flux | sans objet | exige que le panel réponde aux requêtes `Range` |
| Progression | aucune (`ProgressItemType` n'a pas de `LIVE`) | c'est tout l'intérêt |

**`container_extension` est le champ sans lequel rien ne se lance.** Il vient de
`get_vod_streams` et de nulle part ailleurs ; l'oublier à l'ingestion donne un
catalogue complet, joli, et cent pour cent injouable. C'est le premier test à écrire.

### Le volume est le vrai risque, et il n'est pas du même ordre

Une source courante fait quinze mille chaînes — c'est le chiffre qui a dimensionné
S3-07. Un catalogue de films sur le même panel en fait couramment **trente à
cinquante mille**, et les fiches sont dix fois plus grosses qu'une ligne de chaîne.

Deux conséquences, et elles vont dans des directions opposées :

- **la liste** s'ingère en flux, comme le M3U, avec un plafond et un délai — c'est
  du connu, `M3uStreamParser` a déjà résolu ce problème une fois ;
- **la fiche détaillée** ne s'ingère pas du tout. `get_vod_info` est **un appel HTTP
  par film**. Sur trente mille films, c'est trente mille requêtes vers le serveur de
  l'utilisateur, à chaque synchronisation. Ce n'est pas lent : c'est le genre de
  chose qui fait bannir notre adresse IP par le fournisseur du client.

La fiche se charge **quand quelqu'un l'ouvre**. C'est S5-04, et c'est une décision
d'architecture, pas une optimisation.

### Un M3U ne dit pas qu'une entrée est un film

Xtream a des endpoints séparés, donc la question ne se pose pas pour lui. Une
playlist M3U, si : c'est une liste plate, et rien dedans ne déclare un type.

Les indices existent et aucun n'est fiable seul — un `group-title` qui contient
`VOD` ou `MOVIES`, une URL qui finit en `.mkv` ou `.mp4` au lieu de `.m3u8` ou
`.ts`, un `tvg-id` absent. C'est une **heuristique**, elle se trompera, et il faut
décider ce qu'on fait quand elle se trompe avant de l'écrire dans un parseur.

C'est `ADR 0009`, et c'est S5-00.

---

## Une extension de périmètre, à valider explicitement

`AGENTS.md` §6 liste ce qui est v2 : Chromecast, PiP, timeshift, enregistrement,
multi-profils, contrôle parental, Stalker, Play Billing. **Les films n'y sont pas** —
ils sont dans une zone grise, décrits par le contrat comme exposés sans endpoint.

Ouvrir ce sprint, c'est donc décider que la v1 comprend les films. La décision est
assumée, et deux documents la portent, comme US-11 l'avait fait au sprint 3 :

- `AGENTS.md` §1, qui décrit ce que fait chaque application ;
- `docs/architecture.md` §4, qui décrit le catalogue.

Sans cette mise à jour, la première relecture d'`AGENTS.md` conclura à une dérive.

---

## Ce qui existe déjà, et qu'on ne réécrit pas

| Module | Ce qu'il fournit |
|---|---|
| `ingest` | `M3uStreamParser` (parsing en flux, plafonné), `IngestionHttpClient`, `HostConcurrencyLimiter`, `PrivateAddressGuard`, `SourceUrl` |
| `ingest/xtream` | `XtreamClient` — l'authentification, `streamArray`, la construction d'URL, la limite de connexions |
| `catalog` | La pagination, la recherche trigram, le contrôle de propriété avant toute lecture d'URL |
| `core:player` | `LumoPlayer`, `Media3LumoPlayer`, `LumoVideoSurface` — Media3 lit le MP4 et le MKV sans rien de neuf |
| `core:database` | `CataloguePager`, le patron « la base est la vérité, le réseau rafraîchit » |
| `apps/web` | `/api/playback/[channelId]`, le lecteur HLS et ses échecs nommés (S3-09 → S3-11) |
| `feature:vod` | **Le module existe déjà**, avec ses deux écrans placeholders et sa navigation câblée |
| `apps/api` banc d'essai | Six chemins servis, dont une playlist valide et un flux jouable (S2-03, S3-01) |

`feature:vod` a été retiré de la barre de navigation en S2-02, avec `search` et
`series`. Ce sprint l'y remet — pour `vod` seulement.

---

## Definition of Ready

Celle du sprint 1, plus trois conditions propres à ce sprint :

- la tâche dit ce qu'elle fait sur un catalogue de **trente mille** films, pas sur
  celui du banc d'essai ;
- la tâche dit ce qu'elle affiche quand un film n'a **pas d'affiche** — le cas est
  courant et Lumo n'embarque aucune image de repli (CLAUDE.md, règle 2) ;
- la tâche dit ce qu'elle fait d'un film **adulte**, si la source en signale.

## Definition of Done

Celle du sprint 1, sans allègement, plus :

- **la démo montre un déplacement dans le film**, pas seulement un démarrage. Un
  film qui se lance et ne se déplace pas est un film qu'on ne peut pas regarder en
  deux fois, ce qui est la façon normale de regarder un film ;
- **la reprise se démontre entre deux appareils** : arrêté sur le téléphone, repris
  sur la télévision. Sur un seul appareil, un cache local suffirait à donner le
  change ;
- télécommande en main pour la partie TV.

---

## Deux stories ajoutées — **US-13 et US-14, retenues**

### US-13 — Parcourir et regarder les films de ma source

> **En tant qu'** utilisateur, **je veux** voir les films que mon abonnement propose,
> **afin de** ne pas avoir à chercher ailleurs ce que je paie déjà.

```gherkin
Scenario: Le catalogue de films apparaît
  Given ma source est READY et propose des films
  Then je vois mes films par catégorie, avec leurs affiches
  And le nombre de films de chaque catégorie est affiché
  And la liste se pagine de façon fluide au-delà de 500 films

Scenario: Une source sans films
  Given ma source ne propose que des chaînes en direct
  Then l'entrée "Films" ne me promet pas un catalogue vide
  And rien ne s'affiche comme une erreur

Scenario: La fiche d'un film
  When j'ouvre un film
  Then je vois son affiche, son synopsis, son année et sa durée
  And un bouton de lecture qui est le premier élément atteignable

Scenario: Une fiche sans détail
  Given le serveur de ma source ne renvoie pas de synopsis
  Then la fiche affiche ce qu'elle a, sans emplacement vide ni texte de remplacement
  And la lecture reste possible

Scenario: Lecture
  When je lance un film
  Then l'image apparaît en moins de cinq secondes sur une connexion normale
  And je peux me déplacer dans le film, en avant comme en arrière

Scenario: Le serveur ne permet pas le déplacement
  Given le serveur de ma source ne répond pas aux requêtes de plage
  Then la lecture fonctionne quand même
  And le contrôle de déplacement dit qu'il est indisponible, il ne fait pas semblant

Scenario: Film indisponible
  Then je vois un message d'erreur clair et un bouton "Réessayer"
  And l'application ne plante pas et ne reste pas sur un écran noir muet

Scenario: Recherche
  When je cherche un titre
  Then je vois les films correspondants, comme pour les chaînes
```

**Notes** — Aucune affiche n'est embarquée dans l'application ni servie par notre
infrastructure : elle vient du panel de l'utilisateur ou il n'y en a pas. Le média ne
transite jamais par nos serveurs (`architecture.md` §1) — un film pas plus qu'une
chaîne, et la limite du navigateur reste celle d'[`ADR 0007`](../adr/0007-web-playback-direct-only.md).

**Points : 13**

### US-14 — Reprendre là où je me suis arrêté

> **En tant qu'** utilisateur, **je veux** retrouver un film à l'endroit où je l'ai
> quitté, **afin de** pouvoir le regarder en deux fois, sur deux écrans.

```gherkin
Scenario: Reprise sur le même appareil
  Given j'ai arrêté un film au bout de vingt minutes
  When je le rouvre
  Then on me propose de reprendre à vingt minutes
  And je peux choisir de recommencer au début

Scenario: Reprise sur un autre écran
  Given j'ai arrêté un film sur mon téléphone
  When j'ouvre ce film sur ma télévision
  Then la position est la même

Scenario: Le rail "Reprendre"
  Given j'ai commencé des films sans les finir
  Then je les vois en tête de mon catalogue, le plus récent d'abord

Scenario: Un film terminé quitte le rail
  Given j'ai regardé un film jusqu'à la fin
  Then il n'apparaît plus dans "Reprendre"

Scenario: Une chaîne en direct n'a pas de progression
  When je regarde une chaîne en direct
  Then rien n'est enregistré comme progression
```

**Notes** — `PUT /me/progress` et `GET /me/progress` existent depuis `SRV-05` et
n'ont jamais été appelés par personne. « Terminé » est un seuil, pas un événement :
au-delà de 95 % de la durée, l'élément sort du rail. Sans durée connue, il n'en sort
jamais — et c'est mieux que de le retirer à tort.

**Points : 8**

---

## Tâches

Une case par tâche, un pourcentage dès que l'avancement est partiel. La checklist se
met à jour **dans le commit qui livre le travail**, pas après.

| | Id | Tâche | Lot | Cible | Points | Avancement |
|---|---|---|---|---|---|---|
| ☑ | S5-00 | ADR 0009 — reconnaître un film dans une playlist M3U | décision | décision | 2 | 100 % |
| ☑ | S5-01 | Contrat : `VodItem`, ses deux lectures, et la phrase à corriger | contrat | contrat | 5 | 100 % |
| ☑ | S5-02 | Base : `vod_item`, et l'upsert qui survit à une resynchronisation | serveur | api | 3 | 100 % |
| ☑ | S5-03 | Ingestion Xtream : catégories et films, en flux | serveur | api | 5 | 100 % |
| ☑ | S5-04 | La fiche d'un film, à la demande et jamais à l'ingestion | serveur | api | 3 | 100 % |
| ☑ | S5-05 | Ingestion M3U : appliquer la règle de S5-00 | serveur | api | 3 | 100 % |
| ☑ | S5-06 | Le plafond de volume, et ce que l'écran en dit | serveur | api + recette | 5 | 100 % |
| ☑ | S5-07 | `core:data` et Room : les films hors ligne | socle | android | 5 | 100 % |
| ☑ | S5-08 | Mobile : grille d'affiches, fiche, lecture | mobile | mobile | 8 | 100 % |
| ☑ | S5-09 | TV : la même au D-pad, et la carte du parcours | tv | tv | 8 | 100 % |
| ☑ | S5-10 | Web : grille et fiche | web | web | 5 | 100 % |
| ☑ | S5-11 | Reprise de lecture, et le rail qui la rend visible | 3 clients | mobile + tv + web | 8 | 100 % |

**Avancement du sprint : 100 % de 60 points.** Les douze tâches sont livrées. Reste
la recette, qui n'est pas dans ce tableau et sans laquelle rien n'est « terminé » au
sens de la DoD.

---

### S5-00 — ADR 0009 : reconnaître un film dans un M3U · **2** · ☑ tranché

> **Décision écrite dans [`adr/0009`](../adr/0009-m3u-film-detection.md).** Les
> trois questions ont leur réponse, et une quatrième s'est imposée en les écrivant.
>
> **1. Seule l'URL classe.** Pas « deux indices sur quatre » : `group-title` et
> l'absence de `tvg-id` ne classent **pas du tout**, ni seuls ni combinés. Les deux
> indices retenus — une extension de fichier, le segment `/movie/` — décrivent *ce
> que la chose est* ; les deux écartés décrivent *comment on l'a appelée*, et on
> appelle des chaînes en direct `CINE+`, `Film4`, `VOD Sports News`. L'URL est
> aussi le seul indice dont le sens ne dépend d'aucune langue : une liste de
> mots-clés par langue est une liste perpétuellement en retard d'une langue.
>
> **2. Le doute penche vers `LIVE`**, comme proposé, et la raison est asymétrique :
> un film rangé dans les chaînes est en désordre, une chaîne rangée dans les films
> est **cassée** — grille d'affiches sans affiche, « reprendre à 20 min » sur un
> flux continu, et des lignes de `playback_progress` pour quelque chose qui n'a pas
> de position.
>
> **3. La correction a une forme et un prix, pas encore une date.** Elle est **sur
> la catégorie, pas sur la source** : une source porte les deux genres, donc une
> bascule par source serait fausse partout où on l'afficherait. Une catégorie est
> exactement le `group-title` que l'utilisateur voit déjà. **3 points**, et c'est
> écrit au hors-périmètre ci-dessous.
>
> **4. Une conséquence qui contraint S5-01 : un film M3U n'a pas de
> `container_extension`.** Un film Xtream en a besoin parce que son URL se
> *construit* ; une entrée M3U porte l'URL complète, extension comprise — c'est
> précisément ce que la règle 1 lit. Le champ est donc rempli pour Xtream et nul
> pour M3U, et tout ce qui le traiterait comme obligatoire rejetterait la moitié
> des sources pour lesquelles cet ADR existe.

La seule vraie inconnue du sprint, donc elle se tranche en premier — c'est le rôle
qu'avait S3-00 au sprint précédent.

Les indices, du plus au moins fiable :

| Indice | Ce qu'il vaut |
|---|---|
| L'URL finit en `.mkv`, `.mp4`, `.avi` | Le meilleur. Un flux en direct ne se sert pas ainsi |
| L'URL contient `/movie/` | Excellent quand la playlist vient d'un panel Xtream exporté |
| `group-title` contient `VOD`, `MOVIE`, `FILM` | Courant, et faux dès qu'une chaîne s'appelle « Ciné+ » |
| Pas de `tvg-id` | Trop faible seul : beaucoup de chaînes n'en ont pas |

Trois questions à trancher, et l'ADR n'est fini que quand les trois ont une réponse
écrite :

1. **Combien d'indices concordants** pour classer en `VOD` ? Un seul, s'il est fort,
   ou une combinaison ?
2. **Le doute penche de quel côté ?** Proposition : vers `LIVE`. Un film rangé dans
   les chaînes se lance quand même et se cherche ; une chaîne rangée dans les films
   apparaît dans une grille d'affiches qui n'en a pas, à côté d'un bouton « reprendre
   à 20 min » qui n'a aucun sens sur du direct.
3. **L'utilisateur peut-il corriger ?** Une bascule « ce groupe est un catalogue de
   films » sur la source coûte un champ et supprime toute la classe des faux
   classements. À chiffrer, pas forcément à faire dans ce sprint.

Une source Xtream ne passe **jamais** par cette heuristique : ses endpoints séparés
font foi. L'ADR le dit, sinon quelqu'un l'appliquera partout par symétrie.

---

### S5-01 — Contrat : `VodItem` et ses deux lectures · **5** · dépend de S5-00 · ☑

> **Livré avec S5-02, et pas par choix.** `skipDefaultInterface` fait d'une
> opération non implémentée une erreur de compilation (ADR 0001) : ajouter
> `listVod` au contrat rend l'API non compilable tant que la table n'existe pas.
> C'est le même enchaînement qu'à S4-00/S4-01, et c'est la règle qui fonctionne
> comme prévu plutôt qu'une entorse.
>
> **Une correction à la spécification de la tâche.** Elle disait
> `container_extension` « non nullable en base, jamais exposé ». La règle 4 de
> l'ADR l'a contredite le matin même : il est **nullable**, parce qu'un film M3U
> n'en a pas. Non exposé, en revanche, et pour une raison de plus que le secret —
> c'est un fragment d'URL Xtream dont aucun client n'a rien à faire, et un champ
> que tous les consommateurs ignorent n'a pas sa place dans un contrat.
>
> **Deux petites choses tranchées en écrivant.** `VodPlaybackInfo` est un schéma à
> part plutôt qu'un `channel_id` renommé en quelque chose de générique : le
> renommage casserait trois clients générés aujourd'hui pour économiser un objet.
> Et `plot` est stocké mais **absent de toute projection de liste** — un test le
> vérifie, parce que c'est le genre de champ qu'on ajoute au `SELECT` par réflexe.
>
> **Le risque de collision d'`item_ref` est fermé, pas seulement déclaré.**
> `source_id` entre dans le corps, dans la réponse, dans le filtre de lecture et
> dans l'index unique (migration `0015`). Le rejeter comme « à faire plus tard »
> aurait laissé un contrat qui ment pendant un sprint entier.
>
> `redocly` valide, aucun avertissement ajouté, non-dérive verte, **198 tests
> API**, Android et web inchangés et verts.

**`VodItem`** — l'équivalent de `Channel` pour un film, et il ne le copie pas :

| Champ | Pourquoi |
|---|---|
| `id`, `source_id`, `category_id`, `external_id`, `name`, `position` | Le socle commun, mêmes règles qu'une chaîne |
| `poster_url` | Nullable. Vient du panel, jamais de nous |
| `year`, `duration_seconds`, `rating` | Nullables tous les trois. Beaucoup de panels n'en servent aucun |
| `plot` | Nullable. **Chargé à la demande**, absent de la liste — voir S5-04 |
| `container_extension` | **Non nullable en base, jamais exposé.** Il sert à construire l'URL de lecture, et l'URL de lecture ne sort que de l'endpoint dédié |
| `is_adult` | Comme sur `Channel` |

**`GET /sources/{id}/vod`** — paginé, `categoryId`, `q` et `ids` : exactement la
forme de `GET /sources/{id}/channels`, pour que les clients réutilisent leur pagination
au lieu d'en écrire une seconde. Et **aucune URL de lecture dans la réponse**, pour
la raison déjà écrite sur les chaînes : mille films ne portent pas mille URL
sensibles.

**`GET /vod/{id}/playback`** — le jumeau de `GET /channels/{id}/playback`, contrôle
de propriété compris.

**`GET /sources/{id}/categories?contentType=VOD`** existe déjà et n'a rien à changer.
C'est le bénéfice de `content_type` posé au sprint 1.

**Deux points à trancher dans cette tâche, pas plus tard :**

**`SyncStep` gagne une valeur.** L'énumération dit d'elle-même que ses valeurs sont
les phases réelles du serveur et que *trois étapes vraies valent mieux que quatre
inventées*. Ingérer les films est une phase réelle et longue : `PARSING_VOD` s'ajoute
entre `PARSING_CHANNELS` et `FETCHING_EPG`. Ne pas l'ajouter laisserait l'écran
d'attente de S3-03 figé sur « lecture des chaînes » pendant une minute.

**`item_ref` ne porte pas la source, et c'est un risque de collision.** Le contrat le
décrit comme *l'identifiant de l'élément dans sa source*, opaque, forgé par le panel.
Deux abonnements différents peuvent parfaitement utiliser le même identifiant pour
deux films différents — et la progression de l'un s'appliquerait à l'autre. Deux
sorties : préfixer par l'identifiant de source côté client, ou ajouter `source_id` à
`SaveProgressRequest`. **La seconde**, parce qu'un préfixe est une convention que
trois clients doivent appliquer identiquement et qu'un seul suffit à la casser.

**Et la phrase de `ContentType` est corrigée** : les films ne sont pas « ingérés »,
ils le deviennent avec ce sprint. Une description fausse dans le document qui fait
foi coûte plus cher qu'une description absente.

---

### S5-02 — Base : `vod_item` · **3** · dépend de S5-01 · ☑

> **Livré**, migration `0015`, sur le modèle exact de `channel` : index unique sur
> `(source_id, external_id)` pour que la resynchronisation soit un upsert, index
> trigram sur le nom, `stream_url` lu par une seule requête.
>
> **Deux colonnes que la tâche ne prévoyait pas.** `plot_fetched_at`, parce que
> S5-04 doit distinguer « jamais chargé » de « chargé et vide » — un synopsis
> absent chez le fournisseur ne doit pas provoquer un appel à chaque ouverture.
> Et `container_extension` nullable, par la règle 4 de l'ADR.
>
> **La renumérotation de `playback_progress` est dans la même migration**, et son
> changeset le dit : aucun remplissage n'est possible, une ligne existante ne peut
> pas être attribuée à une source après coup. Il n'y en a aucune à perdre — rien
> n'a jamais appelé `PUT /me/progress` — et supprimer est honnête là où deviner ne
> le serait pas.

Une table, sur le modèle exact de `channel` : `PRIMARY KEY` uuid, `source_id` en
cascade, `category_id` en `SET NULL`, et surtout **l'index unique sur
`(source_id, external_id)`**. C'est lui qui fait de la resynchronisation un upsert au
lieu d'un « supprimer puis réinsérer », et c'est ce qui permettra à un favori et à une
progression de continuer à pointer quelque chose de réel.

`stream_url` porte le même commentaire que sur `channel` : jamais sélectionné par une
requête de liste, lu uniquement par l'endpoint de lecture, une ligne à la fois.

Index trigram sur `name`, comme pour les chaînes : la recherche de S5-08 en dépend et
un `ILIKE '%q%'` sur trente mille lignes sans index est un balayage complet.

---

### S5-03 — Ingestion Xtream : catégories et films · **5** · dépend de S5-02 · ☑

> **Livré.** `get_vod_categories` et `get_vod_streams` sur le patron du direct,
> `buildVodStreamUrl` en méthode à part, et le `"LIVE"` en dur remplacé par
> `ContentType` aux trois endroits qui créent une catégorie.
>
> **Le film sans `container_extension` n'est pas émis du tout**, et le filtre est
> dans le client plutôt que dans le service : un film dont l'URL ne peut pas être
> construite n'est pas un film que la couche du dessus doit avoir à écarter.
> Le test le prouve sur une fixture de trois films dont un sans extension.
>
> **Une décision que la tâche ne prévoyait pas, et c'est la plus importante :
> l'échec du catalogue de films ne fait pas échouer la source.** Un panel qui sert
> la télévision et refuse `get_vod_streams` — ou n'a simplement aucun film, ce qui
> est courant — doit rester `READY` avec ses chaînes. Laisser remonter
> l'exception transformerait une source qui marche en source cassée, pour un
> catalogue que l'utilisateur n'ouvrira peut-être jamais. La suppression des films
> non revus est gardée par la même logique : une liste vide après une lecture
> échouée veut dire « la lecture a échoué », pas « le panel les a tous retirés ».
>
> **Trois lectures tolérantes plutôt qu'exactes**, parce que les panels réels le
> sont : `year` accepte `1998`, `1998-03-12` et `N/A` ; `episode_run_time` est en
> minutes et devient des secondes, jamais un zéro ; l'affiche se lit sur
> `stream_icon` **ou** `cover`, que les panels servent indifféremment.
>
> **202 tests API**, dont 4 nouveaux sur le client Xtream.

`get_vod_categories` et `get_vod_streams`, sur le patron de `streamLiveCategories` et
`streamLiveStreams` — même `streamArray`, même consommation une ligne à la fois, même
`HostConcurrencyLimiter`.

**Trois différences avec le direct, et elles se testent une par une :**

1. **L'URL** est `/movie/{user}/{pass}/{id}.{container_extension}`, pas
   `/live/…/{id}.m3u8`. `buildStreamUrl` gagne une variante, il ne se généralise pas
   à coups de paramètres booléens.
2. **`container_extension` peut manquer.** Un film sans lui n'est pas jouable : il est
   **ignoré à l'ingestion**, et compté. Un catalogue qui annonce 12 000 films dont 300
   ne démarreront jamais est pire qu'un catalogue qui en annonce 11 700.
3. **Les catégories s'écrivent en `VOD`**, ce qui veut dire que le `"LIVE"` en dur
   d'`IngestionService` disparaît au profit du type porté par l'appelant. C'est une
   modification d'un code existant et testé : elle se fait en premier, seule, avec ses
   tests, avant d'ajouter quoi que ce soit.

**Le rapport de fin d'ingestion compte les deux séparément** — « 842 chaînes,
12 400 films ». Un total unique cacherait qu'un des deux est vide.

---

### S5-04 — La fiche d'un film, à la demande · **3** · dépend de S5-03 · ☑

> **Livré**, et il manquait une opération que ni la tâche ni S5-01 n'avaient
> nommée : la liste ne porte pas de synopsis, donc il fallait bien un endroit d'où
> le lire. C'est **`GET /vod/{id}`**, ajouté au contrat ici.
>
> **Une inversion de dépendance plutôt qu'un cycle.** `ingest` dépend déjà de
> `catalog` — c'est lui qui écrit le catalogue — donc un contrôleur qui serait allé
> chercher `XtreamClient` dans l'autre sens aurait bouclé les deux paquets.
> L'interface `VodPlotSource` est déclarée dans `catalog`, où le besoin est, et
> implémentée dans `ingest`, où vivent les appels sortants.
>
> **Ce qui est horodaté et ce qui ne l'est pas**, et c'est toute la valeur d'avoir
> deux colonnes :
>
> | Ce qui s'est passé | Horodaté ? | Pourquoi |
> |---|---|---|
> | Le panel a répondu, sans synopsis | **oui** | La réponse ne changera pas ; redemander à chaque ouverture dépenserait le panel de quelqu'un sur une question réglée |
> | Le panel était injoignable | **non** | C'est un moment, pas un fait. La prochaine ouverture réessaie |
> | Source M3U, aucun panel à interroger | **oui** | Sa playlist est tout ce qu'on saura jamais d'elle |
>
> **`plot_fetched_at` n'est pas au contrat et ne doit pas y être** : décider quand
> redemander dépense la capacité du panel de l'utilisateur, et cette décision reste
> de ce côté du fil.
>
> 3 cas de plus, **205 tests API**.

**La tâche la plus courte du sprint et la plus structurante.**

`get_vod_info` prend un identifiant et rend une fiche. **Un appel par film.** L'appeler
à l'ingestion, c'est trente mille requêtes vers le serveur de l'utilisateur à chaque
synchronisation : pas lent — bannissable. Le `HostConcurrencyLimiter` protège notre
infrastructure, pas celle du client.

Donc : `GET /sources/{id}/vod` rend ce que la liste porte — titre, affiche, année,
durée quand le panel les donne dans `get_vod_streams`. `plot` et le reste ne sont
chargés qu'à l'ouverture d'une fiche, par un appel au panel, **mis en cache en base
avec un horodatage**.

Ce qui donne la règle : une fiche déjà vue est instantanée, une fiche jamais ouverte
coûte un aller-retour, et l'écran de S5-08 doit rendre cet aller-retour supportable —
l'affiche et le titre sont déjà là, seul le synopsis arrive après.

**Une fiche que le panel refuse n'est pas une erreur d'écran** : la fiche s'affiche
avec ce qu'elle a, et le bouton de lecture marche. Le synopsis est un confort ; le
film est le produit.

---

### S5-05 — Ingestion M3U : appliquer la règle de S5-00 · **3** · dépend de S5-00 · ☑

> **Livré.** `M3uContentClassifier` : une URL en entrée, un `ContentType` en
> sortie, aucune dépendance, testable sans réseau. C'est la règle 2 de l'ADR.
>
> **Le test dit comment la règle se trompe**, comme la règle 3 l'exige, et ce
> n'est pas de la modestie : une liste des cas où elle a raison serait un test qui
> prétend qu'elle ne se trompe jamais, et la personne suivante n'aurait aucune idée
> du sens dans lequel les erreurs devaient tomber. Les deux directions y sont, avec
> le raisonnement qui les rend acceptables.
>
> **Deux choses que la tâche ne prévoyait pas, trouvées en écrivant :**
>
> **La chaîne de requête n'est pas le chemin.** Les vraies playlists portent des
> jetons et des identifiants de session, et un jeton peut finir par n'importe
> quoi — `?session=x.mkv` classerait un direct en film. Le classificateur coupe
> avant `?` et `#`.
>
> **Un point avant le dernier slash appartient à un hôte, pas à un fichier.**
> `http://vod.example/live/1` n'est pas un film.
>
> **Et une conséquence sur les catégories que le modèle rendait déjà possible :**
> `categoryIds` est maintenant clé sur `(type, nom)`. Un groupe `VOD - ACTION` dont
> une entrée est servie en `.m3u8` produit deux catégories du même nom, une `LIVE`
> et une `VOD` — exactement ce que l'index unique `(source_id, content_type,
> external_id)` autorise depuis le sprint 1.
>
> **Aucune étape `PARSING_VOD` côté M3U**, et c'est cohérent avec la règle de
> l'énumération : une playlist se lit une fois, et ses films sortent de la même
> passe que ses chaînes. Une étape que l'implémentation ne distingue pas est une
> fiction rassurante.
>
> 15 cas de plus, **220 tests API**.

Le classement décidé dans l'ADR, écrit une fois, dans une classe qui ne fait que ça
et qui se teste sans réseau — une entrée M3U en entrée, un `ContentType` en sortie.

**Une table de cas dans le test, pas des exemples choisis** : URL en `.mkv`,
`group-title` « VOD - ACTION », chaîne nommée « Ciné+ Premier » servie en `.m3u8`,
entrée sans `tvg-id` ni extension. Chacun de ces quatre cas est une façon dont
l'heuristique se trompe, et le test est là pour dire **comment** elle se trompe, pas
pour prétendre qu'elle ne se trompe pas.

Le comptage de fin d'ingestion distingue les deux, comme pour Xtream.

---

### S5-06 — Le plafond de volume · **5** · dépend de S5-03 · ☑

> **Livré, et deux des trois volets étaient déjà là — ce qui se dit plutôt que
> se recompte.**
>
> **Le plafond.** `SizeCappedInputStream` compte les octets **lus**, dans
> `IngestionHttpClient`, par lequel passent toutes les lectures sortantes. Le
> catalogue de films emprunte donc exactement le même chemin que `/oversized.m3u`
> et hérite du même `SOURCE_TOO_LARGE` sans une ligne de plus. Et
> **l'isolement était déjà écrit en S5-03** : un catalogue hors plafond ne fait
> pas échouer la source, ses chaînes restent, elle finit `READY`.
>
> **L'écran mentait, et c'est ce que cette tâche a vraiment corrigé.** Les deux
> clients avaient un repli sûr, donc rien n'a cassé quand `PARSING_VOD` est
> arrivé — mais le web l'étiquetait *« récupération du guide »* par son `default`,
> et le téléphone *« démarrage »*. Un repli sûr qui affiche une phrase fausse est
> pire qu'une erreur. Le `switch` du web est désormais **exhaustif, sans
> `default`** : la sixième phase, le jour où elle existera, sera une erreur de type
> plutôt qu'un mot faux sur l'écran de quelqu'un.
>
> **`PARSING_VOD` est sur la liste Xtream seulement**, et ce n'est pas un oubli :
> une playlist se lit une fois, donc une source M3U ne rapporte jamais cette
> phase. La règle de l'énumération vaut dans les deux sens.
>
> **Le banc gagne son septième chemin, et pas celui qui était prévu.** Le document
> demandait `/xtream-vod-huge/` ; or **le banc n'avait alors aucun panel Xtream
> qui fonctionne** — seulement `/xtream-401/` et `/xtream-garbage/`. Un chemin VOD
> Xtream aurait demandé d'écrire un faux panel entier, ce qui a fini par être fait
> au sprint suivant. Ce qui est livré à la
> place est ce dont la recette a besoin en premier : **`/mixed.m3u`**, une playlist
> qui mêle chaînes et films, dont **chaque entrée exerce une règle de l'ADR 0009 —
> y compris les deux cas où la règle se trompe exprès**. C'est le seul endroit où
> ces deux-là sont écrits comme quelque chose à regarder plutôt qu'à corriger.
>
> **Une limite dite plutôt que masquée** : les fichiers de film du banc sont des
> octets MPEG-TS sous un nom de film — **remplacés par de vrais conteneurs le
> 31 août 2026**. Assez pour la classification et
> l'ingestion — ADR 0009 classe sur l'URL — pas assez pour une vraie lecture dans
> un navigateur. La recette le dira ; une fixture qui ferait semblant serait pire.
>
> **Et un manque de couverture, signalé et non comblé** : *rien dans ce dépôt
> n'exerce `IngestionService`*. C'était déjà vrai avant ce sprint — l'ingestion est
> testée au niveau des parseurs et du client — donc la décision de S5-03 (un
> catalogue de films en échec laisse la source `READY`) n'a pas de test. La
> construire ici sortirait du périmètre ; elle est à chiffrer, et en attendant
> c'est un cas de recette.
>
> **Écrit le 31 août 2026 : ce manque a duré deux sprints et a coûté un vrai bug.**
> `IngestionServiceIntegrationTest` le comble, et l'un de ses cinq cas est
> exactement celui-ci. Ce qui l'a débloqué n'était pas le chiffrage, c'était le
> panel Xtream du banc — on ne teste pas une ingestion Xtream sans panel Xtream.

Ce qui protège le serveur, et ce que l'utilisateur en voit.

**Côté serveur** : le plafond de taille et le délai qui existent pour le M3U
s'appliquent au catalogue de films. `SOURCE_TOO_LARGE` est déjà le code, il ne s'en
invente pas un second. L'ingestion des films n'est **pas** dans la même transaction
que celle des chaînes : un catalogue de films hors plafond ne doit pas faire échouer
une source dont les chaînes sont parfaitement valides. Elle se termine `READY` avec
un avertissement, pas en erreur.

**Côté écran** : `PARSING_VOD` s'affiche dans le suivi d'ingestion de S3-03 et de son
équivalent mobile. Une minute sur « lecture des chaînes » pendant que le serveur lit
trente mille films est exactement le silence qui fait fermer l'application.

**Côté banc d'essai** : un septième chemin, `/xtream-vod-huge/`, qui sert un
catalogue de films au-delà du plafond. Sans lui, ce cas ne se recette pas — et c'est
tout le principe du banc, prouver les erreurs sans jamais toucher à une source réelle
(CLAUDE.md, règle 2).

---

### S5-07 — `core:data` et Room : les films hors ligne · **5** · dépend de S5-01 · ☑

Le socle Android, avant les deux écrans, pour la raison qui a fait de S2-01 un
préalable dur.

Une entité `VodItemEntity`, un `VodDao`, un `VodPager` sur le patron de
`CataloguePager`, et un `VodRepository` qui rend des `Flow`. La base est la vérité, le
réseau rafraîchit.

**Un piège propre aux affiches, et il est spécifique à la télévision.** Une grille de
films charge des dizaines d'images par écran, sur un appareil qui a souvent 1 Go de
mémoire utilisable. Les affiches se demandent **à la taille de la carte**, jamais en
pleine résolution, et le cache disque est plafonné. Une grille de chaînes ne posait
pas ce problème : un logo pèse cinquante fois moins qu'une affiche.

**Aucune image de repli.** Pas d'affiche générique, pas de silhouette : une carte sans
affiche montre son titre sur un aplat. Lumo n'embarque aucune illustration de contenu
(CLAUDE.md, règle 2), et une affiche inventée est un contenu inventé.

---

### S5-08 — Mobile : grille, fiche, lecture · **8** · dépend de S5-07 · ☑

`feature:vod` cesse d'être un placeholder, et revient dans la barre de navigation que
S2-02 avait réduite.

Une grille d'affiches en portrait, la bande de catégories `VOD` au-dessus, la
recherche par `?q=` — le même écran que les chaînes dans sa mécanique, un autre dans
sa forme, parce qu'on choisit un film avec les yeux et une chaîne avec un nom.

**La fiche** : affiche, titre, année, durée, synopsis quand S5-04 l'a rendu, et un
bouton de lecture qui est le premier élément atteignable. Rien d'autre en v1 — pas
d'acteurs, pas de recommandations, pas de bande-annonce.

**Le lecteur est celui de S2-11**, avec deux ajouts et une soustraction :

- une **barre de progression déplaçable** — sans elle, un film n'est pas regardable ;
- l'enregistrement de la position, qui est S5-11 ;
- **pas d'indicateur « direct »**, qui n'a aucun sens ici.

**Quand le panel ne répond pas aux requêtes `Range`**, le déplacement est impossible.
Media3 le découvre à la première tentative. Le contrôle s'affiche alors **désactivé
et expliqué** — un curseur qui ne bouge pas sans dire pourquoi est un défaut ; un
curseur absent est une fonction qu'on croit ne pas avoir livrée.

**Une source sans films ne montre pas un onglet vide** : l'entrée disparaît quand
`GET /sources/{id}/categories?contentType=VOD` ne rend rien. Une promesse vide est
pire qu'une absence.

---

### S5-09 — TV : la même, au D-pad · **8** · dépend de S5-07 · ☑

La grille d'affiches sur `VodTvScreen`, et la bande de catégories `VOD` en haut :
c'est la structure de `LiveTvScreen`, et elle est reprise telle quelle. La divergence
serait le défaut.

**Trois points qui ne se voient qu'à la télécommande** et qui sont le vrai contenu de
cette tâche :

- **une affiche en portrait tient moins de colonnes qu'une carte de chaîne.** Le
  nombre de colonnes se fixe sur la lisibilité à trois mètres, pas sur ce qui rentre ;
- **la fiche est une nouvelle surface de focus**, donc une nouvelle section dans
  [`tv-focus-map.md`](../design/tv-focus-map.md), avec l'élément focalisé à l'arrivée
  et les quatre directions depuis chaque zone. Une case vide dans ce tableau est un
  défaut, pas une omission de rédaction ;
- **`BACK` depuis la fiche revient sur le film qu'on regardait dans la grille**, pas
  en tête. C'est la règle d'US-10, et elle vaut ici pour la même raison.

Le lecteur reste celui de S2-14, plus la barre de progression. Sur une télécommande,
`LEFT` et `RIGHT` pendant la lecture déplacent — c'est le geste attendu, et il ne
doit pas ouvrir l'overlay d'information par accident.

---

### S5-10 — Web : grille et fiche · **5** · dépend de S5-01 · ☑

`app/sources/[id]/vod`, sur le modèle exact de `channels` : catégorie, page et
recherche dans l'URL, donc partageable, compatible avec le bouton retour et
**fonctionnel sans JavaScript**.

`/api/playback/vod/[id]` reprend le Route Handler de S3-09, `Cache-Control: no-store`
compris : l'URL ne descend jamais dans le HTML rendu.

**Le lecteur n'est pas celui des chaînes, et ce n'est pas un choix.** `hls.js` ne sert
à rien sur un MP4 progressif : c'est un `<video src>` nu. En revanche, **la limite
d'[`ADR 0007`](../adr/0007-web-playback-direct-only.md) s'applique identiquement** —
un panel servi en `http://` reste du contenu mixte, bloqué sans contournement depuis
`https://lumo.tv`. Aucune décision nouvelle à prendre : la même, et les mêmes messages
d'échec nommés qu'en S3-11.

Le déplacement dans le film exige les requêtes `Range` **et** un en-tête CORS qui les
autorise. C'est une condition de plus que pour le direct, et elle mérite son propre
message d'échec plutôt que d'être rangée dans « indisponible ».

> **Correction à la livraison — la moitié CORS de ce paragraphe est fausse.**
>
> Un `<video>` sans attribut `crossorigin` émet une requête *no-cors* : le
> navigateur négocie les `Range`, lit le `206` et joue le fichier quelle que soit
> l'origine, sans qu'aucun `Access-Control-Allow-Origin` n'intervienne. C'est
> exactement pourquoi le chemin natif Safari du lecteur de chaînes n'a jamais eu
> besoin de CORS alors que son chemin `hls.js` en a besoin : **CORS gouverne
> `fetch`, pas un élément média.**
>
> La condition réelle, et la seule, est donc que le serveur de l'utilisateur
> réponde `206` aux `Range` et annonce `Accept-Ranges`. Beaucoup de panels ne le
> font pas ; le navigateur rend alors un `seekable` vide, son propre curseur
> devient inerte, et c'est ce cas-là qui reçoit son message nommé. Le reste du
> paragraphe tient : la limite mixte de l'`ADR 0007` s'applique à l'identique.

---

### S5-11 — Reprise de lecture, et le rail · **8** · dépend de S5-08, S5-09, S5-10 · ☑

Les deux endpoints existent depuis `SRV-05` et personne ne les a jamais appelés.

**Quand écrire.** À la mise en pause, à la sortie du lecteur, et toutes les trente
secondes pendant la lecture. Pas à chaque image : `PUT /me/progress` est un upsert
idempotent, pas un flux. Trente secondes est le compromis entre « on perd une minute
si l'application est tuée » et « on écrit deux mille fois par film ».

**Quand lire.** À l'ouverture d'une fiche, une fois, avec `?itemType=VOD&itemRef=…` —
le contrat dit que ce couple rend au plus un élément, et c'est exactement pour ça
qu'il existe.

**Reprendre est proposé, jamais imposé.** « Reprendre à 20:14 » et « Recommencer »,
les deux visibles, le premier focalisé. Une reprise automatique est une bonne idée
jusqu'au jour où quelqu'un veut revoir le début.

**Le rail** — les films commencés et non finis, le plus récent d'abord, en tête du
catalogue de films sur les trois surfaces. `GET /me/progress` les rend déjà dans cet
ordre : *« ordered by `updated_at`, most recent first — which is also the order a
"Continue watching" rail wants »*. Sur la télévision, c'est une **puce**, pour la
raison écrite en S4-08 et dans `LiveTvScreen`.

**Terminé est un seuil, pas un événement** : au-delà de 95 % de la durée, l'élément
sort du rail. Sans durée connue — et beaucoup de panels n'en donnent pas — il n'en
sort jamais, ce qui est le bon défaut : un film qui reste dans « Reprendre » est un
agacement, un film qui en disparaît avant la fin est une perte.

**Rien n'est enregistré pour une chaîne en direct.** `ProgressItemType` n'a pas de
`LIVE`, et le contrat le dit : envoyer une progression pour du direct est un bug
client, pas un cas supporté. Un test le vérifie sur chaque client, parce que c'est le
genre d'appel qu'un lecteur partagé entre deux usages fait tout seul.

> **Une décision prise à la livraison : ce que les clients mettent dans `item_ref`.**
>
> Le contrat le décrivait comme « frappé par le panel de l'utilisateur » et « pas un
> de nos identifiants, donc il n'y a rien où le chercher ». Or **le rail a
> précisément besoin de le chercher** : `GET /me/progress` rend des identifiants et
> des positions, pas des affiches et des titres, et `GET /sources/{id}/vod?ids=` est
> la seule opération qui fasse la conversion.
>
> Les clients envoient donc **`VodItem.id`**, et le contrat le dit maintenant. Ça ne
> coûte rien en stabilité : `vod_item` est upserté sur `(source_id, external_id)`,
> donc une ligne garde son identifiant à travers les resynchronisations — la
> propriété pour laquelle une référence frappée par le panel aurait été choisie.
> `source_id` reste dans la clé bien qu'il devienne redondant pour ce type : une clé
> qui change de forme quand `EPISODE` arrivera coûterait plus cher.
>
> **Ce qui n'est pas fait de ce côté :** la progression ne passe pas par Room sur
> Android. Une position s'écrit sur un appareil et se lit sur un autre, et un cache
> local devrait fusionner deux positions qui se contredisent, hors ligne, sans savoir
> laquelle est la plus récente. Le rail est donc vide dans le train, et un film
> ouvert hors ligne repart du début. C'est visible, ce n'est pas faux, et c'est
> mieux que de reprendre quelqu'un à vingt minutes d'un film qu'il a fini hier soir.

---

## Récapitulatif

| Bloc | Tâches | Points |
|---|---|---|
| Décision et contrat | S5-00, S5-01 | 7 |
| Serveur et ingestion | S5-02 → S5-06 | 19 |
| Socle Android | S5-07 | 5 |
| Clients | S5-08 → S5-10 | 21 |
| Reprise | S5-11 | 8 |
| **Total** | **12 tâches** | **60** |

**Ordre de réalisation** — les dépendances comptent plus que les priorités :

```
S5-00 → S5-01 → S5-02 → S5-03 → S5-04 → S5-06
                            → S5-05
                     S5-07 → S5-08 → S5-11
                           → S5-09
                     S5-10 (dès S5-01 servi)
```

S5-00 avant S5-01 : le contrat d'un film dépend de ce que l'ADR décide d'un film.
S5-03 avant S5-04, parce qu'une fiche à la demande n'a de sens qu'une fois la liste
ingérée. S5-11 en dernier : il touche les trois lecteurs et n'a rien à toucher tant
qu'ils n'existent pas.

**La coupure, si 60 points est trop** — et c'est proche des 58 du sprint 3, qui ne
portaient qu'une plateforme :

- **Sprint 5a — le serveur sait ce qu'est un film** : S5-00 → S5-07, **31 points**.
  Ne se démontre pas à un utilisateur, ce qui est son défaut ; mais tout le reste en
  dépend et il se vérifie entièrement en tests.
- **Sprint 5b — les écrans** : S5-08 → S5-11, **29 points**, et se démontre en entier.

Une seconde coupure, plus honnête vis-à-vis de la Definition of Done : **5a plus un
seul client** (le téléphone, S5-08, **39 points**), puis la télévision et le web. Une
verticale complète sur une surface prouve plus qu'un socle sans écran.

---

## Hors périmètre

Explicitement, pour que la question ne se repose pas en cours de route :

- **Les séries.** [`sprint-06.md`](./sprint-06.md). Elles réutilisent tout ce sprint
  et ajoutent un arbre — c'est ce qui justifie de les séparer.
- **Mettre un film en favori.** `AddFavoriteRequest` exige un `channel_id`. Le manque
  est réel et tracé ici pour ne pas être découvert : il demande un aller au contrat
  (un `item_type` sur le favori, ou une opération séparée) et cette décision se prend
  quand les films et les séries existent tous les deux, pas entre les deux.
- **Corriger un classement à la main.** [`adr/0009`](../adr/0009-m3u-film-detection.md)
  règle 3 lui donne sa forme — une bascule **sur la catégorie**, jamais sur la
  source — et son prix : un champ nullable sur `Category`, une colonne, un `PATCH`,
  un contrôle sur les trois surfaces. **3 points.** Écarté de ce sprint, pas de la
  suite : c'est la sortie de secours de toute la classe des faux classements, et
  l'ADR décide sa forme précisément pour que rien d'ici ne la bloque.
- **Acteurs, réalisateur, bande-annonce, recommandations.** `get_vod_info` les sert
  parfois. Rien dans US-13 ne les demande, et chacun coûte un champ dans le contrat.
- **Le contrôle parental sur `is_adult`.** Le champ est ingéré et exposé ; le filtrer
  est un multi-profils déguisé, et le multi-profils est v2 (`AGENTS.md` §6).
- **Le téléchargement pour regarder hors ligne.** Ce serait faire transiter le média,
  et [`architecture.md`](../architecture.md) §1 l'interdit.

La dette assumée — client OAuth Google, webhook Stripe, recette des sprints 1 et 2 —
et les quatre règles qui l'encadrent sont dans [`dette.md`](./dette.md), et valent
pour ce sprint sans changement.

---

## Documents associés

[`sprint-05-recette.md`](./sprint-05-recette.md) et
[`sprint-05-demo.md`](./sprint-05-demo.md), une fois les tâches acceptées. La
recette de ce sprint a un prérequis de plus que celle du sprint 2 : le septième
chemin du banc d'essai livré en S5-06, sans lequel la moitié « volume » ne s'exécute
pas.
