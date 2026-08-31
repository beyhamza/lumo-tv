# Recette — Sprint 5

Plan de qualification d'**US-13** sur les trois surfaces. Il se déroule **à la
main**, sur des appareils réels et dans un navigateur, et il ne remplace ni les
220 tests API, ni les 146 tests Android, ni les 39 tests web : il vérifie ce
qu'aucun des trois ne voit — qu'un film commencé sur un téléphone se reprend sur
une télévision, et qu'une règle de classement écrite dans un ADR produit bien, sur
un vrai catalogue, les erreurs qu'elle annonce.

Les critères d'acceptation sont ceux d'**US-13**, en Gherkin, dans
[`sprint-05.md`](./sprint-05.md). Chaque cas renvoie à la tâche qui l'a livré.

**La numérotation reprend à R-200.** R-10 → R-73 appartiennent à
[`sprint-02-recette.md`](./sprint-02-recette.md), R-100 → R-194 à
[`sprint-04-recette.md`](./sprint-04-recette.md), et les deux restent à jouer.

---

## 1. Prérequis

Six points. Les trois premiers sont ceux de toute recette de ce projet ; les trois
derniers sont propres à ce sprint et **deux d'entre eux rendent des cas
injouables** s'ils sont ratés.

**Les trois surfaces, sur le même compte.** Un téléphone Android physique, une box
ou un téléviseur Android TV physique **avec sa télécommande**, et un navigateur.
La section 8 est la moitié de la valeur de ce sprint et elle ne se démontre pas
sur une seule surface.

**Une mise à jour, pas une installation neuve.** Le piège habituel, et il a une
migration de plus cette fois. `MIGRATION_4_5` crée la table `vod_item`, elle est
écrite à la main, et **une installation fraîche ne l'exécute jamais** : Room crée
le schéma final directement.

```bash
# Sur le téléphone et sur la box, avant de commencer :
adb install -r app-mobile-debug.apk   # -r : par-dessus, jamais une désinstallation
```

Désinstaller entre les deux, c'est jouer R-290 en croyant l'avoir joué.

**Langue.** Chaque cas se rejoue en français **et** en anglais. Deux endroits
souffrent particulièrement cette fois : la phrase de refus de déplacement, longue
dans les deux langues, et le libellé « Reprendre à 1:47:03 » sur une puce de
télévision.

### Les trois prérequis propres au sprint

**Deux sources : une avec des films, une sans.** Le banc d'essai sert les deux —
`/mixed.m3u` porte des chaînes **et** des films, `/playlist.m3u` ne porte que des
chaînes. C'est le seul moyen de jouer R-210, R-211, R-250 et R-278, qui vérifient
une **absence** : l'onglet, l'entrée de rail et le lien web ne doivent pas exister
sur une source sans films. Avec une seule source, ces quatre cas sont « non joué »,
pas « vert ».

**Et la manière de basculer diffère selon la surface.** Le web a une page par
source, donc les deux se regardent côte à côte, sans rien changer. **Les
applications Android lisent la première source du compte et n'ont pas de
sélecteur** : R-210 et R-250 demandent donc soit deux comptes, soit de supprimer et
réenregistrer la source entre les deux passes. C'est plus lourd, ce n'est pas une
excuse pour les sauter, et c'est écrit ici pour que la lourdeur ne soit pas une
surprise.

**Un fichier de film qui se lit vraiment — le banc en sert un depuis le 31 août
2026.** `/film/le-voyage.mp4` est un vrai MP4 : H.264 baseline et AAC, six
secondes de mire et de sinus, `faststart`. `/film/la-traversee.mkv` est le même,
remuxé en Matroska pour Media3. `/movie/` et `/series/` — les chemins qu'un panel
Xtream construit — servent le même fichier.

> **Ces fixtures étaient des octets MPEG-TS sous un nom de film**, ce qui suffisait
> à l'ADR 0009 — qui classe sur l'URL — et ne se décodait nulle part. Six cas de
> lecture réelle sont restés « non joué » deux sprints pour cette raison. Ils sont
> jouables.

**Un serveur qui refuse les requêtes `Range` — le banc en sert un aussi.**
`/film-norange/le-voyage.mp4` sert le même fichier avec `max_ranges 0` : nginx
ignore l'en-tête et répond `200` avec le corps entier, ce que font énormément de
panels. `Accept-Ranges` n'est délibérément **pas** annoncé — annoncer puis ignorer
est un troisième comportement, pire que les deux, et qui ne mérite pas d'être
reproduit.

> Une directive, et elle a mis deux sprints à être écrite. R-242, R-264 et R-277
> l'attendaient ; un `python3 -m http.server` n'y suffisait pas, puisqu'il gère
> `Range`.
---

## 2. La règle qui prime sur la recette elle-même

**Aucun contenu réel, à aucun moment.** Pas de vraie playlist, pas d'identifiants
d'un vrai fournisseur, **pas d'affiche de film**, pas de titre d'une œuvre
existante — ni dans les captures jointes à un rapport, ni dans un ticket ouvert
pendant la session (AGENTS.md §1, CLAUDE.md règle 2).

Ce sprint rend cette règle plus facile à enfreindre que les précédents : une
grille d'affiches invite à tester « avec de vraies jaquettes pour voir le rendu ».
`/mixed.m3u` porte des titres inventés et aucune affiche, et c'est exactement ce
qu'il faut regarder — **une carte sans affiche est un cas nominal ici, pas un cas
dégradé** (R-221).

---

## 3. L'ingestion et le classement — ADR 0009

Cette section se joue **une fois**, sur le serveur, et conditionne tout le reste.
Elle est la seule qui vérifie une décision plutôt qu'un écran.

**R-200 · Une playlist mixte se sépare en deux** · S5-05 · n'importe quelle surface
Enregistrer `/mixed.m3u` et attendre `READY`.
→ La source expose des chaînes **et** des films. Le compte de chaînes de la page
Source ne compte pas les films.

**R-201 · Une extension de fichier fait un film** · S5-05
→ *Le Voyage* (`.mp4`) et *La Traversée* (`.mkv`) sont dans les films.

**R-202 · Le segment `/movie/` aussi, jeton compris** · S5-05
→ *Les Falaises*, dont l'URL est `/movie/user/pass/9001.mp4?token=abc123`, est dans
les films. **La chaîne de requête est coupée avant que l'extension soit lue** : un
jeton qui finirait par autre chose ne doit pas décider.

**R-203 · Le premier cas où la règle se trompe, exprès** · S5-00, S5-05
→ *Chaîne 06 en continu* — une chaîne servie comme fichier progressif — **est
rangée dans les films**. C'est **vert**. L'ADR l'accepte, et la sortie est la
surcharge par catégorie qu'il chiffre à trois points, pas une règle plus maligne.

**R-204 · Le second, exprès aussi** · S5-00, S5-05
→ *Le Dernier Quai* — un film dans un groupe nommé « Films », servi en HLS — **reste
dans les chaînes**. C'est **vert** : le doute penche vers `LIVE`, et
`group-title` ne classe rien. Le film se lit et la recherche le trouve ; il est
seulement dans la mauvaise liste.

> R-203 et R-204 sont les deux cas de cette recette qu'on a envie de déclarer
> rouges. Les déclarer rouges, c'est rouvrir une décision déjà prise et écrite.

**R-205 · Un synopsis coûte une requête, une seule fois** · S5-04
Ouvrir une fiche de film, revenir, la rouvrir.
→ Le synopsis apparaît la première fois. **La seconde ouverture n'appelle pas le
panel** : le serveur a estampillé `plot_fetched_at` et répond depuis sa propre
ligne. Vérifiable dans les journaux de l'API.

**R-206 · Un catalogue de films en échec ne casse pas la source** · S5-03
Sur une source Xtream dont l'appel VOD échoue (panel injoignable pour cette
opération seule).
→ La source reste **`READY`**, les chaînes sont là, les films sont absents. Une
source passée en `ERROR` parce que son catalogue de films n'a pas répondu est
rouge.

> **Ce cas n'avait aucun test automatisé, et il en a cinq depuis le 31 août 2026.**
> `IngestionServiceIntegrationTest` parcourt la synchronisation en entier contre
> les fixtures du banc, et l'un de ses cas est précisément celui-ci : un catalogue
> qui échoue ne doit pas emporter sa source.
>
> Il reste le cas de recette le plus important de la section, pour une raison qui a
> changé : ce que la CI ne peut pas faire est de le jouer contre le panel de
> quelqu'un.

---

## 4. La grille — téléphone

**R-210 · Une source sans films montre l'onglet, et la grille explique** · S5-08 ·
téléphone
Compte dont la **première** source est `/playlist.m3u`.
→ **L'onglet Films est là**, et il ouvre sur « Aucun film — cette source ne propose
que des chaînes. Il ne manque rien ici. »

> **Ce cas disait exactement le contraire, et il était vert.** Il vérifiait
> l'absence de l'onglet, au nom de « une promesse vide est pire qu'une absence ».
> Un usage réel l'a démenti : le propriétaire d'un panel de cent quarante et un
> mille films ne les a pas trouvés et a conclu que la fonction n'existait pas.
> **Une absence est indiscernable d'un bug.** Ce qui subsiste de la règle est la
> distinction entre un catalogue *vide* — une réponse — et un écran *non
> construit* — une promesse ; c'est R-212 qui la tient maintenant.

**R-211 · Une source avec des films ouvre sur la grille** · S5-08 · téléphone
Compte dont la **première** source est `/mixed.m3u`.
→ L'onglet Films est **en deuxième position**, après les chaînes et avant les
favoris, et il ouvre sur les affiches.

**R-212 · Aucun onglet vers un écran non construit** · S5-08 · téléphone
→ **Pas d'onglet Recherche.** C'est un placeholder : une entrée qui mène à « cette
fonction arrivera » dit qu'elle existe alors qu'elle n'existe pas. La distinction
avec R-210 est la seule moitié de l'ancienne règle qui reste.

> **Ce cas nommait aussi les séries, et il ne le fait plus** : `S6-05` a livré la
> fiche, donc l'onglet est là. Il l'a gagné en gagnant un écran, pas un catalogue —
> ce qui est exactement la règle, énoncée dans le bon sens. La recherche est la
> dernière destination que cette phrase tient encore à distance.

**R-213 · Des affiches, pas une liste** · S5-08 · téléphone
→ Deux colonnes, affiches en portrait, titre sous l'image. Trois colonnes est
rouge : sous ~150 dp on ne reconnaît plus un film.

**R-214 · Les catégories filtrent** · S5-08 · téléphone
→ « Tous » d'abord, et c'est l'état d'ouverture. Choisir une catégorie change la
grille.

**R-215 · La recherche parcourt le cache** · S5-08 · téléphone
Chercher un titre exact, puis le même avec une faute de frappe.
→ Le premier trouve, **le second ne trouve rien**, et la phrase sous le champ le
disait. C'est vert : la recherche tolérante appartient au serveur et n'est pas
livrée ici.

**R-216 · La grille tient hors ligne** · S5-07 · téléphone
Synchroniser, couper le réseau, rouvrir l'onglet.
→ Les films sont là, et la mention « Hors ligne » aussi — **discrète**, pas une
bannière.

**R-217 · Une resynchronisation vide les synopsis** · S5-07 · téléphone
Ouvrir une fiche (le synopsis s'affiche), actualiser le catalogue, rouvrir la même
fiche.
→ Le synopsis est **re-récupéré**, une requête. C'est **vert** : c'est écrit dans
`VodDao.replaceForSource` et c'est le prix assumé du remplacement en bloc.

**R-218 · Une grille de 500 films défile sans à-coup** · S5-07 · téléphone
→ Aucun blocage, aucune carte vide qui persiste.

---

## 5. La fiche et la lecture — téléphone

**R-220 · Ce qu'il y a sur la fiche, et ce qu'il n'y a pas** · S5-08 · téléphone
→ Affiche, titre, année, durée, note telle que la source l'écrit, synopsis, bouton
de lecture. **Rien d'autre** : pas d'acteurs, pas de recommandations, pas de
bande-annonce. Une rubrique vide est rouge.

**R-221 · Un film sans affiche montre son titre** · S5-08 · téléphone
→ Un aplat portant le titre. **Aucune image générique, aucune silhouette.** C'est
le cas nominal sur `/mixed.m3u`, qui n'a aucune affiche.

**R-222 · Le bouton de lecture est atteignable sans défiler** · S5-08 · téléphone
→ Il est au-dessus du synopsis. Devoir passer le résumé pour atteindre le seul
contrôle qu'on est venu chercher est rouge.

**R-223 · La note est recopiée telle quelle** · S5-08 · téléphone
Sur une source qui donne `7.4`, `PG-13` ou `★★★★`.
→ Affichée verbatim. Une note normalisée en étoiles ou en pourcentage est rouge.

**R-224 · Une année absente n'est pas un tiret** · S5-08 · téléphone
→ Rien du tout. Beaucoup de panels n'en donnent pas.

**R-240 · Un film se lit** · S5-08 · téléphone
Sur un vrai MP4 progressif (§1).
→ L'image arrive, le son avec.

**R-241 · Le curseur déplace** · S5-08 · téléphone
Faire glisser la barre.
→ La lecture saute à la position, **et la barre ne revient pas sous le doigt**
pendant le glissement. Un curseur qui se bat avec la position qui avance est
rouge.

**R-242 · Un serveur sans `Range` : désactivé *et* expliqué** · S5-08 · téléphone
Sur `/film-norange/` (§1).
→ La barre est **dessinée, inerte, avec la phrase à côté**. Les deux moitiés
comptent : une barre absente est rouge, une barre qui ne bouge pas sans dire
pourquoi est rouge aussi.

**R-243 · Le changement se fait pendant la lecture** · S5-08 · téléphone
→ Media3 le découvre au premier essai, donc une barre qui fonctionnait devient
inerte et s'explique. **C'est vert** : c'est l'ordre dans lequel le serveur répond
réellement.

**R-244 · Pas d'indicateur « direct »** · S5-08 · téléphone
→ Aucun. Ce serait un mensonge sur un fichier.

---

## 6. La télévision, télécommande en main

Cette section se joue **à la télécommande**, sur un appareil réel.
[`tv-focus-map.md`](../design/tv-focus-map.md) est la référence, et **une case vide
de ses tableaux est un défaut**. Une souris produit du survol, pas du focus, et le
survol masque exactement ce que cette section cherche.

**R-250 · L'entrée Films est toujours là, sur une source qui en a ou non** · S5-09 ·
TV
→ Quatre entrées de rail, Films en seconde position, dans les deux cas. Sur une
source sans films elle ouvre sur la grille qui explique.

> **L'argument contraire était plus fort ici qu'ailleurs** — une entrée de rail est
> un arrêt obligatoire en descendant, donc une porte sur une pièce vide coûte un
> appui à chaque trajet. Il reste vrai, et il est le plus petit des deux coûts :
> sur une télévision il n'y a nulle part ailleurs où aller regarder, pas de second
> écran, pas de barre d'adresse. Un appui pour atteindre une grille qui s'explique
> vaut mieux qu'une fonction introuvable.
>
> **L'entrée Séries est là depuis `S6-06`**, à la suite des films, et pour la même
> raison : l'écran derrière elle existe. Ce que R-212 tient encore à distance sur
> les deux surfaces est la recherche.

**R-251 · Le focus arrive sur la première affiche** · S5-09 · TV
→ Pas sur la bande de catégories.

**R-252 · Une seule ligne d'affiches** · S5-09 · TV
→ Une. Deux lignes est rouge : les affiches tombent sous la taille où l'on
reconnaît un film à trois mètres, ce qui est tout leur travail.

**R-253 · Le parcours de la bande** · S5-09 · TV
`UP` depuis la grille, `LEFT`/`RIGHT` sur les puces, `DOWN` pour redescendre.
→ Conforme au tableau *Films* de la carte. Ordre de la bande :
**Tous · [Reprendre] · [catégories]**.

**R-254 · `OK` ouvre la fiche, il ne lance pas le film** · S5-09 · TV
→ La fiche. Lancer directement est rouge : un film se choisit.

**R-255 · Pas d'appui long** · S5-09 · TV
→ Un appui long sur une affiche ne fait rien. Un geste qui ne fait rien à moitié
serait pire.

**R-256 · La fiche a une cible de focus, focalisée à l'arrivée** · S5-09 · TV
→ *Lire*, ou *Reprendre* s'il y a une position. Aucun bouton *Retour* dessiné :
`BACK` est une touche physique.

**R-257 · `BACK` revient sur le film qu'on regardait** · S5-09 · TV
Descendre loin dans la grille, ouvrir une fiche, `BACK`.
→ La grille revient **positionnée sur ce film**, pas en tête. C'est la règle
d'US-10 et c'est le cas qu'il faut jouer après avoir fait défiler, pas sur la
première carte.

**R-258 · Le synopsis est tronqué à dix lignes** · S5-09 · TV
Sur un film au synopsis très long.
→ Tronqué avec des points de suspension, **et complet sur le téléphone**. C'est
**vert** : c'est une limite écrite, et la solution complète serait une zone de
focus dont le seul rôle est de faire défiler du texte.

**R-260 · Rien sur l'image au repos** · S5-09 · TV
→ Ni barre, ni dégradé, ni logo.

**R-261 · `LEFT`/`RIGHT` déplacent et n'ouvrent pas le titre** · S5-09 · TV
Appuyer sur `RIGHT` pendant la lecture.
→ La lecture avance de dix secondes, **la barre seule apparaît**. Le titre du film
**ne doit jamais** apparaître sur un appui directionnel — c'est le cas central de
cette section, et il ne se vérifie qu'à la télécommande.

**R-262 · `OK` ouvre le titre** · S5-09 · TV
→ Le titre **et** la barre. Deux couches distinctes, deux gestes distincts.

**R-263 · Cinq secondes d'inactivité, pas cinq secondes** · S5-09 · TV
Ouvrir une couche, appuyer à nouveau au bout de trois secondes.
→ Le compte à rebours repart. Une couche qui disparaît au milieu d'une phrase
qu'on lit est rouge.

**R-264 · Sans `Range`, les touches le disent** · S5-09 · TV
Sur `/film-norange/` (§1), appuyer sur `RIGHT`.
→ La barre s'ouvre **inerte, avec la phrase**. Une touche qui a l'air morte est
une télécommande dont on croit qu'elle a cessé de fonctionner.

**R-265 · `BACK` depuis le lecteur revient à la fiche** · S5-09 · TV
→ La fiche, et son propre `BACK` remet la grille sur le bon film.

---

## 7. Le web

**R-270 · Tout est dans l'URL** · S5-10 · navigateur
Choisir une catégorie, chercher, changer de page.
→ `?categoryId=`, `?q=`, `?page=`. Recharger les ramène, **le bouton retour les
défait**, le lien est partageable.

**R-271 · Un film est une page** · S5-10 · navigateur
→ `/app/sources/{id}/vod/{filmId}`. C'est ce qui rend un film envoyable à
quelqu'un.

**R-272 · Le retour ramène là où on était** · S5-10 · navigateur
Ouvrir un film depuis la page 7 d'une catégorie, cliquer « Retour aux films ».
→ **Page 7 de cette catégorie.** Pas la tête du catalogue.

**R-273 · Tout marche sans JavaScript** · S5-10 · navigateur
Désactiver JavaScript. Parcourir, filtrer, chercher, paginer, ouvrir une fiche.
→ Les cinq fonctionnent. La lecture, elle, demande JavaScript et c'est attendu ;
tout le reste est la règle de la zone (`apps/web/AGENTS.md` §3).

**R-274 · Le lecteur n'est pas celui des chaînes** · S5-10 · navigateur
Onglet réseau ouvert.
→ **`hls.js` n'est pas chargé.** Un `<video src>` nu, et rien d'autre.

**R-275 · L'URL de flux n'est pas dans le HTML** · S5-10 · navigateur
Afficher la source de la page d'un film, avant et pendant la lecture.
→ **Aucune URL de flux.** Elle n'existe que dans une réponse `fetch`, en
`no-store`. Une URL dans le document est rouge et bloque la livraison.

**R-276 · Contenu mixte : nommé, pas silencieux** · S5-10 · navigateur
Depuis une page `https`, un panel en `http://`.
→ Le message de contenu mixte, **et** le renvoi vers les applications. C'est la
limite de l'ADR 0007, inchangée depuis S3-11.

**R-277 · Sans `Range`, sa propre phrase** · S5-10 · navigateur
Sur `/film-norange/` (§1).
→ Une phrase qui dit que le déplacement est impossible, **distincte** de
« indisponible ». Le curseur natif du navigateur est inerte, et c'est lui qui a
besoin d'être expliqué.

> **Une correction du document de sprint se vérifie ici.** Le sprint disait que le
> déplacement exigeait `Range` **et** un en-tête CORS. La seconde moitié est
> fausse pour un élément média : un `<video>` sans `crossorigin` émet une requête
> *no-cors* et lit les `206` quelle que soit l'origine. Pour le prouver : un
> serveur qui répond aux `Range` **sans** `Access-Control-Allow-Origin` doit
> permettre le déplacement. Si le déplacement échoue là, c'est la correction qui
> est fausse, et il faut le dire.

**R-278 · Les trois liens sont sur la page de la source** · S5-10 · navigateur
→ « Voir les chaînes », « Parcourir les films » et « Parcourir les séries », quelle
que soit la source. Chacun ouvre sur son catalogue, vide ou non.

**R-279 · On traverse entre les deux catalogues, et seulement quand il y en a deux**
· S5-10 · navigateur

Sur `/mixed.m3u` : ouvrir les chaînes, puis la bande **Chaînes · Films**.
→ Les deux onglets sont là, celui de la page courante marqué (`aria-current`), et
chacun mène à l'autre catalogue de **cette** source.

Sur `/playlist.m3u` : ouvrir les chaînes, puis l'onglet Films, puis l'onglet Séries.
→ **La bande est là aussi**, et chaque catalogue vide dit pourquoi : les films
annoncent une source qui ne porte que des chaînes, les séries annoncent qu'une
playlist M3U ne déclare ni saison ni épisode (`adr/0010`). **Deux phrases
distinctes**, parce que ce sont deux faits distincts — un panel Xtream sans séries
en reçoit une troisième.

> **Ce cas a été ajouté après un signalement d'usage**, et c'est le trou qui
> compte : les films étaient atteignables uniquement depuis la page de la source,
> par un lien en bas de celle-ci. Une fois dans les chaînes, **aucun chemin** ne
> menait aux films — il fallait remonter de deux niveaux et savoir que le lien
> existait.
>
> La fonction était livrée et invisible, et **aucun cas de cette recette ne le
> voyait** : ils vérifient tous les films une fois qu'on y est. Une recette qui
> commence chaque cas à la bonne page ne teste jamais le chemin pour y arriver.

---

## 8. La reprise, entre les appareils

Cette section est la moitié de la valeur du sprint et **elle ne se joue que sur
plusieurs surfaces**. Tout le reste pourrait être vert sur trois appareils qui ne
se parlent pas.

**R-280 · Ce qui est commencé sur le téléphone se reprend sur la télévision** ·
S5-11 · téléphone → TV
Lire un film vingt minutes sur le téléphone, quitter. Ouvrir la télévision.
→ Le film est dans la puce **Reprendre**, et sa fiche propose *Reprendre à 20:14*.
La minute près n'est pas exigée : trente secondes est le pas d'écriture.

**R-281 · Les deux boutons, et aucun n'est pressé à votre place** · S5-11 · les
trois surfaces
→ *Reprendre à …* **et** *Recommencer*, tous deux visibles. Une lecture qui
redémarre seule à la position sauvegardée est **rouge** : c'est une bonne idée
jusqu'au jour où l'on veut revoir le début.

**R-282 · *Recommencer* recommence vraiment** · S5-11 · les trois surfaces
→ La lecture part à zéro, et la position sauvegardée n'est pas écrasée avant
d'avoir dépassé zéro.

**R-283 · Un film terminé quitte le rail** · S5-11 · les trois surfaces
Regarder un film au-delà de 95 % de sa durée, revenir à la grille.
→ Il n'est plus dans « Reprendre », et sa fiche montre **un** bouton.

**R-284 · Sans durée connue, il n'en sort jamais** · S5-11 · les trois surfaces
Sur un film dont la source ne donne pas de durée, aller jusqu'à la fin.
→ Il **reste** dans « Reprendre ». C'est **vert**, et c'est la décision : un film
qui s'attarde est un agacement, un film qui disparaît avant la fin est une perte.

**R-285 · La barre de position ne se dessine que si la durée est connue** · S5-11 ·
téléphone + web
→ Sur un film sans durée, **aucune barre** sur l'affiche. Une barre presque vide
dirait qu'on a à peine commencé.

**R-286 · Quitter le lecteur écrit la position** · S5-11 · les trois surfaces
Lire trente secondes, quitter immédiatement — sans attendre le tick suivant.
→ La position est enregistrée. C'est le cas qui attrape l'ordre des opérations :
arrêter le lecteur remet la position à zéro, donc écrire après serait écrire zéro.

**R-287 · Ouvrir puis fermer aussitôt ne perd rien** · S5-11 · les trois surfaces
Sur un film ayant une position sauvegardée : ouvrir la fiche, lancer, fermer dans
la seconde.
→ **La position d'origine est intacte.** Une position écrasée par zéro est rouge —
c'est exactement le scénario pour lequel la reprise existe.

**R-288 · Rien n'est enregistré pour une chaîne** · S5-11 · les trois surfaces
Regarder une chaîne en direct dix minutes, puis regarder le rail Films.
→ **Rien.** Aucune ligne de progression pour une chaîne. Vérifiable sur l'API ; le
lecteur est partagé entre les deux usages, et c'est le genre d'appel qu'il fait
tout seul.

**R-289 · Le rail est vide hors ligne** · S5-11 · téléphone + TV
Couper le réseau, ouvrir l'onglet Films.
→ La grille est là, **le rail est absent**, et un film ouvert repart du début.
C'est **vert** et c'est écrit : une position s'écrit sur un appareil et se lit sur
un autre, donc elle vit sur le serveur et nulle part ailleurs.

---

## 9. Cas transverses

Ceux-là ne portent aucune tâche et bloquent quand même la Definition of Done.

**R-290 · La mise à jour n'efface pas le catalogue** · S5-07 · téléphone + TV
Partir d'un build **antérieur au sprint 5** avec un catalogue synchronisé,
installer par-dessus (`adb install -r`), rouvrir.
→ Chaînes, catégories et favoris **toujours là**. Un catalogue vidé signifie que
`MIGRATION_4_5` manque ou qu'un `fallbackToDestructiveMigration` s'est glissé
quelque part.

**R-291 · Les deux catégories ne se vident pas l'une l'autre** · S5-07 · les trois
surfaces
Sur une source portant chaînes **et** films : ouvrir les films, actualiser, revenir
aux chaînes.
→ La bande de catégories des chaînes est **intacte**. Puis l'inverse. Les deux
types partagent une table ; une bande qui se vide en changeant d'onglet est rouge,
et ne se voit que sur une source qui porte les deux — c'est-à-dire la plupart.

**R-292 · La mémoire tient sur une grille d'affiches** · S5-07 · TV
Sur la box la plus modeste disponible, faire défiler plusieurs centaines de films,
puis revenir.
→ Aucun ralentissement croissant, aucun redémarrage de l'application. Une affiche
pèse une cinquantaine de fois le bitmap d'un logo, et c'est le seul écran du
produit où cela compte.

**R-293 · Aucun secret dans les journaux** · les deux surfaces Android
Dérouler un parcours complet de films, application en `DEBUG`, puis :

```bash
adb logcat -d | grep -iE "m3u8?|\.mp4|\.mkv|player_api|password|Bearer |refresh_token"
```

→ **Aucune ligne.** L'extension de fichier est dans le motif parce que ce sprint a
ajouté un second type d'URL de flux.

**R-294 · Les deux langues tiennent** · les trois surfaces
Rejouer les écrans de films en FR puis en EN.
→ Aucun texte tronqué, aucun débordement, aucune chaîne non traduite. À regarder
en priorité : la phrase de refus de déplacement, et « Reprendre à 1:47:03 » sur une
puce de télévision.

**R-295 · La marque d'overscan** · S5-09 · TV
→ Aucun élément focalisable ni aucun texte dans les 5 % rognés. L'image, elle, doit
atteindre les bords.

**R-296 · Aucun contenu réel dans le livrable**
Relire les captures et les données de test avant de clore la session.
→ Aucun titre d'œuvre existante, **aucune affiche**, aucune URL réelle.

---

## 10. Critère de sortie

La recette est verte quand **tous les cas ci-dessus passent, dans les deux
langues, sur appareils réels, télécommande en main pour la section 6**.

Un cas rouge bloque US-13, sauf R-275, R-293 et R-296 qui bloquent la livraison
quelle que soit la story.

**Trois sections ne s'allègent pas**, et pour trois raisons différentes :

- **La section 3** est la seule qui vérifie l'ADR 0009 sur un vrai catalogue, et
  ses deux cas d'erreur volontaire (R-203, R-204) sont le seul endroit où cette
  décision se regarde plutôt qu'elle ne se corrige.
- **La section 8** est la seule preuve que ce sprint a livré ce qu'il annonce : une
  reprise qui traverse les appareils.
- **R-261** — `RIGHT` qui n'ouvre jamais le titre — est la seule chose de ce sprint
  qui ne se vérifie qu'avec une télécommande dans la main.

Le rapport de session note, pour chaque cas : vert, rouge, ou **non joué avec la
raison**. « Pas de serveur sans `Range` » et « installé à neuf » sont des raisons
recevables et traçables. « Probablement bon » ne l'est pas.

---

## 11. Ce que cette recette ne couvre pas

Écrit ici plutôt que découvert plus tard.

- **`IngestionService` avait zéro test automatisé ; il en a cinq depuis le
  31 août 2026.** Cette ligne est gardée plutôt que supprimée parce que le trou a
  coûté un vrai bug — la contrainte `source_sync_step_check` a fait finir en
  `ERROR` toute source Xtream pendant deux sprints — et que le signaler ici deux
  fois n'a servi à rien tant que personne ne l'a fermé.
- **Le banc sert tout ce que cette recette demande depuis le 31 août 2026.** Les
  six cas de lecture qui étaient « non joué » sont jouables : `/film/` sert un vrai
  MP4 (H.264 + AAC, six secondes), et `/film-norange/` sert le même fichier depuis
  un serveur qui **ignore** `Range` — `max_ranges 0`, une directive. Cette ligne
  est gardée parce que ces cas ont passé deux sprints en « non joué » et que
  personne ne devrait avoir à redécouvrir pourquoi.
- **Aucun sélecteur de source sur Android.** Les deux applications lisent la
  première source du compte. C'est ce qui rend R-210 et R-250 lourds à jouer, et
  c'est une limite du produit plutôt que de cette recette — antérieure à ce sprint,
  et pas chiffrée.
- **La lecture web reste directe ou refusée** (ADR 0007). Elle ne marche pas chez
  tous les fournisseurs, et cette recette vérifie que l'échec est **nommé** — pas
  qu'il n'arrive pas.
