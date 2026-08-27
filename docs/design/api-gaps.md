# Manques d'API impliqués par les maquettes du sprint 1

Deux lots, portés dans le contrat **et servis par le serveur**.

| Lot | Écrans | Contrat | `apps/api` |
|---|---|---|---|
| **G1 → G7** | web (W1 → W4) | porté | servi, une réserve sur G3 |
| **M1 → M6** | mobile et TV | porté | servi |

Ce document garde la justification de chaque ajout — le contrat dit *quoi*,
celui-ci dit *pourquoi*, et c'est cette moitié-là qui manque le jour où
quelqu'un veut retirer un champ.

---

## État d'implémentation

Une ligne par manque. Le détail de chacun est dans sa section.

| # | Manque | Implémenté | Où |
|---|---|---|---|
| G1 | Quotas de l'offre + codes de dépassement | ✅ 100 % | `billing/EntitlementService`, `lumo.plans.*` |
| G2 | Essai (`TRIALING`, `trial_ends_at`) | ✅ 100 % en lecture | `0012`, `EntitlementService` |
| G3 | Souscription et portail Stripe | ⚠️ **80 %** | `billing/BillingController`, `StripeClient` |
| G4 | `POST /auth/device/approve` renvoie l'appareil | ✅ 100 % | `DeviceActivationService` |
| G5 | `Source.last_error_at` | ✅ 100 % | `0010`, `SourceRepository` |
| G6 | `Device.is_current` | ✅ 100 % | `auth/AccountController` |
| G7 | `GET /me/progress` | ✅ 100 % | `userdata/ProgressRepository` |
| M1 | `Source.sync_step` | ✅ 100 % | `0010`, `IngestionService` |
| M2 | `Source.category_count` | ✅ 100 % | `SourceRepository.countCategories` |
| M3 | `Channel.number` | ✅ 100 % | `0011`, `ChannelQuality.parseNumber` |
| M4 | `Channel.quality` | ✅ 100 % | `0011`, `ChannelQuality.detect` |
| M5 | Chaînes récentes | ✅ 100 % | `0013`, `userdata/RecentChannelRepository` |
| M6 | `Source.auto_sync` | ✅ 100 % | `0009`, balayage dans `IngestionService` |

Les trois tags qui n'avaient aucun contrôleur — `account`, `userdata`,
`billing` — en ont un. Plus aucun chemin du contrat ne répond 404.

### Quatre points à trancher, remontés plutôt qu'inventés

**1. Le webhook de paiement n'existe pas.** `POST /billing/checkout-session`
ouvre bien une session Stripe et crée le client, mais **un paiement réussi ne
change rien** : l'endpoint qui recevrait le rappel du prestataire est absent de
`openapi.yaml`, et l'AGENTS.md §3 dit qu'un besoin que le contrat ne couvre pas
s'escalade au lieu de s'inventer. C'est la réserve des 20 % sur G3. Décision à
prendre : ajouter `POST /billing/webhook` au contrat, ou l'exclure explicitement
comme surface non publique.

**2. Le groupe de favoris par défaut n'a pas d'identifiant stable.** Créé au
premier ajout, il faut bien le nommer, et le serveur le nomme `Favorites` — une
chaîne visible par l'utilisateur, dans une seule langue. Le même problème s'était
posé pour les entrées M3U sans `group-title` et avait été résolu par une sentinelle
(`m3u:__unclassified__`) sur laquelle le client traduit. `FavoriteGroup` ne porte
que `id`, `name` et `position` : aucune sentinelle possible. Décision à prendre :
ajouter un champ au schéma, ou accepter que le client renomme.

**3. `display_name: null` ne peut pas effacer.** Le contrat dit qu'un `null`
explicite efface le nom affiché. Le modèle généré porte un `String` nu, qui ne
distingue pas « envoyé à null » de « pas envoyé » — il faudrait `openApiNullable`
dans `packages/contracts/config/spring.yaml`, donc régénérer tous les clients et
casser tous les mappeurs existants. Implémenté en attendant : absent et `null`
valent tous deux « inchangé ». La lecture inverse effacerait le nom de
l'utilisateur à chaque fois qu'un client modifie sa langue.

**4. Un client sans catalogue local ne peut pas nommer une chaîne.** ✅ **Tranché,
porté au contrat et servi.** Relevé en
écrivant S3-08. `Favorite` et `RecentChannel` ne portent que des identifiants —
`channel_id`, `source_id` — et le contrat dit pourquoi : dénormaliser le nom
ferait afficher au rail un nom que la dernière ingestion a changé depuis. Le
raisonnement tient pour Android, qui résout l'identifiant dans sa base Room.

Le web n'a pas de catalogue local, et n'a aucun moyen de nommer ces
identifiants : il n'existe aucune opération rendant une chaîne par son id, et
`GET /sources/{id}/channels` cherche par nom (`q`), pas par id. Nommer dix
entrées de rail voudrait dire parcourir tout le catalogue de la source — quinze
mille chaînes est ordinaire — ce que l'écran est précisément construit pour ne
pas faire.

Deux options étaient sur la table :

| Option | Coût | Effet |
|---|---|---|
| **`ids` (répétable) sur `GET /sources/{id}/channels`** | un paramètre, un `uuid[]` | un appel nomme tout un rail, et servira la reprise VOD |
| `channel` en expansion sur `Favorite` et `RecentChannel` | change deux schémas déjà servis | supprime l'aller-retour, mais réintroduit le nom dénormalisé que le contrat refuse |

**Retenu : `ids`.** Elle conserve la position du contrat — les identifiants
restent des identifiants, et le nom continue de venir du catalogue, qui est le
seul endroit où il est à jour.

Trois points fixés avec elle, parce qu'ils décident du comportement réel :

- **elle compose**, elle ne remplace pas : `categoryId`, `q` et `ids` narguent le
  même résultat ensemble ;
- **elle ne réordonne pas** : l'appelant détient l'ordre qu'il veut — la position
  d'un favori, la date d'un visionnage — et trie lui-même. Faire honorer l'ordre
  du paramètre donnerait deux ordres différents à la même opération selon le
  filtre utilisé ;
- **un identifiant inconnu est absent, pas une erreur.** Une chaîne que la
  dernière resynchronisation a retirée, une chaîne d'un autre compte : même
  réponse, rien. Un 404 transformerait un favori obsolète en écran cassé, et
  laisserait sonder l'existence d'identifiants qui ne vous appartiennent pas.

Bornée à cent : c'est une résolution pour un rail, pas un export du catalogue —
la forme paginée reste la façon de lire un catalogue.

Servi par `catalog/CatalogReadRepository`, couvert par
`catalog/ChannelLookupIntegrationTest`. S3-08 est fermé.

---

# Lot 1 — les écrans web

**Statut : traité, contrat et serveur.** Les sept manques ont été portés dans
`packages/contracts/openapi.yaml`, les trois clients régénérés, et `apps/api`
les sert — avec la réserve sur G3 décrite plus haut : le webhook qui accorderait
l'abonnement n'est pas dans le contrat.

> **G4 a changé de forme à l'implémentation.** La spécification d'origine
> disait « renvoyer le `Device` approuvé ». Le compilateur a montré qu'il
> n'existe pas encore à cet instant. Voir la section G4.

Périmètre analysé : `Lumo - Web Sprint 1.dc.html` (écrans W1 → W4), confronté à
`openapi.yaml` v1.0.0. Le lot mobile et TV est plus bas.

## Récapitulatif

| # | Écran | Manque | Porté dans le contrat |
|---|---|---|---|
| G1 | W1 tarifs, W3 sources et appareils | Quotas de l'offre + codes de dépassement | `Entitlement.max_sources`, `max_devices` ; `SOURCE_LIMIT_REACHED`, `DEVICE_LIMIT_REACHED` |
| G2 | W1 « Essayer 14 jours », W3 abonnement | État d'essai | `EntitlementStatus.TRIALING`, `Entitlement.trial_ends_at` |
| G3 | W1 tarifs, W3 abonnement, FAQ | Souscription et gestion de l'abonnement | `POST /billing/checkout-session`, `POST /billing/portal-session` |
| G4 | W4 succès | L'appareil approuvé n'est pas renvoyé | `POST /auth/device/approve` → `200 DeviceApproval` |
| G5 | W3 source en erreur | Depuis quand la source échoue | `Source.last_error_at` |
| G6 | W3 aperçu appareils | Quel appareil est celui qui consulte | `Device.is_current` |
| G7 | W1 argument « reprise multi-écrans » | Aucune lecture de la progression | `GET /me/progress` |

---

## G1 — Quotas de l'offre, et codes de dépassement

**Écrans.** W1, grille de tarifs : « 1 source, 2 appareils » contre « Sources et
appareils illimités ». W3, bouton `Ajouter une source` et panneau appareils.

**Donnée nécessaire.** Combien de sources et d'appareils l'offre courante
autorise, et combien sont consommés.

**Ce qui manque.** `Entitlement` ne porte que `plan`, `status`, `provider`,
`current_period_end`, `updated_at`. Rien sur les limites.

Ajouter sur `Entitlement` :

| Champ | Type | Sémantique |
|---|---|---|
| `max_sources` | `integer` nullable | `null` = illimité |
| `max_devices` | `integer` nullable | `null` = illimité |

**Corollaire, tout aussi nécessaire.** `ErrorCode` n'a aucun code de
dépassement. Un `POST /sources` refusé pour quota retombe sur `CONFLICT`
générique, indistinguable d'un doublon, et l'écran ne peut ni expliquer ni
proposer la sortie (« Passer à Plus »). Ajouter :

- `SOURCE_LIMIT_REACHED` — `409` sur `POST /sources` ;
- `DEVICE_LIMIT_REACHED` — `409` sur les opérations qui créent un `device`
  (`/auth/register`, `/auth/login`, `/auth/oauth/google`, `/auth/device/token`).

**Pourquoi ça ne peut pas rester côté client.** Coder « FREE = 1 source » dans
le web, l'app mobile et l'app TV, c'est trois copies d'un droit d'accès, et
c'est exactement ce que la règle non négociable n°3 interdit. Le jour où l'offre
gratuite passe à deux sources, une seule valeur doit changer, côté serveur.

---

## G2 — État d'essai

**Écrans.** W1, CTA `Essayer 14 jours`. W3, onglet Abonnement.

**Donnée nécessaire.** Savoir que l'utilisateur est en période d'essai, et
quand elle se termine.

**Ce qui manque.** `EntitlementStatus` vaut `ACTIVE | PAST_DUE | CANCELED |
EXPIRED`. Un utilisateur en essai est donc `PREMIUM` / `ACTIVE`, strictement
indistinguable d'un abonné payant. Or « votre essai se termine dans 3 jours »
et « votre abonnement se renouvelle le 14 » ne sont ni le même message, ni la
même urgence, ni le même bouton.

Ajouter :

- la valeur `TRIALING` à `EntitlementStatus` ;
- `Entitlement.trial_ends_at` (`date-time`, nullable).

`current_period_end` ne suffit pas : il désigne la fin de la période **payée**.

**Note de compatibilité.** Ajouter une valeur d'énumération est un changement
que les clients doivent absorber sans planter — le contrat le dit déjà pour
`ErrorCode`, il ne le dit pas pour `EntitlementStatus`. À décider en même
temps : soit la règle « toute énumération peut s'étendre en v1 » devient
générale, soit `TRIALING` attend une v2.

---

## G3 — Souscrire et gérer l'abonnement

**Écrans.** W1, CTA `Essayer 14 jours` et `Commencer`. W3, onglet Abonnement.
W1, FAQ « Puis-je annuler à tout moment ? ».

**Donnée nécessaire.** Une URL vers laquelle rediriger pour souscrire, et une
pour gérer ou annuler.

**Ce qui manque.** Le contrat n'a aucune opération de facturation. ADR 0003
retient Stripe ; `apps/web/AGENTS.md` §10 constate le trou noir sur blanc
(« la page abonnement lit `GET /me/entitlement` et n'ouvre aucune session de
paiement »).

Ajouter, sous un tag `billing` :

| Opération | Réponse |
|---|---|
| `POST /billing/checkout-session` | `BillingSession { url }` — Stripe Checkout, corps portant le plan visé |
| `POST /billing/portal-session` | `BillingSession { url }` — Stripe Customer Portal : moyen de paiement, factures, annulation |

**Les URL de retour ne sont pas acceptées du client.** La première rédaction de
ce document les mettait dans le corps de la requête ; c'est une redirection
ouverte déguisée en facturation, et ce projet garde déjà exactement ce trou sur
le paramètre `next` de la connexion. Elles sont construites côté serveur depuis
la configuration. Le seul champ que l'appelant influence est `locale`, et il ne
choisit que la langue de rendu de Stripe.

`BillingSession.url` est typée `format: password`, comme
`PlaybackInfo.stream_url` et pour la même raison : c'est une capacité au
porteur — sur le portail, elle donne les factures, le moyen de paiement et le
bouton d'annulation — et le typage force les générateurs à la masquer dans
`toString()`.

Le portail Stripe couvre l'annulation, la reprise et l'historique de factures
sans qu'aucun de ces trois écrans n'ait à exister chez nous. C'est le meilleur
rapport surface / valeur du lot.

**Hors de ce contrat.** Le webhook entrant de Stripe n'est appelé par aucun
client Lumo ; il n'a rien à faire dans `openapi.yaml`. Il se documente côté
`apps/api`.

---

## G4 — `POST /auth/device/approve` ne renvoie rien

**Écran.** W4, état de succès : « "TV du salon" est maintenant reliée à votre
compte. »

**Donnée nécessaire.** Le nom de l'appareil qui vient d'être associé — et,
tant qu'à faire, sa plateforme et son modèle.

**Ce qui manquait.** L'opération répondait `204 No Content`. La page n'avait
aucun moyen de citer l'appareil, d'où le message générique de l'implémentation
livrée : « C'est fait. Votre téléviseur se connecte dans quelques secondes. »

**Retenu :** `200` avec un **`DeviceApproval`**, et non avec un `Device`.

**Pourquoi pas un `Device`.** La spécification d'origine, écrite en lisant le
seul contrat, demandait le `Device` approuvé. La compilation d'`apps/api` l'a
réfutée en une ligne : à l'instant de l'approbation, **aucune ligne `device`
n'existe**. Elle est créée quand le prochain sondage de la télé réussit, dans
`DeviceActivationService.consume()`. La créer plus tôt aurait un coût réel :
toute approbation dont le téléviseur est ensuite éteint laisserait une
installation fantôme, comptée dans le quota de G1 et listée comme un appareil
qui ne s'est jamais connecté.

`DeviceApproval` porte donc ce que la télé a **déclaré** d'elle-même en
demandant le code — `platform`, `name`, `model`, `app_version`, exactement le
contenu de `DeviceCodeRequest`. C'est assez pour nommer un poste sur un écran de
confirmation, et jamais assez pour servir d'identité : ces champs viennent du
client et ne sont pas vérifiés.

**Sécurité.** Aucun secret : ni `device_code`, ni token, ni identifiant de
source. Rien de plus que ce que l'appelant — authentifié, propriétaire du
compte — obtient de `GET /me/devices` la seconde d'après.

**Pourquoi ça vaut le coup.** C'est le seul moment du parcours où l'utilisateur
peut vérifier qu'il a associé **la bonne télé**. Un message générique après
avoir tapé un code lu de travers, c'est un doute qui se termine en ticket de
support.

**Implémenté.** `apps/api` renvoie le `DeviceApproval`, et
`DeviceActivationIntegrationTest` vérifie les deux moitiés : le nom revient, et
aucune ligne `device` n'existe avant le sondage.

---

## G5 — Depuis quand une source est en erreur

**Écran.** W3, ligne de source en erreur : « Identifiants refusés **depuis
hier** — le mot de passe a peut-être changé. »

**Donnée nécessaire.** L'ancienneté de l'échec.

**Ce qui manque.** `Source` porte `last_synced_at`, et rien d'autre de daté.
Ce champ est déjà consommé par la ligne saine, pour « vérifiée il y a 2 h » : il
ne peut pas simultanément dater la dernière réussite et le dernier échec.

Ajouter `Source.last_error_at` (`date-time`, nullable, non nul seulement quand
`status` vaut `ERROR`), ou — plus propre mais plus intrusif — distinguer
explicitement `last_attempted_at` de `last_synced_at`.

**Pourquoi la date compte.** « Identifiants refusés » sans ancienneté ne dit pas
si l'utilisateur a raté deux heures ou deux semaines de télévision. C'est ce qui
fait la différence entre une notification qu'on ignore et une action qu'on mène.

---

## G6 — Identifier l'appareil courant

**Écran.** W3, panneau « Appareils — aperçu » : « Navigateur — **ce poste** ».

**Donnée nécessaire.** Lequel des appareils listés est celui qui fait l'appel.

**Ce qui manque.** `Device` n'a pas de marqueur. `AuthSession.device_id` existe,
mais n'est renvoyé qu'à la connexion : le web devrait le recopier dans son
cookie de session puis le comparer, ce qui déplace côté client une information
que le serveur connaît déjà à chaque requête, et qui devient fausse dès qu'une
session est reprise autrement.

Ajouter `Device.is_current` (`boolean`), positionné par le serveur sur l'élément
correspondant au token présenté.

**Pourquoi ça n'est pas cosmétique.** C'est la ligne qu'il ne faut pas révoquer
par erreur. Un `DELETE /me/devices/{id}` sur son propre appareil est autorisé
par le contrat et équivaut à une déconnexion — l'utilisateur doit voir laquelle
c'est **avant** de cliquer, pas après.

---

## G7 — Aucune lecture de la progression

**Écran.** W1, argument de vente de l'offre Plus : « Reprise de lecture
multi-écrans ». En aval : les lecteurs mobile et TV.

**Donnée nécessaire.** Relire, sur un écran, la progression écrite depuis un
autre.

**Ce qui manque.** Le contrat expose `PUT /me/progress` et **rien d'autre**.
Aucune opération de lecture. En l'état, la progression s'écrit et ne se relit
jamais : la fonctionnalité mise en avant sur la landing est invendable, et
`PlaybackProgress` n'est renvoyé que comme écho de sa propre écriture.

**Retenu :** `GET /me/progress` — page triée par `updated_at` décroissant
(l'ordre qu'un rail « Reprendre » veut de toute façon), filtrable par `itemType`
et `itemRef`. Renvoie un `PlaybackProgressPage`, même enveloppe de pagination
que `ChannelPage` : une seule à apprendre.

**Pas de `GET /me/progress/{item_type}/{item_ref}`**, contrairement à ce que
cette liste proposait d'abord. `item_ref` est un identifiant opaque frappé par
le panel de l'utilisateur ; rien ne lui interdit de contenir une barre oblique
ou un pourcent. Passer les deux filtres en paramètres de requête donne la même
lecture unitaire — au plus un élément — là où l'encodage est sans ambiguïté,
plutôt que dans un segment de chemin où il ne l'est pas.

**Réserve de périmètre.** La progression ne concerne que `VOD` et `EPISODE` — le
direct n'en a pas — et le sprint 1 est explicitement « pas de VOD, pas de
séries ». Le manque est donc réel mais pas bloquant pour la verticale en cours ;
il l'est pour la promesse affichée sur la landing. À arbitrer : soit on ajoute
la lecture, soit on retire l'argument de la grille tarifaire tant qu'il n'est
pas tenu.

---

## Ce qui n'est **pas** un manque

Passé au crible et volontairement écarté, pour que la question ne se repose pas :

| Besoin apparent | Verdict |
|---|---|
| Prix, devise, périodicité, liste des avantages (W1) | Contenu marketing. La zone est SSG et ne peut pas appeler l'API. Vit dans les messages i18n |
| FAQ (W1) | Contenu statique, accordéon `<details>` sans JavaScript |
| Sommaire, temps de lecture, date de mise à jour, précédent / suivant (W2) | Contenu, dérivé au build depuis `src/content/guides.ts` |
| `Contact`, « Assistance prioritaire » (W1) | `mailto:` ou outil externe. Aucun endpoint de support en v1 |
| `en ligne` / `actif` sur un appareil (W3) | `is_current` (G6) + un seuil de rendu sur `last_seen_at`. **Pas** de champ `is_online` : rien ne notifie une déconnexion, il serait faux la moitié du temps |
| Durée de vie du code d'activation (W4) | Constante produit, dans les messages FR et EN. Voir la divergence D2 dans [`web-sprint-1.md`](./web-sprint-1.md) |
| Liens de téléchargement des applications (W1) | Statiques |
| Redirection `login?next=/activate?code=…` (W4) | Routage web, déjà couvert par la garde `nextPath` |

## Divergences, à ne pas confondre avec des manques — **résolues**

Deux points où la maquette **contredisait** le contrat plutôt que de dépasser
son périmètre. Tranchés en faveur du contrat, maquette corrigée. Détail dans
[`web-sprint-1.md`](./web-sprint-1.md), section « Divergences maquette ↔
contrat » :

- **D1** — code d'activation à 6 caractères sur la maquette, 8 dans le contrat
  (et dans l'implémentation livrée) → **8, en 4 + 4**.
- **D2** — durée de vie annoncée à 5 minutes sur la maquette, 10 partout
  ailleurs → **10 minutes**.

Ni l'une ni l'autre n'a entraîné de modification d'`openapi.yaml`.

---

# Lot 2 — les écrans mobile et TV

**Statut : traité.** Les six manques sont dans `openapi.yaml`, les trois
clients régénérés.

Les six sont maintenant servis par `apps/api`. M6 l'avait été d'emblée, pour la
même raison que G4 : `auto_sync` est requis sur `Source`, donc le constructeur
généré a changé et le serveur a cessé de compiler. Un champ qui se lit
correctement et ignore silencieusement les écritures étant pire que pas de champ
du tout, il est allé jusqu'au bout — colonne, lecture, écriture.

**M1 a changé de forme à l'implémentation.** Ce document, comme
`docs/domain-model.md`, disait que `sync_step` ne serait pas une colonne mais un
état connu du travail de fond. Faux dès qu'il y a plus d'une instance : le client
interroge `GET /sources/{id}` sans aucune garantie de tomber sur celle qui ingère,
donc l'étape serait visible d'un appel sur deux — une checklist qui recule, ce qui
est pire que pas de checklist. C'est une colonne, avec une contrainte `CHECK` qui
la force à rester nulle hors `SYNCING`.

Périmètre analysé : `Lumo - Mobile Sprint 1.dc.html` (écrans 1 → 8) et
`Lumo - TV Sprint 1.dc.html` (splash, activation, accueil, grille, lecteur,
réglages, états), confrontés au contrat après le lot 1.

Deux éléments dessinés dans ces maquettes sont **hors périmètre v1** et ne
figurent donc pas ici : le timeshift sur le direct et la diffusion Chromecast,
que l'AGENTS.md §6 range en v2. Voir [`canvas/README.md`](./canvas/README.md).

## Récapitulatif

| # | Écran | Manque | Porté dans le contrat |
|---|---|---|---|
| M1 | Mobile 5 — validation | L'ingestion n'expose qu'un statut, la maquette montre quatre étapes | `Source.sync_step` |
| M2 | Mobile 5 — succès | « 96 catégories » | `Source.category_count` |
| M3 | Mobile 6, TV grille | Le numéro de chaîne (`001`…) | `Channel.number` |
| M4 | Mobile 6, TV grille et aperçu | Le badge de qualité (`HD`, `SD`, `4K`) | `Channel.quality` |
| M5 | TV accueil — rail « Reprendre » | Les chaînes regardées récemment | `GET`/`PUT /me/recent-channels` |
| M6 | Mobile 8, TV réglages | « Actualisation automatique » | `Source.auto_sync` |

---

## M1 — L'ingestion n'a qu'un statut, l'écran montre quatre étapes

**Écran.** Mobile 5, « Vérification en cours… » : *Connexion au serveur* →
*Identifiants acceptés* → *Lecture de la liste des chaînes* → *Récupération du
programme TV*, cochées au fur et à mesure, avec la mention « une grande liste
peut prendre jusqu'à une minute ».

**Donnée nécessaire.** Où en est l'ingestion, au-delà de « elle tourne ».

**Ce qui manque.** `SourceStatus` vaut `PENDING | SYNCING | READY | ERROR`.
Pendant `SYNCING`, un client ne peut afficher qu'un indéterminé.

Ajouter `Source.sync_step` (nullable, non nul pendant `SYNCING`), énumération
calquée sur les étapes réelles du serveur :
`CONNECTING`, `AUTHENTICATED`, `PARSING_CHANNELS`, `FETCHING_EPG`.

**Pourquoi ça vaut plus qu'un spinner.** C'est le même raisonnement que les sept
`IngestionErrorCode` : une minute d'attente muette est l'endroit exact où
l'utilisateur conclut que c'est cassé et ferme l'application. Une étape nommée
dit deux choses qu'un indéterminé ne dit pas — que ça avance, et jusqu'où c'est
allé quand ça échoue.

**Réserve.** Les étapes doivent rester celles du serveur, pas une fiction
rassurante. Si l'implémentation ne distingue pas réellement ces quatre phases,
il vaut mieux trois étapes vraies que quatre inventées.

---

## M2 — Le nombre de catégories

**Écran.** Mobile 5, écran de succès : « **1 248** chaînes trouvées ·
**96** catégories · compte valide jusqu'au 12 mars 2027 ».

**Ce qui manque.** `Source.channel_count` existe, dérivé et documenté comme tel.
Son pendant non.

Ajouter `Source.category_count` (`integer`, nullable, dérivé), avec exactement
la même sémantique : nul tant que la première ingestion n'a pas abouti.

Le moins cher du lot, et le seul dont la justification tient en une ligne : les
deux chiffres sont affichés côte à côte, un seul est disponible.

---

## M3 — Le numéro de chaîne

**Écrans.** TV, grille : chaque carte porte `001`, `002`, `004`… Mobile 6, même
information dans la liste.

**Donnée nécessaire.** Le numéro que le fournisseur attribue à la chaîne.

**Ce qui manque.** `Channel` a `position`, qui est un **ordre d'affichage**
interne — l'index dans la source, réattribué à chaque ingestion. Ce n'est pas la
même chose que le numéro que l'utilisateur connaît par cœur et tape sur sa
télécommande, et les deux divergent dès qu'une chaîne disparaît de la playlist.

Ajouter `Channel.number` (`integer`, nullable) : les playlists M3U le portent
dans `tvg-chno`, les panels Xtream dans leur propre champ, et beaucoup de
sources n'en ont pas — d'où nullable.

**Conséquence produit à ne pas manquer.** Sans ce champ, la saisie directe d'un
numéro à la télécommande — le geste le plus ancien de la télévision — n'a rien
sur quoi s'appuyer.

---

## M4 — Le badge de qualité

**Écrans.** Mobile 6 : « Chaîne 01 · Généralistes · **HD** », « Chaîne 05 ·
Sport · **4K** ». TV, aperçu : « 21:00 – 22:00 · Généralistes · **HD** ».

**Ce qui manque.** Rien dans `Channel` ne porte cette information.

Ajouter `Channel.quality` (`string`, nullable) — **et pas une énumération.**
C'est la décision qui compte ici : les sources écrivent ce qu'elles veulent,
`HD`, `FHD`, `UHD`, `4K`, `H265`, parfois dans le nom de la chaîne lui-même. Une
énumération obligerait le serveur à ranger l'inconnu dans une case, donc à
mentir. Une chaîne libre, échouée telle quelle, laisse le client afficher ce que
la source dit et rien de plus.

**Alternative écartée.** Extraire la qualité du nom de la chaîne côté client.
C'est ce que font les lecteurs existants, ça marche une fois sur deux, et ça
produit une chaîne nommée « Cinéma HD Premium » affichée comme « Cinéma
Premium » avec un badge `HD` faux.

---

## M5 — Les chaînes regardées récemment

**Écran.** TV, accueil : le rail **« Reprendre »** mélange un épisode en cours
(« Documentaire — Épisode 3 · 42 min restantes ») et deux chaînes en direct
(« Chaîne 12 — Magazine », « Chaîne 04 — Sport »). La direction artistique
montre le même rail sous le nom « Chaînes récentes ».

**Ce qui manque.** `GET /me/progress` (ajouté au lot 1) couvre la première
carte. Les deux autres, non : `ProgressItemType` vaut `VOD | EPISODE`, et le
contrat écrit noir sur blanc que le direct n'a pas de progression.

**Il ne faut pas ajouter `LIVE` à `ProgressItemType`.** Ce serait une position
de lecture qui ne veut rien dire sur un flux continu, et `position_ms` deviendrait
un champ obligatoire sans valeur possible. Deux concepts distincts partageant une
table parce qu'ils s'affichent dans le même rail est le genre de raccourci qui se
paie six mois plus tard.

Ajouter plutôt un concept à part :

| Opération | Rôle |
|---|---|
| `PUT /me/recent-channels` | Le lecteur signale qu'une chaîne vient d'être regardée |
| `GET /me/recent-channels` | Le rail, trié par date décroissante, borné |

**Pourquoi côté serveur.** Le rail est le premier écran de la télévision, et sa
valeur vient précisément de ce qu'il connaît ce qu'on a regardé sur le
téléphone. Un historique local ne se synchronise pas, donc n'est pas ce rail-là.

---

## M6 — L'actualisation automatique

**Écrans.** Mobile 8, section Sources : bascule « Actualisation automatique ».
TV, réglages : la même, sous « Actualiser les listes maintenant ».

**Ce qui manque.** Aucune préférence de ce type n'existe dans le contrat.

Ajouter `Source.auto_sync` (`boolean`), lisible et modifiable par
`UpdateSourceRequest`.

**Sur la source, pas sur le compte, et surtout pas sur l'appareil.** La
resynchronisation est un travail serveur qui frappe le serveur IPTV de
l'utilisateur ; la décision de le faire ou non appartient à la source concernée
— on peut vouloir rafraîchir une playlist qui bouge et laisser tranquille un
abonnement stable. Rangée sur l'appareil, elle serait à régler trois fois et
ne décrirait de toute façon pas ce que le serveur fait quand aucun appareil
n'est allumé.

---

## Ce qui n'est **pas** un manque — lot mobile et TV

Passé au crible et volontairement écarté, avec le raisonnement, pour que la
question ne se repose pas.

| Besoin apparent | Verdict |
|---|---|
| **« Annuler » pendant la validation** (mobile 5) | `DELETE /sources/{id}` fait exactement ça : la source existe déjà, en `PENDING`. Rien à ajouter — seulement une exigence côté serveur, arrêter le travail de fond au lieu de le laisser courir |
| **Chaîne « Hors ligne »** (mobile 6, TV grille) | **Ne pas ajouter.** Le serveur ne peut le savoir qu'en sondant chaque flux, c'est-à-dire en martelant le serveur du fournisseur — précisément ce que le contrat protège ailleurs (`SOURCE_SYNC_RATE_LIMITED` est décrit comme une sauvegarde produit, pas de capacité). C'est une observation **du client** : il a essayé, ça a échoué, il s'en souvient localement |
| **Sous-titres et sélecteur `1080p`** (lecteur mobile et TV) | Ce sont les pistes du manifeste HLS, que le lecteur lit lui-même — Media3 les expose nativement. Les faire transiter par l'API demanderait de parser le flux côté serveur, donc de le télécharger : exactement ce que « le média ne transite jamais par cette infrastructure » interdit |
| **Force du mot de passe** (mobile 2) | Mesurée côté client avant soumission (US-01), par entropie. Le serveur la revérifie et répond `PASSWORD_TOO_WEAK` ; rien à exposer de plus |
| **« Ressemble à une playlist valide »** (mobile 4) | Indice de forme, purement local. La vraie validation est le `422` de `POST /sources` |
| **Débit et numéro de tentative** (« 2,1 Mb/s · tentative 1/3 ») | Le lecteur les connaît, l'API non |
| **« dernière mise à jour : il y a 18 min »** en hors ligne | Horodatage du cache local, pas de la source |
| **« Lecture sur données mobiles »** (mobile 8) | Préférence de l'appareil, et seulement de lui |
| **« En ce moment » et « Ensuite : »** (TV) | `GET /channels/{id}/epg` avec une fenêtre couvre les deux |
| **Recherche dans les chaînes** (mobile 6) | `GET /sources/{id}/channels` a déjà son paramètre `q` |

---

## Coût du lot 2

Cinq champs et une paire d'opérations. Aucune rupture : tout est nullable ou
nouveau, rien ne change de forme.

| Type | Détail |
|---|---|
| Champs | `Source.sync_step`, `Source.category_count`, `Source.auto_sync`, `Channel.number`, `Channel.quality` |
| Énumération | `SyncStep` — quatre valeurs |
| Opérations | `GET` et `PUT /me/recent-channels` |

M2 et M6 sont les moins chers et se justifient seuls. M5 est le plus structurant
et mérite d'être tranché à part : c'est un concept de domaine nouveau, pas un
champ.
