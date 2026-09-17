# Roadmap 0.2.0

Date de cadrage : 15 septembre 2026. Mise à jour : 17 septembre 2026.
Statut : cadrage produit en cours.

## Objectif

Rendre Lumo agréable à utiliser au quotidien sur web, Android mobile et Android TV.
Les trois surfaces ont la même priorité ; leurs interactions sont adaptées à leur
mode d'utilisation. Le périmètre complet, les estimations et les sprints restent à fixer.

## Périmètre validé en discussion

- Une source active, mémorisée par appareil, avec changement rapide.
- Un accueil organisé en Continuer, Favoris, Direct.
- Reprise immédiate depuis Continuer, accès secondaire à la fiche, retrait de la
  rangée sur tous les appareils sans perdre la progression, réapparition après
  une nouvelle lecture.
- Tous les favoris de la source active et lancement immédiat d'une chaîne.
- Chaînes récentes dans Direct, programme en cours si disponible, accès au catalogue
  et au guide.
- Navigation adaptée aux trois surfaces et états d'accueil explicités.
- Direct avec vues Chaînes et Guide : grille horaire sur TV/web, liste sur mobile.
- Recherche unifiée par nom et titre dans la source active, résultats regroupés
  par type, sans historique des recherches.
- Bibliothèque avec chaînes favorites et liste À regarder de films et séries,
  partagée entre appareils et filtrée sur la source active.

Le détail fait foi dans [les décisions](decisions.md). Les stories ci-dessous
décrivent la cible validée, pas des fonctionnalités déjà livrées.

| Story | Résultat attendu |
|---|---|
| [US-16](../../backlog/sprint-07.md), [complément 0.2.0](../../design/0.2.0/direct-guide.md) | Consulter le guide sur les trois surfaces |
| [US-017](../../backlog/stories/US-017-home-navigation.md) | Se repérer et ouvrir son accueil |
| [US-018](../../backlog/stories/US-018-active-source.md) | Parcourir une source à la fois |
| [US-019](../../backlog/stories/US-019-continue-watching.md) | Reprendre un film ou une série |
| [US-020](../../backlog/stories/US-020-home-live.md) | Retrouver ses chaînes favorites et récentes |
| [US-021](../../backlog/stories/US-021-unified-search.md) | Rechercher chaînes, films et séries dans la source active |
| [US-022](../../backlog/stories/US-022-library-watchlist.md) | Retrouver sa bibliothèque et enregistrer films et séries pour plus tard |

## Discussions suivantes

1. Bibliothèque : préciser l'organisation des groupes et les détails de la liste validée.
2. Confort du lecteur, sources et réglages.
3. Connexion Google et éventuelle commercialisation.
4. Écrans et détails d'interaction des parcours validés, notamment Direct, guide et recherche.

Les parcours validés conservent des détails d'interaction à préciser dans leurs
documents. Les autres sujets sont candidats : leur présence ici ne vaut pas validation détaillée.
Les fonctions réservées à la v2 dans `AGENTS.md` restent hors périmètre.

## Organisation documentaire

Les décisions sont consignées au fil de la discussion, puis traduites en stories.
Les écrans seront produits à la demande de l'utilisateur et reliés aux stories.
L'état des lieux par surface devra distinguer implémentation et recette avant
estimation. Les sprints seront découpés ensuite, avec démo et recette à chaque sprint.
Les backlogs existants restent l'historique ; ils ne sont ni renumérotés ni dupliqués.

## Dépendances déjà identifiées

- US-019 : le retrait sans perte de progression n'est pas couvert par le contrat
  actuel. Le masquage partagé est validé ; le lot contractuel reste à définir.
- US-020 : l'EPG par chaîne existe ; la lecture groupée dépend du cadrage du sprint 7.
- US-16 : la grille horaire TV est un ajout au sprint 7, à découper et estimer.
- US-022 : la liste à regarder de films et séries nécessite un lot contractuel explicite.
- L'ordre agrégé et le dédoublonnage sont validés dans US-020 ; les gestes de retrait
  dans US-022. Le déplacement dans un groupe contenant plusieurs sources et les
  erreurs partielles restent à préciser.
- Aucune évolution du contrat ni de l'architecture n'est autorisée par ces documents.

La version ne sera pas déclarée prête sur la seule base de pourcentages de code :
les recettes existantes et la [dette documentée](../../backlog/dette.md) seront revues.
