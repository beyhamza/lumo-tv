# docs/design — spécifications d'interface

Ce dossier est la **spécification écrite** des écrans de Lumo. Il ne remplace pas
les maquettes : il dit ce qu'elles veulent dire, en particulier là où un pixel ne
peut pas le dire — quel champ d'API alimente quel libellé, quels états existent,
ce qui se passe quand la donnée manque.

## Sources

| Quoi | Où | Autorité |
|---|---|---|
| Maquettes | Projet Claude Design `176c9cb0-5745-4a29-8e3e-5e95b98e346d` | Source visuelle |
| Tokens | `lumo-tokens.json` dans ce projet | Source des valeurs de design |
| Contrat d'API | `packages/contracts/openapi.yaml` | Source de vérité de l'API (AGENTS.md §3) |
| Cette documentation | `docs/design/` | Source de la **traduction** maquette → implémentation |

Les artboards du projet Design sont découpés en quatre fichiers :
`Lumo - Directions`, `Lumo - Mobile Sprint 1`, `Lumo - TV Sprint 1`,
`Lumo - Web Sprint 1`. Seul le dernier est documenté ici pour l'instant.

## Index

| Document | Contenu |
|---|---|
| [`design-system.md`](./design-system.md) | Direction « Spectre » : palette, typographie, espacement, élévation, dérivations mobile / TV / web |
| [`web-sprint-1.md`](./web-sprint-1.md) | Écrans W1 → W4 (landing, gabarit de guide, espace compte, `/activate`), états compris |
| [`api-gaps.md`](./api-gaps.md) | Ce que ces écrans impliquent côté API et que `openapi.yaml` ne couvre pas |

## Règles qui s'appliquent à toute maquette

Rappelées ici parce qu'elles se violent en maquette avant de se violer en code :

1. **Aucun contenu réel.** Aucun nom de chaîne, aucun logo de bouquet, aucune URL
   de flux — dans les captures produit de la landing comme dans les fixtures
   (AGENTS.md §1). Les emplacements de capture des maquettes sont des
   placeholders hachurés ; ils le restent jusqu'à ce qu'une capture neutre
   existe.
2. **Aucune chaîne en dur.** Tout libellé visible ici a une entrée dans
   `apps/web/src/messages/fr.json` **et** `en.json` (AGENTS.md §4).
3. **Les droits d'accès viennent du serveur.** Une maquette qui affiche
   « illimité » ou « 2 appareils » décrit une valeur que le client **lit**, jamais
   une valeur qu'il **décide** (AGENTS.md §1.3). Voir `api-gaps.md` G1.
4. **Le contrat ne se modifie pas depuis une maquette.** Quand la maquette
   demande une donnée absente d'`openapi.yaml`, elle entre dans `api-gaps.md` et
   attend un lot de modifications explicite (AGENTS.md §9).
