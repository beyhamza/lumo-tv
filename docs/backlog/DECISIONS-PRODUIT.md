# Décisions produit — journal

Une ligne datée par arbitrage, pour ne jamais reposer la même question deux fois.
Les décisions de version restent dans [`docs/roadmap/0.2.0/decisions.md`](../roadmap/0.2.0/decisions.md) ;
ce journal ne consigne que les **clarifications** tirées d'un arbitrage existant ou
les tranchés pris en cours de sprint, avec leur origine.

| Date | Décision | Origine / portée |
|---|---|---|
| 2026-09-26 | L'accès « Toutes les chaînes » et « Guide TV » doit exister sur l'accueil dès que la source est prête, **sans historique ni favori** ; il ne peut pas dépendre de la présence d'un rail de récentes. | Précision PO dérivée de `roadmap/0.2.0/decisions.md` (« États validés », « Direct sur l'accueil »), US-017 et S9-04-07. Web + Android mobile + TV. |
| 2026-09-26 | Une grille horaire dont les titres, horaires et onglets de jour sont illisibles (tronqués/écrasés) n'est pas acceptable ; la densité web/TV doit être corrigée avant la recette de sortie. | Dérivé des critères S9-05-02 / S9-05-03 (GD-05/06). Forme du correctif : @Tech Lead. |
| 2026-09-26 | Défauts de recette #1 (accès à froid), #2 (grille web), #3 (grille TV), #4 (en-tête TV) = **bugs**, pas des comportements voulus. #5 (« 1 chaînes ») = cosmétique hors S9. | Recette QA du 26 septembre 2026 (rapport `PASS-2026-09-26.md`, désormais sous `docs/releases/0.2.0/qa-evidence/`). |
| 2026-09-26 | Les **preuves de recette QA vivent dans le dépôt**, sous `docs/releases/0.2.0/qa-evidence/<passage>/` : rapports, journaux, dumps et captures décisives, avec un `INDEX.md` par release. Un chemin d'espace de travail local n'est plus une référence valable dans `docs/` (il n'est ni suivi ni reproductible). Les essais intermédiaires remplacés sont écartés et listés dans l'index. | Demande de Hamza (26/09) après le merge des 4 branches. Portée : tous les renvois de `docs/` vers une preuve QA. Tenue de l'index : @QA. |
| 2026-09-26 | **S9-05-04 (journée d'une chaîne, Android mobile) reste dans le périmètre de S9-05** : la story ne part en recette de sortie qu'avec la journée mobile ; c'est la seule surface qui manque à la parité du Guide. Conséquence : S9-05 reste `In Progress` et S9-05-04 est brièffée après les merges. | Décision PO après l'arbitrage Tech Lead du 26/09. Alternative écartée : sortir S9-05-04 de la sortie pour ouvrir la recette de sortie tout de suite. Hamza peut la retenir s'il veut sortir avant. |
