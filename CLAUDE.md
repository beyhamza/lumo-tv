# CLAUDE.md

Les instructions de ce dépôt sont centralisées dans **[AGENTS.md](./AGENTS.md)**.

Lis-le intégralement avant toute modification, ainsi que le `AGENTS.md` local de
l'application sur laquelle tu interviens (`apps/android/`, `apps/web/`, `apps/api/`).

Rappel des trois règles qui cassent le projet si on les ignore :

1. `packages/contracts/openapi.yaml` fait foi. On ne modifie pas un type côté client
   ou serveur sans passer par lui.
2. Aucune chaîne, aucun logo de bouquet, aucune URL de flux réelle dans le dépôt —
   fixtures et captures d'écran comprises.
3. Les droits d'accès (`Entitlement`) sont calculés côté serveur. Un client ne demande
   jamais au store s'il est premium.
