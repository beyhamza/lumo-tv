# US-020 — Retrouver mes chaînes favorites et récentes

Statut : besoin validé, ordre entre groupes à préciser. Version cible : 0.2.0.
Surfaces : web, Android mobile, Android TV.

## Besoin

En tant qu'utilisateur, je veux retrouver mes chaînes habituelles sur l'accueil,
afin de lancer le direct rapidement.

## Critères d'acceptation validés

- Favoris rassemble tous les favoris de la source active, dans l'ordre choisi.
- Sélectionner une chaîne favorite lance immédiatement le direct.
- Voir tous les favoris ouvre la bibliothèque avec accès aux groupes et à leur organisation.
- Direct présente les chaînes récemment regardées de la source active.
- Le programme en cours est affiché lorsqu'il est disponible.
- Toutes les chaînes ouvre le catalogue ; Guide TV ouvre le guide.
- Une rangée vide est masquée. Les libellés sont disponibles en FR/EN et les actions
  accessibles sur chaque surface, notamment au D-pad.

## API et dépendances

Favoris, groupes, chaînes récentes et résolution des chaînes par identifiants
existent dans le contrat. Les favoris portent leur source ; la lecture les ordonne
par groupe puis position. L'ordre dans un groupe peut être modifié.

Le contrat permet une même chaîne dans plusieurs groupes. L'ordre global et le
traitement de ces occurrences sur l'accueil doivent donc être tranchés avant
implémentation ; « tous les favoris » ne décide pas implicitement du dédoublonnage.

Le guide par chaîne existe. Pour les programmes de plusieurs cartes, reprendre
[US-16 et le sprint 7](../sprint-07.md) : sa lecture groupée reste une dépendance
contractuelle à traiter explicitement, sans multiplier les appels par carte.

## Avant planification

Fixer l'ordre entre groupes, les doublons, le nombre de cartes et les actions de
la rangée Direct. Définir les cas de chaîne disparue, guide absent ou périmé,
erreur de lecture et retour à l'accueil. Réutiliser les recettes favoris existantes.

Référence : [décisions produit](../../roadmap/0.2.0/decisions.md).
