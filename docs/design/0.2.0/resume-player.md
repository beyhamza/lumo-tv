# Reprise et lecteur — proposition d’écrans 0.2.0

Date : 19 septembre 2026. Statut : présentation générale retenue en conversation ; développement non commencé.

Références : [US-019](../../backlog/stories/US-019-continue-watching.md),
[US-023](../../backlog/stories/US-023-player-preferences.md),
[enchaînement US-15](episode-continuation.md),
[décisions produit](../../roadmap/0.2.0/decisions.md).

## Écrans et tâches

| Écran | Présentation proposée | Tâche |
|---|---|---|
| S12-E01 Continuer | Carte avec titre, saison/épisode et progression ; Reprendre, Voir la fiche et retrait accessibles sans survol | S12-04 |
| S12-E02 Fiche | Reprendre avec position, Recommencer ; choix individuel des épisodes | S12-03 |
| S12-E03 Retrait | Carte masquée, message de confirmation non bloquant ; progression retrouvée depuis la fiche | S12-04/05, après C3 |
| S13-E01 Lecteur | Retour, titre, progression et commandes de lecture ; accès Audio et sous-titres et Qualité | S13-01 |
| S13-E02 Pistes | Langues disponibles et Désactivés pour les sous-titres ; portée locale explicitée | S13-01/02 |
| S13-E03 Qualité | Automatique sélectionné initialement ; variantes uniquement si accessibles | S13-01/02 |
| S13-E04 Fin d’épisode | Carte du suivant, Lire maintenant, Retour à la série ; décompte ou mode manuel | S13-03 |

## Composition proposée

La charte Spectre conserve le fond sombre, les surfaces sobres et l’accent cyan.
Continuer donne la priorité à la reprise immédiate. Le retrait reste une action
secondaire distincte de Recommencer ; il ne remet pas la progression à zéro.
La fiche porte les décisions plus détaillées, sans dialogue avant la reprise
depuis l’accueil. Si la dernière carte est retirée, la rangée disparaît.

Web : commandes sous la progression et panneau de réglage proche du lecteur.
Mobile : cartes empilées et réglages en panneau sous le lecteur dans cette
proposition ; une présentation plein écran paysage reste à dessiner.
TV : commandes espacées, libellés visibles et focus contrasté. La composition
réduite dans la conversation ne remplace pas une maquette 1920 × 1080 ni une
recette à trois mètres et à la télécommande.

Focus proposé : retour au bouton qui ouvre un panneau lorsqu’il est fermé ;
option courante à l’ouverture. Pour la carte de fin, Lire maintenant est le
candidat de focus initial ; un placement programmatique du focus ne doit pas
annuler le décompte. Une interaction utilisateur avec les commandes l’annule.
Ces choix de focus et le retour système restent à valider sur les trois surfaces.

## Règles acquises à préserver

- La progression est conservée dès le début ; l’apparition après 30 secondes
  concerne le temps effectivement lu, jamais la position obtenue par avance.
- Complément du 19 septembre : cumul par film/épisode entre sessions et appareils.
- Les secondes simultanées ne comptent qu’une fois ; la prochaine reprise utilise
  la lecture la plus récente, même moins avancée. Une session déjà en cours lors
  d’un retrait ne peut pas réafficher la carte par ses sauvegardes.
- Le seuil de fin est strictement supérieur à 95 %. Il ne coupe pas le lecteur
  et ne lance pas le suivant ; seule la fin réelle déclenche l’enchaînement.
- Une carte par série ; la source active filtre Continuer.
- Le retrait masque sur tous les appareils sans perte de progression ; relancer
  fait réapparaître la carte dès le démarrage réel de la lecture, sans attendre
  30 nouvelles secondes (complément du 19 septembre).
- Après un épisode terminé, la carte de série propose le suivant disponible même
  s’il n’a pas encore été commencé, sans seuil préalable sur celui-ci.
- Suivant déjà terminé : le relire depuis le début, en conservant l’ordre des épisodes.
- Recommencer conserve l’éligibilité acquise : carte retrouvée au démarrage réel,
  sans attendre 30 nouvelles secondes ; progressions des autres épisodes intactes.
- Un contenu de moins de 30 secondes n’a pas d’exception au seuil d’apparition ;
  sa progression est sauvegardée et le contenu terminé reste absent de Continuer.
- Préférences audio/sous-titres par appareil ; une langue absente ne remplace pas
  la préférence mémorisée. Audio : repli sur la piste par défaut. Sous-titres :
  aucune activation automatique d’une autre langue.
- Qualité manuelle limitée au contenu en cours, prochain contenu en Automatique.
- Décompte de 10 secondes sur TV, 5 sur mobile/web ; toute interaction avec les
  commandes l’annule, sans empêcher Lire maintenant.
- Suivant déjà commencé : reprendre sa position et afficher Reprise à… ; dernier
  épisode disponible terminé : retour à la fiche, sans décompte vide.

## Portée de la proposition interactive

Le prototype illustre une série fictive, une reprise, un retrait local en mémoire,
les menus de pistes et de qualité et un suivant non commencé. Aucun flux, compte
ou appel API n’est connecté. Le bouton Simuler la fin est un outil de démonstration,
pas une commande du produit. La progression ne mesure aucun temps réellement lu.

Les options de pistes et variantes sont fictives, sans promesse de disponibilité
dans les lecteurs réels. La sélection de surface change la présentation ; elle
ne simule pas trois appareils indépendants ni une synchronisation. Le réglage
d’enchaînement affiché à la fin est une proposition d’accès rapide ; son emplacement
définitif reste à décider avec les réglages de lecture.

Restent à illustrer : fiche film jamais commencé/terminé, durée inconnue, pistes
sans alternative, langue préférée absente, erreur de changement de piste, erreur
de résolution, suivant déjà commencé/terminé, changement de saison et fin de série.
La maquette actuelle est en français ; la réalisation inclura FR/EN.

## Arbitrages et vérifications avant réalisation

C3 reste requis pour le masquage partagé et l’éligibilité. Aucun endpoint ni type
contractuel n’est ajouté par ce document. Les règles principales de Q1/Q2 sont
tranchées côté produit ; C3 doit définir l’ordre des événements, les réessais et
la propagation hors ligne pour les appliquer entre appareils.
Recommencer, les contenus courts et le suivant déjà terminé ont été validés
le 19 septembre, comme le cumul, la réapparition et le suivant non commencé.
Les [cas de référence](continue-watching-cases.md) détaillent ces frontières.
Q6 couvre notamment les pistes sans langue, les sous-titres
forcés et le changement de compte. Les erreurs et conflits interappareils restent
à définir avant de considérer les tâches dépendantes comme prêtes.

Recette prévue : reprise/recommencement, retrait sans perte, seuils exacts,
annulation du décompte, mode manuel, langues absentes, retour à Automatique,
navigation clavier/D-pad et retour du focus. Réutiliser les fixtures autorisées
des sprints précédents et vérifier les capacités réelles de chaque lecteur.
Une validation visuelle de cette proposition ne clôture aucune de ces recettes.
