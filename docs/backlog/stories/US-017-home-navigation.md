# US-017 — Retrouver mon accueil et mes destinations

Statut : besoin validé, détails d'interaction et recette à préparer. Version cible : 0.2.0.
Surfaces : web, Android mobile, Android TV, même priorité.

## Besoin

Planification proposée : S8 pour le socle ; clôture S12 après les compléments S9/S11.
Voir le [plan 0.2.0](../../roadmap/0.2.0/delivery-plan.md) ; réalisation non commencée.

En tant qu'utilisateur, je veux un accueil et une navigation faciles à comprendre,
afin de retrouver rapidement quoi regarder sur chacun de mes appareils.

## Critères d'acceptation

- À l'ouverture de l'espace connecté, l'accueil présente Continuer, Favoris et Direct
  dans cet ordre, pour la source active.
- Une section sans élément est masquée sans espace réservé.
- Sans source, un parcours d'ajout est proposé ; la TV accompagne l'ajout depuis
  le téléphone ou le web.
- Avec une source prête mais aucun historique ni favori, les catalogues disponibles
  sont accessibles et une invitation à explorer remplace les rangées vides.
- Pendant une synchronisation, l'avancement est visible et le catalogue déjà
  disponible reste consultable.
- Une erreur de source est expliquée avec une action adaptée.
- TV et web : menu latéral Accueil, Direct, Films, Séries, Ma bibliothèque.
- Mobile : Accueil, Explorer, Bibliothèque, Réglages ; Explorer réunit Direct,
  Films et Séries.
- Recherche, source active et réglages restent accessibles selon la surface.
- Les libellés existent en FR/EN et tous les contrôles TV sont accessibles au D-pad.

## Dépendances et API

US-018, US-019 et US-020 alimentent cet accueil. Sources et états existent dans
`packages/contracts/openapi.yaml` ; aucune opération nouvelle n'est définie ici.
L'accès au guide dépend d'US-16. La recherche et la liste à regarder ont encore
besoin de leur propre cadrage : une destination ne vaut pas implémentation.

## Avant planification

Spécifier les chargements, erreurs partielles, destinations sans contenu et parcours
de focus. Relier les futurs écrans et préparer une recette sur les trois surfaces,
avec télécommande réelle pour la TV.

Référence : [décisions produit](../../roadmap/0.2.0/decisions.md).
