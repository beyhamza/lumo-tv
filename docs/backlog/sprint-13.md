# Sprint 13 — Lecteur et réglages

Statut : proposé, non commencé. Taille relative : XL.
Référence : [plan et DoD commune](../roadmap/0.2.0/delivery-plan.md).

## Objectif

Adapter la lecture à chaque appareil et poursuivre une série selon ses préférences.
Stories : US-023, US-025 et complément US-15. Dépend de S8 et des règles de reprise
S12 ; décisions Q2/Q6/Q7 avant les tâches concernées.

## Tâches proposées

Complément du 23 septembre : [décisions Q6/Q7, états et cas PS-01 à PS-13](../design/0.2.0/player-settings-cases.md).
Ce document précise les règles produit pour S13-00 à S13-06 ; les capacités des
lecteurs, la détection réseau et la recette restent à vérifier. Aucun changement
du sprint 8 ou de la réalisation S8-06 n’est inclus.

Proposition d’écrans : [Reprise et lecteur](../design/0.2.0/resume-player.md).
Les écrans S13-E01 à E04 illustrent les commandes et l’enchaînement ; ils ne
valident pas les capacités des lecteurs ni les arbitrages Q2/Q6/Q7.

Suite : [Réglages](../design/0.2.0/settings.md), S13-E05 à E10, pour S13-04/05/06.
Q6/Q7 cadrés côté produit le 23 septembre dans le complément ci-dessus, notamment
reprise explicite après retour du Wi-Fi ; réalisation et recette non exécutées.

| ID | Travail | Surface/dépendance |
|---|---|---|
| S13-00 | Matrice des capacités des lecteurs et fixtures autorisées multi-audio/sous-titres/variantes, dont pistes non supportées | Toutes ; avant promesse de parité |
| S13-01 | Compléter l'audio existant, exposer sous-titres et qualité avec états honnêtes selon appareil | Android/web |
| S13-02 | Préférences locales de langue/activation, replis, retour qualité Automatique au contenu suivant | Trois clients |
| S13-03 | Préférence d'enchaînement, mode manuel, décomptes 10 s TV/5 s mobile-web ; suivant déjà commencé | Trois clients ; reprise S12 |
| S13-04 | Finaliser cinq rubriques Réglages, langue d'interface, compte/appareils existants, aide/version/pages d'information | Toutes |
| S13-05 | Bloquer les lancements vidéo hors Wi-Fi selon préférence, y compris autoplay, selon Q7 | Android mobile |
| S13-06 | Recette des capacités et préférences sur appareils distincts ; focus des menus lecteur | Toutes |

## Démo et sortie

Changer audio/sous-titres, lire un autre contenu avec langue absente puis présente ;
ne pas réutiliser un identifiant de piste d'un autre média. Montrer qualité manuelle
puis retour Auto, options indisponibles, autoplay désactivé puis décompte annulé,
transition de saison et dernier épisode. Le seuil de 95 % ne lance pas le suivant.
Couper Wi-Fi selon le scénario approuvé et vérifier que le catalogue reste accessible.

Les capacités web limitées doivent être identifiées en S13-00, sans promettre des
pistes inaccessibles ni relayer les flux. Si une fonction validée ne peut être livrée,
soumettre l'écart au lieu de la déclarer terminée. Checks Android/web, tests des
préférences/replis/réseau et fixture incluse dans chaque tâche qui en a besoin.
La [recette finale](../releases/0.2.0/acceptance.md) précise les appareils à couvrir.
