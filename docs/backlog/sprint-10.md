# Sprint 10 — Recherche unifiée

Statut : proposé, non commencé. Taille relative : M.
Référence : [plan et DoD commune](../roadmap/0.2.0/delivery-plan.md).

[Proposition d’écrans](../design/0.2.0/unified-search.md) préparée le 19 septembre
2026 pour les trois surfaces ; relecture visuelle à effectuer.
Les [règles Q9](../design/0.2.0/search-interactions.md) précisent maintenant
les choix d’interaction et les cas SR-01 à SR-15 ; recette non exécutée.

## Objectif

Une saisie retrouve chaînes, films et séries de la source active sur les trois surfaces.
Story : [US-021](stories/US-021-unified-search.md). Dépend de S8 ; Q9 cadré,
capacités réelles et cohérence de la description contractuelle à vérifier.
Le guide S9 n'est pas un prérequis technique. Les opérations `q` existantes suffisent
au périmètre nominal ; tout manque découvert est remonté avant extension du contrat.

## Tâches proposées

| ID | Travail | Surface |
|---|---|---|
| S10-00 | Vérifier les choix Q9 : 350 ms, aperçu de 4, pages de 20, changement de source et clavier de plateforme ; contrôler parité et description contractuelle | Design/clients |
| S10-01 | Composer les recherches existantes, annuler/ignorer les réponses obsolètes, préserver la sémantique partielle et la casse | Android/web |
| S10-02 | Construire les sections/filtres Tous, Chaînes, Films, Séries et Voir tous | Trois clients |
| S10-03 | Brancher lecture directe, fiches et retour avec saisie/filtre/position conservés | Trois clients |
| S10-04 | Traiter champ vide, aucun résultat, erreur partielle, hors ligne ; aucune conservation d'historique | Trois clients |
| S10-05 | Recette avec gros catalogue neutre et saisie rapide, clavier/télécommande, deux sources | Toutes |

## Démo et sortie

Rechercher un fragment avec une casse différente, filtrer un type, ouvrir une fiche
puis retrouver sa place. Lancer une chaîne et revenir ; changer de source pendant
une saisie sans voir de résultats de l'ancienne. Provoquer l'échec d'une section
sans masquer les autres, puis réessayer. Le champ vide ne charge pas tout le catalogue.
Tests pertinents : composition, réponses tardives et pagination ; checks Android/web.
US-021 se clôture après recette sur les trois surfaces.
