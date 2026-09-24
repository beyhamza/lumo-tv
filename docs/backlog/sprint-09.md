# Sprint 9 — Direct et guide sur les trois surfaces

Statut : en réalisation. C1 gelé en S9-01, contrat et serveur livrés en S9-02 ; S9-00 et
S9-03 en recette, S9-04 cadré et en cours, S9-05 à S9-07 à venir. Taille relative : XL.
Référence : [plan et DoD commune](../roadmap/0.2.0/delivery-plan.md).

Première [proposition d’écrans](../design/0.2.0/direct-guide.md) préparée le
19 septembre 2026 ; présentation générale retenue en conversation, réalisation
commencée (S9-00 à S9-04) et recette réelle à effectuer.

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
- [x] S9-02 — contrat C1 écrit et trois clients régénérés ; changeset 0020 (fiche
  d'import EPG avec identifiant de tentative) ; `EpgReadService` sous les deux plafonds
  en lecture `REPEATABLE READ` ; 24 tests, build API vert (319 tests). Vérifié le
  24 septembre sur la pile Docker avec un XMLTV local : 20 contrôles sur 20 (ordre
  des entrées, `NO_TVG_ID`, métadonnées, 400/404, bornes, `epg` sur la lecture
  unitaire, reconfiguration puis import raté sans fausse fraîcheur, lecture pendant
  `SYNCING`). Non joué sur la pile : les plafonds 5 001 / 4 Mio (couverts par les tests)
- [ ] S9-03 — **75 %**, cadrage ci-dessous. Android : Room `epg_programme` (migration
  6 → 7), `EpgRepository` cache d'abord puis une requête groupée, découpage borné du
  422, purge D−1, fraîcheur D4 et « en ce moment » en fonctions pures, barre du lecteur
  TV, grille Direct par page visible, cartes Direct de l'accueil ; 459 tests, lint et
  `assembleDebug` verts. Web : chargeur `server-only` avec le même découpage, mêmes
  fonctions pures, programme en cours sur l'accueil et la page catalogue, ligne
  « Dernier import du guide » ; typecheck, lint, 250 tests, build verts. Reste : recette
  à l'écran (hauteur des cartes TV à 140 dp, détection de la page visible, barre du
  lecteur), migration Room non testée faute de harnais, fuseau web = `Europe/Paris`
- [ ] S9-04 — **en cours** : cadrage ci-dessous, implémentation Android et web
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

## Cadrage S9-04 — arrêté le 24 septembre 2026

S9-04 construit la destination **Direct** à deux vues, **Chaînes** et **Guide**, sur
les trois surfaces, avec les règles de
[navigation et de mémoire](../design/0.2.0/guide-interactions.md) (GD-01 à GD-03).
La grille horaire, la journée mobile et la fiche restent à S9-05 et S9-06 ; la règle
« aucune commande inopérante » impose donc à la vue Guide un contenu réel dès S9-04.

| Élément | Livré en S9-04 | Laissé à |
|---|---|---|
| Deux vues | Bascule Chaînes / Guide dans Direct, sur les trois surfaces ; première ouverture sur Chaînes ; **dernière vue mémorisée par appareil et par source** (DataStore Android, cookie web) | — |
| Chaînes | Filtres partagés Toutes, Favoris (groupes) et catégories de la source ; sur web et TV, **colonne de catégories défilante à gauche**, cartes à droite (ajustement du 19 septembre) ; sélecteur compact sur mobile ; le programme en cours sous chaque carte vient de S9-03 ; sélection = lecture immédiate ; retour du lecteur = position et filtre retrouvés | Aperçu de lecture (S9-E01) : hors lot |
| Recherche | Par nom de chaîne, **conserve le filtre actif** ; sans résultat, proposer d'effacer la recherche ou de revenir à Toutes ; la colonne de catégories reste accessible | — |
| Guide, contenu S9-04 | La liste **En ce moment** : une ligne par chaîne du résultat filtré, programme en cours et suivant, une requête groupée par page (S9-03) ; sur mobile c'est le premier niveau validé (S9-E03) ; sur web et TV c'est le contenu du Guide **jusqu'à la grille de S9-05**, et le bouton Maintenant y ramène en haut | Grille horaire, jours, journée d'une chaîne : S9-05 ; fiche et états d'absence/ancienneté/erreur : S9-06 |
| Mémoire de session | Recherche et filtre partagés entre Chaînes et Guide, conservés à travers fiche et lecteur ; **changement de source** : recherche et filtre effacés, réponses en cours ignorées, vue mémorisée de la nouvelle source ; entre sessions, seule la vue est retenue | — |
| Accueil | *Toutes les chaînes* ouvre Chaînes sans recherche et sur Toutes ; **Guide TV** (nouveau lien, US-020) ouvre Guide sur Maintenant, sans recherche et sur Toutes ; ces accès priment sur la vue mémorisée | — |
| TV | La bande de puces devient une colonne ; carte de focus à mettre à jour ; « Repris » **conservé** en attendant un arbitrage (non cité dans les filtres validés, mais livré en S4-08) | Focus de la grille horaire : S9-05 |

## Découpage des tâches — arrêté le 24 septembre 2026

Chaque tâche est une PR, une seule plateforme, sur la branche `feat/US-16-grouped-epg`
ou une branche `feat/S9-04-xx-…` qui en repart. Les sous-issues Plane portent le
même `external_id` (`S9-04-01`…) et sont rattachées à l'item `S9-04`. Ordre = ordre
d'exécution. Réalisation : **@Dev**. Recette : **@QA**.

### S9-04 — destination Direct à deux vues Chaînes/Guide

| ID | Plateforme | Contenu et module | Fichiers | Critère d'acceptation |
|---|---|---|---|---|
| S9-04-01 | Android `core:data` | Mémoire de vue Direct : dernière vue par appareil **et** par source ; recherche et filtre partagés en session, seuls la vue survit entre sessions | `core/data/…/internal/DirectViewStore.kt` (nouveau, DataStore, sur le modèle d'`ActiveSourceStore.kt`), `…/repository/DirectViewRepository.kt`, `…/di/DirectViewModule.kt`, `core/data/src/test/…/DirectViewStoreTest.kt` | Rouvrir l'appli retrouve la vue de la source ; changer de source efface recherche/filtre et rend la vue mémorisée de la nouvelle source ; la recherche n'est **pas** persistée (GD-02, GD-03) |
| S9-04-02 | Android mobile `feature:live` | Bascule Chaînes/Guide, filtres Toutes/Favoris/catégories partagés, recherche par nom qui **conserve le filtre**, état sans résultat | `feature/live/…/LiveMobileScreen.kt`, `LiveViewModel.kt`, `LiveDestination.kt`, `app-mobile/src/main/res/values{,-fr}/strings.xml` | Chaînes→Guide garde texte et filtre et arrive sur Maintenant (GD-01) ; sans résultat, proposer Effacer et Toutes ; rien d'affiché sans guide (pas de texte inventé) |
| S9-04-03 | Android TV `feature:live` | Colonne de catégories défilante à gauche, cartes à droite, focus D-pad, bascule de vue | `feature/live/…/LiveTvScreen.kt`, `LiveViewModel.kt`, fichier de focus TV | La colonne reste atteignable quand la recherche est vide ; « Repris » conservé ; aucun focus sur élément masqué ou squelette |
| S9-04-04 | Android `feature:home` + apps | Entrées accueil : *Toutes les chaînes* → Chaînes (sans recherche, Toutes) ; *Guide TV* → Guide/Maintenant | `feature/home/…/HomeNavigation.kt`, `HomeMobileScreen.kt`, `HomeTvScreen.kt`, `HomeState.kt`, `HomeViewModel.kt`, `app-mobile/…/navigation/LumoMobileNavHost.kt`, `app-tv/…/navigation/LumoTvNavHost.kt` | Les accès explicites priment sur la vue mémorisée ; GD-02 |
| S9-04-05 | Android `feature:live` | Vue Guide « En ce moment » (servira les trois surfaces) : une ligne par chaîne du résultat filtré, en cours + suivant, **une requête groupée par page**, bouton Maintenant en haut | `feature/live/…/GuideNowList.kt` (nouveau), `LiveViewModel.kt` (consomme `EpgRepository` de S9-03) | Le nombre d'appels EPG ne dépend pas du nombre de cartes (preuve réseau) ; rien affiché quand il n'y a rien ; une réponse de l'ancienne source est ignorée (GD-03) |
| S9-04-06 | Web | Destination Direct à deux vues : **réutiliser la route existante** `/app/sources/[id]/channels` (une vue = `?view=channels\|guide`), pas de route dupliquée ; colonne de catégories, recherche par nom conservant le filtre, état dans l'URL, mémoire de vue par cookie | `src/app/[locale]/app/sources/[id]/channels/page.tsx` (à transformer), `src/components/app/DirectViews.tsx` (nouveau), `src/lib/direct/view-memory.ts` (nouveau), `src/messages/{fr,en}.json` | GD-01/02/03 web ; tout filtre est un lien et toute recherche un GET, la page fonctionne sans JS, et les URLs de S8 restent valides (les anciens liens ouvrent Chaînes) |
| S9-04-07 | Web | Vue Guide « En ce moment » et lien accueil *Guide TV* → Guide/Maintenant | `src/components/app/GuideNowList.tsx` (nouveau), `src/app/[locale]/app/page.tsx`, `src/lib/home/load-home-rails.ts`, `src/messages/{fr,en}.json` | Le lien prioritaire ouvre Guide sur Maintenant sans filtre ; la ligne « programme en cours » de S9-03 reste, aucune requête par carte |

### S9-05 — grilles, journée et navigation temporelle

| ID | Plateforme | Contenu et module | Fichiers | Critère d'acceptation |
|---|---|---|---|---|
| S9-05-01 | Android + Web (logique pure) | Fenêtre de jours J−1→J+3, fuseau de l'appareil, passage de minuit et changements d'heure ; **fonctions pures** partagées | `core/data/…/EpgDayWindow.kt` (nouveau) + `core/data/src/test/…/EpgDayWindowTest.kt` ; `src/lib/epg/day-window.ts` (nouveau) + `src/lib/epg/day-window.test.ts` | Les comparaisons portent sur des instants, pas des heures locales (GD-12) ; une journée sans données n'est jamais promise |
| S9-05-02 | Web | Grille horaire : chaînes en lignes, horaires en colonnes, navigation dans le temps, bouton Maintenant, créneau vide | `src/components/app/EpgGrid.tsx` (nouveau), page `sources/[id]/channels` (vue Guide) | GD-05/06 web ; une requête groupée par jour affiché ; créneau vide → « Aucun programme disponible sur ce créneau » |
| S9-05-03 | Android TV | Grille horaire et navigation D-pad à **heure de référence** conservée entre lignes | `feature/live/…/GuideGridTv.kt` (nouveau), `feature/live/…/GuideFocus.kt` (nouveau), `LiveTvScreen.kt` | GD-04/05/06 : durée différente sans dérive, pas de saut de chaîne sur une lacune, pas de boucle aux bords |
| S9-05-04 | Android mobile | Liste En ce moment → journée d'une chaîne, retour par niveau, navigation temporelle et Maintenant | `feature/live/…/GuideDayMobile.kt` (nouveau), `LiveMobileScreen.kt`, `LiveViewModel.kt` | GD-13 : Retour ramène à la liste En ce moment en conservant la position |

### S9-06 — fiche programme et états

| ID | Plateforme | Contenu et module | Fichiers | Critère d'acceptation |
|---|---|---|---|---|
| S9-06-01 | Android | Fiche programme (panneau latéral TV, bas mobile) ; actions courant/futur/passé ; temps qui passe | `feature/live/…/ProgrammeSheet.kt` (nouveau), `LiveViewModel.kt`, `LiveTvScreen.kt`, `LiveMobileScreen.kt` | GD-07/08 : à la fin, l'action disparaît, le focus va à Fermer, le titre ne change pas ; un futur devenu courant gagne l'action sans voler le focus, état revérifié à l'activation |
| S9-06-02 | Android | Retour à la case après fiche/lecteur, ancre de focus, repli déterministe si la case disparaît | `feature/live/…/GuideFocus.kt`, `LiveViewModel.kt` | GD-09/13 : recherche, filtre, créneau et ancre restaurés ; repli même chaîne/même heure sinon suivante puis précédente |
| S9-06-03 | Android | États : absence, ancienneté, erreur avec données ; Réessayer et Voir les chaînes ; message neutre | `feature/live/…/GuideStates.kt` (nouveau), `LiveViewModel.kt` (réutilise `EpgFreshness` de S9-03) | GD-10/11 : erreur initiale et erreur avec données distinctes ; les données et le focus survivent ; aucune cause inventée pour une liste vide |
| S9-06-04 | Web | Fiche programme et états équivalents | `src/components/app/ProgrammeSheet.tsx` (nouveau), page `sources/[id]/channels`, `src/messages/{fr,en}.json` | GD-07/08/10/11 web ; description et logo absents restent neutres |

### S9-07 — recette (préparée pour le QA, exécutée après S9-04 à S9-06)

| ID | Plateforme | Contenu | Livrable | Critère d'acceptation |
|---|---|---|---|---|
| S9-07-01 | Toutes | Protocole de recette GD-01→GD-14 : horloge contrôlable, fixtures à identifiants stables, preuve réseau du volume borné (une requête groupée par écran, jamais par carte), démonstration d'un changement de programme | `docs/releases/0.2.0/s9-07-recette.md` | Chaque cas GD a son pas-à-pas et son résultat attendu ; la preuve réseau montre un nombre d'appels indépendant du nombre de cartes |
| S9-07-02 | TV + web | Recette FR/EN, clavier web, D-pad réel, fuseau et changement d'heure (GD-12/14) | même rapport, annexe | Actions nommées, focus visible, aucun piège ni texte tronqué essentiel |
| S9-07-03 | Banc + infra | **Harnais de recette** : XMLTV de banc à dates relatives et variantes (I-1, I-2), horloge `LUMO_NOW` du web SSR (I-3), compteur d'appels `/epg` et panne partielle (I-4, I-5), playlist/XMLTV 100 chaînes (I-6) | `apps/web/e2e/bench/**`, helper `now()` et `src/lib/epg/now.ts` (remplace `new Date()` de `channels/page.tsx:327`), profil Compose de banc | Le protocole S9-07 s'exécute sans attente réelle ni source réelle : horloge surchargée, 100 chaînes, échec EPG injectable ; la preuve réseau compte les appels API |

> S9-04 est déjà `In Progress` ; S9-05 et S9-06 passent en `Todo` (découpées et prêtes) ;
> S9-07 reste en `Backlog` : la recette dépend de S9-04 à S9-06, et son harnais
> S9-07-03 doit être livré avant toute session de recette.
>
> **Revue (convention arrêtée le 24 septembre 2026).** Plane n'a pas d'état
> `In Review` : états réels `Backlog`, `Todo`, `In Progress`, `En recette`,
> `Done`, `Cancelled`. Une sous-issue reste donc `In Progress` pendant la revue
> Tech Lead et passe `Done` à l'approbation ; la story reste `In Progress`
> pendant la revue puis passe `En recette` quand elle part en QA.
>
> **Revue S9-04-01 — approuvée** le 24 septembre 2026 (branche
> `feat/S9-04-01-direct-view-memory`, commit `1ed4390`). Sécurité : une
> préférence non chiffrée, sans secret, assumée comme `ActiveSourceStore` ; ADR :
> aucune (même patron que US-018, pas de schéma ni de protocole) ; tests : 5 cas
> sur le vrai DataStore, dont la lecture brute des préférences qui prouve qu'aucune
> recherche ni filtre n'est persisté — `253/0/0` sur la suite `core:data`,
> rejouée indépendamment par le Tech Lead ; lisibilité : conforme au module.
> Réserve : le test tourne sur le stockage Okio alors que la production utilise le
> stockage `File` (raison Windows documentée) ; le schéma de clé est au-dessus des
> deux, l'écart est accepté. Ordre d'exécution validé pour le reste de S9-04 :
> 05 → 02 → 03 → 04 → 06 → 07 (05 fournit la liste « En ce moment » dont 02 et 03
> dépendent).

## Démo et sortie

Preuve du 24 septembre : huit tests EPG, build API complet vert (319 tests) et
24 tests C1 après S9-02. Fixtures relatives à l’horloge et neuf mesures de volume,
plus descriptions longues. S9-00 et S9-03 sont en recette ; S9-04 reste à réaliser
sur les trois surfaces, puis S9-05 et S9-06, avant la recette S9-07.

Complément du 20 septembre : [interactions et cas GD-01 à GD-14](../design/0.2.0/guide-interactions.md)
pour S9-00 et S9-04 à S9-07. Les règles de navigation Q5 sont précisées ; C1 est
gelé en S9-01 (plafonds et seuil de 24 h, §8) et la lecture groupée est livrée en
S9-02. Le schéma de focus complète les écrans S9-E01 à S9-E05 ; aucune recette
applicative n’est déclarée exécutée.

Changer de filtre entre Chaînes et Guide ; naviguer d'hier à J+3 selon les données,
revenir à Maintenant, ouvrir un programme courant et lancer le direct, revenir à
la case. Montrer le mobile en liste et la TV entièrement à la télécommande.
Tester guide absent/partiel/périmé, programme futur/passé sans lecture, programme
qui change et perte réseau. Les cases visibles ne produisent pas un appel par chaîne :
preuve réseau de la lecture groupée bornée.

Checks : contrat, API et ingestion PostgreSQL, Android/cache, web et statique marketing.
US-16 se clôture avec recette des trois surfaces ; US-020 gagne ses informations EPG.
