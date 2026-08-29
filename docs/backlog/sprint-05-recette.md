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
chaînes. C'est le seul moyen de jouer R-210 et R-211, qui vérifient une **absence** :
l'onglet et l'entrée de rail ne doivent pas exister sur une source sans films. Avec
une seule source, ces deux cas sont « non joué », pas « vert ».

**Un fichier de film qui se lit vraiment**, et le banc n'en fournit pas. Les
fixtures `/film/*.mp4` et `/film/*.mkv` sont **des octets MPEG-TS sous un nom de
film** : c'est écrit dans `entrypoint.sh` et c'est assez pour l'ADR 0009, qui
classe sur l'URL, donc assez pour toute la section 3. Ce n'est **pas** un
conteneur qu'un navigateur décode. Les cas de lecture réelle (R-240 → R-244,
R-260 → R-265, R-274 → R-277) demandent donc un vrai MP4 progressif servi en
`http(s)` — n'importe quel fichier généré localement fait l'affaire, du moment
qu'il ne vient de nulle part (§2).

**Un serveur qui refuse les requêtes `Range`.** Le banc n'en a pas non plus : nginx
répond `206` correctement, ce qui est la bonne nouvelle et le problème. Les cas
**R-242, R-264 et R-277** vérifient la moitié la plus délicate de ce sprint et
demandent un serveur qui ignore l'en-tête. Un `python3 -m http.server`
**n'y suffit pas** — il gère `Range`. Il faut un petit serveur qui réponde `200`
avec le corps entier quel que soit l'offset ; sans lui, ces trois cas sont
« non joué avec la raison », et c'est une raison recevable et traçable.

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

> **Ce cas n'a aucun test automatisé**, et c'est signalé plutôt que masqué : rien
> dans le dépôt n'exerce `IngestionService`. C'est le cas de recette le plus
> important de la section.

---

## 4. La grille — téléphone

**R-210 · Une source sans films ne montre pas d'onglet** · S5-08 · téléphone
Compte n'ayant que `/playlist.m3u`.
→ **Il n'y a pas d'onglet Films.** Quatre entrées dans la barre. Une porte sur une
pièce vide est rouge : c'est une exigence *négative*, et le genre qui se défait
sans que rien ne casse.

**R-211 · Une source avec des films le montre** · S5-08 · téléphone
Compte ayant `/mixed.m3u`.
→ L'onglet Films est là, **en deuxième position**, après les chaînes et avant les
favoris.

**R-212 · L'onglet arrive, il ne disparaît pas** · S5-08 · téléphone
Lancer l'application et regarder la barre pendant la première seconde.
→ La barre se dessine avec quatre entrées puis en gagne une. **Jamais l'inverse** :
une barre qui retire une cible sous le pouce est rouge.

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
Sur le serveur sans `Range` (§1).
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

**R-250 · L'entrée Films n'existe que s'il y a des films** · S5-09 · TV
→ Trois entrées de rail sur une source sans films, quatre sinon, Films en seconde
position. Sur une télévision une entrée de rail est un arrêt obligatoire en
descendant : une porte sur une pièce vide coûte un appui à chaque trajet.

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
Sur le serveur sans `Range` (§1), appuyer sur `RIGHT`.
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
Sur le serveur sans `Range` (§1).
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

**R-278 · L'entrée Films n'apparaît que s'il y a des films** · S5-10 · navigateur
→ Le lien « Parcourir les films » est absent de la page d'une source sans films.

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

- **`IngestionService` n'a aucun test automatisé.** R-206 est sa seule
  vérification, et elle est manuelle. C'est de la dette, pas un oubli de cette
  recette — voir [`dette.md`](./dette.md).
- **Le banc d'essai ne sert pas de vrai fichier de film ni de serveur sans
  `Range`.** Six cas en dépendent (§1). Les ajouter au banc est un chiffrage à
  faire ; en attendant, ils se jouent avec un serveur improvisé ou ne se jouent
  pas.
- **La lecture web reste directe ou refusée** (ADR 0007). Elle ne marche pas chez
  tous les fournisseurs, et cette recette vérifie que l'échec est **nommé** — pas
  qu'il n'arrive pas.
