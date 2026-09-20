# Sprint 9 — Direct et guide sur les trois surfaces

Statut : proposé, non commencé. Taille relative : XL.
Référence : [plan et DoD commune](../roadmap/0.2.0/delivery-plan.md).

Première [proposition d’écrans](../design/0.2.0/direct-guide.md) préparée le
19 septembre 2026 ; présentation générale retenue en conversation, réalisation
non commencée et recette réelle à effectuer.

## Objectif

Voir ce qui passe, consulter les programmes et lancer une chaîne depuis le Direct.
Stories : US-16 et complément US-020. Dépend de S8 et des arbitrages C1/Q5.

Ce lot reprend [le sprint 7](sprint-07.md) et son socle : ne pas programmer S7 en
plus de S9. La cible est le [cadrage 0.2.0](../design/0.2.0/direct-guide.md), qui
ajoute notamment la grille horaire TV. Réestimer, sans réutiliser ses 34 points.

## Tâches proposées

| ID | Travail | Reprise/dépendance |
|---|---|---|
| S9-00 | Ajouter XMLTV relatif à la date, gzip, guide cassé/absent/ancien ; vérifier ingestion et rétention | Reprend S7-00 |
| S9-01 | Faire approuver C1 : lecture groupée, limites/erreurs, volume et vraie fraîcheur du guide | Reprend S7-01, après S9-00 |
| S9-02 | Implémenter contrat/serveur, générer les clients et tester limites et isolation par compte | Après S9-01 |
| S9-03 | Brancher cache/rétention Android et accès web ; mettre à jour En ce moment/Ensuite et les cartes d'accueil | Reprend S7-02/03/04 |
| S9-04 | Construire Chaînes/Guide, filtres conservés et recherche de chaînes sur les trois surfaces | Après S9-02/03 |
| S9-05 | Grilles TV/web, liste mobile et journée de chaîne, navigation temporelle et Maintenant | Étend S7-05/06 ; carte de focus TV |
| S9-06 | Fiche programme, lecture en cours, retour à la case, absences/ancienneté/erreurs | Après S9-05 |
| S9-07 | Recette intersurfaces et volume réseau mesuré, démonstration d'un changement de programme | Toutes |

## Démo et sortie

Complément du 20 septembre : [interactions et cas GD-01 à GD-14](../design/0.2.0/guide-interactions.md)
pour S9-00 et S9-04 à S9-07. Les règles de navigation Q5 sont précisées ; la
fraîcheur et la lecture groupée restent soumises à C1. Le schéma de focus complète
les écrans S9-E01 à S9-E05 ; aucune recette applicative n’est déclarée exécutée.

Changer de filtre entre Chaînes et Guide ; naviguer d'hier à J+3 selon les données,
revenir à Maintenant, ouvrir un programme courant et lancer le direct, revenir à
la case. Montrer le mobile en liste et la TV entièrement à la télécommande.
Tester guide absent/partiel/périmé, programme futur/passé sans lecture, programme
qui change et perte réseau. Les cases visibles ne produisent pas un appel par chaîne :
preuve réseau de la lecture groupée bornée.

Checks : contrat, API et ingestion PostgreSQL, Android/cache, web et statique marketing.
US-16 se clôture avec recette des trois surfaces ; US-020 gagne ses informations EPG.
