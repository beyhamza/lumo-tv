# US-024 — Consulter et gérer mes sources

Statut : organisation validée le 17 septembre 2026, détails des actions à préciser.
Version cible : 0.2.0. Surfaces : web, Android mobile, Android TV.

## Besoin

En tant qu'utilisateur, je veux comprendre l'état de mes sources et les gérer,
afin de retrouver leur contenu et de résoudre les problèmes de synchronisation.

## Critères d'acceptation validés

- Mes sources est accessible depuis le sélecteur de source et les réglages.
- Chaque source présente son nom, son type M3U ou Xtream et son état.
- Présenter les contenus disponibles : chaînes, films et séries, avec leurs nombres
  lorsqu'ils sont connus. Une valeur inconnue ne doit pas devenir un zéro affiché.
- Afficher la dernière synchronisation et l'avancement lorsqu'une synchronisation
  est en cours.
- Mobile et web permettent l'ajout et la gestion complète : Utiliser cette source,
  Renommer, Actualiser, Supprimer et réglage Actualisation automatique.
- Sur TV, permettre de changer de source et de l'actualiser ; guider vers le
  téléphone ou le web pour l'ajout et les modifications.
- En cas d'erreur, expliquer le problème et proposer une action adaptée.
- Les libellés sont disponibles en FR/EN et la partie TV est accessible au D-pad.

## Couverture API et existant à réutiliser

Réutiliser US-06/US-07 et les parcours des sprints 2 et 3, puis mesurer les écarts
avec cette cible. US-018 reste la référence pour la source active par appareil.

Le contrat expose les sources, leur ajout, modification, suppression et synchronisation.
`Source` fournit le type, le statut, `sync_step`, `last_synced_at`, `last_error_at`,
`error_code`, `auto_sync`, `channel_count` et `category_count`.

`last_synced_at` désigne la dernière ingestion réussie. L'avancement fourni est une
étape, pas un pourcentage. `auto_sync` est un réglage de la source partagé sur le
compte, exécuté côté serveur, distinct des préférences propres à un appareil.

`Source` n'expose pas de compteurs dédiés films/séries. Les listes paginées existent :
vérifier la réutilisation de leurs totaux et des lecteurs de catalogue existants,
sans charger tout le catalogue ni inventer un champ client/serveur.

Cette story n'introduit aucun endpoint, aucune modification de chiffrement ni de
droits d'accès. Toute évolution de ces éléments exige un cadrage explicite selon
`AGENTS.md`. Les quotas éventuels continuent de venir du serveur.

## Avant planification

- Préciser la présentation des étapes, du succès et des erreurs de synchronisation.
- Définir la confirmation de suppression et ses conséquences sur les données liées.
- Décider quoi afficher si la source active est supprimée depuis un autre appareil.
- Définir l'accès guidé depuis la TV, sans confondre gestion de source et activation TV.
- Préciser les limites et retours de l'actualisation manuelle.
- Auditer le formulaire existant avant de détailler la correction d'une source en erreur.
- Préparer les états vide, hors ligne et de première ingestion incomplète.

## Recette à préparer

Sur les trois surfaces, vérifier l'état d'une source de banc prête, en synchronisation
et en erreur. Vérifier les compteurs connus et inconnus, l'actualisation, le choix
de la source active et la portée partagée d'auto_sync. Sur mobile/web, vérifier
les actions de gestion ; sur TV, le guidage et les commandes à la télécommande.
Réutiliser les fixtures autorisées et les recettes existantes.

Référence : [décisions produit](../../roadmap/0.2.0/decisions.md).
