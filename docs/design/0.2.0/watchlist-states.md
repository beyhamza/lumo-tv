# À regarder — indisponibilité, hors ligne et conflits

Date : 19 septembre 2026. Statut : règles produit retenues ; C2 à définir, développement et recette non commencés.
Références : [US-022](../../backlog/stories/US-022-library-watchlist.md),
[bibliothèque](library.md), [sprint 11](../../backlog/sprint-11.md).

## Décision validée : contenu disparu

Lorsqu’un film ou une série enregistré disparaît du catalogue après actualisation,
conserver sa carte dans À regarder, avec l’indication Indisponible et une action
de retrait manuel. Une indisponibilité ne supprime ni le choix de l’utilisateur
ni sa progression. La série reste enregistrée comme un ensemble.

Une erreur réseau seule ne prouve pas la disparition du contenu. La suppression
confirmée de la source suit le parcours US-024 déjà défini : les éléments de cette
source sont supprimés de la liste. Conserver une carte indisponible ne s’applique
donc pas à une source supprimée du compte.

La représentation d’un contenu absent doit être couverte par C2 : la liste ne
peut pas dépendre uniquement d’une fiche résolue avec succès. Définir quelles
informations restent affichables et comment retirer cette entrée sans sa fiche,
sans inventer ici de schéma de stockage ni de nouveau champ API.

## Hors ligne et conflits — recommandations retenues le 19 septembre 2026

L’utilisateur a accepté les recommandations en attente et confié la suite du
cadrage à l’agent. Les choix ci-dessous complètent la conservation de carte validée.

| Sujet | Décision |
|---|---|
| Hors ligne | Consulter la liste déjà disponible, avec message hors ligne ; attendre la reconnexion pour ajouter ou retirer |
| Ajout et retrait concurrents | La dernière action acceptée par le serveur détermine l’appartenance à la liste |

La consultation ne promet pas une vidéo hors ligne ni une conservation
persistante de la liste. Vérifier les capacités de chaque surface ; aucune
stratégie de cache nouvelle n’est décidée par cet écran.

- Sans connexion à Lumo, les ajouts et retraits sont indisponibles avec une
  explication. Aucune file d’écritures différées n’est introduite.
- Au retour de connexion, relire l’état partagé avant de réactiver les mutations
  sur une vue confirmée. Aucun changement local hors ligne n’est à rejouer.
- L’ordre d’acceptation serveur départage les nouvelles intentions ; il ne dépend
  pas de l’heure des appareils. Un réessai technique d’une même action ne doit
  pas devenir une intention nouvelle ni modifier à nouveau le tri.
- Une action dont la réponse a été perdue reste non confirmée : relire son état
  et respecter les garanties de C2 avant un réessai. Ne pas annoncer sa réussite
  ni la recréer automatiquement pour écraser une action concurrente plus récente.

## Comportements de présentation retenus pour le cadrage

Les comportements suivants sont retenus dans la continuité des recommandations.
Les libellés et la composition visuelle restent des propositions à relire.

| État | Présentation et actions proposées |
|---|---|
| Liste vide confirmée | « Votre liste est vide » et accès aux catalogues disponibles de la source active |
| Filtre sans résultat | Garder les filtres et proposer Tous ; ne pas déclarer toute la liste vide |
| Chargement initial | Indicateur de chargement ; ne pas afficher brièvement un faux état vide |
| Chargement échoué | Explication et Réessayer ; conserver les éléments déjà disponibles |
| Contenu disparu | Carte avec titre connu, Indisponible et Retirer de ma liste ; ne pas proposer une lecture qui exige une fiche absente |
| Résultat d’écriture inconnu | « Modification non confirmée » ; vérifier l’état avant d’annoncer réussite ou échec définitif |
| Écriture refusée | Conserver ou rétablir l’état confirmé, avec explication et réessai adapté |

Conserver au minimum un titre connu et le type de contenu pour identifier une
carte indisponible ; utiliser un visuel de remplacement si son affiche ne peut
plus être chargée. C2 doit rendre ces informations accessibles sans la fiche.
Si le même contenu revient avec une identité reconnue, sa carte redevient
disponible sans nouvel ajout ni changement de rang. Ne pas associer automatiquement
un contenu homonyme : les règles d’identité sont à expliciter dans C2.

Les filtres suivent les types présents dans la liste, y compris les cartes
indisponibles. S’il n’y a qu’un type, ne pas afficher un sélecteur redondant.
La liste vide conserve l’accompagnement vers les catalogues disponibles ; un
échec de chargement ne devient jamais une liste vide confirmée.

Pour une carte indisponible, proposer un panneau compact avec le titre connu et
le retrait, utilisable au tactile, au clavier et au D-pad. Le panneau n’est pas
une fausse fiche de catalogue. Garder le focus sur le déclencheur à la fermeture ;
après retrait, proposer le voisin restant ou le titre de la section vide.

## Cas de recette à préparer

| ID | Scénario | Attente |
|---|---|---|
| WL-01 | Un film enregistré disparaît après actualisation confirmée | Carte conservée, Indisponible, retrait manuel accessible |
| WL-02 | Une série enregistrée disparaît | Une seule carte de série indisponible, sans création de cartes par épisode |
| WL-03 | Retrait manuel d’une carte indisponible | Retrait partagé après confirmation du serveur, progression indépendante |
| WL-04 | Une requête de fiche échoue sur le réseau | Ne pas assimiler cette erreur à une disparition confirmée |
| WL-05 | Suppression confirmée de la source | Appliquer la cascade de suppression de la liste prévue dans US-024/C2, sans cartes orphelines |
| WL-06 | Perte de connexion avec liste déjà disponible | Conserver la consultation avec indication hors ligne ; ajout/retrait indisponibles, sans écriture différée |
| WL-07 | Hors ligne sans liste accessible | Message d’indisponibilité de chargement, sans inventer une liste vide ni des données |
| WL-08 | Retour de connexion après consultation d’une liste ancienne | Relire l’état partagé avant de réactiver les mutations ; aucune action locale à rejouer |
| WL-09 | Ajout sur un appareil et retrait sur un autre | La dernière nouvelle action acceptée par le serveur gagne ; tester ajout puis retrait et retrait puis ajout |
| WL-10 | Réponse perdue après une action peut-être acceptée | Résultat inconnu ; relecture et réessai à encadrer par C2 |
| WL-11 | Plusieurs réessais d’un même ajout | Une seule entrée ; le réessai ne change pas la date d’ajout ni le rang, garantie à définir dans C2 |
| WL-12 | Une autre action intervient avant un réessai retardé | Éviter de confondre réessai technique et nouvelle intention ; garantie à définir dans C2 |
| WL-13 | Lecture commencée ou terminée | L’élément reste dans À regarder, règle déjà validée |
| WL-14 | Changement de source active | Afficher seulement la liste de cette source sans effacer l’autre |
| WL-15 | Retour au catalogue du contenu reconnu comme identique | Carte disponible à sa place initiale, sans doublon ni nouvel ajout |
| WL-16 | Un contenu différent porte le même titre que l’élément disparu | Ne pas fusionner sur le seul titre |
| WL-17 | Liste avec un seul type, puis deux types | Omettre le filtre redondant ; proposer Tous/Films/Séries quand utile, sans masquer les cartes indisponibles |

Exécuter sur les trois surfaces, FR/EN, avec deux comptes et deux sources de banc.
Vérifier séparément disponibilité des métadonnées, appartenance à la liste,
progression et accessibilité. Aucune ligne n’est actuellement déclarée jouée.

## Préparation C2 et limites

Le contrat actuel ne couvre pas la liste À regarder. Les décisions produit
préparent S11-01 ; elles n’autorisent pas un endpoint improvisé ni le détournement
des favoris de chaînes. C2 doit couvrir appartenance, tri, retrait sans fiche,
contenu disparu, suppression de source et garanties de concurrence/réessai.

Restent à définir dans C2 : identité lors d’un retour au catalogue, données
accessibles après disparition, ordre d’acceptation des mutations et identification
des réessais. Vérifier les capacités de consultation sans réseau sur chaque
surface. Une évolution de persistance/cache suit la procédure ADR applicable.

## Correspondance Plane — consultation du 19 septembre 2026

[Page du sprint 11](http://localhost:8585/lumo-tv/projects/7c52f258-8226-4f83-a956-faac0a389773/pages/bdd1eb71-3b6e-4599-8d7d-7fcfae1115a3/).
Lecture seule ; aucun statut, responsable, cycle ou contenu Plane modifié.

| Référence | Élément Plane observé | Complément à reporter lors d’une synchronisation autorisée |
|---|---|---|
| US-022 | Élément 22, Backlog | Règles Q3 et décisions Q8 de cette discussion |
| S11-00 | Élément 136, Backlog | Cas FO et WL ; séparer décisions acquises et garanties à vérifier |
| C2 / S11-01 | Éléments 164 Todo / 137 Backlog | Retrait sans fiche, contenu indisponible et garanties de synchronisation |
| S11-04/05/06 | Éléments 140/141/142, Backlog | États de liste, conflits et recette WL-01 à WL-17 |
| Q3 / Q8 | Éléments 169 / 174, Todo | Décisions produit consignées localement ; garanties techniques et contrat encore à préparer |

Cette correspondance est un instantané, pas une copie faisant autorité sur les
statuts futurs. Le travail S8 de l’autre agent et les fichiers partagés de cadrage
C4/Q4 ne sont pas modifiés dans ce lot documentaire.
