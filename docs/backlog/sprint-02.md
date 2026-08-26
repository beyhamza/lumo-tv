# Backlog — Sprint 2

## Terminer la verticale : les clients Android

> Un utilisateur crée un compte, enregistre une source M3U ou Xtream, et regarde sa
> première chaîne — **sur son téléphone et sur sa télévision**.

C'est mot pour mot l'objectif du sprint 1. Il n'est pas atteint : le serveur le tient,
les clients Android sont des placeholders. Ce sprint ne rouvre aucune story et n'en
crée aucune — il décrit **le travail qui manque pour que les dix stories du sprint 1
passent leur Definition of Done**.

Les critères d'acceptation sont ceux de [`sprint-01.md`](./sprint-01.md), en Gherkin,
et ne sont pas recopiés ici. Chaque tâche dit quelle story elle ferme.

---

## Deux constats qui commandent l'ordre des tâches

**Aucune API ne manque pour les dix stories.** Elles sont couvertes par les
contrôleurs existants : `auth` (inscription, connexion, Google, rotation,
activation TV), `sources` (création, statut, resynchronisation), `catalog`
(catégories, chaînes, lecture, EPG). Aucune des quinze tâches Android ci-dessous
ne touche `openapi.yaml` ni `apps/api`. C'est ce qui les rend faisables par une
seule personne sans coordination.

> Un **lot serveur** a néanmoins été livré depuis, hors périmètre de ce sprint :
> les treize manques que les maquettes avaient exposés. Il n'ajoute aucune story
> et ne change rien aux quinze tâches ; il ouvre les écrans que les maquettes
> mobile et TV dessinent au-delà de la verticale (favoris, reprise, récents,
> abonnement). Voir la section « Lot serveur » en fin de checklist.

**Il manque une couche de données.** `settings.gradle.kts` déclare `core:common`,
`core:designsystem`, `core:network`, `core:auth`, `core:database`, `core:player` — et
rien entre un écran et le client Retrofit généré. Écrire les huit écrans avant ce
socle produirait huit façons différentes d'appeler l'API et de traduire une erreur.
S2-01 est donc un préalable dur, pas une préférence d'architecture.

---

## Ce qui existe déjà, et qu'on ne réécrit pas

À lire avant de commencer, sous peine de reconstruire ce qui est là et testé :

| Module | Ce qu'il fournit |
|---|---|
| `core:auth` | `SessionManager` complet — `open`, `signOut`, `refresh` sous mutex, `isSignedIn` ; stockage chiffré Keystore |
| `core:network` | `AuthInterceptor`, `TokenAuthenticator`, `RetrofitTokenRefresher`, la DI Retrofit/OkHttp |
| `core:database` | `LumoDatabase`, `CategoryDao`, `ChannelDao`, `CataloguePager` |
| `core:player` | `LumoPlayer`, `Media3LumoPlayer`, `LumoVideoSurface` |
| `core:designsystem` | Thèmes mobile et TV, tokens, typographie, `TvFocus`, barre et rail de navigation |
| `feature:*` | Huit modules, navigation câblée, chaînes FR/EN en place — **seuls les écrans sont des placeholders** |

Le client Retrofit est **généré** depuis le contrat (`generated/kotlin`). On ne
l'édite jamais (ADR 0001).

---

## Definition of Ready

Celle du sprint 1, plus deux conditions propres à Android :

- la tâche dit quel écran a le focus à l'arrivée et où mène chaque direction du D-pad,
  si elle touche `app-tv` ;
- ses libellés existent en FR **et** en EN — `MissingTranslation` est fatal en CI.

## Definition of Done

Celle du sprint 1, sans allègement. Le point qui coince en pratique et sur lequel on
ne transige pas : **la démo se fait sur device réel, télécommande en main pour la
partie TV.** Une grille pilotée à la souris sur un émulateur ne prouve rien du seul
défaut qui compte à ce stade — un élément inatteignable au D-pad.

Le déroulé est écrit dans [`sprint-02-demo.md`](./sprint-02-demo.md), le plan de
qualification dans [`sprint-02-recette.md`](./sprint-02-recette.md).

---

## Tâches

Une case par tâche, un pourcentage dès que l'avancement est partiel. La
checklist se met à jour **dans le commit qui livre le travail**, pas après.

| | Id | Tâche | Ferme | Cible | Points | Avancement |
|---|---|---|---|---|---|---|
| ☐ | S2-00 | Aligner les tokens Android sur la charte Spectre | S0-07 | android | 2 | 0 % |
| ☐ | S2-01 | `core:data` : repositories et erreurs typées | socle | android | 8 | 0 % |
| ☐ | S2-02 | Navigation pilotée par la session, périmètre réduit | socle | android | 3 | 0 % |
| ☐ | S2-03 | Banc d'essai des sources | outillage | recette | 3 | 0 % |
| ☐ | S2-04 | Inscription | US-01 | mobile | 5 | 0 % |
| ☐ | S2-05 | Connexion par email | US-02 | mobile | 3 | 0 % |
| ☐ | S2-06 | Connexion Google (Credential Manager) | US-03 | mobile | 5 | 0 % |
| ☐ | S2-07 | Session persistante, de bout en bout | US-04 | mobile + tv | 3 | 0 % |
| ☐ | S2-08 | Ajout de source : choix, formulaires, aide | US-06, US-07 | mobile | 8 | 0 % |
| ☐ | S2-09 | États de la source : validation, succès, quatre erreurs | US-06, US-07 | mobile | 5 | 0 % |
| ☐ | S2-10 | Liste des chaînes : catégories, pagination, hors ligne | US-08 | mobile | 8 | 0 % |
| ☐ | S2-11 | Lecteur mobile | US-09 | mobile | 8 | 0 % |
| ☐ | S2-12 | Activation TV : code, QR, polling | US-05 | tv | 8 | 0 % |
| ☐ | S2-13 | Accueil et grille TV, carte du parcours de focus | US-08 | tv | 8 | 0 % |
| ☐ | S2-14 | Lecteur TV | US-10 | tv | 5 | 0 % |

**Avancement du sprint : 0 % de 82 points.** Rien n'est commencé.

### Lot serveur — livré, hors périmètre du sprint

Compté à part, exprès : ce sont les treize manques d'API relevés sur les
maquettes, pas du travail Android. Les mélanger fausserait l'estimation qui
compte, celle des quinze tâches ci-dessus.

Le détail et la justification de chaque ligne sont dans
[`design/api-gaps.md`](../design/api-gaps.md).

| | Id | Tâche | Ferme | Cible | Points | Avancement |
|---|---|---|---|---|---|---|
| ☑ | SRV-01 | Contrat : sept manques web (G1 → G7) | maquettes W1→W4 | contrat | 5 | 100 % |
| ☑ | SRV-02 | Contrat : six manques mobile et TV (M1 → M6) | maquettes | contrat | 5 | 100 % |
| ☑ | SRV-03 | `account` : `/me`, devices, `is_current`, révocation | G6 | api | 3 | 100 % |
| ☑ | SRV-04 | `billing` : entitlement, essai, quotas et leurs deux codes | G1, G2 | api | 5 | 100 % |
| ☑ | SRV-05 | `userdata` : favoris, groupes, progression | G7 | api | 5 | 100 % |
| ☑ | SRV-06 | Chaînes récentes : table, fenêtre glissante, endpoints | M5 | api | 3 | 100 % |
| ☑ | SRV-07 | Diagnostic de source : `sync_step`, `last_error_at`, `category_count` | M1, M2, G5 | api | 3 | 100 % |
| ☑ | SRV-08 | Ingestion : numéro et qualité de chaîne | M3, M4 | api | 2 | 100 % |
| ☑ | SRV-09 | Resynchronisation automatique honorant `auto_sync` | M6 | api | 2 | 100 % |
| ◩ | SRV-10 | Stripe : checkout, portail, client de facturation | G3 | api | 5 | **80 %** |

**Lot serveur : 96 % de 38 points.** Les 4 % manquants sont SRV-10 : le webhook
qui accorderait l'abonnement est absent du contrat, donc non écrit (AGENTS.md
§3). Ouvrir une session de paiement fonctionne ; **un paiement réussi ne change
encore rien**. C'est une décision à prendre, pas un oubli.

---

### S2-00 — Aligner les tokens Android sur la charte · **2** · ferme S0-07

`LumoTokens.kt` porte une palette qui n'est pas Spectre : un bleu froid
`#4CB8FF`, une encre `#07090F`, des rayons 8/12/20. La direction arrêtée en
passe 1 dit `#0D0C12`, cyan `#6EE7F0`, violet `#A78BFA`, rayons 8/14/20, focus
en outline cyan. Le web a été aligné le 26 août ; Android non.

À faire **avant le premier écran**, sinon dix écrans sont construits sur la
mauvaise palette et la reprise coûte dix fois plus.

Ce sont des changements de valeurs, pas de structure : `LumoColors`,
`LumoSpacing`, `LumoShapes`, `LumoFocus` gardent leurs noms et leurs types.

**Un point à trancher, pas à décider seul.** La charte impose Sora ;
`LumoTypography.kt` utilise délibérément la police système, avec un argument
écrit noir sur blanc — embarquer une famille coûte un téléchargement d'APK sur
une box TV pour une différence que personne ne voit à trois mètres. Les deux
positions se défendent. Trancher avant d'ouvrir la tâche.

---

### S2-01 — `core:data` : repositories et erreurs typées · **8**

Le module qui manque. Il expose aux features des repositories (`SourceRepository`,
`CatalogueRepository`, `PlaybackRepository`, `AccountRepository`) et **une seule
traduction des erreurs**.

Le point difficile est là : le contrat impose de brancher sur `Problem.code` et sur
rien d'autre, et de dégrader proprement sur un code inconnu. Une réponse d'erreur
arrive de Retrofit comme un corps non désérialisé ; le convertir en
`ErrorCode` typé, une fois, dans ce module, est ce qui évite que huit écrans
inventent huit `catch`. Prévoir explicitement le cas `else ->` : depuis la dernière
modification du contrat, **toute énumération peut gagner une valeur en v1**, et un
`when` exhaustif sur un enum généré compile aujourd'hui et lève le mois prochain.

Le cache Room est branché ici, pas dans les écrans : le repository décide s'il sert
le réseau ou la base, et expose l'information « ces données viennent du cache » que
US-08 doit afficher.

**Tests** : traduction de chaque `IngestionErrorCode`, code inconnu, corps illisible,
réseau coupé.

---

### S2-02 — Navigation pilotée par la session, périmètre réduit · **3**

Deux choses, petites et bloquantes.

`LumoMobileNavHost` démarre en dur sur l'onboarding, et son propre commentaire dit
quoi faire : la destination initiale se décide sur `SessionManager.isSignedIn` —
connecté sans source → ajout de source ; connecté avec source → liste des chaînes ;
non connecté → onboarding.

Et la barre de navigation propose ses huit destinations « tant que le produit est un
échafaudage ». VOD, séries et recherche sont **hors périmètre de la verticale**. Les
retirer de la barre : un écran de démo qui offre trois portes fermées se commente
tout seul, et mal.

---

### S2-03 — Banc d'essai des sources · **3**

Sans lui, la recette de US-06 et US-07 n'est pas exécutable : on ne peut pas
provoquer un refus d'identifiants ou une playlist vide en tapant une vraie URL.

Un serveur local — statique ou conteneur de test — servant, sur des chemins fixes :
une playlist M3U valide pointant des flux **libres de droits** (Big Buck Bunny, flux
de test Apple HLS, assets de test Mux), une playlist vide, une réponse qui n'est pas
du M3U, un panel Xtream renvoyant 401, un hôte qui ne répond jamais, et une réponse
au-delà du plafond de taille.

Interdit, et c'est le point : **aucune source réelle, aucun nom de chaîne, aucun logo
de bouquet**, y compris dans ce banc (AGENTS.md §1). Le job `no-content` de la CI
refuse le dépôt sinon.

---

### S2-04 — Inscription · **5** · ferme US-01

Email et mot de passe. La règle non respectée s'affiche **avant** la soumission et le
bouton reste désactivé — c'est écrit dans le Gherkin et c'est ce qui distingue cet
écran d'un formulaire ordinaire. Force mesurée par entropie, pas par composition.

Erreur à soigner : email déjà enregistré. Le message invite à se connecter ou à
réinitialiser, sans jamais confirmer que l'adresse existe.

---

### S2-05 — Connexion par email · **3** · ferme US-02

Message générique sur identifiants invalides. Après cinq échecs le serveur renvoie
`429` avec `Retry-After` : l'écran l'affiche comme une attente, pas comme une panne.

---

### S2-06 — Connexion Google · **5** · ferme US-03

Credential Manager. Le client envoie l'`id_token` et **rien d'autre** — jamais un
email : le serveur vérifie signature, `aud`, `iss` et `exp` lui-même. Le rattachement
à un compte existant est déjà géré côté serveur ; l'écran n'a pas à le détecter.

---

### S2-07 — Session persistante, de bout en bout · **3** · ferme US-04

La mécanique existe et est testée. Ce qui manque est la démonstration : ouvrir une
session depuis un écran réel, tuer l'application, la rouvrir connectée. Et le cas qui
casse en production : un refresh **refusé** déconnecte, un refresh **indisponible**
(API injoignable) ne touche à rien.

---

### S2-08 — Ajout de source · **8** · ferme US-06, US-07

L'écran le plus important de l'application, et le plus facile à rater. Choix M3U /
Xtream, puis le formulaire correspondant.

Il doit être compréhensible par quelqu'un qui ne sait pas ce qu'est un M3U : de
l'aide contextuelle, pas seulement des champs. L'hôte Xtream est accepté avec ou sans
schéma, avec ou sans port, avec ou sans slash final — le serveur normalise, l'écran
ne rejette pas. URL EPG en champ séparé, facultative.

Le mot de passe Xtream ne s'affiche jamais et ne revient jamais de l'API, y compris
en édition : le formulaire garde l'hôte et l'utilisateur, et vide le mot de passe.

---

### S2-09 — États de la source · **5** · ferme US-06, US-07

L'ingestion est asynchrone : `POST /sources` répond `202` en `PENDING`, l'écran poll
`GET /sources/{id}` jusqu'à `READY` ou `ERROR`.

Quatre erreurs, **quatre messages distincts et quatre sorties distinctes** :
`SOURCE_AUTH_FAILED` (le formulaire garde l'hôte), `SOURCE_UNREACHABLE` (bouton
Réessayer, et le message ne doit pas pouvoir se confondre avec un refus
d'identifiants), `SOURCE_INVALID_FORMAT` (expliquer à quoi ressemble une URL M3U
correcte), `SOURCE_EMPTY` (jamais d'écran de succès sur une liste vide).

Succès : nombre de chaînes trouvées, et date d'expiration du compte Xtream quand le
panel la donne.

---

### S2-10 — Liste des chaînes · **8** · ferme US-08

Catégories, puis chaînes, avec le nombre de chaînes par catégorie. Paging 3 sur
`CataloguePager`, qui existe déjà — la liste doit rester fluide au-delà de 500
chaînes.

Hors ligne : la liste vient du cache Room et un indicateur discret le signale. Les
logos sont ceux de la playlist de l'utilisateur (`tvg-logo`) ; Lumo n'embarque aucune
image de repli.

---

### S2-11 — Lecteur mobile · **8** · ferme US-09

Media3. L'URL de flux est demandée **à la volée** à `GET /channels/{id}/playback`,
une chaîne à la fois. Elle ne s'écrit dans aucun log, à aucun niveau, et ne survit pas
à la session de lecture.

Plein écran, rotation sans interruption, focus audio, appel entrant, coupure réseau,
wake lock. Deux erreurs à traiter nommément : flux indisponible — message clair et
bouton Réessayer, jamais un écran noir muet — et limite de connexions simultanées
atteinte, que `PlaybackInfo.max_connections` permet d'expliquer.

---

### S2-12 — Activation TV · **8** · ferme US-05

Le code à 8 caractères et son QR, très grands, avec l'instruction « rendez-vous sur
lumo.tv/activate ». Le QR encode `verification_uri_complete`, donc `?code=…` : le
chemin nominal ne comporte aucune saisie.

Polling à l'`interval` renvoyé par le serveur. `AUTHORIZATION_PENDING` est la réponse
**nominale** et ne s'affiche jamais comme une erreur ; `SLOW_DOWN` ajoute cinq
secondes. À expiration, la télé demande un nouveau code et l'affiche **sans
intervention** — l'utilisateur, lui, est peut-être encore devant son téléphone.

---

### S2-13 — Accueil et grille TV · **8** · ferme US-08

Rails horizontaux, pas des listes verticales. Le focus est le curseur : il combine
échelle, bordure et élévation, jamais une simple variation de couleur, indistinguable
sur un téléviseur mal calibré. Overscan de 5 % sur les quatre bords. Corps de texte
≥ 24 px.

**Livrable en plus des écrans : la carte du parcours de focus.** Pour chaque écran,
quel élément a le focus à l'arrivée et où mène chaque direction depuis chaque zone.
C'est ce document qui évite le défaut le plus courant des applications TV, un bouton
qu'aucune séquence de touches n'atteint.

---

### S2-14 — Lecteur TV · **5** · ferme US-10

État de repos sans aucun overlay. OK fait apparaître une barre d'information avec le
nom de la chaîne, qui disparaît après cinq secondes d'inactivité. BACK revient à la
liste, **positionné sur la chaîne qu'on regardait**.

---

## Récapitulatif

| Bloc | Tâches | Points |
|---|---|---|
| Socle et outillage | S2-00 → S2-03 | 16 |
| Authentification | S2-04 → S2-07 | 16 |
| Sources | S2-08, S2-09 | 13 |
| Catalogue et lecture mobile | S2-10, S2-11 | 16 |
| Télévision | S2-12 → S2-14 | 21 |
| **Total** | **15 tâches** | **82** |

**82 points contre 63 au sprint 1, sur une seule plateforme.** C'est trop pour un
sprint si l'équipe n'a pas doublé. La coupure naturelle est nette et elle est
proposée telle quelle :

- **Sprint 2a — le téléphone** : S2-00 → S2-11, **61 points**. Se démontre seul, et
  ferme sept stories.
- **Sprint 2b — la télévision** : S2-12 → S2-14, **21 points**. Dépend du socle de
  2a et de rien d'autre.

**Ordre de réalisation** — les dépendances comptent plus que les priorités :

```
S2-00 → S2-01 → S2-02 → S2-05 → S2-04 → S2-07 → S2-03 → S2-08 → S2-09 → S2-10 → S2-11
                                                 → S2-06
                                     S2-12 → S2-13 → S2-14
```

S2-01 avant tout : chaque écran écrit avant lui sera à reprendre. S2-05 avant S2-04
parce que se connecter est plus simple que s'inscrire et valide le chemin complet
avec moins de surface. S2-06 se glisse n'importe où après S2-05. Les trois tâches TV
n'ont besoin que du socle, et peuvent partir en parallèle du bloc source dès que
S2-02 est en place.

## Hors périmètre

Explicitement, pour que la question ne se repose pas en cours de route :

- **L'implémentation de G1 → G7** (quotas, essai, Stripe, lecture de progression,
  `/me`, appareils, favoris). Le contrat les expose, personne ne les implémente, et
  aucune story du sprint 1 n'en a besoin. Voir [`../design/api-gaps.md`](../design/api-gaps.md).
- **Les écrans web du design** (W1 → W4 : tarifs, FAQ, espace compte en écriture).
  Voir [`../design/web-sprint-1.md`](../design/web-sprint-1.md).
- **VOD, séries, recherche.** Les modules existent, ils restent des placeholders et
  sortent de la barre de navigation (S2-02).
- Tout ce que l'AGENTS.md §6 range en v2 : Chromecast, PiP, timeshift, enregistrement,
  multi-profils, contrôle parental, Stalker, Play Billing.
