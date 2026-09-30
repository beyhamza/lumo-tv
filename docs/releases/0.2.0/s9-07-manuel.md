# S9-07 — résidu manuel (matériel réel)

Liste **minimale** des cas que l'automatique ne peut pas couvrir et qui exigent
du matériel réel : D-pad/télécommande, téléphone Android réel, contrôle visuel
FR/EN. Elle **ne double pas** l'automatisé : chaque entrée dit ce qui est déjà
prouvé et ce qui reste à faire à la main.

Automatisé déjà vert au 2026-09-30 (voir
[`qa-evidence/s9-07-automatise-2026-09-27/RAPPORT.md`](qa-evidence/s9-07-automatise-2026-09-27/RAPPORT.md)) :
GD-07, GD-08, GD-11 (web) ; GD-10 erreur initiale (web + Android émulateur) ;
GD-12 minuit (web SSR) ; GD-14 FR/EN + clavier web ; preuve réseau I-4 web
(1 appel à 3/50/100 chaînes) et contrat/bornage API + Android (unitaires).

## Résidu à jouer sur matériel réel

1. **GD-04, GD-05, GD-06 — croix D-pad** sur TV/box réelle et télécommande :
   durées différentes, lacune, bords de grille, aucune boucle, aucun focus sur
   élément masqué. (Automate : aucun ; non joué.)
2. **GD-07, GD-08 — fiche programme au D-pad réel** : fin de programme, focus
   rejoint *Fermer* ; programme futur devenu courant sans vol de focus.
   (Automate : web horloge contrôlée + instrumenté émulateur ; le geste
   télécommande reste à confirmer.)
3. **GD-09 — retour lecteur** : ancre/recherche/filtre restaurés après lecture,
   sur TV et mobile réels. (Automate : aucun ; non joué.)
4. **GD-10 — erreur avec données** : guide déjà chargé **puis** panne EPG, la
   grille et son focus restent, message distinct. (Automate : seule l'erreur
   initiale est couverte.)
5. **GD-11 — guide ancien (> 24 h)** et chaîne sans `tvg_id` : date de mise à
   jour affichée, nom lisible sans logo, aucun logo fictif. (Automate : guide
   partiel web seulement.)
6. **GD-12 — changement d'heure du 25/10/2026** (`Europe/Paris`) et **appareil
   réglé sur un autre fuseau** que le serveur : horaires de l'appareil,
   comparaisons sur des instants. (Automate : impossible, rétention XMLTV ; repli
   unitaire `day-window.test.ts`.)
7. **GD-13 — mobile réel** : fiche → journée → Retour → Retour, position de liste
   conservée. (Automate : aucun ; non joué.)
8. **GD-14 — D-pad réel** (focus stable, aucune prise de focus par une réponse
   tardive), **troncature à 320 px** (contrôle visuel FR/EN) et **glissement
   horizontal de la grille au clavier**. (Automate : FR/EN + Tab/Entrée/Échap
   web seulement.)
9. **GD-01, GD-02, GD-03 — parcours intersurfaces réels** : bascule
   Chaînes → Guide, quitter/rouvrir, changement de source pendant une requête
   (fenêtre « en vol » non reprochable localement). (Automate : aucun.)
10. **Preuve réseau Android à l'écran** : comptage `LumoHttp` à 3/50/100 chaînes
    sur téléphone/TV réels, cache d'abord. (Automate : contrat API + client
    unitaire seulement.)
11. **Parcours complet TV réelle** : accueil → Direct → Guide → fiche → lecture →
    retour, vérifier qu'aucune croix n'atterrit sur un élément masqué et que le
    focus revient à la case lancée.

> Rappel §7 du protocole : aucun cas « vu sur émulateur » n'est tenu pour vert.
> Tant que cette liste n'est pas jouée, le critère de sortie S9-07 reste **non
> atteint**.
