# Sprint 12 — Continuer et reprise partagée

Statut : **ouvert le 15 octobre 2026**, version **0.3.0**. Taille relative : L. Mode rapide
(décision du 15/10) : chaque lot livre ses tests automatisés et une vérification rapide du chemin
principal ; pas de passe de recette dédiée, les bugs vont au backlog, la recette approfondie est
groupée avant la sortie 0.3.0.

## Avancement

- [ ] S12-00 — règles CW-17→22 et CW-29→33 — 90 % : [ADR 0012](../adr/0012-continue-watching-protocol.md)
  acceptée le 15/10 ; appliquées par le serveur et les trois clients.
- [x] S12-01 — contrat C3, serveur, génération — fusionné (PR #70) : `ContinueWatchingIntegrationTest`
  14/14, suite API 398/398.
- [ ] S12-02 — mesure de la lecture effective — 85 % : compteur de segments réels sur le web
  (`WatchMeter`) et sur Android (`WatchedSegmentTracker`, Media3), envoyé avec chaque sauvegarde avec
  `observed_at` et `session_started_at`. Reste : lecture réelle sur appareil (les flux du banc ne sont
  pas joignables depuis l'émulateur), renvoi des segments d'une sauvegarde échouée sur Android (S12-05).
- [ ] S12-03 — fiches Regarder / Reprendre / Recommencer / Revoir — 85 % : web et Android (mobile, TV),
  épisode à poursuivre sur la fiche série. Parcours série non vérifié (banc sans progression de série).
- [ ] S12-04 — rail Continuer unifié, masquage partagé — 95 % : rail piloté par `in_continue`, une carte
  par série avec l'épisode suivant (CW-12/15/19/21), « Retirer de Continuer » (web, mobile, appui long OK
  sur TV) ; la carte reprend directement (S12-05).
- [ ] S12-05 — contenus disparus, conflits, hors ligne — 85 % (18/10) : reprise directe depuis une carte
  Continuer (web, mobile, TV) ; échec de lancement avec Réessayer / Retour, progression conservée
  (CW-25) ; épisode suivant indisponible → « Retour à la série », jamais un autre épisode (CW-27) ;
  contenu absent d'une réponse réussie → carte « Indisponible » avec seulement « Retirer de Continuer »
  (CW-28) ; Android garde en mémoire les sauvegardes échouées et les renvoie au retour du réseau.
  **Correctif trouvé en route** : sur Android la reprise repartait toujours de 0 (la demande de position
  partait avant que le flux l'accepte) — corrigé et vérifié sur émulateur. Branches
  `feat/S12-05-web` (502/502, reprise directe vérifiée contre l'API) et `feat/S12-05-android`
  (834/834, temps regardé réel et reprise vérifiés sur le téléphone). Non vérifiés sur appareil :
  CW-25, CW-27, CW-28, hors ligne, application TV.

Branches : `feat/S12-web-continue` (Vitest 484/484, vérifié sur un film contre l'API) et
`feat/S12-android-continue` (tests 806/806, rail, retrait et fiches vérifiés sur émulateur).
Question produit en attente : un épisode « terminé » par une simple avance passe-t-il la main au
suivant dans Continuer ?

**Avancement global ≈ 90 %**.
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
