# US-025 — Retrouver mes réglages et contrôler la lecture mobile

Statut : organisation validée le 17 septembre 2026, cas particuliers à préciser.
Version cible : 0.2.0. Surfaces : web, Android mobile, Android TV.

## Besoin

Proposition d’écrans : [Réglages](../../design/0.2.0/settings.md), S13-E05 à E10,
à relire. Les opérations de compte guidées depuis TV restent proposées.

Planification proposée : Rubriques initiales S8, clôture S13.
Voir le [plan 0.2.0](../../roadmap/0.2.0/delivery-plan.md) ; réalisation non commencée.

En tant qu'utilisateur, je veux retrouver mes réglages dans des rubriques claires,
afin d'adapter Lumo et de gérer mon compte et mes appareils.

## Organisation validée

| Rubrique | Contenu |
|---|---|
| Compte et appareils | Informations du compte, appareils connectés, déconnexion |
| Mes sources | Gestion définie par US-024 |
| Lecture | Langues audio/sous-titres, activation des sous-titres, épisode suivant automatique |
| Application | Langue d'interface : français ou anglais |
| Aide et informations | Guides, version de Lumo, confidentialité et conditions |

Sur TV, les préférences de lecture sont directement modifiables ; les opérations
de compte plus longues guident vers le téléphone ou le web. Détailler les actions
de compte concernées avant de concevoir les écrans.

## Lecture sur données mobiles

- Sur Android mobile, proposer Lecture sur données mobiles, activée par défaut.
- Si l'utilisateur désactive cette option, bloquer le lancement d'une vidéo hors
  Wi-Fi et afficher un message explicatif.
- Le catalogue reste consultable.
- Décision du 19 septembre 2026 : si le Wi-Fi se coupe pendant une vidéo alors
  que cette option est désactivée, mettre la lecture en pause et demander de
  retrouver le Wi-Fi. Vérifier la suspension effective des requêtes média.
- Ne pas présenter cette option comme un mode hors ligne ou une interdiction de
  tout transfert de données : elle concerne la lecture vidéo.
- Les libellés sont disponibles en FR/EN et les commandes sont accessibles selon
  la surface, notamment au D-pad pour les réglages TV.

## API et dépendances

Les opérations de compte, appareils et déconnexion existent dans le contrat, ainsi
que la locale utilisateur. Vérifier les comportements existants avant de détailler
la portée du choix de langue. Ne pas confondre langue d'interface et préférences
audio/sous-titres locales définies dans US-023.

Réutiliser US-024 pour les sources, US-023 pour les pistes et le complément à US-15
pour la lecture automatique. Le contrôle de lecture selon le réseau relève du
client mobile ; aucun endpoint nouveau n'est défini ici. Aucun changement de droits
d'accès ou de modèle d'authentification n'est inclus dans cette story.

## Règles de réalisation des rubriques — arrêtées le 23 septembre 2026 (S8-06)

S8 pose la **structure** des cinq rubriques sur les trois surfaces et n'y met que ce
qui est opérationnel. Règle commune du sprint : ce qui n'est pas livré n'est pas
affiché — aucune commande factice, aucun libellé « bientôt ».

| Rubrique | En S8-06 | Laissé à |
|---|---|---|
| Compte et appareils | Email du compte, appareil courant identifié, autres appareils avec dernière activité connue ou « indisponible », déconnexion confirmée ; sur TV, guidage vers le téléphone ou le web pour révoquer un appareil | Édition du profil : hors lot |
| Mes sources | Ouvre l'écran d'US-024 (S8-05) | — |
| Lecture | **Absente** tant que ses réglages n'existent pas | S13 (US-023, données mobiles) |
| Application | Langue de l'interface : lecture seule, valeur suivant la langue du système, avec la mention que le choix arrive | Choix FR/EN et portée : S13, Q6 |
| Aide et informations | Version installée ou déployée, liens vers les guides et les pages confidentialité et conditions **quand elles existent** ; sinon la rubrique ne liste que la version | Pages manquantes : à créer hors sprint |

Retraits de la 0.2.0 (décision du 17 septembre, périmètre gratuit sans Google ni
paiement), sans refondre l'authentification :

- **Google** : le bouton disparaît des écrans de connexion et d'inscription sur mobile
  et web ; le code du client OAuth reste (dette n° 1), rien n'est appelé.
- **Abonnement** : l'entrée de menu web, la carte d'accueil, le lien « offre
  supérieure » à la limite de sources et la page `/app/subscription` sont retirés
  des parcours ; la limite de sources s'explique sans renvoi vers un paiement.
- **Tarifs** : la section du site marketing et ses liens d'en-tête et de pied sont
  retirés ; le site présente une application gratuite.
- **Android** : le texte « passez à une offre supérieure » à la limite de sources est
  remplacé par une explication neutre. Les quotas servis par le serveur ne changent pas.

## Avant planification

- Préciser la portée par compte/appareil de la langue d'interface et sa valeur initiale.
- Préciser la persistance du choix de réseau et son comportement au changement de compte.
- Passage Wi-Fi vers réseau mobile : pause validée le 19 septembre 2026 lorsque
  l’option est désactivée. Préciser la reprise au retour du Wi-Fi ; la proposition
  de design conserve une reprise explicite, non encore validée.
- Définir les cas Ethernet, VPN, réseau inconnu et Wi-Fi avec accès limité, sans
  supposer que Wi-Fi signifie gratuit ou illimité.
- Détailler les actions sur les appareils et les opérations de compte guidées depuis TV.
- Vérifier les guides et pages d'information existants, puis lister les manques.

## Recette à préparer

Vérifier les cinq rubriques sur les trois surfaces, les liens, la version affichée,
les deux langues et la navigation TV. Sur mobile, vérifier le lancement vidéo en
Wi-Fi et hors Wi-Fi, avec l'option activée puis désactivée, ainsi que la consultation
du catalogue lorsque la lecture est bloquée. Inclure les lancements depuis
l'accueil, les fiches et l'enchaînement automatique.
Vérifier également la pause lors d’une perte du Wi-Fi pendant une lecture lorsque
l’option est désactivée, la position conservée et l’absence de requêtes média
qui continueraient à charger la vidéo sur les données mobiles.

Référence : [décisions produit](../../roadmap/0.2.0/decisions.md).
