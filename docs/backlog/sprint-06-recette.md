# Recette — Sprint 6

Plan de qualification d'**US-15** sur les trois surfaces, plus le rattrapage
d'**US-12** sur le web. Il se déroule **à la main**, sur des appareils réels et
dans un navigateur, et il ne remplace ni les 253 tests API, ni les 171 tests
Android, ni les 47 tests web : il vérifie ce qu'aucun des trois ne voit — qu'une
série commencée sur un téléphone se reprend sur une télévision, qu'un décompte de
dix secondes s'annule sous le doigt, et qu'un groupe de favoris réparti sur deux
abonnements s'affiche entier.

Les critères d'acceptation sont ceux d'**US-15**, en Gherkin, dans
[`sprint-06.md`](./sprint-06.md). Chaque cas renvoie à la tâche qui l'a livré.

**La numérotation reprend à R-300.** R-10 → R-73 appartiennent à
[`sprint-02-recette.md`](./sprint-02-recette.md), R-100 → R-194 à
[`sprint-04-recette.md`](./sprint-04-recette.md), R-200 → R-296 à
[`sprint-05-recette.md`](./sprint-05-recette.md), et les trois restent à jouer.

---

## 1. Prérequis

### Le prérequis qui décide si cette recette est jouable

**Il faut un panel Xtream qui répond, et le banc d'essai n'en a pas.**

Les séries sont une fonction Xtream et rien d'autre
([`adr/0010`](../adr/0010-series-are-xtream-only.md)) : une playlist M3U ne déclare
ni saison ni épisode, et Lumo n'invente pas d'arbre à partir des titres. Or le banc
(`apps/web/e2e/bench/nginx.conf`) ne sert que deux endpoints Xtream, et **les deux
sont des pannes** :

| Chemin | Ce qu'il rend | Ce qu'il sert à vérifier |
|---|---|---|
| `/xtream-401/` | `401 {"user_info":{"auth":0}}` | `SOURCE_AUTH_FAILED` |
| `/xtream-garbage/` | `200 not json` | `SOURCE_INVALID_FORMAT` |

Il n'existe **aucun `player_api.php` qui réponde** `get_series_categories`,
`get_series` ou `get_series_info`. Sans l'un des deux moyens ci-dessous,
**les sections 3 à 8 sont intégralement « non joué »**, ce qui est une réponse
traçable et une recette qui ne prouve rien.

**Les deux moyens, et leurs coûts :**

1. **Un abonnement réel**, celui d'un membre de l'équipe. C'est ce qui marche
   aujourd'hui, et c'est ce qui rend la §2 non négociable : rien de ce panel
   n'entre dans le dépôt, ni capture, ni titre, ni URL, ni identifiant.
2. **Ajouter un panel Xtream au banc.** C'est le bon investissement et il n'est pas
   chiffré. Il faut un `player_api.php` qui réponde aux six appels que
   `XtreamClient` émet, avec un arbre inventé de deux séries — dont **une avec deux
   saisons et un trou dans la numérotation**, parce que c'est le cas que
   `NextEpisodeTest` couvre en JVM et que personne n'a jamais vu à l'écran.

   > C'est le pendant exact de ce que la recette du sprint 5 disait du fichier de
   > film : le banc ne fournit pas la chose que le sprint teste. Deux sprints de
   > suite, la même phrase. Elle est reprise en §12 et dans
   > [`dette.md`](./dette.md).

### Les prérequis habituels

**Les trois surfaces, sur le même compte.** Un téléphone Android physique, une box
ou un téléviseur Android TV physique **avec sa télécommande**, et un navigateur. La
section 8 est la moitié de la valeur de ce sprint et ne se démontre pas sur une
seule surface.

**Une mise à jour, pas une installation neuve.** Le piège habituel, et il a une
migration de plus. `MIGRATION_5_6` crée `series`, `season` et `episode` — **les
premières clés étrangères du schéma** — elle est écrite à la main, et une
installation fraîche ne l'exécute jamais : Room crée le schéma final directement.

```bash
# Sur le téléphone et sur la box, avant de commencer :
adb install -r app-mobile-debug.apk   # -r : par-dessus, jamais une désinstallation
```

Désinstaller entre les deux, c'est jouer R-392 en croyant l'avoir joué.

**Langue.** Chaque cas se rejoue en français **et** en anglais. Trois endroits
souffrent particulièrement : la phrase d'une playlist qui ne peut pas porter de
séries (longue dans les deux langues), « À suivre dans 7 s » sur une carte de
télévision, et « Depuis *[source]* » sous une chaîne favorite.

**Deux sources, dont une M3U.** `/playlist.m3u` du banc suffit pour la seconde :
c'est elle qui fait la différence entre « ce format ne peut pas porter de séries »
et « ce panel n'en propose pas », et ce sont deux phrases distinctes qu'il faut
avoir vues toutes les deux (R-312, R-313).

**Et la manière de basculer diffère selon la surface**, comme au sprint 5 : le web
a une page par source, les applications Android lisent **la première source du
compte** et n'ont pas de sélecteur. Les cas qui demandent une M3U sur Android
exigent donc deux comptes, ou une suppression et un réenregistrement entre les deux
passes.

**Deux sources pour la section 9**, et cette fois c'est le cœur du cas : R-380
vérifie qu'un groupe contenant des chaînes de **deux abonnements** les affiche
toutes. Avec une seule source, ce cas est « non joué », pas « vert » — et c'est
précisément le défaut que S6-09 est venu corriger.

---

## 2. La règle qui prime sur la recette elle-même

**Aucun contenu réel, à aucun moment.** Pas de vraie playlist, pas d'identifiants
d'un vrai fournisseur, **pas d'affiche**, pas de titre d'une œuvre existante, pas
de nom de bouquet — ni dans les captures jointes à un rapport, ni dans un ticket
ouvert pendant la session (AGENTS.md §1, CLAUDE.md règle 2).

**Ce sprint rend cette règle plus difficile à tenir que tous les précédents**, et
il faut le dire franchement : le §1 admet qu'un abonnement réel est aujourd'hui le
seul moyen de jouer la recette. Un catalogue de séries réel est plein de titres
que tout le monde reconnaît, et la tentation de joindre une capture « pour montrer
le rendu » est maximale.

**La règle de session est donc :** on regarde l'écran, on ne le photographie pas.
Un rapport décrit — « la carte À suivre est apparue à la fin de l'épisode, le
décompte s'est arrêté au premier appui » — et ne montre pas. Une capture n'est
recevable que si elle vient du banc, avec des titres inventés.

---

## 3. L'ingestion, l'arbre et le cache — serveur

Cette section se joue **une fois**, sur le serveur, et conditionne tout le reste.

**R-300 · Une source Xtream se synchronise entière** · S6-03 · serveur
Enregistrer une source Xtream, attendre `READY`.
→ `sync_step` traverse `CONNECTING` → `AUTHENTICATED` → `PARSING_CHANNELS` →
`PARSING_VOD` → **`PARSING_SERIES`** → `FETCHING_EPG`, et la source finit `READY`.

> **Ce cas est le plus important de la section, et il l'est pour une raison
> historique.** La contrainte `source_sync_step_check` énumérait les valeurs
> valides et n'a jamais appris `PARSING_VOD` (sprint 5) ni `PARSING_SERIES`
> (sprint 6). `markSyncStep` levait une `DataIntegrityViolationException` — pas une
> `IngestionException` — donc **elle contournait le gestionnaire dont tout le rôle
> est d'empêcher l'échec d'un catalogue de faire échouer sa source**. Toute source
> Xtream synchronisée depuis la fin du sprint 5 finissait en `ERROR` et perdait ses
> chaînes avec ses films, en accusant le fournisseur (`SOURCE_UNREACHABLE`).
>
> Corrigé par `0017-sync-step-values.sql` et gardé par `SyncStepConstraintTest`,
> qui itère sur `SyncStep.values()` au lieu d'énumérer. **Ce cas est ce qui aurait
> attrapé le bug**, et il a survécu deux sprints parce que rien n'exerce
> `IngestionService` (§12).

**R-301 · Une playlist M3U ne produit aucune série** · S6-00 · serveur
→ `GET /sources/{id}/series` rend `total_elements: 0`. Pas d'erreur, pas de série
devinée à partir d'un titre. C'est `adr/0010`, vérifié sur une vraie ingestion.

**R-302 · L'arbre n'est pas chargé à la synchronisation** · S6-03 · serveur
Après une synchronisation complète, interroger la base.
→ **Toutes les lignes `series` ont `tree_fetched_at` à `NULL`.** Un panel de
cinquante mille séries qui chargerait chaque arbre à la synchro ferait cinquante
mille appels chez le fournisseur, et c'est exactement la décision que S6-03 a prise
contre.

**R-303 · Le premier `GET /series/{id}` va chercher l'arbre** · S6-03 · serveur
→ La réponse est lente (c'est un aller-retour chez le fournisseur), puis
`tree_fetched_at` est renseigné et **le second appel répond immédiatement**.

**R-304 · Le cache expire à six heures** · S6-03 · serveur
Reculer `tree_fetched_at` de plus de six heures en base, rappeler.
→ Un nouvel appel part chez le fournisseur. `TREE_TTL = 6h`, et six heures est le
compromis entre une saison ajoutée qu'on ne voit pas et un panel qu'on martèle.

**R-305 · Deux arbres au plus en vol par source** · S6-03 · serveur
Ouvrir cinq séries d'affilée sur la même source.
→ Les requêtes sortantes ne dépassent jamais deux en parallèle vers ce panel.
`MAX_CONCURRENT_PER_SOURCE = 2`, et c'est `ADR 0005` appliqué : les threads
virtuels ont supprimé la contre-pression implicite, donc la limite est obligatoire
et pas une optimisation.

**R-306 · Deux ouvertures simultanées de la même série font un seul appel** ·
S6-03 · serveur
→ Le `single-flight` (`inFlight`) tient : deux clients qui ouvrent la même série au
même instant produisent **une** requête sortante.

**R-307 · Un panel qui ne répond pas donne 503, pas 404** · S6-01 · serveur
Couper le panel, appeler `GET /series/{id}` sur une série jamais ouverte.
→ `503` avec `SOURCE_UNREACHABLE`, **jamais** `404 SERIES_NOT_FOUND`. Dire à
quelqu'un que sa série n'existe pas parce que son fournisseur a hoqueté l'envoie
chercher au mauvais endroit — c'est pour ça que le contrat donne deux codes.

**R-308 · L'unicité survit à une resynchronisation** · S6-02 · serveur
Resynchroniser la source, comparer les identifiants avant et après.
→ Les `series.id` et `episode.id` sont **inchangés** pour tout ce que le panel
propose toujours. C'est ce qui fait qu'une position sauvegardée n'est pas perdue à
chaque synchro, et c'est ce que la section 8 suppose.

**R-309 · Un épisode retiré emporte ses lignes** · S6-02 · serveur
→ Les `season` et `episode` d'une série disparue sont supprimés en cascade. Aucun
orphelin.

---

## 4. La grille des séries — téléphone

**R-310 · L'onglet Séries est toujours là** · S6-05 · téléphone
→ **Six entrées** dans la barre : Chaînes, Films, **Séries**, Favoris, Source,
Réglages. L'onglet est là quelle que soit la source.

> Ce cas dit le contraire de ce que R-210 disait au sprint 5, et pour la raison qui
> a fait renverser R-210 : un usage réel a montré qu'une absence est indiscernable
> d'un bug. La moitié de la règle qui survit est R-311.

**R-311 · Aucun onglet vers un écran non construit** · S6-05 · téléphone
→ **Pas d'onglet Recherche.** C'est le dernier placeholder, et une entrée qui mène
à « cette fonction arrivera » dit qu'elle existe alors qu'elle n'existe pas.

**R-312 · Une playlist M3U dit pourquoi elle n'a pas de séries** · S6-05 · téléphone
Compte dont la première source est `/playlist.m3u`.
→ « Aucune série » puis : *cette source est une playlist M3U, le format ne déclare
ni saison ni épisode, Lumo n'invente pas d'arbre à partir des titres, les séries
sont disponibles sur les sources Xtream.*

**R-313 · Un panel sans séries dit autre chose** · S6-05 · téléphone
Source Xtream dont le panel ne propose pas de séries.
→ « Ce fournisseur ne propose pas de séries. » **Une phrase différente de R-312**,
et c'est le cas qui compte : dire à un utilisateur Xtream que son panel ne peut pas
faire quelque chose qu'il peut faire est la pire des deux erreurs.

**R-314 · Une recherche sans résultat dit une troisième chose** · S6-05 · téléphone
→ « Aucun résultat », et non « aucune série ». Trois absences, trois phrases.

**R-315 · Des affiches, deux colonnes** · S6-05 · téléphone
→ Deux colonnes, affiches en portrait 2:3, titre et année sous l'image. Identique à
la grille des films : un spectateur qui a appris l'un des deux catalogues a appris
l'autre.

**R-316 · Une série sans affiche n'a pas d'image de remplacement** · S6-05 ·
téléphone
→ Un rectangle neutre. **Aucune image inventée**, jamais (AGENTS.md §1).

**R-317 · La bande de catégories filtre** · S6-05 · téléphone
→ « Toutes » en premier, puis les catégories de séries du panel avec leur décompte.

**R-318 · La recherche est locale et ne pardonne pas une faute de frappe** ·
S6-05 · téléphone
→ Elle parcourt ce qui est déjà synchronisé, et le dit dans son message vide.

---

## 5. La fiche et la lecture — téléphone

**R-320 · L'écran n'est jamais vide pendant que l'arbre charge** · S6-05 · téléphone
Ouvrir une série jamais ouverte, sur une connexion lente.
→ **Affiche, titre et informations sont là immédiatement** (ils viennent de la
liste) ; **seule la zone des saisons attend**. Un spinner sur tout l'écran est
rouge : c'est une attente imposée sur des données déjà détenues.

**R-321 · La première saison est ouverte à l'arrivée** · S6-05 · téléphone
→ Pas de sélecteur vide. Une saison à choisir avant de voir quoi que ce soit est
une décision imposée à quelqu'un qui n'a rien demandé.

**R-322 · Une seule saison n'affiche aucun sélecteur** · S6-05 · téléphone
→ Pas de puce solitaire au-dessus de ses propres épisodes.

**R-323 · Un épisode sans titre affiche son numéro** · S6-05 · téléphone
→ « Épisode 4 ». **Jamais « Épisode sans titre »**, qui remplit une ligne pour dire
qu'elle est vide.

**R-324 · Un fournisseur qui ne répond pas n'est pas une série vide** · S6-05 ·
téléphone
Couper le réseau, ouvrir une série dont l'arbre n'est pas en cache.
→ « Épisodes indisponibles — votre fournisseur n'a pas répondu, la série est
toujours là », **et un bouton Réessayer**. Afficher « aucun épisode » serait un
mensonge qui dure autant que la panne.

**R-325 · Un panel qui ne liste aucune saison le dit comme un fait** · S6-05 ·
téléphone
→ « Votre fournisseur liste cette série mais n'a renvoyé aucune saison pour elle.
Il ne manque rien de notre côté. » Rare et réel, et **distinct de R-324**.

**R-326 · Le décompte annoncé apparaît quand il diverge** · S6-05 · téléphone
Saison où le panel annonce 24 épisodes et en renvoie 22.
→ « 22 épisodes listés, 24 annoncés par votre fournisseur. » Non réconcilié : la
liste est ce qu'on peut regarder, l'annonce est parfois le seul indice.

**R-327 · Un épisode se lit** · S6-05 · téléphone
→ Image et son. Le lecteur est celui des films.

**R-328 · Le curseur avoue quand le serveur refuse `Range`** · S6-05 · téléphone
→ La barre est dessinée, inerte, **avec la phrase à côté**. Une touche qui a l'air
morte est un lecteur dont on croit qu'il a cessé de fonctionner. *(Demande un
serveur sans `Range` — voir la recette du sprint 5 §1.)*

**R-329 · L'URL de flux n'apparaît nulle part** · S6-05 · téléphone

```bash
adb logcat | grep -iE "username|password|player_api|\.mkv|\.mp4"
```

→ **Aucune ligne.** Ce cas ne bloque pas une story, il bloque la livraison
(AGENTS.md §5).

---

## 6. La télévision, télécommande en main

**Cette section ne se joue qu'avec une télécommande.** Une souris produit du survol,
pas du focus, et le survol masque exactement les défauts que
[`tv-focus-map.md`](../design/tv-focus-map.md) existe pour éviter.

**R-340 · L'entrée Séries est dans le rail** · S6-06 · TV
→ **Cinq entrées** : Chaînes, Films, Séries, Source, Réglages. Pas de Recherche.

**R-341 · La grille arrive sur la première affiche** · S6-06 · TV
→ Une ligne d'affiches, focus sur la première carte, `UP` vers la bande de
catégories, `DOWN` retour. Pas sur la bande : celui qui ouvre les séries veut une
série.

**R-342 · `OK` ouvre la fiche, il ne lance pas** · S6-06 · TV
→ Une série se **choisit**. Il n'y a rien d'autre que `OK` puisse faire : une série
ne se lit pas, un épisode se lit.

**R-343 · Le focus arrive sur un épisode, jamais sur le sélecteur de saison** ·
S6-06 · TV
→ Ouvrir une série, ne toucher à rien : le focus est sur un épisode.

**R-344 · `UP` depuis le premier épisode atteint le sélecteur — quand il existe** ·
S6-06 · TV
Une série à plusieurs saisons, puis une série à saison unique.
→ Multi-saisons : `UP` atteint la puce. Saison unique : **`UP` est un bord et ne
fait rien**, parce que le sélecteur n'est pas dessiné.

> **C'est la seule zone de focus conditionnelle de toute l'application**, et le seul
> endroit où une carte de focus peut être juste sur le papier et fausse en main.
> Ce cas ne s'allège pas.

**R-345 · `OK` sur une saison descend le focus dans sa liste** · S6-06 · TV
→ Le focus ne reste pas sur la puce. Y rester coûte un appui pour découvrir que ce
qu'on a demandé a bien eu lieu.

**R-346 · La carte « À suivre » apparaît à la fin d'un épisode** · S6-06 · TV
→ Une carte, **un décompte de dix secondes affiché en chiffres**, et elle prend le
focus.

**R-347 · `OK` lance le suivant tout de suite** · S6-06 · TV

**R-348 · Le décompte s'arrête au premier appui, quelle que soit la touche** ·
S6-06 · TV
Appuyer sur `UP` — une touche que cet écran ignore — pendant le décompte.
→ **Le décompte s'arrête, et la carte reste.** `OK` fonctionne toujours.

> **Le cas le plus important de la section.** Quelqu'un qui appuie sur une touche a
> pris la télécommande *parce que* le générique a commencé. Lancer un épisode sous
> son doigt ne se pardonne pas. Et l'annulation doit valoir pour les touches que
> l'écran n'utilise pas — c'est pour ça qu'elle est en `onPreviewKeyEvent`.

**R-349 · La fin d'une saison enchaîne sur la suivante** · S6-06 · TV
→ Le dernier épisode de la saison 1 propose le premier de la saison 2.

**R-350 · Le dernier épisode d'une série ne propose rien** · S6-06 · TV
→ Aucune carte, et l'écran **revient à la fiche**. Une image figée sur la dernière
image d'une série est, à trois mètres, une application qui a cessé de répondre.

**R-351 · Six épisodes enchaînés laissent un seul `BACK`** · S6-06 · TV
Laisser trois enchaînements se faire, puis `BACK`.
→ On revient **à la fiche**, pas à l'épisode précédent. Avancer ne navigue pas.

**R-352 · `LEFT`/`RIGHT` n'ouvrent jamais le titre** · S6-06 · TV
→ Ils déplacent et ouvrent **la barre seule**. `OK` ouvre la barre d'information.
Les deux touches sont à un geste l'une de l'autre sur toutes les télécommandes.

**R-353 · Rien ne se déplace pendant que la carte est là** · S6-06 · TV
→ `LEFT`/`RIGHT` pendant l'offre : le décompte s'arrête (R-348) et **la lecture ne
bouge pas**. L'épisode est fini ; les touches appartiennent à la carte.

**R-354 · `BACK` revient sur la série qu'on regardait** · S6-06 · TV
Descendre dans la grille, ouvrir une série, `BACK`.
→ La grille revient **sur cette affiche**, pas en tête. US-10, un catalogue plus
loin.

---

## 7. Le web

**R-360 · Les trois onglets sont là** · S6-07 · navigateur
→ Chaînes, Films, Séries, sur la page de n'importe quelle source.

**R-361 · La fiche se rend en deux temps** · S6-07 · navigateur
Ouvrir une série jamais ouverte.
→ Affiche, titre et informations arrivent d'abord ; l'arbre suit en `Suspense`.
C'est la seule zone du site où ce découpage est justifié, et il l'est parce que
`GET /series/{id}` va chercher chez le fournisseur.

**R-362 · La saison ouverte est dans l'URL** · S6-07 · navigateur
→ `?season=2`. Partageable, correct au bouton retour.

**R-363 · La fiche fonctionne sans JavaScript** · S6-07 · navigateur
Désactiver JavaScript, ouvrir la fiche, changer de saison.
→ Tout marche sauf le lecteur. La règle de la zone ne se suspend pas parce que
l'écran est neuf.

**R-364 · 404 et 503 ne disent pas la même chose** · S6-07 · navigateur
→ Une série inexistante est finale ; un panel muet propose de réessayer.

**R-365 · Un épisode se lit, ou l'échec est nommé** · S6-07 · navigateur
→ `<video src>`, pas `hls.js`. Sur un panel en `http://` depuis `https://`, le
message de contenu mixte apparaît — **ADR 0007 sans changement**, et l'échec est
nommé plutôt que silencieux.

---

## 8. La reprise, entre les appareils

**C'est la moitié de la valeur du sprint, et elle ne se démontre pas sur une seule
surface.**

**R-370 · Une position d'épisode est enregistrée** · S6-08 · téléphone
Regarder cinq minutes d'un épisode, quitter le lecteur.
→ `GET /me/progress?itemType=EPISODE` porte une ligne avec cet `item_ref`.

**R-371 · La dernière sauvegarde précède l'arrêt du lecteur** · S6-08 · téléphone
Quitter le lecteur à 12:00, rouvrir.
→ La position est **12:00**, pas zéro. Sauvegarder après `stop()` écrirait chaque
spectateur au début de tout ce qu'il quitte.

**R-372 · Une barre apparaît sous l'épisode commencé, et sous lui seul** · S6-08 ·
téléphone
→ Une seule ligne de la saison porte une barre. **Une barre à zéro sur chaque
épisode est rouge** : elle dirait que tout le monde a commencé tout.

**R-373 · Aucune barre sans durée annoncée** · S6-08 · téléphone
Épisode dont le panel ne donne pas de durée.
→ Pas de barre. Une barre a besoin d'une fin ; pleine parce que la fin est inconnue,
elle ment.

**R-374 · Le rail « Reprendre » montre une carte par série** · S6-08 · téléphone
Regarder trois épisodes de la **même** série.
→ **Une seule carte**, celle de l'épisode le plus récent. Trois cartes est rouge :
c'est avoir compris la donnée et pas l'usage.

**R-375 · Sous 95 %, la carte reprend à la position** · S6-08 · téléphone

**R-376 · Au-delà de 95 %, la carte propose l'épisode suivant, au début** · S6-08 ·
téléphone
→ Ce qu'on veut après le générique est l'épisode d'après, pas le générique.

**R-377 · Le dernier épisode terminé sort la série du rail** · S6-08 · téléphone
→ Proposer de recommencer une série finie n'est pas une proposition.

**R-378 · Commencé sur le téléphone, repris sur la télévision** · S6-08 ·
téléphone → TV
Regarder dix minutes d'un épisode sur le téléphone, ouvrir la même série sur la TV.
→ **Le focus arrive sur cet épisode**, avec sa barre, et `OK` reprend à dix minutes.

> **C'est le cas qui prouve le sprint.** Et il vérifie au passage la limite
> assumée : le rail de la télévision ne montre que les séries dont l'arbre est
> connu de cet appareil, et ouvrir la fiche est précisément ce qui le charge.

**R-379 · La puce « Reprendre » de la TV n'apparaît que si elle contient quelque
chose** · S6-08 · TV
→ Sur un compte neuf, pas de puce. Une puce qui filtre sur rien laisse une grille
vide après un `OK`, ce qui à trois mètres se lit comme une panne.

---

## 9. Les favoris à l'échelle du compte — web

**Cette section ne porte pas sur les séries.** C'est le rattrapage d'US-12 sur le
web, et elle a besoin de **deux sources**.

**R-380 · Un groupe montre les chaînes de deux abonnements** · S6-09 · navigateur
Créer un groupe, y mettre une chaîne de la source A **et** une de la source B,
ouvrir `/app/favorites`.
→ **Les deux sont là.**

> **C'est le cas qui donne son existence à cette tâche.** Avant S6-09 le web
> montrait ce groupe amputé de la moitié, sans le dire — la catégorie de défaut la
> plus chère, parce que rien n'a l'air cassé.

**R-381 · Chaque ligne dit d'où elle vient** · S6-09 · navigateur
→ « Depuis *[libellé de la source]* » sous chaque chaîne. Sans ça, l'écran ne dit
toujours pas ce qu'il est venu dire.

**R-382 · Le groupe ouvert est dans l'URL** · S6-09 · navigateur
→ `?group=`. Partageable, correct au bouton retour.

**R-383 · L'écran fonctionne sans JavaScript** · S6-09 · navigateur
→ Changer de groupe, créer, renommer, supprimer, retirer un favori : tout marche.

**R-384 · Un groupe de plus de cent chaînes s'affiche entier** · S6-09 · navigateur
Un groupe d'au moins 120 chaînes sur une source.
→ **120 lignes.** Cent tout rond est rouge.

> C'est `R-140` du sprint 4 transposé au web. Le plafond `ids` est de cent **par
> requête**, donc il faut une boucle et pas une coupe — une coupe tronquerait avec
> un `200` et aucune erreur. Huit tests JVM couvrent le découpage
> (`resolve-channels.test.ts`) ; **ce cas est le seul qui le voie sur un vrai
> catalogue**.

**R-385 · La page des chaînes dit ce qu'elle ne montre pas** · S6-09 · navigateur
Ouvrir la page des chaînes de la source A, sur un groupe qui contient aussi des
chaînes de B.
→ Une phrase sous le rail compte les chaînes venues d'ailleurs et renvoie vers
`/app/favorites`. **Le rail reste limité à sa source** — c'est structurel, il ne
peut résoudre que dans la sienne — mais il ne se tait plus.

**R-386 · Renommer un groupe le renomme aux deux endroits** · S6-09 · navigateur
→ Le nom change sur `/app/favorites` **et** sur la page des chaînes. Une seule
barre, un seul composant.

---

## 10. Cas transverses

**R-390 · Les deux langues** · toutes surfaces
→ Chaque écran de ce sprint, en français et en anglais. Trois endroits à regarder :
la phrase de la playlist M3U, « À suivre dans 7 s », « Depuis *[source]* ».

**R-391 · Hors ligne, l'arbre déjà chargé reste lisible** · S6-04 · téléphone
Ouvrir une série, couper le réseau, revenir dessus.
→ Saisons et épisodes s'affichent. **Ce qui est en cache gagne toujours** ; un
rafraîchissement pose `stale`, jamais un spinner.

**R-392 · Une mise à jour ne perd rien** · S6-04 · téléphone
Installer par-dessus une version du sprint 5, sans désinstaller.
→ Chaînes, films et favoris intacts ; les séries apparaissent après une synchro.
**`MIGRATION_5_6` est ce qui est testé ici**, et une installation fraîche ne
l'exécute jamais.

**R-393 · Aucun secret dans les journaux** · toutes surfaces
→ Rejouer `R-70`. Ne bloque pas une story, bloque la livraison.

**R-394 · Aucun contenu réel dans le livrable** · toutes surfaces
→ Rejouer `R-73`, et avec une attention particulière cette fois : voir §2.

---

## 11. Critère de sortie

La recette est verte quand **tous les cas ci-dessus passent, dans les deux langues,
sur appareils réels, télécommande en main pour la section 6**.

Un cas rouge bloque US-15, sauf R-393 et R-394 qui bloquent la livraison quelle que
soit la story, et R-380 → R-386 qui bloquent US-12.

**Quatre cas ne s'allègent pas**, et pour quatre raisons différentes :

- **R-300** est ce qui aurait attrapé un bug qui a détruit des synchronisations
  pendant deux sprints. Il est manuel parce que rien n'exerce `IngestionService`.
- **R-344** — `UP` depuis le premier épisode — est la seule zone de focus
  conditionnelle de l'application, et ne se vérifie qu'à la télécommande.
- **R-348** — le décompte qui s'arrête sous n'importe quelle touche — est le seul
  endroit du sprint où un défaut lance une lecture que personne n'a demandée.
- **R-380** est la raison d'être de S6-09.

Le rapport de session note, pour chaque cas : vert, rouge, ou **non joué avec la
raison**. « Pas de panel Xtream sur le banc » et « installé à neuf » sont des
raisons recevables et traçables. « Probablement bon » ne l'est pas.

---

## 12. Ce que cette recette ne couvre pas

Écrit ici plutôt que découvert plus tard.

- **Le banc d'essai ne sert aucun panel Xtream fonctionnel**, et les séries sont
  Xtream uniquement. Les sections 3 à 8 dépendent donc d'un abonnement réel, ce qui
  met la §2 sous tension maximale. **C'est le deuxième sprint de suite où le banc ne
  fournit pas la chose que le sprint teste** — le sprint 5 disait la même phrase
  d'un fichier de film. Le chiffrage d'un `player_api.php` de banc est à faire, et
  il est dans [`dette.md`](./dette.md).
- **`IngestionService` n'a toujours aucun test automatisé.** R-300 est sa seule
  vérification et elle est manuelle. Ce trou a laissé passer le bug de
  `source_sync_step_check` pendant deux sprints ; la recette du sprint 5 le nommait
  déjà.
- **Le rafraîchissement des séries n'est pas mesuré.** `SeriesRepository.refresh`
  parcourt la pagination à 200 par page ; un panel de cinquante mille séries est
  **deux cent cinquante requêtes** à la première synchronisation d'un appareil.
  Aucun cas ici ne le chronomètre, et personne ne sait ce que ça donne sur une
  connexion domestique.
- **Le glisser-déposer des favoris n'existe pas sur le web.**
  `PATCH /me/favorites/{id}` est livré côté serveur depuis `S4-01` et n'est appelé
  par aucun client web. C'est hors de S6-09 par décision explicite, pas un oubli.
- **Aucun sélecteur de source sur Android.** Toujours vrai, toujours ce qui rend
  R-312 et R-313 lourds à jouer, toujours pas chiffré.
- **La lecture web reste directe ou refusée** (ADR 0007). Cette recette vérifie que
  l'échec est **nommé**, pas qu'il n'arrive pas.
