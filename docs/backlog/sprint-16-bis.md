# Sprint 16-bis — Préparer le test interne Google Play

Statut : **ouvert le 19 octobre 2026**, version **0.4.0**, en mode rapide. Taille relative : M.
Placé avant la qualification [S17](sprint-17.md) : la recette groupée se jouera sur les versions
installées depuis la piste de **test interne** de Google Play, sur un vrai téléphone et une vraie TV.

## Avancement

- [ ] S16B-00 — décisions d'environnement — 40 % : VPS sous Docker (Hamza, 19/10) ; compte Google
  Play développeur existant. Reste : nom de domaine, fournisseur d'e-mails, accès au VPS.
- [ ] S16B-01 — pile de production sur VPS — 0 %
- [ ] S16B-02 — suppression de compte — 0 %
- [ ] S16B-03 — politique de confidentialité — 0 %
- [ ] S16B-04 — numéro de version et bundle de publication — 90 % : `LUMO_VERSION_CODE` /
  `LUMO_VERSION_NAME` (défaut 0.4.0) lus à la construction par les deux applis ; `bundleRelease`
  refuse de tourner sans clé d'envoi ni code de version ; deux AAB construits et signés avec une
  clé de **test** hors dépôt (même empreinte, `versionCode 40001`) ; procédure dans
  [`apps/android/RELEASE.md`](../../apps/android/RELEASE.md), état des exigences dans
  [`exigences-play.md`](../releases/0.4.0/play/exigences-play.md). Reste : clé d'envoi réelle et
  bundles définitifs (Hamza, adresse de l'API de S16B-00/01) ; avant la production, nom dans la
  bannière TV et icône adaptative.
- [ ] S16B-05 — fiche Play et formulaires — 85 % : fiches FR/EN des deux applis, sécurité des
  données, classification, public visé, graphismes, captures du banc (téléphone et TV, FR/EN) et
  checklist Play Console dans [`docs/releases/0.4.0/play/`](../releases/0.4.0/play/README.md).
  Reste à confirmer par Hamza : URL de confidentialité (S16B-03) et de suppression (S16B-02),
  conservation des IP (S16B-01), catégorie IARC, ressemblance du logo ; compte de démonstration
  et source du banc en ligne pour l'accès vérificateur.
- [ ] S16B-06 — publication sur la piste de test interne — 0 %

**Avancement global ≈ 31 %**.

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
