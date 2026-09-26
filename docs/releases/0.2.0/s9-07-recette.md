# Recette S9-07 — Direct et guide, trois surfaces

Protocole de recette de **S9-07-01** et **S9-07-02** (US-16, complément US-020).
Rédigé par QA le 24 septembre 2026, **non exécuté** : S9-04 à S9-06 ne sont pas
livrées et recettées. Ce document ne déclare aucun cas joué et ne vaut pas preuve.

Sources de vérité : le [découpage du sprint 9](../../backlog/sprint-09.md)
(commit `c6ddb85`) et les
[interactions GD-01 à GD-14](../../design/0.2.0/guide-interactions.md). Les
critères d'acceptation sont ceux du cadrage, **pas** les tests du dev : un test
unitaire vert prouve que le code fait ce que le dev voulait.

Ce qui ne peut pas être automatisé est joué à la main, sur appareil réel, avec
télécommande pour la TV. Ce qui peut l'être (fonctions pures de jour et de
fuseau, preuve de requête bornée côté client) doit avoir son test automatique
avant la session : la recette manuelle le **confirme**, elle ne le remplace pas.

---

## 1. Prérequis

### 1.1 La pile

```bash
LUMO_INGEST_ALLOW_PRIVATE_HOSTS=true docker compose --profile bench --env-file apps/api/.env up -d --build --wait
```

| Ce qu'il faut | Où |
|---|---|
| API | `http://localhost:8080` ; depuis un émulateur, `adb reverse tcp:8080 tcp:8080` |
| Site | `pnpm dev` dans `apps/web`, `http://localhost:3000` |
| Banc de sources | `http://localhost:18081` ; depuis un appareil réel, `BENCH_PUBLIC_URL=http://<ip du poste>:18081` avant le `up` |
| Base | `docker exec -it lumo-postgres psql -U lumo -d lumo` (port hôte `15432`) |
| Guide de test | **à instrumenter** — voir §1.3 : le banc ne sert pas encore d'XMLTV |

Deux comptes email créés pour la session (`recette-s9-…@test.example`), un
téléphone Android réel, une TV/box avec télécommande, un navigateur de bureau.
**Aucune source réelle, aucun logo, aucune URL de flux réelle** dans les fixtures
ni les captures. Les programmes sont fictifs (« Programme A1 », « Programme
B1 »…), les chaînes « Chaîne 01 »… conformément au banc existant.

### 1.2 Ce qui doit exister dans la source

La source de banc porte une **adresse EPG** pointant vers le banc
(`http://bench/guide.xml` vu par l'API ; `http://<ip>:18081/guide.xml` vu par le
navigateur pour un contrôle direct). Sans `epg_url`, il n'y a pas de guide et
S9-07 n'est pas jouable. Le dernier import EPG réussi est distinct de la
synchronisation du catalogue : vérifier la ligne « Dernier import du guide », pas
« dernière synchronisation ».

### 1.3 À instrumenter avant toute session — bloquants

Cette liste est le livrable « ce qui reste à instrumenter ». Rien ci-dessous
n'existe aujourd'hui ; chaque ligne nomme le propriétaire pressenti.

| # | Manque | Pourquoi c'est bloquant | Où | Propriétaire |
|---|---|---|---|---|
| I-1 | XMLTV de banc à **dates relatives**, servi par `nginx`, avec `tvg-id` identiques à `playlist.m3u`/`mixed.m3u` | Aucune session avec guide n'est reproductible sans lui ; un fichier committé expirerait | `apps/web/e2e/bench/entrypoint.sh` (génération) + `nginx.conf` (`location = /guide.xml`) | Dev / infra |
| I-2 | Variantes `guide-partial.xml`, `guide-empty.xml`, `guide-broken.xml`, `guide-stale.xml`, `guide-big.xml` | GD-06/GD-10/GD-11 et la preuve de volume | même endroit | Dev / infra |
| I-3 | Horloge contrôlable côté **web SSR** | Le `new Date()` du rendu serveur de `channels/page.tsx` (il alimente `epgDayWindow`, puis le `loadEpgWindow` de la vue Guide) n'est pas atteint par le `page.clock` de Playwright. `loadEpgWindow` a un **second** appelant serveur : `src/lib/home/load-home-rails.ts` (défaut `now = new Date()`), donc patcher la seule page Guide laisserait « En ce moment » sur l'horloge réelle | Helper `apps/web/src/lib/epg/clock.ts` — **pas** `now.ts`, déjà pris par S9-03 (`currentAndNext`/`onAirByChannel`, + `now.test.ts`). Surcharge `LUMO_NOW` (RFC 3339) active seulement si posée, **centralisée** et lue par les **deux** appelants serveur de `loadEpgWindow` : `channels/page.tsx` **et** `src/lib/home/load-home-rails.ts` | Dev |
| I-4 | Compteur d'appels `/sources/{id}/epg` côté **API** pour le web | L'appel EPG du web part du serveur Next (`server-only`), donc invisible à `page.on("request")` | Logger `org.springframework.web.servlet.DispatcherServlet` en DEBUG **en préservant la casse** : la ligne de requête est émise par `DispatcherServlet`, pas `FrameworkServlet` (le champ logger de ce dernier prend la classe d'exécution, donc activer le nom parent n'allume rien). La variable d'env `LOGGING_LEVEL_…_DISPATCHERSERVLET` est relâchée en minuscules par le relaxed binding de Spring Boot et ne cible donc **pas** un logger sensible à la casse. Chemin sûr : `SPRING_APPLICATION_JSON` ou profil yaml avec la clé quotée `logging.level."org.springframework.web.servlet.DispatcherServlet": DEBUG` (le profil livré `application-epg-logging.yml` le fait). Puis `docker logs lumo-api`. **Démontré en live** (image reconstruite, ligne de requête au préfixe `/v1`) | Dev / infra |
| I-5 | Échec EPG à la demande en gardant les données affichées (GD-10 web) | Le web SSR ne peut pas « garder la grille + erreur » sans une panne partielle injectable | proxy/flag dev faisant échouer `/epg` en `503` | Dev / infra |
| I-6 | Playlist 100 chaînes + XMLTV 100 chaînes alignés | Preuve de volume (S9-04-05 / S9-05-02) | `playlist-100.m3u` + `guide-big.xml` | Dev / infra |

Tant que I-1 et I-3 ne sont pas faits, GD-01 à GD-14 restent **non joués**, pas
« verts par déduction ».

---

## 2. Fixtures à identifiants stables

### 2.1 Principes

- **Dates relatives à l'ancre `T`**, l'instant de démarrage de la session. Le
  XMLTV est régénéré à chaque `up`, comme l'`oversized.m3u` du banc. Aucune date
  absolue committée.
- **Identifiants fixes** : `tvg-id="bench.1"` … `bench.4`, plus une chaîne sans
  `tvg-id` (Chaîne 05). Les noms et les groupes sont ceux de `playlist.m3u` :
  « Chaîne 01 FHD » (Généralistes), « Chaîne 02 » (Généralistes), « Chaîne 03 HD »
  (Sport), « Chaîne 04 4K » (Sport), « Chaîne 05 ».
- **Durations utiles** : 15, 30, 45 et 120 min. **Aucun logo, aucune description
  réelle** ; une description absente et une description de 8 192 caractères dans
  le jeu « volume ».
- Le calendrier est celui de `EpgBenchFixtures` (S9-00) : J−1 → J+3, généré autour
  de `T`. **Réutiliser son calendrier, pas ses identifiants** : `EpgBenchFixtures`
  génère des `tvg-id="epg-bench-N"` qui ne rattachent aucune chaîne du banc ; les
  ids XMLTV du banc sont ceux de `playlist.m3u` (`bench.1`…`bench.4`, Chaîne 05
  sans id).

### 2.2 Jeu A — grille canonique (GD-04/05/06)

Ancre `T` = `20:00` locale. Reprend l'exemple du cadrage.

| Chaîne | `tvg-id` | Programme | Plage |
|---|---|---|---|
| Chaîne 01 FHD | `bench.1` | A1 | `T` → `T+45` |
| " | " | A2 | `T+45` → `T+90` |
| Chaîne 02 | `bench.2` | B1 | `T+15` → `T+30` |
| " | " | B2 | `T+30` → `T+90` |
| Chaîne 03 HD | `bench.3` | C1 | `T` → `T+60` |
| Chaîne 04 4K | `bench.4` | D1 | `T+2h` → `T+3h` (lacune à `T+25 min`) |
| Chaîne 05 | *(aucun)* | — | pas de guide |

Résultat attendu de GD-04 sur ce jeu : référence `20:25`, A1 (20:00–20:45) →
`Bas` → B1 (20:15–20:30, couvre 20:25) → `Bas` → C1 (20:00–21:00, couvre 20:25)
→ `Haut` → B1 → `Haut` → A1. Aucune dérive vers 20:00.

### 2.3 Jeu B — changement de programme (GD-07/08)

Programme `E1` `T−30 min` → `T+2 min` (fin imminente) et `E2` `T+3 min` →
`T+33 min` sur `bench.1`. La fiche d'`E1` est ouverte avant la fin ; le passage
d'`E2` à l'état courant se fait par horloge contrôlée (§3), jamais en attendant
réellement, sauf contrôle manuel explicitement noté.

**Fichier : `guide-transition.xml`** — le jeu A est `guide.xml` (§2.2). Les deux
jeux posent un programme sur `bench.1` à `T` (A1 `T→T+45` d'un côté ; E1/E2
`T−30→T+2` puis `T+3→T+33` de l'autre), et un XMLTV ne peut pas porter deux
programmes simultanés sur une chaîne : ils sont donc servis par **deux fichiers**,
et la session change l'`epg_url` de la source entre les deux. Détail du harnais :
`apps/web/e2e/bench/README.md`.

### 2.4 Jeu C — états (GD-10/11, S9-06-03)

- `guide-partial.xml` : `bench.3` absente du XMLTV → lacune, pas d'invention.
- `guide-empty.xml` : aucune `<programme>` → « Aucun programme disponible sur ce
  créneau », sans cause affirmée.
- `guide-broken.xml` : XML tronqué → l'import échoue, `epg_attempt_status=FAILED`.
- Guide ancien : importer `guide.xml`, puis reculer la date d'ingestion côté base
  pour dépasser 24 h :
  `update source set epg_last_success_at = now() - interval '25 hours' where id = '<uuid>';`
  (le seuil de fraîcheur est celui de C1 §8 : > 24 h = ancien, à 24 h exactes non).
- Chaîne sans `tvg_id` (`bench.5`) et source sans guide : **rien** affiché, la
  chaîne reste lisible depuis Chaînes.

### 2.5 Jeu D — volume (preuve réseau)

`playlist-100.m3u` + `guide-big.xml` : 100 chaînes `tvg-id="bench.001"`…`bench.100`,
programmes de 30 min, J−1 → J+3. Variante descriptions de 8 192 caractères pour
franchir les 4 Mio. Le jeu doit permettre de comparer 3, 50 et 100 chaînes à
fenêtre égale.

---

## 3. Horloge contrôlable

| Surface | Méthode | Ce qu'elle ne couvre pas |
|---|---|---|
| Web (client) | Playwright `page.clock.install({ time })` + `fastForward` | Ne change pas le rendu serveur |
| Web (SSR) | Surcharge `LUMO_NOW` (I-3), puis `docker compose restart` du web | À implémenter |
| Android | `Clock` déjà injecté (`EpgModule.clock()`) ; sur appareil réel, régler manuellement Date et heure (désactiver l'automatique) | Régler l'horloge TV/box est parfois verrouillé ; **remettre à l'heure après** |
| Android (automatisé) | Test instrumenté avec un `Clock` fixé, sans toucher l'appareil | Demande un harnais |

Une fois la surcharge en place, la session se joue à `LUMO_NOW`/horloge réglée sur
`T`, puis avance par paliers (`T+1 min`, `T+45 min`, `T+2 h`, et autour de minuit /
changement d'heure). Le protocole **n'attend jamais** une transition en temps réel
sauf mention explicite.

---

## 4. Cas GD-01 → GD-14

Chaque cas indique la surface, le jeu, le pas-à-pas et le résultat attendu.
Statut initial : **non joué**.

### GD-01 · Chaînes → Guide conserve recherche et filtre · S9-04
**Surfaces** : mobile, TV, web.
1. Direct, vue Chaînes. Choisir le filtre Favoris (ou une catégorie), saisir
   « aîne 0 » dans la recherche ; vérifier que le résultat reste filtré.
2. Basculer sur Guide (bouton, pas un lien d'accueil).
3. **Attendu** : texte et filtre conservés ; la position est **Maintenant** ; la
   recherche n'a pas été effacée par la bascule.

### GD-02 · Quitter puis rouvrir · S9-04
**Surfaces** : mobile, TV, web.
1. Se placer sur Guide, filtre Sport ; quitter complètement l'application (web :
   fermer l'onglet).
2. Rouvrir la **même** source, même appareil.
3. **Attendu** : la vue Guide est retrouvée ; recherche vide, filtre Toutes.
   Depuis l'accueil, *Toutes les chaînes* → Chaînes et *Guide TV* → Guide **priment**
   sur la mémoire.
4. Changer de source pendant une requête (GD-03) : ancienne réponse ignorée.

### GD-03 · Changement de source pendant une requête · S9-04
**Surfaces** : mobile, TV, web.
1. Sur une source, lancer une lecture EPG lente (jeu D, 100 chaînes).
2. Changer de source immédiatement.
3. **Attendu** : aucune ligne, aucun focus de l'ancienne source ne s'affiche ;
   recherche et filtre sont effacés sur la nouvelle ; la vue mémorisée de la
   nouvelle source s'applique, Maintenant si Guide.

### GD-04 · Haut/Bas entre durées différentes · S9-05
**Surface** : TV (D-pad réel). **Jeu** : A.
1. Entrer sur Maintenant ; vérifier la sélection de A1 sur `bench.1`, référence 20:25.
2. `Bas`, `Bas`, puis `Haut`, `Haut`.
3. **Attendu** : B1, puis C1 (chaque fois la case couvrant 20:25 malgré les durées
   différentes) ; le retour est réversible et revient à A1, **sans dérive**.

### GD-05 · Droite puis Bas · S9-05
**Surface** : TV. **Jeu** : A.
1. Sur `bench.1`, A1 (20:00–20:45), `Droite` → A2 (20:45–21:30), référence 20:45.
2. `Bas` → la case de `bench.2` couvrant 20:45, soit B2 (20:30–21:00).
3. **Attendu** : référence au **début de la nouvelle case** après `Droite` ; la
   bonne case voisine est sélectionnée.

### GD-06 · Lacune ou bord de grille · S9-05
**Surface** : TV. **Jeux** : A (`bench.4` en lacune), C (guide partiel).
1. Depuis `bench.3`, `Bas` vers `bench.4` à `T+25 min` (lacune).
2. Continuer `Bas` jusqu'à la dernière chaîne, puis `Haut` jusqu'à la première.
3. `Gauche`/`Droite` aux limites de la fenêtre.
4. **Attendu** : la lacune affiche une case neutre **sans action de lecture** et
   **ne fait pas sauter** la chaîne ; aucune boucle aux bords ; *Voir les chaînes*
   reste atteignable ; le focus n'atteint jamais un élément masqué ou un squelette.

### GD-07 · Fin du programme, fiche ouverte, bouton focalisé · S9-06
**Surface** : TV/mobile/web. **Jeu** : B, horloge contrôlée.
1. Ouvrir la fiche d'E1 (`T+2 min`), focaliser *Regarder en direct*.
2. Avancer l'horloge au-delà de la fin d'E1.
3. **Attendu** : l'action disparaît, le focus rejoint **Fermer**, la fiche reste
   ouverte et **le titre ne change pas** (pas de bascule silencieuse vers E2).

### GD-08 · Programme futur devenu courant · S9-06
**Surface** : TV/mobile/web. **Jeu** : B.
1. Ouvrir la fiche d'E2 (futur), le focus ailleurs que sur l'action.
2. Avancer l'horloge à l'intérieur d'E2.
3. **Attendu** : E2 gagne *Regarder en direct* **sans voler le focus** ; l'état
   temporel est revérifié au moment d'activer l'action (re-basculer l'horloge avant
   l'activation doit changer le résultat).

### GD-09 · Retour lecteur après mise à jour du guide · S9-05/06
**Surface** : TV/mobile.
1. Guide, noter recherche, filtre, créneau et ancre de focus.
2. Lancer le direct d'une chaîne, puis revenir.
3. Modifier le guide pendant la lecture (retirer la case d'origine côté fixture),
   revenir : **attendu** repli même chaîne/même heure, sinon suivante, sinon
   précédente, sinon contrôle de filtre si la liste est vide.
4. **Attendu** : recherche, filtre, créneau et ancre restaurés dans le cas normal.

### GD-10 · Erreur initiale, puis erreur avec données · S9-06
**Surface** : Android en priorité (cache d'abord, puis échec réseau).
1. Guide jamais chargé, API injoignable → **attendu** : erreur initiale,
   *Réessayer* et *Voir les chaînes*, **pas** « aucun programme ».
2. Guide chargé puis API en échec (I-4/I-5) → **attendu** : la grille et son focus
   restent, message d'erreur **distinct** ; aucune réponse d'une autre source /
   journée / recherche ne se réinjecte.
3. **Web** : couvert seulement quand I-5 existe ; sinon noté non joué.

### GD-11 · Guide partiel, logo et description absents · S9-00/06
**Surfaces** : toutes. **Jeux** : C, guide ancien.
1. Charger `guide-partial.xml` (`bench.3` absente), une chaîne sans `tvg_id`, une
   source sans guide.
2. Charger un guide ancien (> 24 h).
3. **Attendu** : noms lisibles sans logo, emplacement neutre ; aucun logo
   fictivement attribué ; liste vide → message neutre sans cause inventée ; guide
   ancien → date de mise à jour affichée, lecture depuis **Chaînes** conservée.

### GD-12 · Minuit et changement d'heure · S9-00/05
**Surfaces** : web (SSR), TV, mobile. **Fonctions pures** : S9-05-01.
1. Jouer autour de minuit (`T` ≈ 23:50, passage à J+1) et au changement d'heure
   (dernier dimanche d'octobre 2026, `Europe/Paris`).
2. **Attendu** : horaires et sélection basés sur des **instants**, pas sur des
   heures locales ; deux heures locales identiques à la fin du changement d'heure
   désignent deux instants distincts ; une journée sans données ne promet rien.
3. **Écart connu** : le web épingle `Europe/Paris` (constante `timeZone` de
   `src/i18n/request.ts`) ; le
   « fuseau de l'appareil » n'est vrai qu'Android pour 0.2.0. À jouer et à noter
   tel quel, sans le maquiller.

### GD-13 · Mobile : fiche → journée → En ce moment · S9-05/06
**Surface** : mobile.
1. Liste En ce moment, descendre à une position, ouvrir la journée d'une chaîne,
   ouvrir une fiche, puis Retour, Retour, Retour.
2. **Attendu** : Retour ferme d'abord la fiche, puis remonte de la journée à En ce
   moment ; la position de liste est **conservée**.

### GD-14 · FR/EN, clavier web, D-pad réel · S9-07-02
**Surfaces** : web, TV. Voir l'annexe §6 pour le détail.

---

## 5. Preuve réseau du volume borné

Règle : **une requête groupée par écran ou par page, jamais une par carte**. Deux
preuves, l'automatique d'abord.

### 5.1 Automatique (à exécuter avant la session)

| Preuve | Commande | Assertion |
|---|---|---|
| Client Android, split borné | `./gradlew :core:data:testDebugUnitTest --tests '*EpgRepositoryTest*'` depuis `apps/android` | `server.requestCount` vaut 3 pour 4 chaînes et 15 pour 8, jamais N ; au plus 2 requêtes en vol |
| Contrat serveur | `./gradlew test --tests '*GroupedEpgIntegrationTest*'` depuis `apps/api` | bornes, 422 sans programme, isolation par source/compte |
| Web, découpage | `pnpm test` depuis `apps/web` (`src/lib/epg/split.test.ts`, `load-epg-window` / `now` / `freshness`) | le nombre de sous-requêtes ne dépend pas du nombre de cartes |

### 5.2 Écran réel — web

1. Activer le logger `DispatcherServlet` en DEBUG **en préservant la casse** (I-4) :
   la ligne de requête est émise par
   `org.springframework.web.servlet.DispatcherServlet` — **pas** `FrameworkServlet`,
   dont le champ logger prend la classe d'exécution (activer le nom parent n'allume
   rien). La variable d'env
   `LOGGING_LEVEL_ORG_SPRINGFRAMEWORK_WEB_SERVLET_DISPATCHERSERVLET` est relâchée
   en minuscules par Spring Boot et ne vise donc pas le logger sensible à la casse.
   Chemin sûr : `SPRING_APPLICATION_JSON` ou profil yaml avec la clé quotée
   `logging.level."org.springframework.web.servlet.DispatcherServlet": DEBUG` — le
   profil livré `application-epg-logging.yml` le fait déjà. Recréer le conteneur.
2. Vider les logs, charger la vue Guide, puis compter (`getRequestURI()` porte le
   préfixe `/v1`) :
   ```bash
   docker logs lumo-api 2>&1 | grep -cE 'GET "/v1/sources/[0-9a-f-]+/epg'
   ```
3. Rejouer avec 3, 50 puis 100 chaînes (jeu D).
4. **Attendu** : un nombre **petit et indépendant du nombre de cartes** (1 en
   marche normale ; 1+2+4 en cas de 422, borné) ; jamais ≈ N. Recopier les trois
   comptes et le nombre de cartes affichées dans le rapport.

### 5.3 Écran réel — Android

Le build debug journalise déjà en BASIC sous le tag `LumoHttp`
(`NetworkModule.debugLogging`), requête comprise, avec seuls `token`, `code` et
`user_code` cachés.

```bash
adb logcat -c
# charger la vue Guide à l'écran
adb logcat -d -s LumoHttp:D | grep -c 'GET .*\/epg'
```

Répéter pour 3, 50, 100 chaînes. **Attendu** : compte borné et indépendant du
nombre de cartes, et **cache d'abord** — un second affichage de la même fenêtre
ne doit pas multiplier les appels.

---

## 6. Annexe S9-07-02 — FR/EN, clavier, D-pad, fuseau

### 6.1 FR/EN

Chaque cas GD se rejoue en **français et en anglais**. Vérifier : boutons nommés
(*Maintenant* / *Now* — `directNowButton` ; *Réessayer* / *Try again* —
`sourceRetry`, **pas** « Retry » ; *Voir les chaînes* / *See channels* —
`sourceOpenCatalogue`), aucun français résiduel dans l'interface anglaise, formats
d'heure conformes à la locale, aucun texte essentiel tronqué à 320 px de large.
*Regarder en direct* / *Watch live* **n'existe pas encore** dans
`src/messages/{fr,en}.json` : S9-06 doit créer la clé, et cette annexe s'alignera
sur le libellé livré. Le créneau vide d'une grille chargée utilise
`directGuideEmptySlot` (*Aucun programme disponible sur ce créneau* / *No
programme available in this slot*), posé par S9-05-02.

### 6.2 Clavier web

Sans souris : `Tab`/`Maj+Tab` atteignent recherche, filtres, cartes, fiche ;
`Entrée`/`Espace` activent ; `Échap` ferme la fiche ; le focus reste **visible**
et **piégé** dans la fiche tant qu'elle est ouverte, puis revient à la case.
Glisser la grille horizontale au clavier quand un défilement est nécessaire.

### 6.3 D-pad réel

TV/box physique, télécommande en main : GD-04/05/06/07/08/09 plus les parcours
accueil → Direct → Guide → fiche → lecture → retour. Vérifier chaque fois qu'une
croix n'atterrit pas sur un élément masqué, qu'un cul-de-sac propose une sortie, et
que le focus revient à la case lancée après lecture.

### 6.4 Fuseau et changement d'heure

Voir GD-12. Tester un appareil réglé sur un fuseau **différent** de celui du
serveur (ex. `America/New_York`) : les horaires doivent rester ceux de l'appareil,
les comparaisons sur des instants. Consigner l'écart web connu (§GD-12.3) comme
observation, pas comme échec de S9-07, car il est déjà inscrit au reste de S9-03.

---

## 7. Critère de sortie

La recette est verte quand **chaque** cas GD-01→GD-14 est joué sur chaque surface
applicable, en FR **et** EN, avec télécommande réelle pour la TV, et que :

- la preuve réseau montre un nombre d'appels **indépendant du nombre de cartes**
  (§5) pour web et Android ;
- la démonstration GD-07/GD-08 est faite sur fiche ouverte, horloge contrôlée ;
- aucun cas n'est « vu sur émulateur » tenu pour vert.

Le rapport de session note, par cas : **vert**, **rouge** (étapes de reproduction +
test qui échoue) ou **non joué avec la raison**. Un bug hors story devient une
issue de type Bug, avec label de plateforme, étapes et test ; le PO décide du cycle.
Tant que I-1, I-3, I-4 et I-5 ne sont pas livrés, la session se limite aux cas qui
ne les exigent pas, et les autres restent **non joués** — jamais « verts ».

---

## 8. Traçabilité

| Sous-issue Plane | Contenu | Livrable | État |
|---|---|---|---|
| S9-07-01 | Protocole GD-01→GD-14, horloge, fixtures, preuve réseau, changement de programme | ce document | protocole écrit, recette non jouée |
| S9-07-02 | Annexe FR/EN, clavier, D-pad, fuseau | §6 de ce document | idem |

Instrumentation ouverte : **I-1 à I-6** (§1.3). Aucune écriture Plane n'a été
faite pour ce protocole : l'état de S9-07 reste à confirmer par le PO/Tech Lead.
