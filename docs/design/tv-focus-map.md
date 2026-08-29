# Carte du parcours de focus — Android TV

Livrable de `S2-13`, au même titre que les écrans, et tenu à jour par chaque tâche
qui ouvre une surface de focus — `S4-07` pour la couche modale, `S5-09` pour les
trois surfaces des films.

## À quoi ça sert

Le défaut le plus courant des applications TV n'est pas une erreur de logique :
c'est **un contrôle qu'aucune séquence de touches n'atteint**. Il ne plante pas,
il ne lève rien, aucun test unitaire ne le voit, et il ne se découvre qu'avec une
télécommande en main — souvent après la publication.

Ce document existe pour que la question se pose **avant** l'écran plutôt qu'après :
pour chaque surface, quel élément a le focus à l'arrivée, et où mène chaque
direction depuis chaque zone. Une case vide dans un tableau ci-dessous est un
défaut, pas une omission de rédaction.

## Les règles qui valent partout

Elles viennent de `docs/architecture.md` §3 et d'`apps/android/AGENTS.md` §6, et
sont rappelées ici parce que c'est le document qu'on lit en concevant un écran.

1. **Tout écran a au moins une cible focalisable.** Sans ça `BACK` devient la seule
   touche qui fait quelque chose, et l'utilisateur est piégé. La seule exception est
   un écran sans aucun contrôle — l'activation — et elle est justifiée ligne à ligne
   plus bas.
2. **La signature de focus est échelle + bordure + élévation, ensemble.** Jamais une
   simple variation de couleur : elle disparaît sur une dalle mal calibrée et pour un
   daltonien. `Modifier.lumoTvFocus` est le seul endroit où elle est écrite.
3. **Focus ≠ sélection.** Le focus est où se trouve la télécommande ; la sélection
   est ce qui est ouvert. Les confondre donne l'impression que l'application a
   navigué alors qu'on ne fait que parcourir.
4. **Le focus suit la disposition visuelle.** Compose cherche le focus dans la
   direction de la touche : c'est pour ça que la coquille TV est un `Row` (rail à
   gauche, contenu à droite) et non un `Scaffold` avec une barre en bas.
5. **Overscan 5 % sur les bords externes.** Un élément focalisable dans la bande
   rognée est un élément que certains utilisateurs ne verront jamais, même en
   l'atteignant.
6. **`clickable` suffit.** Il rend focalisable *et* lie la touche centrale. Ajouter
   `focusable()` en plus crée deux cibles sur un seul élément.

---

## Coquille — rail latéral

Présent sur toutes les surfaces **sauf quand personne n'est connecté**, où il est
masqué : il proposerait le catalogue et les réglages d'un compte qui n'existe pas,
et sur une TV il est le premier endroit où la D-pad atterrit.

| Depuis | UP | DOWN | LEFT | RIGHT | OK | BACK |
|---|---|---|---|---|---|---|
| Un élément du rail | élément précédent | élément suivant | — (bord) | entre dans le contenu | ouvre la destination | quitte l'application |

Trois éléments, quatre quand la source propose des films : **Chaînes · [Films] ·
Source · Réglages**. Séries et recherche restent des placeholders et restent hors du
rail — sur une TV chaque entrée de plus est un `DOWN` de plus entre le spectateur et
ce qu'il vient chercher.

**Films n'apparaît que si la source en a** (US-13, `S5-09`). L'argument du téléphone
pèse davantage ici : une entrée de rail est un arrêt obligatoire en descendant, donc
une porte sur une pièce vide coûte un appui à chaque spectateur, à chaque trajet.
La réponse vient de `CatalogueSections`, qui la tient d'une seule requête, et elle
est `false` tant que rien n'a dit le contraire — un rail qui gagne une entrée vaut
mieux qu'un rail qui retire un arrêt sous la D-pad.

---

## Activation (`AuthTvScreen`) — écran d'entrée, déconnecté

**Focus à l'arrivée : aucun. C'est le flux, pas un oubli.**

Il n'y a aucun contrôle sur cet écran : on lit un code, on approuve sur un
téléphone, et le téléviseur avance seul quand c'est fait. Ajouter un bouton pour
que quelque chose puisse prendre le focus serait ajouter une chose à presser sans
raison, sur un écran dont tout l'argument est que la télécommande est le mauvais
instrument.

La règle 1 est donc respectée par sa raison plutôt que par sa lettre : l'utilisateur
n'est pas piégé, parce qu'il n'y a rien à atteindre. Le rail étant masqué, `BACK`
reste ce qu'il est toujours sur une TV — la sortie de l'application.

| Depuis | Toutes directions | OK | BACK |
|---|---|---|---|
| L'écran | rien ne bouge | rien | quitte l'application |

---

## Chaînes (`LiveTvScreen`) — l'écran principal

**Focus à l'arrivée : la première carte de la grille — sauf au retour du lecteur,
où c'est la chaîne qu'on regardait (US-10, voir *Lecteur de chaîne* plus bas).**

Pas la bande de catégories : quelqu'un qui allume sa télévision veut une chaîne, et
l'étagère où il se trouve déjà est la bonne. Atteindre les catégories coûte un
`UP` ; atteindre une chaîne depuis les catégories aurait coûté un `DOWN` **plus une
décision que personne n'a demandée**.

| Depuis | UP | DOWN | LEFT | RIGHT | OK |
|---|---|---|---|---|---|
| Carte de chaîne, 1re colonne | bande (si 1re ligne) / carte au-dessus | carte en dessous | **rail** | carte suivante | lance la chaîne |
| Carte de chaîne, ailleurs | carte au-dessus / bande | carte en dessous | carte précédente | carte suivante | lance la chaîne |
| Puce de la bande | — (bord haut) | grille | puce précédente | puce suivante | filtre la grille |

**La bande porte les groupes de favoris depuis `S4-06`, et ce tableau n'a pas
bougé.** C'est le résultat qu'on espérait et il a été vérifié ligne à ligne, pas
supposé : un groupe filtre la grille exactement comme une catégorie, donc sa puce
se comporte comme une puce. Ordre de la bande : **Toutes · Repris · [groupes] ·
[catégories de la source]**, les deux derniers blocs séparés par un intervalle et
non par un libellé de section — la bande n'a pas la hauteur d'une ligne de titres.

**« Repris » est la puce des chaînes vues récemment** (`S4-08`), en deuxième
position parce que c'est ce vers quoi quelqu'un qui allume sa télévision tend le
plus souvent — et la seule étagère qu'il n'a pas eu à construire. Elle n'apparaît
pas tant que rien n'a été regardé. C'est aussi la forme retenue contre le *rail*
que décrivait [`api-gaps.md`](./api-gaps.md) M5 : le rail se défendait sur le fond,
mais il aurait ouvert un second mécanisme sur un écran qui en a déjà un.

**Un groupe vide n'a pas de puce.** Filtrer sur rien produit une grille blanche au
bout d'un `OK`, ce qui à trois mètres ressemble à une panne et pas à une étagère
vide. Le téléphone peut se permettre d'écrire « ce groupe est vide » ; la bande
n'a la place de rien écrire.

`BACK` depuis n'importe où sur cet écran quitte l'application : c'est la
destination de départ quand une session existe et qu'une source est prête.

**Deux points qui se vérifient à la télécommande et nulle part ailleurs :**

- **La grille a deux lignes, pas trois.** Avec des cartes assez grandes pour être
  lues à trois mètres, une troisième ligne tombe sous la marge d'overscan sur une
  dalle 1080p — et une ligne que personne ne voit est une ligne que personne ne
  focalise.
- **Une carte de remplacement n'est pas focalisable.** Paging dessine des fenêtres
  non chargées à la bonne taille pour que la grille garde sa forme ; si elles
  prenaient le focus, la D-pad s'arrêterait sur des culs-de-sac qui apparaissent et
  disparaissent au défilement.

**« Toutes » est la première puce** et l'état d'ouverture : un catalogue qui
démarre à l'intérieur de la première catégorie de quelqu'un est un catalogue qui
cache le reste.

---

## Groupes de favoris (`LumoTvFavoriteGroupSheet`) — couche modale

Livrée par `S4-07`. Ouverte par un **appui long sur `OK`** depuis une carte de la
grille, et c'est la seule surface de ce document qui se superpose à une autre.

**Focus à l'arrivée : la première ligne.** Toute surface doit en avoir une, sinon
`BACK` devient la seule touche qui fait quelque chose — c'est la règle 1 de ce
document, et une couche modale n'y échappe pas.

| Depuis | UP | DOWN | LEFT | RIGHT | OK | BACK |
|---|---|---|---|---|---|---|
| Ligne de groupe, 1re | — (bord haut) | groupe suivant | — | — | ajoute ou retire | ferme, sans rien changer |
| Ligne de groupe, autre | groupe précédent | groupe suivant / — (bord bas) | — | — | ajoute ou retire | ferme, sans rien changer |

**Trois décisions qui se voient à la télécommande et nulle part ailleurs :**

- **`LEFT` et `RIGHT` ne font rien, exprès.** Il n'y a rien à côté de cette liste,
  et un focus qui s'échapperait latéralement atterrirait sur la grille *pendant*
  que la feuille est ouverte — deux couches actives, et l'utilisateur ne saurait
  plus laquelle reçoit ses touches.
- **Pas de bouclage aux extrémités.** Une liste qui reboucle n'a pas de fin, et
  personne ne peut savoir qu'il a tout vu.
- **La case n'est pas une seconde cible.** `OK` sur la ligne bascule le groupe. Une
  case à cocher focalisable doublerait la longueur du parcours dans une liste dont
  on cherche à sortir.

**Le geste qui l'ouvre, et pourquoi ce geste-là.** `OK` court lance la chaîne —
c'est US-10 et ça ne bouge pas. Un second bouton dessiné sur chaque carte donnerait
**deux cibles de focus par carte** dans une grille qui en compte des centaines : le
parcours horizontal doublerait de longueur pour une action qu'on fait une fois par
chaîne dans une vie. Le cœur affiché sur une carte favorite est un **état dessiné**,
jamais focalisable.

---

## Films (`VodTvScreen`) — la grille d'affiches

Livrée par `S5-09`. **N'existe dans le rail que si la source propose des films**
(US-13) : une entrée de rail est un arrêt obligatoire en descendant, donc une porte
sur une pièce vide coûte un appui à chaque spectateur, à chaque trajet, pour une
pièce que la plupart des playlists M3U n'ont pas.

**Focus à l'arrivée : la première affiche — sauf au retour de la fiche, où c'est le
film qu'on regardait.**

C'est le raisonnement de *Chaînes*, mot pour mot : celui qui ouvre les films veut un
film, et l'étagère où il se trouve déjà est la bonne.

| Depuis | UP | DOWN | LEFT | RIGHT | OK |
|---|---|---|---|---|---|
| Affiche, 1re colonne | bande de catégories | — (bord bas) | **rail** | affiche suivante | ouvre la **fiche** |
| Affiche, ailleurs | bande de catégories | — (bord bas) | affiche précédente | affiche suivante | ouvre la **fiche** |
| Puce de la bande | — (bord haut) | grille | puce précédente | puce suivante | filtre la grille |

`BACK` depuis n'importe où sur cet écran revient aux chaînes, qui sont la
destination de départ.

**`OK` ouvre la fiche, il ne lance pas le film.** C'est le seul endroit où cet écran
s'écarte de la grille des chaînes, et c'est ce qu'est un film : une chaîne se lance,
un film se **choisit**, et choisir demande une année, une durée et un synopsis
qu'aucune carte n'a la place de porter.

**Pas d'appui long.** La grille des chaînes s'en sert pour classer un favori ; un
film n'a pas de favori en v1, et un geste qui ne fait rien est pire que pas de geste.

**Une ligne, pas deux, et c'est de l'arithmétique.** Une carte de chaîne est une
bande large et basse ; une affiche est un portrait 2:3. Après overscan il reste
environ 480 dp de hauteur utile sur une dalle 1080p, dont 150 pris par le titre et
la bande. Deux lignes d'affiches dans ce qui reste mettent chacune autour de 100 dp
de large — sous la taille à laquelle on reconnaît un film à trois mètres, et une
affiche qu'on ne reconnaît pas est une carte qui a cessé de faire son travail.

C'est exactement l'arbitrage que la tâche nomme : **le nombre de colonnes se fixe
sur la lisibilité à trois mètres, pas sur ce qui rentre.**

**La bande ne porte pas les groupes de favoris**, contrairement à celle des
chaînes : un groupe contient des chaînes (US-12), donc sa puce filtrerait cette
grille sur rien — l'étagère vide que les règles de l'écran des chaînes refusent
déjà.

**Une carte de remplacement n'est pas focalisable**, comme dans la grille des
chaînes : Paging dessine les fenêtres non chargées à la bonne taille pour que la
grille garde sa forme, et une carte sans film derrière serait un cul-de-sac qui
apparaît et disparaît au défilement.

---

## Fiche d'un film (`VodDetailTvScreen`)

Livrée par `S5-09`, et **c'est une nouvelle surface de focus** : d'où cette section.

**Focus à l'arrivée : le bouton *Lire*.**

**Une seule cible focalisable, exprès.** Celui qui a appuyé sur `OK` depuis une
affiche a déjà décidé ; l'écran est là pour confirmer ce qu'il a choisi, pas pour le
faire voyager. Tout le reste est du texte, et du texte qui prend le focus sur une
télévision est du texte qu'il faut dépasser en appuyant.

Il n'y a pas de bouton *Retour* dessiné : `BACK` est une touche physique, et en
dessiner un serait une seconde cible pour ce que la télécommande fait déjà.

| Depuis | UP | DOWN | LEFT | RIGHT | OK | BACK |
|---|---|---|---|---|---|---|
| Bouton *Lire* | — (seule cible) | — (seule cible) | rail | — | lance le film | **grille, sur ce film** |

**`BACK` revient sur le film qu'on regardait dans la grille**, pas en tête. C'est la
règle d'US-10 et elle vaut ici pour la raison qui l'a fait écrire : un catalogue de
trente mille affiches qui revient en haut a perdu la place du spectateur, et le film
qu'il vient de quitter est le plus difficile de tous à retrouver. La fiche écrit son
`filmId` dans le `SavedStateHandle` de l'entrée du dessous avant de dépiler ; la
grille le lit, y place le focus et l'efface.

**Le synopsis est plafonné à dix lignes, et c'est une limite écrite plutôt qu'un
oubli.** Presque tous les synopsis qu'un panel IPTV porte sont plus courts. La
solution complète serait un bloc défilant, ce qui sur une télévision veut dire **une
seconde zone de focus dont le seul rôle est de faire défiler du texte** — une zone
que ce tableau devrait décrire comme un endroit où `OK` ne fait rien. Un synopsis
très long est donc tronqué ici et complet sur le téléphone.

---

## Lecteur de film (`VodPlayerTvScreen`)

Livré par `S5-09`. C'est le lecteur de `S2-14` avec **la seule chose qu'un film
demande et qu'une chaîne ne peut pas avoir** : le déplacement. Tout le reste est
délibérément identique — rien sur l'image au repos, la surface focalisable pour que
`OK` arrive quelque part, cinq secondes d'*inactivité* avant qu'une couche parte.

**Focus à l'arrivée : la surface vidéo elle-même**, pour la raison écrite sur le
lecteur de chaînes : un écran plein sans cible focalisable est un écran où seul
`BACK` répond.

| Depuis | UP/DOWN | LEFT | RIGHT | OK | BACK |
|---|---|---|---|---|---|
| La surface, au repos | — | recule de 10 s, ouvre **la barre seule** | avance de 10 s, ouvre **la barre seule** | ouvre la **barre d'information** | retour à la fiche |
| La surface, une couche visible | — | recule de 10 s, relance les 5 s | avance de 10 s, relance les 5 s | relance les 5 s | retour à la fiche |
| Bouton *Réessayer* (échec) | — (seule cible) | — | — | relance la lecture | retour à la fiche |

**Deux couches, et c'est tout le contenu de cette tâche côté lecteur.** La consigne
est explicite : `LEFT` et `RIGHT` déplacent et **ne doivent pas ouvrir l'overlay
d'information par accident**. Or les deux touches sont à un geste l'une de l'autre
sur toutes les télécommandes du monde. Donc :

- **`OK` ouvre la barre d'information** — le titre du film, comme le nom de la
  chaîne sur l'autre lecteur, avec la barre de progression dessous ;
- **`LEFT`/`RIGHT` ouvrent la barre de progression seule.** Pas de titre, pas de
  panneau. Quelqu'un qui saute une scène n'a pas demandé qu'on lui rappelle ce qu'il
  regarde.

**Les deux touches sont interceptées en `onPreviewKeyEvent`**, et ce n'est pas un
détail d'implémentation : sans ça Compose les traite comme une recherche de focus,
ne trouve rien sur une image plein écran, et les laisse tomber en silence.

**La barre est dessinée, jamais focalisable.** C'est un **affichage**, pas un
contrôle : `LEFT` et `RIGHT` sont liés à l'écran et fonctionnent que la barre soit
là ou non, donc un curseur focalisable serait une seconde cible pour des touches qui
font déjà le travail — et il disputerait la D-pad à une image qui ne doit écouter
que ces deux touches, `OK` et `BACK`.

**Quand le serveur refuse de se déplacer, les touches le disent au lieu de ne rien
faire.** Se déplacer dans un fichier progressif exige que le serveur de
l'utilisateur réponde aux requêtes `Range`, et beaucoup de panels ne le font pas.
`LEFT` et `RIGHT` ouvrent alors quand même la barre, dessinée là, inerte, **avec la
phrase à côté**. Une touche qui a l'air morte est une télécommande dont on croit
qu'elle a cessé de fonctionner.

**Dix secondes par appui.** Le pas sur lequel tous les lecteurs TV se sont arrêtés,
et la raison est qu'une télécommande n'a pas de curseur : l'unité doit être assez
petite pour tomber sur une réplique et assez grande pour qu'une coupure publicitaire
se franchisse en une poignée d'appuis plutôt qu'en une minute.

**Ce qui ne se vérifie qu'à la télécommande, sur cet écran :** qu'un appui sur
`RIGHT` n'a **jamais** fait apparaître le titre, et que sur un serveur sans `Range`
la phrase apparaît au premier appui plutôt qu'au bout de plusieurs.

---

## Source (`SourceTvScreen`) — placeholder

La carte entière est la cible focalisable, et son texte dit ce que fait OK. Un
bouton dessiné à l'intérieur d'une carte focalisable donnerait deux cibles à la
D-pad sur un écran qui a une seule chose à faire.

| Depuis | UP/DOWN | LEFT | RIGHT | OK | BACK |
|---|---|---|---|---|---|
| La carte | — | rail | — | — | retour aux chaînes |

---

## Réglages (`SettingsTvScreen`)

Même forme, et une action réelle : la déconnexion. C'est aussi la seule façon de
remettre un téléviseur à son écran d'activation sans réinitialiser la box — ce dont
la recette a besoin, et ce dont a besoin quelqu'un qui passe son appareil à un
autre.

| Depuis | UP/DOWN | LEFT | RIGHT | OK | BACK |
|---|---|---|---|---|---|
| La carte | — | rail | — | déconnecte | retour aux chaînes |

Après la déconnexion, le graphe est reconstruit sur l'activation et la pile est
vidée : `BACK` ne doit pas ramener dans un compte qui n'existe plus.

---

## Lecteur de chaîne (`PlayerTvScreen`)

**Focus à l'arrivée : la surface vidéo elle-même.**

C'est le piège classique du lecteur TV : **les contrôles s'effacent, et la D-pad
doit les rappeler.** Un lecteur plein écran sans cible focalisable pendant que les
contrôles sont masqués est un écran où seul `BACK` répond — le cas que la règle 1
interdit. La surface est donc `focusable()` bien qu'elle ne soit pas un contrôle :
elle ne fait rien d'autre que donner à la télécommande un endroit où être, pour que
`OK` arrive quelque part.

Au repos, **rien n'est dessiné sur l'image** : ni barre, ni dégradé, ni logo. C'est
l'écran devant lequel on reste une heure, et tout ce qui est posé sur l'image est
posé sur ce pour quoi on est venu.

| Depuis | UP/DOWN/LEFT/RIGHT | OK | BACK |
|---|---|---|---|
| La surface, au repos | — (rien à atteindre) | ouvre la barre d'information | retour aux chaînes |
| La surface, barre visible | — | relance les cinq secondes | retour aux chaînes |
| Bouton *Réessayer* (échec) | — (seule cible) | relance la lecture | retour aux chaînes |

Le rail est masqué sur cette destination, comme sur l'activation et pour une raison
de plus : c'est une cible focalisable qui disputerait la D-pad à l'image, sur un
écran qui ne doit écouter que `OK` et `BACK`.

**Cinq secondes d'inactivité, pas cinq secondes.** Chaque appui relance le
compte à rebours, pour que quelqu'un qui lit lentement le nom de la chaîne ne se le
fasse pas retirer en cours de phrase.

**`BACK` ramène la grille positionnée sur la chaîne qu'on regardait** (US-10). Le
lecteur écrit son `channelId` dans le `SavedStateHandle` de l'entrée du dessous
avant de dépiler ; la grille le lit, y place le focus et l'efface. Sans cela, le
retour repart en haut d'un catalogue de quinze mille lignes, et la chaîne qu'on
vient de quitter est la plus difficile à retrouver de toutes.

Deux choses qui se vérifient à la télécommande et nulle part ailleurs : que la barre
part bien **cinq secondes après le dernier appui** et non après le premier, et que le
retour tombe sur la bonne carte quand la chaîne regardée était en bas d'une grille
qu'il a fallu faire défiler.

---

## Comment on vérifie

À la télécommande, sur un appareil réel. C'est la Definition of Done du sprint et
elle ne s'allège pas : **une souris produit du survol, pas du focus**, et le survol
masque exactement les défauts que ce document existe pour éviter.

Le filet de sécurité sur émulateur, qui ne remplace rien :

```bash
adb shell input keyevent 19  # UP
adb shell input keyevent 20  # DOWN
adb shell input keyevent 21  # LEFT
adb shell input keyevent 22  # RIGHT
adb shell input keyevent 23  # OK
```
