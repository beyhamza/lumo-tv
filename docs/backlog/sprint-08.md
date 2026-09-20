# Sprint 8 — Navigation, accueil et sources

Statut : en cours depuis le 19 septembre 2026. Taille relative : L.
Référence : [plan 0.2.0 et DoD commune](../roadmap/0.2.0/delivery-plan.md).

[Première proposition d’écrans](../design/0.2.0/sprint-08-screens.md) disponible
pour relecture ; disposition visuelle à valider, développement non commencé.

## Objectif

Sur mobile, TV et web, ouvrir Lumo, choisir sa source et retrouver un accueil utile.
Gérer les sources avec des états compréhensibles, sans accès aux paiements ni Google.

Stories : US-017, US-018, US-024 ; première intégration US-020/US-025.
Prérequis : C4/Q4 du [registre](../roadmap/0.2.0/open-questions.md), écrans accueil,
navigation et sources précisés. Pas de changement implicite des droits ou du chiffrement.

## Tâches proposées

| ID | Travail | Surface/dépendance |
|---|---|---|
| S8-00 | Établir le parcours de référence email → source → lecture et rassembler les recettes historiques hors Google | Toutes ; point de départ de recette |
| S8-01 | Cadrer et faire valider la consultation de l'ancien catalogue ; vérifier cascades et contrat de suppression | API/contrat, C4 ; avant clients concernés |
| S8-02 | Implémenter le lot C4 approuvé et ses tests, sans confondre consultation et permission de lecture | API/contrat ; après S8-01 |
| S8-03 | Introduire le choix de source mémorisé localement, filtrage cohérent et remplacement après suppression | Android/web ; US-018 |
| S8-04 | Adapter menus latéraux et Explorer mobile ; poser l'accueil à partir des rails existants | Toutes ; après S8-03 |
| S8-05 | Aligner Mes sources, auto_sync par source, étapes, erreurs, délai de retry, confirmations et guidage TV | Toutes ; après S8-02/03 |
| S8-06 | Poser les rubriques Réglages ; retirer des parcours 0.2.0 les accès non opérationnels Google/paiement sans refondre l'auth | Toutes |
| S8-07 | Recette de la verticale, FR/EN et retour/focus ; documenter les compléments encore attendus | Toutes |

## Avancement

Mis à jour le 19 septembre 2026. Une case cochée signifie recetté, pas seulement écrit.

- [ ] S8-00 — parcours de référence et recettes historiques
- [x] S8-01 — [lot C4](../roadmap/0.2.0/c4-previous-catalogue.md) cadré à partir du
  code réel, décisions D1 à D5 validées et reportées dans le registre, les
  décisions produit et US-024
- [x] S8-02 — contrat modifié, trois clients régénérés, gardes du catalogue,
  compteurs, limite de synchronisation et 20 tests ; build API vert (287 tests).
  Vérifié le 19 septembre sur la pile Docker avec le banc, 21 contrôles sur 21 :
  actualisation en cours, échec avec ancien catalogue, délai serveur, suppression.
  La recette sur appareils reste celle de S8-07
- [ ] S8-03 — **75 %** : source active livrée sur les trois surfaces
  (`feat/US-018-active-source`). Android : `ActiveSourceRepository`, sélecteur
  mobile et TV, filtrage des catalogues, favoris et reprises ; 265 tests unitaires,
  lint et `assembleDebug` verts. Web : cookie par compte, sélecteur sans JavaScript,
  liens Direct/Films/Séries, favoris filtrés ; typecheck, lint, 115 tests et build
  verts, pages marketing toujours statiques. Reste : voir le sélecteur à l'écran
  (téléphone, télécommande, navigateur), le parcours à deux sources et deux
  appareils, et la vérification toutes les 60 s pendant une lecture (avec S8-05).
  Playwright : suite `journey` périmée depuis fin août, sans lien avec cette tâche
- [ ] S8-04 — **75 %** : accueil et navigation livrés sur les trois surfaces
  (`feat/US-017-home-navigation`), périmètre arrêté dans US-017. Android : module
  `:feature:home`, Explorer sur mobile, rail TV à six entrées, Ma bibliothèque sur
  TV, assemblage des reprises et libellés de synchronisation descendus dans
  `core:data` ; 330 tests unitaires, lint et `assembleDebug` verts. Web : accueil à
  trois rangées, états vierge/synchronisation/erreur, menu 0.2.0 ; typecheck, lint,
  158 tests et build verts, marketing statique. Carte de focus TV à jour. Reste :
  recette à l'écran. Écart connu : sur le web, une carte de film ouvre la fiche
  (« Reprendre à… ») au lieu de lancer la lecture
- [ ] S8-05 — **40 %**, sur `feat/US-024-my-sources`, spécification US-024. Web livré :
  Mes sources (compteurs, source utilisée, Utiliser cette source), actualisation avec
  `Retry-After`, confirmation de suppression détaillée, catalogues avec bandeau en
  actualisation ou en erreur, refus de lecture dont `SOURCE_AUTH_FAILED`, arrêt de la
  lecture quand un `404` prouve la suppression ; typecheck, lint, 209 tests et build
  verts. Android : en cours. Reste aussi la recette à l'écran
- [ ] S8-06 — rubriques Réglages, retrait des accès Google et paiement
- [ ] S8-07 — recette de la verticale

Arbitrages du 19 septembre 2026 pour ce sprint :

- la lecture reste fermée pendant `SYNCING` ; seule la consultation s'ouvre (C4) ;
- après un échec, la lecture est autorisée si un catalogue précédent existe, sauf
  identifiants refusés ou abonnement expiré (C4, D2) ;
- le délai serveur `SOURCE_SYNC_RATE_LIMITED` / `Retry-After`, au contrat mais
  jamais émis, est implémenté dans S8-02 plutôt que retiré de la démo ;
- l'accueil validé prévaut, pour l'accueil seulement, sur l'ancienne règle qui
  écartait les rangées des grilles TV (S2-13, S4-08) ;
- S8-06 retire aussi la section Tarifs du site et le texte Android invitant à
  changer d'offre ; les quotas servis par le serveur ne changent pas.

## Compléments relevés pendant le sprint

- **Corriger une source en erreur depuis le web** : le lien « Corriger les
  identifiants » recharge la page de la source ; il n'existe aucun formulaire web
  pour modifier l'hôte, l'identifiant ou le mot de passe d'une source. Le contrat le
  permet déjà (`PATCH /sources/{id}`). US-024 demandait d'auditer ce formulaire avant
  de le détailler : l'audit est fait, la réalisation reste à planifier.
- **Suite Playwright `journey`** périmée depuis le 29 août (interface des groupes de
  favoris) et démontage e2e qui laisse un conteneur orphelin : sans lien avec S8.
- **Noms de chaînes réels** dans un test Android et dans un test et un commentaire de
  l'API, antérieurs à ce sprint et contraires à `AGENTS.md` §1.
- **`safeRedirectTarget("/fr//hôte")`** renvoie `//hôte` : à durcir.

## Démo et sortie

Compte email connecté, deux sources de banc : choisir l'une sur téléphone sans
changer celle de la TV, rouvrir l'application, parcourir l'accueil et lire. Montrer
une actualisation en cours puis en erreur avec ancien catalogue, un délai serveur,
et une suppression confirmée avec remplacement correct sur un second appareil.
Tester séparément aucune/une/plusieurs sources restantes et un compte vierge.

US-018 peut se clore ; US-024 attend encore la cascade À regarder de S11. US-017
reste partielle : règles Continuer en S12, EPG en S9. Les anciens rails ne sont
pas présentés comme la totalité de la nouvelle fonctionnalité.

Fixtures : deux sources neutres, première ingestion, source en erreur et suppression
pendant consultation. Inclure les compléments du banc dans les tâches qui les utilisent.
Checks : API si modifiée, Android, web ; contrat/génération si C4 le modifie ; recette réelle.
