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
| Q1 | Partiellement décidé le 19 septembre : cumul par film/épisode entre sessions/appareils ; réapparition dès le démarrage réel après retrait. Restent lectures simultanées, Recommencer, contenu déjà terminé, concurrence et écritures retardées | S12/C3 |
| Q2 | Partiellement décidé le 19 septembre : garder la série avec le suivant non commencé, sans seuil préalable sur lui. Restent contenus très courts et suivant déjà terminé ; durée inconnue : reprise conservée déjà validée | S12 puis S13 |
| Q3 | Réordonner les chaînes d'une source dans un groupe contenant aussi d'autres sources ; erreurs partielles lors du retrait de tous les groupes | S11 |
| Q4 | Comportements produit validés le 19 septembre dans US-024 : indisponibilité, catalogue inaccessible/partiel, suppression pendant une lecture et vérification après reconnexion. Restent à définir/vérifier le mécanisme de détection et les capacités de cache/C4 ; recette non exécutée | S8 |
| Q5 | Seuil de fraîcheur EPG, programme se terminant avec fiche ouverte, conservation de recherche/filtres, focus des cases de durées différentes | S9 |
| Q6 | Portée et défaut de langue d'interface, sous-titres forcés, pistes non nommées, compte changé sur l'appareil | S13 |
| Q7 | Partiellement décidé le 19 septembre : perte du Wi-Fi en lecture → pause et demande de Wi-Fi si données mobiles désactivées. Restent reprise au retour du Wi-Fi, Ethernet/VPN/réseau inconnu, Wi-Fi limité et préférence au changement de compte | S13 |
| Q8 | Contenu À regarder disparu ; états vide/hors ligne ; écritures concurrentes et erreurs de synchronisation | S11 |
| Q9 | Recherche : nombre de cartes, pagination, délai de saisie, changement de source pendant une requête, clavier TV | S10 |

## Écrans

Les [cas Continuer CW-01 à CW-28](../../design/0.2.0/continue-watching-cases.md)
séparent les règles acquises des arbitrages Q1/Q2 et des propositions d’erreur.
C3 reste à définir et faire approuver ; ces décisions n’autorisent pas une
modification implicite du contrat.

Les maquettes seront produites lorsque l'utilisateur le demandera. Avant chaque
lot d'interface, préciser les écrans utiles, les états, le focus TV et les retours.
L'approbation du comportement ne vaut pas approbation d'une nouvelle composition
visuelle. Réutiliser les tokens existants ; ne pas modifier les exports historiques
des canevas à la main. Les écrans de la 0.2.0 restent séparés.
