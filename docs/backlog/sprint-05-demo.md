# Plan de démo — Sprint 5

La Definition of Done exige une démo sur appareils réels, téléphone **et**
télévision pilotée à la télécommande. Ce document est le déroulé de celle-ci.

**Durée visée : 16 minutes**, questions non comprises — la somme des actes
ci-dessous, et quatre de plus que le sprint 2. Le fil traverse trois surfaces au
lieu de deux, et son dernier acte en demande deux à la fois :

> Je vois les films de mon serveur, je regarde, j'arrête. Je rallume ailleurs et
> je reprends là où j'en étais.

**S'il faut couper**, c'est dans cet ordre : l'acte 7 (les langues), puis la moitié
web de l'acte 5. **Jamais l'acte 6** — voir plus bas pourquoi.

---

## Matériel

- Un téléphone Android physique, **écran dupliqué** sur le vidéoprojecteur.
- Une box ou un téléviseur Android TV physique, **avec sa télécommande**. Pas
  d'émulateur : l'acte 4 porte sur deux touches d'une télécommande, et une souris
  ne les a pas.
- Un navigateur, sur le même compte.
- L'API et le banc d'essai démarrés et vérifiés.
- Un réseau dont on connaît le comportement.

### Les deux choses que le banc ne fournit pas

À préparer, sinon deux actes tombent à l'eau et on le découvre devant la salle.

**Un vrai fichier MP4 progressif.** Les fixtures `/film/*.mp4` du banc sont **des
octets MPEG-TS sous un nom de film** : c'est assez pour l'ingestion, qui classe sur
l'URL, et **ce n'est pas un conteneur qu'un lecteur décode**. Les actes 3, 4 et 5
ont besoin d'un vrai fichier, généré localement et servi par le banc.

**Un serveur qui ignore les requêtes `Range`.** Le banc répond `206`
correctement — ce qui est la bonne nouvelle et le problème. La moitié de l'acte 3
et un tiers de l'acte 4 montrent ce que fait le produit quand le serveur refuse de
se déplacer, et cela demande un serveur qui refuse. `python3 -m http.server` ne
convient pas : il gère `Range`.

Les deux sont des trous du banc d'essai, notés dans
[`sprint-05-recette.md`](./sprint-05-recette.md) §11. Ils sont à chiffrer ; en
attendant, ils se préparent à la main.

## Préparation, trente minutes avant

1. Dérouler le parcours en entier, une fois, seul.
2. **Deux sources enregistrées sur le compte** : `/playlist.m3u` (chaînes seules) et
   `/mixed.m3u` (chaînes **et** films). L'acte 1 repose entièrement sur la
   différence entre les deux, et le web est le seul endroit où elle se montre.
   **`/mixed.m3u` doit être la première** des deux : les applications Android
   lisent la première source du compte, et tout le reste de la démo se joue sur
   les films.
3. Vérifier que les catalogues sont synchronisés **sur les trois surfaces**.
4. **Effacer la progression du compte de démo.** L'acte 6 doit partir de rien : un
   rail « Reprendre » déjà rempli tue son propre effet de surprise.
5. Mettre les appareils en français, préparer le basculement pour l'acte 7.
6. Couper les notifications.

---

## Déroulé

### Acte 1 — L'onglet qui n'est pas là · 1 min 30 · navigateur + téléphone

Commencer par une **absence**. C'est contre-intuitif et c'est le bon ordre.

**Sur le web, où les deux sources se montrent côte à côte** :

1. Ouvrir la page de la source `/playlist.m3u`. Un seul lien : « Voir les
   chaînes ». **Pas de « Parcourir les films ».**
2. Ouvrir celle de `/mixed.m3u`. Les deux liens sont là.

Le dire à voix haute : la plupart des playlists M3U ne portent que des chaînes. Un
lien qui ouvrirait sur une grille vide serait une promesse que personne ne peut
tenir, et l'utilisateur irait chercher la pièce qu'on lui a montrée.

**Puis sur le téléphone** : la barre du bas porte l'onglet Films, en deuxième
position, parce que la source du compte en a.

**Ce qu'il faut faire remarquer** : l'onglet *arrive*, il ne disparaît jamais. La
barre se dessine avec ce qui est certain et gagne une entrée ; jamais l'inverse.
Retirer une cible sous le pouce de quelqu'un est le genre de détail qu'on ne
programme pas exprès et qu'on subit tous les jours.

> **La comparaison se fait sur le web et pas sur le téléphone**, et c'est une
> limite du produit plutôt qu'un choix de mise en scène : **les applications
> Android lisent la première source du compte** et n'ont pas de sélecteur. Le web,
> lui, a une page par source. C'est dit à la fin (§ ce que cette démo ne montre
> pas) plutôt que contourné en silence.

### Acte 2 — La grille, et une affiche qu'on n'a pas · 2 min · téléphone

Ouvrir l'onglet Films. Des affiches en portrait, deux colonnes.

Une phrase sur le nombre de colonnes, parce qu'elle dit comment ce produit décide :
trois colonnes rentraient. Sous cent cinquante points, on ne reconnaît plus un film
qu'on a vu — et reconnaître l'image est tout le travail de cet écran.

Puis **s'arrêter sur une carte sans affiche**, qui montre son titre sur un aplat.

> Lumo n'embarque aucune illustration de contenu. Pas d'affiche générique, pas de
> silhouette. Une affiche inventée est un contenu inventé.

Ajouter que c'est aussi le rendu honnête : beaucoup de fournisseurs annoncent leurs
affiches en `http`, que l'application n'autorise pas. « Pas d'affiche » et
« l'affiche n'a pas chargé » sont le même résultat vu du canapé.

Ouvrir une fiche. Affiche, titre, année, durée, note **telle que la source l'écrit**,
synopsis. Et rien d'autre : pas d'acteurs, pas de recommandations, pas de
bande-annonce. Le dire : aucun panel IPTV ne porte ces données de façon fiable, et
une rubrique vide sur neuf films sur dix se lit comme une panne, pas comme de la
sobriété.

### Acte 3 — Lire, et le curseur qui avoue · 3 min · téléphone

L'acte qui distingue ce sprint. **Commencer par ce qui marche, finir par ce qui ne
marche pas** — l'inverse du sprint 2, parce qu'ici l'échec est une limite du serveur
de l'utilisateur et non une erreur.

1. Lancer un film sur le vrai MP4. Image, son, barre de progression.
2. **Faire glisser le curseur.** La lecture saute. Montrer que la barre ne revient
   pas sous le doigt pendant le glissement.
3. Revenir en arrière, relancer **sur le serveur sans `Range`**.

Là, ne rien dire pendant deux secondes et laisser la salle voir : la barre est
dessinée, **inerte, avec une phrase à côté d'elle**.

> Le déplacement dans un fichier exige que le serveur de l'utilisateur réponde à
> une requête partielle. Beaucoup de panels ne le font pas.

Puis la phrase qui porte la décision :

> Un curseur qui ne bouge pas sans dire pourquoi est un défaut. Un curseur absent
> est une fonction qu'on croit ne pas avoir livrée. Alors il est là, désactivé, et
> il s'explique.

Ajouter, sans s'y attarder, que Media3 ne le découvre **qu'au premier essai** : une
barre qui fonctionnait devient inerte pendant la lecture. C'est l'ordre dans lequel
le serveur répond réellement.

### Acte 4 — La télécommande · 3 min · TV, télécommande uniquement

Poser la télécommande sur la table entre deux gestes. Comme au sprint 2, le geste
dit le propos.

1. Ouvrir les Films depuis le rail. **Une seule ligne d'affiches.** Expliquer :
   après overscan il reste environ quatre cent quatre-vingts points de haut, dont
   cent cinquante pris par le titre et la bande. Deux lignes mettraient chaque
   affiche à cent points de large — invisible à trois mètres. **Le nombre de
   colonnes se fixe sur la lisibilité, pas sur ce qui rentre.**
2. `OK` sur une affiche : **la fiche s'ouvre, le film ne se lance pas.** Une chaîne
   se lance, un film se choisit — et choisir demande une année, une durée et un
   synopsis qu'aucune carte ne porte.
3. Descendre loin dans la grille, ouvrir une fiche, `BACK`. **La grille revient sur
   ce film**, pas en tête. Le faire depuis le fond, pas depuis la première carte :
   c'est là que ça se voit.
4. Lancer le film. Rien sur l'image au repos.

**Le moment central de l'acte**, et il tient en deux touches :

- Appuyer sur `RIGHT`. La lecture avance de dix secondes et **la barre seule
  apparaît**.
- Appuyer sur `OK`. **Le titre apparaît**, avec la barre dessous.

Le dire clairement : ce sont deux couches et deux gestes. Les deux touches sont à un
centimètre l'une de l'autre sur toutes les télécommandes du monde, et quelqu'un qui
saute une scène n'a pas demandé qu'on lui rappelle ce qu'il regarde.

Finir en tendant la télécommande à quelqu'un de la salle.

### Acte 5 — Le web, et ce qu'il n'envoie pas · 2 min · navigateur

Ouvrir les films dans le navigateur. Catégorie, recherche, page : **tout est dans
l'URL**. Copier le lien d'un film et le coller dans un second onglet — c'est la
chose qu'on envoie à quelqu'un.

Puis les deux démonstrations qui valent l'acte :

1. **Désactiver JavaScript**, recharger. Parcourir, filtrer, chercher, paginer,
   ouvrir une fiche. Tout fonctionne. Seule la lecture demande JavaScript.
2. **Afficher la source de la page**, pendant la lecture. Chercher l'URL du flux :
   **elle n'y est pas**. Elle n'existe que dans une réponse en `no-store`, obtenue
   au moment de lire.

Une phrase pour finir : sur un panel Xtream, cette URL contient le nom
d'utilisateur et le mot de passe de l'abonné. C'est la réponse la plus sensible de
toute l'API, et elle ne descend jamais dans un document.

### Acte 6 — Reprendre ailleurs · 2 min · téléphone → TV

L'acte qui justifie le sprint. Il demande de la préparation (§ préparation, point 4)
et se joue en trois gestes.

1. Sur le téléphone, lancer un film et le laisser tourner. Avancer le curseur vers
   vingt minutes. **Quitter.**
2. Allumer la télévision, ouvrir les Films. Une puce **Reprendre** est apparue dans
   la bande, en deuxième position, et le film y est.
3. Ouvrir sa fiche. **Deux boutons : *Reprendre à 20:14* et *Recommencer*.**

Ne pas appuyer tout de suite. Poser la question à la salle : lequel des deux
attendiez-vous ?

> Une reprise automatique est une bonne idée jusqu'au jour où quelqu'un veut revoir
> le début. Ce jour-là, c'est une fonction dont on ne peut pas sortir. Alors les
> deux sont là, et aucun n'est pressé à votre place.

Appuyer sur **Recommencer**, pour le prouver. Puis revenir et appuyer sur
*Reprendre* : la lecture repart à vingt minutes.

**Si le temps manque, c'est l'acte qu'il faut garder.** Tout le reste peut être vert
sur trois appareils qui ne se parlent pas.

### Acte 7 — Les deux langues · 1 min · téléphone + TV

Basculer la langue du système. Repasser sur la fiche et sur le lecteur.

Deux endroits à montrer, parce que ce sont les deux qui souffrent : **la phrase de
refus de déplacement**, longue dans les deux langues, et **« Reprendre à 1:47:03 »**
sur une puce de télévision.

Aucun texte tronqué, rien resté en anglais.

### Acte 8 — Ce qui ne se voit pas · 1 min 30 · écran de terminal

```bash
adb logcat -d | grep -iE "m3u8?|\.mp4|\.mkv|player_api|password|Bearer |refresh_token"
```

Aucune ligne. L'extension de fichier est dans le motif **parce que ce sprint a
ajouté un second type d'URL de flux**, et que la règle ne se réécrit pas à chaque
fois qu'un chemin nouveau apparaît.

Enchaîner sur la décision de l'ADR 0009, qui est le meilleur endroit pour montrer
comment ce projet tranche. Ouvrir `/mixed.m3u` et pointer deux entrées :

- une chaîne servie comme fichier progressif, **rangée dans les films** ;
- un film dans un groupe nommé « Films », servi en HLS, **resté dans les chaînes**.

> Ces deux-là sont faux, et c'est écrit dans l'ADR avant d'avoir été codé. Seule
> l'URL classe : un titre de groupe ne classe rien, et dans le doute on penche vers
> le direct. La sortie n'est pas une règle plus maligne — c'est une surcharge par
> catégorie, chiffrée à trois points, que l'utilisateur pose lui-même.

C'est l'acte qui convainc les gens qui liront le code après.

---

## Si ça casse

| Panne | Repli |
|---|---|
| Le vrai MP4 ne se lit pas | Second fichier préparé, encodé autrement. Ne **pas** basculer sur les fixtures du banc : elles ne décodent pas |
| Le serveur sans `Range` ne démarre pas | Capture d'écran de la barre inerte et de sa phrase, en secours. Le dire plutôt que de le faire |
| Le rail « Reprendre » ne se remplit pas | Attendre trente secondes : c'est le pas d'écriture. Ne pas relancer le film en boucle |
| Le catalogue de films est vide sur un appareil | Actualiser depuis l'écran. Ne pas réinstaller devant la salle |
| Une affiche ne charge pas | **Ne pas s'en excuser.** C'est l'acte 2 : le titre sur un aplat est le comportement voulu |
| Un plantage | Le noter et continuer |

---

## Ce que cette démo ne montre pas

À dire, pas à laisser découvrir.

- **Pas de séries.** C'est le sprint 6, et l'ADR y recommande de les limiter à
  Xtream en v1.
- **Pas de guide des programmes.** L'EPG est en dernier, délibérément.
- **Pas de favoris sur un film.** Les groupes du sprint 4 contiennent des chaînes ;
  un film n'a pas de cœur, et la grille TV n'a donc pas d'appui long.
- **La recherche ne pardonne pas une faute de frappe.** Elle parcourt le cache de
  l'appareil. La recherche tolérante appartient au serveur et n'est pas livrée.
- **Le rail « Reprendre » est vide hors ligne**, et un film ouvert hors ligne repart
  du début. Une position s'écrit sur un appareil et se lit sur un autre : elle vit
  sur le serveur et nulle part ailleurs.
- **Aucun sélecteur de source sur Android.** Les deux applications lisent la
  première source du compte. Quelqu'un qui en a deux ne peut pas passer de l'une à
  l'autre depuis son téléphone ou sa télévision — seul le web a une page par
  source. C'est visible dès l'acte 1, et ce n'est pas propre à ce sprint.
- **Toujours aucune facturation, aucun Google Sign-In.** Notés dans
  [`dette.md`](./dette.md), préparés pour ne pas coûter cher plus tard, pas traités.
- **Le déplacement dans un film échoue chez certains fournisseurs**, et c'est montré
  à l'acte 3 plutôt que caché. Ce que Lumo livre ici est un échec **nommé**, pas un
  échec évité.

Et la phrase qui ferme la démo, la même qu'au sprint 2 parce qu'elle est le
produit :

> Lumo ne fournit, n'héberge et ne revend aucun contenu. Tout ce que vous avez vu
> vient de fichiers de test générés pour l'occasion.
