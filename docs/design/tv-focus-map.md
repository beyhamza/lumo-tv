# Carte du parcours de focus — Android TV

Livrable de la tâche `S2-13`, au même titre que les écrans.

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

Trois éléments seulement : **Chaînes, Source, Réglages**. VOD, séries et recherche
sont hors du périmètre de la verticale et retirés du rail — sur une TV chaque
entrée de plus est un `DOWN` de plus entre le spectateur et ce qu'il vient
chercher.

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

**Focus à l'arrivée : la première carte de la grille.**

Pas la bande de catégories : quelqu'un qui allume sa télévision veut une chaîne, et
l'étagère où il se trouve déjà est la bonne. Atteindre les catégories coûte un
`UP` ; atteindre une chaîne depuis les catégories aurait coûté un `DOWN` **plus une
décision que personne n'a demandée**.

| Depuis | UP | DOWN | LEFT | RIGHT | OK |
|---|---|---|---|---|---|
| Carte de chaîne, 1re colonne | catégories (si 1re ligne) / carte au-dessus | carte en dessous | **rail** | carte suivante | lance la chaîne |
| Carte de chaîne, ailleurs | carte au-dessus / catégories | carte en dessous | carte précédente | carte suivante | lance la chaîne |
| Puce de catégorie | — (bord haut) | grille | puce précédente | puce suivante | filtre la grille |

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

## Lecteur TV — `S2-14`, à remplir avec l'écran

La ligne à ne pas oublier au moment de l'écrire, parce que c'est le piège classique
du lecteur TV : **les contrôles s'effacent, et la D-pad doit les rappeler.** Un
lecteur plein écran sans cible focalisable pendant que les contrôles sont masqués
est un écran où seul `BACK` répond — le cas que la règle 1 interdit.

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
