# Manques d'API impliqués par les écrans web du sprint 1

**Statut : liste, pas modification.** `packages/contracts/openapi.yaml` n'a pas
été touché. Ce document est le lot de modifications proposé, à instruire
explicitement (AGENTS.md §3 et §9).

Périmètre analysé : `Lumo - Web Sprint 1.dc.html` (écrans W1 → W4), confronté à
`openapi.yaml` v1.0.0. Les écrans mobile et TV n'ont pas été passés au crible ;
G7 les concerne néanmoins de plein fouet.

## Récapitulatif

| # | Écran | Manque | Nature | Priorité |
|---|---|---|---|---|
| G1 | W1 tarifs, W3 sources et appareils | Quotas de l'offre + codes de dépassement | Champs + `ErrorCode` | Haute |
| G2 | W1 « Essayer 14 jours », W3 abonnement | État d'essai | Enum + champ | Haute |
| G3 | W1 tarifs, W3 abonnement, FAQ | Souscription et gestion de l'abonnement | Deux endpoints | Haute |
| G4 | W4 succès | L'appareil approuvé n'est pas renvoyé | Réponse d'opération | Haute |
| G5 | W3 source en erreur | Depuis quand la source échoue | Champ | Moyenne |
| G6 | W3 aperçu appareils | Quel appareil est celui qui consulte | Champ | Moyenne |
| G7 | W1 argument « reprise multi-écrans » | Aucune lecture de la progression | Endpoint | Haute |

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
| `POST /billing/checkout-session` | `{ url }` — Stripe Checkout, corps portant le plan visé et les URL de retour |
| `POST /billing/portal-session` | `{ url }` — Stripe Customer Portal : changement de moyen de paiement, factures, annulation |

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

**Ce qui manque.** L'opération répond `204 No Content`. La page n'a aucun moyen
de citer l'appareil. C'est précisément ce que l'implémentation livrée
contourne, avec un message générique : « C'est fait. Votre téléviseur se
connecte dans quelques secondes. »

Faire passer la réponse nominale de `204` à `200` avec le `Device` approuvé
(directement, ou enveloppé dans un `DeviceApproval { device }` si l'on veut se
laisser la place d'y ajouter autre chose).

**Sécurité.** `Device` ne contient aucun secret : ni `device_code`, ni token,
ni identifiant de source. Le renvoyer ici n'expose rien de plus que
`GET /me/devices`, que l'appelant — authentifié, et propriétaire du compte —
peut appeler dans la seconde qui suit.

**Pourquoi ça vaut le coup.** C'est le seul moment du parcours où l'utilisateur
peut vérifier qu'il a associé **la bonne télé**. Un message générique après
avoir tapé un code lu de travers, c'est un doute qui se termine en ticket de
support.

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

Ajouter :

- `GET /me/progress` — liste paginée, filtrable par `item_type`, triée par
  `updated_at` décroissant (c'est aussi ce qui alimenterait un rail
  « Reprendre ») ;
- et/ou `GET /me/progress/{item_type}/{item_ref}` — lecture unitaire, pour
  qu'un lecteur qui ouvre un item n'ait pas à rapatrier toute la liste.

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
