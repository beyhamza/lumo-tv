# Preuves de recette QA — 0.2.0

Ce dossier rend les preuves de recette **durables et versionnées** : les rapports
QA citaient jusqu'ici un chemin d'espace de travail local, qui n'est ni suivi ni
reproductible. Décision produit du 26 septembre 2026 (voir
[`docs/backlog/DECISIONS-PRODUIT.md`](../../../backlog/DECISIONS-PRODUIT.md)) :
les preuves de recette vivent dans le dépôt.

## Règles

- **Un sous-dossier par passage** (`<sprint>-<objet>-<date>/`) : ex. `s9-07-fixe-215-2026-09-26/`.
  On garde le **passage final** d'un correctif, pas les essais intermédiaires
  remplacés ; ils sont listés comme écartés dans `INDEX.md` avec la raison.
- **Toujours versionnés** : rapports `RECETTE-*.md` / `PASS-*.md`, journaux (`.log`,
  `.txt`), dumps XML/texte, scripts de banc (`.mjs`, `.ts`, `.xml`).
- **Captures** : uniquement les images qui portent le verdict (état avant/après,
  zoom sur le point recetté). Pas de vidéo, pas d'APK, pas de doublon de passe.
  Une capture > 1 Mio n'est gardée que si elle porte le verdict.
- Tout fichier **écarté** est nommé dans `INDEX.md` (fichier, taille, raison) — la
  preuve n'est pas silencieusement perdue.
- `INDEX.md` (tenu par @QA) : une ligne par passage — objet recetté, SHA, verdict,
  lien vers le rapport et vers les captures décisives.

## Index des passages

Voir [`INDEX.md`](INDEX.md).
