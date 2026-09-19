# Réglages — proposition d’écrans 0.2.0

Date : 19 septembre 2026. Statut : proposition à relire ; développement non commencé.

Références : [US-025](../../backlog/stories/US-025-general-settings.md),
[US-023](../../backlog/stories/US-023-player-preferences.md),
[US-024](../../backlog/stories/US-024-source-management.md),
[sprint 13](../../backlog/sprint-13.md), [décisions](../../roadmap/0.2.0/decisions.md).

## Organisation et rattachement

Les cinq rubriques validées restent au même niveau. Web et TV : liste à gauche,
contenu à droite. Mobile : entrée par rubrique ; la proposition interactive
présente un sélecteur compact au-dessus du contenu pour faciliter la relecture.
Il ne remplace pas la navigation principale mobile Accueil/Explorer/Bibliothèque/Réglages.

| Écran | Présentation proposée | Tâches |
|---|---|---|
| S13-E05 Lecture | Audio, activation et langue des sous-titres, épisode suivant automatique ; portée par appareil | S13-02/03/04 |
| S13-E06 Réseau mobile | Lecture sur données mobiles et explication du blocage hors Wi-Fi | S13-05 |
| S13-E07 Compte et appareils | Compte email, appareil courant, autres appareils, déconnexion confirmée | S13-04 |
| S13-E08 Application | Langue d’interface FR/EN, distincte des langues du lecteur | S13-04 |
| S13-E09 Aide et informations | Guides, version installée, liens confidentialité et conditions | S13-04 |
| S13-E10 Gestion depuis TV | Réglages de lecture directs ; guidage téléphone/web pour gérer les autres appareils | S13-04/06 |

Mes sources ouvre les écrans de [S8](sprint-08-screens.md) : la rubrique ne crée
pas un deuxième parcours de gestion ni une nouvelle story.

## Lecture et réseau

Les langues audio/sous-titres et l’activation sont indépendantes entre appareils.
La maquette démarre avec un exemple français, sous-titres désactivés : cet exemple
ne décide pas des valeurs initiales du produit, encore couvertes par Q6.
La préférence de langue reste conservée lorsque les sous-titres sont désactivés.
La qualité reste dans le lecteur : elle vaut pour le contenu courant uniquement.

Épisode suivant automatique est activé par défaut. Le réglage permanent est ici,
dans Lecture ; l’accès rapide présenté sur la carte de fin reste une proposition.
Les règles de décompte et de reprise suivent le [lecteur](resume-player.md).

Sur Android mobile, Lecture sur données mobiles est activée par défaut. Lorsqu’elle
est désactivée, bloquer les lancements vidéo hors Wi-Fi et expliquer comment
retrouver le Wi-Fi ou changer ce réglage ; le catalogue reste consultable.

**Décision utilisateur du 19 septembre 2026 :** si le Wi-Fi se coupe pendant une
lecture et que cette option est désactivée, mettre la vidéo en pause et demander
de retrouver le Wi-Fi. Vérifier en réalisation la suspension effective des requêtes
média, pas seulement l’arrêt visuel. Cette décision ne tranche pas la reprise
automatique au retour du Wi-Fi : la proposition conserve une reprise explicite,
à confirmer avant implémentation. Ethernet, VPN, réseau inconnu et persistance
au changement de compte restent dans Q7.

## Compte et appareils

Proposition web/mobile : informations du compte, appareil courant clairement
identifié et autres installations. Afficher la dernière activité si connue,
sinon une indication d’indisponibilité ; ne pas prétendre qu’un appareil est en ligne.
Pas d’adresse email éditable : le contrat limite la modification du profil au
nom d’affichage et à la locale. L’édition du profil n’est pas ajoutée à ce lot visuel.

Déconnecter un autre appareil ouvre une confirmation qui le nomme et précise
qu’il devra se reconnecter lors de son prochain appel au service. L’annulation
ne modifie rien ; en cas d’échec, conserver l’appareil et proposer un réessai.
Déconnexion locale : confirmation distincte, puis retour au parcours de connexion.
Ne pas annoncer de suppression du compte, du catalogue ou des progressions.

Proposition TV : déconnexion locale accessible ; gestion des autres appareils
guidée vers Réglages → Compte et appareils sur téléphone/web avec le même compte.
Ce choix précise une opération longue candidate ; il reste à valider visuellement.
Pas de QR code ni de lien d’activation inventé pour cette navigation.

Le contrat couvre `GET /me`, `GET /me/devices`, `DELETE /me/devices/{id}` et
`POST /auth/logout`. `is_current` identifie l’installation courante ; `last_seen_at`
est une dernière activité, pas une preuve de présence. La proposition ne change
ni le modèle d’authentification ni les droits d’accès.

## Application, aide et erreurs

Le choix FR/EN prévisualise l’interface traduite. `PATCH /me` expose déjà la locale
du compte, mais son articulation avec la langue locale de chaque surface reste
à vérifier et arbitrer dans Q6. La maquette ne simule aucune écriture du compte.

Aide : entrées courtes pour ajouter sa source et reprendre une lecture ; accès
aux informations publiées. En réalisation, montrer la version réellement installée,
jamais une constante de roadmap. La maquette indique explicitement « version cible ».
Les destinations des guides, de la confidentialité et des conditions doivent être
inventoriées et vérifiées ; leur emplacement n’atteste pas que les documents existent.

Repérage dans le dépôt le 19 septembre, sans recette de navigation :

| Ressource | Présence repérée | Travail S13-04 restant |
|---|---|---|
| Guides | Routes web `/{locale}/guides` et `/{locale}/guides/{slug}` ; catalogue M3U, Xtream, Android TV dans `apps/web/src/content/guides.ts` | Relire FR/EN, vérifier les destinations et l’accès mobile/TV |
| Appareils | Route web `/{locale}/app/devices` | Raccorder depuis les réglages et vérifier confirmations/erreurs |
| Confidentialité et conditions | Aucune route dédiée repérée dans l’inventaire `apps/web/src/app` | Identifier les documents et destinations approuvés avant d’ajouter des liens ; ne pas inventer leur contenu |
| Version | Affichage cible seulement dans la proposition | Déterminer la provenance de la version installée par surface |

En chargement du compte ou des appareils, garder les rubriques locales utilisables.
Une erreur propose Réessayer et ne devient pas une liste vide. Une écriture échouée
ne doit pas être annoncée réussie. Prévoir session expirée, appareil déjà retiré et
échec de lecture/écriture des préférences ; ces états restent à illustrer.

Focus proposé : rubrique sélectionnée dans la navigation ; Annuler dans une
confirmation, puis retour au déclencheur. Les préférences doivent rester nommées
et utilisables sans survol. Le retour système, le D-pad et la lisibilité à distance
exigent une recette réelle.

## Portée et recette

Prototype FR/EN, données fictives, préférences en mémoire séparées pour web/mobile/TV,
aucun service connecté. Les changements de surface simulent trois jeux de préférences,
sans constituer une preuve de persistance ou d’isolation entre appareils réels.
Les déconnexions sont simulées ; Mes sources récapitule le parcours précédent.
Le sélecteur de réseau et le test vidéo sont des commandes de démonstration.

Contrôler : rubriques, changements de langues, préférences indépendantes, confirmations,
blocage hors Wi-Fi, perte du Wi-Fi pendant lecture et catalogue encore consultable.
Compléter par lancement depuis Continuer, fiche, direct et autoplay, session expirée,
erreurs d’écriture, retour de focus et appareils réels avant clôture de S13.

Contrôles du prototype effectués : préférences séparées par surface, blocage des
lancements et pause simulée à la perte du Wi-Fi, annulation/confirmation de
déconnexion, changement FR/EN. Les cinq rubriques ont été parcourues dans les
deux langues sur les trois surfaces aux largeurs 320, 390, 736 et 1024 px, sans
débordement horizontal ni erreur JavaScript observée. Captures web et mobile
relues. Aucun de ces contrôles ne constitue une recette du produit réel.
