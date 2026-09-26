# Plan de test S9-06 — fiche programme et états du Guide

Plan de test de la story **S9-06** (US-16, complément US-020), rédigé par QA le
26 septembre 2026 à l'ouverture de la réalisation. **Plan non exécuté** : ce
document ne déclare aucun cas joué et ne vaut pas preuve.

Sources de vérité : le [découpage du sprint 9](../../backlog/sprint-09.md)
(section S9-06) et les
[interactions GD-01 à GD-14](../../design/0.2.0/guide-interactions.md), plus la
[fiche de programme](../../design/0.2.0/direct-guide.md) du cadrage.

Règle : **les critères d'acceptation sont ceux du cadrage, pas les tests du
dev**. Un test unitaire vert prouve que le code fait ce que le dev voulait, pas
ce que le PO voulait. Chaque critère ci-dessous a son cas ; les cas limites non
listés par le PO sont en §4.

## 0. Découpage couvert

| Lot | Plateforme | Cas GD | Fichiers livrés |
|---|---|---|---|
| S9-06-01 | Android TV + mobile | GD-07, GD-08 | `feature/live/…/ProgrammeSheet.kt` (nouveau), `LiveViewModel.kt`, `LiveTvScreen.kt`, `LiveMobileScreen.kt`, `GuideGridTv.kt`, `GuideDayMobile.kt` |
| S9-06-02 | Android | GD-09, GD-13 | `GuideFocus.kt`, `LiveViewModel.kt` |
| S9-06-03 | Android | GD-10, GD-11 | `GuideStates.kt` (nouveau), `LiveViewModel.kt` (`EpgFreshness` de S9-03) |
| S9-06-04 | Web | GD-07, GD-08, GD-10, GD-11 (web) | `src/components/app/ProgrammeSheet.tsx` (nouveau), page `sources/[id]/channels`, `src/messages/{fr,en}.json` |

## 1. Ce qui est testable tout de suite, et ce qui ne l'est pas

- **Android pur (`momentOf`, `watchAvailable`, `watchAllowed`, `sheetFocusAfter`,
  restitution GD-09/13, états GD-10/11)** : testable de façon déterministe dès
  maintenant — `LiveViewModel` injecte déjà `java.time.Clock`. Prérequis inutile.
- **Android instrumenté (panneaux Compose, D-pad, focus réel)** : testable dès
  maintenant sur émulateur TV 1080p (`Television_1080p`) et mobile (`Pixel_10`),
  sans harnais EPG (le guide peut être servi par le banc de dev).
- **Web temps-dépendant (GD-07/08 côté SSR : « futur devenu courant », créneau
  « courant » calculé au rendu)** : **bloqué par S9-07-03 I-3** (`apps/web/src/lib/epg/clock.ts`
  + `LUMO_NOW` lu par les **deux** appelants serveur de `loadEpgWindow` :
  `channels/page.tsx` **et** `src/lib/home/load-home-rails.ts`). Tant qu'I-3 n'est
  pas livré, tout verdict web sur le temps serait « vert par déduction ».
- **GD-10/11 (guide partiel, variantes cassées/anciennes/vides)** : bloqué par
  S9-07-03 **I-1/I-2** (XMLTV de banc à dates relatives + variantes) et **I-5**
  (panne partielle injectable) pour l'erreur et l'ancienneté.

## 2. Cas des critères d'acceptation (S9-06-01, GD-07/08)

| # | Critère | Précondition | Pas-à-pas | Attendu | Type | Statut |
|---|---|---|---|---|---|---|
| QA-06-01-01 | Fiche : programme **en cours** | Guide ouvert sur un programme courant | Sélectionner la case (TV) / l'élément de la journée (mobile) | Panneau latéral (TV) / bas (mobile) ; titre, horaires, description si présente ; bouton **Regarder en direct** visible | instrumenté + manuel | à jouer |
| QA-06-01-02 | Fiche : programme **à venir** | Case future | Sélectionner la case | Mêmes informations, **aucun** bouton de lecture ; Fermer présent | instrumenté | à jouer |
| QA-06-01-03 | Fiche : programme **passé** | Case passée | Sélectionner la case | Informations seules, aucun replay, aucun bouton de lecture | instrumenté | à jouer |
| QA-06-01-04 | **GD-07** fin du programme, focus sur l'action | Programme courant, fiche ouverte, focus sur **Regarder en direct** | Laisser passer `endsAt` (horloge contrôlable ou attente courte sur programme de test 1 min) | L'action **disparaît**, le focus rejoint **Fermer**, la fiche reste ouverte, le **titre ne change pas** | pur (moment) + instrumenté (focus) | partiel — voir §5 défaut D1 |
| QA-06-01-05 | **GD-07** fin du programme, focus ailleurs | Fiche ouverte, focus sur Fermer | Laisser passer `endsAt` | Focus **inchangé** ; fiche ouverte | pur + instrumenté | partiel |
| QA-06-01-06 | **GD-08** futur devenu courant | Fiche ouverte sur un programme futur | Attendre `startsAt` | L'action **apparaît sans voler le focus** (le focus reste où il était) | pur + instrumenté | à jouer |
| QA-06-01-07 | **GD-08** état revérifié à l'activation | Fiche ouverte, programme courant | Attendre la fin **sans que le panneau recompose**, puis activer l'action | L'activation est **refusée** (`watchAllowed` relit `Instant.now()`), aucune lecture lancée | pur | à jouer |
| QA-06-01-08 | Fermer / Retour | Fiche ouverte | Activer **Fermer** | La fiche se ferme, rien ne change sous elle (la restitution du focus = S9-06-02) | instrumenté | à jouer |
| QA-06-01-09 | Fermer / Retour | Fiche ouverte | Appui **Back** (touche Retour TV, bouton système mobile) | La fiche se ferme **d'abord** ; le niveau Guide reste | instrumenté | à jouer |
| QA-06-01-10 | Fermer / Retour | Fiche ouverte dans la **journée mobile** | Back deux fois | 1er Back : fiche ; 2e Back : retour de la journée vers la liste **En ce moment** | instrumenté | à jouer |
| QA-06-01-11 | Changement de source | Fiche ouverte | Changer la source active (autre appareil / accueil) | La fiche se ferme ; aucune fiche de l'ancienne source ne survit (GD-03) | pur + instrumenté | à jouer |
| QA-06-01-12 | Vue Chaînes garde la lecture immédiate | Vue **Chaînes** | Sélectionner une chaîne | Lecture immédiate, **pas** de fiche (la fiche n'est que pour le Guide) | manuel | à jouer |
| QA-06-01-13 | Cellule de grille → bonne chaîne lue | Fiche d'une case de la chaîne A, action activée | Activer Regarder en direct | La lecture ouvre **A** (le `channelId`/nom réel, pas l'id de programme du quirk antérieur) | instrumenté | à jouer |

## 3. Cas GD-09 / GD-13 (S9-06-02) et GD-10 / GD-11 (S9-06-03)

| # | Critère | Précondition | Pas-à-pas | Attendu | Type | Statut |
|---|---|---|---|---|---|---|
| QA-06-02-01 | **GD-13** retour par niveau (mobile) | Liste En ce moment, entrer dans la journée d'une chaîne, y défiler | Back : journée → En ce moment | Position de liste **conservée** (ancre), aucune perte de recherche/filtre | instrumenté | à jouer (S9-06-02) |
| QA-06-02-02 | **GD-09** retour lecteur, position conservée | Grille avec créneau/recherche/filtre, lecture lancée puis Retour | Retour lecteur | Recherche, filtre, créneau et **ancre de focus** restaurés ; pas de ré-entrée sur Maintenant | instrumenté | à jouer (S9-06-02) |
| QA-06-02-03 | **GD-09** repli même chaîne/même heure | Case focalisée, puis guide mis à jour et la case supprimée | Retour / mise à jour EPG | Repli déterministe : même chaîne et même heure ; sinon chaîne suivante ; sinon précédente ; sinon contrôle de filtre | pur + instrumenté | à jouer (S9-06-02) |
| QA-06-02-04 | Aucun focus sur case masquée | Mise à jour EPG pendant navigation | Le focus ne tombe jamais sur un squelette ni un élément masqué | instrumenté | à jouer (S9-06-02) |
| QA-06-03-01 | **GD-10** erreur initiale | Aucune donnée EPG, première requête en échec | Charger le Guide | Message d'erreur **distinct** d'un guide vide ; boutons **Réessayer** et **Voir les chaînes** ; aucun « guide vide » annoncé | instrumenté + manuel | à jouer (S9-06-03, dépend I-5) |
| QA-06-03-02 | **GD-10** erreur avec données | Grille déjà affichée, focus posé | Provoquer un échec de rafraîchissement | La grille et le focus **survivent** ; message d'erreur distinct de l'erreur initiale ; Réessayer | instrumenté + manuel | à jouer (S9-06-03, dépend I-5) |
| QA-06-03-03 | **GD-11** guide partiel | Variante `guide-partial.xml` | Ouvrir le Guide | Créneaux sans données → « Aucun programme disponible sur ce créneau » ; aucun texte inventé | manuel | à jouer (dépend I-2) |
| QA-06-03-04 | **GD-11** logo et description absents | Programme sans logo ni description | Ouvrir la fiche / la grille | Nom lisible sans logo (repère neutre, aucun logo fictif) ; description absente = pas de zone vide | instrumenté + manuel | à jouer |
| QA-06-03-05 | Liste vide, cause non inventée | Guide vide ou créneau non couvert | Ouvrir le Guide | Message neutre, **aucune** cause attribuée (panne vs absence indistinguables au contrat) | manuel | à jouer |
| QA-06-03-06 | Guide ancien | Variante `guide-stale.xml` | Ouvrir le Guide | Programmes conservés + date de mise à jour indiquée | manuel | à jouer (dépend I-2) |

## 4. Cas limites non listés par le PO (ajout QA)

| # | Cas | Attendu |
|---|---|---|
| QA-06-L01 | `startsAt == endsAt` (durée nulle) | Jamais marqué courant, jamais de bouton lecture (`momentOf` → Passé par la règle demi-ouverte) |
| QA-06-L02 | Fiche ouverte **à cheval sur minuit** (ex. 23:30–00:15) | Les horaires restent ceux de l'appareil ; la fin à 00:15 fait basculer l'action ; aucune bascule à minuit |
| QA-06-L03 | Pendant un **changement d'heure** (nuit DST) | Le créneau affiché et l'action reposent sur des **instants**, pas sur l'heure locale ; pas de double déclenchement ni d'action figée |
| QA-06-L04 | Titre très long | Tronqué proprement (TV : 2 lignes + ellipse ; mobile : 2 lignes) sans casser le panneau |
| QA-06-L05 | `channelName` nul ou vide | Aucune ligne de chaîne vide, pas de trou de mise en page |
| QA-06-L06 | Description longue | 6 lignes max + ellipse, pas de scroll qui pousse les actions hors écran |
| QA-06-L07 | Fiche ouverte pendant qu'un **rafraîchissement EPG** retire le programme du guide | La fiche garde le programme ouvert (valeur capturée) ; Fermer restitue la case ou le repli GD-09, sans crash |
| QA-06-L08 | Ouvrir/fermer la fiche **rapidement** en rafale | Aucun focus fantôme, aucun panneau résiduel, aucune lecture accidentelle |
| QA-06-L09 | Guide **cassé** (`guide-broken.xml`) | Fiche non ouverte ; section Guide en état d'erreur (GD-10), pas d'écran blanc |
| QA-06-L10 | Guide **100 chaînes** | Sélection d'une case et ouverture de fiche restent correctes au-delà du premier écran (pas d'index décalé par le lazy) |
| QA-06-L11 | **D-pad TV** dans le panneau | Haut → action, Bas → Fermer, Gauche/Droite avalées (pas de fuite vers la grille), Back sort ; focus **toujours visible** (GD-14) |
| QA-06-L12 | Activation puis **retour du lecteur** | Comportement à trancher : la fiche est-elle encore ouverte au retour ? (le code actuel ne la ferme pas à l'activation — voir §5 D2) |
| QA-06-L13 | **FR/EN** | Libellés « Regarder en direct » / « Fermer » traduits, jamais de clé brute affichée |
| QA-06-L14 | Deux appareils, même compte | La fiche est un état d'UI local : un changement de source piloté depuis l'autre appareil ferme la fiche sans laisser l'ancienne source |
| QA-06-L15 | Mort de process / rotation mobile | Rotation : fiche conservée (ViewModel). Mort de process : perte acceptée, mais aucun crash au retour |

## 5. Ouvertures à trancher (relevées sur `feat/S9-06-01-programme-sheet` @ `a9465ff`)

Ces points ne sont pas des verdicts : ils sont à confirmer par @Dev / @Tech Lead
avant la recette, puis transformés en cas ci-dessus si besoin.

- **D1 — la règle de focus GD-07 n'est pas prouvée par le test.** La fonction
  pure `sheetFocusAfter(...)` est **définie et testée mais jamais appelée** par
  `ProgrammeSheetTv` : le panneau réimplémente la règle en ligne
  (`if (!watchAvailable(moment) && watchFocused) … closeRequester.requestFocus()`).
  Le test vert porte donc sur un miroir, pas sur le code qui s'exécute. La
  règle « le focus rejoint Fermer » demande un test **instrumenté** du panneau,
  pas seulement `ProgrammeSheetTest`.
- **D2 — l'activation ne ferme pas la fiche.** `onWatch` appelle `onPlay(...)`
  sans `onProgrammeClosed()` (TV et mobile). Au retour du lecteur, la fiche est
  donc encore ouverte. À trancher au regard de GD-09 (« retour lecteur restaure
  le créneau consulté ») : fiche rouverte = continuité voulue, ou défaut ?
- **D3 — arrivée non déterministe.** L'effet d'arrivée du panneau TV lit
  `Instant.now()` en dur (`LaunchedEffect(sheet.programme.id)`), alors que le
  reste du panneau reçoit `now` en paramètre. Avec une horloge surchargée, la
  pose initiale du focus ne sera pas reproductible ; à aligner sur l'horloge
  injectée si l'on veut instrumenter ce cas.
- **D4 — le relais de focus à la disparition peut être annulé.** `watchFocused`
  est mis à jour par le `onFocusChanged` du bouton ; quand le bouton quitte la
  composition à `endsAt`, la remise à `false` peut courir contre la
  `LaunchedEffect` qui veut rendre le focus à Fermer. Cas QA-06-01-04 à jouer en
  instrumenté pour lever le doute.

## 6. Preuve attendue à la fin

- Un cas par critère GD + les cas limites, chacun avec son verdict
  ✅ / ❌ (étapes de reproduction) / ⚠️.
- Les tests **automatisés** correspondants ajoutés par QA (instrumentés sur
  émulateur TV/mobile + web/e2e une fois I-3/I-1/I-2/I-5 livrés), exécutés dans
  la pile de recette (Docker Compose côté web), pas seulement les unitaires du dev.
- Le rapport de recette S9-07 (§GD) reste le document final ; ce plan en est la
  partie S9-06.
