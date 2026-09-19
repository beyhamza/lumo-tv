# Recherche — règles d’interaction et recette Q9

Date : 19 septembre 2026. Statut : choix de cadrage arrêtés dans la suite confiée
à l’agent ; réalisation et recette non commencées.
Références : [US-021](../../backlog/stories/US-021-unified-search.md),
[écrans](unified-search.md), [sprint 10](../../backlog/sprint-10.md).

Ces règles précisent le parcours validé sans changer les opérations API ni la
navigation et le choix de source réalisés en S8. La composition visuelle reste
à relire sur les appareils réels.

## Saisie et résultats

- Délai de 350 ms après la dernière modification du texte. Entrée ou l’action
  Rechercher lance immédiatement la recherche courante, sans seconde requête
  identique à l’expiration du délai.
- Ne pas interroger pendant une composition de texte non terminée ; attendre
  sa validation avant d’appliquer le délai.
- Retirer les espaces en début et fin pour construire la requête. Une saisie
  vide après ce traitement annule la recherche, efface les résultats et affiche
  l’invitation à saisir, sans charger tout le catalogue.
- Limite de 100 caractères pour la requête, conformément aux trois paramètres
  `q` existants. Refuser le dépassement avec un message FR/EN ; ne pas tronquer
  silencieusement la requête envoyée. Aligner le comptage Unicode avec les
  clients et le validateur serveur avant réalisation.
- Tous affiche au maximum quatre résultats par type disponible. Voir tous
  apparaît si le total de ce type dépasse quatre. Conserver l’ordre existant
  des listes ; ne pas annoncer de classement par pertinence.
- Dans la liste d’un type, charger 20 résultats à la fois, puis Afficher plus
  sur demande. Garder les résultats précédents pendant le chargement ; en cas
  d’échec, le réessai concerne uniquement la page suivante.
- Un type absent de la source ne crée pas de filtre. Un type présent mais sans
  résultat pour la saisie conserve son filtre et affiche son état sans résultat.

Les quatre résultats d’aperçu et les pages de 20 correspondent à des requêtes
distinctes. Voir tous démarre la page 0 avec une taille de 20 ; ne pas réutiliser
un index de pagination calculé pour la taille 4. La liste complète remplace
l’aperçu, sans concaténer ses quatre éléments une seconde fois.

## Changements pendant une recherche

Chaque réponse n’est applicable qu’au contexte qui l’a demandée : compte, source,
texte, type, page et taille. Invalider le contexte lors d’un changement ; annuler
les requêtes si possible et ignorer les réponses obsolètes dans tous les cas.

Changement de texte : réinitialiser la pagination et présenter le chargement
du nouveau texte ; ne pas afficher les anciens résultats comme s’ils correspondaient
à la nouvelle saisie. Une erreur partielle conserve les sections réussies de
la même recherche, jamais celles d’une saisie précédente non identifiée.

Changement de source : rester dans Recherche, conserver le texte, revenir au
filtre Tous et à la première page, retirer immédiatement les résultats de
l’ancienne source et lancer la recherche dans la nouvelle si le texte est valide.
La remise à zéro des filtres suit US-018 ; aucune modification du sélecteur S8
n’est incluse dans cette préparation. Changer de compte vide le contexte courant.

Retour d’une fiche ou du lecteur : restaurer le texte, le filtre, les pages
chargées, la position et la carte sélectionnée tant que compte et source sont
inchangés. Si la carte a disparu, viser le voisin restant puis, à défaut, le
champ de recherche. La mémorisation est limitée au parcours courant, sans
historique persistant ni restitution d’une recherche après une nouvelle session.

## Clavier et focus TV

À l’entrée, placer le focus sur le champ sans ouvrir automatiquement un clavier
qui masquerait les résultats d’un retour. OK/Entrée sur le champ ouvre le clavier
fourni par la plateforme ; ne pas construire un clavier propriétaire dans S10.

L’action Rechercher valide la saisie et ferme le clavier. Un accès Voir les
résultats permet de rejoindre le premier résultat une fois chargé ; tant que
le chargement est en cours, garder le focus sur ce contrôle, sans déplacement
asynchrone inattendu. L’utilisateur l’active à nouveau lorsque les résultats
sont disponibles. Sans résultat, proposer Modifier la recherche ; après une
erreur totale, Réessayer reste accessible. Retour ferme d’abord le clavier s’il
est ouvert ; le retour de la page suit la navigation normale de la surface.

Dans les résultats, préserver un ordre de focus stable. L’arrivée de données
ne vole pas le focus au champ ou à la carte courante. Après Afficher plus,
une fois le chargement demandé terminé, proposer l’accès aux nouveaux résultats
sans revenir en tête de liste. La réalisation doit tester clavier système,
touches Retour et D-pad avec une télécommande réelle.

## Réseau et résultats disponibles

Une erreur n’est jamais un ensemble vide. Conserver les résultats déjà obtenus
pour le même contexte et signaler les sections impossibles à actualiser.
Réessayer ne relance que la section ou la page en échec.

Si la recherche locale fonctionne sur un catalogue déjà accessible, la conserver
avec indication de données potentiellement anciennes. Sinon, expliquer que la
recherche ne peut pas être actualisée et proposer un réessai ; ne pas prétendre
qu’aucun contenu ne correspond. Ne pas afficher le total d’un catalogue complet
si seul un sous-ensemble local a été recherché. Aucune nouvelle stratégie de
cache n’est introduite. Une suppression confirmée de source suit US-018/024.

## Couverture contractuelle et vérifications

Les trois listes par source acceptent `q` de 1 à 100 caractères, `page` à partir
de zéro et `size` de 1 à 200. Les réponses portent `total_elements` et
`total_pages`. Les tailles 4 et 20 sont dans ces bornes ; aucun endpoint nouveau
n’est demandé par ce cadrage.

La story conserve une recherche partielle insensible à la casse. À vérifier
avant implémentation : le descriptif de `q` des chaînes mentionne encore
« typo-tolerant (trigram) », alors que celui des films décrit une sous-chaîne
insensible à la casse et précise que l’index trigramme n’est pas approximatif.
Ne pas transformer cette divergence de description en promesse de correction
des fautes. Vérifier les comportements réels, les tests et la parité Android,
puis soumettre toute correction contractuelle nécessaire ; aucun changement
d’OpenAPI ni de code S8 n’est effectué ici.

## Recette prévue

| ID | Scénario | Résultat attendu |
|---|---|---|
| SR-01 | Saisie rapide de plusieurs caractères | Recherche finale après 350 ms sans requête pour chaque caractère |
| SR-02 | Entrée avant expiration du délai | Recherche immédiate, sans doublon ultérieur |
| SR-03 | Composition de texte, puis validation | Pas de recherche pendant la composition ; délai après validation |
| SR-04 | Espaces seuls ou effacement | Invitation à rechercher, aucun chargement du catalogue complet |
| SR-05 | Limite de 100 caractères puis dépassement | Requête conforme à la limite ; message sans troncature silencieuse |
| SR-06 | Plus de quatre résultats dans un type | Quatre dans Tous, Voir tous, puis page 0 de 20 sans doublons |
| SR-07 | Afficher plus puis échec de page suivante | Premières pages conservées ; réessai ciblé, pas de retour en tête |
| SR-08 | Une ancienne requête termine après la nouvelle | Aucun ancien résultat ne remplace le contexte courant |
| SR-09 | Source modifiée pendant la saisie ou la pagination | Texte conservé, Tous/page 0, aucun résultat de l’ancienne source |
| SR-10 | Une section échoue, les autres réussissent | Sections réussies conservées pour la même saisie, réessai local |
| SR-11 | Type absent puis type présent sans correspondance | Filtre absent dans le premier cas, présent avec état sans résultat dans le second |
| SR-12 | Retour de fiche ou lecteur | Contexte et position restitués sans créer d’historique persistant |
| SR-13 | TV : champ, clavier, validation, résultats, Retour | Focus stable, aucune prise de focus par une réponse tardive |
| SR-14 | Catalogue local consultable, puis aucun catalogue local | Recherche locale honnête si disponible ; sinon erreur explicite et réessai |
| SR-15 | Changement de compte | Contexte précédent vidé, aucune réponse ni recherche de l’autre compte affichée |

Recette non exécutée. Prévoir FR/EN, grand catalogue de banc, deux sources,
plusieurs comptes, erreurs et latence contrôlée. Les tests de maquette historiques
ne prouvent ni la pagination réelle ni le comportement des lecteurs.

## Correspondance Plane

Consultation du 19 septembre 2026 : Q9 (élément 175) est Todo ; S10-00 à S10-05
(éléments 130 à 135) sont Backlog. Ce cadrage prépare leurs descriptions et
critères sans modifier Plane. Le statut d’exécution doit être revérifié avant
de commencer une réalisation ou une synchronisation.
