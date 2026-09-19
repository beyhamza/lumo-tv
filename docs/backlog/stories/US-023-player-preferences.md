# US-023 — Choisir l'audio, les sous-titres et la qualité

Statut : besoin validé le 17 septembre 2026 ; capacités par lecteur à vérifier.
Version cible : 0.2.0. Surfaces : web, Android mobile, Android TV, même priorité.

## Besoin

Proposition de présentation : [écrans Reprise et lecteur](../../design/0.2.0/resume-player.md),
S13-E01 à E03, à relire.

Planification proposée : S13.
Voir le [plan 0.2.0](../../roadmap/0.2.0/delivery-plan.md) ; réalisation non commencée.

En tant qu'utilisateur, je veux choisir mes pistes et la qualité pendant la
lecture, afin d'adapter l'expérience à mes préférences et à mon appareil.

## Critères d'acceptation validés

- Pendant la lecture, un menu Audio et sous-titres et un réglage Qualité sont accessibles.
- Audio liste les pistes réellement disponibles, avec la langue lorsqu'elle est renseignée.
- Sous-titres permet de désactiver l'affichage ou de choisir une piste disponible.
- Qualité utilise Automatique par défaut ; un choix manuel est proposé lorsque
  plusieurs qualités sont réellement accessibles au lecteur.
- Sans alternative, indiquer simplement qu'aucune autre piste ou qualité n'est disponible.
- Mémoriser par appareil la langue audio préférée, la langue des sous-titres et
  leur activation. Un changement sur le téléphone ne change pas les réglages de la TV.
- Lorsque la langue audio préférée manque, utiliser la piste audio par défaut.
- Lorsque la langue de sous-titres préférée manque, ne pas activer automatiquement
  des sous-titres dans une autre langue.
- Un repli ponctuel ne remplace pas la préférence de langue mémorisée.
- Le choix manuel de qualité vaut pour la lecture en cours uniquement ; le contenu
  suivant repart sur Automatique.
- Les options reflètent les capacités réelles du flux et du lecteur sur l'appareil.
- Les libellés existent en FR/EN et les commandes sont accessibles au tactile,
  au clavier et au D-pad selon la surface.

## API et limites

Le [cadrage des écarts de design](../../design/api-gaps.md) identifie déjà les pistes
comme des informations lues par le lecteur. Aucun nouvel endpoint n'est défini :
les médias ne transitent pas par l'API et celle-ci ne choisit pas les pistes.

Vérifier les capacités des lecteurs existants, notamment dans le navigateur, avant
de chiffrer. Un badge de qualité dans le catalogue ne prouve pas que le flux propose
plusieurs variantes sélectionnables. La parité produit ne promet pas des pistes
inaccessibles sur une surface.

## Avant planification

- Auditer les commandes existantes et la persistance locale des préférences.
- Définir les libellés des pistes sans langue ou de plusieurs pistes de même langue.
- Définir les préférences initiales et le traitement des sous-titres forcés.
- Préciser le comportement lors d'une erreur de changement de piste ou de variante.
- Définir le focus à l'ouverture du menu et au retour au lecteur.
- Préciser la portée locale des préférences lors d'un changement de compte.

## Recette à préparer

Utiliser des assets de banc autorisés avec plusieurs pistes et variantes, puis
des assets sans alternative. Vérifier les sélections, la persistance sur le même
appareil, l'indépendance de deux appareils, les langues absentes, le retour d'une
langue préférée sur un autre contenu et le retour de la qualité à Automatique.
Prévoir les trois surfaces et une session TV à la télécommande. Toute fixture
manquante doit être incluse dans le chiffrage.

Référence : [décisions produit](../../roadmap/0.2.0/decisions.md).
