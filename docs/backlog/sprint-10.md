# Sprint 10 — Recherche unifiée

Statut : **cycle ouvert**, du 1er au 7 octobre 2026 (validé par Hamza le 30 septembre 2026). Sélection `Todo` : `S10-00` → `S10-05` + `US-021`, affectée au cycle Plane « Sprint 10 — Recherche unifiée ». `S10-00` (contrat `q`) démarre en premier ; l'ordre des lots suivants est fixé par le Tech Lead. Taille relative : M.
Référence : [plan et DoD commune](../roadmap/0.2.0/delivery-plan.md).

[Proposition d’écrans](../design/0.2.0/unified-search.md) préparée le 19 septembre
2026 pour les trois surfaces ; relecture visuelle à effectuer.
Les [règles Q9](../design/0.2.0/search-interactions.md) précisent maintenant
les choix d’interaction et les cas SR-01 à SR-15 ; recette non exécutée.

## Cycle 10 — ouvert le 30 septembre 2026

Objectif validé par Hamza : **une saisie retrouve chaînes, films et séries de la
source active, sur les trois surfaces.** Sélection `Todo` affectée au cycle Plane
« Sprint 10 — Recherche unifiée » (01/10 → 07/10/2026) : `S10-00` à `S10-05`, plus
la story `US-021`. `S10-00` passe en premier et clôt la vérification de Q9 ; le
Tech Lead découpe et briefe @Dev tâche par tâche. La recette `S10-05` (télécommande
réelle, deux sources) rejoint la liste matérielle déjà ouverte en S9-07 : la DoD
commune n'est pas levée pour `US-021` sans elle.

## Objectif

Une saisie retrouve chaînes, films et séries de la source active sur les trois surfaces.
Story : [US-021](stories/US-021-unified-search.md). Dépend de S8 ; Q9 cadré,
capacités réelles et cohérence de la description contractuelle à vérifier.
Le guide S9 n'est pas un prérequis technique. Les opérations `q` existantes suffisent
au périmètre nominal ; tout manque découvert est remonté avant extension du contrat.

## Tâches proposées

| ID | Travail | Surface |
|---|---|---|
| S10-00 | Vérifier les choix Q9 : 350 ms, aperçu de 4, pages de 20, changement de source et clavier de plateforme ; contrôler parité et description contractuelle | Design/clients |
| S10-01 | Composer les recherches existantes, annuler/ignorer les réponses obsolètes, préserver la sémantique partielle et la casse | Android/web |
| S10-02 | Construire les sections/filtres Tous, Chaînes, Films, Séries et Voir tous | Trois clients |
| S10-03 | Brancher lecture directe, fiches et retour avec saisie/filtre/position conservés | Trois clients |
| S10-04 | Traiter champ vide, aucun résultat, erreur partielle, hors ligne ; aucune conservation d'historique | Trois clients |
| S10-05 | Recette avec gros catalogue neutre et saisie rapide, clavier/télécommande, deux sources | Toutes |

Découpe détaillée (modules, dépendances, unités de PR, points d'attention) et
vérification contractuelle `q` : [US-021 § Tâches](stories/US-021-unified-search.md).
Parité vérifiée le 30 septembre 2026 : les trois listes appliquent la même
sous-chaîne insensible à la casse ; la description `q` des chaînes (« typo-tolerant »)
est à corriger en S10-00. La recherche web passe par une route BFF serveur, le jeton
d'accès étant httpOnly.

## Démo et sortie

Rechercher un fragment avec une casse différente, filtrer un type, ouvrir une fiche
puis retrouver sa place. Lancer une chaîne et revenir ; changer de source pendant
une saisie sans voir de résultats de l'ancienne. Provoquer l'échec d'une section
sans masquer les autres, puis réessayer. Le champ vide ne charge pas tout le catalogue.
Tests pertinents : composition, réponses tardives et pagination ; checks Android/web.
US-021 se clôture après recette sur les trois surfaces.

## Avancement

Mis à jour le 4 octobre 2026. Une case cochée signifie **recetté**, pas seulement écrit.

- [x] S10-00 — contrat `q` : description des chaînes corrigée (sous-chaîne insensible à la casse, `pg_trgm` accélère sans approximer), test d'intégration `q` séries ajouté ; **Q9 `Done`**. Décision du 30/09.
- [x] S10-01 — composer les recherches existantes, annuler les réponses obsolètes, préserver la sémantique partielle et la casse ; Android + web.
- [x] S10-02 — sections/filtres Tous, Chaînes, Films, Séries et « Voir tous » sur les trois clients.
- [x] S10-03 — lecture directe, fiches et retour avec saisie/filtre/position conservés, sur les trois surfaces ; fusionné dans `main` (dont PR #19, TV).
- [ ] S10-04 — états vide / sans résultat / erreur partielle / hors ligne : web `0c38784`, mobile `9652e82`, TV `dc0f9d2` approuvés par le Tech Lead. **Correctif D-pad TV** `fix/S10-04-tv-search-dpad` @ `93fff2f` (`BUG-S10-05-01`) poussé, revu, **re-recette TV due**. Note franche : `SearchTvFocusTest` est un `androidTest` non lancé par la CI — il ne remplace pas la recette TV.
- [ ] S10-05 — recette : **web + mobile conformes** (`qa-evidence/s10-05-mobile-2026-10-04/`, réserves SR-14 sans cache global et pagination non exerçable) ; **TV partielle** — point ouvert `BUG-S10-05-01`, SR-10/SR-12 TV à rejouer après correctif (`s10-05-tv-2026-10-04/`). **S10-05 n'est pas terminal.**

Point de sortie hors périmètre d'une story : la CI `web` de `main` est encore le run rouge. `fix/e2e-bench-anchor` @ `546dbdd` (test-only, vert en local) n'est **pas** fusionné dans `main`, et aucun run web vert sur PR n'existe à ce jour (`gh` absent côté agents ; ouverture de PR à la main par Hamza). Cause non corrigée dans `main` tant que ce n'est pas mergé.
