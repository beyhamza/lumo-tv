# US-024 — Consulter et gérer mes sources

Statut : organisation validée le 17 septembre 2026, détails des actions à préciser.
Version cible : 0.2.0. Surfaces : web, Android mobile, Android TV.

## Besoin

Planification proposée : S8 ; complément de cascade de la liste À regarder en S11.
Voir le [plan 0.2.0](../../roadmap/0.2.0/delivery-plan.md) ; réalisation non commencée.

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

## Après ajout — précisions validées le 19 septembre 2026

- Première source : elle devient active sur l’appareil ; une fois le catalogue
  prêt, proposer Découvrir mon catalogue.
- Source supplémentaire : conserver la source active et proposer Utiliser cette source.
- Pendant l’importation, permettre de quitter l’écran et de parcourir une autre source.
- Après un échec d’importation d’une source créée, la conserver avec son erreur
  et proposer Réessayer sans imposer un nouvel ajout.

Ces précisions complètent les règles d’actualisation et d’erreur déjà validées.

## Actualisation — règles validées

- Afficher les étapes réelles fournies : récupération, traitement du catalogue,
  guide selon la synchronisation ; ne pas afficher un pourcentage approximatif.
- Garder le catalogue existant consultable pendant l'actualisation.
- Pendant une synchronisation, le bouton indique Actualisation en cours et ne
  permet pas de lancer une seconde opération.
- À la réussite, afficher les compteurs disponibles et la date de mise à jour.
- En cas d'échec, conserver le catalogue précédent lorsqu'il est disponible,
  expliquer l'erreur et proposer Réessayer.
- Si le serveur impose une attente avant une nouvelle tentative, indiquer ce délai.
- Actualisation automatique s'applique à la source sur tout le compte : une
  activation depuis le mobile bénéficie aussi au web et à la TV.

## Indisponibilité et hors ligne — validés le 19 septembre 2026

- Conserver la source choisie sans basculer automatiquement vers une autre.
- Afficher le dernier catalogue lorsqu’il est disponible, avec un message indiquant
  qu’il peut être ancien. Cela ne garantit pas la disponibilité des flux vidéo.
- Sans catalogue accessible, expliquer la situation et proposer Réessayer et
  Changer de source ; respecter le délai serveur éventuel pour une nouvelle tentative.
- Si le catalogue est partiellement accessible, conserver les parties disponibles
  et signaler celles qui n’ont pas pu charger, sans les présenter comme vides.
- Une panne ne supprime ni les favoris ni les progressions. Une indisponibilité
  ne constitue pas une preuve de suppression de la source.

La disponibilité locale dépend des capacités existantes à vérifier. Cette décision
ne définit pas de nouveau cache persistant ni de téléchargement hors ligne ; C4
et tout éventuel besoin d’ADR restent à cadrer avant implémentation.

## Suppression — comportement validé

- Demander confirmation en nommant la source concernée et ses conséquences.
- Supprimer son catalogue de Lumo, ses favoris, progressions et éléments À regarder.
- Conserver les autres sources et leurs données.
- Préciser que l'opération ne résilie pas l'abonnement auprès du fournisseur.
- Indiquer que réajouter la source ne restaure pas automatiquement ses données supprimées.
- Si la source était active, sélectionner l'unique source restante, proposer un
  choix s'il en reste plusieurs, ou revenir à Ajouter une source si aucune ne reste.
- Appliquer cette règle lorsqu'un autre appareil constate la suppression, selon US-018.
- Si une lecture est en cours, l’arrêter dès que la suppression est confirmée
  par le serveur et afficher « Cette source a été supprimée de votre compte. ».
- Proposer Continuer pour revenir à la navigation et appliquer le choix de source
  restante décrit ci-dessus, sans lancer automatiquement un autre contenu.
- Hors ligne, attendre une vérification serveur avant de déclencher ce parcours ;
  une erreur réseau seule ne confirme pas la suppression.

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

La synchronisation expose `SOURCE_SYNC_IN_PROGRESS` et `SOURCE_SYNC_RATE_LIMITED`,
avec `Retry-After` pour le délai. Réutiliser ces réponses sans inventer une durée.
Vérifier avant implémentation comment le catalogue précédent reste accessible en
état SYNCING ou ERROR sur chaque surface : le besoin produit ne prouve pas que les
lectures serveur et les caches le permettent déjà.

Cette story n'introduit aucun endpoint, aucune modification de chiffrement ni de
droits d'accès. Toute évolution de ces éléments exige un cadrage explicite selon
`AGENTS.md`. Les quotas éventuels continuent de venir du serveur.

La suppression du catalogue et des favoris est déjà décrite dans le contrat.
Vérifier et expliciter la suppression de la progression et de la future liste
À regarder dans le lot contractuel correspondant avant implémentation. La cible
validée ne constitue pas une preuve que toutes les cascades existent déjà.

## Avant planification

- Détailler les libellés par étape et par code d'erreur, ainsi que la conservation
  du catalogue précédent lors d'une actualisation.
- Détailler les erreurs de suppression et son mécanisme de détection depuis un
  autre appareil, y compris après reconnexion. Le comportement produit pendant
  une lecture est validé le 19 septembre 2026 ; sa réalisation reste à vérifier.
- Définir l'accès guidé depuis la TV, sans confondre gestion de source et activation TV.
- Auditer le formulaire existant avant de détailler la correction d'une source en erreur.
- Préparer les états vide, hors ligne et de première ingestion incomplète.

## Recette à préparer

Sur les trois surfaces, vérifier l'état d'une source de banc prête, en synchronisation
et en erreur. Vérifier les compteurs connus et inconnus, l'actualisation, le choix
de la source active et la portée partagée d'auto_sync. Sur mobile/web, vérifier
les actions de gestion ; sur TV, le guidage et les commandes à la télécommande.
Réutiliser les fixtures autorisées et les recettes existantes.
Vérifier aussi les doubles lancements, le délai imposé par le serveur, la première
ingestion sans catalogue préalable et l'échec d'une actualisation avec un catalogue
déjà disponible. Une réussite doit actualiser les compteurs et la date affichée.
Vérifier la suppression avec zéro, une et plusieurs sources restantes, la
conservation des données des autres sources, et la réaction d'un second appareil.
Inclure la suppression pendant une lecture : arrêt après confirmation serveur,
message, retour via Continuer et absence de lecture automatique d’un autre contenu.
Vérifier qu’une panne réseau ne déclenche pas ce parcours et que la suppression
confirmée après reconnexion le déclenche correctement.

Référence : [décisions produit](../../roadmap/0.2.0/decisions.md).
