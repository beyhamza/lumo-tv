# S11-06 — rejeu sur émulateur des correctifs `BUG-S11-06-02`, `-04`, `-06`

Date : 15 octobre 2026. Base : `dev` @ `eea2898` (correctifs fusionnés par les PR #67 et #68),
APK de debug de cette base sur `Television_1080p` (EN, compte A) et `Pixel_10` (FR, compte B).
Joué par le lead, sur accord de Hamza : ces trois correctifs n'avaient été vérifiés que par
les tests unitaires, faute d'accès au pilotage des émulateurs et à la base pendant la passe de
correction. Les quatre autres correctifs Android (`-01`, `-07`, `-08`, `-10`) ont été
vérifiés sur émulateur au moment de la correction ; `-03` et `-05` (web) restent à rejouer.

Contenu du banc uniquement. Les comptes de recette sont ceux du passage
[`s11-06-recette-2026-10-15`](../s11-06-recette-2026-10-15/RAPPORT.md).

## Verdicts

| Bug | Scénario | Résultat | Captures |
|---|---|---|---|
| `BUG-S11-06-06` — type d'une carte indisponible | Un film ajouté à la liste de chaque compte, puis sa ligne de catalogue supprimée en base (`delete from vod_item`), comme le fait une synchronisation qui ne le voit plus. | ✅ **Conforme** — TV : « Unavailable · Film » ; téléphone (FR) : « Indisponible · Film », la carte disponible voisine garde « Film ». | [TV](ecrans/bug06-tv-carte-indisponible-type.png), [téléphone](ecrans/bug06-mobile-fr-carte-indisponible-type.png) |
| `BUG-S11-06-04` — fiche hors ligne sans explication | Le film revient au catalogue sous un nouvel identifiant (synchronisation), la liste est relue en ligne, puis l'API est arrêtée (`docker stop lumo-api`) et la carte ouverte : la fiche n'a aucune copie locale. | ✅ **Conforme** — « Ce film n'est pas disponible hors ligne — Il se chargera au retour de la connexion », *Réessayer* ; *Lire* et « Ma liste… » désactivés, ce dernier avec « Hors ligne : votre liste ne peut pas être modifiée avant le retour de la connexion. » (WL-06). | [fiche](ecrans/bug04-mobile-fr-fiche-sans-cache-hors-ligne.png) |
| `BUG-S11-06-02` — *Continue* rouvrait la fiche du film supprimé | Source M3U temporaire choisie sur la TV ; Films → « Le Voyage » → *Play* ; la source est supprimée par l'API pendant la lecture. Rejoué aussi depuis le Direct. | ✅ **Conforme** — lecture arrêtée, « This source has been removed from your account. » avec *Continue* seul ; *Continue* ramène à l'accueil de la source restante (« Banc Xtream A1 »), sans fiche ni lecture. Même résultat depuis le Direct. | [message](ecrans/bug02-tv-film-source-supprimee-message.png), [après Continue (film)](ecrans/bug02-tv-film-continue-accueil.png), [après Continue (Direct)](ecrans/bug02-tv-direct-continue-accueil.png) |

## Observations, sans écart ouvert

- **Le mode avion ne coupe pas l'émulateur de l'API.** Avec le mode avion et le tunnel `adb
  reverse` retiré, une connexion déjà ouverte a survécu : le retrait de « Le Voyage » depuis sa
  fiche est bien arrivé au serveur, et l'app a eu raison de l'afficher réussi. Le scénario hors
  ligne a donc été joué par arrêt de l'API.
- **Une panne de l'API est présentée comme « hors ligne »** sur la fiche, alors que le réseau de
  l'appareil fonctionne. Le comportement est le bon (consultation seule, explication) ; le
  libellé pourrait distinguer la panne du service.
- **Deux *Réessayer* sur la fiche hors ligne** (celui de la fiche et celui du bouton « Ma
  liste »). Ils relancent la même lecture.
- **Nom de la section** : « À regarder » sur le téléphone, « Ma liste » sur le web (FR), « My
  list » sur la TV. US-022 parle de la section « À regarder ».

## Nettoyage

Les deux éléments ajoutés aux listes pour `-06` sont retirés ; les sources temporaires sont
supprimées (c'était le scénario) ; « Le Voyage », retiré par le test hors ligne, est remis dans
la liste du compte B. Les deux sources de banc ont été resynchronisées, ce qui a restauré la
ligne de catalogue supprimée pour `-06`.
