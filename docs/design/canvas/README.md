# Canevas Claude Design

Export du projet **Lumo TV — Direction artistique**
(`176c9cb0-5745-4a29-8e3e-5e95b98e346d`), le 26 août 2026.

Ce sont les maquettes elles-mêmes, versionnées ici pour qu'on puisse les ouvrir
sans compte et voir ce qu'un écran donné disait à une date donnée. La
spécification écrite, elle, est dans le dossier parent : `design-system.md`
pour la charte, `web-sprint-1.md` pour les écrans, `api-gaps.md` pour ce que
tout cela implique côté API.

| Fichier | Contenu |
|---|---|
| `Lumo - Directions.dc.html` | Passe 1 : les trois directions artistiques comparées, le logotype, les marques (icône, favicon, bannières), les tokens |
| `Lumo - Mobile Sprint 1.dc.html` | Écrans 1 à 8 : onboarding, inscription, connexion, ajout de source, états de la source, liste des chaînes, lecteur, réglages |
| `Lumo - TV Sprint 1.dc.html` | Splash, activation, accueil en rails, grille, lecteur, réglages, états — **et la carte du parcours de focus** |
| `Lumo - Web Sprint 1.dc.html` | Écrans 9 à 12 : landing, gabarit de guide, espace compte, `/activate` |
| `lumo-tokens.json` | La source unique des valeurs de design. Aucune valeur ne doit être redéfinie ailleurs |
| `support.js` | Runtime de rendu du canevas. **Généré, jamais édité à la main.** Sans lui les maquettes s'affichent empilées au lieu d'être posées sur un plan pan/zoom |

Fichiers **conservés à l'octet près** tels que le projet les exporte. Deux
conséquences : ne pas les reformater, et corriger une maquette se fait dans
Claude Design puis se réexporte — pas l'inverse, sinon les deux divergent en
silence.

Le `.thumbnail` de l'export n'est pas versionné : c'est une image blanche vide.

## Contenu : conforme

Relus intégralement avant versionnement (AGENTS.md §1). Aucun nom de chaîne
réel, aucun logo de bouquet, aucune URL de flux, aucun identifiant. Les
placeholders sont « Chaîne 01 » à « Chaîne 12 », des programmes génériques
(« Journal du soir », « Documentaire — Épisode 3 »), `exemple.com` et
`utilisateur-0000`. La bannière Play l'écrit même en toutes lettres : « aucune
capture d'écran, aucun contenu simulé ».

## Ce que ces maquettes montrent et qui est **hors périmètre v1**

À lire avant d'implémenter depuis le mobile ou la TV. Ces éléments sont dessinés
et ne doivent pas être construits : AGENTS.md §6 les range en v2.

| Élément dessiné | Où | Statut |
|---|---|---|
| Timeshift sur le direct (`−30`/`+30`, « −12 min du direct ») | lecteur mobile et TV | **v2** |
| « Diffuser » (Chromecast) | lecteur mobile | **v2** |

## Ce que ces maquettes demandent à l'API

Le reste de l'écart entre ces maquettes et le contrat est analysé dans
[`../api-gaps.md`](../api-gaps.md), **lot 2** — six manques `M1` → `M6`, chacun
avec sa justification, plus dix besoins apparents écartés avec le raisonnement.
Le contrat n'a pas été modifié pour ce lot.

Les deux plus visibles depuis ces fichiers : l'ingestion n'expose qu'un statut
là où l'écran 5 du mobile montre quatre étapes nommées, et le rail
« Reprendre » de la TV mélange une progression de VOD — couverte — avec des
chaînes en direct, qui n'ont aucun équivalent côté serveur.

## Un défaut connu

Le canevas web contient un lien vers `Lumo — Mobile Sprint 1.dc.html`, avec un
tiret cadratin, alors que le fichier s'appelle `Lumo - Mobile Sprint 1.dc.html`.
Le lien ne résout pas. Corrigé dans Claude Design il se réexportera d'ici ;
corrigé ici il divergerait de la source.
