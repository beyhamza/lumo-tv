# US-021 — Rechercher dans ma source active

Statut : parcours validé le 17 septembre 2026, détails d'interaction à préparer.
Version cible : 0.2.0. Surfaces : web, Android mobile, Android TV, même priorité.

## Besoin

En tant qu'utilisateur, je veux rechercher une chaîne, un film ou une série depuis
un seul champ, afin de retrouver rapidement un élément de ma source active.

## Critères d'acceptation validés

- La recherche porte sur le nom des chaînes et le titre des films et séries,
  uniquement dans la source active, dont le nom reste visible.
- Une partie du nom ou du titre suffit ; majuscules et minuscules sont équivalentes.
- Tous présente des résultats regroupés en Chaînes, Films et Séries.
- Les filtres Tous, Chaînes, Films et Séries sont proposés selon les types de contenu
  disponibles dans la source. Un type présent dans la source mais sans correspondance
  pour la requête ne doit pas être confondu avec un type absent du catalogue.
- Voir tous les résultats ouvre la liste du type concerné en conservant la recherche.
- Sélectionner une chaîne lance immédiatement le direct ; sélectionner un film ou
  une série ouvre sa fiche avec accès à la lecture ou à la reprise.
- Au retour d'une fiche ou du lecteur, conserver recherche, filtre et position.
- Les résultats s'actualisent automatiquement après une courte pause dans la saisie.
- Champ vide : afficher une invitation à rechercher, sans charger tout le catalogue.
- Sans résultat : rappeler le texte recherché et la source active, proposer d'effacer.
- En cas d'erreur partielle : garder les résultats disponibles, indiquer la section
  en erreur et proposer de réessayer cette section. Une erreur n'est pas un résultat vide.
- Aucun historique de recherches n'est conservé. Le retour de navigation conserve
  néanmoins la recherche en cours.
- Sur mobile, l'accès se situe en haut d'Explorer ; sur TV/web, dans la navigation
  principale. Sur TV, saisie au clavier à l'écran et accès aux résultats au D-pad.
- Libellés FR/EN, navigation au clavier sur le web et à la télécommande sur TV.

## Couverture contractuelle

Les opérations existantes de liste de chaînes, films et séries par source portent
le paramètre `q` et sont paginées dans `packages/contracts/openapi.yaml`.
La recherche est une correspondance partielle insensible à la casse ; elle ne
promet ni correction des fautes ni classement par pertinence.

Une présentation unifiée peut composer ces recherches existantes. Aucun endpoint
nouveau n'est défini ici. L'implémentation devra vérifier la parité des recherches
locales Android avec la sémantique contractuelle.

## Avant planification

- Définir le délai de saisie, le nombre de résultats par section et la pagination.
- Préciser le focus initial et le passage clavier/résultats sur TV.
- Définir le comportement lors d'un changement de source pendant la recherche.
- Vérifier que la réponse d'une ancienne saisie ne remplace pas la réponse courante.
- Préparer les états de chargement, hors ligne et de source indisponible.

## Recette à préparer

Avec des données de banc neutres, couvrir les trois types, le filtrage par source,
les recherches partielles et la casse. Vérifier le champ vide, aucun résultat,
une section en erreur, la saisie rapide, le retour du lecteur et d'une fiche.
Prévoir une recette sur les trois surfaces, avec télécommande réelle pour la TV.

## Hors périmètre de cette story

Recherche par description, acteur, épisode ou programme EPG ; recherche entre
plusieurs sources ; historique persistant. Toute extension demande un nouveau cadrage.

Référence : [décisions produit](../../roadmap/0.2.0/decisions.md).
