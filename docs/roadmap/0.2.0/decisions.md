# Décisions produit — 0.2.0

Validées en conversation le 15 septembre 2026, complétées le 17 septembre 2026. Ce document consigne des décisions
produit ; il ne remplace ni le contrat ni les ADR.

## Accueil et navigation validés

| Sujet | Décision |
|---|---|
| Surfaces | Web, Android mobile et Android TV au même niveau de priorité |
| Arrivée | Accueil avec Continuer, Favoris, Direct, dans cet ordre |
| Sources | Une source active ; accueil, recherche et catalogues limités à celle-ci |
| Changement de source | Rapide, sans perte des favoris ou progressions des autres sources |
| Mémorisation | Par appareil ; changer sur mobile ne change pas la source de la TV |
| Sélecteur | Source active visible ; avec une seule source, afficher simplement son nom |
| Continuer | Films et épisodes commencés ; une carte par série |
| Action principale | Reprendre immédiatement la lecture |
| Actions secondaires | Voir la fiche ; retirer de Continuer sans perdre la progression |
| Favoris | Tous les favoris de la source active, dans l'ordre choisi ; accès à la bibliothèque et aux groupes |
| Lecture d'une chaîne | Sélectionner un favori lance immédiatement le direct |
| Direct sur l'accueil | Chaînes récentes, programme en cours si disponible, accès Toutes les chaînes et Guide TV |
| TV et web | Menu latéral : Accueil, Direct, Films, Séries, Ma bibliothèque |
| Mobile | Accueil, Explorer, Bibliothèque, Réglages ; Direct, Films et Séries dans Explorer |
| Accès complémentaires | Recherche, sélecteur de source et réglages accessibles |

## États validés

- Sans source : accompagner son ajout ; sur TV, guider vers le téléphone ou le web.
- Source prête sans activité : accès aux catalogues disponibles et invitation à explorer.
- Section vide : la masquer sans espace réservé.
- Synchronisation : afficher l'avancement et garder le catalogue déjà disponible consultable.
- Erreur de source : expliquer le problème et proposer une action adaptée.

## Points encore ouverts

- Retrait de Continuer : éventuelle restauration manuelle et modalités de synchronisation.
- Favoris : déplacement dans un groupe contenant plusieurs sources et erreurs
  partielles lors du retrait de plusieurs appartenances.
- Source mémorisée supprimée : choix de la source de remplacement.
- Cartes Continuer : seuils de début/fin et choix de l'épisode à reprendre, à confronter
  aux règles déjà livrées au sprint 6.
- Nombre de cartes, comportement des chargements et erreurs partielles, retour du
  focus TV et représentation des destinations sans contenu : à préciser avec les écrans.

Ces points ne sont pas des critères validés implicitement.

## Complément validé le 17 septembre 2026 — Continuer

- Retirer une carte la masque sur tous les appareils du compte.
- La progression est conservée.
- Relancer le contenu fait réapparaître sa carte dans Continuer.

Le besoin de masquage partagé nécessite un lot contractuel explicite avant
implémentation. La décision produit ne définit pas sa forme technique.

## Direct et guide — validés le 17 septembre 2026

- Une destination Direct, deux vues Chaînes et Guide, pour la source active.
- Chaînes : filtres Toutes, Favoris et catégories ; recherche par nom ; nom, logo
  de la source et programme en cours si disponible.
- Sélectionner une chaîne lance immédiatement la lecture ; revenir du lecteur
  restitue la position et le filtre.
- Guide TV et web : grille de chaînes en lignes et horaires en colonnes, adaptée
  à la télécommande sur TV ; filtres et déplacement temporel sur le web.
- Guide mobile : liste des programmes en cours, puis programme de la journée de
  la chaîne sélectionnée.
- Maintenant ramène au créneau actuel ; les chaînes sans guide restent dans Chaînes.
- Sélectionner un programme ouvre une fiche compacte : titre, horaires et description
  si disponible ; Regarder en direct pour le programme en cours, informations seules
  pour les programmes passés et à venir.
- Fermer la fiche restitue la position sélectionnée dans le guide.
- La fiche prend la forme d'un panneau latéral sur TV/web et d'un panneau depuis
  le bas sur mobile.
- Le guide s'ouvre sur Maintenant et marque le programme en cours. Il propose
  Aujourd'hui et les jours disponibles d'hier à J+3 ; Maintenant reste accessible.
- Un créneau vide affiche « Aucun programme disponible sur ce créneau » ; un guide
  absent est expliqué avec un accès Voir les chaînes.
- Un guide ancien conserve ses programmes et indique sa date de mise à jour.
- Une erreur de chargement conserve les données affichées et propose Réessayer.
- La consultation des programmes passés est informative, sans replay.
- Chaînes et Guide partagent Toutes, Favoris et catégories ; changer de vue conserve
  le filtre. La recherche de chaînes par nom conserve également le filtre actif.
- Aucun résultat : proposer d'effacer la recherche ou de revenir à Toutes.
- Première ouverture du Direct dans Chaînes, puis dernière vue mémorisée par appareil
  et par source.
- Depuis l'accueil, Toutes les chaînes ouvre Chaînes sans filtre ; Guide TV ouvre
  Guide sur Maintenant. Une nouvelle entrée dans Guide revient sur Maintenant,
  tandis qu'un retour du lecteur conserve la position précédente.

Voir le [cadrage Direct et guide](../../design/0.2.0/direct-guide.md) pour les
interactions encore ouvertes et le complément à US-16. La grille horaire TV
étend le périmètre du sprint 7 et devra être estimée.

## Recherche unifiée — validée le 17 septembre 2026

- Un champ pour le nom des chaînes et le titre des films et séries de la source active.
- Résultats regroupés par type ; filtres Tous, Chaînes, Films, Séries selon le
  contenu de la source ; accès à la liste complète d'un type en conservant la saisie.
- Sélectionner une chaîne lance le direct ; un film ou une série ouvre sa fiche.
- Accès en haut d'Explorer sur mobile, dans la navigation principale sur TV/web.
- Actualisation automatique après une courte pause ; correspondance partielle,
  insensible aux majuscules et minuscules.
- Champ vide : invitation à rechercher. Aucun résultat : rappeler la saisie et la
  source, proposer d'effacer. Erreur partielle : conserver les résultats disponibles
  et permettre de réessayer la section concernée.
- Pas d'historique des recherches ; le retour de fiche ou de lecteur conserve la
  recherche en cours, son filtre et la position.
- Sur TV, saisie avec le clavier à l'écran et navigation des résultats au D-pad.

Voir [US-021](../../backlog/stories/US-021-unified-search.md) pour la couverture
contractuelle et les détails restant à spécifier.

## Bibliothèque et liste à regarder — validées le 17 septembre 2026

- Deux sections dans la source active : Chaînes favorites et À regarder.
- Chaînes favorites rassemble les favoris et leurs groupes personnalisés.
- À regarder rassemble films et séries, avec filtres Tous, Films et Séries.
- Ajouter à ma liste depuis une fiche ; une série est enregistrée dans son ensemble.
- Liste partagée entre les appareils, ajouts les plus récents en premier.
- Sélectionner une carte ouvre sa fiche ; le retrait est manuel.
- Commencer ou terminer une lecture ne retire pas l'élément de la liste.
- Continuer dépend des lectures ; À regarder dépend des choix explicites de l'utilisateur.

Voir [US-022](../../backlog/stories/US-022-library-watchlist.md). La liste de films
et séries nécessite une évolution contractuelle à définir avant implémentation.

## Organisation des favoris — validée le 17 septembre 2026

- Groupes personnalisés : créer, renommer, supprimer, réordonner.
- Dans un groupe : ajouter, retirer et réordonner les chaînes.
- Une chaîne peut appartenir à plusieurs groupes.
- Tous les favoris et l'accueil montrent chaque chaîne une seule fois : ordre
  des groupes, puis des chaînes ; première occurrence retenue.
- Les groupes sont partagés entre appareils et affichés pour la source active.
- Réordonnancement par glisser-déposer et commandes accessibles sur mobile/web ;
  menu Déplacer avant / après sur TV.

US-020 définit l'agrégation ; US-022 reprend la gestion existante des groupes.
Les actions d'ajout et de retrait sont définies ci-dessous.

## Ajout et retrait dans la bibliothèque — validés le 17 septembre 2026

- Ajouter une chaîne l'enregistre dans le groupe par défaut ; Organiser permet
  ensuite de choisir ses groupes.
- Retirer depuis un groupe retire uniquement cette appartenance.
- Depuis l'accueil ou Tous les favoris, Retirer de tous mes favoris demande
  confirmation et retire la chaîne de tous ses groupes.
- Supprimer un groupe personnalisé transfère ses favoris au groupe par défaut ;
  cette conséquence est expliquée dans la confirmation.
- Le groupe par défaut est renommable, mais non supprimable.
- Pour les films et séries, Ajouter à ma liste devient Dans ma liste après ajout ;
  sélectionner à nouveau retire l'élément, sans modifier la progression.

## Audio, sous-titres et qualité — validés le 17 septembre 2026

- Menu Audio et sous-titres et réglage Qualité accessibles pendant la lecture.
- Choix parmi les pistes disponibles, avec langue si renseignée ; sous-titres
  désactivables et qualité Automatique par défaut.
- Choix manuel de qualité seulement si plusieurs qualités sont accessibles au lecteur.
- Indiquer simplement l'absence d'autre piste ou qualité.
- Langues audio et sous-titres, ainsi que l'activation des sous-titres, mémorisées
  par appareil ; choix indépendants entre téléphone et TV.
- Langue audio absente : piste par défaut. Langue de sous-titres absente : aucune
  activation automatique d'une autre langue.
- Qualité manuelle limitée à la lecture en cours ; prochain contenu en Automatique.

Voir [US-023](../../backlog/stories/US-023-player-preferences.md) pour les capacités
à vérifier sur chaque lecteur et les détails restant à préciser.

## Enchaînement des épisodes — validé le 17 septembre 2026

- Lecture automatique du suivant activée par défaut, mémorisée par appareil.
- À la fin d'un épisode, carte du suivant avec Lire maintenant et Retour à la série.
- Mode automatique : décompte de 10 secondes sur TV, 5 secondes sur mobile/web.
- Toute interaction avec les commandes annule le décompte ; Lire maintenant reste
  disponible pour un lancement explicite.
- Mode désactivé : proposition du suivant sans lancement automatique.
- Passage à la saison suivante si un épisode est disponible ; retour à la fiche
  de la série à la fin du dernier épisode disponible.

Voir le [complément à US-15](../../design/0.2.0/episode-continuation.md), qui reprend
le sprint 6 et identifie les écarts à vérifier avant planification.
