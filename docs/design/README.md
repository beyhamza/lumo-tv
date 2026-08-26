# docs/design — spécifications d'interface

Ce dossier est la **spécification écrite** des écrans de Lumo. Il ne remplace pas
les maquettes : il dit ce qu'elles veulent dire, en particulier là où un pixel ne
peut pas le dire — quel champ d'API alimente quel libellé, quels états existent,
ce qui se passe quand la donnée manque.

## Sources

| Quoi | Où | Autorité |
|---|---|---|
| Maquettes | Projet Claude Design `176c9cb0-5745-4a29-8e3e-5e95b98e346d` | Source visuelle |
| Export des maquettes | [`canvas/`](./canvas/) | Copie versionnée, à l'octet près |
| Tokens | [`canvas/lumo-tokens.json`](./canvas/lumo-tokens.json) | Source des valeurs de design |
| Contrat d'API | `packages/contracts/openapi.yaml` | Source de vérité de l'API (AGENTS.md §3) |
| Cette documentation | `docs/design/` | Source de la **traduction** maquette → implémentation |

Les maquettes sont **dans le dépôt** ([`canvas/`](./canvas/)), pour qu'on puisse
les ouvrir sans compte et relire ce qu'un écran disait à une date donnée. Elles
restent une copie : on corrige dans Claude Design, puis on réexporte.

Quatre canevas — `Directions`, `Mobile Sprint 1`, `TV Sprint 1`,
`Web Sprint 1`. Seul le dernier est spécifié par écrit ici pour l'instant ; le
[README du dossier](./canvas/README.md) liste ce que les deux autres montrent et
qui est **hors périmètre v1** — timeshift et Chromecast sont dessinés, et sont
en v2.

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
