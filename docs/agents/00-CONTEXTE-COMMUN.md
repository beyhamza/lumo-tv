# Lumo TV — contexte commun à tous les bots

> Ce fichier est lu par les quatre bots. Il ne contient que des faits stables.
> Tout ce qui bouge (backlog, décisions de sprint) vit dans le monorepo, pas ici.

## Le produit
- Lecteur IPTV multi-plateforme, modèle « bring your own playlist » : l'utilisateur apporte sa propre source (M3U / Xtream), Lumo ne fournit aucun contenu.
- Cibles : Android mobile, Android TV, web (lumo.tv).
- Propriétaire du produit : Hamza. Il est le seul décideur final ; les bots proposent, il tranche.

## Stack
- Backend : Java 25 (virtual threads), Spring Boot 4.1.x, PostgreSQL, Liquibase (changesets SQL formatés).
- Web : Next.js.
- Mobile : Expo (React Native).
- Android TV : <!-- À COMPLÉTER par Hamza : Expo TV ? Kotlin/Compose for TV ? -->
- Infra : Docker / Docker Compose (dev sous Windows, rien d'installé hors Docker), Kubernetes en cible.

## Où trouver quoi dans le monorepo
- `docs/adr/` — décisions d'architecture. Une ADR acceptée fait loi ; on ne la contredit pas sans en écrire une nouvelle.
- `docs/backlog/` — stories et sprints (propriété du PO).
- `docs/agents/` — prompts d'initialisation et souls (ce dossier).
- <!-- À COMPLÉTER : chemins réels des modules backend / web / mobile / tv -->

## Artefacts qui circulent entre bots
| Artefact | Produit par | Consommé par | Format |
|---|---|---|---|
| Story | PO | Tech lead, QA | `docs/backlog/stories/LUMO-xxx.md` (template ci-dessous) + issue Plane LUMO-xxx |
| Tâche technique | Tech lead | Dev | Sous-issue Plane + section « Tâches » dans la story |
| Cycle | PO | Tous | Cycle Plane + `docs/backlog/cycles/CYCLE-NN.md` en clôture |
| PR | Dev | Tech lead (review), QA (test) | Branche `feat/LUMO-xxx-slug`, PR liée à la story |
| Rapport de test | QA | PO, Tech lead | Commentaire sur la PR + `docs/backlog/stories/LUMO-xxx.md#QA` |
| ADR | Tech lead | Tous | `docs/adr/NNNN-titre.md` |

Les bots communiquent **par ces fichiers**, pas par messages libres. Si une info n'est pas dans un fichier, elle n'existe pas.

## Plane — suivi des tâches et des cycles
Plane tourne en local : `http://localhost:8585/lumo-tv` (accès via l'API REST avec la clé fournie dans l'environnement du bot, ou via le navigateur du bot).

**Règle de source de vérité, à ne jamais contourner :**
- `docs/` = le **contenu** (texte des stories, critères d'acceptation, ADR, rapports QA détaillés).
- Plane = le **suivi** (état, cycle, assignation, sous-tâches, commentaires courts).
- Une story = un fichier `docs/backlog/stories/LUMO-xxx.md` **et** une issue Plane du même identifiant. L'issue contient le titre, les plateformes en labels, un lien vers le fichier, et les critères d'acceptation copiés dans la description. Si les deux divergent, le fichier `docs/` a raison et l'issue est corrigée.
- Les tâches techniques du tech lead sont des **sous-issues** de la story dans Plane, reprises en liste dans la section « Tâches » du fichier.

**États des issues (dans cet ordre, sans en sauter) :**
`Backlog` → `Todo` (découpée, prête) → `In Progress` (dev) → `In Review` (tech lead) → `QA` → `Done`
Seul le bot responsable de l'étape fait avancer l'état : dev vers In Progress / In Review, tech lead vers QA, QA vers Done ou retour à In Progress.

**Cycles (sprints Plane) :**
- Durée : 1 semaine. Un cycle = un objectif en une phrase, écrit par le PO dans la description du cycle.
- Le PO ouvre le cycle N+1 pendant le cycle N avec les stories `Todo`, en respectant la capacité fixée par Hamza (nombre de stories, pas de points).
- Une story non terminée en fin de cycle est reportée explicitement au cycle suivant avec un commentaire « pourquoi ». Rien ne glisse en silence.
- En fin de cycle, le PO écrit `docs/backlog/cycles/CYCLE-NN.md` : objectif, fait / pas fait, décisions, ce qu'on change pour le suivant.

## Template de story
```
# LUMO-xxx — Titre
Plateformes : [backend | web | mobile | tv]
## En tant que … je veux … afin de …
## Critères d'acceptation (Given / When / Then)
## Hors périmètre
## Tâches (rempli par le tech lead)
## QA (rempli par le QA)
```

## Règles non négociables (tous les bots)
- Jamais de push sur `main`. Tout passe par PR.
- Jamais de migration Liquibase modifiée après merge : on en ajoute une nouvelle.
- Jamais de secret en dur, jamais de log de token ou d'URL de playlist utilisateur.
- Toute action irréversible (suppression, migration en prod, dépense) = demander l'approbation de Hamza.
- Une seule story à la fois par bot. On termine avant de commencer.
- Quand tu ne sais pas, tu poses la question dans le fichier concerné et tu t'arrêtes. Tu n'inventes pas.
