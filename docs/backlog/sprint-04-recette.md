# Recette — Sprint 4

Plan de qualification d'US-12 sur les trois surfaces. Il se déroule **à la main**,
sur des appareils réels et dans un navigateur, et il ne remplace ni les
122 tests Android ni les 28 tests web : il vérifie ce qu'aucun des deux ne voit —
qu'un groupe créé sur un téléphone existe sur une télévision.

Les critères d'acceptation sont ceux d'**US-12**, en Gherkin, dans
[`sprint-04.md`](./sprint-04.md). Chaque cas renvoie à la tâche qui l'a livré.

**La numérotation reprend à R-100** pour ne pas empiéter sur les quarante cas de
[`sprint-02-recette.md`](./sprint-02-recette.md), qui restent à jouer et gardent
R-10 → R-73.

**R-187 à R-189 ont été ajoutés après coup**, quand un usage réel a montré que le
geste des groupes ne marchait pas sur le web alors que tous les cas de la section 8
passaient. Le trou est décrit sous R-182.

---

## 1. Prérequis

Cinq points, et les deux derniers sont propres à ce sprint.

**Les trois surfaces, sur le même compte.** Un téléphone Android physique, une box
ou un téléviseur Android TV physique **avec sa télécommande**, et un navigateur.
C'est la seule configuration qui prouve quoi que ce soit ici : la moitié de ce
sprint est la synchronisation d'un groupe entre des appareils, et une démo sur une
seule surface ne la montre pas.

**Deux sources enregistrées, pas une.** Non négociable, et c'est le prérequis qu'on
oublie : un groupe qui mélange deux abonnements est le cas d'usage même de la
fonction, et **la ligne « Depuis … » du téléphone ne s'affiche qu'à partir de deux
sources** (S4-04). Avec une seule, R-121 et R-122 ne sont pas jouables — ils sont
« non joué », pas « vert ».

**Un catalogue synchronisé sur chaque appareil**, sur les deux sources. Les favoris
se résolvent par jointure locale ; une source jamais synchronisée sur un appareil
emprunte le chemin de repli (`?ids=`), qui est justement R-140.

**Une mise à jour, pas une installation neuve.** Le point le plus facile à rater de
cette recette. Les migrations Room `2 → 3` et `3 → 4` sont écrites à la main, et
**une installation fraîche ne les exécute jamais** : Room crée le schéma final
directement. Il faut donc partir d'un build antérieur au sprint 4 déjà installé,
avec un catalogue en cache, puis installer par-dessus.

```bash
# Sur le téléphone et sur la box, avant de commencer :
adb install -r app-mobile-debug.apk   # -r : par-dessus, jamais -t ni une désinstallation
```

Désinstaller entre les deux, c'est jouer R-190 en croyant l'avoir joué.

**Langue.** Chaque cas se rejoue en français **et** en anglais. Le français est
environ 30 % plus long, et cette fois deux endroits précis en souffrent : les
puces de la bande TV et les libellés de la barre de groupes du web.

---

## 2. La règle qui prime sur la recette elle-même

**Aucune source réelle, à aucun moment.** Pas de vraie playlist, pas
d'identifiants d'un vrai fournisseur, pas de nom de chaîne ni de logo de bouquet —
ni dans les captures jointes à un rapport, ni dans un ticket ouvert pendant la
session (AGENTS.md §1).

Le banc d'essai du sprint 2 sert les deux sources dont cette recette a besoin :
`/playlist.m3u` pour la première, et une seconde source pointant le même banc sous
un autre nom fait parfaitement l'affaire — ce qui compte ici est qu'il y en ait
deux, pas qu'elles diffèrent.

---

## 3. Le geste, sur le téléphone

**R-100 · Le premier favori crée le groupe par défaut** · S4-03 · téléphone
Compte vierge de favoris. Appuyer sur le cœur d'une chaîne.
→ Le cœur se remplit **immédiatement**, sans attendre le réseau. L'onglet Favoris
montre un groupe, nommé « Favoris » en français et « Favourites » en anglais.

**R-101 · Le cœur ne se fait pas attendre** · S4-03 · téléphone
Appuyer sur le cœur d'une chaîne, réseau normal.
→ Le remplissage est **instantané**. Un cœur qui met un aller-retour à réagir est
un cœur sur lequel on appuie deux fois, et le second appui est une seconde requête
contre un état qui a déjà changé. Un délai visible est rouge.

**R-102 · Appui long : la feuille des groupes** · S4-03 · téléphone
Appui long sur le cœur d'une chaîne.
→ Une feuille s'ouvre : une case par groupe, l'état réel de la chaîne dans chacun,
et une ligne « Nouveau groupe ». **Des cases, pas des boutons radio** : on doit
pouvoir en cocher deux.

**R-103 · Créer un groupe sans quitter la chaîne** · S4-03 · téléphone
Dans la feuille, « Nouveau groupe », saisir « Documentaire », valider.
→ Le groupe est créé **et** la chaîne y est rangée. Rien n'envoie l'utilisateur
vers un écran de réglages.

**R-104 · Une chaîne dans deux groupes** · S4-03 · téléphone
Ajouter la même chaîne à « Documentaire » puis à « Ciné ».
→ Les deux cases sont cochées. **Le cœur reste plein.**

**R-105 · La retirer d'un groupe ne la dé-favorise pas** · S4-03 · téléphone
Sur la chaîne de R-104, décocher « Documentaire ».
→ Elle reste dans « Ciné », et **le cœur reste plein sans clignoter**. Un cœur qui
se vide puis se remplit un instant plus tard est rouge : c'est le cas que
`stillFavoritedWithout` traite, et il ne se voit qu'avec deux groupes.

**R-106 · Un appui court sur une chaîne déjà favorite** · S4-03 · téléphone
→ La feuille s'ouvre. Il ne **dé-favorise pas** : une chaîne rangée dans deux
groupes n'a pas de chose unique qu'un appui pourrait défaire.

**R-107 · Hors ligne, l'écriture est refusée à voix haute** · S4-03 · téléphone
Couper le réseau, appuyer sur un cœur.
→ Un message dit que la modification n'a pas pu être enregistrée, et **le cœur
revient à son état réel**. Rien n'est mis en file d'attente. Un cœur qui reste
rempli après un échec est rouge — c'est précisément la perte silencieuse que le
Gherkin interdit.

---

## 4. L'écran Favoris — téléphone

**R-110 · Un onglet par groupe** · S4-04 · téléphone
→ Les groupes en onglets, le groupe par défaut en premier, les autres dans leur
ordre.

**R-111 · L'ordre est celui de l'utilisateur** · S4-04 · téléphone
Un groupe contenant au moins trois chaînes dont les initiales ne sont pas dans
l'ordre alphabétique.
→ La liste suit **l'ordre de rangement**, pas l'ordre alphabétique ni celui du
fournisseur. Une liste triée par nom est rouge, et c'est le défaut le plus facile
à ne pas voir : il a l'air parfaitement normal jusqu'à ce qu'on essaie R-131.

**R-112 · Deux vides, deux messages** · S4-04 · téléphone
Compte sans aucun favori, puis compte avec des favoris mais un groupe vide.
→ Le premier **nomme le geste** (« appuyez sur le cœur… »). Le second dit
seulement que le groupe est vide. Le même message dans les deux cas est rouge :
quelqu'un qui a quarante favoris n'a pas besoin qu'on lui réexplique le cœur.

**R-113 · En chargement n'est pas vide** · S4-04 · téléphone
Ouvrir l'onglet Favoris sur un compte qui en a, réseau lent.
→ Un indicateur d'attente, **jamais** « Aucun favori ». Un écran vide devant
quelqu'un qui en a quarante est rouge.

**R-120 · Hors ligne, la liste est là** · S4-04 · téléphone
Ouvrir Favoris avec réseau, couper le réseau, rouvrir l'application.
→ Groupes et chaînes s'affichent depuis le cache.

**R-121 · La source est nommée quand il y en a deux** · S4-04 · téléphone
Compte à **deux sources**, un groupe contenant une chaîne de chacune.
→ Chaque ligne dit de quelle source elle vient.

**R-122 · Et elle ne l'est pas quand il n'y en a qu'une** · S4-04 · téléphone
Le même écran sur un compte à une seule source.
→ **Aucune ligne « Depuis … ».** La même phrase sous chaque ligne est du bruit,
pas de l'information.

**R-123 · Hors ligne, la source n'est pas nommée** · S4-04 · téléphone
Compte à deux sources, réseau coupé, ouvrir Favoris.
→ Les chaînes s'affichent, **sans** la ligne de source. Pas de blanc, pas de
« Depuis … » vide, pas d'identifiant technique.

---

## 5. Organiser — téléphone

**R-130 · Renommer** · S4-05 · téléphone
Appui long sur une puce de groupe → Renommer → « Cinéma ».
→ Le nom change. Vérifier ensuite sur la **télévision** et sur le **web** : c'est
R-170.

**R-131 · Monter et descendre** · S4-05 · téléphone
Appui long sur une ligne de favori → Monter.
→ La ligne monte d'un rang et **la liste reste contiguë**. À croiser avec R-111 :
si le tri est alphabétique, ce cas ne montre rien du tout.

**R-132 · Les entrées impossibles ne sont pas proposées** · S4-05 · téléphone
Ouvrir le menu sur la **première** puce, puis sur le **dernier** favori, puis sur
le groupe **par défaut**.
→ Respectivement : pas de « Déplacer à gauche », pas de « Descendre », **pas de
« Supprimer »**. Une entrée grisée est acceptable ; une entrée qui répond une
erreur est rouge.

**R-133 · La suppression dit ce qu'elle fait, avec le nombre** · S4-05 · téléphone
Supprimer un groupe contenant plusieurs chaînes.
→ « Ses N chaînes iront dans "Favoris". Rien n'est retiré de vos favoris. »
**Le nombre doit être juste.** Un « Êtes-vous sûr ? » est rouge : il n'informe de
rien.

**R-134 · Et elle fait ce qu'elle a dit** · S4-05 · téléphone
Confirmer la suppression de R-133.
→ Les chaînes sont dans le groupe par défaut, **à la suite** de ce qui s'y
trouvait déjà. Aucune n'a disparu des favoris.

**R-135 · Une chaîne déjà présente des deux côtés** · S4-05 · téléphone
Mettre une chaîne dans le groupe par défaut **et** dans « Ciné », puis supprimer
« Ciné ».
→ Elle apparaît **une fois**, pas deux. Elle est toujours en favori.

**R-136 · Déplacer vers un autre groupe** · S4-05 · téléphone
Appui long sur un favori → Déplacer vers un autre groupe.
→ Le groupe où il se trouve déjà **n'est pas proposé**. Après le déplacement, il
n'y a qu'une ligne, pas deux.

**R-137 · Réorganiser sans glisser-déposer** · S4-05 · téléphone, TalkBack activé
Parcourir la liste au lecteur d'écran et réordonner deux favoris.
→ Faisable **entièrement** par le menu Monter / Descendre. Si la seule façon de
réordonner est un geste de glissement, c'est rouge — l'ordre est ici celui de
l'utilisateur, et une réorganisation qu'un lecteur d'écran ne peut pas effectuer
n'en est pas une.

---

## 6. La télévision

Tous ces cas se font **télécommande en main**. Une souris sur un émulateur invalide
le résultat.

**R-150 · Les groupes sont des puces, dans le bon ordre** · S4-06 · TV
→ La bande affiche **Toutes · Repris · [groupes] · [catégories]**, dans cet ordre.
Les groupes sont séparés des catégories par un intervalle visible.

**R-151 · Une puce filtre la grille** · S4-06 · TV
`OK` sur une puce de groupe.
→ La grille ne montre plus que les chaînes du groupe, **dans l'ordre de
l'utilisateur**.

**R-152 · Un groupe vide n'a pas de puce** · S4-06 · TV
Créer un groupe vide depuis le téléphone, actualiser la télévision.
→ **Aucune puce n'apparaît.** Une puce qui ouvre une grille blanche ressemble à une
panne à trois mètres.

**R-153 · La bande reste parcourable** · S4-06 · TV
Depuis la grille, `UP` puis `LEFT`/`RIGHT` sur toute la longueur de la bande.
→ Chaque puce est atteignable, `DOWN` redescend dans la grille. À croiser avec la
carte du parcours de [`tv-focus-map.md`](../design/tv-focus-map.md) § *Chaînes* :
elle affirme que ce tableau n'a pas changé, et c'est ici qu'on le vérifie.

**R-154 · Les noms tiennent en français** · S4-06 · TV
Bande en français, avec un groupe au nom long.
→ Rien n'est tronqué de façon illisible à trois mètres, rien ne déborde.

**R-160 · Appui long sur OK, sans quitter la grille** · S4-07 · TV
Maintenir `OK` sur une carte de chaîne.
→ La feuille des groupes s'ouvre **par-dessus** la grille, qu'on voit encore
derrière un voile. `OK` court, lui, lance toujours la chaîne.

**R-161 · La feuille arrive avec le focus** · S4-07 · TV
→ La **première ligne** a le focus dès l'ouverture. Une feuille où `BACK` est la
seule touche qui fait quelque chose est rouge.

**R-162 · Les directions latérales ne font rien** · S4-07 · TV
Dans la feuille, appuyer sur `LEFT` puis sur `RIGHT`.
→ **Rien ne bouge, et surtout le focus ne part pas sur la grille du dessous.** Deux
couches actives en même temps, et l'utilisateur ne sait plus laquelle reçoit ses
touches.

**R-163 · Pas de bouclage** · S4-07 · TV
Depuis la première ligne, `UP`. Depuis la dernière, `DOWN`.
→ Le focus **reste sur place**. Une liste qui reboucle n'a pas de fin, et personne
ne peut savoir qu'il a tout vu.

**R-164 · `OK` bascule la ligne, et il n'y a qu'une cible** · S4-07 · TV
→ `OK` sur la ligne coche ou décoche. La coche **n'est pas focalisable** : le
parcours ne doit pas demander deux `RIGHT` par groupe.

**R-165 · `BACK` ferme sans rien changer** · S4-07 · TV
Ouvrir la feuille, ne rien cocher, `BACK`.
→ Retour à la grille, focus sur la carte d'où l'on venait, aucun favori ajouté.

**R-166 · Le cœur est dessiné, pas atteignable** · S4-07 · TV
Parcourir la grille aux flèches sur une ligne contenant des chaînes favorites.
→ Le cœur est **visible** sur les cartes concernées, et le D-pad ne s'y arrête
jamais. Une seconde cible par carte doublerait la longueur du parcours horizontal.

**R-167 · La puce « Repris »** · S4-08 · TV
Regarder une chaîne sur le **téléphone**, puis actualiser la télévision.
→ La puce « Repris » existe, et la chaîne regardée sur le téléphone y est. C'est
tout l'intérêt de la fonction : une puce qui ne montrerait que ce que cette
télévision a regardé n'aurait pas eu besoin d'un endpoint.

**R-168 · Elle n'apparaît pas quand rien n'a été regardé** · S4-08 · TV
Compte neuf.
→ **Pas de puce « Repris ».**

**R-169 · Le survol ne compte pas** · S4-08 · TV
Traverser vingt chaînes au D-pad **sans en lancer aucune**, puis regarder la puce
« Repris ».
→ **Aucune des vingt n'y est.** Un historique construit sur ce que la télécommande
a effleuré est l'historique de l'utilisateur, abîmé.

---

## 7. Le compte, pas l'appareil

**Ces six cas sont la Definition of Done de ce sprint.** Ce sont les seuls qu'aucun
test de ce dépôt ne peut établir, et le seul motif pour lequel les trois surfaces
sont exigées.

**R-170 · Un groupe créé ici existe là-bas** · US-12 · les trois
Créer « Documentaire » sur le **téléphone**, y ranger deux chaînes.
→ Il apparaît sur la **télévision** (puce) et sur le **web** (barre de groupes),
avec les deux mêmes chaînes.

**R-171 · Un renommage suit partout** · US-12 · les trois
Renommer sur le téléphone, actualiser les deux autres.
→ Le nouveau nom partout.

**R-172 · Le groupe par défaut se traduit — jusqu'au renommage** · S4-00 · les trois
Sur un compte neuf, regarder le nom du groupe par défaut en FR puis en EN. Le
renommer ensuite en « Mes chaînes ».
→ Avant : « Favoris » / « Favourites ». Après : « Mes chaînes » **dans les deux
langues et sur les trois surfaces**. Une traduction qui écrase le nom choisi par
l'utilisateur est rouge.

**R-173 · Renommer le groupe par défaut n'en crée pas un second** · S4-01 · téléphone
Après R-172, mettre une nouvelle chaîne en favori **sans choisir de groupe**.
→ Elle atterrit dans « Mes chaînes ». **Il n'y a toujours qu'un seul groupe par
défaut.** Un second groupe nommé « Favorites » qui apparaît est exactement le bug
que `is_default` corrige, et ce cas est le seul qui le voit.

**R-174 · Une suppression suit partout** · US-12 · les trois
Supprimer un groupe sur le web.
→ Il disparaît du téléphone et de la télévision, et ses chaînes sont dans le
groupe par défaut sur les trois.

**R-175 · Un favori d'une source jamais synchronisée ici** · S4-02 · TV
Ranger sur le **téléphone** une chaîne d'une source que la **télévision** n'a
jamais synchronisée, puis ouvrir la télévision.
→ La chaîne apparaît, **avec son nom**. C'est le chemin `?ids=`, et une ligne vide
ou absente ici est rouge.

**R-140 · Un groupe de plus de cent chaînes** · S4-02 · téléphone
Constituer un groupe d'au moins 120 chaînes, puis ouvrir Favoris sur un appareil
dont le cache est vide.
→ **Les 120 s'affichent.** Cent tout rond est rouge, et c'est le piège le plus
discret du sprint : la réponse tronquée arrive avec un `200` et aucune erreur.

---

## 8. Le web

**R-180 · Le groupe vit dans l'URL** · S4-09 · navigateur
Choisir un groupe dans la barre.
→ L'URL porte `?group=…`. Recharger la ramène ; **le bouton retour la défait** ;
le lien est partageable.

**R-181 · L'étoile dépose dans le groupe ouvert** · S4-09 · navigateur
Ouvrir « Documentaire », puis étoiler une chaîne de la liste.
→ Elle est rangée dans « Documentaire », et le libellé de l'étoile le disait avant
le clic.

**R-182 · Les étoiles ne se vident pas quand on filtre** · S4-09 · navigateur
Étoiler deux chaînes dans deux groupes différents, puis ouvrir l'un des deux.
→ **Les deux chaînes gardent leur étoile pleine** dans la liste. Une étoile qui se
vide parce qu'on regarde un autre groupe est rouge — l'étoile dit si la chaîne est
en favori, pas si elle est dans ce groupe-ci.

> **Ce cas était vert et le geste était cassé.** Il vérifie l'*affichage*, et il a
> raison ; mais tant que l'étoile n'avait qu'une action, ce même affichage rendait
> cette action ambiguë — et elle se résolvait en « retirer ». **Une chaîne déjà en
> favori ne pouvait donc pas être mise dans un second groupe depuis le web**, ce
> qui est pourtant le point structurel d'US-12. Corrigé après un signalement
> d'usage ; R-187 ci-dessous est le cas qui manquait.

**R-187 · Mettre une chaîne déjà en favori dans un second groupe** · S4-09 ·
navigateur
Étoiler une chaîne (elle part dans « Favoris »), puis cliquer à nouveau son étoile.
→ **La liste des groupes s'ouvre**, elle ne retire rien. Une coche marque « Favoris »
; presser « Documentaire » l'y ajoute **sans la retirer de « Favoris »**. Les deux
coches sont là ensuite.

> C'est le geste du téléphone, à l'identique : appui court sur une chaîne non
> favorite = classement direct, appui sur une chaîne déjà favorite = la feuille
> s'ouvre. Deux surfaces qui ne s'accordent pas sur ce que fait une étoile est pire
> que l'une des deux imparfaite.

**R-188 · Le premier clic reste un seul clic** · S4-09 · navigateur
Sur une chaîne **non** favorite, cliquer l'étoile.
→ Elle est classée immédiatement, sans liste intermédiaire — dans le groupe ouvert
dans la barre, ou dans le groupe par défaut si la barre est sur « tous ». Une liste
qui s'ouvrirait ici ajouterait un clic au geste le plus fréquent.

**R-189 · La liste des groupes marche sans JavaScript** · S4-09 · navigateur
JavaScript désactivé, cliquer l'étoile d'une chaîne déjà favorite.
→ La liste s'ouvre — c'est un `<details>` natif — et chaque ligne est un formulaire
qui fonctionne. La règle de la zone ne se suspend pas pour un contrôle nouveau.

**R-183 · Un groupe ouvert se montre en entier** · S4-09 · navigateur
Ouvrir un groupe de plus de douze chaînes.
→ Toutes s'affichent, pas douze.

**R-184 · Supprimer, avec le nombre** · S4-09 · navigateur
→ Même phrase et **même nombre** que R-133 sur le téléphone. Le groupe par défaut
n'a **aucun** bouton de suppression.

**R-185 · Tout marche sans JavaScript** · S4-09 · navigateur
Désactiver JavaScript. Choisir un groupe, en créer un, en renommer un, étoiler une
chaîne.
→ Les quatre fonctionnent. C'est la règle de la zone (`apps/web/AGENTS.md` §3), et
elle n'est pas suspendue parce que l'écran est nouveau.

**R-186 · Un groupe supprimé ailleurs, dans une URL** · S4-09 · navigateur
Ouvrir une URL `?group=…` d'un groupe supprimé depuis le téléphone.
→ La page affiche **tous** les favoris, pas un rail vide et pas une erreur.

---

## 9. Cas transverses

Ceux-là ne portent aucune tâche et bloquent quand même la Definition of Done.

**R-190 · La mise à jour n'efface pas le catalogue** · S4-02, S4-08 · téléphone + TV
Partir d'un build **antérieur au sprint 4** avec un catalogue synchronisé, installer
par-dessus (`adb install -r`), rouvrir.
→ Le catalogue est **toujours là**, et les favoris apparaissent après la première
actualisation. Un catalogue vidé signifie qu'une migration manque ou qu'un
`fallbackToDestructiveMigration` s'est glissé quelque part — et le prix, c'est
quinze mille chaînes retéléchargées sur les données mobiles de quelqu'un.

**R-191 · Aucun secret dans les journaux** · les deux surfaces Android
Dérouler un parcours complet de favoris, application en `DEBUG`, puis :

```bash
adb logcat -d | grep -iE "m3u8?|player_api|password|Bearer |refresh_token"
```

→ **Aucune ligne.** Ce cas se rejoue après toute tâche qui touche à la lecture ou
à la session ; il est repris ici parce que ce sprint a ajouté des appels réseau.

**R-192 · Les deux langues tiennent** · les trois surfaces
Rejouer les écrans de favoris en FR puis en EN.
→ Aucun texte tronqué, aucun débordement, aucune chaîne non traduite. Les deux
endroits à regarder en priorité : la bande de puces TV et la barre de groupes du
web.

**R-193 · Le pluriel du message de suppression** · S4-05, S4-09 · téléphone + web
Supprimer un groupe contenant **une** chaîne, puis un groupe en contenant
plusieurs.
→ Deux phrases grammaticalement correctes dans les deux langues. Le singulier est
le cas qu'on n'essaie jamais.

**R-194 · Aucun contenu réel dans le livrable**
Relire les captures et les données de test avant de clore la session.
→ Aucun nom de chaîne, aucun logo de bouquet, aucune URL réelle.

---

## 10. Critère de sortie

La recette est verte quand **tous les cas ci-dessus passent, dans les deux langues,
sur appareils réels, télécommande en main pour la section 6**.

Un cas rouge bloque US-12, sauf R-191 et R-194 qui bloquent la livraison quelle que
soit la story.

**La section 7 ne s'allège pas.** Ses six cas sont la seule preuve que ce sprint a
livré ce qu'il annonce : des groupes qui appartiennent au compte. Tout le reste
pourrait être vert sur trois appareils qui ne se parlent pas, et le sprint serait
raté.

Le rapport de session note, pour chaque cas : vert, rouge, ou **non joué avec la
raison** — « une seule source enregistrée » et « installé à neuf » sont des raisons
recevables et traçables. « Probablement bon » ne l'est pas.
