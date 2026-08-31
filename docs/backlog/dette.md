# Dette assumée

Trois chantiers sont volontairement repoussés : un vrai client OAuth Google, le
webhook Stripe, et la recette des sprints 1 et 2. **Ce ne sont pas des oublis, ce
sont des décisions** — et la différence entre les deux tient à un seul fait : une
décision est écrite quelque part, avec ce qui la rouvrirait.

> **Deux dettes ont été fermées le 31 août 2026, et elles se tenaient.**
> `IngestionService` n'avait aucun test — il en a cinq. Le banc d'essai ne servait
> ni panel Xtream, ni vrai fichier vidéo, ni serveur refusant `Range` — il sert les
> trois. On ne teste pas une ingestion Xtream sans panel Xtream, donc la seconde a
> dû partir la première.
>
> **Leurs sections ont été supprimées plutôt que barrées**, comme le demande la
> dernière règle de ce document. Ce qu'elles ont laissé derrière elles est la règle
> n° 5 ci-dessous, qui leur survit — c'est le propre d'une règle.

C'est ce document. Il se relit **à l'ouverture de chaque sprint**, avant d'écrire les
tâches, parce que c'est le seul moment où corriger le cap coûte encore peu.

Il ne recense pas ce qui est hors périmètre — ça, c'est `AGENTS.md` §6 et la section
« Hors périmètre » de chaque sprint. Il recense ce qui **devra être fait**, ne l'est
pas, et n'a pas de date.

---

## État au 31 août 2026

| Dette | État réel | Ce qui la rouvrira |
|---|---|---|
| Client OAuth Google | Le code des trois surfaces existe et **n'a jamais tourné contre un vrai client** — il n'y en a dans aucun build | Un sprint dédié, ou le jour où quelqu'un s'inscrit |
| Webhook Stripe | `SRV-10` à 80 %. Ouvrir une session marche ; **un paiement réussi n'accorde rien** | La décision 1 d'[`api-gaps.md`](../design/api-gaps.md) |
| Recette sprints 1 et 2 | **Partiellement jouée**, sans rapport de session. **0 story sur 10** en Definition of Done | Une session de recette avec un rapport, cas par cas |

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

## Les six règles qui empêchent la dette de grossir

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
laisser casser deux sprints de synchronisations.

La règle est née d'une dette qui est maintenant fermée, et elle lui survit : c'est
le propre d'une règle. `SyncStepConstraintTest` la tient pour `SyncStep`, et
`IngestionServiceIntegrationTest` tient le chemin en dessous — remettre l'ancienne
contrainte fait rougir trois de ses cinq cas.

**6. Le banc sert ce que le sprint teste, et il le sert avant la fin du sprint.**
Deux sprints de suite, une recette a été écrite en ouvrant sur « ce plan n'est pas
jouable ». Les deux fois, ce qui manquait a coûté moins cher à écrire que la gêne
qu'il a causée — un panel de banc, un vrai fichier vidéo, et `max_ranges 0`. Une
tâche qui a besoin d'une fixture que le banc n'a pas porte cette fixture dans son
chiffrage.

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
