# Backlog — Sprint 4

## Organiser son catalogue : favoris et groupes

> Un utilisateur range ses chaînes dans ses propres groupes — « Documentaire »,
> « Ciné » — et les retrouve sur son téléphone, sa télévision et sur `lumo.tv`.

C'est le premier sprint qui n'ajoute ni une surface ni un protocole : tout est déjà
servi, ou presque. Il est placé avant les films et les séries pour cette raison —
**trois fois moins cher, et visible dès le premier écran livré.**

---

## Trois constats qui commandent l'ordre des tâches

### Le serveur sait déjà presque tout faire

Le lot `SRV-05` du sprint 2 a livré les favoris, les groupes et la progression, et
`ids` sur `GET /sources/{id}/channels` est arrivé en S3-08. Ce qui existe :

| Opération | Ce qu'elle fait |
|---|---|
| `GET /me/favorites` | Tous les favoris, ou ceux d'un groupe (`?groupId=`) |
| `POST /me/favorites` | Ajoute une chaîne à un groupe ; `group_id` omis ⇒ groupe par défaut |
| `DELETE /me/favorites/{id}` | Retire un favori |
| `GET`/`POST /me/favorite-groups` | Liste et crée les groupes |
| `GET /sources/{id}/channels?ids=` | Résout jusqu'à 100 identifiants en chaînes lisibles |

Trois propriétés viennent gratuitement avec ce modèle, et ce sont exactement celles
que la demande exige : **une chaîne peut être dans plusieurs groupes** (le `409` est
sur le couple chaîne + groupe, pas sur la chaîne) ; **un groupe est sur le compte,
pas sur la source**, donc il mélange des chaînes de plusieurs abonnements ; et
**les favoris survivent à une resynchronisation**, parce que l'ingestion est un
upsert sur `external_id` et que les identifiants ne bougent pas.

Deux manques réels, et ils se voient à la première minute d'usage :

| Manque | Ce que ça donne à l'écran |
|---|---|
| Aucun `PATCH`/`DELETE /me/favorite-groups/{id}` | Un groupe créé avec une faute de frappe est là pour toujours |
| Aucun `PATCH /me/favorites/{id}` | `position` s'accepte à la création et plus jamais ; on ne déplace pas un favori d'un groupe à l'autre |

**Ce sprint touche donc le contrat, et c'est assumé** — trois opérations, un champ
nouveau, aucune rupture. La règle d'`AGENTS.md` §3 est respectée dans l'ordre qu'elle
impose : le contrat d'abord (S4-00), le serveur ensuite (S4-01), les écrans après.

### Un groupe n'est pas une catégorie, et c'est ce qui le rend utile

La confusion est facile — les deux se ressemblent à l'écran — et elle coûterait cher.

| | Catégorie | Groupe de favoris |
|---|---|---|
| Appartient à | une source | le compte |
| Nommée par | le fournisseur | l'utilisateur |
| Survit à une resynchro | son `external_id` oui, sa `position` non | oui, entièrement |
| Peut mélanger deux sources | non | **oui** |

**La conséquence est une conséquence de navigation, et c'est le seul changement
structurel du sprint.** Aujourd'hui tout le catalogue est sous une source :
`app/sources/[id]/channels` sur le web, `LiveMobileScreen` derrière une source
`READY` sur Android. Un écran Favoris n'a pas de source à mettre dans son URL, parce
que ses chaînes peuvent venir de deux. Il se range **à côté** du catalogue, pas
dedans.

### Sur la télévision, un groupe est une puce — pas un rail

`S2-13` a tranché contre les rails, et le raisonnement est écrit dans
[`LiveTvScreen`](../../apps/android/feature/live/src/main/kotlin/tv/lumo/android/feature/live/LiveTvScreen.kt) :
*un rail plafonne ce qu'il contient, et un rail dont la huit-centième chaîne est
inatteignable est un défaut*. L'écran est une bande de catégories en haut et une
grille paginée en dessous.

Un groupe se comporte **exactement** comme une catégorie du point de vue de cet
écran : il filtre la grille. Il devient donc une puce de plus dans la bande
existante, devant les catégories de la source. Zéro nouvelle zone de focus, zéro
ligne à ajouter à la carte du parcours — c'est le bénéfice de ne pas avoir cédé aux
rails au sprint 2.

**Une exception, et une seule : les chaînes récemment regardées.**
[`design/api-gaps.md`](../design/api-gaps.md) M5 décrit un *rail*, premier écran de
la télévision. L'écran livré n'en a pas. La divergence est réelle et se tranche dans
S4-08, pas dans un commentaire de code.

---

## Ce qui existe déjà, et qu'on ne réécrit pas

| Module | Ce qu'il fournit |
|---|---|
| `apps/api` `userdata` | Les favoris, les groupes et la progression, avec les tables `favorite` et `favorite_group` et leurs positions |
| `core:data` | `CatalogRepository`, la traduction des erreurs typées, le patron à copier |
| `core:database` | `LumoDatabase`, `CategoryDao`, `ChannelDao`, `CataloguePager` |
| `core:designsystem` | `TvFocus`, la bande de puces de `LiveTvScreen`, les thèmes des deux surfaces |
| `apps/web` | [`actions/favorites.ts`](../../apps/web/src/actions/favorites.ts) — l'étoile en Server Action, testée sans JavaScript |
| `apps/web` | Les deux rails du haut de catalogue et la requête unique qui les remplit (S3-08) |

Le web sait **ajouter**. Il ne sait pas **organiser** : aucun écran ne crée, ne
renomme ni ne supprime un groupe, et `group_id` n'est délibérément pas envoyé tant
que la décision 2 d'`api-gaps` n'est pas prise. C'est S4-00 qui la prend.

---

## Definition of Ready

Celle du sprint 1, plus deux conditions propres à ce sprint :

- la tâche dit ce qu'elle fait **hors ligne**. Un écran de favoris qui a besoin du
  réseau pour afficher une liste que l'utilisateur a lui-même écrite est un défaut,
  pas une limite ;
- la tâche dit ce qu'elle fait quand un favori pointe une chaîne **disparue** de la
  source depuis la dernière synchronisation. Ce cas n'est pas rare, il est normal.

## Definition of Done

Celle du sprint 1, sans allègement, plus :

- **la démo se joue sur les trois surfaces avec les mêmes groupes.** Créer
  « Documentaire » sur le téléphone et le voir apparaître sur la télévision est la
  seule preuve que le groupe est sur le compte et pas sur l'appareil. Une démo faite
  sur une seule surface ne prouve rien de ce que ce sprint apporte ;
- télécommande en main pour la partie TV, comme toujours.

---

## Une story ajoutée — **US-12, retenue**

Aucune story du sprint 1 ne couvre l'organisation du catalogue : US-08 parle des
catégories du fournisseur, pas de celles de l'utilisateur. Les favoris n'existaient
que comme un lot serveur livré hors périmètre (`SRV-05`) et un écran web (S3-08).

### US-12 — Ranger mes chaînes dans mes propres groupes

> **En tant qu'** utilisateur, **je veux** créer mes propres groupes de chaînes,
> **afin de** retrouver ce que je regarde sans parcourir les catégories de mon
> fournisseur.

```gherkin
Scenario: Créer un groupe et y ranger une chaîne
  Given je parcours mes chaînes
  When je mets une chaîne en favori et que je choisis "Nouveau groupe"
  And je le nomme "Documentaire"
  Then la chaîne est dans "Documentaire"
  And le groupe apparaît dans la liste de mes groupes

Scenario: Une même chaîne dans deux groupes
  Given une chaîne est déjà dans "Documentaire"
  When je l'ajoute à "Ciné"
  Then elle est dans les deux groupes
  And la retirer de l'un ne la retire pas de l'autre

Scenario: Un groupe mélange les sources
  Given j'ai deux sources enregistrées
  When j'ajoute une chaîne de chacune à "Documentaire"
  Then le groupe affiche les deux
  And rien ne me demande de choisir une source pour l'ouvrir

Scenario: Les groupes suivent le compte
  Given j'ai créé "Documentaire" sur mon téléphone
  When j'ouvre l'application sur ma télévision
  Then "Documentaire" y est, avec les mêmes chaînes

Scenario: Renommer et supprimer
  When je renomme "Ciné" en "Cinéma"
  Then le nom change partout
  When je supprime un groupe
  Then je suis prévenu de ce qui va disparaître
  And aucune chaîne de mon catalogue n'est perdue

Scenario: Hors ligne
  Given j'ai déjà ouvert mes favoris et je n'ai plus de réseau
  Then je vois mes groupes et leurs chaînes depuis le cache local
  And une modification faite hors ligne est refusée explicitement,
    pas silencieusement perdue

Scenario: Un favori dont la chaîne a disparu
  Given une chaîne mise en favori n'est plus dans la source après resynchronisation
  Then le groupe s'affiche sans elle
  And rien ne casse, et aucune erreur ne s'affiche
```

**Notes** — Les groupes sont sur le compte, jamais sur l'appareil. Le nombre de
groupes n'est plafonné par rien aujourd'hui ; le jour où il le sera, ce sera par
`GET /me/entitlement`, jamais par une constante dans un client (CLAUDE.md, règle 3).

**Points : 8**

---

## Tâches

Une case par tâche, un pourcentage dès que l'avancement est partiel. La checklist se
met à jour **dans le commit qui livre le travail**, pas après.

| | Id | Tâche | Lot | Cible | Points | Avancement |
|---|---|---|---|---|---|---|
| ☑ | S4-00 | Contrat : renommer, supprimer, déplacer — et le nom du groupe par défaut | contrat | contrat | 3 | 100 % |
| ☑ | S4-01 | `userdata` : les trois opérations, et ce que devient un groupe supprimé | serveur | api | 3 | 100 % |
| ☑ | S4-02 | `core:data` et Room : favoris et groupes lisibles hors ligne | socle | android | 5 | 100 % |
| ☑ | S4-03 | Mobile : mettre en favori, choisir le groupe, en créer un à la volée | mobile | mobile | 5 | 100 % |
| ☐ | S4-04 | Mobile : l'écran Favoris, un onglet par groupe | mobile | mobile | 5 | 0 % |
| ☐ | S4-05 | Mobile : organiser — renommer, supprimer, déplacer, réordonner | mobile | mobile | 3 | 0 % |
| ☐ | S4-06 | TV : les groupes dans la bande de puces | tv | tv | 5 | 0 % |
| ☐ | S4-07 | TV : mettre en favori sans quitter la grille | tv | tv | 3 | 0 % |
| ☐ | S4-08 | TV : les chaînes récemment regardées, et la divergence M5 tranchée | tv | tv | 3 | 0 % |
| ☐ | S4-09 | Web : organiser ses groupes | web | web | 3 | 0 % |

**Avancement du sprint : 42 % de 38 points.** Le contrat porte les trois opérations
qui manquaient, le serveur les sert, Android sait lire et écrire des favoris, et
**le premier geste est en place** : un cœur sur chaque chaîne du téléphone.

---

### S4-00 — Contrat : renommer, supprimer, déplacer · **3** · ☑

> **Livré.** `PATCH`/`DELETE /me/favorite-groups/{id}`, `PATCH /me/favorites/{id}`,
> `FavoriteGroup.is_default`, et `FAVORITE_GROUP_NOT_DELETABLE` dans `ErrorCode`.
> Les trois clients régénérés, la vérification de non-dérive verte, et aucun
> avertissement `redocly` ajouté — onze avant, onze après.

Trois opérations, et une décision qui traîne depuis le sprint 2.

**`PATCH /me/favorite-groups/{id}`** — `name` et `position`. `409
FAVORITE_GROUP_ALREADY_EXISTS` si le nom est pris, le même code qu'à la création.

**`DELETE /me/favorite-groups/{id}`** — et la question qui compte : **que deviennent
ses favoris ?** Trois réponses possibles, une seule est bonne.

| Option | Verdict |
|---|---|
| Supprimer les favoris avec le groupe | Non. « Supprimer une étagère » et « jeter les livres » ne sont pas la même action, et rien à l'écran ne les distingue |
| Les déplacer vers le groupe par défaut | **Retenu.** L'utilisateur perd son classement, pas ses chaînes |
| Refuser tant que le groupe n'est pas vide | Non. Vider un groupe pour le supprimer est une corvée, pas une sécurité |

Le groupe par défaut ne se supprime pas : `409 FAVORITE_GROUP_NOT_DELETABLE`. Sinon
la suppression d'un groupe déplacerait des favoris vers un groupe qui vient de
disparaître.

**`PATCH /me/favorites/{id}`** — `group_id` et `position`. C'est ce qui permet de
déplacer un favori sans le retirer puis le remettre — enchaînement qui perd sa
position et, sur une connexion coupée entre les deux appels, perd le favori.

**Et la décision 2 d'[`api-gaps.md`](../design/api-gaps.md).** Le groupe par défaut
est créé au premier ajout, le serveur le nomme `Favorites`, en anglais, et aucun
client ne peut le traduire faute d'identifiant stable. Le même problème a été résolu
une fois pour les entrées M3U sans `group-title`, par une sentinelle. La réponse est
la même en esprit : **ajouter `is_default: boolean` à `FavoriteGroup`.** Un booléen
plutôt qu'une sentinelle dans `name`, parce que le nom appartient à l'utilisateur dès
qu'il le change, et qu'un client doit continuer de savoir que c'est le groupe par
défaut après un renommage.

Cette décision est aussi ce qui débloque S4-09 : le web n'envoie pas `group_id`
aujourd'hui précisément parce qu'elle n'était pas prise.

**Livrable** : `openapi.yaml` modifié, les trois clients régénérés, la CI de
non-dérive verte, `api-gaps.md` décision 2 marquée tranchée.

---

### S4-01 — `userdata` : les trois opérations · **3** · dépend de S4-00 · ☑

> **Livré**, et la tâche a trouvé un bug qu'elle ne cherchait pas. Le groupe par
> défaut était identifié **par son nom** — l'`ON CONFLICT (user_id, name)` de
> l'upsert et le `SELECT` qui le relisait portaient tous les deux sur
> `'Favorites'`. Renommer ce groupe suffisait donc à en faire créer un second au
> favori suivant. Personne ne l'avait vu parce qu'aucun écran ne savait renommer
> un groupe : la fonction livrée ici est exactement celle qui l'exposait.
> `is_default` le corrige, et `renamingTheDefaultGroupKeepsItDefault` le prouve.
>
> Migration `0014`, onze cas d'intégration, **191 tests verts** sur l'API.

Le côté serveur des trois opérations ci-dessus, plus la mécanique de position.

**Réordonner est la seule partie qui n'est pas triviale.** `position` est un entier
et deux favoris du même groupe ne doivent pas partager la même valeur. Le
déplacement se fait en une transaction : décaler ce qui est entre l'ancienne et la
nouvelle place, puis écrire la nouvelle. Écrire d'abord et décaler ensuite laisse une
fenêtre où l'ordre est faux, et deux clients qui réordonnent en même temps la
trouvent.

**La suppression déplace, elle n'efface pas.** Une transaction : les favoris passent
au groupe par défaut, appendus à la fin dans leur ordre courant, puis le groupe
disparaît. Si le groupe par défaut n'existe pas encore — possible, il est créé
paresseusement — il est créé là.

**Tests** : suppression d'un groupe non vide, suppression du groupe par défaut
refusée, renommage vers un nom pris, réordonnancement concurrent, déplacement vers un
groupe d'un autre compte (`404`, pas `403` — un `403` confirme que le groupe existe).

---

### S4-02 — `core:data` et Room : hors ligne · **5** · dépend de S4-01 · ☑

> **Livré.** Deux tables Room (`favorite_group`, `favorite`), `MIGRATION_2_3` écrite
> à la main et vérifiée mot pour mot contre le `3.json` que Room génère, et un
> `FavoriteRepository` qui lit depuis la base et écrit par le réseau.
>
> **Le découpage à 100 a un piège de plus que prévu.** Le plafond du contrat était
> connu ; ce qui ne l'était pas, c'est que `size` vaut 50 par défaut sur la même
> opération. Un lot de 100 identifiants envoyé sans `size` revient donc **à moitié
> répondu, avec un `200` et aucune erreur** — un catalogue qui ressemble simplement
> à un plus petit catalogue. Le test l'affirme sur les trois appels.
>
> **Une décision prise en écrivant : deux routes de rafraîchissement après une
> écriture.** Ajouter, retirer, créer et renommer touchent une ligne et s'appliquent
> localement — mettre un cœur ne coûte pas trois allers-retours. Déplacer un favori,
> déplacer un groupe et supprimer un groupe font **renuméroter par le serveur** des
> lignes que l'appareil détient : ceux-là relisent les deux listes, parce que deviner
> ce que la renumérotation a fait est la façon dont un ordre local cesse
> silencieusement de correspondre à celui du compte.
>
> 7 cas, **108 tests verts** sur Android, `lint` et `assembleDebug` propres.

Le socle Android, et il vient avant les écrans pour la raison qui a fait de S2-01 un
préalable dur : trois écrans écrits avant lui produiraient trois façons de lire un
favori.

Deux tables Room, `favorite_group` et `favorite`, et un `FavoriteRepository` qui rend
des `Flow`. La base est la source de vérité, le réseau la rafraîchit — c'est déjà le
patron de `CataloguePager`, on ne l'invente pas.

**Le piège est `ids`, et il est réel.** Le paramètre est plafonné à 100, et le
contrat dit pourquoi : c'est une résolution pour un rail, pas un export du catalogue.
Un groupe « Documentaire » à 300 chaînes demande donc **trois** requêtes, et un
client qui ne découpe pas verra ses favoris silencieusement tronqués à 100 — sans
erreur, sans rien. Le découpage se fait dans le repository, une fois, testé une fois.

**Android a un avantage que le web n'a pas** : le catalogue est déjà dans Room. Un
favori se résout par une jointure locale, et `ids` ne sert qu'aux chaînes qu'une
source n'a pas encore synchronisées sur cet appareil. C'est le chemin normal ; le
réseau est le repli.

**Un favori orphelin n'est pas une erreur.** La jointure ne trouve rien, la ligne ne
s'affiche pas. Aucun message, aucune icône de rupture : la chaîne a quitté le
bouquet, ce n'est pas une panne de l'application.

**Hors ligne en écriture** : une modification sans réseau est **refusée avec un
message**, pas mise en file. Une file d'attente demande une résolution de conflit —
deux appareils qui renomment le même groupe — et ce n'est ni le sprint ni le prix.
Le Gherkin le dit explicitement pour que personne ne le prenne pour un oubli.

**Tests** : découpage à 100, favori orphelin, groupe vide, écriture hors ligne
refusée, ordre stable après rafraîchissement.

---

### S4-03 — Mobile : mettre en favori · **5** · dépend de S4-02 · ☑

> **Livré**, avec une correction de la tâche telle qu'elle était écrite et un
> arbitrage de module.
>
> **La feuille est dans `core:designsystem`, pas dans un feature.** Le
> `settings.gradle.kts` l'impose noir sur blanc : *un feature ne dépend jamais d'un
> autre — le partagé descend dans `core/`*. Or la même feuille servira à l'écran
> Favoris (S4-04) et à la télévision (S4-07). Elle est donc un composant **sans
> état**, `LumoFavoriteGroupSheet`, qui reçoit des `LumoFavoriteGroupChoice` et ne
> sait pas ce qu'est un repository. Le libellé est résolu par l'appelant, exprès :
> décider s'il faut traduire le nom du groupe par défaut demande `is_default`
> **et** de savoir si le nom est encore celui du serveur, ce que le feature sait et
> qu'un composant de dessin n'a pas à savoir.
>
> **La tâche disait « appui court sur une chaîne déjà favorite ouvre la feuille ».
> Livré ainsi, et voici pourquoi c'est le bon comportement** plutôt qu'un
> dé-favori : une chaîne rangée dans deux groupes n'a pas de chose unique qu'un
> appui pourrait défaire, et deviner la retirerait d'un groupe que personne n'a
> nommé.
>
> **Un cas que le document ne prévoyait pas.** Retirer une chaîne d'un groupe ne la
> dé-favorise pas — elle est peut-être dans un autre. Vider le cœur serait un
> mensonge que la prochaine émission de Room corrige aussitôt : un clignotement sur
> une liste que quelqu'un regarde. `stillFavoritedWithout` répond à ça, et c'est le
> seul endroit du geste qui se trompe silencieusement sur un compte à plusieurs
> groupes.
>
> 5 cas sur l'état, **113 tests verts** sur Android, `lint` et `assembleDebug`
> propres.

Un cœur sur chaque carte de chaîne, dans la liste et dans le lecteur.

**Un appui court sur une chaîne non favorite l'ajoute au groupe par défaut, sans
question.** C'est le geste courant et il doit coûter un doigt. Un appui long — ou
l'appui court sur une chaîne déjà favorite — ouvre la feuille des groupes : une case
par groupe, l'état réel de la chaîne dans chacun, et une ligne « Nouveau groupe » en
bas.

**Le choix des groupes est multiple, parce que le modèle l'est.** Une liste à choix
unique forcerait à retirer d'un groupe pour ajouter à un autre — l'inverse de ce que
la story demande.

**La création à la volée est ce qui rend la fonction utilisable.** Quelqu'un qui veut
« Documentaire » le veut au moment où il regarde une chaîne documentaire, pas dans un
écran de réglages qu'il faudrait aller chercher d'abord.

Retour immédiat à l'écran : l'état optimiste s'affiche, et un échec le repose en
nommant la raison. Un cœur qui met 400 ms à répondre est un cœur sur lequel on
appuie deux fois.

---

### S4-04 — Mobile : l'écran Favoris · **5** · dépend de S4-02

Le premier écran du catalogue qui n'est pas sous une source, et c'est le point à
traiter avant de dessiner quoi que ce soit — voir le deuxième constat.

Un onglet par groupe, le groupe par défaut en premier, les autres dans l'ordre de
leur `position`. Sous les onglets, les chaînes du groupe, dans **l'ordre de
l'utilisateur** — pas dans celui du fournisseur, sinon la réorganisation de S4-05
n'aurait rien à montrer.

**Chaque carte dit de quelle source elle vient**, discrètement. Sans ça, deux chaînes
homonymes venant de deux abonnements sont indiscernables, et c'est le cas d'usage
même du groupe multi-sources.

**Aucun favori, aucun groupe** : un `EmptyState` qui dit le geste — « mettez une
chaîne en favori depuis vos chaînes » — et un lien qui y mène. Pas un écran qui
constate le vide.

**Le nom du groupe par défaut** se traduit tant que `is_default` est vrai **et** que
l'utilisateur ne l'a pas renommé. C'est ce que S4-00 rend possible.

---

### S4-05 — Mobile : organiser · **3** · dépend de S4-04

Renommer, supprimer, déplacer, réordonner. Rien de subtil sauf deux choses.

**La suppression dit ce qu'elle fait avant de le faire.** « Les 12 chaînes de ce
groupe iront dans Favoris » — pas « Êtes-vous sûr ? », qui n'informe de rien. C'est
la règle de S4-00 rendue visible : l'utilisateur doit pouvoir prédire l'état d'après.

**Le glisser-déposer n'est pas le seul chemin.** Un menu « Monter / Descendre » sur
chaque ligne fait le même travail et reste accessible à quelqu'un qui utilise
TalkBack. Le glisser-déposer est le confort, pas le mécanisme.

---

### S4-06 — TV : les groupes dans la bande de puces · **5** · dépend de S4-02

Les groupes deviennent des puces, devant les catégories de la source, séparés d'elles
par un intervalle visuel — pas par un libellé de section, qui prendrait une hauteur
que la bande n'a pas.

L'ordre de la bande : **Toutes · [groupes] · [catégories de la source]**. « Toutes »
reste la première puce et l'état d'ouverture, comme S2-13 l'a tranché : un catalogue
qui démarre dans un groupe cache le reste.

**Rien à ajouter à la carte du parcours de focus, et c'est le but.** Une puce de
groupe se comporte comme une puce de catégorie : `LEFT`/`RIGHT` parcourent la bande,
`DOWN` descend dans la grille, `OK` filtre. Le tableau de
[`tv-focus-map.md`](../design/tv-focus-map.md) § *Chaînes* reste vrai mot pour mot.
Il faut quand même **le relire et le confirmer explicitement** dans la tâche : une
case qui reste vraie sans qu'on l'ait vérifiée est une case qu'on a eu de la chance
d'avoir juste.

**Un groupe vide n'a pas de puce.** Filtrer sur rien produit une grille vide au bout
d'un `OK`, ce qui ressemble à une panne.

---

### S4-07 — TV : mettre en favori sans quitter la grille · **3** · dépend de S4-06

Le geste qui coûte le plus cher à mal concevoir sur une télécommande.

`OK` lance la chaîne — c'est US-10 et ça ne bouge pas. La mise en favori se fait par
**un appui long sur `OK`**, ou par la touche dédiée quand la télécommande en a une.
Un second bouton dessiné sur la carte donnerait deux cibles de focus par chaîne dans
une grille qui en compte des centaines : le parcours horizontal doublerait de
longueur pour une action utilisée une fois par chaîne dans une vie.

Un appui long ouvre la même feuille de groupes que sur le téléphone, adaptée à la
distance : une colonne, la première ligne focalisée, `BACK` referme sans rien
changer. **Cette feuille, elle, s'ajoute à la carte du parcours de focus** — c'est
une nouvelle surface, avec ses directions à documenter.

Un retour visuel sur la carte, immédiat : un cœur qui apparaît. À trois mètres, une
action sans retour visible est une action dont on ne sait pas si elle a eu lieu.

---

### S4-08 — TV : les chaînes récemment regardées · **3**

`SRV-06` a livré la table, la fenêtre glissante et les deux endpoints il y a un
sprint. **Personne ne les appelle**, sur aucune des deux applications Android.

Et il y a une divergence à trancher, pas à contourner :
[`api-gaps.md`](../design/api-gaps.md) M5 justifie la fonction par *un rail, premier
écran de la télévision*. `S2-13` a livré une grille et documenté pourquoi les rails
plafonnent ce qu'ils contiennent. Les deux raisonnements sont bons ; ils ne portent
pas sur la même chose.

**Proposition, à valider dans la tâche : une puce « Repris », en tête de bande, juste
après « Toutes ».** Un historique récent est court par construction — la fenêtre
glissante le garantit — donc l'objection du plafond ne s'applique pas à lui. Et il
reste dans la mécanique de l'écran au lieu d'en ouvrir une seconde.

**`PUT /me/recent-channels` s'envoie quand la lecture démarre**, jamais au focus.
C'est la règle déjà écrite en S3-08 et elle vaut double sur une télévision, où la
D-pad traverse vingt chaînes pour en atteindre une. Un historique construit sur le
passage du focus est du bruit, et c'est l'historique de l'utilisateur qu'on abîme.

Si la puce est retenue, `api-gaps.md` M5 est corrigé pour dire ce qui a été fait.
Un document qui décrit un rail qui n'existe pas est pire que pas de document.

---

### S4-09 — Web : organiser ses groupes · **3** · dépend de S4-00

Le web sait ajouter depuis S3-08. Il lui manque la gestion, et le `group_id` que
l'étoile n'envoie pas.

Les groupes vivent dans l'URL comme le reste du catalogue : `?group=` à côté de
`?category=`, `?q=` et `?page=`. Partageable, compatible avec le bouton retour,
et **fonctionnel sans JavaScript** — la règle de la zone n'est pas suspendue parce
que l'écran est nouveau.

Création, renommage et suppression en Server Actions, sur le modèle des trois qui
existent. La suppression demande la même confirmation nommée que sur le téléphone,
avec le même chiffre.

**L'étoile envoie enfin `group_id`.** Un `<select>` dans le formulaire de l'étoile,
avec le groupe par défaut présélectionné : un formulaire, un envoi, pas de
JavaScript. Le retour se fait sur la vue exacte d'où l'étoile a été cliquée — groupe,
catégorie, recherche et page compris.

---

## Récapitulatif

| Bloc | Tâches | Points |
|---|---|---|
| Contrat et serveur | S4-00, S4-01 | 6 |
| Socle Android | S4-02 | 5 |
| Téléphone | S4-03 → S4-05 | 13 |
| Télévision | S4-06 → S4-08 | 11 |
| Web | S4-09 | 3 |
| **Total** | **10 tâches** | **38** |

**Ordre de réalisation** — les dépendances comptent plus que les priorités :

```
S4-00 → S4-01 → S4-02 → S4-03 → S4-04 → S4-05
                     → S4-06 → S4-07
                     → S4-08
S4-00 → S4-09 (parallèle à tout le bloc Android)
```

S4-00 et S4-01 avant tout : trois opérations de contrat qui manquent, et tout écran
écrit sans elles devra être repris. S4-02 avant le moindre écran Android, pour la
raison qui a fait de S2-01 un préalable dur. S4-09 ne dépend que du contrat et peut
partir en parallèle du bloc Android dès que S4-00 est mergé.

**Une coupure naturelle**, si le sprint est trop gros : S4-00 → S4-05 et S4-09
(**24 points**) livrent les favoris sur le téléphone et le web et se démontrent
seuls. La télévision (S4-06 → S4-08, **14 points**) suit et ne dépend que de S4-02.

---

## Hors périmètre

Explicitement, pour que la question ne se repose pas en cours de route :

- **Les favoris de films et de séries.** `AddFavoriteRequest` exige un `channel_id` :
  mettre un film en favori demandera un aller au contrat. Il est prématuré tant que
  les films n'existent pas — voir [`sprint-05.md`](./sprint-05.md), qui le nomme dans
  son propre hors-périmètre pour que le manque soit tracé et pas découvert.
- **La synchronisation hors ligne des modifications.** Décidée contre, ci-dessus.
- **Le partage d'un groupe entre comptes.** Rien ne le demande, et ça ouvrirait une
  question de droits qui n'existe nulle part ailleurs dans le produit.
- **Un plafond sur le nombre de groupes.** Le jour où il existe, il vient de
  `GET /me/entitlement` — voir la dette ci-dessous.

---

## La dette qu'on assume

Trois chantiers sont volontairement repoussés — un vrai client OAuth Google, le
webhook Stripe, la recette complète des sprints 1 et 2. Ce ne sont pas des oublis :
ce sont des décisions, et elles vivent dans **[`dette.md`](./dette.md)**, qui se relit
à l'ouverture de chaque sprint.

Ce document porte aussi **les quatre règles qui empêchent la dette de grossir** — dont
celle qui compte le plus ici : aucun écran ne devine un droit d'accès, même quand tout
le monde est `FREE` et illimité. Un plafond sur le nombre de groupes de favoris, le
jour où il existera, viendra de `GET /me/entitlement` et de nulle part ailleurs.

---

## Documents associés

Sur le modèle du sprint 2, deux documents suivent une fois les tâches acceptées :
`sprint-04-recette.md` et `sprint-04-demo.md`. Ils ne sont pas écrits d'avance — la
recette de S4-08 dépend de ce que la tâche tranche sur la divergence M5.
