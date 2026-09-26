# Sprint 9 — Direct et guide sur les trois surfaces

Statut : en réalisation. C1 gelé en S9-01, contrat et serveur livrés en S9-02 ; S9-00 et
S9-03 en recette ; S9-04 livré et **En recette** sur les trois surfaces (01→07 approuvées),
correctifs des défauts #1 fusionnés ;
S9-05 **En recette** (01→04 `Done`, journée mobile `f77930e` fusionnée via PR #5 ; correctifs #2/#3/#4 fusionnés) ; S9-06 découpée et prête (Todo) ;
S9-06 **en réalisation** (01→04) ; S9-07 passe **`Backlog` → `Todo`** (harnais `S9-07-03` à livrer avant la recette). Aucun item n'est déclaré terminé tant que
la recette réelle (DoD commune) n'est pas jouée. Taille relative : XL.
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

Mis à jour le 26 septembre 2026. Une case cochée signifie recetté, pas seulement écrit.

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
- [ ] S9-04 — **En recette** : 01→07 approuvées par le Tech Lead (Android mobile/TV,
  web), story passée `En recette` dans Plane le 24 septembre 2026 ; reste la recette
  réelle à l'écran (S9-07) ; accès à froid corrigés, revus et **fusionnés dans `main`** —
  `BUG-S9-04-07-01` (#213, web) et `BUG-S9-04-04-01` (#217, Android mobile **et** TV),
  PR #1 et #2
- [ ] S9-05 — **En recette** : découpage (01→04) **terminé**. **S9-05-01 `Done`**
  (fonctions pures de jour et de fuseau, Android `555e03d` + Web `ab97891`),
  **S9-05-02 `Done`** (grille web, `910dd13`), **S9-05-03 `Done`** (grille TV + D-pad,
  `b1cf500`) et **S9-05-04 `Done`** (journée d'une chaîne mobile, `f77930e`, recette QA
  7/7 versionnée sous `docs/releases/0.2.0/qa-evidence/s9-05-04-mobile-channel-day-2026-09-26/`),
  les quatre approuvées par le Tech Lead et **fusionnées dans `main`** (`7173b5b` puis
  PR #5 `c1496fc`) ; `BUG-S9-05-02-01` (grille web) et
  `BUG-S9-05-03-01` / `BUG-S9-05-03-02` (grille et en-tête TV) corrigés et **fusionnés
  dans `main`** (PR #3 et #4). **Décision PO du 26 septembre :
  S9-05-04 reste dans le périmètre de S9-05** — la journée mobile est livrée, la story
  ne part en recette de sortie qu'en recette intersurfaces (S9-07).
- [ ] S9-06 — **In Progress** : découpage arrêté (01→04). **S9-06-01 `Done`**
  (`feat/S9-06-01-programme-sheet` @ `f2d738f` + `fix/S9-06-01-tv-sheet-focus`
  @ `c91eedd` : 4 tests instrumentés verts, dont le cas de focus ; `BUG-S9-06-01-01` `Done`).
  **S9-06-02 `Done`** (`feat/S9-06-02-guide-return-anchor`, recette conforme par lecture
  et unitaires). **S9-06-03** : recette rendue (`qa/S9-06-02-03-recette` @ `4e20072`) mais
  un **défaut d'affichage TV** de l'« erreur initiale » du Guide a été confirmé
  → `BUG-S9-06-03-01` (Todo, priorité haute), correctif attendu sur
  `feat/S9-06-03-guide-states`. **S9-06-04** (web) : recette rendue
  (`qa/S9-06-04-web-recette` @ `f0be4fe`) — 01/02/03/04/05 (1<sup>re</sup> moitié)/06/08/09/10/12
  conformes, `QA-06-04-07` **retiré du périmètre web** et `QA-06-04-11` **non applicable
  au web** (natif Android/TV, voir Précision ci-dessous). Un PR à la fois ; cycle S9
  ouvert le 26/09.
- [ ] S9-07 — **Todo** : protocole écrit par QA
  (`docs/releases/0.2.0/s9-07-recette.md`, `be2a89a`), **non exécuté** ; **S9-07-03
  `Done`** (harnais : `feat/S9-07-03-bench-harness` @ `1afa234`, mergé `7d3e6d2`) —
  I-1→I-5 livrés. Recette `S9-07-01`/`02` **gated** : elle démarre quand S9-06 est vert,
  c'est-à-dire après la correction de `BUG-S9-06-03-01` (TV). Les cas web restants de
  S9-06-04 sont levés (`QA-06-04-03` conforme, `QA-06-04-11` non applicable).

### Reste à faire (état au 26 septembre 2026)

1. **`BUG-S9-06-03-01` (TV, priorité haute)** — corriger le rendu de l'« erreur
   initiale » du Guide sur `feat/S9-06-03-guide-states`, puis revue @Tech Lead et
   re-recette @QA (titre + corps + les deux boutons peints). Le rework @Dev est à
   **relancer** : la première assignation a expiré (`timed_out`). ⚠️ La copie de
   travail de cette branche porte un **WIP non commité** (`LumoStateMessage.kt`,
   `GuideGridTv.kt`, `feature/live/build.gradle.kts`) : ne pas repartir d'un checkout
   propre qui l'écraserait sans le préserver.
2. **S9-06-04 (web)** — recette close de notre côté ; l'état `Done` de la sous-issue
   reste posé par @Tech Lead. Story `S9-06` à passer quand TV est vert.
3. **S9-07-01/02** — exécuter la recette intersurfaces, **gated** sur S9-06 vert ; le
   harnais `S9-07-03` est livré. Une campagne e2e en mode `auto` devra recréer
   `lumo-e2e-bench` via compose (le conteneur ne redémarre pas tel quel).
4. **PRs à ouvrir par Hamza** (`gh` absent) : `feat/S9-06-01-programme-sheet`,
   `feat/S9-06-02-guide-return-anchor`, `feat/S9-06-03-guide-states` (après le
   correctif), `feat/S9-06-04-web-programme-sheet`, `fix/S9-06-01-tv-sheet-focus`, et
   les branches de preuves QA.
5. **S9-05** reste `In Progress` (journée mobile dans le périmètre) jusqu'à la recette
   intersurfaces ; les correctifs #2/#3/#4 sont fusionnés.

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

### Précision produit — 26 septembre 2026 (recette S9-07)

L'accès « Toutes les chaînes » / « Guide TV » de l'accueil est un **accès aux
catalogues disponibles** : il doit exister dès que la source est prête, **sans
historique ni favori**. C'est déjà écrit dans les
décisions 0.2.0 (« Source prête sans activité : accès aux catalogues disponibles et
invitation à explorer », et ligne « Direct sur l'accueil ») et dans US-017. La règle
« section vide : la masquer sans espace réservé » vaut pour le **rail**, pas pour les
deux accès : un rail de récentes vide ne peut donc pas emporter « Toutes les chaînes »
et « Guide TV ». Vaut pour **S9-04-07 (web)** et **S9-04-04 (Android mobile et TV)** ;
les deux liens s'affichent indépendamment du contenu du rail. Le *comment* (liens
conservés dans un rail vide, ou ligne d'accès autonome) est technique : @Tech Lead.

### Précision produit — 26 septembre 2026 (recette S9-06)

**GD-10 « erreur avec données » est un critère natif Android/TV pour la 0.2.0.** Le
web est rendu côté serveur : au moment d'une lecture `/epg` en échec, il n'a **pas** de
grille précédente à conserver, donc le critère « la grille et la position survivent » ne
décrit pas un comportement web. Ce qui est exigé et accepté sur web, c'est l'**erreur
initiale distincte** (message neutre, jamais « guide vide ») avec **Réessayer** et
**Voir les chaînes**, couverte par `QA-06-04-06`. Aucun lot « cache client web » n'est
ouvert ce cycle ; c'est un candidat de version ultérieure. Portée : S9-06-04 /
`QA-06-04-07`, design GD-10 (`docs/design/0.2.0/guide-interactions.md`).

**`QA-06-04-11` « changement de source, fiche ouverte » (web) est lui aussi un critère
natif Android/TV pour la 0.2.0** quand le changement vient d'un **autre appareil** : le
web n'a pas de source active de compte, le choix de source y est un cookie **par
appareil** et la fiche est liée à l'URL, jamais à la source active. L'attendu web accepté
(et prouvé, `run4` de la recette) est que la fiche d'un appareil reste celle de **sa**
source quand un autre appareil change la sienne. Aucun lot ouvert. Portée : S9-06-04 /
`QA-06-04-11`, design GD-03 (`docs/design/0.2.0/guide-interactions.md`).

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
| S9-04-07 | Web | Entrées d'accueil **explicites** : *Guide TV* → `?view=guide`, *Toutes les chaînes* → `?view=channels` (la vue Guide « En ce moment » est livrée avec S9-04-06, dans `DirectViews.tsx`) ; le rail Live porte les deux liens | `src/app/[locale]/app/page.tsx`, `src/components/app/ChannelRail.tsx` (prop `more` à plusieurs liens), `src/messages/{fr,en}.json` | Le lien *Guide TV* ouvre Guide/Maintenant sans filtre et prime la mémoire ; *Toutes les chaînes* ouvre Chaînes et prime la mémoire ; aucune requête par carte |

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

**Plan de test QA S9-06** : [s9-06-plan-de-test.md](../releases/0.2.0/s9-06-plan-de-test.md)
(26 septembre 2026). Un cas par critère GD-07/08/09/10/11/13 + cas limites.
**Partiellement exécuté** : S9-06-01 instrumenté 4/4, S9-06-02/03 par lecture et
unitaires (preuves `qa/S9-06-02-03-recette` @ `4e20072`), S9-06-04 web
(`qa/S9-06-04-web-recette` @ `f0be4fe` : `QA-06-04-03` conforme, `QA-06-04-11` non
applicable au web, `QA-06-04-07` natif-only). Reste la recette TV de l'erreur initiale
à rejouer après le correctif de `BUG-S9-06-03-01`.

### S9-07 — recette (préparée pour le QA, exécutée après S9-04 à S9-06)

| ID | Plateforme | Contenu | Livrable | Critère d'acceptation |
|---|---|---|---|---|
| S9-07-01 | Toutes | Protocole de recette GD-01→GD-14 : horloge contrôlable, fixtures à identifiants stables, preuve réseau du volume borné (une requête groupée par écran, jamais par carte), démonstration d'un changement de programme | `docs/releases/0.2.0/s9-07-recette.md` | Chaque cas GD a son pas-à-pas et son résultat attendu ; la preuve réseau montre un nombre d'appels indépendant du nombre de cartes |
| S9-07-02 | TV + web | Recette FR/EN, clavier web, D-pad réel, fuseau et changement d'heure (GD-12/14) | même rapport, annexe | Actions nommées, focus visible, aucun piège ni texte tronqué essentiel |
| S9-07-03 | Banc + infra | **Harnais de recette** : XMLTV de banc à dates relatives et variantes (I-1, I-2), horloge `LUMO_NOW` du web SSR (I-3), compteur d'appels `/epg` et panne partielle (I-4, I-5), playlist/XMLTV 100 chaînes (I-6) | `apps/web/e2e/bench/**`, helper `apps/web/src/lib/epg/clock.ts` (remplace le `new Date()` du rendu serveur de `channels/page.tsx` **et** de `src/lib/home/load-home-rails.ts`), profil Compose de banc | Le protocole S9-07 s'exécute sans attente réelle ni source réelle : horloge surchargée, 100 chaînes, échec EPG injectable ; la preuve réseau compte les appels API |

### Défauts confirmés par la recette du 26 septembre 2026 (web + TV, EPG réel)

Le dépôt reste la référence ; les correctifs de densité (#2/#3/#4) relèvent de
l'arbitrage technique du @Tech Lead.

| # | Constat recette | Story | Décision produit |
|---|---|---|---|
| 1 | Accueil à froid (source prête, aucun historique) : « Toutes les chaînes » / « Guide TV » absents, rail masqué car vide | S9-04-07 (web) ; S9-04-04 (Android mobile **et** TV) | **Bug** confirmé sur les **trois** surfaces : les deux accès sont exigés à froid (voir Précision ci-dessus). Corrigé (`BUG-S9-04-07-01`, `BUG-S9-04-04-01`), recettes QA conformes, **fusionné dans `main`** (PR #1 et #2). |
| 2 | Grille web à 1440 px : heures qui se chevauchent, titres tronqués à 1–3 caractères | S9-05-02 | **Défaut** de lisibilité ; à corriger avec la densité. |
| 3 | Grille TV 1080p : cellules ~50 px, titre **et** horaire réduits à « … » (donnée correcte au log) | S9-05-03 | **Défaut** confirmé : la donnée est là, le rendu ne l'affiche pas. Correction obligatoire avant recette de sortie. |
| 4 | En-tête de jour TV : une seule pastille visible (Row ~192 px), les 5 jours écrasés par « Now » / « See channels » | S9-05-03 | **Défaut** confirmé, même sujet de densité que #3. |
| 5 | Compteur « 1 chaînes » (pluriel) | hors S9 | Cosmétique, hors sprint ; à corriger au prochain passage i18n, pas d'issue dédiée. |

**Suivi Plane (26 septembre 2026, PO).** Les défauts #1 à #4 sont créés comme work
items `Backlog` rattachés à leur story, avec constat, critère d'acceptation et renvoi
au rapport QA. Ne pas les recréer : un item existant se met à jour par `PATCH`.

| Item Plane | Défaut | Story parente |
|---|---|---|
| `BUG-S9-04-07-01` (#213) | #1 accueil à froid (web) | S9-04 |
| `BUG-S9-04-04-01` (#217) | #1 accueil à froid (Android mobile et TV) | S9-04 |
| `BUG-S9-05-02-01` (#214) | #2 grille web 1440 px | S9-05 |
| `BUG-S9-05-03-01` (#215) | #3 cellules TV en « … » | S9-05 |
| `BUG-S9-05-03-02` (#216) | #4 en-tête de jour TV | S9-05 |
| `BUG-S9-06-03-01` | #6 erreur initiale du Guide TV non peinte (recette S9-06-03) | S9-06 |

Les items `#213/217/214/215/216` sont **corrigés, revus par le Tech Lead et fusionnés dans `main`** (test rouge
d'abord, recettes QA conformes, preuves versionnées sous `docs/releases/0.2.0/qa-evidence/`,
un dossier par passage (`s9-07-passe-initiale-main-2026-09-26/`, `s9-04-07-cold-home-web-2026-09-26/`,
`s9-04-04-cold-home-android-2026-09-26/`, `s9-04-04-217-cold-entries-android-2026-09-26/`,
`s9-05-02-214-grid-web-2026-09-26/`, `s9-05-03-215-216-grid-tv-2026-09-26/`) et
passés `Done` dans Plane. Les 4 branches ont été mergées par Hamza via les PR #1 à #4 ;
`main` = `1d70871`. Rôle : le PO crée les items, le Tech Lead découpe et brieffe @Dev,
le QA recette ; le PO ne briefe pas @Dev directement. Le test qui échoue accompagne le
correctif.

> `BUG-S9-06-03-01` (#6, recette S9-06-03) reste **Todo** : c'est le seul rouge de la
> chaîne S9-06, et le gate de la recette S9-07.
>
> S9-04 est déjà `In Progress` ; S9-05 et S9-06 passent en `Todo` (découpées et prêtes) ;
> S9-07 reste en `Backlog` : la recette dépend de S9-04 à S9-06, et son harnais
> S9-07-03 doit être livré avant toute session de recette.
>
> **Cycle S9 rouvert le 26 septembre 2026 — objectif validé par Hamza :** « Fermer S9 :
> livrer S9-06 puis exécuter S9-07, pour clôturer le Guide sur les trois surfaces
> (`US-16`, complément EPG d'`US-020`). » **S9-07 passe `Backlog` → `Todo`** : son lot
> `S9-07-03` est du **code** (harnais de banc), sélectionnable et livrable maintenant ;
> la recette `S9-07-01`/`02` reste bloquée jusqu'à sa livraison. Le harnais part **en
> parallèle** de S9-06, pas après. S10 (recherche unifiée) n'est pas ouvert ce cycle.
> Décision PO du 26/09 (voir `DECISIONS-PRODUIT.md`).
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
>
> **Revue S9-04-06 — approuvée** le 24 septembre 2026 (branche
> `feat/S9-04-06-web-two-views`, commit `630fe7b`). Sécurité : cookie `httpOnly`,
> `SameSite=Lax`, `secure` selon l'environnement, nom dérivé d'un UUID vérifié,
> valeur validée `channels|guide` (une valeur forgée reste l'une des deux vues et
> rien d'autre) ; ADR : aucune (réutilise la route existante et le patron du cookie
> de source active). Tests rejoués indépendamment par le Tech Lead : `pnpm test` →
> 26 fichiers, 266 tests, 0 échec ; `pnpm typecheck` vert. Une seule `loadEpgWindow`
> par rendu sert les deux vues : le Guide n'ajoute aucune requête. Le `?view=`
> explicite prime la mémoire, une URL S8 sans `?view=` garde Chaînes. **Arbitrage** :
> la liste « En ce moment » web livrée par 06 reste dans `DirectViews.tsx` (un seul
> consommateur) ; S9-04-07 ne porte plus que les entrées d'accueil explicites et la
> prop `more` multi-liens de `ChannelRail`. Non vérifié : rendu SSR réel à travers le
> proxy, clavier web, comportement du cookie sous navigation → recette S9-07.

## Démo et sortie

Preuve du 24 septembre : huit tests EPG, build API complet vert (319 tests) et
24 tests C1 après S9-02. Fixtures relatives à l’horloge et neuf mesures de volume,
plus descriptions longues. S9-00, S9-03 et S9-04 sont en recette ; S9-05 est en cours
(01/02/03 livrées et approuvées), S9-06 est découpée et prête à réaliser, puis viendra la
recette S9-07. Le découpage du sprint 9
est terminé : la réalisation est confiée au Tech Lead, qui briefe @Dev tâche par tâche.

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
