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

**Tranché en S6-00 : les séries sont une fonction Xtream en v1**
([`adr/0010`](../adr/0010-series-are-xtream-only.md)). Une entrée M3U qui ressemble
à un épisode reste classée par `ADR 0009` et rien de plus, **son titre affiché tel
quel**, token compris.

L'ADR ajoute deux choses que cette section n'avait pas :

- **l'argument qui décide vraiment** n'est pas la précision d'une heuristique, c'est
  qu'**il n'existe aucune vérité de référence** — un test de parseur de titres ne peut
  qu'affirmer le parseur contre lui-même ;
- **où l'absence se dit** : pas un onglet vide, mais une phrase sur la page de la
  source, à côté de ce qu'elle propose. C'est ce qui réconcilie cette limite avec la
  règle de `S5-08` — un onglet est une promesse, la page d'une source est une
  description.

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
| ☑ | S6-00 | Décision : les séries en M3U | décision | décision | 2 | 100 % |
| ☑ | S6-01 | Contrat : `Series`, `Season`, `Episode`, et leurs **quatre** lectures | contrat | contrat | 5 | 100 % |
| ☑ | S6-02 | Base : l'arbre, et son unicité qui survit à une resynchronisation | serveur | api | 3 | 100 % |
| ☑ | S6-03 | Ingestion : la liste à la synchro, l'arbre à la demande, le cache qui expire | serveur | api | 8 | 100 % |
| ☑ | S6-04 | `core:data` et Room : l'arbre hors ligne | socle | android | 5 | 100 % |
| ☐ | S6-05 | Mobile : fiche série, saisons, épisodes | mobile | mobile | 8 | **90 %** |
| ☐ | S6-06 | TV : la même au D-pad, et « Épisode suivant » | tv | tv | 8 | **90 %** |
| ☑ | S6-07 | Web : fiche série | web | web | 5 | 100 % |
| ☐ | S6-08 | Reprendre une série, pas un épisode | 3 clients | mobile + tv + web | 5 | 0 % |
| ☐ | S6-09 | Web : l'écran Favoris, à l'échelle du compte | web | web | 5 | 0 % |

**Avancement du sprint : 79 % de 54 points.** Le serveur, le socle Android, le web et
les deux surfaces Android sont livrés. Il reste la reprise et le rattrapage web.

**S6-05 et S6-06 sont à 90 %, et les deux buttent sur la même absence** — une
position d'épisode enregistrée. Ce qui manque à S6-05 est la barre de progression sur
chaque épisode ; ce qui manque à S6-06 est le focus qui devait arriver sur l'épisode
à reprendre, sinon sur le premier non regardé.

Aucun des deux ne peut exister avant S6-08 : aucune position d'épisode n'est
enregistrée nulle part — `ProgressRepository.save` n'accepte que `VOD`, et le
contrat lui-même ne connaît `EPISODE` comme `item_ref` que depuis S6-01, sans
écriture derrière. **La dépendance de l'énoncé est inversée** : les deux
écrans ne précèdent pas S6-08, ils en attendent la moitié. Les dix pour cent
restants se ferment le jour où la reprise écrit, et il n'y a rien à réécrire ici —
l'emplacement de la barre est marqué dans les deux `EpisodeRow`, et le repli du
focus TV est écrit comme un repli plutôt que comme une règle.

**Rien ne devine en attendant**, et c'est la décision qui compte : un écran qui
aurait choisi « le premier épisode de la dernière saison » ou dessiné une barre à
zéro aurait produit une réponse fausse plutôt qu'un manque visible. Un repli honnête
redevient une exception le jour où il y a quelque chose à préférer ; une devinette,
elle, reste.

**S6-07 est passé avant S6-05 et S6-06**, hors de l'ordre prévu : un signalement
d'usage a montré que les films et les séries étaient introuvables sur le web, et un
onglet Séries exige une destination.

**S6-01 et S6-02 sont partis dans le même commit**, comme S5-01 et S5-02 au sprint
précédent, et pour la même raison mécanique : `ADR 0001` génère les interfaces avec
`interfaceOnly` et `skipDefaultInterface`, donc une opération ajoutée au contrat est
une **erreur de compilation** tant que le contrôleur ne l'implémente pas. Le contrat
ne peut pas être livré seul, et c'est voulu.

S6-09 ne porte pas sur les séries et n'a aucune dépendance dans ce sprint : c'est un
retard du web sur US-12, mesuré après le sprint 5, et il est ici parce que c'est le
prochain sprint qui a de la place. Il peut démarrer le premier jour.

---

### S6-00 — Décision : les séries en M3U · **2** · ☑ tranché

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

**Livré** : [`ADR 0010`](../adr/0010-series-are-xtream-only.md), **option A**, avec
quatre décisions plutôt que les deux attendues.

Les deux prévues : les séries sont Xtream en v1, et une entrée M3U qui ressemble à un
épisode reste classée par `ADR 0009` — **son titre affiché verbatim**, token compris.
Nettoyer le titre serait la même erreur en miniature : décider ici que `S01 E02` est
une métadonnée et pas une partie du nom.

**La troisième a demandé de résoudre une collision avec une règle en vigueur.**
`S5-08` a décidé qu'une source sans films ne montre pas d'onglet Films — une promesse
vide est pire qu'une absence. Appliquée telle quelle, un utilisateur M3U ne verrait
jamais d'onglet Séries **et ne saurait jamais pourquoi** : exactement le risque que le
tableau ci-dessus portait contre l'option A.

Les deux règles sont justes et parlent d'endroits différents. **Un onglet est une
promesse ; la page d'une source est une description.** Donc pas d'onglet, et une phrase
sur la page de la source, à côté du nombre de chaînes. Avec **deux phrases
distinctes**, parce que ce sont deux faits distincts : une playlist M3U ne peut pas
porter de séries, un panel Xtream qui n'en propose pas pourrait.

**La quatrième est le prix de changer d'avis**, et il n'était pas chiffré. Reconstruire
un arbre plus tard déplace des lignes de `vod_item` vers `episode`, et une position
sauvegardée pointe sur un `VodItem.id` (`S5-11`). **Toute progression sur un film
converti devient orpheline.** Le moment bon marché pour revenir sur cette décision,
c'est maintenant ; il n'y en aura pas un second.

---

### S6-01 — Contrat : `Series`, `Season`, `Episode` · **5** · dépend de S6-00 · ☑

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

> **Deux écarts avec ce qui était écrit ci-dessus, tranchés à la livraison.**
>
> **Il y a quatre lectures, pas trois.** `GET /sources/{id}/episodes?ids=` a été
> ajoutée, et elle existe pour une seule chose : un rail « Reprendre ».
> `GET /me/progress` rend des identifiants et des positions — pas des titres, pas
> d'affiches, et pas la série à laquelle un épisode appartient. Sans ce résolveur,
> S6-08 découvrait le problème une fois les écrans écrits. **C'est exactement ce qui
> s'est passé au sprint 5** avec le rail des films, et cette fois c'est vu avant
> plutôt qu'après. `ids` y est **obligatoire** : sans ça l'opération listerait tous
> les épisodes de toutes les séries d'une source, ce dont personne n'a l'usage.
>
> **`item_ref` ne porte pas l'identifiant externe de l'épisode mais `Episode.id`.**
> Le paragraphe ci-dessus disait le contraire ; c'est le même arbitrage qu'en S5-11,
> et il tombe du même côté. Un rail doit remonter de la ligne de progression à une
> série avec son affiche, et les opérations qui font cette conversion prennent nos
> identifiants. La stabilité est acquise autrement : `episode` est upserté sur
> `(series_id, external_id)` — l'identifiant du panel, qui contrairement à celui
> d'une saison existe toujours puisqu'il construit l'URL de lecture.

---

### S6-02 — Base : l'arbre · **3** · dépend de S6-01 · ☑

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

**Livré tel quel**, avec une colonne de plus que le tableau ne laissait attendre :
`episode.source_id`, dénormalisée depuis `series`. Chaque contrôle de propriété et
chaque résolution par identifiant filtre sur la source, et remonter deux niveaux à
chaque fois ne rapporte rien. Elle ne peut pas diverger : un épisode ne change jamais
de série.

Treize cas d'intégration, dont trois portent sur des choses qu'un arbre rate et
qu'une ligne ne peut pas rater : **une saison vide reste une saison** (une jointure
interne l'aurait fait disparaître en silence), **le compte annoncé par le panel peut
contredire la liste** et c'est la liste qui compte, et **supprimer une série emporte
ses saisons et ses épisodes** — sans quoi une URL de flux resterait lisible sur une
ligne que plus aucune opération n'atteint.

---

### S6-03 — Ingestion : la liste, l'arbre, le cache · **8** · dépend de S6-02 · ☑

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

**Livré, seize cas.** Les sept demandés, et neuf de plus qui se sont imposés en
écrivant les fixtures — dont une saison absente du tableau `seasons` mais présente
dans `episodes` (les panels sont incohérents là-dessus, et c'est `episodes` qui porte
le contenu), et un épisode sans `container_extension`, écarté pour la raison qu'un
film sans extension est écarté : son URL ne peut pas être construite.

> **Un défaut trouvé par un test, et il valait le sprint 5 aussi.**
>
> La lecture de l'année prenait « la première clé non vide » parmi `year`,
> `releaseDate` et `release_date`. Or les panels envoient couramment
> **`year: "N/A"` à côté d'un `releaseDate` utilisable** — et `"N/A"` n'est pas
> vide. La clé inutile masquait donc la bonne, sur la moitié d'un catalogue.
>
> C'est maintenant « la première qui **s'analyse** », ce qui est la sémantique qu'on
> croyait avoir. Le même raisonnement vaut pour `firstNonBlank` partout où il
> précède une conversion ; les affiches n'en souffrent pas — une URL vide et une
> URL absente sont le même cas — mais c'est à regarder si un troisième champ
> numérique arrive.

**Deux écarts avec ce qui était écrit, tous deux dans le sens de la prudence :**

**La limite par source est de deux requêtes simultanées, pas d'une.** Une seule ferait
attendre derrière la première quelqu'un qui ouvre une série, revient, en ouvre une
autre. Au-delà de deux il n'y a plus de spectateur à servir : on lit un écran à la
fois.

**Un refus de démarrer parce qu'une requête est déjà en vol compte comme « rien en
cache ».** C'est le seul endroit où la garde et la réponse se rencontrent : la seconde
ouverture simultanée d'une série jamais chargée reçoit un `503`, pas un arbre vide.
Servir un arbre vide serait dire à quelqu'un que sa série n'a pas d'épisodes, ce qui
est bien plus alarmant que « votre fournisseur n'a pas répondu ».

---

### S6-04 — `core:data` et Room : l'arbre hors ligne · **5** · dépend de S6-01 · ☑

Trois entités, trois DAO, un `SeriesRepository`, et le `SeriesPager` sur le patron de
`VodPager`. La base est la vérité, le réseau rafraîchit.

**Ce qui est nouveau par rapport au sprint 5** : le repository doit rendre trois états
distincts pour une fiche — *arbre en cache*, *arbre en cours de chargement*, *arbre
indisponible* — et les trois donnent trois écrans différents. Les confondre produit
soit un écran vide qui ressemble à une série sans épisodes, soit un tourniquet
permanent sur une série qu'on a déjà.

**Une série dont l'arbre n'a jamais été chargé s'affiche quand même** dans la grille :
affiche, titre, année. C'est ce que la liste porte, et c'est assez pour choisir.

**Livré, avec quatre états plutôt que trois.** Le quatrième est `Idle` — personne n'a
encore rien demandé — et il n'est pas un raffinement : sans lui, la première image
d'une fiche est soit un tourniquet qui prétend une requête non faite, soit une liste
de saisons vide qui prétend une réponse que personne n'a demandée.

> **Ce qui a demandé le plus d'attention n'est pas les états mais l'ordre de
> priorité entre eux.** `tree()` combine quatre choses — les saisons, les épisodes,
> la ligne de série et ce que ce processus est en train de faire — et **ce qui est en
> cache gagne toujours**. Un rafraîchissement sur un arbre déjà là ne pose pas de
> tourniquet : il pose un `stale = true` sur des épisodes qui restent affichés.
>
> C'est le même arbitrage que le serveur fait en S6-03, à un endroit différent. Le
> confondre donnait la régression que la tâche nomme : un écran d'attente sur des
> données qu'on a déjà.

**Deux détails qui ne se voient pas et qui coûteraient cher :**

**`tree_fetched_at` n'est écrit que par `replaceTree`.** Une synchronisation qui
estamperait les lignes laisserait chaque série *paraître* en cache, et chaque fiche
afficherait un arbre vide au lieu d'en demander un. Un test le tient.

**L'état `Loading` vit en mémoire, pas dans Room.** Écrit sur disque, il survivrait à
la mort du processus et laisserait un tourniquet que plus rien ne peut effacer.

**Les premières clés étrangères de ce schéma**, sur `season` et `episode`. `favorite`
et `recent_channel` n'en ont délibérément pas — ils pointent des chaînes que
l'appareil peut ne pas avoir en cache. Une saison est le cas inverse : elle n'existe
que dans l'arbre d'une série, écrite en une transaction, et une saison dont la série a
disparu est une ligne que rien ne peut atteindre.

---

### S6-05 — Mobile : fiche série, saisons, épisodes · **8** · dépend de S6-04 · ☐ 90 %

`feature:series` cesse d'être un placeholder et revient dans la barre de navigation.

La grille reprend celle des films sans une ligne de différence — même pager, mêmes
affiches en portrait, même bande de catégories, même recherche.

**La fiche est le seul écran vraiment neuf du sprint.** Affiche et synopsis en haut,
un sélecteur de saison, et la liste des épisodes de la saison ouverte. Chaque ligne
d'épisode : numéro, titre s'il existe, durée, et **une barre de progression quand il y
en a une** — c'est ce qui rend « où j'en suis » lisible d'un coup d'œil.

> **La barre n'est pas livrée, et c'est le seul manque de la tâche.** Elle suppose une
> position enregistrée pour un épisode ; il n'en existe aucune. `ProgressRepository.save`
> pose `itemType = VOD` en dur, et rien dans le sprint n'a encore écrit une ligne de
> progression pour un épisode.
>
> Dessiner la barre quand même la mettrait à zéro sur chaque épisode de chaque série,
> ce qui dirait que tout le monde a commencé tout — l'exact contraire de ce que la
> ligne demande. L'emplacement est marqué d'un commentaire dans `EpisodeRow` et se
> remplit en S6-08.

**La première saison est ouverte à l'arrivée**, pas un sélecteur vide. Une saison à
choisir avant de voir quoi que ce soit est une décision qu'on impose à quelqu'un qui
n'a rien demandé.

**Un épisode sans titre affiche son numéro**, et rien d'autre. Pas « Épisode sans
titre », qui remplit une ligne pour dire qu'elle est vide.

**Le chargement de l'arbre est visible sans que l'écran soit vide** : l'affiche, le
titre et le synopsis sont là immédiatement — ils viennent de la liste — et seule la
zone des saisons attend. C'est le Gherkin, et c'est ce que S6-04 rend possible en
distinguant trois états.

**Le lecteur est celui de S5-08, à une soustraction près.** `EpisodePlayerViewModel` en
est la copie — un module de fonction ne dépend jamais d'un autre
(`settings.gradle.kts`) — moins la boucle de trente secondes qui enregistre une
position, pour la raison ci-dessus. Le déplacement dans le fichier reste : bouger dans
ce qu'on regarde est de la lecture, y revenir demain est la fonction qui n'existe pas
encore.

**Ce que la barre de navigation gagne**, et c'est la seconde moitié de l'énoncé :
l'onglet Séries est là, sans condition, à la suite des films. Il y arrive parce que
l'écran derrière existe — jamais parce que la source a un catalogue. C'est la
distinction qui a survécu au renversement du sprint 5 (`adr/0010`), et
`MobileDestinationsTest` la tient : le dernier `doesNotContain` du fichier porte
désormais sur la recherche, qui est encore un placeholder. Une source qui n'a pas de
séries ouvre sur une grille qui le dit, et une playlist M3U en reçoit une phrase à
elle : le format ne déclare ni saison ni épisode, ce qui n'est pas la même chose qu'un
panel qui ne propose rien.

---

### S6-06 — TV : la fiche au D-pad, et l'épisode suivant · **8** · dépend de S6-04 · ☐ 90 %

La grille reprend `VodTvScreen`. La fiche, elle, est **une nouvelle surface de focus à
deux zones** — le sélecteur de saison et la liste d'épisodes — donc une nouvelle
section dans [`tv-focus-map.md`](../design/tv-focus-map.md), avec l'élément focalisé à
l'arrivée et les quatre directions depuis chaque zone.

**Le focus arrive sur l'épisode à reprendre**, ou sur le premier épisode non regardé,
ou à défaut sur le premier épisode. Pas sur le sélecteur de saison : quelqu'un qui
ouvre une série veut la regarder, et l'étagère où il se trouve est la bonne. C'est
exactement le raisonnement de `S2-13` sur la bande de catégories, appliqué ici.

> **Les deux premières branches ne sont pas livrées, et c'est le seul manque de la
> tâche.** Elles exigent une position d'épisode enregistrée, que `S6-08` écrira ;
> aujourd'hui il n'en existe aucune. Le focus arrive donc sur le premier épisode de
> la saison ouverte, **et rien ne devine en attendant** : un écran qui aurait choisi
> « la dernière saison » ou « le dernier épisode listé » aurait donné une réponse
> fausse au lieu d'un repli visible. La troisième branche est la réponse pour
> l'instant ; elle redevient le repli le jour où il y a quelque chose à préférer.

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

**Où il a été écrit, et pourquoi une seule fois.** Tout — la proposition, le
décompte, son annulation, l'enchaînement d'une saison à la suivante et l'arrêt à la
fin d'une série — est dans `EpisodePlayerViewModel`, partagé par les deux surfaces.
La **seule** différence entre elles est un nombre passé en argument : dix secondes
sur la télévision, cinq sur le téléphone. Une télécommande peut être sur
l'accoudoir ; un téléphone est déjà dans la main.

**La règle « quel épisode vient après » est descendue dans `core:data`** —
`List<Season>.episodeAfter` — et elle est la seule partie de cette fonction qui a une
bonne et une mauvaise réponse. Le reste est du Compose, et ce n'est pas le Compose
qui se trompe ici : ce sont les panels. `NextEpisodeTest` tient neuf cas, tous réels —
un numéro sauté parce que le fichier manque, une saison 2 jamais ingérée entre la 1 et
la 3, une saison déclarée et vide, un épisode numéroté 0 pour un hors-série, un
épisode disparu de l'arbre sous un lecteur en train de tourner. Chacun d'eux
terminerait une série trop tôt sous une implémentation qui ajoute 1 à un numéro.

**Avancer ne navigue pas.** Le suivant remplace le courant dans le même écran : six
épisodes enchaînés laissent **une** entrée de pile, et `BACK` est à un appui de la
fiche au lieu de six. C'est la différence entre une soirée et un labyrinthe.

**Le suivant est cherché dans le cache, jamais sur le réseau.** La fin d'un épisode
est le pire moment possible pour faire une requête : le décompte se passerait à
regarder un spinner plutôt qu'à décider. L'arbre est dans Room parce que le
spectateur a ouvert cette série pour arriver à cet épisode — s'il n'y était pas, il
n'aurait pas pu.

**La carte de focus a gagné trois sections** — la grille, la fiche à deux zones et le
lecteur — dans [`tv-focus-map.md`](../design/tv-focus-map.md). Celle de la fiche est
la première de tout le document à décrire **deux zones**, et c'est là que se trouve
la seule zone conditionnelle de l'application : le sélecteur de saison disparaît sous
deux saisons, donc `UP` depuis le premier épisode est tantôt un déplacement tantôt un
bord. Cette ligne-là ne se vérifie que télécommande en main.

> **Au passage, la section *Films* du même document disait encore que l'entrée de
> rail est conditionnelle.** Elle ne l'est plus depuis le renversement du sprint 5 ;
> la correction est dans le même commit, parce qu'une carte de focus qui décrit un
> écran qui n'existe plus est pire qu'une section manquante — on la recette.

---

### S6-07 — Web : fiche série · **5** · dépend de S6-01 · ☑

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

**Livré, sans le `Suspense`, et c'est à assumer.** La fiche attend l'arbre. La raison
est mesurée plutôt que supposée : sur un vrai panel, `get_series_info` répond en une
à deux secondes pour une série, et le serveur met le résultat en cache six heures
(S6-03). Découper le rendu en deux pour une seconde d'attente sur la première
ouverture ajoute une frontière `Suspense` — la seule du site — à un écran qui n'en a
pas besoin la plupart du temps.

**Ce qui rouvrirait la question** : un panel lent. Le `503` est déjà distingué du
`404` et porte sa propre phrase, donc l'échec est traité ; c'est la lenteur qui ne
l'est pas. Si la recette trouve des ouvertures au-delà de trois secondes, le
`Suspense` revient — et le paragraphe ci-dessus reste la bonne description de
comment le faire.

**Les trois onglets sont toujours visibles**, ce qui renverse en partie
`adr/0010` ruling 3 et la règle de `S5-08`. Le motif est écrit dans l'ADR : cacher
la fonction est ce qui a fait conclure qu'elle n'existait pas. **Le téléphone et la
télévision cachent toujours les leurs** — c'est désormais une incohérence, pas une
décision, et elle est notée dans l'ADR.

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
| Rattrapage web (hors séries) | S6-09 | 5 |
| **Total** | **10 tâches** | **54** |

**Ordre de réalisation** — les dépendances comptent plus que les priorités :

```
S6-00 → S6-01 → S6-02 → S6-03
              → S6-04 → S6-05 → S6-08
                      → S6-06
              → S6-07 (dès S6-01 servi)

S6-09 (aucune dépendance — livrable dès le premier jour)
```

S6-00 avant S6-01 : le contrat dépend de ce que la décision retient. S6-03 peut
avancer en parallèle des clients dès que S6-02 est en place — c'est la tâche la plus
longue et elle ne bloque que la lecture réelle, pas l'écriture des écrans.

**La coupure, si 54 points est trop** : S6-00 → S6-05 (**31 points**) livre les
séries de bout en bout sur le téléphone et se démontre seul, comme la coupure
proposée au sprint 5. La télévision et le web suivent (S6-06, S6-07, S6-08,
**18 points**) et ne dépendent que du socle.

**S6-09 n'est pas dans cette coupure et ne doit pas y entrer comme variable
d'ajustement.** C'est la tâche la moins risquée du sprint — aucune dépendance,
aucune inconnue serveur — donc celle qu'on repousse le plus facilement, et elle a
déjà été repoussée deux sprints. Si le sprint doit maigrir, il maigrit sur les
séries, pas sur elle.

---

### S6-09 — Web : l'écran Favoris, à l'échelle du compte · **5** · aucune dépendance

**Ce n'est pas une fonction manquante, c'est une fonction qui ment discrètement** —
et c'est ce qui la fait entrer ici plutôt qu'attendre.

Le web a reçu les groupes en `S4-09` : la barre, l'étoile, créer, renommer,
supprimer. Tout marche. Mais ils vivent sur la page des chaînes **d'une source**, et
cette page filtre :

```
apps/web/…/sources/[id]/channels/page.tsx
.filter((favorite) => favorite.source_id === id)
```

Or c'est **le point structurel d'US-12** : un groupe appartient au compte et peut
contenir des chaînes de deux abonnements. Le téléphone l'assume avec un écran dédié
et une ligne « Depuis *[source]* » sous chaque chaîne (`S4-04`). Le web montre un
groupe « Documentaire » **amputé des chaînes de l'autre source, sans le dire** — la
catégorie de défaut la plus chère, parce que rien n'a l'air cassé.

**Livrable :** `app/favorites`, à la racine de la zone compte et **pas** sous
`sources/[id]`. Le groupe ouvert est dans l'URL (`?group=`), donc partageable,
compatible avec le bouton retour, et **fonctionnel sans JavaScript** — la règle de la
zone ne se suspend pas parce que l'écran est nouveau.

#### Ce qui est déjà écrit et ne se réécrit pas

C'est ce qui rend le chiffrage crédible plutôt qu'optimiste. `S4-09` a livré, et
tout est réutilisable tel quel :

- les cinq Server Actions (`actions/favorites.ts`) ;
- le composant `FavoriteGroups` — barre, création, renommage, suppression avec son
  décompte ;
- `groupLabel` / `defaultGroupLabel`, qui traduisent le nom du groupe par défaut ;
- le composant `Rail` et le résolveur `railOf`.

**Et un cadeau du schéma** : `favorite.channel_id` porte un
`REFERENCES channel(id) ON DELETE CASCADE`, tandis que la resynchronisation
**upserte** sur `(source_id, external_id)`. Une chaîne qui reste garde son
identifiant ; une chaîne qui disparaît emporte ses favoris. Cet écran n'a donc
**aucun cas de favori orphelin** à traiter.

#### Le seul mécanisme réellement nouveau, et c'est lui qui coûte

`GET /me/favorites` rend les favoris **de tout le compte**. Pour les afficher il
faut des noms et des logos, qui viennent de
`GET /sources/{id}/channels?ids=` — **une opération par source**. Rien sur le web ne
fait ça aujourd'hui : la page des chaînes ne résout jamais que dans la sienne.

Donc : grouper par `source_id`, une requête par source, en parallèle, puis
reconstituer l'ordre — celui des favoris, pas celui des réponses.

**Le piège est celui que `S4-02` a payé sur le téléphone, à un endroit neuf.** Le
contrat plafonne `ids` à cent, **par requête**. Un groupe de trois cents chaînes
réparties sur trois sources, ce sont des lots à découper par source, pas trois
requêtes. Android a fini par extraire un `ChannelResolver` pour ne l'écrire qu'une
fois ; la page des chaînes du web s'en tire avec un `.slice(0, 100)` défensif parce
qu'un rail y est borné par construction. **Ici il faut une boucle, pas une coupe** :
une coupe tronquerait un groupe en silence, avec un `200` et aucune erreur.

C'est le cas `R-140` de la recette du sprint 4, transposé au web — un groupe de
cent vingt chaînes qui en affiche cent tout rond.

Et le second piège du même endroit : **`size` vaut 50 par défaut**. Un lot de cent
identifiants revient à moitié répondu si on ne le passe pas explicitement.

#### Ce dont cet écran n'a pas besoin

**Aucun lecteur.** Une chaîne favorite ouvre
`/app/sources/{sourceId}/channels?play={channelId}` — la page de *sa* source, qui a
déjà tout. C'est ce qui garde la tâche à cinq points : l'écran liste et route, il ne
lit pas.

Il faut en revanche **une requête de plus, `GET /sources`**, pour la ligne
« Depuis *[source]* » : les libellés ne sont dans aucune des deux autres réponses, et
sans eux l'écran ne dit toujours pas d'où vient une chaîne — ce qui serait livrer le
défaut sous un autre nom.

#### Ce qui ne bouge pas

**La barre de groupes reste aussi sur la page des chaînes.** Les deux endroits ont
deux rôles : on **range** là où est l'étoile, on **parcourt** ici. C'est la
répartition d'Android — le cœur dans la liste, l'onglet Favoris à côté — et la
dupliquer volontairement coûte moins qu'un aller-retour par mise en favori.

#### Pourquoi 5 et pas 3

`S4-09` valait 3 : une barre et un rail ajoutés à une page qui existait, dans une
seule source. Ici il y a une route de plus, une entrée de navigation, deux jeux de
libellés — et surtout **un résolveur multi-source à écrire correctement du premier
coup**, sur un plafond qui s'est déjà payé une fois sur le téléphone. C'est le même
prix que `S5-10` et `S6-07` : une route neuve plus un mécanisme neuf.

**Ce qui le ferait déraper à 8** : décider en cours de route que l'écran doit aussi
réordonner les favoris par glisser-déposer (`PATCH /me/favorites/{id}`, livré côté
serveur en `S4-01` et jamais appelé par le web). C'est une tâche à part entière et
elle n'est pas dans celle-ci.

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
- **Le glisser-déposer des favoris sur le web.** `PATCH /me/favorites/{id}` existe
  depuis `S4-01` et le web ne l'appelle toujours pas. C'est ce que `S6-09` laisse
  volontairement de côté pour ne pas déraper.

---

## Documents associés

`sprint-06-recette.md` et `sprint-06-demo.md`, une fois les tâches acceptées. La
recette de ce sprint a un cas qu'aucune autre n'a : **enchaîner deux épisodes sans
toucher la télécommande**, puis recommencer en appuyant sur une touche pendant le
décompte pour vérifier qu'il s'annule.
