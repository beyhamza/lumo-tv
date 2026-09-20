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

## Règles de réalisation du socle — arrêtées le 20 septembre 2026 (S8-04)

Ce que S8 livre, et ce qu'il laisse volontairement aux sprints suivants. Règle
commune : **aucune commande inopérante** — ce qui n'est pas livré n'est pas affiché.

| Élément | Livré en S8-04 | Laissé à |
|---|---|---|
| Continuer | Films et épisodes en cours de la source active, fusionnés, du plus récent au plus ancien, une carte par série, 12 au plus. Action principale : reprendre ; secondaire : la fiche | Retrait partagé et règles d'apparition : S12 |
| Favoris | Favoris de la source active, chaque chaîne une fois selon l'ordre des groupes puis des chaînes (US-020), 12 au plus, puis Voir tous | Gestes d'organisation : S11 |
| Direct | Chaînes récentes de la source active, 12 au plus, et Toutes les chaînes | Programme en cours et Guide TV : S9 |
| Web et TV | Accueil, Direct, Films, Séries, Ma bibliothèque. Sources, Appareils et Abonnement restent en second groupe sur le web jusqu'à S8-06 | Recherche : S10 |
| Mobile | Accueil, Explorer (Direct, Films, Séries), Bibliothèque, Réglages. Mes sources s'ouvre depuis le sélecteur et les Réglages | Recherche : S10 |
| Ma bibliothèque | Les chaînes favorites de la source active ; sur TV, une grille simple, OK pour lire | À regarder et groupes sur TV : S11 |
| Arrivée | L'application s'ouvre sur l'accueil ; Retour depuis une autre destination ramène à l'accueil | — |

États : sans source, parcours d'ajout (guidage sur TV) ; choix de source à faire,
invitation à choisir ; rien à montrer, invitation à explorer plutôt que trois
rangées vides ; synchronisation ou erreur, un bandeau avec l'étape ou la cause
réelles, et les rangées restent affichées dès qu'un catalogue précédent existe
([C4](../../roadmap/0.2.0/c4-previous-catalogue.md)). L'alignement des écrans
Direct, Films et Séries sur ces états est fait en S8-05.

L'ancienne règle qui écartait les rangées des grilles TV (S2-13, S4-08) reste
valable pour les grilles ; l'accueil validé le 19 septembre prévaut pour l'accueil.

## Avant planification

Spécifier les chargements, erreurs partielles, destinations sans contenu et parcours
de focus. Relier les futurs écrans et préparer une recette sur les trois surfaces,
avec télécommande réelle pour la TV.

Référence : [décisions produit](../../roadmap/0.2.0/decisions.md).
