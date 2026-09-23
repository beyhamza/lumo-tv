# S9-00 — Rapport du banc EPG

24 septembre 2026. **Banc automatisé livré, en recette.** Cadrage C1 validé en
conversation le 24 septembre ; plafonds définitifs et contrat à figer dans S9-01.
Ce rapport ne clôt ni S9-02 ni la recette des interfaces S9-07.

## Exécution et reproduction

Environnement : Windows, JDK 25.0.1, PostgreSQL 16 Alpine éphémère via Testcontainers,
Gradle du dépôt. La base de développement et le sprint 8 ne sont pas utilisés.

Depuis `apps/api` :

```powershell
.\gradlew.bat test --tests '*EpgBenchIntegrationTest' --rerun-tasks
.\gradlew.bat build
```

Résultat final : **295 tests, zéro échec, zéro ignoré**, dont huit tests du banc EPG.
Le build complet termine avec succès. Les modifications concernent exclusivement
le support de test et les documents ; aucun code de production ni contrat n’est changé.

Le test démarre son serveur HTTP sur une boucle locale et un port libre, génère
ses XMLTV autour de l’instant d’exécution, puis lance le vrai `IngestionService`.
Les programmes sont fictifs, sans logo. La playlist référence seulement l’actif
de test Mux autorisé ; ce flux n’est jamais chargé par les tests d’ingestion.
L’exception d’accès aux hôtes privés est limitée à cette classe de test.

Artefacts régénérés : `apps/api/build/reports/epg-bench.json`,
`epg-bench-long-descriptions.json`, résultats JUnit et
`apps/api/build/epg-bench-fixtures/` (normal, gzip, vide, cassé après lots, ancien).
Les XMLTV générés ne sont pas committés : leurs dates expireraient. Ils sont servis
par le serveur de test, pas ajoutés au nginx de la pile web existante.

## Ce qui est effectivement vérifié

| Cas | Observation |
|---|---|
| XMLTV normal | Programmes lus, stockés dans PostgreSQL, source READY |
| gzip | Décodage via `Content-Encoding`, puis via suffixe `.gz` sans cet en-tête |
| Réimport | Nombre de programmes et UUID existant conservés |
| Vide, hors rétention, aucune configuration EPG | Catalogue de 100 chaînes utilisable, aucun programme importé |
| XML cassé avant le premier lot | Zéro programme, source READY |
| XML cassé après lots | Plus de 500 programmes persistent, source READY et `last_synced_at` renseigné : cette date ne mesure pas un succès EPG |
| Rétention | Programme expiré supprimé par la purge réelle, programmes courants conservés |
| Durées 15/45 minutes | Bonne fenêtre, exclusion du programme se terminant exactement à `from` |
| Mapping vide et autre propriétaire | Aucun programme retourné par le repository existant |
| 100 chaînes partageant un `tvg_id` | 192 programmes stockés ; 600 occurrences sur 3 h dans la réponse candidate, chacune sous sa chaîne |

Les tests caractérisent l’existant. Ils ne prétendent pas que les métadonnées
EPG de C1 existent déjà ou qu’un snapshot atomique a été ajouté.

## Mesures de volume

Matrice : programmes de 30 minutes, descriptions de 100 caractères. JSON UTF-8
non compressé, enveloppe candidate de C1 incluse. L’enveloppe est construite seulement
dans le test pour mesurer son volume ; aucun type client/serveur n’est ajouté.

| Chaînes | Fenêtre | Occurrences | JSON (octets) | gzip (octets) |
|---:|---:|---:|---:|---:|
| 1 | 3 h | 6 | 2 578 | 556 |
| 1 | 24 h | 48 | 17 362 | 1 978 |
| 1 | 96 h | 192 | 68 132 | 6 943 |
| 50 | 3 h | 300 | 110 721 | 10 102 |
| 50 | 24 h | 2 400 | 849 921 | 69 032 |
| 50 | 96 h | 9 600 | 3 388 421 | 331 565 |
| 100 | 3 h | 600 | 221 023 | 19 727 |
| 100 | 24 h | 4 800 | 1 699 087 | 137 324 |
| 100 | 96 h | 19 200 | 6 774 935 | 662 565 |

Cas descriptions longues : 100 chaînes partageant un identifiant EPG, 3 h,
600 occurrences, description de 8 192 caractères → **5 075 314 octets**, avant
ajout des métadonnées d’enveloppe, soit déjà plus de 4 Mio. Le gzip tombe à 38 275
octets, mais les descriptions synthétiques sont très répétitives : ce taux n’est
pas une prévision pour un fournisseur. Il ne réduit pas le coût après décompression.

## Temps, mémoire et limites de la mesure

Les lectures mesurées utilisent **le repository unitaire existant**, soit 1, 50 ou
100 requêtes de programmes selon le cas. Ordres de grandeur observés : 35,6 ms pour
50 chaînes/3 h, 71 ms pour 100 chaînes/24 h, 105 ms pour 100 chaînes/96 h. Ce sont
des temps cumulés de repository dans ce banc local, pas une latence HTTP, un p95
ou une preuve de performance du futur endpoint groupé. La sérialisation prend
environ 1,5 / 4,1 / 20,4 ms respectivement sur cette exécution.

Le rapport JSON conserve aussi la somme des pics utilisés des pools mémoire heap
JVM après remise à zéro des compteurs avant chaque scénario : environ 164 à 259 Mo
sur ce passage. Cette valeur inclut le contexte applicatif et les objets des tests,
et additionne des pics qui ne sont pas nécessairement simultanés. Ce n’est ni un
delta attribuable à la réponse, ni le RSS du processus, ni une capacité de production.

## Recommandation pour S9-01

Conserver la proposition de **5 000 occurrences et 4 Mio non compressés**, deux
limites indépendantes :

- le cas courant 50 chaînes/3 h tient largement ;
- 100 chaînes/24 h tient dans cette fixture ;
- 50 chaînes/96 h dépasse le nombre, même si les octets tiennent ;
- des descriptions longues dépassent les octets bien avant 5 000 occurrences.

Ne pas augmenter les plafonds pour faire passer toutes les fenêtres. Prévoir le
découpage borné et les erreurs explicites de C1. La vérification exacte aux frontières
4 Mio et 5 000/5 001, l’arrêt anticipé de lecture/sérialisation, les compteurs SQL
de la lecture groupée et sa latence HTTP appartiennent à S9-02 : l’opération n’existe
pas encore. Ils restent des conditions de recette avant de déclarer C1 livré.

La validation finale de S9-01 peut maintenant s’appuyer sur ces preuves. La démo
web/mobile/TV et la preuve d’un seul appel réseau par grille demeurent dans S9-07.
