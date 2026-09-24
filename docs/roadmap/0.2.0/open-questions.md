# Arbitrages avant implémentation

Ce registre distingue décisions produit validées et détails encore ouverts.
Il ne remet pas en question la version gratuite ni le report de Google/Stripe.
Un lot dépendant attend son arbitrage ; les autres travaux peuvent avancer.
Aucun endpoint ni changement d'ADR n'est défini ici.

## Lots contractuels à préparer et faire valider

| ID | Besoin | État | Avant |
|---|---|---|---|
| C1 | EPG groupé, bornes, volume, âge réel des données du guide | [Cadrage D1–D5](c1-grouped-epg.md) validé le 24 septembre ; banc S9-00 exécuté. **Gel S9-01 prononcé le 24 septembre** (plafonds 5 000 occurrences / 4 Mio, §8) ; contrat écrit et serveur implémenté en S9-02 ; métadonnées d’import distinctes du catalogue et de la récupération ; OpenAPI inchangé | S9 |
| C2 | Liste À regarder de films et séries, partagée, tri par ajout, retrait et suppression de source | Besoin validé, forme contractuelle à soumettre | S11 |
| C3 | Masquage partagé de Continuer et mesure d'éligibilité après 30 secondes effectives | Règles produit Q1/Q2 acquises ; garanties d’ordre, de réessai et de propagation hors ligne à définir, forme contractuelle à soumettre | S12 |
| C4 | Consultation du catalogue précédent pendant SYNCING/ERROR et cascades documentées de suppression | [Lot validé le 19 septembre](c4-previous-catalogue.md) (D1 à D5) : consultation ouverte dès qu'une ingestion a réussi ; lecture fermée en PENDING/SYNCING, autorisée en ERROR avec catalogue sauf identifiants refusés ou abonnement expiré ; cascades décrites ; délai serveur implémenté ; aucun endpoint ni ADR. Réalisation : S8-02 | S8 |

Contrat écrit à la main, génération des trois clients et implémentation serveur
dans le lot approuvé, avant le branchement client. Annoncer tout changement du
contrat dans la PR. Une évolution de persistance/cache nécessitant un ADR suit
la procédure du dépôt. Aucun changement de chiffrement ou de droits n'est prévu.

## Décisions produit à concentrer avant le lot concerné

| ID | Question à trancher | Avant |
|---|---|---|
| Q1 | Règles principales tranchées le 19 septembre : cumul par contenu sans compter deux fois les secondes simultanées ; lecture la plus récente pour la reprise ; masque conservé face à une lecture déjà en cours, retour au nouveau démarrage réel après retrait ; Recommencer conserve l’éligibilité. C3 doit encore définir ordre des événements, réessais et propagation hors ligne ; restauration manuelle éventuelle à préciser dans US-019 | S12/C3 |
| Q2 | Tranché côté produit le 19 septembre : suivant non commencé proposé sans seuil préalable ; contenu court sans exception d’apparition et absent une fois terminé ; suivant déjà terminé relu depuis zéro dans l’ordre ; durée inconnue avec reprise conservée. Réalisation et recette restent à faire | S12 puis S13 |
| Q3 | Règles produit validées le 19 septembre : conserver les places des autres sources lors d’un réordonnancement filtré ; conserver les retraits réussis et réessayer le reste. Vérifier la permutation avec les opérations unitaires existantes, échecs intermédiaires, résultat inconnu et concurrence (FO-01 à FO-12) | S11 |
| Q4 | Comportements produit validés le 19 septembre dans US-024 : indisponibilité, catalogue inaccessible/partiel, suppression pendant une lecture et vérification après reconnexion. Mécanisme de détection proposé dans [C4, P6](c4-previous-catalogue.md) : seul un `404 SOURCE_NOT_FOUND` prouve une suppression ; vérification toutes les 60 secondes pendant une lecture, validée le 19 septembre (D5). Capacités de cache vérifiées : l'ancien catalogue reste en base, Android le garde dans Room ; recette non exécutée | S8 |
| Q5 | Partiellement résolu le 20 septembre : fiche liée au programme initial, action mise à jour aux bornes horaires, recherche/filtre conservés pendant la consultation, heure de référence pour le focus TV et retours déterministes ; voir GD-01 à GD-14. Le seuil et la provenance de fraîcheur EPG sont tranchés par C1 (gel S9-01 : strictement plus de 24 h = ancien, « dernier import du guide » distinct de la fraîcheur du fournisseur) ; recette sur appareils réels non exécutée | S9 |
| Q6 | Cadré côté produit le 23 septembre dans player-settings-cases.md : interface locale, langues de lecture par compte/appareil, défauts, sous-titres forcés, pistes sans langue, erreurs et changement de compte. Capacités et recette PS-01 à PS-07/13 à vérifier ; non livré | S13 |
| Q7 | Cadré côté produit le 23 septembre dans player-settings-cases.md : pause et reprise explicite, autorisation Wi-Fi uniquement si option désactivée, Ethernet/VPN/réseau inconnu, Wi-Fi limité et préférence réseau conservée par installation. Détection et transferts média à vérifier, PS-08 à PS-13 non exécutés | S13 |
| Q8 | Contenu À regarder disparu ; états vide/hors ligne ; écritures concurrentes et erreurs de synchronisation | S11 |
| Q9 | Recherche : nombre de cartes, pagination, délai de saisie, changement de source pendant une requête, clavier TV | S10 |

## Écrans

Les [cas Continuer CW-01 à CW-33](../../design/0.2.0/continue-watching-cases.md)
séparent les règles acquises des arbitrages Q1/Q2 et des propositions d’erreur.
C3 reste à définir et faire approuver ; ces décisions n’autorisent pas une
modification implicite du contrat.

Les maquettes seront produites lorsque l'utilisateur le demandera. Avant chaque
lot d'interface, préciser les écrans utiles, les états, le focus TV et les retours.
L'approbation du comportement ne vaut pas approbation d'une nouvelle composition
visuelle. Réutiliser les tokens existants ; ne pas modifier les exports historiques
des canevas à la main. Les écrans de la 0.2.0 restent séparés.
