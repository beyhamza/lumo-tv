# Arbitrages avant implémentation

Ce registre distingue décisions produit validées et détails encore ouverts.
Il ne remet pas en question la version gratuite ni le report de Google/Stripe.
Un lot dépendant attend son arbitrage ; les autres travaux peuvent avancer.
Aucun endpoint ni changement d'ADR n'est défini ici.

## Lots contractuels à préparer et faire valider

| ID | Besoin | État | Avant |
|---|---|---|---|
| C1 | EPG groupé, bornes, volume, âge réel des données du guide | S7-01 est une proposition historique, pas un contrat livré ; distinguer ingestion EPG et simple date de récupération | S9 |
| C2 | Liste À regarder de films et séries, partagée, tri par ajout, retrait et suppression de source | Besoin validé, forme contractuelle à soumettre | S11 |
| C3 | Masquage partagé de Continuer et mesure d'éligibilité après 30 secondes effectives | Besoin validé, règles Q1/Q2 et forme contractuelle à soumettre | S12 |
| C4 | Consultation du catalogue précédent pendant SYNCING/ERROR et cascades documentées de suppression | Le contrôleur exige READY ; déterminer quelles lectures peuvent rester disponibles sans modifier implicitement la lecture des flux | S8 |

Contrat écrit à la main, génération des trois clients et implémentation serveur
dans le lot approuvé, avant le branchement client. Annoncer tout changement du
contrat dans la PR. Une évolution de persistance/cache nécessitant un ADR suit
la procédure du dépôt. Aucun changement de chiffrement ou de droits n'est prévu.

## Décisions produit à concentrer avant le lot concerné

| ID | Question à trancher | Avant |
|---|---|---|
| Q1 | Les 30 secondes s'accumulent-elles entre sessions/appareils ? Quelle réapparition après retrait ou Recommencer ? | S12/C3 |
| Q2 | Exception au seuil d'apparition pour un épisode suivant non commencé ? Contenus très courts, suivant déjà terminé, durée inconnue ? | S12 puis S13 |
| Q3 | Réordonner les chaînes d'une source dans un groupe contenant aussi d'autres sources ; erreurs partielles lors du retrait de tous les groupes | S11 |
| Q4 | Indisponibilité, hors ligne et catalogue inaccessible/partiel : comportement validé le 19 septembre dans US-024. Restent la suppression pendant une lecture et sa détection après reconnexion ; capacités de cache/C4 à vérifier | S8 |
| Q5 | Seuil de fraîcheur EPG, programme se terminant avec fiche ouverte, conservation de recherche/filtres, focus des cases de durées différentes | S9 |
| Q6 | Portée et défaut de langue d'interface, sous-titres forcés, pistes non nommées, compte changé sur l'appareil | S13 |
| Q7 | Changement Wi-Fi vers mobile en cours de lecture ; Ethernet/VPN/réseau inconnu ; préférence réseau au changement de compte | S13 |
| Q8 | Contenu À regarder disparu ; états vide/hors ligne ; écritures concurrentes et erreurs de synchronisation | S11 |
| Q9 | Recherche : nombre de cartes, pagination, délai de saisie, changement de source pendant une requête, clavier TV | S10 |

## Écrans

Les maquettes seront produites lorsque l'utilisateur le demandera. Avant chaque
lot d'interface, préciser les écrans utiles, les états, le focus TV et les retours.
L'approbation du comportement ne vaut pas approbation d'une nouvelle composition
visuelle. Réutiliser les tokens existants ; ne pas modifier les exports historiques
des canevas à la main. Les écrans de la 0.2.0 restent séparés.
