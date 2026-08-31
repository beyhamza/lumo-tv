# Plan de démo — Sprint 6

La Definition of Done exige une démo sur appareils réels, téléphone **et**
télévision pilotée à la télécommande. Ce document est le déroulé de celle-ci.

**Durée visée : 16 min 30**, questions non comprises — la somme des actes ci-dessous.
Le fil est plus simple que celui du sprint 5, parce que le sujet l'est :

> Je choisis une série, je regarde un épisode, le suivant s'enchaîne tout seul —
> et il ne s'enchaîne pas si je touche la télécommande. Je m'arrête, je rallume
> ailleurs, je reprends au bon épisode.

**S'il faut couper**, c'est dans cet ordre : l'acte 8 (les favoris web), puis
l'acte 7 (les langues), puis la moitié web de l'acte 6. **Jamais l'acte 4** — c'est
la seule chose de ce sprint qui ne se montre qu'avec une télécommande dans la main,
et c'est celle sur laquelle la salle aura un avis.

---

## Matériel

- Un téléphone Android physique, **écran dupliqué** sur le vidéoprojecteur.
- Une box ou un téléviseur Android TV physique, **avec sa télécommande**. Pas
  d'émulateur : l'acte 4 porte sur le fait qu'une touche annule un décompte, et une
  souris n'a pas de touches.
- Un navigateur, sur le même compte.
- L'API et le banc d'essai démarrés et vérifiés.

### Le banc suffit, depuis le 31 août 2026

**Cette section disait qu'il n'y avait pas de démo sans abonnement réel.** Le banc
sert maintenant un panel Xtream qui répond :

```bash
docker compose --profile bench --env-file apps/api/.env up -d
```

Source Xtream sur `http://localhost:18081`, identifiants quelconques. Deux séries,
trois films, trois chaînes — tout inventé.

**Et c'est mieux qu'un abonnement réel, pas seulement plus commode.** La série de
démonstration `Les Falaises` a deux saisons **et un trou à l'épisode 3**, ce qu'un
catalogue réel ne garantit pas ; l'acte 4 a besoin d'une fin de saison, et le banc
la fournit toujours au même endroit. Un des épisodes n'a pas de titre, ce qui rend
« Épisode 4 » visible plutôt que raconté.

**Et la règle du contenu cesse d'être en tension.** Il n'y a plus de raison de
brancher un catalogue plein de titres que tout le monde reconnaît, donc plus de
moment où quelqu'un demande une capture qu'il faut refuser. Si une démo se joue
quand même sur un abonnement réel, la règle d'origine tient : on regarde, on
n'archive pas (AGENTS.md §1, CLAUDE.md règle 2).

**Ce que le banc ne fournit toujours pas : un fichier qu'un lecteur décode.** Ce que
servent `/movie/` et `/series/` sont des octets MPEG-TS sous un nom de film. Tout se
démontre jusqu'à la lecture — la grille, la fiche, l'arbre, le focus, le décompte —
et **l'image elle-même demande un vrai fichier préparé à la main**, comme au sprint
5. C'est ce qui reste de la dette n° 4.
**Choisir la série de démonstration à l'avance**, et en prendre une qui a **au
moins deux saisons**. L'acte 4 a besoin d'une fin de saison pour montrer
l'enchaînement, et l'acte 3 a besoin d'un sélecteur de saison pour exister.

---

## Préparation, trente minutes avant

1. Dérouler le parcours en entier, une fois, seul.
2. **Deux sources sur le compte** : une Xtream (**la première**, les applications
   Android lisent la première source du compte) et `/playlist.m3u` du banc. L'acte 1
   repose sur la différence, et le web est le seul endroit où elle se montre côte à
   côte.
3. **Ouvrir la série de démonstration une fois sur chaque appareil**, la veille ou
   le matin. Le premier `GET /series/{id}` va chercher l'arbre chez le fournisseur
   et peut prendre plusieurs secondes ; le cache tient six heures. Ce n'est pas de
   la triche : c'est exactement ce que fait un utilisateur qui a déjà regardé la
   série, et c'est l'état dans lequel l'acte 6 la trouve.
4. **Effacer la progression du compte de démo.** L'acte 6 doit partir de rien.
5. **Repérer un épisode court**, ou savoir avancer jusqu'à la fin d'un épisode :
   l'acte 4 a besoin d'un générique et personne n'a quarante minutes.
6. Mettre les appareils en français, préparer le basculement pour l'acte 7.
7. Couper les notifications.

---

## Déroulé

### Acte 1 — Trois absences, trois phrases · 1 min 30 · navigateur

Commencer par ce que le produit dit quand il n'a rien à montrer. C'est
contre-intuitif, et c'est là que se joue la leçon du sprint précédent.

**Sur la source M3U**, ouvrir l'onglet Séries :

> « L'onglet est là. Il n'y a rien derrière, et l'écran explique pourquoi : une
> playlist M3U ne déclare ni saison ni épisode. Le format ne peut pas porter de
> séries. »

**Sur la source Xtream**, chercher une série qui n'existe pas :

> « Autre phrase : aucun résultat. Ce n'est pas la même chose. »

Puis la troisième, à nommer sans la montrer :

> « Et si le panel n'avait pas de séries du tout, il y aurait une troisième phrase.
> Trois absences, trois raisons. Dire à quelqu'un que son panel ne sait pas faire
> quelque chose qu'il sait faire est la pire des trois erreurs. »

**Le point à faire passer**, et c'est un aveu :

> « Au sprint dernier, ces onglets n'existaient pas quand le catalogue était vide.
> On trouvait ça propre. Le propriétaire d'un panel de cent quarante mille films
> ne les a pas trouvés et a conclu que la fonction n'existait pas. Une absence est
> indiscernable d'un bug. »

---

### Acte 2 — La grille, et rien de neuf · 1 min · téléphone

Ouvrir l'onglet Séries.

> « C'est la grille des films. Mêmes deux colonnes, mêmes affiches, même bande de
> catégories, même recherche. Il n'y a rien à apprendre ici, et c'est le but :
> quelqu'un qui sait se servir d'un des deux catalogues sait se servir de l'autre. »

Faire défiler assez pour montrer que c'est paginé, sans commenter.

---

### Acte 3 — La fiche, et ce qui arrive en deux temps · 2 min 30 · téléphone

**Ouvrir une série jamais ouverte sur cet appareil** — pas celle de démonstration.

> « Regardez le haut de l'écran : l'affiche, le titre, l'année sont là tout de
> suite. Ils viennent de la liste, on les a déjà. Seule la zone des épisodes
> attend. »

Laisser l'arbre arriver.

> « Ce qui vient d'arriver a coûté un appel au serveur de l'utilisateur. Un seul,
> pour cette série, au moment où quelqu'un la regarde. Une grille qui chargerait
> l'arbre de chaque affiche ferait huit cents requêtes chez son fournisseur pour un
> défilement. »

Montrer le sélecteur de saison, la première saison déjà ouverte.

> « La première saison est ouverte. On ne demande pas de choisir une saison avant de
> montrer quoi que ce soit. »

Montrer un épisode sans titre :

> « Épisode 4. Pas "épisode sans titre" — ça remplit une ligne pour dire qu'elle est
> vide. »

**Si l'occasion se présente**, montrer le décompte divergent :

> « Le panel annonce vingt-quatre épisodes, il en a renvoyé vingt-deux. On affiche
> les deux. La liste est ce qu'on peut regarder ; l'annonce est parfois le seul
> indice qu'il manque quelque chose. »

---

### Acte 4 — La télécommande, et l'épisode suivant · 4 min · TV, télécommande uniquement

**L'acte le plus important. Il ne se coupe pas.**

Depuis le rail, descendre à Séries, `RIGHT`, choisir la série de démonstration.

> « Le focus arrive sur un épisode, pas sur le sélecteur de saison. Celui qui ouvre
> une série veut la regarder. »

`UP` :

> « Le sélecteur est au-dessus. Sur une série à saison unique il n'est pas dessiné
> du tout, et `UP` ne fait rien — c'est la seule zone de focus conditionnelle de
> l'application. »

`DOWN`, `OK` sur le dernier épisode d'une saison. Avancer jusqu'au générique.

**La carte apparaît.**

> « Dix secondes. La carte a le focus, `OK` lance tout de suite. »

**Laisser le décompte descendre à 3, puis appuyer sur `UP`** — une touche que
l'écran n'utilise pas.

> « Le décompte s'est arrêté. La carte est toujours là. »

Marquer un temps. C'est le moment de l'acte.

> « Quelqu'un qui appuie sur une touche pendant un générique a pris sa télécommande
> *parce que* le générique a commencé. Lancer un épisode sous son doigt ne se
> pardonne pas. Ce qu'il perd, c'est la partie automatique — pas la proposition. »

`OK`.

> « Et c'était le dernier épisode de la saison 1 : le suivant est le premier de la
> saison 2. »

Laisser un enchaînement se faire tout seul, puis `BACK`.

> « On revient à la fiche. Pas à l'épisode précédent. Trois épisodes enchaînés, une
> seule entrée dans la pile — sinon une soirée devient un labyrinthe. »

**Terminer par la règle qui n'a pas d'écran :**

> « Ce qui vient après un épisode, c'est le suivant *listé*. Pas le numéro plus un.
> Les panels sautent des numéros — un fichier qui n'a pas été téléversé, un
> hors-série numéroté zéro, une saison 2 jamais ingérée entre la 1 et la 3.
> Neuf tests couvrent ça, et tous les neuf sont des panels qui existent. »

---

### Acte 5 — Ce que la télévision ne fait pas · 1 min · TV

Montrer la bande de catégories, sans « Reprendre » (le compte est vierge).

> « Sur le téléphone, "Reprendre" sera un rail au-dessus de la grille. Ici c'est une
> puce dans cette bande. Un rail au-dessus d'une grille de télévision, c'est une
> seconde zone de focus, et cette bande n'a pas la place. Deux appuis au lieu d'un,
> et aucune zone nouvelle. C'est la troisième fois qu'on prend cette décision, et
> c'est la même. »

---

### Acte 6 — Reprendre, et pas où on croit · 3 min · téléphone → TV

**L'acte qui prouve le sprint.**

Sur le **téléphone**, ouvrir la série de démonstration, lancer un épisode, laisser
tourner **une minute**, revenir.

> « Une barre est apparue sous cet épisode. Sous lui seul. Une barre à zéro sur
> chaque ligne dirait que tout le monde a commencé tout. »

Revenir à la grille des séries :

> « Et le rail "Reprendre" a une carte. Une par série — pas une par épisode. »

**Basculer sur la télévision.** Ouvrir la même série.

> « Le focus est arrivé sur l'épisode que je regardais sur le téléphone, avec sa
> barre. »

`OK`.

> « Et il reprend à la minute. »

**Puis la phrase qui explique tout le titre de la tâche :**

> « La progression est enregistrée sur un **épisode**. Mais ce qu'on reprend, c'est
> une **série** — personne ne se souvient d'un identifiant, on se souvient d'être
> arrivé à l'épisode quatre. Passer de l'un à l'autre demande l'arbre, et c'est
> pour ça que le rail ne montre que les séries que cet appareil a déjà ouvertes.
> Ce qui est le cas de toute série qu'on a commencé à regarder dessus. »

**Et le seuil**, s'il reste du temps :

> « Au-delà de quatre-vingt-quinze pour cent, la carte ne propose plus l'épisode
> qu'on vient de finir : elle propose le suivant, au début. Et quand il n'y a plus
> de suivant, la série sort du rail. Proposer de recommencer une série finie n'est
> pas une proposition. »

---

### Acte 7 — Les deux langues · 1 min · téléphone + TV

Basculer la langue système. Rouvrir la fiche et le lecteur.

> « Trois endroits à regarder : la phrase de la playlist, "À suivre dans 7 s", et la
> ligne "Depuis" qu'on va voir dans une seconde. Les deux langues sont livrées
> ensemble ; le lint fait échouer le build sur une traduction manquante. »

---

### Acte 8 — Le groupe qui mentait · 2 min 30 · navigateur

**Ce n'est pas une fonction manquante, c'est une fonction qui mentait.**

Sur le web, page des chaînes de la **source A**, sur un groupe qui contient aussi
une chaîne de la **source B** :

> « Ce groupe contient quatre chaînes. Cet écran en montre trois. »

Montrer la phrase sous le rail :

> « Il le dit, maintenant. Avant, il se taisait. »

Cliquer sur « Voir tous les favoris » :

> « Voilà le groupe entier. Et sous chaque chaîne, d'où elle vient. »

> « Un groupe appartient au **compte**, pas à une source — c'est le point structurel
> d'US-12 depuis le début, et le téléphone l'assume depuis le sprint 4. Le web
> montrait un groupe amputé de la moitié sans le dire. C'est la catégorie de défaut
> la plus chère : rien n'a l'air cassé. »

**Terminer sur le plafond**, si la salle est technique :

> « Le contrat plafonne à cent identifiants **par requête**. Un groupe de trois cents
> chaînes sur trois sources, ce n'est pas trois requêtes : c'est des lots, par
> source, dans une boucle. Une coupe à cent aurait tronqué un groupe avec un `200` et
> aucune erreur. Le téléphone avait déjà payé cette facture au sprint 4. »

---

## Si ça casse

| Panne | Repli |
|---|---|
| Le panel Xtream ne répond pas | `docker compose --profile bench up -d`, et vérifier `curl localhost:18081/player_api.php`. Sans lui il n'y a pas de démo, ce qui est pourquoi la préparation le vérifie |
| La fiche met plus de dix secondes à charger | C'est le vrai comportement d'un premier chargement — le dire, ne pas relancer. C'est l'acte 3 |
| L'arbre ne charge pas du tout | Montrer « Épisodes indisponibles » et son bouton : c'est le comportement voulu, pas une panne de la démo |
| Le décompte ne s'arrête pas à l'appui | **Ne pas insister devant la salle.** Le noter, passer à l'acte 5 |
| Le rail « Reprendre » ne se remplit pas | Attendre trente secondes : c'est le pas d'écriture. Ne pas relancer l'épisode en boucle |
| La TV ne reprend pas au bon épisode | Vérifier que la série a été ouverte une fois sur la TV — c'est la limite assumée, et elle s'explique en une phrase |
| Une affiche ne charge pas | **Ne pas s'en excuser.** Le titre sur un aplat est le comportement voulu |
| Un plantage | Le noter et continuer |

---

## Ce que cette démo ne montre pas

À dire, pas à laisser découvrir.

- **Pas de séries en M3U, et jamais.** C'est une décision écrite
  ([`adr/0010`](../adr/0010-series-are-xtream-only.md)), pas un manque : reconstruire
  un arbre à partir de titres marche jusqu'au jour où ça ne marche pas, et ce
  jour-là quelqu'un a une saison inventée dans son catalogue.
- **Pas de favoris sur une série.** Les groupes contiennent des chaînes. C'est un
  manque d'API tracé ailleurs, pas un oubli d'écran.
- **Pas de guide des programmes.** L'EPG est en dernier, délibérément.
- **Pas de glisser-déposer sur les favoris web.** `PATCH /me/favorites/{id}` est
  livré côté serveur depuis le sprint 4 et n'est appelé par aucun client web. Hors
  périmètre par décision explicite.
- **Le rail « Reprendre » ne montre que les séries que cet appareil a ouvertes.**
  Limite assumée, et l'acte 6 la nomme plutôt que de la contourner.
- **Aucun sélecteur de source sur Android.** Les deux applications lisent la
  première source du compte. Seul le web a une page par source. Antérieur à ce
  sprint, toujours pas chiffré.
- **La recherche des séries ne pardonne pas une faute de frappe**, comme celle des
  films : elle parcourt le cache de l'appareil.
- **Le premier rafraîchissement d'un gros catalogue n'est pas mesuré.** Cinquante
  mille séries, c'est deux cent cinquante requêtes de pagination à la première
  synchronisation d'un appareil. Personne ne sait ce que ça donne sur une connexion
  domestique, et c'est écrit dans la recette.
