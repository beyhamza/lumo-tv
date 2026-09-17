# État des lieux pour planifier la 0.2.0

Revue statique ciblée du 17 septembre 2026, sur la base de `034613c` et des fichiers
présents dans le workspace. Aucun build, test applicatif ou parcours sur appareil
n'a été exécuté pour cette revue documentaire. « Présent » ne signifie pas « recetté ».
Les modifications locales Android préexistantes ne font pas partie de ce travail.

## Fonctions et écarts

| Sujet | Évidence observée | Reste pour la cible validée | Lot |
|---|---|---|---|
| Accueil web | `apps/web/src/app/[locale]/app/page.tsx` affiche des liens sources/appareils/abonnement | Accueil Continuer/Favoris/Direct et navigation 0.2.0 | S8, finalisation S12 |
| Navigation Android | Les NavHost mobile/TV orientent `AppStart.Ready` vers le direct | Accueil, Explorer mobile, bibliothèque et recherche ; retour/focus | S8 puis chaque lot |
| Source active | `LiveViewModel` et `VodViewModel` prennent la première source ; le web navigue par source dans ses routes | Choix commun aux écrans, mémorisé localement par compte/appareil, remplacement après suppression | S8 |
| Sources | `SourceRepository`, `SourceViewModel`, actions web et `SourceService` existent | Aligner les actions par surface, états, erreurs et confirmations | S8 |
| Consultation pendant synchronisation | `CatalogController.requireReadableSource` exige READY ; `requirePlayableSource` aussi | La consultation du catalogue précédent exige un travail serveur/contrat à cadrer ; elle ne promet pas la lecture pendant SYNCING | S8, C4 |
| Suppression et progression | `0015-vod.sql`, changeset 0015-05 : FK de progression vers source avec `ON DELETE CASCADE` | Vérifier en intégration et préciser le contrat ; ajouter la future liste à regarder à la cascade | S8 puis S11 |
| Guide | Parseur/ingestion XMLTV et `CatalogController.getChannelEpg` présents ; aucun appel applicatif `getChannelEpg` trouvé dans les clients inspectés | Lecture groupée, fraîcheur, caches, vues et commandes des trois clients | S9 reprend S7 |
| Banc EPG | Aucun XMLTV repéré dans les fichiers du banc ni ses scripts inspectés | Fixtures datées relativement au lancement, guide absent/cassé/ancien | S9 |
| Recherche | `SearchMobileScreen` et `SearchTvScreen` sont des placeholders ; listes API par type avec `q`, repositories Android de recherche existants | Composer les résultats, états partiels et navigation sur les trois surfaces | S10 |
| Favoris/groupes | Repository API, cache Android, écrans et actions web existants ; contrat de déplacement/renommage/suppression | Agrégation sans doublon, gestes validés, réordonnancement accessible, recette partagée | S8 partiel, S11 complet |
| À regarder | Le contrat `Favorite` désigne une chaîne ; aucune liste films/séries repérée | Nouveau besoin partagé, contrat et serveur puis clients | S11, C2 |
| Progression | `ProgressRepository`, `WatchProgress`, utilitaires web et rails des sprints 5/6 présents | Masquage partagé, éligibilité après lecture effective, cohérence fiches/accueil | S12, C3 |
| Enchaînement | Android : décompte et suivant dans `EpisodePlayerViewModel`. Web : `EpisodePlayer` délègue à `FilmPlayer`, sans enchaînement repéré dans ces composants | Préférence locale, mode désactivé, enchaînement web, reprise du suivant | S13 |
| Audio | `LumoPlayer` expose `audioTracks` et `selectAudioTrack`, Media3 et choix UI implémentés ; usages dans les épisodes | Réutiliser l'audio Android, vérifier tous les lecteurs, préférences de langue ; adapter le web selon ses capacités | S13 |
| Sous-titres/qualité | Pas de commandes correspondantes dans l'interface `LumoPlayer` inspectée | Exposition des pistes/variantes et commandes, limites par navigateur | S13 |
| Réglages | `SettingsViewModel` Android expose session, appareils, sources, version ; son interrupteur auto_sync agit sur toutes les sources | Cinq rubriques, réglage auto_sync au niveau de chaque source, langue et préférences locales, blocage réseau mobile | S8 et S13 |
| Sortie gratuite | Une entrée abonnement est présente dans l'aperçu web ; Google/Stripe sont documentés comme incomplets | Parcours email/activation utilisables ; aucun accès vers un paiement ou une connexion tierce non opérationnels | S8 puis vérification S14 |

## Points à ne pas recréer

Le contrat et ses générateurs, l'authentification email, l'activation TV, les
catalogues et lecteurs, les groupes de favoris et l'enregistrement de progression
sont des bases existantes. Les sprints ajoutent ou adaptent leurs comportements.
Les fichiers mentionnés ci-dessus sont des pistes de revue, pas un audit exhaustif.

## État de validation

Les sprints 4 et 5 distinguent code livré et recette restante ; le sprint 6 se
déclare terminé. Cette revue n'a pas revalidé ces affirmations sur appareil.
Les rapports existants devront être rassemblés et les cas non joués marqués tels
quels. La recette email et activation des sprints 1/2 reste à reprendre ; Google
et les paiements sont exclus de la sortie 0.2.0 selon la décision utilisateur.

Voir [le plan de livraison](delivery-plan.md) et [la recette de version](../../releases/0.2.0/acceptance.md).
