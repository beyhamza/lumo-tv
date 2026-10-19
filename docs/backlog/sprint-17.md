# Sprint 17 — Qualification 0.4.0

Statut : **prévu**, version **0.4.0**. Taille relative : M, à réévaluer selon les défauts.
Référence : [roadmap 0.4.0](../roadmap/0.4.0/README.md). Les recettes Android se jouent sur les
versions de la piste de test interne Google Play ([S16-bis](sprint-16-bis.md)). Mode rapide (décision du 15/10) : c'est
ici qu'a lieu la recette approfondie groupée des versions 0.3.0 et 0.4.0.

## Avancement

- [ ] S17-00 — forme de la sortie décidée — 0 %
- [ ] S17-01 — recette groupée 0.3.0 sur appareils réels — 0 %
- [ ] S17-02 — recette 0.4.0 (PiP, Cast) sur appareils réels — 0 %
- [ ] S17-03 — défauts bloquants corrigés et rejoués — 0 %
- [ ] S17-04 — checks, notes de sortie, limites connues — 0 %
- [ ] S17-05 — décision de livraison — 0 %

**Avancement global ≈ 0 %**.

## Objectif

Sortir une version démontrée sur appareils réels qui rassemble la bibliothèque, la reprise, le
lecteur et les réglages (0.3.0) et le lecteur avancé (0.4.0).

## Tâches proposées

| ID | Travail | Preuve |
|---|---|---|
| S17-00 | Décider si la 0.3.0 sort seule avant la 0.4.0 ou si une seule 0.4.0 porte les deux | Décision Hamza dans `DECISIONS-PRODUIT.md` |
| S17-01 | Rejouer les reports de la 0.3.0 : vraie TV et vrai téléphone, CW-25/27/28 et hors ligne, enchaînement d'épisodes Android, vrais changements de piste (médias multipistes), `BUG-S11-06-03`/`-05`, S11/S12/S13 non joués | Rapports dans `docs/releases/0.3.0/qa-evidence/` |
| S17-02 | PiP sur téléphone et navigateurs, Cast vers un vrai Chromecast avec les flux du banc, flux refusé | Rapports dans `docs/releases/0.4.0/qa-evidence/` |
| S17-03 | Corriger les défauts bloquants et rejouer les scénarios touchés | Régressions ciblées et nouvelle preuve |
| S17-04 | Checks contract/API/Android/web verts, changelog, notes de sortie, limites connues | Sorties CI, `RELEASE-NOTES.md` |
| S17-05 | Revue des critères de sortie ; candidate puis tag sur la fusion `release/<version> → main` ; publication seulement sur demande explicite | Décision de livraison |

## Sortie

Aucun scénario obligatoire rouge ou non joué sans décision produit tracée. Un flux qu'un
Chromecast refuse est une limite connue nommée, pas un échec masqué.
