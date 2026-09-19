# Enchaînement des épisodes — complément 0.2.0

Comportement validé le 17 septembre 2026, pour web, Android mobile et Android TV.
Ce document complète [US-15 et le sprint 6](../../backlog/sprint-06.md) ; il ne crée
pas une deuxième story pour l'enchaînement déjà décrit dans le backlog.

## Comportement validé

Présentation proposée : [Reprise et lecteur, S13-E04](resume-player.md).
La présentation générale a été retenue ; l’accès rapide au réglage et les détails
de focus restent proposés.

- Lecture automatique de l'épisode suivant est activée par défaut et mémorisée
  par appareil. Le réglage d'un appareil ne modifie pas celui d'un autre.
- À la fin d'un épisode, si un suivant est disponible, présenter sa carte avec
  Lire maintenant et Retour à la série.
- Lecture automatique activée : décompte de 10 secondes sur TV, 5 secondes sur
  mobile et web, puis lancement du suivant.
- Toute interaction avec les commandes annule le décompte. Lire maintenant reste
  disponible et une activation explicite de cette action lance l'épisode.
- Lecture automatique désactivée : proposer le suivant sans décompte ni lancement
  automatique.
- Le dernier épisode d'une saison passe à la saison suivante lorsqu'un épisode
  est disponible.
- À la fin du dernier épisode disponible, revenir à la fiche de la série.
- Si l'épisode suivant est déjà commencé, reprendre sa position et afficher
  brièvement « Reprise à… » avec la position concernée.
- Libellés FR/EN et actions utilisables au tactile, au clavier et au D-pad.
- Le décompte démarre uniquement à la fin réellement atteinte par le lecteur.
  Le seuil de plus de 95 % utilisé pour Continuer ne coupe jamais la lecture et
  ne déclenche pas l'enchaînement.

## Articulation avec l'existant

Le sprint 6 décrit le décompte Android, son annulation, la sélection de l'épisode
suivant et les transitions entre saisons. Auditer ce qui est effectivement livré
sur chaque surface avant de chiffrer les écarts : notamment la préférence locale,
le mode désactivé et la règle de cinq secondes sur le web.

Les opérations de séries, épisodes, lecture et progression existent dans le
contrat. Aucun nouvel endpoint n'est défini ici. Vérifier les mécanismes existants
de préférences par appareil avant implémentation.

## Détails encore ouverts

- Emplacement du réglage et portée lors d'un changement de compte.
- Focus initial de la carte de fin et comportement des commandes Retour.
- Erreur lors du lancement du suivant et disparition d'un épisode du catalogue.
- Épisode suivant déjà terminé : comportement à préciser avec les critères de fin.
- Épisodes numérotés avec des trous ou saisons vides : conserver les règles métier
  existantes et vérifier leur cohérence avec le parcours retenu.

## Recette à préparer

Articulation avec Continuer : [cas de référence](continue-watching-cases.md).
Le suivant non commencé est proposé dans la rangée sans lecture préalable,
décision du 19 septembre ; cela ne déclenche pas son lancement avant la fin réelle
de l’épisode en cours dans le lecteur.

Vérifier les décomptes sur les trois surfaces, le lancement immédiat et l'annulation
par interaction. Vérifier le mode désactivé, la persistance locale et l'indépendance
de deux appareils, le passage de saison et la fin du dernier épisode disponible.
Réutiliser les fixtures et recettes du sprint 6, avec une télécommande réelle pour
la TV. L'absence d'épisode suivant ne doit pas créer de décompte.
Vérifier aussi que le suivant déjà commencé reprend sa position et affiche le
message de reprise.

Référence : [décisions produit](../../roadmap/0.2.0/decisions.md).
