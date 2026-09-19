# Sprint 8 — Proposition d’écrans

Date : 17 septembre 2026. Mise à jour : 19 septembre 2026.
Statut : disposition de l’accueil validée ; autres écrans à relire ; développement non commencé.

Références : [sprint 8](../../backlog/sprint-08.md), [design system](../design-system.md),
[décisions produit](../../roadmap/0.2.0/decisions.md).

## Intention

Présenter un accueil utile et un choix de source visible sur les trois surfaces.
La maquette interactive présentée dans la conversation permet de passer entre web,
mobile et TV, puis de comparer les états avec contenu, sans historique, sans source,
en actualisation et en erreur. Les données et visuels sont fictifs.

La charte Spectre est reprise : fond sombre, surfaces sobres, accent cyan/violet
réservé à la marque, au focus et à la progression. La disposition et la densité
des autres écrans restent des propositions. La disposition de l’accueil avec
Continuer, Favoris et Direct a été validée le 19 septembre 2026.

## Inventaire et rattachement

| Écran | Contenu et actions proposés | Story / tâche |
|---|---|---|
| S8-E01 Accueil | Continuer, Favoris, Direct ; source active visible ; lecture et accès secondaire aux destinations | US-017/020, S8-04 |
| S8-E02 Accueil vierge | Sans source : ajouter ou guidage TV ; sans historique : accès au catalogue, aucune rangée vide | US-017, S8-04 |
| S8-E03 Navigation | Menu latéral web/TV ; navigation mobile avec Explorer ; accès aux sources et réglages | US-017, S8-04 |
| S8-E04 Choix de source | Source actuelle identifiée ; changement limité à l’appareil ; accès à Mes sources | US-018, S8-03 |
| S8-E05 Mes sources | État, volumes, sélection, actualisation ; gestion complète sur web/mobile et guidage TV | US-024, S8-05 |
| S8-E06 Ajouter | Nom, choix M3U/Xtream, champs vides adaptés au type ; première synchronisation | US-024, S8-05 |
| S8-E07 Actualisation / erreur | Étapes identifiées, ancien catalogue, erreur compréhensible et réessai | US-024, S8-01/02/05 |
| S8-E08 Renommer / supprimer | Formulaire simple ; confirmation des conséquences ; remplacement de la source active | US-018/024, S8-03/05 |
| S8-E09 Gestion depuis la TV | Choisir et actualiser ; explication pour ajouter/modifier depuis mobile ou web | US-024, S8-05 |
| S8-E10 Réglages | Entrées Compte et appareils, Sources, Lecture, Application, Aide et informations | US-025, S8-06 |

## Adaptation aux surfaces

Interaction du sélecteur validée le 19 septembre : nom, état, coche de la source
active et changement immédiat. Conserver la rubrique ouverte, réinitialiser les
filtres propres à l’ancienne source et quitter une fiche vers le catalogue
correspondant. Ces critères complètent la proposition ; le prototype initial
n’a pas été mis à jour pour simuler tous ces cas.

- Web : navigation persistante, accueil aéré et gestion des sources dans la page.
- Mobile : priorité à la reprise ; seconde carte Continuer compacte proposée pour
  limiter la hauteur ; actions de gestion accessibles au toucher.
- TV : commandes et titres agrandis, focus visible et parcours directionnel.
  La présentation dans la conversation se réorganise selon la largeur disponible :
  elle ne valide pas une composition physique 1920 × 1080 ni une lecture à trois mètres.

Focus cible à vérifier lors de l’implémentation : première reprise à l’accueil,
ajout en état vierge, source actuelle dans le sélecteur, Annuler dans une confirmation
destructive. Fermer un dialogue doit rendre le focus à son déclencheur. La navigation
à la télécommande, les marges TV et le retour système nécessitent une recette réelle.

## Limites du prototype et périmètre de livraison

Toutes les interactions sont simulées en mémoire. Aucun catalogue, compte, service
de lecture ou identifiant réel n’est connecté. L’ajout et l’actualisation simulés ne
valident ni l’ingestion, ni les délais de réessai, ni le contrat API.

Les liens vers Direct, Recherche, Bibliothèque et les rubriques détaillées des
réglages servent à situer la navigation. Le guide sera détaillé en S9, la recherche
en S10, la bibliothèque en S11, les règles complètes de reprise en S12 et le lecteur
et les réglages en S13. Leur présence dans la maquette ne les livre pas en S8.

Le retrait partagé de Continuer reste dépendant de S12. La confirmation de suppression
devra intégrer la liste À regarder lors de S11. L’accès à l’ancien catalogue durant
une actualisation reste soumis au lot C4 ; la maquette illustre la cible produit et
n’autorise pas une évolution du contrat. C4 et Q4 restent ouverts dans le
[registre](../../roadmap/0.2.0/open-questions.md).

## Vérifications et relecture attendue

Vérifications du prototype : changement de source indépendant entre surfaces,
ajout simulé, confirmation de suppression, guidage TV, états vides et erreur ; absence
d’erreur JavaScript et de débordement horizontal aux largeurs 320, 390, 736 et 1024 px.
Une relecture visuelle des captures web, mobile, TV et des dialogues a été effectuée.

Ces contrôles ne remplacent pas la recette applicative : persistance après relancement,
synchronisation entre appareils, lecteur, accessibilité et télécommande restent à tester.
La proposition est en français ; l’implémentation doit couvrir FR/EN.

La disposition de l’accueil est validée le 19 septembre 2026. La prochaine discussion
porte sur le sélecteur et la gestion des sources ; la navigation mobile et la lisibilité
sur une TV réelle restent à vérifier. Cette validation visuelle ne clôt aucune recette.
