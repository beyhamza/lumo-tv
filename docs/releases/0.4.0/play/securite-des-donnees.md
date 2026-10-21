# Formulaires Play : sécurité des données, classification, public visé (S16B-05)

Réponses **proposées** pour les deux applications, établies d'après ce que le
code traite réellement au 19/10/2026 (`packages/contracts/openapi.yaml`, manifestes
fusionnés, dépendances Android). Hamza les relit et les saisit ; tout ce qui est
marqué **À confirmer** dépend d'une décision ou d'un travail qui n'est pas encore
fait.

Les deux applications partagent tout le code sous l'interface et le même
serveur : **les réponses sont identiques pour `tv.lumo.android` et
`tv.lumo.androidtv`**, sauf mention contraire.

---

## 1. Ce que le code traite réellement

| Donnée | Où | Envoyée au serveur Lumo | Pourquoi |
|---|---|---|---|
| Adresse e-mail | inscription, connexion (téléphone, web) | oui | compte, vérification d'adresse, mot de passe oublié |
| Mot de passe du compte | inscription, connexion | oui, haché (Argon2id), jamais stocké en clair | authentification |
| Nom affiché (facultatif) | inscription sur téléphone, Réglages | oui | affichage dans l'app |
| Langue | Réglages | oui | e-mails et interface |
| Sources : libellé, adresse de la playlist ou du serveur Xtream, identifiant et **mot de passe Xtream**, adresse du guide | ajout d'une source | oui ; le mot de passe Xtream est **chiffré au repos** (AES-256-GCM) et n'est renvoyé par aucune réponse | synchroniser le catalogue de l'utilisateur, construire l'adresse de lecture |
| Catalogue de la source (chaînes, films, séries, guide) | synchronisation, côté serveur | lu par le serveur chez le fournisseur de l'utilisateur | afficher le catalogue |
| Favoris, groupes, « Ma liste », chaînes récentes, progression de lecture, temps regardé | usage de l'app | oui | reprise, « Continuer », synchronisation entre écrans |
| Appareil : plateforme, nom et modèle (`Build.MODEL`, `Build.MANUFACTURER`), version de l'application, date de dernière activité | chaque connexion ou activation TV | oui | liste des appareils, révocation d'un appareil |
| Jetons de session | appareil | oui (présentés à chaque requête) | authentification ; stockés chiffrés par une clé du Keystore |
| Adresse IP | chaque requête | vue par le serveur | limitation des tentatives de connexion (par IP et e-mail) ; **À confirmer** : conservation dans les journaux du proxy de production (S16B-01) |

Ce que l'application **ne fait pas** (vérifié dans les dépendances et les
manifestes) : aucune publicité, aucun SDK d'analyse ni de rapport de plantage
(ni Firebase, ni Crashlytics, ni Sentry), aucun service Google Play
(`play-services`), aucune localisation, aucun accès aux contacts, photos,
fichiers, micro ou caméra, aucun achat intégré. La lecture des flux se fait
**directement** entre l'appareil et le serveur du fournisseur de l'utilisateur :
le flux vidéo ne passe pas par les serveurs Lumo.

---

## 2. Formulaire « Sécurité des données »

### Collecte et sécurité

| Question | Réponse proposée |
|---|---|
| L'application collecte-t-elle ou partage-t-elle des données utilisateur des types requis ? | **Oui** |
| Toutes les données collectées sont-elles chiffrées en transit ? | **Oui** — l'API de production est en HTTPS. **À confirmer** : le proxy TLS de S16B-01 en place et `LUMO_API_BASE_URL_RELEASE` en `https://`. (Les flux vidéo en `http` vont au serveur de l'utilisateur, pas à Lumo : ils ne sont pas une « collecte ».) |
| Proposez-vous un moyen de demander la suppression des données ? | **Oui** — dans l'application et par une page web. **À confirmer** : livré par S16B-02 ; l'URL de la page web est demandée par la Console |
| Création de compte | « Mon application permet aux utilisateurs de créer un compte » → **nom d'utilisateur (e-mail) et mot de passe**. L'application TV ne crée pas de compte : elle s'active par code ; même réponse, c'est le même compte |

### Types de données à déclarer

« Partagée » = transmise à un tiers. Les prestataires qui traitent les données
**pour le compte** de Lumo (hébergeur du VPS, fournisseur d'e-mails) ne sont pas
un partage au sens de Google : **aucune donnée n'est partagée**.

| Catégorie Play | Type | Collectée | Partagée | Facultative ? | Finalités à cocher |
|---|---|---|---|---|---|
| Informations personnelles | **Adresse e-mail** | oui | non | obligatoire | Fonctionnement de l'application ; Gestion du compte ; Communications du développeur (e-mails de vérification et de réinitialisation) |
| Informations personnelles | **Nom** | oui | non | **facultative** (nom affiché) | Fonctionnement de l'application ; Gestion du compte |
| Informations personnelles | **Identifiants utilisateur** (*User IDs*) | oui — identifiant de compte interne | non | obligatoire | Fonctionnement de l'application ; Gestion du compte |
| Activité dans l'application | **Autres actions** (*Other actions*) — favoris, Ma liste, chaînes récentes, progression | oui | non | obligatoire pour la reprise, pas de réglage pour la désactiver | Fonctionnement de l'application ; Personnalisation |
| Activité dans l'application | **Autre contenu généré par l'utilisateur** — sources ajoutées (adresses, identifiants du fournisseur) | oui | non | obligatoire (sans source l'app est vide) | Fonctionnement de l'application |
| Appareil ou autres identifiants | **Identifiants d'appareil ou autres** — identifiant d'appareil Lumo, modèle | oui | non | obligatoire | Fonctionnement de l'application ; Sécurité et prévention de la fraude (révocation d'un appareil) ; Gestion du compte |
| Historique d'achat, finances, santé, messages, photos, audio, fichiers, agenda, contacts, localisation, navigation web | — | **non** | — | — | — |

Points volontairement **non** déclarés, avec leur raison :

- *Mots de passe du compte* : Google ne les range dans aucune catégorie ; ils
  servent l'authentification et ne sont pas stockés en clair.
- *Historique de recherche* : la recherche interroge le catalogue au moment de
  la frappe ; **aucun historique n'est enregistré** côté serveur ni dans l'app.
  À re-vérifier si une « recherche récente » est ajoutée.
- *Adresse IP / localisation approximative* : l'IP n'est utilisée que pour la
  limitation de débit, sans en déduire une position. **À confirmer** avec la
  durée de conservation des journaux de S16B-01 ; si elle est conservée au-delà
  du traitement de la requête, Google recommande de la déclarer en
  « Identifiants d'appareil ou autres ».
- *Plantages et diagnostics* : aucun outil de rapport de plantage. Les
  statistiques que Google collecte lui-même via la Play Console ne sont pas à
  déclarer.

---

## 3. Classification du contenu (questionnaire IARC)

| Question | Réponse proposée | Justification |
|---|---|---|
| Catégorie | **Toutes les autres catégories d'applications** (*All Other App Types*) | Lumo n'est ni un service de streaming (il ne fournit aucun contenu) ni un réseau social. **À confirmer** : certains vérificateurs attendent « Divertissement » pour un lecteur ; « Toutes les autres » décrit plus fidèlement une application sans contenu propre |
| Violence, peur, sexualité, langage, substances, jeux d'argent | **Non** à tout | l'application ne contient aucun contenu |
| Interaction entre utilisateurs, partage de contenu entre utilisateurs | **Non** | aucun échange entre comptes |
| Partage de la position | **Non** | |
| Achats numériques | **Non** | aucun achat dans l'application en 0.4.0 |
| Accès non restreint à Internet / contenu choisi par l'utilisateur | **Oui** — à cocher par honnêteté | l'utilisateur lit ce que contient **sa** source, que Lumo ne contrôle pas. C'est aussi ce qui justifie le public 18+ ci-dessous |
| Application de type jeu | **Non** | |

Résultat attendu : une classification basse (PEGI 3 / Tout public) avec la
mention « Accès non restreint à Internet » ou équivalente. C'est cohérent : la
note porte sur l'application, pas sur ce que l'utilisateur y branche.

---

## 4. Public visé et contenu

| Question | Réponse proposée | Pourquoi |
|---|---|---|
| Tranches d'âge visées | **18 ans et plus** uniquement | une source ajoutée par l'utilisateur peut contenir n'importe quoi, et le contrôle parental est hors périmètre v1 (AGENTS.md §6). Viser un public plus jeune ferait entrer l'application dans le programme « Familles », aux exigences qu'elle ne remplit pas |
| L'application pourrait-elle attirer des enfants ? | **Non** | ni graphismes ni contenu destinés aux enfants |
| Annonces | **Non, l'application ne contient pas d'annonces** | |
| Application gouvernementale, financière, de santé, d'actualité | **Non** à tout | |
| Accès à l'application (*App access*) | **Certaines fonctionnalités sont restreintes** → fournir un compte de démonstration et une source **du banc**, voir [`checklist-play-console.md`](checklist-play-console.md) §5 | sans compte et sans source, le vérificateur voit un écran vide et refuse |
| Déclaration « Identifiant publicitaire » | **Non**, l'application n'utilise pas l'identifiant publicitaire | aucun SDK publicitaire ; la permission `AD_ID` n'est pas dans les manifestes fusionnés |

---

## 5. Reste à confirmer par Hamza

1. URL de la politique de confidentialité (S16B-03) et de la page de suppression
   de compte (S16B-02).
2. Proxy TLS en place et adresse de l'API de production (S16B-00, S16B-01).
3. Conservation des adresses IP dans les journaux de production (S16B-01) :
   déclaration ou non.
4. Catégorie IARC : « Toutes les autres » (proposé) ou « Divertissement ».
5. Si une fonction est ajoutée d'ici l'envoi (historique de recherche, rapport
   de plantage, Chromecast, achat), **refaire ce formulaire avant l'envoi** :
   une déclaration fausse est un motif de retrait, pas seulement de refus.
