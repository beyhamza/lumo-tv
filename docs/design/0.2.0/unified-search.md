# Recherche unifiée — proposition d’écrans 0.2.0

Date : 19 septembre 2026. Statut : proposition visuelle à relire ; réalisation non commencée.

Références : [US-021](../../backlog/stories/US-021-unified-search.md),
[sprint 10](../../backlog/sprint-10.md), [décisions](../../roadmap/0.2.0/decisions.md).

## Base déjà validée

Recherche partielle insensible à la casse sur les noms de chaînes et les titres
des films/séries, dans la source active. Tous regroupe les résultats par type ;
les filtres correspondent aux types réellement présents dans la source. Une chaîne
ouvre la lecture ; un film ou une série ouvre sa fiche. Le retour conserve le
contexte courant, sans historique persistant de recherches.

Ces comportements sont repris de la story et ne sont pas soumis à une nouvelle
validation. La proposition porte sur leur présentation et leurs interactions visuelles.

## Écrans proposés

| Écran | Présentation | Tâches |
|---|---|---|
| S10-E01 Recherche | Source visible, champ unique, filtres Tous/Chaînes/Films/Séries | S10-02 |
| S10-E02 Résultats regroupés | Chaînes en cartes horizontales, films et séries avec visuels de remplacement ; Voir tous par rubrique | S10-02 |
| S10-E03 Liste par type | Recherche conservée, aperçu étendu et Afficher plus | S10-02/03 |
| S10-E04 Champ vide / aucun résultat | Invitation à saisir ; requête et source rappelées en absence de résultat, action Effacer | S10-04 |
| S10-E05 Chargement / erreurs | Chargement explicite ; erreur des films avec réessai local et autres résultats conservés ; hors ligne sans résultat disponible | S10-04 |
| S10-E06 Saisie TV | Clavier de démonstration, Supprimer/Espace et accès explicite aux résultats | S10-00/02 |
| S10-E07 Retour de fiche / lecture | Fiche simplifiée film/série, aperçu de lecture, retour au résultat sélectionné | S10-03 |

Web : navigation principale et résultats au centre. Mobile : recherche située dans
Explorer, chaînes en liste et films/séries sur deux colonnes. TV : textes des cartes
agrandis, navigation directionnelle et clavier à l’écran de démonstration.

Le prototype démarre avec une requête fictive « Démo » pour montrer les trois
rubriques. L’état Champ vide montre l’entrée réelle sans historique. Le scénario
Source avec chaînes seules illustre l’absence des filtres Films/Séries, distincte
d’une recherche sans correspondance dans un catalogue qui possède ces types.

## Choix de la maquette historique et suite Q9

Les [règles d’interaction Q9](search-interactions.md) retiennent 350 ms et quatre
résultats par rubrique ; les listes réelles par type utiliseront des pages de
20. Elles fixent aussi le changement de source, les réponses tardives et le focus.
La maquette initiale n’est pas modifiée par ce cadrage ; ses limites restent
décrites ci-dessous.

- Pause de saisie de 350 ms, utilisée uniquement pour simuler la mise à jour automatique.
- Quatre résultats par rubrique dans Tous ; variante de deux résultats disponible
  pour comparer la densité. Huit premiers résultats et Afficher plus dans une liste
  par type. Ces nombres ne définissent pas la pagination serveur.
- Clavier TV alphabétique simplifié avec caractères de démonstration ; ce n’est ni
  un clavier système complet ni une décision d’en construire un spécifique.
- Voir les résultats masque ce clavier et place le focus sur le premier résultat.
  Le focus initial de l’application réelle et les actions sans résultat restent à préciser.

Le changement de source en cours de requête, les réponses tardives et la pagination
réelle sont spécifiés dans le complément Q9 ; leur réalisation et la parité avec
la recherche locale Android restent à vérifier.
La composition de la fiche de contenu complète sera étudiée séparément.

## Portée et vérifications

Données fictives en mémoire, sans chaîne réelle, logo, affiche ni URL de flux.
Les clics de lecture ouvrent un aperçu explicite. Les réessais sont simulés ; aucun
appel API n’est effectué. Les choix de présentation peuvent être mémorisés dans la
conversation, mais le texte recherché n’est pas enregistré par ce mécanisme.

Le scénario hors ligne montre le cas sans résultats disponibles. Il ne décide pas
de supprimer des résultats déjà affichés ni de désactiver une recherche locale
opérationnelle ; les capacités réelles de chaque surface doivent être vérifiées.

Vérifications du prototype : recherche partielle et casse, filtre par type, Afficher
plus, retour avec saisie/filtre/focus conservés, erreur partielle et réessai, absence
de types, saisie au clavier TV et passage aux résultats. États résultats, champ vide,
chargement, erreur partielle et hors ligne contrôlés sans débordement horizontal à
320, 390, 736 et 1024 px sur les trois variantes. Aucune erreur JavaScript observée.
Relecture visuelle des captures web, mobile et TV effectuée.

La maquette française n’est pas une recette applicative : FR/EN, accessibilité
complète, lecteur réel, grands catalogues, délais réseau et télécommande réelle
restent à tester. Le rendu TV dans la conversation se déroule verticalement et ne
valide pas encore la composition 1920 × 1080 ni la lisibilité à trois mètres.
