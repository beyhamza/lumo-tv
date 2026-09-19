# Plan de livraison 0.2.0

Proposition du 17 septembre 2026. Périmètre fonctionnel validé ; découpage proposé,
non commencé. Aucun engagement de date ou de points sans capacité d'équipe,
arbitrages contractuels et écrans. Les tailles ci-dessous sont relatives, pas des
durées. Chaque sprint fonctionnel inclut mobile, TV et web.

## Ordre proposé

| Sprint | Résultat démontrable | Stories | Taille relative |
|---|---|---|---|
| [8 — Navigation et sources](../../backlog/sprint-08.md) | Choisir une source, retrouver l'accueil et gérer ses sources | US-017, 018, 024 ; socle 020/025 | L |
| [9 — Direct et guide](../../backlog/sprint-09.md) | Choisir quoi regarder avec le guide sur les trois surfaces | US-16, fin 020 pour l'EPG | XL |
| [10 — Recherche](../../backlog/sprint-10.md) | Trouver une chaîne, un film ou une série depuis une seule saisie | US-021 | M |
| [11 — Bibliothèque](../../backlog/sprint-11.md) | Organiser ses favoris et retrouver sa liste À regarder ailleurs | US-022, fin 020 hors EPG | L |
| [12 — Reprise partagée](../../backlog/sprint-12.md) | Reprendre, masquer et retrouver une lecture entre appareils | US-019, fin 017 | L |
| [13 — Lecteur et réglages](../../backlog/sprint-13.md) | Choisir pistes et qualité, enchaîner les épisodes selon ses préférences | US-023, 025, complément US-15 | XL |
| [14 — Sortie gratuite](../../backlog/sprint-14.md) | Qualifier et préparer la publication de la 0.2.0 | Ensemble du périmètre | M, variable selon défauts |

## Dépendances

- S8 pose source active et navigation ; S9, S10, S11 et S12 s'y raccordent.
- S9 reprend les tâches utiles du sprint 7 : **un seul chantier EPG**, pas deux
  sprints à compter. Les 34 points historiques ne comprennent pas toute la grille TV.
- S11 et S12 ont chacun leur lot contractuel ; S13 reprend les règles de reprise S12.
- S14 exige la clôture des lots fonctionnels et rassemble les preuves déjà produites.
- L'ordre 9 → 10 → 11 → 12 privilégie une progression produit lisible. Hormis les
  dépendances ci-dessus, il n'impose pas de couplage technique entre ces lots.

## Ce que signifie une livraison intermédiaire

S8 assemble l'accueil à partir des données existantes et réserve le complément
EPG pour S9, les gestes complets de bibliothèque pour S11 et les nouvelles règles
de Continuer pour S12. Il ne déclare donc pas US-017 ou US-019 complètement terminées.
Les fonctions futures ne sont pas présentées comme des boutons inopérants.

| Story | Première intégration | Clôture fonctionnelle prévue |
|---|---|---|
| US-017 | S8 | S12, après dépendances S9/S11 |
| US-018 | S8 | S8 |
| US-019 | rails existants en S8 | S12 |
| US-020 | S8 | S11, après EPG S9 |
| US-021 | S10 | S10 |
| US-022 | S11 | S11 |
| US-023 | S13 | S13 |
| US-024 | S8 | S11 pour la cascade de la nouvelle liste À regarder |
| US-025 | rubriques en S8 | S13 |
| US-15 complément | S13 | S13 |
| US-16 complément | S9 | S9 |

## Conditions d'entrée communes

1. Relire la story, la dette, le cadrage design et les AGENTS locaux applicables.
2. Fermer les [arbitrages](open-questions.md) nécessaires au lot, faire valider les
   évolutions contractuelles ; aucune interface client ne devine une donnée absente.
3. Préciser écrans, états et parcours de focus. L'utilisateur demandera les maquettes.
4. Préparer fixtures et environnement de recette avant de coder le parcours.
5. Estimer les tâches après ces étapes. Ni chiffres historiques ni nombre de
   fichiers ne servent de promesse de délai.

## Definition of Done commune

- Résultat utilisable sur les trois surfaces, FR/EN et cas d'erreur compris.
- Logique métier accompagnée de tests ; checks des applications touchées et contrat
  le cas échéant ; marketing web toujours statique.
- Démo sur données autorisées, téléphone réel et TV à la télécommande ; rapport
  comportant résultat, build, appareil et preuves, y compris les cas non joués.
- Pas de secret ou URL sensible exposé dans les journaux, pas de contenu réel ajouté.
- Stories/doc actualisées, commit atomique ; écarts enregistrés sans annoncer une
  story terminée lorsqu'un critère obligatoire n'est pas vérifié.

La [recette de version](../../releases/0.2.0/acceptance.md) précise la clôture finale.
Google, Stripe, paiements et fonctions réservées à la v2 restent hors périmètre.
La gratuité n'autorise aucune modification implicite des quotas ou droits existants.

## Si un lot dépasse la capacité

S9 peut se diviser entre Direct/En ce moment sur les trois surfaces, puis guides
complets. S13 peut se diviser entre audio/sous-titres/qualité, puis enchaînement et
réglages. Conserver à chaque fois les trois surfaces ; renuméroter les futurs lots
si cette division est retenue. Aucune fonction validée n'est retirée automatiquement.

## Prochaine étape

La séquence de propositions visuelles couvre désormais accueil/sources, Direct/Guide,
recherche, bibliothèque, reprise/lecteur et [réglages](../../design/0.2.0/settings.md).
Le [plan de qualification S14](../../releases/0.2.0/execution-plan.md) prépare les
sessions et preuves sans les déclarer exécutées.

Relire les réglages, puis fermer les arbitrages qui conditionnent chaque lot.
L’entrée en réalisation reste S8 avec C4/Q4, les capacités à vérifier et la recette
de départ. Les validations visuelles acquises ne ferment pas les lots contractuels
ni les sprints. Les autres décisions seront traitées avant leur sprint, sans
redemander la validation de chaque règle déjà acquise.
