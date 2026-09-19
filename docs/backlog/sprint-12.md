# Sprint 12 — Continuer et reprise partagée

Statut : proposé, non commencé. Taille relative : L.
Référence : [plan et DoD commune](../roadmap/0.2.0/delivery-plan.md).

## Objectif

Retrouver une lecture au bon endroit, masquer sa carte sur tous les appareils et
la retrouver après relance, sans perdre sa progression.
Stories : US-019, clôture US-017 après S9/S11. Dépend de S8 ; arbitrages C3, Q1/Q2.

## Tâches proposées

Proposition d’écrans : [Reprise et lecteur](../design/0.2.0/resume-player.md).
Les écrans S12-E01 à E03 précisent la présentation ; C3 et Q1/Q2 restent ouverts.

| ID | Travail | Surface/dépendance |
|---|---|---|
| S12-00 | Trancher lecture effective entre sessions, réapparition, contenu court, suivant non commencé et durée inconnue | Produit, Q1/Q2 |
| S12-01 | Faire approuver C3 puis implémenter contrat/serveur et génération ; mesurer l'éligibilité sans la confondre avec position | API/clients |
| S12-02 | Instrumenter la lecture effective et garder la sauvegarde dès le début ; tests pauses, buffering, seek et seuils | Trois lecteurs |
| S12-03 | Aligner fiches Regarder/Reprendre/Recommencer/Revoir et sélection d'épisode | Trois clients |
| S12-04 | Brancher rail unifié par série, source active, masquage partagé et réapparition | Trois clients ; après S12-01 |
| S12-05 | Gérer résolution de contenus disparus, conflit/hors ligne suivant décisions, puis recette interappareils | Toutes |

## Démo et sortie

Lire brièvement : progression conservée sans carte avant le seuil validé ; dépasser
30 secondes effectives, reprendre ailleurs. Une avance manuelle n'est pas du temps
regardé. Masquer, retrouver la position depuis la fiche, relancer et retrouver la
carte selon l'arbitrage. Vérifier une carte par série et les limites exactes à 95 %
et au-delà ; la lecture continue sans lancement prématuré du suivant.

Tests : horloges/mesure, seuils, progression inconnue, isolation par source/compte,
masquage et concurrence. Checks contrat/API/Android/web et recette réelle. Finir
l'accueil US-017 seulement quand ses dépendances bibliothèque et guide sont closes.
