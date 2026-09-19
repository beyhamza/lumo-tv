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
- Cartes Continuer : garanties d’ordre des événements, réessais et propagation
  hors ligne à cadrer dans C3. Temps simultané, priorité de la lecture récente et
  maintien du masque face à une lecture déjà en cours sont validés le 19 septembre.
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

## Reprise depuis les fiches — validée le 17 septembre 2026

- Film jamais commencé : Regarder ; commencé : Reprendre avec progression et
  Recommencer ; terminé : Revoir depuis le début.
- Série : Reprendre avec saison et épisode en cours, ou Regarder l'épisode suivant
  si le précédent est terminé et qu'un suivant est disponible.
- Chaque épisode reste sélectionnable ; un épisode commencé propose Reprendre ou
  Recommencer cet épisode, sans modifier les progressions des autres épisodes.
- L'enchaînement vers un épisode déjà commencé reprend sa position avec un bref
  message Reprise à… ; l'accueil Continuer lance toujours directement la lecture.

Ces règles complètent US-019 et le cadrage d'enchaînement.

## Apparition dans Continuer et fin — validées le 17 septembre 2026

- Apparition après 30 secondes de lecture effective, progression conservée dès le début.
- Conserver le seuil contractuel de plus de 95 % de la durée : le film quitte
  Continuer et propose Revoir ; la série propose l'épisode suivant s'il existe,
  ou quitte Continuer après le dernier épisode disponible terminé.
- Sans durée connue, conserver la reprise avec retrait manuel possible.
- Le seuil de 95 % ne coupe pas la lecture et ne déclenche pas le suivant : le
  décompte attend la fin réellement atteinte par le lecteur.

La mesure de lecture effective et sa propagation restent à cadrer techniquement
dans US-019, distinctement de la position de lecture.

## Continuer — compléments validés le 19 septembre 2026

- Les 30 secondes réellement regardées se cumulent entre sessions et appareils
  pour le même film ou épisode ; elles ne s’additionnent pas entre épisodes différents.
- En cas de lecture simultanée sur plusieurs appareils, les secondes qui se
  chevauchent ne comptent qu’une fois : deux lectures pendant les mêmes 20 secondes
  donnent 20 secondes cumulées, pas 40.
- En cas de positions différentes entre appareils, utiliser la lecture la plus
  récente pour la prochaine reprise, même si sa position est moins avancée.
- Retirer une carte pendant une lecture déjà en cours sur un autre appareil la
  garde masquée malgré les sauvegardes de cette session. Seul un nouveau démarrage
  réel après le retrait permet sa réapparition ; la progression reste sauvegardée.
- Après retrait d’une carte, relancer le contenu la fait réapparaître dès le
  démarrage réel de la lecture, sans attendre 30 nouvelles secondes.
- Lorsqu’un épisode est terminé et qu’un suivant est disponible mais non commencé,
  garder la série dans Continuer et proposer directement le suivant. Celui-ci
  n’a pas besoin d’avoir déjà été lu et aucune progression fictive n’est créée.

- Contenu de moins de 30 secondes : aucune exception au seuil d’apparition.
  La progression est sauvegardée, mais le contenu terminé n’apparaît pas dans
  Continuer. La proposition du suivant d’une série reste applicable.
- Épisode suivant déjà terminé : le relire depuis le début pour conserver l’ordre,
  sans rechercher silencieusement un autre épisode non terminé.
- Recommencer : repartir à zéro sans attendre 30 nouvelles secondes pour retrouver
  une carte déjà éligible, dès le démarrage réel. Les progressions des autres
  épisodes restent intactes.

Voir les [cas CW-17 à CW-22 et CW-29 à CW-31](../../design/0.2.0/continue-watching-cases.md).
Les garanties d’ordre, de réessai et de propagation hors ligne restent à préciser.
C3 demeure un lot contractuel distinct à définir et approuver ; aucun endpoint
ni mécanisme de synchronisation n’est décidé ici.

## Mes sources — organisation validée le 17 septembre 2026

- Page accessible depuis le sélecteur et les réglages.
- Nom, type M3U ou Xtream, état, contenus disponibles et nombres lorsqu'ils sont connus.
- Dernière synchronisation et avancement pendant une synchronisation.
- Mobile/web : ajout et gestion complète, Utiliser cette source, Renommer, Actualiser,
  Supprimer et Actualisation automatique.
- TV : changement de source et actualisation ; guidage vers téléphone/web pour
  l'ajout et les modifications.
- En cas d'erreur, explication et action adaptée.

Voir [US-024](../../backlog/stories/US-024-source-management.md) pour les données
existantes et les détails d'action restant à préciser.

## Actualisation des sources — validée le 17 septembre 2026

- Étapes réelles de synchronisation, sans pourcentage approximatif.
- Catalogue existant consultable pendant l'actualisation ; bouton Actualisation
  en cours pour éviter les doubles lancements.
- Réussite : compteurs disponibles et date de mise à jour.
- Échec : conserver le catalogue précédent lorsqu'il est disponible, expliquer
  le problème et proposer Réessayer.
- Indiquer le délai d'attente lorsqu'il est imposé par le serveur.
- Actualisation automatique partagée pour la source sur tout le compte.

US-024 porte ces critères et la vérification d'accès au catalogue précédent dans
les états de synchronisation et d'erreur.

## Suppression d'une source — validée le 17 septembre 2026

- Confirmation nommant la source et annonçant la suppression de son catalogue,
  de ses favoris, progressions et éléments À regarder dans Lumo.
- Les autres sources et leurs données sont conservées.
- La confirmation précise que la suppression ne résilie pas l'abonnement du
  fournisseur et qu'un nouvel ajout ne restaure pas automatiquement les données supprimées.
- Source active supprimée : sélectionner l'unique source restante, demander de
  choisir s'il en reste plusieurs, ou revenir à Ajouter une source si aucune ne reste.
- Même règle lorsqu'un autre appareil constate cette suppression.

US-018 et US-024 portent ces critères. La couverture contractuelle des données
liées, notamment progression et future liste À regarder, reste à vérifier et compléter.

## Réglages généraux — validés le 17 septembre 2026

- Cinq rubriques : Compte et appareils, Mes sources, Lecture, Application,
  Aide et informations.
- Compte et appareils : informations du compte, appareils connectés, déconnexion.
- Lecture : préférences audio/sous-titres et lecture automatique du suivant.
- Application : langue d'interface français/anglais.
- Aide et informations : guides, version, confidentialité et conditions.
- TV : réglages de lecture directement modifiables ; opérations de compte plus
  longues guidées vers téléphone/web.
- Mobile : Lecture sur données mobiles activée par défaut. Lorsqu'elle est
  désactivée, lancement vidéo hors Wi-Fi bloqué avec explication ; catalogue consultable.

Voir [US-025](../../backlog/stories/US-025-general-settings.md) pour les dépendances
et les cas particuliers restant à préciser, dont le changement de réseau en lecture.

## Source supprimée pendant une lecture — validé le 19 septembre 2026

- Arrêter la lecture lorsque le serveur confirme la suppression de sa source.
- Afficher « Cette source a été supprimée de votre compte. » et une action Continuer.
- Revenir à la navigation : unique source restante sélectionnée, choix si plusieurs,
  ou ajout si aucune. Ne jamais lancer automatiquement un autre contenu.
- Hors ligne, attendre de pouvoir vérifier la suppression ; une erreur réseau
  seule ne déclenche pas ce parcours.

Voir [US-024](../../backlog/stories/US-024-source-management.md). Le mécanisme
de détection et sa recette restent à définir avant implémentation.

## Source indisponible et hors ligne — comportement validé le 19 septembre 2026

- Conserver la source choisie, sans changement automatique.
- Montrer le dernier catalogue disponible avec une indication de données potentiellement anciennes.
- Sans catalogue accessible, expliquer la situation et proposer Réessayer et Changer de source.
- En cas d’accès partiel, garder les parties disponibles et signaler celles non chargées.
- Une panne ne supprime ni favoris ni progressions et ne prouve pas une suppression.

Voir [US-024](../../backlog/stories/US-024-source-management.md). Les capacités de
consultation et de cache restent à vérifier ; aucune lecture vidéo hors ligne
ni nouvelle stratégie de persistance n’est décidée.

## Changement de source — comportement validé le 19 septembre 2026

- Nom de la source active visible ; accès au sélecteur avec plusieurs sources.
- Dans le sélecteur : nom, état et coche pour la source active.
- Application immédiate sans confirmation, uniquement sur l’appareil concerné.
- Conserver la rubrique ouverte et réinitialiser les filtres propres à l’ancienne source.
- Depuis une fiche, revenir au catalogue correspondant de la nouvelle source.

Ces critères complètent [US-018](../../backlog/stories/US-018-active-source.md).

## Disposition de l’accueil — validation visuelle du 19 septembre 2026

La disposition Continuer, Favoris et Direct de la première proposition interactive
est validée. Voir les [écrans du sprint 8](../../design/0.2.0/sprint-08-screens.md).
Cette validation porte sur l’accueil ; les autres écrans restent à relire et la
recette sur appareils réels reste à effectuer.

## Présentation Direct et Guide — retenue le 19 septembre 2026

La présentation générale de la proposition interactive web, mobile et TV est
retenue après relecture en conversation. Elle illustre les comportements déjà
validés dans le [cadrage Direct et Guide](../../design/0.2.0/direct-guide.md).
Les arbitrages C1/Q5 et la recette sur appareils réels restent à compléter.

Complément demandé ensuite : sur la liste complète des chaînes, placer toutes les
catégories dans une colonne défilante à gauche et conserver les cartes à droite.
La maquette révisée applique cette disposition sur TV/web ; son adaptation mobile
compacte est une proposition, pas une nouvelle validation explicite.

## Présentation Reprise et lecteur — retenue le 19 septembre 2026

La proposition [Reprise et lecteur](../../design/0.2.0/resume-player.md) est retenue
en conversation avant de poursuivre la roadmap. Cette validation de présentation
ne clôture pas C3, Q1/Q2/Q6 ni la vérification des capacités des lecteurs réels.

## Perte du Wi-Fi pendant une lecture — validée le 19 septembre 2026

Sur Android mobile, lorsque Lecture sur données mobiles est désactivée, une perte
du Wi-Fi pendant une vidéo met la lecture en pause et demande de retrouver le Wi-Fi.
Le catalogue reste consultable. La reprise au retour du Wi-Fi et les autres cas
réseau restent ouverts dans Q7 ; aucun comportement automatique n’est décidé ici.

## Présentation Ma bibliothèque — retenue le 19 septembre 2026

La présentation générale de [Ma bibliothèque](../../design/0.2.0/library.md)
est retenue dans l’échange repris par l’utilisateur. Cette validation visuelle
ne clôture ni C2, ni Q3/Q8, ni la recette sur appareils réels. La discussion
suivante porte sur la reprise de lecture et le lecteur des sprints 12 et 13.

## Objectif de sortie — décision du 17 septembre 2026

- La 0.2.0 reste gratuite ; aucune ouverture des paiements n'est prévue pour cette version.
- Reporter le chantier de configuration et validation Google, ainsi que les autres
  connexions tierces. L'utilisateur souhaite se concentrer sur les fonctions du lecteur.
- Google et Stripe ne sont pas des critères bloquants de sortie 0.2.0 ; leur dette
  reste ouverte, sans être déclarée résolue.
- Conserver comme base le compte existant par email et l'activation TV. Aucun mode
  sans compte ni changement des quotas ou droits d'accès n'est décidé ici.
- Dans la recette 0.2.0, marquer les cas Google hors périmètre ; maintenir la recette
  des parcours retenus et les vérifications transverses.
