# Plan de démo — Sprint 4

La Definition of Done exige une démo sur appareils réels, téléphone **et**
télévision pilotée à la télécommande. Ce document est le déroulé de celle-ci.

> **Écrit après coup, et il faut le savoir en l'ouvrant.** Les sprints 2, 5 et 6 ont
> eu leur plan de démo au moment où ils se terminaient ; celui-ci manquait. Il est
> donc rédigé contre le code d'**aujourd'hui**, pas contre celui de la fin du
> sprint 4 — deux sprints ont passé depuis et l'un d'eux a changé un des écrans dont
> il est question ici (acte 7). Là où ça compte, c'est dit.
>
> Il reste jouable et il vaut la peine de l'être : **la moitié de ce sprint ne se
> prouve que sur trois appareils à la fois**, et rien dans ce dépôt ne peut
> l'établir à sa place.

**Durée visée : 15 min 30**, questions non comprises — la somme des actes ci-dessous.
Le fil :

> Je range mes chaînes dans mes propres groupes, avec mes propres noms. Et le
> rangement me suit d'un appareil à l'autre, parce qu'il appartient à mon compte et
> pas à mon téléphone.

**S'il faut couper**, c'est dans cet ordre : l'acte 8 (ce qui ne se voit pas), puis
l'acte 5 (« Repris »). **Jamais l'acte 6** — c'est la Definition of Done du sprint,
littéralement, et le seul acte qu'aucun test de ce dépôt ne remplace.

---

## Matériel

- Un téléphone Android physique, **écran dupliqué** sur le vidéoprojecteur.
- Une box ou un téléviseur Android TV physique, **avec sa télécommande**. Pas
  d'émulateur : l'acte 4 porte sur un appui long et sur des touches qui ne doivent
  **rien** faire, et une souris n'a ni l'un ni les autres.
- Un navigateur, sur le même compte.
- L'API et le banc d'essai démarrés et vérifiés.

### Le prérequis qu'on oublie, et il coûte deux actes

**Deux sources enregistrées, pas une.** Un groupe qui mélange deux abonnements est
le cas d'usage même de la fonction — c'est ce qui distingue un groupe d'une
catégorie — et **la ligne « Depuis *[source]* » du téléphone ne s'affiche qu'à
partir de deux sources**. Avec une seule, l'acte 2 perd son point et l'acte 6 perd
la moitié du sien.

Le banc suffit : `/playlist.m3u` pour la première, et **une seconde source pointant
le même banc sous un autre libellé** fait parfaitement l'affaire. Ce qui compte est
qu'il y en ait deux, pas qu'elles diffèrent.

> C'est le seul sprint de la série dont le banc d'essai fournit **tout** ce dont la
> démo a besoin. Les sprints 5 et 6 n'ont pas cette chance, et leurs plans le disent
> en tête.

---

## Préparation, trente minutes avant

1. Dérouler le parcours en entier, une fois, seul.
2. **Deux sources sur le compte**, synchronisées **sur les trois surfaces**. Une
   source jamais synchronisée sur un appareil emprunte le chemin de repli `?ids=` —
   c'est un vrai cas et c'est l'acte 8, mais on ne le découvre pas devant la salle.
3. **Effacer les favoris et les groupes du compte de démo.** L'acte 1 commence sur
   un compte vierge : le groupe par défaut doit être créé **devant** la salle, c'est
   la moitié de son intérêt.
4. **Effacer l'historique de lecture** (`/me/recent-channels`). L'acte 5 doit partir
   de rien.
5. **Repérer trois chaînes** dans la grille du téléphone : une pour l'acte 1, une
   pour l'acte 2 (celle de la **seconde** source, pour la ligne « Depuis »), une
   pour l'acte 5.
6. Mettre les appareils en français.
7. Couper les notifications.

---

## Déroulé

### Acte 1 — Le cœur, et ce qu'il ne fait pas attendre · 2 min · téléphone

Ouvrir la liste des chaînes. **Appui court sur un cœur.**

> « Il s'est rempli tout de suite. Pas après l'aller-retour au serveur — tout de
> suite. »

Marquer le point, parce qu'il est plus profond qu'il n'en a l'air :

> « Un cœur qui met un aller-retour à réagir est un cœur sur lequel on appuie deux
> fois. Et le second appui est une seconde requête contre un état qui a déjà changé.
> Ce n'est pas du confort, c'est ce qui évite une classe de bugs. »

Ouvrir l'onglet Favoris :

> « Le groupe par défaut vient d'être créé par ce geste. Personne n'a eu à le
> décider. »

**Appui long sur le cœur d'une autre chaîne.** La feuille s'ouvre.

> « Une case par groupe, et l'état réel de la chaîne dans chacun. **Des cases, pas
> des boutons radio** : une chaîne peut être dans deux groupes. »

« Nouveau groupe », saisir **Documentaire**, valider.

> « Créé, et la chaîne rangée dedans, sans quitter la chaîne. Rien n'envoie
> quelqu'un vers un écran de réglages pour créer un dossier avant de pouvoir s'en
> servir. »

**Décocher un groupe sur une chaîne qui est dans deux.**

> « Elle sort de Documentaire, elle reste dans l'autre, et **le cœur ne clignote
> pas**. Il ne se vide pas pour se remplir un instant plus tard. »

---

### Acte 2 — L'écran Favoris, et une ligne qui dit tout · 2 min · téléphone

Ouvrir l'onglet Favoris.

> « Un onglet par groupe. Le groupe par défaut en premier, les autres dans l'ordre
> que l'utilisateur leur a donné. »

Faire défiler une liste d'au moins trois chaînes :

> « Et l'ordre à l'intérieur est celui du rangement. Pas alphabétique, pas celui du
> fournisseur. C'est une étagère, pas un index. »

**Montrer la ligne « Depuis *[source]* »** sous une chaîne de la seconde source.

> « Voilà pourquoi il fallait deux sources. Cette chaîne vient de l'autre
> abonnement, et elle est dans le même groupe. »

Et le point structurel du sprint :

> « Un groupe appartient au **compte**, pas à une source. C'est ce qui le distingue
> d'une catégorie : une catégorie est nommée par le fournisseur et vit sous sa
> source ; un groupe est nommé par l'utilisateur et peut mélanger deux abonnements.
> C'est pour ça que cet écran est **à côté** du catalogue et pas dedans — il n'a pas
> de source à mettre dans son adresse. »

---

### Acte 3 — Organiser, et ce que devient le contenu · 2 min · téléphone

Renommer **Documentaire** en **Docus**.

> « Le nom est à l'utilisateur. »

**Renommer le groupe par défaut** en « Mes chaînes ».

Puis mettre une nouvelle chaîne en favori **sans choisir de groupe** :

> « Elle atterrit dans "Mes chaînes". Il n'y a toujours **qu'un seul** groupe par
> défaut : le serveur ne vient pas d'en recréer un nommé "Favoris" à côté. »

> « Et si on repasse l'appareil en anglais, il s'appellera toujours "Mes chaînes".
> Le nom par défaut se traduit **jusqu'à ce que quelqu'un le change** — après, une
> traduction qui écrase son choix serait une application qui le contredit. »

**Supprimer un groupe.** Lire le message avant de confirmer.

> « Il dit combien de chaînes vont bouger et où elles vont. Pas "êtes-vous sûr ?",
> qui n'apprend rien à personne : le nombre et la destination laissent prévoir
> l'état dans lequel on sera. »

Confirmer, montrer que les chaînes sont dans le groupe par défaut.

> « Rien n'a été supprimé. Un groupe supprimé se vide dans le groupe par défaut,
> jamais dans le vide. »

---

### Acte 4 — La télévision, télécommande en main · 2 min 30 · TV

**L'acte où l'on montre une décision d'architecture avec une télécommande.**

Ouvrir les chaînes. Montrer la bande de puces.

> « **Toutes · Repris · les groupes · les catégories de la source.** Les groupes
> sont des puces, pas un rail. »

Et pourquoi, en une phrase :

> « Un rail plafonne ce qu'il contient, et un rail dont la huit-centième chaîne est
> inatteignable est un défaut. Cette décision a été prise au sprint 2. Un groupe
> filtre la grille exactement comme une catégorie, donc c'est une puce de plus dans
> une bande qui existait déjà : **zéro nouvelle zone de focus, zéro ligne à ajouter
> à la carte du parcours**. C'est le bénéfice de ne pas avoir cédé aux rails il y a
> deux sprints. »

`OK` sur une puce de groupe :

> « Elle filtre. Dans l'ordre de l'utilisateur. »

**Maintenir `OK` sur une carte de chaîne.** La feuille s'ouvre par-dessus la grille.

> « Appui long. La grille est encore là derrière, sous un voile. `OK` court, lui,
> lance toujours la chaîne — on n'a pas déplacé le geste principal. »

**Appuyer sur `LEFT`, puis `RIGHT`.**

> « Rien ne bouge. Et surtout, le focus **ne part pas** sur la grille du dessous.
> Deux couches qui reçoivent les touches en même temps, et plus personne ne sait
> laquelle répond. »

**`UP` depuis la première ligne**, `DOWN` depuis la dernière :

> « Le focus reste sur place. Une liste qui reboucle n'a pas de fin, et personne ne
> peut savoir qu'il a tout vu. »

`BACK` :

> « Retour sur la carte d'où l'on venait. »

Enfin, faire défiler une ligne contenant des chaînes favorites :

> « Le cœur est **dessiné** sur les cartes, et le D-pad ne s'y arrête jamais. Une
> seconde cible par carte doublerait la longueur du parcours horizontal. »

---

### Acte 5 — « Repris », et une divergence tranchée · 1 min · téléphone → TV

Sur le **téléphone**, lancer une chaîne. La laisser tourner cinq secondes, revenir.

Sur la **télévision**, actualiser. Montrer la puce « Repris ».

> « La chaîne que je viens de regarder sur le téléphone est là. Une puce qui ne
> montrerait que ce que **cette** télévision a regardé n'aurait pas eu besoin d'un
> serveur. »

**Puis la partie qui n'existe pas, et c'est le point :**

> « Le document de conception décrivait un **rail**, premier écran de la télévision.
> On a livré une puce. Le rail se défendait sur le fond — l'objection du plafond ne
> s'applique pas à une liste que le serveur garde courte — mais il aurait ouvert un
> **second mécanisme** sur un écran qui en a déjà un. La divergence a été tranchée
> dans la tâche, et le document a été corrigé pour décrire ce qui existe. Un document
> qui décrit un rail qui n'existe pas est pire que pas de document. »

**Et ce qui n'y est pas** — traverser vingt chaînes au D-pad sans en lancer aucune,
revenir sur la puce :

> « Aucune des vingt. L'historique s'écrit quand la lecture démarre, jamais au
> focus. Sur une télévision, la D-pad traverse vingt chaînes pour en atteindre une :
> un historique construit là-dessus, c'est l'historique de l'utilisateur qu'on
> abîme. »

---

### Acte 6 — Le compte, pas l'appareil · 3 min · les trois

**C'est la Definition of Done du sprint, littéralement. Cet acte ne se coupe pas.**

Sur le **téléphone**, créer un groupe **Sport** et y ranger deux chaînes, dont une
de la seconde source.

Sans rien faire d'autre, sur la **télévision** : actualiser.

> « La puce Sport est là, avec les deux chaînes. »

Sur le **web** : ouvrir la page des chaînes.

> « Et la barre de groupes aussi. »

Marquer le temps :

> « Trois appareils, un seul rangement. C'est ce que veut dire "un groupe appartient
> au compte", et c'est la seule chose de ce sprint qu'aucun test de ce dépôt ne peut
> établir — c'est pour ça que la Definition of Done exige les trois surfaces. »

**Renommer Sport depuis le web**, actualiser les deux autres :

> « Partout. »

**Supprimer un groupe depuis le web**, actualiser :

> « Parti des trois, et ses chaînes sont dans le groupe par défaut sur les trois. »

**Le cas qui a demandé du travail**, si le temps le permet — ranger sur le téléphone
une chaîne d'une source que la **télévision** n'a jamais synchronisée, puis ouvrir
la télévision :

> « Elle apparaît, **avec son nom**. La télévision n'a pas cette source en cache :
> elle est allée résoudre l'identifiant. Un favori qui s'afficherait comme une ligne
> vide serait pire que pas de favori. »

---

### Acte 7 — Le web, sans JavaScript · 1 min 30 · navigateur

Sur la page des chaînes, montrer la barre de groupes : créer, renommer, supprimer.

**Puis désactiver JavaScript et refaire les trois.**

> « Tout marche. Ce sont des `<form>` pointés sur des Server Actions, pas des appels
> depuis le navigateur — ce qui garde les jetons hors du JavaScript client **et**
> fait fonctionner l'écran sans lui. Choisir un groupe est un lien, parce que c'est
> une vue : c'est dans l'adresse, ça survit à un rechargement, le bouton retour le
> défait. »

> **À dire, parce que le code a bougé depuis :** au sprint 4, cette barre était le
> seul endroit du web où l'on organisait ses groupes, et le rail au-dessus était
> filtré sur **la source de la page** — donc un groupe qui contenait des chaînes de
> deux abonnements s'affichait amputé, **sans le dire**. C'est le défaut que
> `S6-09` est venu corriger deux sprints plus tard, avec un écran `/app/favorites` à
> l'échelle du compte. Si la démo se joue sur le build d'aujourd'hui, la phrase sous
> le rail et le lien vers cet écran sont visibles : les montrer, en disant qu'ils
> sont arrivés après.

---

### Acte 8 — Ce qui ne se voit pas · 1 min 30 · écran de terminal

L'acte pour les gens qui liront le code après.

**Le piège que ce sprint a payé, et il a une valeur numérique :**

> « `GET /me/favorites` rend des identifiants et rien d'autre : pas de nom, pas de
> logo. Il faut les résoudre par `?ids=`, et le contrat plafonne à **cent
> identifiants par requête**. Un groupe de cent vingt chaînes, ce n'est donc pas une
> requête tronquée à cent : c'est deux requêtes. »

Et le second piège, qui est le vrai :

> « Le plafond, on le voit dans le contrat. Ce qu'on ne voit pas, c'est que `size`
> vaut **cinquante par défaut**. Un lot de cent identifiants revient à moitié
> répondu — **avec un `200` et aucune erreur**. C'est la pire forme d'un bug : rien
> n'a l'air cassé. »

> « Les favoris et les récents ont exactement la même forme. Écrit deux fois,
> c'était deux fois le même piège. `ChannelResolver` l'écrit une fois, pour les
> deux. »

**Et une ligne de schéma qui évite tout un écran de code :**

> « `favorite.channel_id` porte un `ON DELETE CASCADE`, et la resynchronisation fait
> un upsert sur `(source_id, external_id)`. Une chaîne qui reste garde son
> identifiant ; une chaîne qui disparaît emporte ses favoris. Il n'y a donc **aucun
> cas de favori orphelin** à traiter, nulle part, sur aucune surface. »

---

## Si ça casse

| Panne | Repli |
|---|---|
| Le groupe créé sur le téléphone n'apparaît pas sur la TV | Actualiser l'écran. C'est un cache, pas une synchro temps réel — le dire plutôt que d'attendre |
| La ligne « Depuis … » n'apparaît pas | Il n'y a qu'une source enregistrée. C'est le prérequis, et il est trop tard : passer l'acte 2 sur ce point |
| L'appui long n'ouvre pas la feuille sur la TV | Vérifier que c'est bien `OK` maintenu et non répété. Si ça persiste, le noter et passer à l'acte 5 |
| La puce « Repris » est vide | La lecture doit avoir **démarré**, pas seulement été sélectionnée. C'est exactement le point de l'acte |
| Une chaîne favorite s'affiche sans nom | C'est le chemin `?ids=` qui a échoué. Le noter — c'est un vrai défaut, pas un aléa de démo |
| Un logo ne charge pas | **Ne pas s'en excuser.** Aucune image de remplacement n'est livrée, c'est voulu |
| Un plantage | Le noter et continuer |

---

## Ce que cette démo ne montre pas

À dire, pas à laisser découvrir.

- **Pas de favori sur un film ni sur une série.** Les groupes contiennent des
  chaînes. C'est un manque d'API tracé, pas un oubli d'écran — et il l'est toujours
  deux sprints plus tard.
- **Pas de glisser-déposer sur le web.** `PATCH /me/favorites/{id}` existe côté
  serveur depuis ce sprint et **aucun client web ne l'appelle**. Le téléphone
  réordonne, le web non.
- **Pas de guide des programmes.** L'EPG est en dernier, délibérément.
- **Rien n'est mis en file d'attente hors ligne.** Un cœur touché sans réseau est
  refusé **à voix haute** et revient à son état réel. C'est une décision : une
  écriture silencieusement mise de côté est une écriture qu'on croit faite.
- **Aucun sélecteur de source sur Android.** Les deux applications lisent la
  première source du compte. Quelqu'un qui en a deux ne peut pas passer de l'une à
  l'autre depuis son téléphone ou sa télévision — seul le web a une page par source.
  Antérieur à ce sprint, toujours pas chiffré.
- **La recette de ce sprint n'a pas de rapport de session.**
  [`sprint-04-recette.md`](./sprint-04-recette.md) existe, `R-100` → `R-194`, et la
  démo ne la remplace pas : une démo montre ce qui marche, une recette cherche ce
  qui ne marche pas.
