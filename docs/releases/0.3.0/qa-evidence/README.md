# Preuves de recette QA — 0.3.0

Même principe que pour la [0.2.0](../../0.2.0/qa-evidence/README.md) : les preuves de recette
vivent **dans le dépôt**, versionnées, et non dans un chemin d'espace de travail local
(décision produit du 26 septembre 2026, voir
[`docs/backlog/DECISIONS-PRODUIT.md`](../../../backlog/DECISIONS-PRODUIT.md)).

## Règles

- **Un sous-dossier par passage** (`<lot>-<objet>-<date>/`) : ex. `s11-06-recette-2026-10-15/`.
  On garde le **passage final** d'un correctif, pas les essais intermédiaires remplacés ; ils sont
  listés comme écartés dans `INDEX.md` avec la raison.
- Chaque passage a un `RAPPORT.md` : objet, base (`dev` @ SHA), environnement, comptes (jamais
  d'identifiants ni de jetons : ils restent dans `apps/api/.env.recette`, gitignoré), verdict par cas
  (**Conforme** / **Non conforme** `BUG-…` / **Non joué** + raison), écarts, observations, non joué.
- **Captures** dans `ecrans/` : uniquement celles qui portent un verdict, nommées par cas
  (`r08-bug01-…png`). PNG ≤ ~1 Mio quand c'est possible. Pas de vidéo, pas d'APK, pas de doublon.
- **Contenu neutre uniquement** (AGENTS.md §1) : banc `lumo-bench` (« Chaîne 01 FHD », « Le Voyage »,
  mire de test), jamais de chaîne, logo ou URL de flux réels — captures comprises.
- Tout fichier **écarté** est nommé dans `INDEX.md` (fichier, taille, raison).
- `INDEX.md` (tenu par @QA) : une ligne par passage — objet, SHA, verdict, liens vers le rapport et
  les captures décisives.

## Index des passages

Voir [`INDEX.md`](INDEX.md).
