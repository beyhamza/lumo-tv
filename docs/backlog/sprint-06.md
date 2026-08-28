# Backlog — Sprint 6

## Les séries

> Un utilisateur ouvre une série, choisit une saison, lance un épisode — et quand il
> revient, l'application sait lequel vient ensuite.

Ce sprint réutilise le précédent presque entièrement : le contrat, la table, les
écrans et le lecteur de films sont les mêmes objets à un niveau près. Ce qu'il ajoute
est **un arbre**, et c'est ce qui justifie de l'avoir séparé plutôt que de gonfler le
sprint 5 à cent points.

---

## Trois constats qui commandent l'ordre des tâches

### Une série n'est pas un film avec des épisodes

Un film est une ligne : un identifiant, une URL, une progression. Une série est trois
niveaux, et chacun se lit à un moment différent.

| Niveau | Ce qu'il porte | Quand il se charge |
|---|---|---|
| Série | affiche, synopsis, année, catégorie | à la synchronisation |
| Saison | numéro, nombre d'épisodes | à l'ouverture de la fiche |
| Épisode | numéro, titre, durée, **son URL** | à l'ouverture de la fiche |

**La progression est sur l'épisode, pas sur la série** — `ProgressItemType` vaut
`EPISODE`, et cette valeur attend depuis le lot `SRV-05` du sprint 2 que quelque
chose l'utilise. Mais **ce que l'utilisateur veut reprendre, c'est la série** : il ne
se souvient pas d'un identifiant d'épisode, il se souvient d'avoir regardé « jusqu'à
l'épisode 4 ». Traduire l'un en l'autre demande l'arbre, et l'arbre n'est pas toujours
chargé. C'est S6-08, et c'est le seul endroit du sprint où la mécanique n'est pas
évidente.

### `get_series_info` est un appel par série, et c'est le piège central

Le sprint 5 a posé la règle sur `get_vod_info` : une fiche ne s'ingère pas, elle se
charge quand quelqu'un l'ouvre. Ici c'est la même règle et l'enjeu est plus grand,
parce qu'un seul appel rend **tout l'arbre** d'une série — saisons et épisodes
compris.

Un panel courant propose huit cents séries. Les ingérer à la synchronisation, c'est
huit cents requêtes vers le serveur de l'utilisateur, à chaque resynchronisation
automatique. Ce n'est pas lent : c'est le genre de chose qui fait bannir notre adresse
IP par le fournisseur de notre propre client. Le `HostConcurrencyLimiter` protège
notre infrastructure, pas la sienne.

Donc : **`get_series` à la synchronisation** — la liste, plate, avec affiches et
catégories. **`get_series_info` à l'ouverture d'une fiche**, mis en cache.

**Et une différence avec le sprint 5 qui compte.** Le synopsis d'un film ne change
jamais : une fois en cache, il y reste. L'arbre d'une série **grossit** — une série en
cours gagne un épisode par semaine. Le cache a donc une durée de validité, là où celui
des films n'en avait pas besoin. Une série terminée en 2011 et une série diffusée ce
soir n'ont pas le même besoin, et rien dans les données ne les distingue : la durée
de validité est courte et uniforme, quelques heures, plutôt qu'intelligente et fausse.

### En M3U, une série n'existe pas — et il faut le dire

Xtream a des endpoints séparés pour les séries : la question ne se pose pas. Une
playlist M3U est une liste plate d'entrées, et rien dedans ne déclare une saison ni un
épisode. Ce qui existe, ce sont des **conventions de nommage** : `Nom S01 E02`,
`Nom - 1x02`, `Nom saison 1 épisode 2`, et vingt variantes par langue.

`ADR 0009` a déjà tranché sur les films en acceptant une heuristique et en écrivant
comment elle se trompe. Reconstruire un arbre à trois niveaux à partir de titres est
d'un autre ordre : une erreur ne range pas mal un élément, elle **fabrique une
structure fausse** — une saison 1 à trente épisodes parce que le séparateur n'a pas
été reconnu, deux séries distinctes fusionnées parce que leurs titres se ressemblent.

**Proposition, à confirmer en S6-00 : les séries sont une fonction Xtream en v1.** Une
entrée M3U qui ressemble à un épisode reste un film, comme aujourd'hui. C'est une
limite honnête, elle se dit en une phrase à l'utilisateur, et elle retire du sprint le
seul risque qu'on ne sait pas borner. Le contraire — un arbre inventé à partir de
titres — produit un écran dont personne ne peut dire s'il est juste.

---

## Ce qui existe déjà, et qu'on ne réécrit pas

Ce tableau est court parce que le sprint 5 vient de tout construire.

| Module | Ce qu'il fournit |
|---|---|
| `ingest/xtream` | `streamArray`, l'authentification, la construction d'URL, la variante `/movie/` de S5-03 |
| `apps/api` `catalog` | `GET /vod/{id}/playback` et son contrôle de propriété — l'épisode le copie |
| `apps/api` | Le chargement de fiche à la demande et son cache horodaté (S5-04) |
| `core:data` | `VodRepository`, `VodPager`, la gestion mémoire des affiches (S5-07) |
| `core:player` | La barre de progression déplaçable et le repli quand `Range` n'est pas servi (S5-08) |
| `userdata` | `PUT`/`GET /me/progress`, branchés sur les trois lecteurs depuis S5-11 |
| `feature:series` | **Le module existe**, avec ses deux écrans placeholders et sa navigation câblée |

`feature:series` a été sorti de la barre de navigation en S2-02, avec `vod` et
`search`. Le sprint 5 y a remis `vod` ; celui-ci y remet `series`.

---

## Definition of Ready

Celle du sprint 1, plus trois conditions propres à ce sprint :

- la tâche dit ce qu'elle fait quand l'arbre d'une série **n'est pas encore chargé**.
  C'est l'état normal au premier affichage, pas un cas d'erreur ;
- la tâche dit ce qu'elle fait d'une série dont une **saison est vide** ou dont les
  numéros d'épisodes ont des trous. Les deux sont courants ;
- la tâche dit ce qu'elle affiche quand un épisode n'a **pas de titre** — beaucoup de
  panels ne servent qu'un numéro.

## Definition of Done

Celle du sprint 1, sans allègement, plus :

- **la démo enchaîne deux épisodes** : lancer, avancer jusqu'à la fin, et voir le
  suivant démarrer. Un épisode qui se lance seul ne prouve rien de ce que ce sprint
  ajoute par rapport au sprint 5 ;
- **la reprise se démontre entre deux appareils**, comme au sprint 5, et sur la
  *série* — arrêté à l'épisode 4 sur le téléphone, la télévision propose l'épisode 4 ;
- télécommande en main pour la partie TV.

---

## Une story ajoutée — **US-15, retenue**

### US-15 — Suivre une série

> **En tant qu'** utilisateur, **je veux** parcourir les séries de ma source par
> saison et par épisode, **afin de** suivre une série sans chercher où j'en suis.

```gherkin
Scenario: Le catalogue de séries
  Given ma source est READY et propose des séries
  Then je vois mes séries par catégorie, avec leurs affiches
  And la liste se pagine de façon fluide

Scenario: Ouvrir une série
  When j'ouvre une série
  Then je vois son affiche, son synopsis, et la liste de ses saisons
  And la première saison est ouverte

Scenario: L'arbre n'est pas encore chargé
  Given je n'ai jamais ouvert cette série
  Then l'affiche et le titre s'affichent immédiatement
  And les saisons apparaissent dès qu'elles sont chargées
  And l'attente est visible sans que l'écran soit vide

Scenario: Lancer un épisode
  When je choisis un épisode
  Then il se lance en plein écran
  And je peux me déplacer dedans

Scenario: Épisode suivant
  Given un épisode arrive à sa fin
  Then l'épisode suivant m'est proposé
  And je peux refuser sans qu'il démarre

Scenario: Le dernier épisode d'une saison
  Given je termine le dernier épisode d'une saison qui en a une suivante
  Then le premier épisode de la saison suivante m'est proposé

Scenario: Le dernier épisode d'une série
  Then rien ne m'est proposé, et je reviens à la fiche

Scenario: Reprendre une série
  Given j'ai regardé la moitié de l'épisode 4
  When je rouvre la série
  Then l'épisode 4 est mis en avant, avec sa position
  And le rail "Reprendre" propose la série, pas la liste de ses épisodes

Scenario: Une série en M3U
  Given ma source est une playlist M3U
  Then ses épisodes apparaissent comme des films
  And rien ne me promet une organisation par saison
```

**Notes** — `ProgressItemType.EPISODE` existe depuis `SRV-05` et n'a jamais servi.
« Terminé » reste le seuil de 95 % défini en S5-11 ; c'est lui qui décide si l'épisode
suivant est celui d'après ou celui en cours.

**Points : 13**

---

## Tâches

Une case par tâche, un pourcentage dès que l'avancement est partiel. La checklist se
met à jour **dans le commit qui livre le travail**, pas après.

| | Id | Tâche | Lot | Cible | Points | Avancement |
|---|---|---|---|---|---|---|
| ☐ | S6-00 | Décision : les séries en M3U | décision | décision | 2 | 0 % |
| ☐ | S6-01 | Contrat : `Series`, `Season`, `Episode`, et leurs trois lectures | contrat | contrat | 5 | 0 % |
| ☐ | S6-02 | Base : l'arbre, et son unicité qui survit à une resynchronisation | serveur | api | 3 | 0 % |
| ☐ | S6-03 | Ingestion : la liste à la synchro, l'arbre à la demande, le cache qui expire | serveur | api | 8 | 0 % |
| ☐ | S6-04 | `core:data` et Room : l'arbre hors ligne | socle | android | 5 | 0 % |
| ☐ | S6-05 | Mobile : fiche série, saisons, épisodes | mobile | mobile | 8 | 0 % |
| ☐ | S6-06 | TV : la même au D-pad, et « Épisode suivant » | tv | tv | 8 | 0 % |
| ☐ | S6-07 | Web : fiche série | web | web | 5 | 0 % |
| ☐ | S6-08 | Reprendre une série, pas un épisode | 3 clients | mobile + tv + web | 5 | 0 % |

**Avancement du sprint : 0 % de 49 points.**

---

### S6-00 — Décision : les séries en M3U · **2**

Le pendant d'`ADR 0009`, et la seule inconnue du sprint — donc en premier.

Deux options, et la recommandation est écrite dans le troisième constat.

| Option | Ce qu'elle coûte | Ce qu'elle risque |
|---|---|---|
| **A — Les séries sont Xtream uniquement** | Une phrase à l'utilisateur, un `EmptyState` honnête | Un utilisateur M3U ne voit jamais l'onglet Séries |
| B — Reconstruire l'arbre depuis les titres | Un parseur de conventions, par langue, et sa maintenance | Une structure fausse qu'aucun test ne peut invalider, parce qu'il n'existe aucune vérité de référence |

**Option A recommandée.** Ce n'est pas de la prudence : c'est que B produit un écran
dont personne — ni nous, ni l'utilisateur — ne peut dire s'il est juste. Un catalogue
mal rangé se corrige à l'œil ; un arbre inventé se croit.

L'ADR dit aussi **ce qui rouvrirait la question** : une bascule par source, réglée par
l'utilisateur, qui déclare « ce groupe contient des épisodes » et accepte une seule
convention de nommage. Ce n'est pas ce sprint, et c'est écrit pour que la question ne
se repose pas en cours de route.

**Livrable** : `ADR 0010`, et la phrase que les trois clients affichent à un
utilisateur M3U qui cherche ses séries.

---

### S6-01 — Contrat : `Series`, `Season`, `Episode` · **5** · dépend de S6-00

Trois schémas, trois lectures, et la moitié est déjà écrite au sprint 5.

**`Series`** est un `VodItem` à deux champs près : pas de `container_extension` — une
série ne se joue pas — et un `episode_run_time` indicatif. Le reste est identique, y
compris `poster_url` nullable et `plot` chargé à la demande.

**`Season`** — `season_number`, `episode_count`, `poster_url` nullable. Rien d'autre :
une saison est un intercalaire, pas un objet que quelqu'un ouvre pour lui-même.

**`Episode`** — `season_number`, `episode_number`, `name` nullable,
`duration_seconds` nullable, `external_id`. Et `container_extension`, **stocké et
jamais exposé**, exactement comme sur `VodItem` : il sert à construire l'URL, et l'URL
ne sort que de l'endpoint dédié.

Trois lectures :

| Opération | Ce qu'elle rend |
|---|---|
| `GET /sources/{id}/series` | Paginé, `categoryId`, `q`, `ids` — la forme des deux autres catalogues |
| `GET /series/{id}` | L'arbre complet : saisons et épisodes. **C'est l'appel qui déclenche `get_series_info`** |
| `GET /episodes/{id}/playback` | Le jumeau de `GET /vod/{id}/playback` |

**`GET /series/{id}` est la seule opération du produit qui peut être lente au premier
appel**, parce qu'elle va chercher chez le fournisseur. Le contrat doit le dire, et
dire ce qui se passe quand le panel ne répond pas : `503` avec un code qui distingue
« le panel est injoignable » de « cette série n'existe pas ». Sans cette distinction,
l'écran affiche « série introuvable » sur une panne réseau.

**`SyncStep` gagne `PARSING_SERIES`**, après `PARSING_VOD`. L'énumération exige des
phases réelles : récupérer la liste des séries en est une, et sur un gros panel elle
n'est pas instantanée. Le sprint 5 a ajouté `PARSING_VOD` sur le même raisonnement.

**`ProgressItemType.EPISODE` n'a rien à changer** — elle attend depuis le sprint 2.
`item_ref` porte l'identifiant externe de l'épisode, et `source_id` l'accompagne
depuis la décision prise en S5-01.

---

### S6-02 — Base : l'arbre · **3** · dépend de S6-01

Trois tables : `series`, `season`, `episode`, en cascade depuis `source`.

**L'unicité est ce qui compte, et elle n'est pas au même endroit à chaque niveau :**

| Table | Clé d'upsert |
|---|---|
| `series` | `(source_id, external_id)` — comme `channel` et `vod_item` |
| `season` | `(series_id, season_number)` — un panel ne donne pas toujours d'identifiant de saison |
| `episode` | `(series_id, external_id)` — l'identifiant d'épisode, lui, existe toujours : c'est ce qui construit l'URL |

C'est cette unicité qui fait de la resynchronisation un upsert et qui permet à une
progression de continuer à pointer un épisode réel après le passage hebdomadaire.

**Deux colonnes de cache sur `series`** : `tree_fetched_at` et rien d'autre. Un arbre
absent et un arbre périmé se distinguent par une comparaison de date, pas par un
booléen qu'il faudrait remettre à zéro quelque part.

Index trigram sur `series.name`, comme sur les deux autres catalogues.

---

### S6-03 — Ingestion : la liste, l'arbre, le cache · **8** · dépend de S6-02

La tâche la plus longue du sprint, et elle contient la seule décision qui peut coûter
un client.

**À la synchronisation** : `get_series_categories` et `get_series`, en flux, sur le
patron de S5-03. Le plafond de volume et le délai de S5-06 s'appliquent, et
l'ingestion des séries reste **hors de la transaction** des chaînes : un catalogue de
séries en échec ne fait pas échouer une source dont les chaînes sont bonnes.

**À l'ouverture d'une fiche** : `get_series_info` sur cette série, et sur elle seule.
Écriture de l'arbre, `tree_fetched_at` posé, réponse.

**Le cache expire, et c'est la différence avec les films.** Une série en cours gagne un
épisode par semaine ; un synopsis de film ne change jamais. Durée de validité courte
et uniforme — quelques heures — plutôt qu'une règle intelligente qui prétendrait
distinguer une série terminée d'une série en cours à partir de données qui ne le
disent pas.

**Un arbre périmé s'affiche pendant qu'il se rafraîchit.** L'utilisateur voit les
épisodes qu'il connaît, et le nouveau apparaît quand la réponse arrive. L'inverse — un
écran d'attente sur des données qu'on a déjà — est une régression pour une fonction
censée être un confort.

**Deux protections, et ce sont elles qui empêchent le bannissement :**

- **une seule requête en vol par série**, même si trois écrans l'ouvrent en même temps
  sur trois appareils. Sans cette garde, une fiche ouverte trois fois est trois appels
  au panel ;
- **une limite par source**, pas seulement par hôte. Quelqu'un qui parcourt vingt
  fiches en une minute ne doit pas produire vingt requêtes simultanées vers son
  fournisseur.

**Tests** : arbre absent, arbre périmé, panel injoignable sur un arbre déjà en cache
(on sert le cache), panel injoignable sur un arbre jamais chargé (`503`, code
distinct), saison vide, numéros d'épisodes à trous, deux ouvertures simultanées de la
même série.

---

### S6-04 — `core:data` et Room : l'arbre hors ligne · **5** · dépend de S6-01

Trois entités, trois DAO, un `SeriesRepository`, et le `SeriesPager` sur le patron de
`VodPager`. La base est la vérité, le réseau rafraîchit.

**Ce qui est nouveau par rapport au sprint 5** : le repository doit rendre trois états
distincts pour une fiche — *arbre en cache*, *arbre en cours de chargement*, *arbre
indisponible* — et les trois donnent trois écrans différents. Les confondre produit
soit un écran vide qui ressemble à une série sans épisodes, soit un tourniquet
permanent sur une série qu'on a déjà.

**Une série dont l'arbre n'a jamais été chargé s'affiche quand même** dans la grille :
affiche, titre, année. C'est ce que la liste porte, et c'est assez pour choisir.

---

### S6-05 — Mobile : fiche série, saisons, épisodes · **8** · dépend de S6-04

`feature:series` cesse d'être un placeholder et revient dans la barre de navigation.

La grille reprend celle des films sans une ligne de différence — même pager, mêmes
affiches en portrait, même bande de catégories, même recherche.

**La fiche est le seul écran vraiment neuf du sprint.** Affiche et synopsis en haut,
un sélecteur de saison, et la liste des épisodes de la saison ouverte. Chaque ligne
d'épisode : numéro, titre s'il existe, durée, et **une barre de progression quand il y
en a une** — c'est ce qui rend « où j'en suis » lisible d'un coup d'œil.

**La première saison est ouverte à l'arrivée**, pas un sélecteur vide. Une saison à
choisir avant de voir quoi que ce soit est une décision qu'on impose à quelqu'un qui
n'a rien demandé.

**Un épisode sans titre affiche son numéro**, et rien d'autre. Pas « Épisode sans
titre », qui remplit une ligne pour dire qu'elle est vide.

**Le chargement de l'arbre est visible sans que l'écran soit vide** : l'affiche, le
titre et le synopsis sont là immédiatement — ils viennent de la liste — et seule la
zone des saisons attend. C'est le Gherkin, et c'est ce que S6-04 rend possible en
distinguant trois états.

Le lecteur est celui de S5-08, sans modification.

---

### S6-06 — TV : la fiche au D-pad, et l'épisode suivant · **8** · dépend de S6-04

La grille reprend `VodTvScreen`. La fiche, elle, est **une nouvelle surface de focus à
deux zones** — le sélecteur de saison et la liste d'épisodes — donc une nouvelle
section dans [`tv-focus-map.md`](../design/tv-focus-map.md), avec l'élément focalisé à
l'arrivée et les quatre directions depuis chaque zone.

**Le focus arrive sur l'épisode à reprendre**, ou sur le premier épisode non regardé,
ou à défaut sur le premier épisode. Pas sur le sélecteur de saison : quelqu'un qui
ouvre une série veut la regarder, et l'étagère où il se trouve est la bonne. C'est
exactement le raisonnement de `S2-13` sur la bande de catégories, appliqué ici.

**« Épisode suivant » est ce qui fait une application de séries**, et c'est le seul
endroit du sprint où le comportement par défaut se discute :

- à la fin d'un épisode, une carte propose le suivant avec un **décompte de dix
  secondes** ;
- `OK` le lance tout de suite, `BACK` annule et revient à la fiche ;
- **le décompte s'annule dès qu'on touche la télécommande.** Quelqu'un qui appuie sur
  une touche est quelqu'un qui regarde, et lancer un épisode sous son doigt est le
  genre de chose qu'on ne pardonne pas ;
- le dernier épisode d'une saison enchaîne sur la saison suivante ; le dernier épisode
  d'une série ne propose rien et revient à la fiche.

Ce comportement vaut aussi sur le téléphone, avec un décompte plus court. Il est écrit
ici parce que c'est sur une télévision qu'il compte, et qu'il se recette télécommande
en main.

---

### S6-07 — Web : fiche série · **5** · dépend de S6-01

`app/sources/[id]/series` et `app/sources/[id]/series/[seriesId]`, sur le modèle des
films. La saison ouverte est dans l'URL — `?season=2` — donc partageable, compatible
avec le bouton retour, et **la fiche fonctionne sans JavaScript**.

`/api/playback/episode/[id]` reprend le Route Handler de S3-09.

**La limite d'[`ADR 0007`](../adr/0007-web-playback-direct-only.md) s'applique sans
changement**, et les messages d'échec sont ceux de S3-11. Un épisode est un fichier
progressif comme un film : `<video src>`, pas `hls.js`, et les mêmes conditions sur
`Range` et CORS pour le déplacement.

**Le premier chargement d'une fiche peut être lent** — c'est `GET /series/{id}` qui va
chercher chez le fournisseur. Sur une page rendue côté serveur, cela veut dire une
réponse qui tarde. La fiche se rend en deux temps : ce que la liste porte
immédiatement, l'arbre en `Suspense`. C'est la seule zone du site où ce découpage est
justifié, et la tâche doit dire pourquoi pour que personne ne le généralise.

---

### S6-08 — Reprendre une série, pas un épisode · **5** · dépend de S6-05, S6-06, S6-07

Le seul endroit du sprint où la mécanique n'est pas évidente, et il tient en une
phrase : **la progression est sur l'épisode, la reprise se pense en série.**

`GET /me/progress?itemType=EPISODE` rend les épisodes commencés, le plus récent
d'abord. Pour en faire une carte « Reprendre », il faut remonter de l'épisode à sa
série — ce que le client sait faire **si l'arbre est en cache**, et pas autrement.

D'où la règle, et elle est assumée : **le rail « Reprendre » ne montre que les séries
dont l'arbre est connu de cet appareil.** C'est vrai de toute série qu'on a ouverte au
moins une fois sur cet appareil, donc de toute série qu'on a commencé à regarder.
L'exception — reprendre sur une télévision une série commencée sur le téléphone — se
résout en chargeant l'arbre à l'ouverture de la fiche, ce qui est le chemin normal.

**Ce que la carte propose** dépend d'un seul seuil, celui de S5-11 :

| État de l'épisode en cours | Ce qui est proposé |
|---|---|
| Commencé, sous 95 % | Le reprendre, à sa position |
| Au-delà de 95 % | L'épisode suivant, au début |
| Dernier épisode terminé | La série sort du rail |

**Une seule carte par série**, jamais une par épisode. Un rail qui montre trois
épisodes de la même série a compris la donnée et pas l'usage.

---

## Récapitulatif

| Bloc | Tâches | Points |
|---|---|---|
| Décision et contrat | S6-00, S6-01 | 7 |
| Serveur et ingestion | S6-02, S6-03 | 11 |
| Socle Android | S6-04 | 5 |
| Clients | S6-05 → S6-07 | 21 |
| Reprise | S6-08 | 5 |
| **Total** | **9 tâches** | **49** |

**Ordre de réalisation** — les dépendances comptent plus que les priorités :

```
S6-00 → S6-01 → S6-02 → S6-03
              → S6-04 → S6-05 → S6-08
                      → S6-06
              → S6-07 (dès S6-01 servi)
```

S6-00 avant S6-01 : le contrat dépend de ce que la décision retient. S6-03 peut
avancer en parallèle des clients dès que S6-02 est en place — c'est la tâche la plus
longue et elle ne bloque que la lecture réelle, pas l'écriture des écrans.

**La coupure, si 49 points est trop** : S6-00 → S6-05 (**31 points**) livre les
séries de bout en bout sur le téléphone et se démontre seul, comme la coupure
proposée au sprint 5. La télévision et le web suivent (S6-06, S6-07, S6-08,
**18 points**) et ne dépendent que du socle.

---

## Hors périmètre

Explicitement, pour que la question ne se repose pas en cours de route :

- **Les séries reconstruites depuis un M3U.** Décidé contre en S6-00, avec ce qui
  rouvrirait la question.
- **Mettre une série en favori.** Le manque est le même que pour les films et il est
  tracé dans [`sprint-05.md`](./sprint-05.md) : `AddFavoriteRequest` exige un
  `channel_id`. **La décision se prend maintenant que les deux existent** — c'est ce
  que le sprint 5 annonçait, et c'est le premier candidat du sprint suivant.
- **Marquer un épisode comme vu sans le regarder.** Demande un état qui n'est pas une
  progression, donc une table et une opération de plus.
- **Les sous-titres et le choix de piste.** `api-gaps.md` a déjà tranché : ce sont les
  pistes du manifeste, Media3 les expose nativement, et les faire passer par l'API
  demanderait de télécharger le flux — ce qu'`architecture.md` §1 interdit.
- **Le contrôle parental, le multi-profils.** v2, `AGENTS.md` §6.

La dette assumée — client OAuth Google, webhook Stripe, recette des sprints 1 et 2 —
et les quatre règles qui l'encadrent sont dans [`dette.md`](./dette.md), et valent
pour ce sprint sans changement.

---

## Et après

Trois chantiers restent, et ils sont nommés ici pour que la fin de ce sprint ne soit
pas une page blanche :

- **L'EPG.** Le serveur le fait déjà — `XmltvStreamParser`, la table `epg_programme`,
  `GET /channels/{id}/epg` répondent depuis le sprint 1. Il ne manque que les écrans :
  « En ce moment / Ensuite » sur la télévision, le guide sur le téléphone, la grille
  horaire sur le web. C'est le sprint le moins cher qui reste, et il ne bloque rien.
- **La dette technique**, dans l'ordre où elle fait mal : le client OAuth Google, puis
  le webhook Stripe, puis la recette avec un rapport de session. Chacune a son état
  réel et ce qui la rouvre dans [`dette.md`](./dette.md).
- **Les favoris de films et de séries**, décision annoncée ci-dessus.

---

## Documents associés

`sprint-06-recette.md` et `sprint-06-demo.md`, une fois les tâches acceptées. La
recette de ce sprint a un cas qu'aucune autre n'a : **enchaîner deux épisodes sans
toucher la télécommande**, puis recommencer en appuyant sur une touche pendant le
décompte pour vérifier qu'il s'annule.
