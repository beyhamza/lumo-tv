# Prompt d'initialisation — Claude Design

> **Mode d'emploi.** Trois passes, dans l'ordre : identité → écrans mobile/web →
> écrans TV. Ne saute pas la première : sans direction artistique arrêtée, les passes
> suivantes produiront trois esthétiques différentes.
>
> Le TV est traité **séparément et en dernier**, jamais comme une adaptation du mobile.
> C'est la contrainte la plus dure du projet et elle mérite sa propre passe.

---

## PASSE 1 — Identité et design system

```
CONTEXTE

Lumo TV est un lecteur IPTV pour Android, Android TV et web. L'utilisateur apporte sa
propre source (playlist M3U ou compte Xtream Codes). Nous ne fournissons aucun contenu :
nous fournissons une expérience nette pour regarder ce à quoi il a déjà accès.

Le marché est occupé par des applications fonctionnelles mais visuellement datées :
gradients bleu-noir, effets 3D, icônes hétéroclites, interfaces surchargées. Notre
avantage différenciant est le soin. Lumo doit ressembler à un produit qu'on paie, posé
à côté de Netflix ou Disney+ sur un écran de télévision sans détonner.

CE QUE JE TE DEMANDE

1. Une direction artistique, en trois propositions distinctes — pas trois variantes de
   la même idée. Pour chacune : le principe directeur en une phrase, une palette
   complète (thème sombre en priorité : une app vidéo se regarde dans le noir), une
   échelle typographique, un traitement des surfaces, un système d'élévation.
2. Un logotype et un jeu de marques : icône d'app Android, favicon, bannière Play Store,
   bannière Android TV (320×180, la contrainte la plus ingrate — l'icône doit rester
   lisible en 1280×720 sur un écran vu à trois mètres).
3. Le design system exportable en tokens : couleur, espacement, rayon, typographie,
   durées et courbes d'animation. Une seule source, avec des dérivations explicites
   pour mobile, TV et web.

CONTRAINTES NON NÉGOCIABLES

- Le nom est "Lumo" et le domaine "lumo.tv". La marque évoque la lumière de l'écran.
- Thème sombre par défaut, thème clair uniquement sur le web marketing.
- Contraste AA minimum sur tous les textes ; AAA sur les surfaces TV.
- Aucune capture, aucun logo, aucun nom de chaîne ou de bouquet réel, nulle part —
  y compris dans les maquettes de présentation. Utilise des placeholders neutres.
- Une même échelle de couleurs et de types doit fonctionner à 30 cm et à 3 m. Prévois
  les dérivations plutôt que deux systèmes indépendants.

LIVRABLE

Les trois directions présentées côte à côte sur un même écran type, pour que le choix
soit comparable. Puis j'en retiens une avant de passer à la suite.
```

---

## PASSE 2 — Écrans mobile et web

> À lancer une fois la direction artistique choisie. Remplace `[DIRECTION]` par son nom.

```
On retient la direction "[DIRECTION]". Applique-la aux écrans du sprint 1.

ANDROID MOBILE (téléphone, thème sombre)

1. Onboarding — 3 écrans expliquant le principe "apportez votre source", sans jargon.
2. Inscription — email + mot de passe, avec indicateur de force de mot de passe.
3. Connexion — email + bouton Google.
4. Ajout de source — l'écran le plus important de l'application. Choix M3U / Xtream,
   puis formulaire adapté. Doit être compréhensible par quelqu'un qui ne sait pas ce
   qu'est un M3U : prévois l'aide contextuelle, pas juste des champs.
5. États de la source — en cours de validation, succès (avec nombre de chaînes trouvées
   et date d'expiration du compte), et QUATRE états d'erreur distincts : identifiants
   refusés, serveur injoignable, format invalide, playlist vide. Chacun avec son
   message et son action de sortie.
6. Liste des chaînes — catégories, puis chaînes, avec état hors ligne.
7. Lecteur — plein écran, contrôles, barre d'information, état d'erreur, état de
   chargement.
8. Réglages — compte, sources, appareils, langue.

WEB (lumo.tv)

9. Landing page — proposition de valeur, captures produit, tarifs, FAQ. Optimisée
   conversion et lecture rapide.
10. Gabarit d'article de guide — c'est notre moteur SEO, la lisibilité prime sur tout.
11. Espace compte — sources, appareils, abonnement.
12. /activate — saisie du code d'activation TV. Écran utilisé debout, téléphone en main,
    face à sa télé : gros champ, gros caractères, feedback immédiat, autocomplétion du
    format.

CE QUE JE VEUX VOIR EN PLUS DES ÉCRANS NOMINAUX

Les états vides, de chargement, d'erreur et hors ligne. Ce sont eux qui font la
différence entre un produit soigné et une maquette. L'écran d'ajout de source et ses
états d'erreur méritent autant d'attention que l'écran d'accueil — c'est là que se
perdent les utilisateurs.

CONTRAINTES

- Bilingue FR/EN : vérifie que la mise en page tient avec des libellés allemands ou
  français, 30 % plus longs que l'anglais.
- Cibles tactiles ≥ 48 dp.
- Aucun contenu réel dans les maquettes.
```

---

## PASSE 3 — Android TV

```
On adapte "[DIRECTION]" à Android TV. À lire avant de commencer :

CE N'EST PAS UNE VERSION AGRANDIE DU MOBILE. Les contraintes sont différentes en nature,
pas en degré :

- Entrée au D-pad uniquement. Pas de curseur, pas de tactile. Chaque élément
  interactif doit être atteignable par une séquence haut/bas/gauche/droite évidente.
- Distance de lecture de 3 mètres. Corps de texte minimum 18 sp, titres 24 sp et plus.
- Overscan : 5 % de marge sur les quatre bords, aucun élément critique en dehors.
- Le focus est le curseur. Il doit être visible instantanément, sans chercher. Combine
  échelle, bordure et élévation — jamais une simple variation de couleur, indistinguable
  sur un téléviseur mal calibré.
- Navigation horizontale par rails, pas verticale par listes.
- Aucune saisie de texte si on peut l'éviter : le clavier virtuel TV est une épreuve.

ÉCRANS

1. Splash et écran d'activation — code à 8 caractères + QR code, très grands, avec
   l'instruction "rendez-vous sur lumo.tv/activate".
2. Écran d'accueil — rails de catégories.
3. Grille de chaînes — avec état de focus, et l'aperçu de la chaîne focalisée.
4. Lecteur plein écran — état repos (aucun overlay), overlay d'information au OK,
   disparition après 5 s.
5. Réglages — navigation D-pad intégrale.
6. États : chargement, erreur de flux, hors ligne, aucune source configurée.

LIVRABLE SPÉCIFIQUE

En plus des écrans : une carte du parcours de focus. Pour chaque écran, quel élément
a le focus à l'arrivée, et où mène chaque direction du D-pad depuis chaque zone. C'est
ce document qui évite qu'un bouton devienne inatteignable — et c'est le défaut le plus
courant des applications TV.

CONTRAINTE

Toute maquette doit être évaluée en imaginant une télécommande dans la main, pas une
souris. Si tu ne peux pas décrire la séquence de touches pour atteindre un élément,
la maquette n'est pas finie.
```

---

## Passe de suivi utile

```
À partir des écrans validés, liste tout ce que l'interface implique côté API et qui
n'est pas déjà couvert par packages/contracts/openapi.yaml.

Pour chaque manque : l'écran concerné, la donnée nécessaire, et l'endpoint ou le champ
qu'il faudrait ajouter. Ne modifie pas le contrat — produis la liste, je la traiterai
comme un lot de modifications explicite.
```
