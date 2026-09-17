# Backlog — Sprint 7

> **Planification 0.2.0 — 17 septembre 2026 :** ce chantier EPG est repris dans
> [le sprint 9](sprint-09.md), avec les interactions validées et la grille horaire TV.
> Le présent document conserve son périmètre et son estimation historiques ; ne pas
> compter ses tâches en plus de S9. Les 34 points ci-dessous ne chiffrent pas toute
> la cible 0.2.0. Voir le [plan de livraison](../roadmap/0.2.0/delivery-plan.md).

## Le guide des programmes

> Un utilisateur regarde une chaîne et sait ce qui passe, ce qui suit, et ce qu'il
> aurait pu regarder à la place.

**C'est le sprint le moins cher qui reste, et pas parce qu'il est petit : parce que
la moitié est déjà écrite depuis le sprint 1 et n'a jamais été appelée.**

---

## Trois constats qui commandent l'ordre des tâches

### Le serveur fait déjà tout, et personne ne lui parle

Vérifié dans le dépôt plutôt que supposé :

| Ce qui existe | Depuis | État |
|---|---|---|
| `XmltvStreamParser` | sprint 1 | Lit un XMLTV en flux, sans le charger en mémoire |
| Table `epg_programme` | `0006-epg.sql` | Avec sa contrainte `ends_at > starts_at` |
| `IngestionService.ingestEpg` | sprint 1 | Étape `FETCHING_EPG`, et **un guide qui ne charge pas ne fait pas échouer la source** |
| `GET /channels/{id}/epg` | contrat | Avec `from`/`to`, défaut « maintenant » → +24 h, plafond 4 jours |
| Purge de rétention | `housekeeping()` | Fenêtre glissante D-1 → D+3, toutes les 15 minutes |

Et le résultat de la recherche qui compte :

```
$ grep -rn "getChannelEpg" apps/android/ apps/web/src
(aucun résultat)
```

**Aucun des trois clients n'appelle cette opération.** Ce sprint ne construit pas
une fonction, il branche une fonction construite il y a six sprints. C'est ce qui
le rend bon marché, et c'est aussi ce qui doit rendre méfiant : du code jamais
appelé n'est pas du code qui marche, c'est du code qui compile. La première tâche
est donc une tâche de serveur, et elle est là pour **découvrir** plutôt que pour
construire.

### La télévision et le web n'ont pas le même besoin, et le contrat n'en sert qu'un

C'est le seul vrai arbitrage du sprint et il ne se découvre pas en cours de route.

- **« En ce moment » et « Ensuite »**, sur la télévision et le téléphone, c'est
  **une chaîne à la fois**. `GET /channels/{id}/epg?from=now&to=now+3h` répond, et
  [`api-gaps.md`](../design/api-gaps.md) le dit déjà en toutes lettres.
- **Une grille horaire**, sur le web, c'est **cinquante chaînes × trois heures sur
  un écran**. Avec l'opération actuelle, c'est cinquante requêtes pour une page.

**Personne n'a jamais envisagé le second cas.** `api-gaps.md` ne mentionne l'EPG
qu'une fois, pour la ligne « En ce moment / Ensuite : » de la télévision. La grille
est la moitié qui n'a pas été pensée, et elle a besoin d'une opération que le
contrat n'a pas.

> **C'est exactement le cas qu'`AGENTS.md` §9 décrit** : un besoin non couvert par
> le contrat → on s'arrête et on tranche, dans une tâche, avant d'écrire un écran.
> Ne pas le faire donnerait une grille qui « marche » en développement sur cinq
> chaînes et martèle le serveur à cinquante.

### Le plafond est le même que celui qu'on a déjà payé deux fois

Une opération groupée porte un plafond, et ce projet a une histoire avec les
plafonds :

- `S4-02` : `?ids=` plafonné à cent, et `size` par défaut à **50** — un lot de cent
  revenait à moitié répondu, avec un `200` et aucune erreur ;
- `S6-09` : le même piège au même endroit, sur le web cette fois, et la correction
  a été un **module avec ses tests** plutôt qu'une coupe défensive.

La troisième fois n'a pas besoin d'être une découverte. `S7-01` écrit le plafond
dans le contrat **et** le comportement au-delà, et `S7-06` réutilise le résolveur
par lots de `S6-09` au lieu d'en écrire un second.

---

## Ce qui existe déjà, et qu'on ne réécrit pas

| Module | Ce qu'il fournit |
|---|---|
| `apps/api` `ingest/xmltv` | `XmltvStreamParser`, en flux, avec ses tests |
| `apps/api` `catalog` | `epg_programme`, la purge, et `GET /channels/{id}/epg` |
| `core:database` | `ChannelEntity.tvgId` est **déjà là** — la migration Room ne touche pas aux chaînes |
| `apps/web` `lib/catalogue` | `resolveChannels`, le découpage par lots de `S6-09`, et ses huit tests |
| Banc d'essai | Un panel Xtream qui répond, et une source M3U avec `epgUrl` |

**Ce que le banc n'a pas :** aucun XMLTV. C'est la seule fixture à ajouter, et la
sixième règle de [`dette.md`](./dette.md) veut qu'elle soit dans le chiffrage de la
tâche qui en a besoin — c'est `S7-00`.

---

## Definition of Ready

Celle du sprint 1, plus deux conditions propres à ce sprint :

- la tâche dit ce qu'elle affiche **quand il n'y a pas de guide**. Une chaîne sans
  `tvg_id`, une source sans `epgUrl`, un XMLTV qui n'a pas chargé : trois absences,
  et le contrat renvoie une liste vide pour les trois. Un écran qui les confond
  dira « aucun programme » à quelqu'un dont le fournisseur n'en fournit jamais ;
- la tâche dit ce qu'elle affiche **quand le guide est en retard**. La rétention va
  de D-1 à D+3 et la synchronisation est horaire au mieux : un guide vieux de deux
  jours est le cas normal, pas le cas dégradé.

## Definition of Done

Celle du sprint 1, sans allègement, plus :

- **la démo montre une chaîne dont le programme change pendant la démo**, ou dit
  pourquoi elle ne peut pas. « En ce moment » qui n'avance jamais est
  indiscernable d'une valeur figée ;
- **la grille web se charge en une requête**, montrée dans l'onglet réseau. C'est
  la seule preuve que `S7-01` a servi à quelque chose ;
- télécommande en main pour la partie TV.

---

## Une story ajoutée — **US-16, retenue**

### US-16 — Savoir ce qui passe

> **En tant qu'** utilisateur, **je veux** voir ce qui passe et ce qui suit sur mes
> chaînes, **afin de** choisir quoi regarder sans zapper à l'aveugle.

```gherkin
Scenario: En ce moment, sur la chaîne que je regarde
  Given je regarde une chaîne dont la source a un guide
  When j'ouvre la barre d'information
  Then je vois le titre du programme en cours et son heure de fin
  And je vois le titre du suivant

Scenario: Une chaîne sans guide ne ment pas
  Given je regarde une chaîne sans `tvg_id`
  When j'ouvre la barre d'information
  Then rien n'est affiché à la place du programme
  And aucune phrase ne suggère que le guide arrive

Scenario: La grille du navigateur
  Given ma source a un guide
  When j'ouvre la grille des programmes
  Then je vois mes chaînes en lignes et les heures en colonnes
  And le programme en cours est marqué
  And la page se charge sans une requête par chaîne

Scenario: Un guide en retard le dit
  Given le guide de ma source date de plus de vingt-quatre heures
  When j'ouvre la grille
  Then l'âge du guide est affiché
  And ce n'est pas une erreur
```

**Notes** — `GET /channels/{id}/epg` existe depuis le sprint 1 et n'a jamais été
appelé. La rétention D-1 → D+3 est déjà en place et purgée toutes les quinze
minutes ; aucune tâche de ce sprint n'y touche.

---

## Tâches

| | ID | Tâche | Bloc | App | Pts | Avancement |
|---|---|---|---|---|---|---|
| ☐ | S7-00 | Banc : un XMLTV, et l'ingestion qu'on regarde vraiment tourner | banc | banc + api | 3 | 0 % |
| ☐ | S7-01 | Contrat : le guide de plusieurs chaînes en une requête | contrat | contrat + api | 5 | 0 % |
| ☐ | S7-02 | `core:data` et Room : le guide hors ligne, et son âge | socle | android | 5 | 0 % |
| ☐ | S7-03 | TV : « En ce moment » et « Ensuite » dans la barre du lecteur | tv | tv | 5 | 0 % |
| ☐ | S7-04 | TV : le programme en cours sous chaque carte de la grille | tv | tv | 3 | 0 % |
| ☐ | S7-05 | Mobile : le guide d'une chaîne, et la liste qui le porte | mobile | mobile | 5 | 0 % |
| ☐ | S7-06 | Web : la grille horaire, en une requête | web | web | 8 | 0 % |

**Total : 34 points.** C'est le sprint le plus court depuis le sprint 4, et l'écart
avec les deux précédents tient entièrement au fait que le serveur est déjà écrit.

**Avancement du sprint : 0 % de 34 points.**

**L'ordre n'est pas négociable sur les deux premières.** `S7-00` sert à découvrir
ce que six sprints de code jamais appelé cachent ; `S7-01` dépend de ce qu'elle
trouve. Écrire le contrat avant d'avoir vu une ingestion XMLTV réussir serait
écrire une opération sur une supposition.

---

### S7-00 — Banc : un XMLTV, et l'ingestion qu'on regarde vraiment tourner · **3**

**Ce n'est pas une tâche de fixture, c'est la tâche qui vérifie une hypothèse.**

Le banc n'a aucun XMLTV, donc `ingestEpg` n'a jamais tourné contre lui, donc
personne n'a jamais vu une ligne entrer dans `epg_programme` autrement qu'en test
unitaire de parseur. Six sprints de code qui compile.

**Ce qu'il faut ajouter :**

- `/epg.xml` — un XMLTV inventé couvrant les `tvg-id` de `/playlist.m3u` et les
  chaînes du panel Xtream, sur **quatre jours autour de maintenant**, donc généré
  au démarrage comme la charge surdimensionnée l'est déjà : un fichier commité avec
  des dates fixes serait périmé le lendemain ;
- `/epg-gzip.xml.gz`, parce que la moitié des fournisseurs servent le guide
  compressé et que `SizeCappedInputStream` compte les octets **lus**, pas ceux
  reçus ;
- `/epg-broken.xml`, un XML mal formé — le cas que `ingestEpg` attrape pour ne
  **pas** faire échouer la source, et qui n'a jamais été observé de bout en bout.

**Et un test d'intégration**, dans la foulée de `IngestionServiceIntegrationTest` :
une source avec `epgUrl` finit `READY` **avec** des programmes ; une source dont
l'EPG est cassé finit `READY` **sans**. Le second cas est une décision écrite dans
le code depuis le sprint 1 et jamais vérifiée.

> **Le chiffrage porte la fixture**, conformément à la sixième règle de
> [`dette.md`](./dette.md). C'est la première tâche écrite après cette règle, et
> c'est délibéré.

---

### S7-01 — Contrat : le guide de plusieurs chaînes en une requête · **5** · dépend de S7-00

`GET /sources/{id}/epg?channelIds=…&from=…&to=…`, et la tâche tranche trois choses.

**Le plafond, et ce qui se passe au-delà.** Cent identifiants par requête, comme
`?ids=` partout ailleurs — mais **cette fois le contrat dit explicitement ce que le
serveur fait d'une liste plus longue** : un `400 VALIDATION_FAILED`, pas une
troncature silencieuse. Les deux fois où ce plafond a coûté quelque chose, c'était
parce qu'une réponse partielle arrivait avec un `200`.

**La fenêtre.** Mêmes bornes que l'opération par chaîne — défaut 24 h, plafond
4 jours — parce que deux plafonds différents pour la même donnée sont deux règles à
retenir.

**Le volume, qui est le vrai risque.** Cinquante chaînes × trois heures, c'est
peut-être trois cents programmes ; cinquante chaînes × quatre jours, c'est dix
mille. La tâche mesure sur le banc avant de choisir, et **écrit le chiffre dans le
contrat**.

**Ce que ça ne fait pas :** aucune agrégation, aucun « en ce moment » calculé côté
serveur. Le serveur rend des programmes avec leurs bornes ; « en cours » est une
comparaison à l'horloge, et l'horloge de l'appareil est la bonne — c'est celle que
l'utilisateur regarde.

> **`ADR 0001` s'applique** : une opération ajoutée au contrat est une erreur de
> compilation tant que le contrôleur ne l'implémente pas. Contrat et serveur
> partiront dans le même commit, comme `S6-01` et `S6-02`.

---

### S7-02 — `core:data` et Room : le guide hors ligne, et son âge · **5** · dépend de S7-01

Une table `epg_programme`, une migration Room `6 → 7`, un `EpgRepository`.

**Ce qui est nouveau par rapport aux trois caches précédents** : cette donnée
**périme toute seule**. Une chaîne reste une chaîne ; un programme de 20 h 00 n'a
plus d'intérêt à 21 h 30. Le cache doit donc porter son âge et savoir se purger,
et c'est la seule chose de ce socle qui ne se copie pas de `SeriesRepository`.

**Deux règles, et la seconde est le sujet de la tâche :**

- **ce qui est en cache gagne**, comme partout ailleurs ;
- **l'âge est une valeur du modèle, pas une décision du dépôt.** `EpgWindow` porte
  `fetchedAt`, et c'est l'écran qui décide si « il y a 3 h » vaut la peine d'être
  dit. Un dépôt qui déciderait à sa place produirait trois seuils différents sur
  trois surfaces.

**Une purge locale**, calquée sur celle du serveur : ce qui est plus vieux que D-1
part. Sans elle, un appareil qui regarde la télévision tous les soirs accumule un
guide pour toujours.

---

### S7-03 — TV : « En ce moment » et « Ensuite » dans la barre du lecteur · **5** · dépend de S7-02

**Aucune zone de focus nouvelle**, et c'est ce qui garde la tâche à cinq points. La
barre d'information du lecteur existe (`S2-14`, étendue en `S5-09`) ; elle gagne
deux lignes sous le nom de la chaîne.

**Ce qui s'affiche :** le titre en cours, son heure de fin, et le titre suivant. Pas
de barre de progression du programme — elle demanderait de redessiner à la seconde
une couche qui part au bout de cinq secondes d'inactivité.

**Ce qui ne s'affiche pas, et c'est la moitié de la tâche.** Une chaîne sans
`tvg_id`, une source sans `epgUrl` et un guide qui n'a pas chargé rendent tous les
trois une liste vide. La barre montre alors **le nom de la chaîne, comme avant**, et
rien d'autre : pas de « Programme indisponible », pas d'espace réservé.

> Quelqu'un dont le fournisseur ne donne jamais de guide verrait cette phrase à
> chaque ouverture, pour toujours. Une absence silencieuse est ici la bonne réponse
> — c'est l'inverse de la règle du sprint 5 sur les onglets, et la différence est
> qu'un onglet est une **porte** tandis qu'une ligne de programme est une
> **information** : une porte absente cache une fonction, une information absente
> ne cache rien.

**L'appel se fait à l'ouverture de la chaîne, pas à chaque ouverture de la barre.**
Une fenêtre de trois heures couvre plusieurs pressions sur `OK`.

---

### S7-04 — TV : le programme en cours sous chaque carte · **3** · dépend de S7-02

Une ligne sous le nom, dans la grille des chaînes. `LiveTvScreen` uniquement.

**Le piège est le nombre de requêtes**, et c'est pour lui que `S7-01` existe : une
grille paginée de chaînes qui demanderait le guide carte par carte ferait une
requête par affiche visible, et davantage au défilement. Une fenêtre courte pour
les chaînes de la page courante, en **une** requête.

**Rien sous une carte sans guide**, pour la raison de `S7-03`.

---

### S7-05 — Mobile : le guide d'une chaîne, et la liste qui le porte · **5** · dépend de S7-02

Deux endroits, et le second est le moins évident :

- **la liste des chaînes** gagne la même ligne « en ce moment » que la grille TV ;
- **une chaîne ouverte** gagne son guide de la journée, en liste verticale, avec le
  programme en cours marqué et la possibilité de remonter à hier et descendre à
  J+3.

**Le téléphone est la surface qui a la place de montrer la rétention**, et c'est ce
qui justifie de mettre le guide long ici plutôt que sur la télévision : quatre jours
de programmes au D-pad seraient une liste dont on ne voit jamais la fin.

**L'âge du guide s'affiche dès qu'il dépasse vingt-quatre heures**, et pas avant :
« mis à jour il y a 3 h » sur un guide frais est du bruit.

---

### S7-06 — Web : la grille horaire, en une requête · **8** · dépend de S7-01

**La tâche la plus chère du sprint, et la seule qui construise quelque chose de
neuf.** Chaînes en lignes, heures en colonnes, le programme en cours marqué.

**Les colonnes sont du CSS grid et rien d'autre.** Pas de virtualisation, pas de
calcul de position en JavaScript : une page de cinquante chaînes sur trois heures
est une grille de taille connue, et la zone compte reste rendue côté serveur.

**Le curseur temporel est dans l'URL** — `?at=2026-09-01T20:00` — comme la saison
d'une série et le groupe de favoris. Partageable, correct au bouton retour, et
**fonctionne sans JavaScript** : les flèches « ‹ 3 h » et « 3 h › » sont des liens.

**Une requête, et c'est vérifiable.** Les identifiants des chaînes de la page,
découpés par lots avec **le résolveur de `S6-09`** — `resolveChannels` a déjà la
boucle, le plafond et les tests ; ce qui change est l'opération appelée, pas le
découpage. Écrire un second découpage serait le troisième exemplaire du même piège.

**L'âge du guide en tête**, quand il dépasse vingt-quatre heures.

**Ce qui n'est pas dedans** : aucun rappel, aucun enregistrement, aucun clic vers
la lecture depuis une case. Une case ouvre la chaîne ; ce qu'on en fait est
l'affaire de la page des chaînes, qui a déjà tout.

---

## Récapitulatif

| Bloc | Tâches | Points |
|---|---|---|
| Banc et découverte | S7-00 | 3 |
| Contrat et serveur | S7-01 | 5 |
| Socle Android | S7-02 | 5 |
| Télévision | S7-03, S7-04 | 8 |
| Téléphone | S7-05 | 5 |
| Web | S7-06 | 8 |
| **Total** | **7 tâches** | **34** |

**Ordre de réalisation** — les dépendances comptent plus que les priorités :

```
S7-00 → S7-01 → S7-02 → S7-03
                      → S7-04
                      → S7-05
              → S7-06
```

`S7-00` avant tout : elle est là pour découvrir ce que six sprints de code jamais
appelé cachent, et `S7-01` dépend de ce qu'elle trouve. `S7-06` ne dépend que du
contrat et peut avancer en parallèle du socle Android.

**La coupure, si 34 points est trop** : `S7-00` → `S7-04` (**21 points**) livre le
guide sur la télévision de bout en bout et se démontre seul. Le téléphone et le web
suivent. **`S7-00` et `S7-01` ne se coupent pas** : sans elles, chaque écran
réinvente sa façon d'appeler l'EPG.

---

## Hors périmètre

- **Les rappels et l'enregistrement.** v2, `AGENTS.md` §6.
- **Le timeshift et le replay.** Hors périmètre v1, et ils demanderaient au flux de
  transiter par notre infrastructure, ce qu'`architecture.md` §1 interdit.
- **Un EPG mutualisé entre comptes.** `architecture.md` §209 l'envisage — les
  données de programmation sont largement publiques — mais c'est une décision de
  produit et de coût, pas une tâche d'écran.
- **La recherche dans le guide.** Elle demanderait un index sur `epg_programme`, et
  la fenêtre glissante rend cet index cher pour ce qu'il rapporte.

---

## La dette qu'on assume

[`dette.md`](./dette.md) est à **trois entrées** depuis le 31 août 2026 : le client
OAuth Google, le webhook Stripe, et la recette des sprints 1 et 2. Aucune n'est
technique — les trois demandent une décision ou une session humaine, pas du code.

**Ce sprint est le premier à naître sous la sixième règle**, celle qui dit que le
banc sert ce que le sprint teste avant la fin du sprint. `S7-00` la respecte en
portant sa fixture dans son chiffrage, et c'est la raison pour laquelle elle est
la première tâche plutôt qu'une note en bas de la recette.

---

## Et après

- **La recette des sprints 1 et 2**, qui reste à jouer avec un rapport de session.
  Dix stories à 0 sur 10 en Definition of Done, quel que soit l'état du code. C'est
  la dette la plus vieille et la seule que le temps aggrave.
- **Les favoris de films et de séries.** Les groupes contiennent des chaînes ; le
  manque est tracé et n'a pas de date.
- **Le glisser-déposer des favoris sur le web.** `PATCH /me/favorites/{id}` existe
  depuis `S4-01` et aucun client web ne l'appelle.
- **Le client OAuth Google et le webhook Stripe**, dans cet ordre.
