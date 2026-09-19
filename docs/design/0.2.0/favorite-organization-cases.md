# Favoris — ordre par source et retraits partiels

Date : 19 septembre 2026. Statut : deux règles Q3 validées ; garanties techniques à vérifier, recette non exécutée.
Références : [US-022](../../backlog/stories/US-022-library-watchlist.md),
[US-020](../../backlog/stories/US-020-home-live.md), [bibliothèque](library.md),
[sprint 11](../../backlog/sprint-11.md).

## Périmètre

Les groupes appartiennent au compte et peuvent contenir plusieurs sources.
L’interface affiche seulement les favoris de la source active. A1/A2 et B1/B2
désignent des entrées abstraites de deux sources de banc, pas des chaînes réelles.
Les règles de création, de renommage et de suppression des groupes sont conservées.

## Décisions produit validées le 19 septembre 2026

| Sujet | Décision |
|---|---|
| Ordre dans une source | Réordonner les favoris visibles dans les emplacements de cette source, en laissant les favoris des autres sources à leur place |
| Retrait global partiel | Conserver les retraits réussis, afficher les appartenances restantes et proposer de réessayer seulement ce qui reste |

Exemple d’ordre retenu : groupe complet A1, B1, A2, B2, A3. Déplacer A3 avant
A1 donne A3, B1, A1, B2, A2. La vue de A devient A3, A1, A2 ; celle de B
reste B1, B2. L’ordre des groupes lui-même reste partagé à l’échelle du compte.

## Présentation des résultats — proposition

Pendant un retrait global, afficher une action en cours et éviter les doubles
lancements. Si une appartenance subsiste, conserver la chaîne dans Tous les favoris
et sur l’accueil selon les règles d’agrégation. Une chaîne retirée de certains
groupes peut changer de place dans l’agrégation, puisque la première occurrence
restante détermine sa position.

En cas d’échec partiel connu, proposer un message tel que « Retrait incomplet.
Cette chaîne est encore dans 1 groupe. » avec Réessayer et Fermer. Utiliser les
groupes réellement restants, sans annoncer que le retrait global a réussi.

Si le résultat est inconnu à cause d’une coupure réseau, ne pas annoncer un nombre
restant certain. Relire les appartenances avant de proposer les actions suivantes.
Le traitement d’une appartenance ajoutée simultanément depuis un autre appareil
reste à cadrer : un réessai ne doit pas élargir silencieusement une intention
ancienne à de nouveaux ajouts sans règle explicite.

## Cas de recette à préparer

| ID | Scénario | Attente ou arbitrage |
|---|---|---|
| FO-01 | Permuter A1 et A2 dans A1, B1, A2 | A2, B1, A1 ; B1 conserve sa place |
| FO-02 | Déplacer A3 en tête dans A1, B1, A2, B2, A3 | A3, B1, A1, B2, A2 ; les positions de B1/B2 restent inchangées |
| FO-03 | Consulter B sur un autre appareil après le déplacement dans A | L’ordre visible de B est conservé ; vérifier aussi ses places dans le groupe complet |
| FO-04 | Réordonner dans un groupe contenant une seule source | Même comportement que les commandes existantes Avant/Après et glisser-déposer |
| FO-05 | Retirer une chaîne appartenant à trois groupes, deux retraits réussissent | Conserver les deux réussites et indiquer l’appartenance restante ; ne pas recréer les appartenances retirées |
| FO-06 | Réessayer après FO-05 | Réessayer l’appartenance restante, puis retirer la carte agrégée seulement si aucune appartenance ne subsiste |
| FO-07 | Fermer le message d’échec partiel | Ne pas présenter la fermeture comme un succès ; conserver l’état réellement constaté |
| FO-08 | Réponse perdue après un retrait potentiellement effectué | Relire pour distinguer réussite, échec et résultat encore inconnu ; ne pas inventer un état certain |
| FO-09 | Retirer uniquement depuis un groupe | Les autres appartenances restent conservées, règle déjà validée |
| FO-10 | Retirer la première occurrence d’une chaîne présente dans plusieurs groupes | Une seule carte agrégée reste affichée, à la position de sa première occurrence restante |
| FO-11 | Autre appareil modifie le groupe pendant un réordonnancement | Garanties de concurrence à définir avant implémentation ; ne pas promettre une permutation atomique sans couverture |
| FO-12 | Autre appareil ajoute la chaîne à un groupe pendant un retrait global | Définir la portée du réessai face au nouvel ajout ; ne pas déclarer ce conflit résolu par le seul choix d’erreur partielle |

Contrôler les trois surfaces, deux comptes et deux sources de banc. Distinguer
ordre complet du groupe, ordre filtré, appartenance et ordre agrégé. Les résultats
restent non joués, même après validation du comportement produit.

## Contrat existant et vérification avant réalisation

Le contrat expose `GET /me/favorites`, avec filtre de groupe, et des opérations
d’ajout, de modification et de retrait par favori. Chaque appartenance a son
identifiant. `PATCH /me/favorites/{id}` déplace un favori vers un index du groupe
complet ; les autres positions sont décalées et restent contiguës.

Une position dans la liste filtrée ne peut donc pas être envoyée directement
comme si elle était un index du groupe complet. La règle de conserver les
emplacements des autres sources n’est pas la sémantique d’un déplacement unitaire.
Vérifier si les opérations existantes peuvent couvrir la permutation et ses
échecs intermédiaires ; elles ne promettent pas une permutation atomique du groupe.
Si une garantie requiert une évolution contractuelle, la soumettre conformément
à AGENTS.md §3 avant réalisation, sans inventer d’endpoint dans cette préparation.

Le retrait global correspond à plusieurs retraits d’appartenances ; l’opération
unitaire ne garantit pas que tous réussissent ensemble. La politique retenue
doit guider l’affichage, la relecture et le réessai, en distinguant réponse connue
et résultat inconnu. Aucun changement de contrat, de persistance ou d’ADR n’est
effectué ici. C2 demeure le lot distinct de la liste À regarder.
