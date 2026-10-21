# Captures Play — 19/10/2026 (S16B-05)

Prises sur émulateur, **banc de test uniquement** : la source est
`playlist-100.m3u` du banc (`apps/web/e2e/bench/`, chaînes « Chaîne 001 » à
« Chaîne 100 », guide `guide-big.xml` généré, flux mire libre de droits), dans un
compte jetable créé pour l'occasion sur l'API locale. Aucune vraie chaîne, aucun
logo, aucune URL réelle. Le libellé de la source a été renommé « Ma playlist »
pour ne pas être tronqué.

| Dossier | Format Play | Appareil |
|---|---|---|
| `telephone/fr`, `telephone/en` | 1080 × 1920 (9:16, dans la limite « côté long ≤ 2 × côté court ») | AVD Pixel 10 (API 37), écran forcé à 1080 × 1920, barre d'état en mode démo (10:00, batterie pleine) |
| `tv/fr`, `tv/en` | 1920 × 1080 (16:9, exigé pour Android TV) | AVD Television 1080p (Android TV, API 36) |

Ordre conseillé dans la Console : celui des numéros. La première capture
téléphone est l'ajout de source, parce qu'elle porte elle-même la phrase « Lumo
ne fournit aucun contenu : vous apportez le vôtre » — c'est ce que le
vérificateur doit voir en premier.

Construction utilisée : APK debug, interface identique à la 0.4.0 pour ces
écrans. Côté téléphone, l'émulateur de capture a été partagé avec la session
S16B-02/03, qui y avait installé sa propre build de travail ; aucun des écrans
retenus ne montre ses changements (Réglages → Compte n'est pas capturé).

`verifications/tv-banniere-dans-le-lanceur.png` n'est **pas** une capture de
fiche : c'est la preuve que la bannière TV s'affiche dans le lanceur, et qu'elle
n'y porte pas le nom de l'application (voir [`../exigences-play.md`](../exigences-play.md)).

## Écartés, et pourquoi

- **Lecteur téléphone** : en portrait la mire est recadrée, en paysage une bande
  blanche apparaît du côté de l'encoche. Défaut d'affichage à suivre au backlog,
  pas une capture de fiche.
- **Grille de chaînes TV** (« Direct → Chaînes ») : à 1080p / xhdpi, les tuiles
  coupent les noms (« Cha / îne… »). Défaut d'affichage au backlog ; la capture
  du guide TV montre aussi une seule ligne de chaîne visible.
- **Écran d'activation TV** : il affiche l'adresse de la pile locale
  (`localhost:3000`), donnée par l'API de développement. À reprendre sur la
  pile de production si on veut le montrer.
