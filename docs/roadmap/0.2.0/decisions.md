# Décisions produit — 0.2.0

Validées en conversation le 15 septembre 2026. Ce document consigne des décisions
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

- Retrait de Continuer : masquage local ou partagé entre appareils, retour de la carte
  après une nouvelle lecture et éventuelle restauration manuelle.
- Favoris : ordre d'ensemble entre groupes et chaîne présente dans plusieurs groupes.
- Source mémorisée supprimée : choix de la source de remplacement.
- Cartes Continuer : seuils de début/fin et choix de l'épisode à reprendre, à confronter
  aux règles déjà livrées au sprint 6.
- Nombre de cartes, comportement des chargements et erreurs partielles, retour du
  focus TV et représentation des destinations sans contenu : à préciser avec les écrans.

Ces points ne sont pas des critères validés implicitement.
