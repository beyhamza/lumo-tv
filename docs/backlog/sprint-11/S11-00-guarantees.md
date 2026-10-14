# S11-00 — Garanties Q3/Q8 (référentiel normatif)

Date : 8 octobre 2026. Statut : **normatif**, porté par le `@Tech Lead`. Développement
et recette des garanties non commencés.
Références : [US-022](../stories/US-022-library-watchlist.md),
[ordre par source et retraits partiels](../../design/0.2.0/favorite-organization-cases.md) (FO-01→12),
[états, indisponibilité, hors ligne et conflits](../../design/0.2.0/watchlist-states.md) (WL-01→17),
[sprint 11](../sprint-11.md).

Ce document ne décrit pas un écran : il fixe ce qu'une implémentation **doit**
garantir, et ce qu'elle **ne garantit pas** aujourd'hui. Il est la référence de
revue : une PR qui ne peut pas le citer ne peut pas être approuvée sur ces points.
Le document de design (WL/FO) reste la propriété du `@PO` ; il n'est pas modifié ici.

## 1. Ce que le contrat actuel porte déjà (vérifié le 8 octobre 2026)

`packages/contracts/openapi.yaml`, opération `updateFavorite` :
`position` est l'index du favori **dans le groupe complet**, compté à partir de
zéro ; les favoris déplacés glissent ; les positions d'un groupe sont toujours
**contiguës** ; un déplacement est une seule opération, jamais un
retirer-puis-ajouter. `GET /me/favorites` répond trié par groupe puis `position`.

Conséquences que le découpage doit accepter telles quelles :

- Réordonner la **vue filtrée d'une source** est la composition d'une sous-séquence
  dans un groupe complet : ce n'est **pas** un `PATCH` unique.
- Il n'existe **ni** clé d'idempotence **ni** endpoint de permutation par lot.

## 2. Garanties testables sans évolution de contrat (S11-00)

| ID | Garantie | Énoncé testable | Cas | Où le test vit |
|---|---|---|---|---|
| G1 | Permutation filtrée = fonction pure | Le passage de la position visible à l'index de groupe complet est une fonction pure `visibleSlot → fullIndex`, projetée sur la sous-séquence de la source active. Depuis `[A1,B1,A2,B2,A3]`, viser `[A3,B1,A1,B2,A2]` laisse **B1/B2 à leurs index 1 et 3**. Test unitaire, déterministe, sans base ni réseau. | FO-01, FO-02, FO-03, FO-04 | API (unitaire) |
| G2 | Échec intermédiaire / retrait partiel | Sur une séquence de moves ou un retrait global multi-appartenances qui échoue au milieu : les opérations **réussies sont conservées**, l'appartenance restante est affichée, le réessai ne porte **que** sur ce qui reste, aucune opération réussie n'est rejouée, et le résultat n'est **jamais** annoncé comme un succès global. Après échec, l'état est **relu** avant toute nouvelle action. | FO-05, FO-06, FO-07, FO-08 | API (intégration) |
| G3 | Concurrence = dernière action acceptée serveur | Entre deux **nouvelles intentions**, l'appartenance est celle de la dernière action **acceptée par le serveur**, par favori. L'ordre de départage est l'ordre d'acceptation serveur, **jamais l'horloge d'un appareil**. Un résultat inconnu (réponse perdue) n'autorise ni à annoncer la réussite ni à écraser une action concurrente : relire d'abord. | FO-11, FO-12, WL-09 | API (intégration) |
| G4 | Disparition, panne, hors ligne, suppression de source | Quatre faits distincts, jamais confondus : (a) **disparition confirmée après actualisation** → la carte est conservée, marquée `Indisponible`, retrait manuel accessible **sans charger la fiche** ; (b) **erreur réseau** → ne prouve pas une disparition ; (c) **hors ligne** → consultation seule, ajout/retrait indisponibles, **aucune file d'écritures différées**, relecture de l'état partagé à la reconnexion ; (d) **suppression de source** → cascade de retrait (US-024), la carte **part**, elle ne devient pas `Indisponible`. | WL-01, WL-02, WL-03, WL-04, WL-05, WL-06, WL-07, WL-08, WL-13→WL-17 | API (intégration) + recette appareil |
| G5 | Lecture ≠ retrait | Commencer ou terminer une lecture ne retire jamais l'élément de la liste, et ne change pas son rang d'ajout. | WL-13 | API (intégration) |

**Non-garantie explicite, à écrire dans les tests plutôt qu'à supposer :** la
séquence de moves de G1 n'est **pas atomique**. Un échec au milieu laisse un groupe
**valide mais partiellement permuté**. C'est le comportement produit retenu ; il n'y
a pas de rollback côté serveur et aucun batch n'est promis.

## 3. Ce qui n'est pas garanti aujourd'hui — renvoyé à l'ADR 0011

« Un réessai technique n'est pas une nouvelle intention » (WL-10, WL-11, WL-12)
**n'est pas exprimable avec le contrat actuel**. Sans clé d'idempotence :

- rejouer un ajout après une réponse perdue peut créer une **nouvelle** intention
  et déplacer le rang d'ajout (WL-11) ;
- un réessai retardé peut s'appliquer par-dessus une action concurrente plus
  récente (WL-12) ;
- une réponse perdue après une action peut-être acceptée reste un résultat
  indéterminé sans règle de relecture normative (WL-10).

Ces trois cas restent **« garantie à définir »** et ne sont pas automatisés comme
des garanties. Le mécanisme (clé d'idempotence, a minima) est tranché par
[ADR 0011](../../adr/0011-watchlist-protocol.md), en `Proposed`.

## 4. Découpage (rappel du 8 octobre 2026)

- `@Tech Lead` : ce référentiel + ADR 0011. Les garanties sont **normatives** ;
  celui qui les écrit n'est pas celui qui les implémente, sinon la revue ne
  contrôle plus rien.
- `@Dev` : la **fixture multi-sources** et les tests de G1→G3 et G5, sans toucher au
  contrat. Elle sert aussi à `@QA`.
- `@QA` : le plan de test S11 contre des critères **0.3.0**, G4 par appareil,
  G1→G3 côté API.
- `S11-01` et l'approbation C2 **après** l'accord de Hamza sur l'ADR 0011 ;
  `S11-04` ne branche rien avant.
