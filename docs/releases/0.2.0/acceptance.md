# Recette et critères de sortie — 0.2.0

Plan de recette, créé le 17 septembre 2026. **Non exécuté.** Version gratuite,
compte email et activation TV ; Google et paiements exclus par décision utilisateur.

## Préparation

Voir le [plan d’exécution](execution-plan.md) pour les sessions de bout en bout,
le format des preuves et le dossier de livraison à assembler en S14.

- Build candidat identifié par commit, versions et configuration non secrète.
- Téléphone Android réel, Android TV/box avec télécommande, navigateurs retenus
  identifiés par version. Couvrir Chromium et un parcours HLS natif si celui-ci est
  annoncé compatible ; arrêter la matrice exacte avant S13.
- Deux comptes, deux appareils et au moins deux sources neutres de banc, dont un
  compte vierge ; aucune source réelle ni donnée sensible dans les preuves.
- Fixtures EPG, médias autorisés avec pistes/variantes, source erronée, catalogue
  volumineux et serveur sans seek préparés dans les sprints concernés.

## Matrice de clôture

Chaque ligne exige une preuve par surface applicable ; le statut ci-dessous est
celui de ce plan, pas une affirmation sur les recettes historiques.

| ID | Parcours | Lots | État |
|---|---|---|---|
| R020-01 | Email, session persistante, déconnexion, activation TV, absence de fuite entre comptes | S8/S14, recettes 1/2 | Non joué |
| R020-02 | Source active locale, accueil et navigation, retour/focus | S8/S12 | Non joué |
| R020-03 | Sources : ajout, états, ancienne donnée pendant sync, retry, suppression et remplacement | S8/S11 | Non joué |
| R020-04 | Direct/Guide, filtres, dates, fiche, absence/ancienneté/erreurs, lecture groupée | S9 | Non joué |
| R020-05 | Recherche par type, saisie rapide, source changée, erreur partielle et retour | S10 | Non joué |
| R020-06 | Favoris/groupes, ordre et dédoublonnage, gestes, propagation | S11 | Non joué |
| R020-07 | À regarder : ajout/retrait, partage, tri, source supprimée | S11 | Non joué |
| R020-08 | Continuer : lecture effective, seuil de fin, reprise, masquage et réapparition | S12 | Non joué |
| R020-09 | Films et séries : fiches, seek disponible/indisponible, progression et erreurs | S12/S13, recettes 5/6 | Non joué |
| R020-10 | Audio/sous-titres/qualité selon capacités et préférences locales | S13 | Non joué |
| R020-11 | Épisode suivant, décompte annulé, mode manuel, saison/final et reprise | S13 | Non joué |
| R020-12 | Réglages, langues, informations, réseau mobile et autoplay | S13 | Non joué |
| R020-13 | FR/EN, clavier/focus TV, lisibilité, contenu volumineux, erreurs réseau | Tous | Non joué |
| R020-14 | Aucun contenu réel ajouté, aucun secret/URL sensible dans les preuves et logs | Tous | Non joué |
| R020-15 | Pas de parcours Google/paiement non opérationnel exposé ; quotas existants non modifiés implicitement | S8/S14 | Non joué |
| R020-16 | Mise à jour depuis version précédente, migrations, données conservées, catalogue et lecture toujours accessibles | S14 | Non joué |

R020-12 inclut la pause après perte du Wi-Fi pendant une lecture, lorsque Lecture
sur données mobiles est désactivée (décision du 19 septembre 2026). Vérifier le
message, la position conservée et l’arrêt des requêtes média ; le catalogue reste
consultable. Les autres cas de Q7 attendent leurs arbitrages.

Google OAuth et encaissement Stripe : **hors périmètre**, sans clôturer leurs dettes.

R020-08 reprend les [cas Continuer](../../design/0.2.0/continue-watching-cases.md),
dont CW-17 à CW-19 validés le 19 septembre : cumul de 30 s entre sessions/appareils,
réapparition au démarrage réel après retrait et suivant non commencé proposé.
Les autres arbitrages restent ouverts et aucun de ces cas n’est déclaré joué.
Complément validé : CW-20 à CW-22 couvrent le contenu court terminé absent de
Continuer, le suivant déjà terminé relu depuis le début et Recommencer sans
nouvelle attente pour une carte éligible. Les vérifier dans R020-08/09/11 ; Q2
est tranché côté produit, ces recettes restent non jouées.
Chaque rapport conserve date, commit, surface/appareil, cas, résultat, preuve et
anomalie liée. Créer les rapports au moment de l'exécution, sans préremplir des succès.

## Vérifications techniques prévues

Consulter les AGENTS locaux au moment de l'exécution. Commandes repérées dans le
dépôt, à adapter au shell et aux runtimes présents, sans mise à jour de dépendance :

| Zone | Checks attendus |
|---|---|
| Contrat, depuis racine | `npm --prefix packages/contracts run lint` et `npm --prefix packages/contracts run check` |
| API, depuis apps/api | `./gradlew build` avec PostgreSQL via Testcontainers |
| Android, depuis apps/android | `./gradlew build` ; au fil des changements `testDebugUnitTest`, `lint`, `assembleDebug` selon les AGENTS |
| Web, depuis apps/web | `pnpm lint`, `pnpm typecheck`, `pnpm test`, `pnpm build`, `pnpm test:e2e` |

Sur Windows, utiliser le wrapper `.bat` approprié ; la génération du contrat exige
aussi le shell prévu par ses scripts. Conserver la vérification CI des pages
marketing statiques, des traductions et du contenu interdit. Les checks sont
planifiés ici, pas exécutés lors de la rédaction.

## Autorisation de sortie

- Toutes les stories du périmètre et tous les compléments clos avec leurs preuves.
- Aucun cas obligatoire rouge ou non joué ; pas de défaut critique de lecture,
  perte de données, focus bloquant ou fuite de données entre comptes.
- Évolutions contractuelles explicites, migrations vérifiées et clients régénérés.
- Notes de sortie, limites réelles, versions et procédure de livraison prêtes.
- Toute limitation nouvelle est arbitrée, jamais cachée sous une mention « terminé ».
- La publication effective reste une action distincte, à demander explicitement.
