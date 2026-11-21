# Test ouvert Google Play — ce qu'il faut en plus du test interne

État vérifié le 21/11/2026 sur le site en ligne (`https://lumo-tv.com`). Complète
[`checklist-play-console.md`](checklist-play-console.md), qui couvre le test interne : tout ce
qui y est fait reste valable, cette page ne liste que **ce qui change** pour la piste
**Test ouvert**.

## Ce qui change avec un test ouvert

| | Test interne | Test ouvert |
|---|---|---|
| Qui installe | jusqu'à 100 comptes listés | **n'importe qui**, la fiche est visible et cherchable sur le Play Store (mention « version test ») |
| Examen par Google | léger, souvent quelques minutes | **examen complet**, comme la production : quelques heures à plusieurs jours |
| « Contenu de l'application » | quelques tâches suffisent | **toutes** les tâches de la Console doivent être terminées |
| Fiche Play | facultative | **obligatoire et complète** : textes, icône, image de présentation, captures |
| Accès à la piste | immédiat | immédiat pour un compte **organisation** (voir §0) |

## 0. Type de compte : organisation — pas de test fermé obligatoire

Confirmé par Hamza le 21/11/2026 : le compte développeur est un compte **organisation**. Le verrou
des comptes personnels récents (test fermé de 12 testeurs pendant 14 jours, puis demande d'accès à
la production) **ne s'applique pas** : la piste Test ouvert est disponible dès que §1 à §6 sont
faits. Le test interne reste utile pour vérifier les bundles avant l'examen complet.

Contrepartie d'un compte organisation : la fiche publique affiche le nom et l'adresse de
l'organisation vérifiés par Google. Le responsable du traitement nommé dans la politique (§1) doit
être **la même entité**.

## 1. Politique de confidentialité — **bloquant**

URL à saisir dans **Contenu de l'application → Règles de confidentialité** (les deux applications) :

```
https://lumo-tv.com/fr/privacy
```

La page répond (200, HTTPS valide, sans connexion, pas un PDF), mais **elle affiche encore le
projet** : l'avertissement « Projet de texte, à valider par Hamza » et sept passages entre crochets.
Un examen complet refuse une politique qui ne nomme ni responsable ni contact. À faire :

- [ ] Administration → Documents légaux → Politique de confidentialité : compléter
  - [ ] responsable du traitement : raison sociale et adresse de l'organisation, identiques au
        compte développeur
  - [ ] adresse de contact (deux endroits : « Responsable » et « Vos droits »)
  - [ ] conservation des adresses IP par Caddy (journaux d'accès)
  - [ ] fournisseur d'e-mails
  - [ ] hébergeur et pays du VPS
  - [ ] durée de conservation des sauvegardes (`deploy/` : quotidienne, rotation à vérifier)
- [ ] **Publier** la version (le site refuse un texte qui garde des crochets) ; la page publique se
      met à jour aussitôt et l'avertissement disparaît.
- [ ] Faire la même chose pour la version anglaise (`/en/privacy`) si la fiche anglaise est ajoutée.

## 2. Contact — **bloquant**

- [ ] Créer l'adresse de contact (S16B-00), puis la saisir dans **Administration → Configuration**.
      Aujourd'hui le pied de page du site affiche « Contact [mock] données manquantes » : un
      examinateur qui ouvre la politique le voit.
- [ ] La même adresse dans **Paramètres de la fiche → Coordonnées** de chaque application (e-mail
      obligatoire, site `https://lumo-tv.com`).

## 3. Accès pour l'examinateur — **bloquant**

L'application ne fait rien sans compte ni source : sans accès, l'examen complet est refusé
(« fonctionnalité restreinte »).

- [ ] Compte de démonstration créé sur la production, adresse vérifiée.
- [ ] Une **source du banc servie en ligne** (flux libres de droits uniquement : Big Buck Bunny,
      flux de test Apple, Mux), déjà ajoutée au compte de démonstration pour que l'examinateur
      voie un catalogue sans rien saisir.
- [ ] Instructions dans **Accès à l'application** (identifiants saisis dans la Console, jamais dans
      le dépôt) ; pour Lumo TV, le pas à pas de l'activation par code sur
      `https://lumo-tv.com/activate`.
- [ ] Ne pas fermer les inscriptions (Administration → Configuration) pendant l'examen.

## 4. Formulaires « Contenu de l'application » — tous terminés

Réponses dans [`securite-des-donnees.md`](securite-des-donnees.md). Restent à trancher :

- [ ] **SDK Cast** (téléphone uniquement) : déclarer ou non les journaux envoyés à Google (§1 et §5
      du document). Une déclaration incomplète est le motif de refus le plus fréquent des examens
      complets.
- [ ] Catégorie IARC (« Toutes les autres » proposée).
- [ ] URL de suppression de compte : `https://lumo-tv.com/fr/delete-account` (en ligne, 200).
- [ ] « Suppression de données sans supprimer le compte » : **Oui**, URL
      `https://lumo-tv.com/fr/delete-data` (page ajoutée en S16B-08, en ligne après déploiement).
- [ ] Annonces **Non**, identifiant publicitaire **Non**, public **18 ans et plus**, applications
      gouvernementales / financières / santé / actualités **Non**.

## 5. Fiche Play complète

Fichiers prêts dans [`graphismes/`](graphismes/) et [`ecrans/`](ecrans/), textes dans
[`fiche-telephone.md`](fiche-telephone.md) et [`fiche-tv.md`](fiche-tv.md).

- [ ] Fiche principale FR (+ EN par « Gérer les traductions ») des deux applications.
- [ ] Icône 512, image de présentation 1024 × 500, captures téléphone ; captures TV et bannière
      1280 × 720 pour Lumo TV.
- [ ] Catégorie **Lecteurs et éditeurs vidéo**.
- [ ] Décider pour la ressemblance du logo ([`exigences-play.md`](exigences-play.md)) : une fiche
      publique est plus exposée qu'une piste interne.

## 6. Ce qui peut faire refuser Lumo TV en particulier

L'examen complet d'une application TV applique les règles de qualité Android TV. Un refus ne bloque
que la diffusion TV, pas le téléphone.

- [ ] Bannière du lanceur **sans le nom de l'application** ([`exigences-play.md`](exigences-play.md)) :
      à corriger avant l'envoi (nouveau bundle).
- [ ] Navigation entièrement à la télécommande jamais vérifiée sur un vrai téléviseur : la passer
      au moins une fois sur le test interne avant d'ouvrir.
- [ ] Facteur de forme Android TV ajouté et conditions acceptées.

## 7. Créer la piste de test ouvert

Une fois §0 à §6 faits, pour chaque application :

- [ ] **Tester → Test ouvert → Pays/régions** : choisir les pays (France, Belgique, Suisse,
      Canada… ou tous).
- [ ] **Testeurs** : nombre illimité ou plafond (1 000 minimum) ; adresse de retour pour les
      commentaires (l'adresse de contact).
- [ ] **Créer une release** : reprendre le bundle déjà envoyé en interne (« Ajouter depuis la
      bibliothèque ») ou un nouveau `LUMO_VERSION_CODE`.
- [ ] Notes de version FR/EN, puis **Envoyer pour examen** depuis **Vue d'ensemble de la
      publication**.
