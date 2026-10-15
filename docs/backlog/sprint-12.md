# Sprint 12 — Continuer et reprise partagée

Statut : **ouvert le 15 octobre 2026**, version **0.3.0**. Taille relative : L. Mode rapide
(décision du 15/10) : chaque lot livre ses tests automatisés et une vérification rapide du chemin
principal ; pas de passe de recette dédiée, les bugs vont au backlog, la recette approfondie est
groupée avant la sortie 0.3.0.

## Avancement

- [ ] S12-00 — règles CW-17→22 et CW-29→33 — 80 % : portées par l'[ADR 0012](../adr/0012-continue-watching-protocol.md),
  acceptée par Hamza le 15/10 ; il reste l'application côté clients (S12-02/04).
- [ ] S12-01 — contrat C3, serveur, génération — 90 % : `PUT /me/progress` accepte
  `watched_segments`, `observed_at`, `session_started_at` ; `POST /me/progress/hide` ;
  `PlaybackProgress` porte `watched_ms`, `hidden`, `in_continue` calculés par le serveur ;
  migration `0022` (les progressions existantes gardent leur place) ;
  `ContinueWatchingIntegrationTest` 14/14. Branche `feat/S12-01-continue-watching`.
- [ ] S12-02 — mesure de la lecture effective dans les lecteurs — 0 %
- [ ] S12-03 — fiches Regarder / Reprendre / Recommencer / Revoir — 0 %
- [ ] S12-04 — rail Continuer unifié par série, masquage partagé — 0 %
- [ ] S12-05 — contenus disparus, conflits, hors ligne — 0 %

**Avancement global ≈ 28 %**.
Référence : [plan et DoD commune](../roadmap/0.2.0/delivery-plan.md).

## Objectif

Retrouver une lecture au bon endroit, masquer sa carte sur tous les appareils et
la retrouver après relance, sans perdre sa progression.
Stories : US-019, clôture US-017 après S9/S11. Dépend de S8 ; arbitrages C3, Q1/Q2.

## Tâches proposées

Proposition d’écrans : [Reprise et lecteur](../design/0.2.0/resume-player.md).
Les écrans S12-E01 à E03 précisent la présentation ; les règles principales Q1/Q2
sont acquises. C3 reste à définir, notamment ordre des événements et propagation
hors ligne ; aucune recette n’est clôturée.

Préparation de S12-00/02/05 : [cas Continuer CW-01 à CW-33](../design/0.2.0/continue-watching-cases.md).
Les cas validés, arbitrages et propositions d’erreur y sont distingués ; recette non exécutée.

| ID | Travail | Surface/dépendance |
|---|---|---|
| S12-00 | Appliquer CW-17 à CW-22 et CW-29 à CW-31 validés ; préparer les garanties d’ordre, de réessai et de propagation hors ligne de CW-32/33 pour C3 | Produit/contrat ; Q1/Q2 acquis pour ces règles |
| S12-01 | Faire approuver C3 puis implémenter contrat/serveur et génération ; mesurer l'éligibilité sans la confondre avec position | API/clients |
| S12-02 | Instrumenter la lecture effective et garder la sauvegarde dès le début ; tests pauses, buffering, seek et seuils | Trois lecteurs |
| S12-03 | Aligner fiches Regarder/Reprendre/Recommencer/Revoir et sélection d'épisode | Trois clients |
| S12-04 | Brancher rail unifié par série, source active, masquage partagé et réapparition | Trois clients ; après S12-01 |
| S12-05 | Gérer résolution de contenus disparus, conflit/hors ligne suivant décisions, puis recette interappareils | Toutes |

## Démo et sortie

Lire brièvement : progression conservée sans carte avant le seuil validé ; dépasser
30 secondes effectives, reprendre ailleurs. Une avance manuelle n'est pas du temps
regardé. Masquer, retrouver la position depuis la fiche, relancer et retrouver la
carte dès le démarrage réel. Vérifier une carte par série et les limites exactes à 95 %
et au-delà ; la lecture continue sans lancement prématuré du suivant.

Complément du 19 septembre : démontrer 20 s puis 10 s sur deux appareils, une
réapparition dès le démarrage réel après retrait et une carte proposant le suivant
non commencé. Ne pas cumuler du temps entre deux épisodes distincts.
Vérifier aussi CW-20 à CW-22 : contenu court terminé absent, suivant déjà terminé
relu depuis zéro dans l’ordre et Recommencer sans nouvelle attente pour la carte
éligible, sans modifier les progressions des autres épisodes.
Complément concurrence : deux lectures pendant les mêmes 20 s ne donnent que
20 s éligibles ; une lecture plus récente à 12 min prime sur une ancienne à 40 min.
Un retrait pendant une session déjà en cours conserve le masque malgré ses
sauvegardes, jusqu’à un nouveau démarrage réel après ce retrait (CW-29 à CW-31).

Tests : horloges/mesure, seuils, progression inconnue, isolation par source/compte,
masquage et concurrence. Checks contrat/API/Android/web et recette réelle. Finir
l'accueil US-017 seulement quand ses dépendances bibliothèque et guide sont closes.
