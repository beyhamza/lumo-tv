# Plan de démo — Sprint 2

La Definition of Done du sprint 1 exige une démo sur device réel, téléphone **et**
télévision pilotée à la télécommande. Ce document est le déroulé de cette démo.

**Durée visée : 12 minutes**, questions non comprises. Le fil est la phrase qui
ouvre le backlog, jouée en vrai :

> Un utilisateur crée un compte, enregistre une source, et regarde sa première
> chaîne — sur son téléphone et sur sa télévision.

---

## Matériel

- Un téléphone Android physique, **écran dupliqué** sur le vidéoprojecteur ou l'écran
  de partage.
- Une box ou un téléviseur Android TV physique, **avec sa télécommande**. Pas
  d'émulateur : la démo porte en partie sur la navigation au D-pad, qu'une souris
  masque entièrement.
- L'API et le banc d'essai des sources (S2-03) démarrés et vérifiés.
- Un réseau dont on connaît le comportement. Le point de bascule d'un acte est une
  coupure volontaire ; une coupure subie au mauvais moment sabote la démonstration.

## Préparation, trente minutes avant

1. Dérouler le parcours en entier, une fois, seul. Une démo répétée zéro fois échoue.
2. Réinitialiser les données : le compte de démo **n'existe pas encore**, l'acte 1 le
   crée pour de bon.
3. Vérifier le banc d'essai sur ses six chemins (voir la recette §1).
4. Mettre les deux appareils en français, et préparer le basculement en anglais pour
   l'acte 6.
5. Couper les notifications des deux appareils.
6. Ouvrir `lumo.tv/activate` sur le téléphone, dans un onglet, **non connecté** :
   l'acte 4 montre la redirection qui conserve le code.

---

## Déroulé

### Acte 1 — Le compte · 1 min 30 · téléphone

Créer le compte à l'écran.

**À montrer explicitement** : saisir d'abord un mot de passe trop court. Le bouton
reste désactivé et la règle manquante s'affiche **avant** toute soumission. C'est un
détail d'une ligne dans le Gherkin et c'est la différence entre un formulaire et un
formulaire soigné.

Puis le vrai mot de passe, et la session s'ouvre immédiatement.

### Acte 2 — La source, et ce qui rate · 3 min · téléphone

Le cœur de la démo. **Commencer par l'échec, pas par le succès.**

1. Saisir des identifiants Xtream refusés par le banc d'essai. Lire le message à voix
   haute : « vos identifiants ont été refusés par le serveur ». Montrer que **l'hôte
   est resté dans le formulaire**.
2. Pointer un hôte injoignable. Lire le second message. Le laisser une seconde à côté
   du premier : ce sont deux phrases qu'on ne peut pas confondre, et c'est le travail
   qui a été fait.
3. Enfin, la playlist M3U valide. Nombre de chaînes trouvées à l'écran, statut qui
   bascule tout seul de l'attente à prêt.

C'est l'acte qui justifie le produit : le marché renvoie « une erreur est survenue »,
Lumo dit quoi faire ensuite.

### Acte 3 — Regarder, sur le téléphone · 2 min

Catégories, puis chaînes, avec leurs comptes. Défiler franchement pour montrer la
pagination sur une source volumineuse.

Lancer une chaîne. Plein écran, **pivoter le téléphone** : la lecture ne s'interrompt
pas.

Puis couper le réseau et revenir à la liste : elle est toujours là, depuis le cache,
avec l'indicateur hors ligne. Rétablir.

### Acte 4 — Associer la télévision · 2 min · TV + téléphone

Le moment le plus démonstratif du sprint. Poser la question à la salle avant de
commencer : *combien de temps pour taper un mot de passe à la télécommande ?*

1. Ouvrir l'application sur la télé : code à 8 caractères et QR, lisibles depuis le
   fond de la pièce.
2. Scanner le QR avec le téléphone. La page s'ouvre **code déjà rempli**, et comme la
   session web n'est pas ouverte, elle passe par la connexion **en conservant le
   code** — le montrer, c'est un détail que personne ne remarque quand il marche et
   que tout le monde subit quand il manque.
3. Valider. **Ne rien toucher.** La télévision se connecte seule en moins de dix
   secondes.

Reposer la télécommande sur la table pendant cet acte. Le geste dit le propos.

### Acte 5 — Regarder, sur la télévision · 2 min 30 · télécommande uniquement

Naviguer aux flèches. Insister sur le focus : à trois mètres, on voit sans chercher
où on est.

Lancer une chaîne. OK fait apparaître la barre d'information, qui disparaît après
cinq secondes — **les compter à voix haute**, ça se vérifie en direct.

BACK : retour à la liste, **positionné sur la chaîne qu'on regardait**.

Finir en tendant la télécommande à quelqu'un de la salle. Une application TV qui ne
survit pas à une main inconnue n'est pas finie.

### Acte 6 — Les deux langues · 1 min · téléphone

Basculer la langue du système. Reparcourir deux écrans, dont l'ajout de source, qui
porte les libellés les plus longs.

Aucun texte tronqué, rien de resté en anglais.

### Acte 7 — Ce qui ne se voit pas · 1 min 30 · écran de terminal

L'acte qu'on oublie et qui convainc les gens qui liront le code après.

```bash
adb logcat -d | grep -iE "m3u8?|player_api|password|Bearer |refresh_token"
```

Aucune ligne. Après un parcours complet, en `DEBUG`, ni URL de flux, ni mot de passe
Xtream, ni token n'a atteint un log.

Enchaîner en une phrase : le mot de passe Xtream est chiffré avant persistance et
l'API ne le renvoie **à personne, pas même à son propriétaire** ; le flux part du
serveur de l'utilisateur directement vers l'appareil et **ne transite jamais par notre
infrastructure**.

---

## Si ça casse

Prévu à l'avance, parce qu'improviser devant une salle coûte plus cher que la panne.

| Panne | Repli |
|---|---|
| Le réseau de la salle tombe | Point d'accès du téléphone, testé la veille |
| Le banc d'essai ne répond pas | Captures des trois messages d'erreur, en secours |
| La télé ne s'associe pas en 10 s | Ne pas rafraîchir en boucle : expliquer le polling, laisser finir. Si échec, montrer le nouveau code que la télé affiche seule |
| Un flux de test est mort | Deux chaînes de repli identifiées à la préparation |
| Un plantage | Le noter et continuer. Une démo qu'on reprend à zéro perd la salle |

---

## Ce que cette démo ne montre pas

À dire, pas à laisser découvrir. Une démo qui laisse croire que le produit est fini
prépare la déception de la revue suivante.

- **Aucune facturation.** Les offres, l'essai et le paiement existent dans le contrat
  d'API et ne sont implémentés nulle part.
- **Aucun espace compte en écriture** : la gestion des appareils, l'abonnement et les
  favoris n'ont pas de contrôleur côté serveur.
- **Ni VOD, ni séries, ni recherche.** Hors périmètre de la verticale, retirés de la
  navigation.
- **Les écrans web du design** — tarifs, FAQ, espace compte — ne sont pas construits.
- Le web ne montre que ce qui sert la verticale : inscription, connexion, activation.

Et la phrase qui ferme la démo, parce qu'elle est le produit :

> Lumo ne fournit, n'héberge et ne revend aucun contenu. Tout ce que vous avez vu
> vient de flux de test libres de droits.
