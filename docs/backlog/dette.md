# Dette assumée

Cinq chantiers sont volontairement repoussés : un vrai client OAuth Google, le
webhook Stripe, la recette des sprints 1 et 2, un banc d'essai qui ne sert pas ce
que les sprints testent, et `IngestionService` sans aucun test. **Ce ne sont pas des
oublis, ce sont des décisions** — et la différence entre les deux tient à un seul
fait : une décision est écrite quelque part, avec ce qui la rouvrirait.

**Les deux dernières sont arrivées par le bas plutôt que par un arbitrage**, et
elles sont écrites ici pour cesser de l'être : la quatrième s'est signalée deux
sprints de suite en rendant une recette injouable, et la cinquième a coûté un vrai
bug chez de vrais utilisateurs.

C'est ce document. Il se relit **à l'ouverture de chaque sprint**, avant d'écrire les
tâches, parce que c'est le seul moment où corriger le cap coûte encore peu.

Il ne recense pas ce qui est hors périmètre — ça, c'est `AGENTS.md` §6 et la section
« Hors périmètre » de chaque sprint. Il recense ce qui **devra être fait**, ne l'est
pas, et n'a pas de date.

---

## État au 30 août 2026

| Dette | État réel | Ce qui la rouvrira |
|---|---|---|
| Client OAuth Google | Le code des trois surfaces existe et **n'a jamais tourné contre un vrai client** — il n'y en a dans aucun build | Un sprint dédié, ou le jour où quelqu'un s'inscrit |
| Webhook Stripe | `SRV-10` à 80 %. Ouvrir une session marche ; **un paiement réussi n'accorde rien** | La décision 1 d'[`api-gaps.md`](../design/api-gaps.md) |
| Recette sprints 1 et 2 | **Partiellement jouée**, sans rapport de session. **0 story sur 10** en Definition of Done | Une session de recette avec un rapport, cas par cas |
| Banc d'essai incomplet | **Aucun panel Xtream fonctionnel**, aucun vrai fichier de film, aucun serveur sans `Range`. Deux sprints de suite, le banc ne sert pas ce que le sprint teste | Une tâche de banc, chiffrée. En attendant : un abonnement réel, ce qui met la règle du contenu sous tension |
| `IngestionService` sans test | **Zéro test automatisé** sur la classe qui orchestre toute la synchronisation. A laissé passer un bug pendant deux sprints | Une tâche de test dédiée, avec un panel de banc (dette n° 4) |

---

## 1. Le client OAuth Google

**Ce qui existe.** US-03 est à 100 % d'implémentation, sur les trois surfaces :
Credential Manager sur le téléphone (`S2-06`), le bouton de Google sur le web
(`S3-13`), et le serveur qui vérifie l'`id_token` lui-même — signature, `aud`, `iss`,
`exp`. Il ne fait jamais confiance à un email transmis par un client, ce qui était le
point qui comptait.

**Ce qui manque n'est pas du code.** Il n'existe aucun client OAuth dans ce dépôt, et
il ne doit pas y en avoir — ce serait un secret versionné. Ce qui manque est une
configuration : un projet chez Google, un identifiant client web avec ses origines
autorisées, un identifiant client Android par variante de build avec son empreinte
SHA-1, et les variables d'environnement des trois applications.

**Ce que ça bloque, exactement.** Les cas `R-15` (première connexion Google) et `R-16`
(email déjà enregistré, rattachement sans doublon) de
[`sprint-02-recette.md`](./sprint-02-recette.md) ne sont pas exécutables. Donc US-03
ne peut pas passer sa Definition of Done, quoi qu'on fasse par ailleurs.

**Le risque de l'ajourner** est faible et il est nommable : c'est un chemin
d'authentification alternatif, l'inscription par email fonctionne, et rien dans les
sprints 4 à 6 ne s'en approche (règle 3 ci-dessous). Le risque réel est de découvrir
tard que le rattachement d'identité produit un doublon — ce qui se voit en une
minute avec un vrai client, et jamais sans.

---

## 2. Le webhook Stripe

**Ce qui existe.** `POST /billing/checkout-session` ouvre une vraie session de
paiement et crée le client chez Stripe. `POST /billing/portal-session` ouvre le
portail. `StripeClient` et `BillingController` sont écrits et testés.

**Ce qui manque.** L'endpoint qui reçoit le rappel du prestataire est **absent
d'`openapi.yaml`**, donc il n'a pas été écrit — `AGENTS.md` §3 dit qu'un besoin que le
contrat ne couvre pas s'escalade au lieu de s'inventer, et c'est ce qui a été fait.
Conséquence directe et un peu absurde : **un paiement réussi ne change rien.**
L'utilisateur paie, Stripe encaisse, et son `entitlement` reste `FREE`.

**La décision à prendre** est la décision 1 d'[`api-gaps.md`](../design/api-gaps.md) :
ajouter `POST /billing/webhook` au contrat, ou l'exclure explicitement comme surface
non publique. Les deux se défendent — un webhook n'est pas une API que nos clients
appellent — mais il faut choisir, parce que l'ambiguïté est ce qui laisse l'endpoint
non écrit.

**Ce que l'implémentation demandera**, le jour venu, et qui n'est pas trivial :

- la **vérification de signature**, sans laquelle n'importe qui accorde un abonnement
  à n'importe qui par une requête HTTP ;
- l'**idempotence**, parce que Stripe réémet ses événements et qu'un même
  `checkout.session.completed` traité deux fois doit produire le même état ;
- la **révocation** sur `customer.subscription.deleted` et le passage en `PAST_DUE`,
  qui sont la moitié du travail et celle qu'on oublie.

**Le risque de l'ajourner est nul tant que personne ne paie**, et devient immédiat le
jour où quelqu'un paie. C'est donc la dette à fermer **avant** toute mise en ligne
d'une page de tarifs, pas avant.

---

## 3. La recette des sprints 1 et 2

**Ce qui existe.** [`sprint-02-recette.md`](./sprint-02-recette.md) : 40 cas,
`R-10` → `R-73`, chacun rattaché à sa story. C'est un bon document et il n'a pas
besoin d'être réécrit.

**Ce qui manque.** Il a été **partiellement joué**, sans rapport de session. Le
document dit lui-même ce qui est acceptable : *« Non joué est une réponse acceptable
et traçable ; probablement bon ne l'est pas. »* Tant qu'aucun rapport ne dit vert,
rouge ou non joué pour chaque cas, **les dix stories du sprint 1 restent à 0 sur 10**
en Definition of Done, quel que soit l'état du code.

**Le seul pourcentage de code encore ouvert vit ici** : `S2-07` est à 70 %, et les
30 % ne sont pas du code. Ce sont `R-17` et `R-18` — tuer l'application sur un
téléphone réel et la rouvrir connectée, couper l'API et vérifier qu'un refresh
*indisponible* ne déconnecte pas. Un test JVM n'a ni processus à tuer ni Keystore à
faire survivre.

**Quatre prérequis, et sans eux la session n'est pas exécutable :**

- un téléphone Android physique, et une box ou un téléviseur Android TV physique
  **avec sa télécommande** — l'émulateur ne révèle ni un élément inatteignable au
  D-pad, ni un texte illisible à trois mètres ;
- un vrai client OAuth Google, pour `R-15` et `R-16` — c'est la dette n° 1 ;
- le banc d'essai lancé avec `BENCH_PUBLIC_URL` **et**
  `LUMO_INGEST_ALLOW_PRIVATE_HOSTS`. Oublier l'une des deux produit une panne qui ne
  ressemble pas à sa cause, et le document explique laquelle ;
- deux comptes de test, dont un vierge.

**Deux cas ne bloquent pas une story mais la livraison entière** : `R-70` (aucun
secret dans les journaux) et `R-73` (aucun contenu réel dans le livrable). Ils se
rejouent après chaque tâche qui touche à la lecture ou à la session, et ils ne
s'ajournent pas.

**Le compromis retenu** contre un sprint de recette d'un bloc est la règle 4
ci-dessous : chaque sprint se termine par sa propre démo, sur ce qu'il vient de
livrer. Une heure en fin de sprint plutôt que deux jours une fois. Cela ne rattrape
pas les 40 cas en retard — c'est ce que ce document est là pour rappeler.

---

## 4. Le banc d'essai ne sert pas ce que les sprints testent

**Ce qui existe.** `apps/web/e2e/bench/` sert des playlists M3U — valide, vide,
malformée, surdimensionnée —, un flux HLS décodable avec et sans en-tête CORS, et
deux endpoints Xtream. Pour les sprints 1 à 4, cela suffisait.

**Ce qui manque, et depuis quand :**

| Manque | Signalé au | Ce que ça rend injouable |
|---|---|---|
| Un vrai fichier vidéo progressif | sprint 5 | Six cas de lecture de film. Les fixtures `/film/*.mp4` sont **des octets MPEG-TS sous un nom de film** — assez pour l'ADR 0009 qui classe sur l'URL, pas un conteneur qu'un lecteur décode |
| Un serveur qui ignore `Range` | sprint 5 | `R-242`, `R-264`, `R-277`. Le banc répond `206` correctement, ce qui est la bonne nouvelle et le problème |
| **Un panel Xtream qui répond** | sprint 6 | **Les sections 3 à 8 de la recette du sprint 6.** Les deux endpoints Xtream du banc sont `/xtream-401/` et `/xtream-garbage/` : deux pannes. Aucun `player_api.php` ne répond `get_series` |

**Pourquoi le troisième change la nature du problème.** Les deux premiers rendaient
des cas injouables. Le troisième rend **une story entière** injouable : les séries
sont Xtream uniquement ([`adr/0010`](../adr/0010-series-are-xtream-only.md)), donc
sans panel il n'y a ni recette ni démo du sprint 6.

**Et il met la règle du contenu sous tension.** Le seul moyen actuel est un
abonnement réel, c'est-à-dire un catalogue plein de titres que tout le monde
reconnaît, au moment précis où quelqu'un voudra joindre une capture « pour montrer le
rendu ». AGENTS.md §1 ne se suspend pas, et c'est plus facile à tenir avec un banc
qu'avec de la discipline.

**Ce qu'il faudrait**, et ce n'est pas chiffré : un `player_api.php` qui réponde aux
six appels qu'émet `XtreamClient`, avec un arbre inventé de deux séries — dont **une
à deux saisons avec un trou dans la numérotation**, parce que c'est le cas que
`NextEpisodeTest` couvre en JVM et que personne n'a jamais vu à l'écran.

**Ce qui le rouvrira :** le prochain sprint qui a de la place. Chaque sprint qui
passe sans le faire ajoute une recette qui ne se joue qu'à moitié.

---

## 5. `IngestionService` n'a aucun test

**Ce qui existe.** 253 tests côté API. Aucun ne traverse `IngestionService`, la
classe qui orchestre la synchronisation d'une source de bout en bout : connexion,
authentification, chaînes, films, séries, EPG, et la gestion des échecs partiels.

**Ce que ça a coûté, et ce n'est pas hypothétique.** La contrainte
`source_sync_step_check` énumérait les valeurs valides de `sync_step`. Elle n'a
jamais appris `PARSING_VOD` (sprint 5) ni `PARSING_SERIES` (sprint 6).
`markSyncStep` levait donc une `DataIntegrityViolationException` — **pas** une
`IngestionException` — et contournait le gestionnaire dont tout le rôle est
d'empêcher l'échec d'un catalogue de faire échouer sa source.

**Résultat : toute source Xtream synchronisée depuis la fin du sprint 5 finissait en
`ERROR` et perdait ses chaînes avec ses films**, en accusant le fournisseur
(`SOURCE_UNREACHABLE`). Le défaut a été trouvé en utilisant le produit, pas en le
testant.

**Ce qui a été fait.** `0017-sync-step-values.sql` élargit la contrainte et répare
les sources cassées ; `SyncStepConstraintTest` itère sur `SyncStep.values()` au lieu
d'énumérer, donc une valeur ajoutée au contrat fait échouer le test tant que la
migration ne suit pas. **Cette classe de bug est fermée.**

**Ce qui ne l'est pas.** Rien ne teste `IngestionService` lui-même. La recette du
sprint 5 §11 le disait déjà, celle du sprint 6 le redit en §12, et `R-300` est
aujourd'hui sa seule vérification — manuelle, et elle demande un panel de banc
qui n'existe pas (dette n° 4). **Les deux dettes se tiennent** : fermer la
cinquième proprement demande la quatrième.

**Ce qui le rouvrira :** la prochaine fois qu'une étape de synchronisation est
ajoutée. Il n'y a pas de raison de croire que la troisième fois se passera mieux que
les deux premières.

---

## Les cinq règles qui empêchent la dette de grossir

Elles coûtent presque rien maintenant et très cher plus tard. Elles valent pour tout
sprint tant que ce document n'est pas vide.

**1. Aucun écran ne devine un droit d'accès.** Même si tout le monde est `FREE` et
illimité aujourd'hui, un écran qui sera un jour plafonné lit `GET /me/entitlement`. Un
appel, une fois. C'est la règle 3 du [`CLAUDE.md`](../../CLAUDE.md), et c'est
exactement là qu'on la casse par facilité.

Ce n'est pas une aspiration, c'est déjà la pratique : `S3-06` lit `max_sources` sur le
web plutôt que d'écrire « FREE = 1 source », et
[`AccountRepository`](../../apps/android/core/data/src/main/kotlin/tv/lumo/android/core/data/repository/AccountRepository.kt)
fait la même chose sur Android. La règle protège un acquis, elle ne demande pas un
effort nouveau.

**2. Un seul endroit lit l'entitlement, par client.** `AccountRepository` sur Android,
la couche `lib/api` sur le web. Le jour où le webhook accorde vraiment quelque chose,
il y a un fichier à regarder par surface, pas quinze.

**3. Les sprints qui suivent ne touchent pas `auth`.** Google se termine avec un vrai
client OAuth, pas avec du code. Une tâche qui vous emmène dans `apps/api/auth` ou dans
`feature:auth` est le signe qu'elle a dérivé — sauf si elle est justement là pour
fermer la dette n° 1.

**4. La démo de fin de sprint porte sur ce que le sprint a livré**, sur appareil réel,
télécommande en main pour la partie TV. C'est le compromis assumé contre un sprint de
recette d'un bloc.

**5. Une valeur ajoutée à une énumération du contrat s'accompagne du test qui
itère dessus.** Pas de la liste écrite à la main quelque part : du test qui parcourt
`values()`. C'est ce qui aurait attrapé `PARSING_VOD` au sprint 5 au lieu de le
laisser casser deux sprints de synchronisations. La règle est née de la dette n° 5 et
elle est ici plutôt que dans un commentaire, parce qu'un commentaire ne se relit pas
à l'ouverture d'un sprint.

---

## Ce qui n'est pas de la dette

Pour que ce document ne devienne pas la décharge où l'on range tout ce qu'on ne fait
pas :

- **Le hors-périmètre v1** — Chromecast, PiP, timeshift, enregistrement,
  multi-profils, contrôle parental, Stalker, Play Billing. C'est `AGENTS.md` §6, et ce
  sont des choix de produit, pas des travaux en retard.
- **Les décisions prises contre.** Le relais du flux par nos serveurs
  ([`ADR 0007`](../adr/0007-web-playback-direct-only.md)), la synchronisation hors
  ligne des modifications de favoris ([`sprint-04.md`](./sprint-04.md)), les séries
  reconstruites depuis un M3U ([`sprint-06.md`](./sprint-06.md)). Une décision prise
  contre n'est pas une dette : elle est fermée, avec sa raison.
- **Les manques d'API tracés ailleurs.** Les favoris de films et de séries, par
  exemple, sont nommés dans le hors-périmètre des sprints 4, 5 et 6 et se décident à
  la clôture du sprint 6. Ils ont un propriétaire et une échéance, donc ils ne sont
  pas ici.

---

## Comment on tient ce document

- **Il se relit à l'ouverture de chaque sprint.** C'est sa seule raison d'exister.
- **Une dette se ferme en supprimant sa section**, pas en la barrant. L'historique
  Git garde la trace ; un document plein de lignes barrées ne se lit plus.
- **Une dette nouvelle s'ajoute avec les mêmes trois colonnes** : l'état réel, ce que
  ça bloque exactement, et ce qui la rouvrira. Une entrée sans « ce qui la rouvrira »
  est un vœu, pas une dette.
- **La date en tête de l'état se met à jour** à chaque relecture, même si rien n'a
  changé. Un tableau sans date ne dit pas s'il est encore vrai.
