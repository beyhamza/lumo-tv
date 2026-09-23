# Sprint 9 — Direct et guide sur les trois surfaces

Statut : préparation technique en cours ; banc S9-00 livré, en recette. Taille relative : XL.
Référence : [plan et DoD commune](../roadmap/0.2.0/delivery-plan.md).

Première [proposition d’écrans](../design/0.2.0/direct-guide.md) préparée le
19 septembre 2026 ; présentation générale retenue en conversation, réalisation
non commencée et recette réelle à effectuer.

## Objectif

Voir ce qui passe, consulter les programmes et lancer une chaîne depuis le Direct.
Stories : US-16 et complément US-020. Dépend de S8 et des arbitrages C1/Q5.

Ce lot reprend [le sprint 7](sprint-07.md) et son socle : ne pas programmer S7 en
plus de S9. La cible est le [cadrage 0.2.0](../design/0.2.0/direct-guide.md), qui
ajoute notamment la grille horaire TV. Réestimer, sans réutiliser ses 34 points.

## Tâches proposées

Préparation du 23 septembre : [proposition C1, D1 à D5 et C1-01 à C1-12](../roadmap/0.2.0/c1-grouped-epg.md).
Cadrage D1–D5 validé le 24 septembre. Le [rapport S9-00](../releases/0.2.0/s9-00-epg-bench.md)
recommande les plafonds de 5 000 occurrences et 4 Mio ;
S9-01 les fige avant toute évolution OpenAPI dans S9-02. Le dernier import EPG
réussi est distinct de la synchronisation du catalogue et de la récupération client.

| ID | Travail | Reprise/dépendance |
|---|---|---|
| S9-00 | Ajouter XMLTV relatif à la date, gzip, guide cassé/absent/ancien ; vérifier ingestion et rétention | Reprend S7-00 |
| S9-01 | Faire approuver C1 : lecture groupée, limites/erreurs, volume et vraie fraîcheur du guide | Reprend S7-01, après S9-00 |
| S9-02 | Implémenter contrat/serveur, générer les clients et tester limites et isolation par compte | Après S9-01 |
| S9-03 | Brancher cache/rétention Android et accès web ; mettre à jour En ce moment/Ensuite et les cartes d'accueil | Reprend S7-02/03/04 |
| S9-04 | Construire Chaînes/Guide, filtres conservés et recherche de chaînes sur les trois surfaces | Après S9-02/03 |
| S9-05 | Grilles TV/web, liste mobile et journée de chaîne, navigation temporelle et Maintenant | Étend S7-05/06 ; carte de focus TV |
| S9-06 | Fiche programme, lecture en cours, retour à la case, absences/ancienneté/erreurs | Après S9-05 |
| S9-07 | Recette intersurfaces et volume réseau mesuré, démonstration d'un changement de programme | Toutes |

## Avancement

Mis à jour le 24 septembre 2026. Une case cochée signifie recetté, pas seulement écrit.

- [ ] S9-00 — **en recette** : banc XMLTV livré, huit tests, rapport S9-00
- [x] S9-01 — cadrage D1–D5 validé le 24 septembre ; **gel** des plafonds
  (5 000 occurrences, 4 Mio) et des schémas dans [C1 §8](../roadmap/0.2.0/c1-grouped-epg.md)
- [ ] S9-02 — **en cours** sur `feat/US-16-grouped-epg` : contrat, migration, serveur, tests
- [ ] S9-03 — cache et accès clients
- [ ] S9-04 — Chaînes / Guide sur les trois surfaces
- [ ] S9-05 — grilles et journée
- [ ] S9-06 — fiche programme
- [ ] S9-07 — recette

## Cadrage S9-03 — arrêté le 24 septembre 2026

S9-03 reprend S7-02, S7-03 et S7-04 et pose le socle client de C1 avant les
écrans de S9-04 à S9-06. Règle commune : **une requête groupée par écran**, jamais
une par carte, et **rien d'affiché quand il n'y a rien** — pas de « programme
indisponible », pas d'espace réservé (S7-03).

| Surface | Livré en S9-03 | Laissé à |
|---|---|---|
| Android, `core:data` + Room | Table `epg_programme` (migration 6 → 7, schéma exporté), `EpgRepository` : fenêtre groupée par source, **le cache gagne**, purge locale avant D−1, oubli à la suppression d'une source ; `EpgWindow` porte sa date de récupération **et** l'`EpgImportStatus` du serveur ; fraîcheur D4 en fonction pure (> 24 h = ancien, à 24 h exactes non) ; découpage borné du 422 (chaînes puis fenêtre, 2 requêtes simultanées, 3 niveaux, fusion par UUID) | Grille TV, journée mobile : S9-05 |
| Android TV | « En ce moment » et « Ensuite » dans la barre du lecteur, une requête à l'ouverture de la chaîne, fenêtre de 3 h (S7-03) ; programme en cours sous chaque carte de la grille Direct, une requête par page visible (S7-04) | Vue Guide : S9-04/05 |
| Android mobile | Programme en cours sur les cartes Direct de l'accueil | Liste Chaînes et journée : S9-04/05 |
| Web | Chargeur `server-only` de la lecture groupée avec le même découpage borné et la même fonction de fraîcheur ; programme en cours sur les cartes Direct de l'accueil et sur les chaînes de la page catalogue, une requête par page | Grille horaire : S9-05 |
| Toutes | Aucun appel au fournisseur ; une chaîne sans `tvg_id`, une source sans guide, un guide non chargé → **rien**, la chaîne reste lisible ; libellé « Dernier import du guide », jamais « programmes à jour » | Lien Guide TV de l'accueil : S9-04 |

Les heures s'affichent dans le fuseau de l'appareil ; « en ce moment » se calcule
depuis les horaires et l'horloge locale, sans requête à la seconde.

## Démo et sortie

Preuve du 24 septembre : huit tests EPG, build API complet vert (295 tests).
Fixtures relatives à l’horloge et neuf mesures de volume, plus descriptions longues.
S9-00 est en recette ; S9-01 doit figer le contrat, S9-02 et les écrans restent à réaliser.

Complément du 20 septembre : [interactions et cas GD-01 à GD-14](../design/0.2.0/guide-interactions.md)
pour S9-00 et S9-04 à S9-07. Les règles de navigation Q5 sont précisées ; la
fraîcheur et la lecture groupée restent soumises à C1. Le schéma de focus complète
les écrans S9-E01 à S9-E05 ; aucune recette applicative n’est déclarée exécutée.

Changer de filtre entre Chaînes et Guide ; naviguer d'hier à J+3 selon les données,
revenir à Maintenant, ouvrir un programme courant et lancer le direct, revenir à
la case. Montrer le mobile en liste et la TV entièrement à la télécommande.
Tester guide absent/partiel/périmé, programme futur/passé sans lecture, programme
qui change et perte réseau. Les cases visibles ne produisent pas un appel par chaîne :
preuve réseau de la lecture groupée bornée.

Checks : contrat, API et ingestion PostgreSQL, Android/cache, web et statique marketing.
US-16 se clôture avec recette des trois surfaces ; US-020 gagne ses informations EPG.
