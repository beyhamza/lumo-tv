# Sprint 14 — Qualifier la sortie gratuite 0.2.0

Statut : **clos le 14 octobre 2026 par la sortie de la 0.2.0**, sur recette partielle
assumée (voir la [matrice](../releases/0.2.0/acceptance.md) et
[`DECISIONS-PRODUIT.md`](DECISIONS-PRODUIT.md)). S14 n'a pas été joué comme un sprint
séparé : la recette de sortie s'est faite ligne par ligne du 10 au 14/10. Taille relative : M, à réévaluer selon les défauts.
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

## Avancement — clôture du 14/10/2026

- [ ] S14-00 — rassembler les rapports — 90 % : matrice tenue à jour ligne par ligne ; le
  reste des cas historiques passe en 0.3.0.
- [ ] S14-01 — recette transversale — 60 % : R020-01, 03, 14, 15, 16 joués et conformes ;
  02, 04, 05, 13 partiels, limites connues.
- [x] S14-02 — défauts bloquants corrigés et rejoués (`BUG-R020-01-01/02`, `-16-01/02`,
  `-14-01/02`, `-15-01`, `-03-01/03/04`).
- [x] S14-03 — checks contract/API/Android/web verts sur `dev` @ `f4908be`.
- [x] S14-04 — parcours gratuit, pas de Google ni paiement exposé, ancien compte conservé
  (R020-15, R020-16).
- [x] S14-05 — changelog et notes de sortie, limites connues.
- [ ] S14-06 — décision de livraison prise le 14/10 ; tag `v0.2.0` après la fusion
  `release/0.2.0 → main`. Publication : non demandée.

**≈ 78 %.** Toute ligne non jouée est une exclusion tracée, pas un succès.

## Sortie

Aucun scénario obligatoire rouge ou non joué. Toute exclusion supplémentaire
demande une décision produit tracée. Google et paiements sont hors périmètre,
pas marqués réussis. Aucune nouvelle fonctionnalité dans ce sprint. Les blocages
d'environnement ou d'appareil sont rendus visibles, sans transformer un build vert
en preuve de fonctionnement TV.
