# Sprint 8 — Navigation, accueil et sources

Statut : proposé, non commencé. Taille relative : L, estimation à faire après cadrage.
Référence : [plan 0.2.0 et DoD commune](../roadmap/0.2.0/delivery-plan.md).

## Objectif

Sur mobile, TV et web, ouvrir Lumo, choisir sa source et retrouver un accueil utile.
Gérer les sources avec des états compréhensibles, sans accès aux paiements ni Google.

Stories : US-017, US-018, US-024 ; première intégration US-020/US-025.
Prérequis : C4/Q4 du [registre](../roadmap/0.2.0/open-questions.md), écrans accueil,
navigation et sources précisés. Pas de changement implicite des droits ou du chiffrement.

## Tâches proposées

| ID | Travail | Surface/dépendance |
|---|---|---|
| S8-00 | Établir le parcours de référence email → source → lecture et rassembler les recettes historiques hors Google | Toutes ; point de départ de recette |
| S8-01 | Cadrer et faire valider la consultation de l'ancien catalogue ; vérifier cascades et contrat de suppression | API/contrat, C4 ; avant clients concernés |
| S8-02 | Implémenter le lot C4 approuvé et ses tests, sans confondre consultation et permission de lecture | API/contrat ; après S8-01 |
| S8-03 | Introduire le choix de source mémorisé localement, filtrage cohérent et remplacement après suppression | Android/web ; US-018 |
| S8-04 | Adapter menus latéraux et Explorer mobile ; poser l'accueil à partir des rails existants | Toutes ; après S8-03 |
| S8-05 | Aligner Mes sources, auto_sync par source, étapes, erreurs, délai de retry, confirmations et guidage TV | Toutes ; après S8-02/03 |
| S8-06 | Poser les rubriques Réglages ; retirer des parcours 0.2.0 les accès non opérationnels Google/paiement sans refondre l'auth | Toutes |
| S8-07 | Recette de la verticale, FR/EN et retour/focus ; documenter les compléments encore attendus | Toutes |

## Démo et sortie

Compte email connecté, deux sources de banc : choisir l'une sur téléphone sans
changer celle de la TV, rouvrir l'application, parcourir l'accueil et lire. Montrer
une actualisation en cours puis en erreur avec ancien catalogue, un délai serveur,
et une suppression confirmée avec remplacement correct sur un second appareil.
Tester séparément aucune/une/plusieurs sources restantes et un compte vierge.

US-018 peut se clore ; US-024 attend encore la cascade À regarder de S11. US-017
reste partielle : règles Continuer en S12, EPG en S9. Les anciens rails ne sont
pas présentés comme la totalité de la nouvelle fonctionnalité.

Fixtures : deux sources neutres, première ingestion, source en erreur et suppression
pendant consultation. Inclure les compléments du banc dans les tâches qui les utilisent.
Checks : API si modifiée, Android, web ; contrat/génération si C4 le modifie ; recette réelle.
