# Sprint 14 — Qualifier la sortie gratuite 0.2.0

Statut : proposé, non commencé. Taille relative : M, à réévaluer selon les défauts.
Référence : [plan et DoD commune](../roadmap/0.2.0/delivery-plan.md).

## Objectif

Disposer d'une version candidate démontrée sur web, mobile et TV, prête à être
publiée. Ce lot ne remplace pas les recettes de chaque sprint et n'ouvre aucun
chantier Google, paiement, droits ou chiffrement.

Entrée : S8–S13 clos, évolutions de contrat couvertes, rapports disponibles.
Référence de sortie : [recette 0.2.0](../releases/0.2.0/acceptance.md).
Préparation : [sessions, rapports et dossier de livraison](../releases/0.2.0/execution-plan.md).
Ce plan est non exécuté ; aucun build candidat n’est déclaré prêt.

Préparation visuelle : [couverture S9 à S14 et preuves attendues](../design/0.2.0/screen-coverage.md)
(revue du 20 septembre), pour S14-00/01. Les maquettes ne remplacent pas les
captures du build candidat ni les parcours à la télécommande ; scénarios non joués.

## Tâches proposées

| ID | Travail | Preuve |
|---|---|---|
| S14-00 | Rassembler rapports et reprendre les cas email/activation/session historiques non joués | Matrice vert/rouge/non joué/hors périmètre |
| S14-01 | Recette transversale de tous les parcours sur les trois surfaces, FR/EN, erreurs, deux appareils et sources | Rapports de sessions sur build identifié |
| S14-02 | Corriger les défauts bloquants et rejouer les scénarios affectés | Régressions ciblées et nouvelle preuve |
| S14-03 | Exécuter checks contract/API/Android/web, contrôler marketing statique et conformité du livrable | Sorties de checks/CI |
| S14-04 | Vérifier parcours gratuit, aucune invitation non opérationnelle Google/paiement, ancien compte et données conservés | Recette install/migration et navigation |
| S14-05 | Mettre à jour changelog/version des artefacts concernés, notes de sortie, limites connues, procédure de livraison | Version candidate traçable |
| S14-06 | Revue des critères de sortie et préparation du lot de publication | Décision de livraison ; publication seulement sur demande explicite |

## Sortie

Aucun scénario obligatoire rouge ou non joué. Toute exclusion supplémentaire
demande une décision produit tracée. Google et paiements sont hors périmètre,
pas marqués réussis. Aucune nouvelle fonctionnalité dans ce sprint. Les blocages
d'environnement ou d'appareil sont rendus visibles, sans transformer un build vert
en preuve de fonctionnement TV.
