# Checklist Play Console — test interne 0.4.0 (S16B-06, pour Hamza)

Pas à pas pour publier les deux applications sur la piste de **test interne**.
C'est Hamza qui clique : l'agent a préparé les fichiers, il ne publie rien.
Les libellés sont ceux de la Console en français ; ils bougent parfois, le chemin
reste reconnaissable.

> **Le piège qui ne se rattrape pas.** Le nom de package d'une application Play
> est fixé par le **premier** bundle envoyé, définitivement. Envoyer
> `app-tv-release.aab` dans l'application « Lumo » (téléphone) lie cette fiche à
> `tv.lumo.androidtv` pour toujours. Vérifier deux fois avant chaque premier envoi :
>
> | Application Play | Fichier | Package |
> |---|---|---|
> | **Lumo** | `app-mobile-release.aab` | `tv.lumo.android` |
> | **Lumo TV** | `app-tv-release.aab` | `tv.lumo.androidtv` |

---

## 0. Avant de commencer

- [ ] **S16B-01** : l'API de production répond en HTTPS à son adresse définitive.
      Sans elle, les applications s'installent mais personne ne peut créer de
      compte.
- [ ] **S16B-02** et **S16B-03** livrés : suppression de compte et politique de
      confidentialité en ligne (leurs URL sont demandées plus bas).
- [ ] La **clé d'envoi** existe, hors dépôt, sauvegardée avec son mot de passe
      ([`apps/android/RELEASE.md`](../../../../apps/android/RELEASE.md) §2).
- [ ] Les deux bundles sont construits avec la vraie clé d'envoi, le bon
      `LUMO_VERSION_CODE` (proposé : `40001`), et l'adresse de l'API de
      production (`LUMO_API_BASE_URL_RELEASE`, `LUMO_ACTIVATION_URL`) —
      RELEASE.md §3 à §5. Les bundles construits par l'agent avec une clé de
      **test** ne doivent **pas** être envoyés : la première clé reçue devient
      la clé d'envoi enregistrée.
- [ ] Noter le type de compte développeur : un compte **personnel** créé après
      novembre 2023 devra faire un test **fermé** (12 testeurs, 14 jours) avant
      la production. Rien de tel pour le test interne.

## 1. Créer l'application « Lumo » (téléphone)

- [ ] Accueil → **Créer une application**.
- [ ] Nom : `Lumo – lecteur de sources` (ou le nom retenu dans
      [`fiche-telephone.md`](fiche-telephone.md)) ; langue par défaut :
      **Français (France) – fr-FR**.
- [ ] Application ou jeu : **Application** ; Gratuite ou payante : **Gratuite**
      (ce choix ne peut plus revenir de gratuite à payante).
- [ ] Cocher les déclarations (Règles du programme pour les développeurs, lois
      américaines sur l'exportation) → **Créer l'application**.

## 2. Créer l'application « Lumo TV »

- [ ] Même chemin, nom `Lumo TV`, même langue, Application, Gratuite.
- [ ] Dans « Lumo TV » : **Test et publication → Configuration avancée →
      Facteurs de forme → Ajouter un facteur de forme → Android TV**, accepter
      les conditions propres à la TV.
      Si la Console propose alors une piste dédiée (« Android TV uniquement »),
      utiliser **sa** piste de test interne à l'étape 4.

## 3. Testeurs (une fois par application)

- [ ] **Tester → Test interne → Testeurs → Créer une liste d'adresses e-mail**,
      par exemple « Lumo — interne », avec les **comptes Google** des testeurs
      (jusqu'à 100). La même liste peut servir aux deux applications.
- [ ] Renseigner une adresse de retour (e-mail ou URL) pour les testeurs.
- [ ] Enregistrer. Le **lien d'invitation** apparaît sous la liste (« Copier le
      lien ») ; il n'est actif qu'après le premier déploiement de l'étape 4.

## 4. Envoyer le bundle sur la piste de test interne

Pour **Lumo** avec `app-mobile-release.aab`, puis pour **Lumo TV** avec
`app-tv-release.aab` :

- [ ] **Tester → Test interne → Créer une release**.
- [ ] Au premier envoi : **Signature d'application par Google Play** → garder
      « Utiliser une clé générée par Google » (recommandé) et accepter. La
      Console enregistre alors l'empreinte de la clé d'envoi du bundle.
- [ ] **Importer** le `.aab`. Vérifier ce que la Console affiche : package
      (tableau ci-dessus), `versionCode 40001`, `versionName 0.4.0`.
- [ ] Nom de la release : `0.4.0 (40001)`.
- [ ] Notes de version :

      <fr-FR>
      Première version de test interne. Ajoutez votre propre source : Lumo ne fournit aucun contenu.
      </fr-FR>
      <en-US>
      First internal test build. Add your own source: Lumo provides no content.
      </en-US>

- [ ] **Suivant** → lire les avertissements (un avertissement sur l'absence de
      fichier de désobscurcissement est attendu et sans gravité) →
      **Enregistrer** → **Déployer sur Test interne**.
- [ ] Vérifier dans **Configuration → Intégrité de l'application → Signature
      d'application** que l'empreinte SHA-256 de la clé d'envoi est bien celle
      de `keytool -printcert` (RELEASE.md §5), la même pour les deux
      applications.

## 5. Contenu de l'application (« Règles et programmes → Contenu de l'application »)

La Console dit dans **Vue d'ensemble de la publication** ce qui bloque un
déploiement ; le test interne en exige moins que la production. Tout est prêt
dans [`securite-des-donnees.md`](securite-des-donnees.md) :

- [ ] Règles de confidentialité : URL publique de S16B-03.
- [ ] **Accès à l'application** : « Toutes ou certaines fonctionnalités sont
      restreintes » → un compte de démonstration (e-mail + mot de passe) **et**
      les instructions pour ajouter une **source du banc** servie en ligne
      (aucune vraie chaîne). Pour Lumo TV, expliquer l'activation : se
      connecter sur le site avec le compte de démonstration et saisir le code
      affiché par la TV. Le compte et la source du banc en ligne restent à
      créer sur la pile de production (S16B-01) ; ne pas mettre ces
      identifiants dans le dépôt.
- [ ] Annonces : **Non**.
- [ ] Classification du contenu : questionnaire IARC (§3 du document).
- [ ] Public cible : **18 ans et plus** (§4).
- [ ] Sécurité des données (§2), dont l'URL de suppression de compte (S16B-02).
- [ ] Identifiant publicitaire : **Non**.
- [ ] Applications gouvernementales, fonctionnalités financières, santé,
      actualités : **Non**.

## 6. Fiche Play (« Développer la base d'utilisateurs → Fiche principale »)

Pas obligatoire pour le test interne, nécessaire avant tout examen :

- [ ] Textes FR et EN : [`fiche-telephone.md`](fiche-telephone.md),
      [`fiche-tv.md`](fiche-tv.md) (ajouter l'anglais par « Gérer les
      traductions »).
- [ ] Icône, image de présentation, bannière TV : [`graphismes/`](graphismes/).
- [ ] Captures : [`ecrans/`](ecrans/) — téléphone pour Lumo, Android TV
      pour Lumo TV.
- [ ] Catégorie **Lecteurs et éditeurs vidéo**, e-mail de contact, site web.

## 7. Installer chez les testeurs

- [ ] Envoyer aux testeurs le lien d'invitation de **chaque** application.
- [ ] Téléphone : ouvrir le lien avec le compte Google listé → **Accepter** →
      lien vers le Play Store → **Installer**.
- [ ] Télévision : accepter l'invitation de « Lumo TV » depuis un téléphone ou
      un ordinateur avec le **même compte Google** que celui de la TV, puis sur
      la TV : Play Store → rechercher « Lumo TV » (ou « Mes applications »).
      La mise à disposition peut prendre de quelques minutes à quelques heures
      après le premier déploiement.
- [ ] Premier lancement sur chaque appareil : créer un compte (téléphone),
      ajouter une source du banc ou une source personnelle, activer la TV par
      son code. C'est la sortie du sprint 16-bis et le point de départ de la
      recette S17.

## 8. Envois suivants

- [ ] Augmenter `LUMO_VERSION_CODE` (`40002`, …), reconstruire **les deux**
      bundles, les envoyer chacun dans une nouvelle release de leur piste.
      Un code déjà envoyé est refusé, même si la release a été abandonnée.
