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
- Favoris : ordre d'ensemble entre groupes et chaîne présente dans plusieurs groupes.
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

Voir le [cadrage Direct et guide](../../design/0.2.0/direct-guide.md) pour les
interactions encore ouvertes et le complément à US-16. La grille horaire TV
étend le périmètre du sprint 7 et devra être estimée.
