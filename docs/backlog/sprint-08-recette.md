# Recette — Sprint 8

Livrable de **S8-00** (parcours de référence et recettes historiques reprises) et
plan de **S8-07** (recette de la verticale 0.2.0). Rédigé le 24 septembre 2026.
**Non exécuté** sur appareil réel : ce qui a été vu sur l'émulateur TV est marqué
tel quel, et ne vaut pas recette.

Il se joue **à la main**, sur un téléphone Android réel, une TV ou box avec
télécommande, et un navigateur, en **français et en anglais**. Il ne remplace ni les
287 tests API, ni les 418 tests Android, ni les 215 tests web : il vérifie ce
qu'aucun des trois ne voit — qu'un choix de source fait sur le téléphone ne change
pas celui de la TV, qu'un catalogue reste lisible pendant qu'il se réactualise, et
qu'une source supprimée depuis le web arrête une lecture sur la TV.

Les critères sont ceux d'US-017, US-018, US-020 (socle), US-024 et US-025 (socle).
**La numérotation reprend à R-400.** R-10 → R-73 sont dans
[`sprint-02-recette.md`](./sprint-02-recette.md), R-100 → R-394 dans les recettes
des sprints 4, 5 et 6.

---

## 1. Prérequis

### La pile

```bash
LUMO_INGEST_ALLOW_PRIVATE_HOSTS=true docker compose --profile bench --env-file apps/api/.env up -d --build --wait
```

| Ce qu'il faut | Où |
|---|---|
| API | `http://localhost:8080` ; depuis un émulateur, `adb reverse tcp:8080 tcp:8080` |
| Site | `pnpm dev` dans `apps/web`, `http://localhost:3000` |
| Banc | `http://localhost:18081` ; depuis un appareil réel, `BENCH_PUBLIC_URL=http://<ip du poste>:18081` avant le `up` |
| Source A (M3U) | `http://bench/mixed.m3u` vu par l'API — 3 chaînes, 4 films |
| Source B (Xtream) | hôte `http://bench`, identifiants `bench` / `bench` — chaînes, films, 2 séries |
| Source en erreur | `http://bench/not-a-playlist.html` (`SOURCE_INVALID_FORMAT`), `/xtream-401/` (`SOURCE_AUTH_FAILED`), `192.0.2.1` (`SOURCE_UNREACHABLE`) |
| Délai serveur | `LUMO_MANUAL_SYNC_INTERVAL` vaut `PT5M` : deux actualisations manuelles à moins de cinq minutes suffisent |

Depuis un appareil réel, les adresses de source sont celles du banc **vues par le
conteneur API** (`http://bench/…`) : c'est l'API qui va les chercher, pas l'appareil.

### Les comptes et les appareils

Deux comptes email créés pendant la session (`recette-s8-…@test.example`), dont un
qui reste **vierge** jusqu'à R-430. Un téléphone, une TV activée par
`lumo.tv/activate` depuis le navigateur (R-51), et ce même navigateur.

**Aucune source réelle, à aucun moment** — la règle de `sprint-02-recette.md` §2
s'applique entière, captures comprises.

**Langue.** Chaque cas se rejoue en français **et** en anglais.

---

## 2. Parcours de référence — email → source → lecture

C'est le point de départ de toute session : s'il ne passe pas, rien d'autre n'est
jouable. Il reprend les cas historiques encore valables, **hors Google** : R-15 et
R-16 (US-03) sont **retirés de la 0.2.0** par décision du 17 septembre 2026 —
non joués, pas échoués — et le bouton n'existe plus sur aucune surface depuis S8-06.

| Étape | Surface | Cas repris | Attendu en 0.2.0 |
|---|---|---|---|
| Inscription, connexion | téléphone, navigateur | R-10 à R-14 | inchangé ; **aucun bouton Google** sur ces écrans (R-433) |
| Session | téléphone | R-17, R-18 | inchangé |
| Activation TV | TV + navigateur | R-50 à R-55 | inchangé ; à l'activation, la TV arrive sur **Accueil**, plus sur les chaînes |
| Première source | téléphone ou navigateur | R-20, R-21, R-30 | la première source devient **active sur cet appareil** ; une fois prête, *Découvrir mon catalogue* |
| Source en erreur | idem | R-22, R-23, R-31 à R-34 | la source est **conservée** avec son erreur et *Réessayer* ; jamais un nouvel ajout imposé |
| Catalogue | téléphone, TV | R-40 à R-42, R-60 à R-62 | inchangé, dans Explorer (mobile) ou Direct (TV) |
| Lecture | téléphone, TV | R-43 à R-47, R-63 à R-65 | inchangé |
| Transverse | toutes | R-70 à R-73 | inchangé ; R-71 vaut pour tous les nouveaux écrans |

Les recettes des sprints 1 et 2 n'ont jamais eu de rapport de session
([`dette.md`](./dette.md) §3) : **ce parcours les rejoue**, et son rapport en tient
lieu. Les sprints 4, 5 et 6 gardent leurs propres plans, à jouer séparément.

---

## 3. Source active — S8-03, US-018

**R-400 · Deux sources, choix demandé** · TV, téléphone
Compte à deux sources, appareil jamais ouvert dessus.
→ Une fenêtre *Choisir une source* s'ouvre d'elle-même : nom, état, **rien de
coché**. `BACK` ne la ferme pas. *Vu sur l'émulateur TV le 20 septembre.*

**R-401 · Le choix ne quitte pas l'appareil** · téléphone + TV
Choisir A sur le téléphone, B sur la TV, fermer et rouvrir les deux.
→ Chacun retrouve **son** choix. Le navigateur, lui aussi, garde le sien (cookie).

**R-402 · Une seule source** · toutes
→ Son nom s'affiche en texte simple, sans menu, sans flèche.

**R-403 · Changer depuis une fiche** · téléphone, navigateur
Ouvrir la fiche d'un film de A, changer pour B.
→ Retour à la grille Films **de B**, filtres remis à zéro ; jamais une fiche de A.

**R-404 · Favoris et reprises suivent la source** · toutes
Un favori sur A, un sur B ; une reprise sur A.
→ Sur A, seuls les siens ; passer à B puis revenir : ceux de A sont **toujours là**.

**R-405 · Une panne ne prouve rien** · téléphone
Couper le réseau, rouvrir l'application.
→ La source choisie reste choisie ; le catalogue en cache s'affiche avec un bandeau
« Lumo n'a pas pu être joint », *Réessayer* et *Changer de source*. Jamais
« aucune source ».

---

## 4. Accueil et navigation — S8-04, US-017, US-020

**R-410 · Ordre et sections** · toutes
→ Continuer, Favoris, Direct, **dans cet ordre** ; une section vide n'a ni titre ni
espace. *Favoris et Direct vus sur l'émulateur TV.*

**R-411 · Continuer dès le premier accueil** · TV
Reprise enregistrée depuis le téléphone, TV fraîchement activée.
→ La carte est là **sans** passer par Films. *Défaut trouvé et corrigé le 20 septembre,
revérifié sur cache vide ; à confirmer sur appareil.*

**R-412 · Reprendre en un geste** · toutes
`OK` / tap sur une carte Continuer.
→ La lecture reprend **à la position enregistrée**. Sur le web, un **film** ouvre sa
fiche (« Reprendre à… ») : écart connu, à arbitrer.

**R-413 · Chaque favori une fois** · toutes
La même chaîne dans deux groupes.
→ Une seule carte, à la place de sa première occurrence (ordre des groupes, puis des
chaînes).

**R-414 · Menus** · toutes
→ TV et web : Accueil, Direct, Films, Séries, Ma bibliothèque. Mobile : Accueil,
Explorer (Direct, Films, Séries), Bibliothèque, Réglages. Pas de Recherche, pas
d'Abonnement.

**R-415 · Retour** · TV, téléphone
Depuis Films, Réglages, Ma bibliothèque : `BACK`.
→ Accueil ; depuis Accueil, l'application se ferme. *Vu sur l'émulateur.*

**R-416 · Focus TV de l'accueil** · TV
→ Arrivée sur la première carte ; `LEFT` depuis une première carte va au rail ;
`UP`/`DOWN` passent d'une rangée à l'autre ; retour d'un lecteur sur **la carte
lancée** ; appui long = fiche. Voir `tv-focus-map.md` §Accueil.

**R-417 · États vierge, synchronisation, erreur** · toutes
Compte avec une source prête et rien regardé ; source en cours d'actualisation ;
source en erreur.
→ Invitation à explorer avec trois actions, jamais trois rangées vides ; bandeau
avec l'**étape réelle** ; bandeau avec la **cause** et un lien vers Mes sources, les
rangées toujours affichées.

---

## 5. Mes sources — S8-05, US-024

**R-420 · La liste** · téléphone, navigateur
→ Nom, type, état, dernière synchronisation, compteurs chaînes / films / séries
**quand ils sont connus** ; jamais un « 0 » pour un inconnu ; pas de séries sur une
M3U ; la source utilisée par cet appareil est marquée.

**R-421 · Utiliser cette source** · téléphone, navigateur, TV
→ Changement immédiat, on reste sur Mes sources ; sur TV le focus passe sur
*Actualiser* de la source choisie.

**R-422 · Actualiser, puis réactualiser** · toutes
→ « Actualisation en cours », bouton désactivé, étapes réelles ; une seconde
demande à moins de cinq minutes affiche **le délai du serveur** (« dans environ
5 minutes »), jamais un chiffre inventé.

**R-423 · Ancien catalogue pendant l'actualisation** · toutes
Lancer une actualisation, ouvrir Direct pendant qu'elle tourne (`SYNCING` peut se
forcer en base sur le banc : voir `c4-previous-catalogue.md` §7).
→ Le catalogue précédent reste consultable, bandeau « actualisation en cours » ; la
lecture d'un flux **attend** la fin.

**R-424 · Échec avec ancien catalogue** · toutes
Corriger l'adresse vers `not-a-playlist.html`.
→ Correction acceptée malgré le délai ; source en erreur, catalogue précédent
consultable avec « peut être ancien », **lecture possible** ; avec `/xtream-401/`,
lecture refusée « identifiants refusés » avec un lien vers Mes sources.

**R-425 · Renommer, actualisation automatique par source** · téléphone, navigateur
→ Le nom change partout ; l'interrupteur agit sur **cette** source et se retrouve
sur les autres appareils.

**R-426 · Supprimer** · téléphone, navigateur
→ Confirmation qui **nomme** la source et ses conséquences (catalogue, favoris,
progressions, chaînes récentes ; abonnement fournisseur non résilié ; réajout sans
restauration) ; *Annuler* par défaut. Jouer avec **zéro, une et plusieurs** sources
restantes : ajout, source unique choisie seule, choix demandé.

**R-427 · Supprimée ailleurs, pendant une lecture** · TV + navigateur
Lecture sur la TV ; supprimer la source depuis le navigateur.
→ Dans la minute : lecture arrêtée, « Cette source a été supprimée de votre
compte. », *Continuer* seul ; puis la règle de R-426. Aucun autre contenu lancé.
*Vu sur l'émulateur TV.* Couper le réseau pendant une lecture : **rien** ne s'arrête.

**R-428 · TV : gestion guidée** · TV
→ Choisir et actualiser seulement ; le bloc explique que le reste se fait depuis le
téléphone ou lumo.tv, sans parler d'activation.

---

## 6. Réglages et retraits — S8-06, US-025

**R-430 · Les rubriques** · toutes
→ Compte et appareils, Mes sources, Application, Aide et informations. **Pas de
Lecture.** Aucune ligne « mock », aucun bouton inopérant.

**R-431 · Appareils** · téléphone, navigateur
→ Cet appareil identifié ; les autres avec leur dernière activité ou
« indisponible » ; révocation confirmée ; l'appareil révoqué est déconnecté.
Sur TV : lecture seule et guidage.

**R-432 · Déconnexion confirmée** · toutes
→ Une confirmation, *Annuler* par défaut ; après, `BACK` ne ramène pas dans le
compte.

**R-433 · Plus de Google, d'abonnement, de tarifs** · toutes
→ Connexion et inscription sans Google ; espace web sans Abonnement ; site sans
Tarifs ni offre « Plus » ; limite de sources expliquée sans offre supérieure.

**R-434 · Langue et version** · toutes
→ Langue de l'interface en lecture seule, version affichée, guides accessibles.

---

## 7. Critère de sortie

La recette est verte quand tous les cas passent, dans les deux langues, sur
appareil réel, télécommande en main pour la TV — et que le parcours de référence
du §2 a son rapport. R-70 à R-73 bloquent la livraison quelle que soit la story.

Le rapport note, pour chaque cas : vert, rouge, ou **non joué avec la raison**.
« Vu sur l'émulateur » n'est pas vert.
