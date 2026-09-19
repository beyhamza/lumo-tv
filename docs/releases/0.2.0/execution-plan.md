# Préparation de la recette finale — 0.2.0

Date : 19 septembre 2026. Statut : plan proposé, non exécuté.
Références : [sprint 14](../../backlog/sprint-14.md), [critères de sortie](acceptance.md),
[arbitrages](../../roadmap/0.2.0/open-questions.md).

## Entrée en qualification

S14 commence après clôture documentée de S8–S13. Les maquettes et leur relecture
ne valent pas livraison. Rassembler pour chaque lot : arbitrages nécessaires clos,
contrat approuvé et implémenté, tests et recette sur les surfaces concernées,
anomalies résolues ou limites explicitement arbitrées.

Préparer un build candidat identifié et des comptes/sources de banc autorisés.
Les parcours suivants organisent l’exécution de R020-01 à R020-16 sans les remplacer.
Chaque résultat doit être rapporté séparément pour chaque surface applicable.

## Sessions proposées

| Session | Parcours de bout en bout | Lignes de recette |
|---|---|---|
| A — Installation et compte | Installation vierge, compte email, activation TV, session, déconnexion locale et autre appareil | 01, 13, 15 |
| B — Première source | Ajout mobile/web, attente/import, catalogue disponible, sélection locale sur TV, erreur et reprise | 02, 03, 14 |
| C — Navigation quotidienne | Accueil, Direct/Guide, recherche, fiche et retour à la position précédente | 02, 04, 05, 09, 13 |
| D — Bibliothèque partagée | Groupes, ordre, dédoublonnage, À regarder, retrait et propagation sur deux appareils | 06, 07 |
| E — Reprise | Temps réellement lu, limites 30 s et 95 %, durée inconnue, retrait sans perte, relance et autre appareil | 08, 09 |
| F — Lecteur et réglages | Pistes disponibles/absentes, préférences locales, qualité Auto, fin réelle, décompte annulé, saison et final | 10, 11, 12 |
| G — Réseau et incidents | Lancement hors Wi-Fi, perte du Wi-Fi, autoplay, source indisponible/supprimée, erreurs et retour de connexion | 03, 08, 09, 12, 13 |
| H — Mise à jour et sortie | Version précédente vers candidate, données conservées, checks CI, parcours gratuit, notes et paquet livrable | 14, 15, 16 |

FR/EN, clavier et D-pad sont transversaux. Répéter les parcours critiques sur un
téléphone réel et une TV avec télécommande ; les tests navigateur ne les remplacent pas.
Vérifier aussi deux sources et deux comptes sans fuite de catalogue ou de préférences
selon la portée retenue. La matrice exacte navigateur/OS/appareil est à fixer avant S13.

## Rapport à créer lors de l’exécution

Utiliser un rapport daté par session, lié depuis la matrice de recette. Ne créer
aucun succès à l’avance. Champs requis :

| Champ | Information attendue |
|---|---|
| Identité | Session, date, personne ou agent exécutant |
| Candidat | Commit, version des artefacts, configuration non secrète |
| Environnement | Surface, OS, modèle d’appareil, navigateur/version, télécommande |
| Préconditions | Comptes de banc anonymisés, fixtures autorisées, arbitrages applicables |
| Cas | ID R020 et sous-cas précis, étapes, résultat attendu |
| Résultat | Réussi, échoué, non joué ou hors périmètre avec décision citée |
| Preuve | Capture ou sortie de check nettoyée de toute donnée sensible |
| Suite | Anomalie liée, correction, commit et résultat du rejeu |

Une session bloquée par un appareil manquant reste non jouée. Une panne réseau
ne prouve pas une suppression de source. Une réussite partielle ne clôture pas
toute une ligne R020. Garder les traces des premiers échecs et de leur rejeu.

## Dossier de livraison à préparer en S14

- Inventaire des builds réellement testés et des versions des artefacts concernés.
- Matrice de recette remplie avec liens vers les preuves ; anomalies et limites connues.
- Changelog et notes de sortie décrivant uniquement les fonctions effectivement livrées.
- Procédure d’installation/mise à jour et procédure de retour documentée selon les
  migrations réellement livrées ; ne pas présumer qu’une migration est réversible.
- Vérification des guides, liens de confidentialité/conditions et numéro de version affiché.
- Résultats des checks contract/API/Android/web, dont marketing statique et traductions.

Google et paiements restent hors périmètre, leurs dettes ouvertes. Le parcours gratuit
n’autorise aucune modification implicite de quotas ou droits. La décision de sortie
et la publication sont distinctes ; aucune publication n’est lancée par ce plan.
