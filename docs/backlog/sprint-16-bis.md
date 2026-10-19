# Sprint 16-bis — Préparer le test interne Google Play

Statut : **ouvert le 19 octobre 2026**, version **0.4.0**, en mode rapide. Taille relative : M.
Placé avant la qualification [S17](sprint-17.md) : la recette groupée se jouera sur les versions
installées depuis la piste de **test interne** de Google Play, sur un vrai téléphone et une vraie TV.

## Avancement

- [ ] S16B-00 — décisions d'environnement — 40 % : VPS sous Docker (Hamza, 19/10) ; compte Google
  Play développeur existant. Reste : nom de domaine, fournisseur d'e-mails, accès au VPS.
- [ ] S16B-01 — pile de production sur VPS — 0 %
- [ ] S16B-02 — suppression de compte — 90 % (branche `feat/S16B-account-privacy`) :
  - [x] contrat : `POST /me/delete` confirmé par le mot de passe, `403 INVALID_CREDENTIALS`, ce qui est
    supprimé et l'effet sur les autres appareils ; clients régénérés ; [ADR 0014](../adr/0014-account-deletion.md)
    (proposé, à accepter par Hamza)
  - [x] API : suppression en cascade, jeton d'un compte supprimé refusé dès l'appel suivant, limitation
    par compte ; `AccountDeletionIntegrationTest` (balayage de toutes les colonnes `user_id` / `source_id`)
  - [x] web : Réglages → Compte et appareils, confirmation avec mot de passe puis déconnexion ; page
    publique statique `/fr|en/delete-account` (lien à donner à Google Play) ; vérifié sur le web local
  - [x] Android mobile et TV : Réglages → Compte et appareils, dialogue avec mot de passe, purge de fin
    de session réutilisée ; tests unitaires ; parcours vérifié sur émulateur téléphone et TV (mauvais mot de
    passe refusé, suppression, retour à la connexion / à l'activation)
  - [ ] recette sur appareils réels et à la télécommande (S17) ; acceptation de l'ADR 0014 ; durée de
    conservation des sauvegardes à écrire sur la page (S16B-00/01)
- [ ] S16B-03 — politique de confidentialité — 75 % (même branche) :
  - [x] page statique `/fr|en/privacy` (metadata, hreflang, sitemap), rédigée d'après le code, avec
    l'avertissement « à valider par Hamza »
  - [x] liens depuis le pied de page et Réglages → Aide sur le web, le mobile et la TV
  - [ ] **validation du texte par Hamza** et passages entre crochets à compléter : responsable du
    traitement, contact, hébergeur, fournisseur d'e-mails, durée de conservation (dépend de S16B-00)
- [ ] S16B-04 — numéro de version et bundle de publication — 0 %
- [ ] S16B-05 — fiche Play et formulaires — 0 %
- [ ] S16B-06 — publication sur la piste de test interne — 0 %

**Avancement global ≈ 6 %**.

## Objectif

Installer Lumo depuis Google Play sur un vrai téléphone et une vraie TV, connecté à une API en
ligne, sans rien qui fasse refuser l'application par Google.

## Pourquoi maintenant

Rien n'est déployé (notes de la 0.2.0 : « ni store, ni déploiement ») : une version de production
appelle `https://api.lumo.tv/v1`, qui n'existe pas. Google exige en plus, pour toute application qui
crée des comptes, une suppression de compte dans l'application et par le web, et une politique de
confidentialité publiée.

## Tâches proposées

| ID | Travail | Surface/dépendance |
|---|---|---|
| S16B-00 | Décider le domaine (API, site), le fournisseur d'e-mails (vérification d'adresse, `SPRING_MAIL_*`), l'accès au VPS ; noter le type de compte Play (un compte personnel récent doit faire un test fermé de 12 testeurs pendant 14 jours avant la production, pas avant le test interne) | Hamza |
| S16B-01 | Pile de production : `docker-compose.prod.yml` (PostgreSQL, API, site web en image Next.js autonome, proxy TLS automatique), secrets dans un `.env` du serveur jamais versionné, sauvegarde quotidienne de la base, procédure de mise en ligne et de mise à jour | Ops ; S16B-00 pour le domaine |
| S16B-02 | Suppression de compte : contrat d'abord (`openapi.yaml`), API (données, sources, appareils, jetons), web (Réglages → Compte) et page publique qui l'explique, Android mobile et TV | Toutes ; ADR si le protocole l'exige |
| S16B-03 | Politique de confidentialité FR/EN sur le site (statique, zone marketing), liée depuis Réglages → Aide sur les trois surfaces. Texte à valider par Hamza | Web, Android |
| S16B-04 | `versionCode` et `versionName` donnés à la construction (variables d'environnement, valeur par défaut pour le développement), 0.4.0 ; construction des deux bundles AAB signés, clé d'envoi hors dépôt | Android |
| S16B-05 | Fiche Play FR/EN des deux applications (texte, captures du banc uniquement, bannière TV), formulaire « Sécurité des données », classification, public visé ; mention claire « aucun contenu fourni, l'utilisateur ajoute sa propre source » contre le refus des lecteurs IPTV | Docs ; S16B-03 |
| S16B-06 | Envoi des deux bundles sur la piste de test interne, liste de testeurs. **Fait par Hamza** dans la Play Console : l'agent prépare, ne publie pas | Hamza ; tout le reste |

## Sortie

Les deux applications s'installent depuis le lien de test interne, créent un compte, ajoutent une
source du banc servie en ligne ou une source de l'utilisateur, et la TV s'active par code. Aucune
URL réelle, aucun secret, aucune capture de vraie chaîne dans le dépôt ni sur la fiche.
