# Design system — direction « Spectre »

Source des valeurs : `lumo-tokens.json` (projet Claude Design). **Aucune valeur
ne doit être redéfinie ailleurs** : les plateformes dérivent, elles ne
réinventent pas.

## Principe

Thème sombre par défaut — une application vidéo se regarde dans le noir. Les
surfaces sont **translucides et superposées**, jamais ombrées : l'élévation se
lit en opacité, pas en ombre portée. La couleur de marque est un dégradé
cyan → violet, réservé à trois usages (marque, focus, progression) et **jamais
utilisé en fond**.

Le thème clair existe, et seulement sur le web marketing.

## Couleur

| Token | Valeur | Usage |
|---|---|---|
| `bg` | `#0D0C12` | Fond application |
| `surface-1` | `rgba(255,255,255,0.05)` | Cartes, lignes de liste |
| `surface-2` | `rgba(255,255,255,0.09)` | Élément sélectionné, bouton secondaire |
| `surface-3` | `rgba(255,255,255,0.13)` | État pressé |
| `border` | `#26232F` | Séparateurs, contours de champs |
| `text-primary` | `#F2F0F7` | 17,1:1 sur `bg` — AAA |
| `text-secondary` | `#A29FB3` | 7,2:1 sur `bg` — AAA |
| `text-muted` | `#6D6980` | **Décoratif uniquement.** Jamais seul porteur d'information |
| `accent-cyan` | `#6EE7F0` | Focus, liens, état actif |
| `accent-violet` | `#A78BFA` | Second point du dégradé |
| `gradient-brand` | `linear-gradient(90deg,#6EE7F0,#A78BFA)` | Marque, focus, progression |
| `on-accent` | `#0D0C12` | Texte posé sur un accent |
| `danger` | `#FF7A8A` | Erreur |

**Thème clair — web marketing uniquement** : `bg #F7F6FA`, `surface-1 #FFFFFF`,
`text-primary #17141F`, `text-secondary #55516A`.

Le `text-muted` mérite son avertissement : sur la maquette W3 il porte
« 1 248 chaînes · vérifiée il y a 2 h ». C'est acceptable parce que la même
information est portée ailleurs (la pastille d'état) ; ce ne le serait pas si
c'était le seul indice.

## Espacement

Base 4. Échelle : `1`=4, `2`=8, `3`=12, `4`=16, `5`=24, `6`=32, `7`=48, `8`=64.

## Rayons

`sm`=8, `md`=14, `lg`=20, `pill`=999. Sur TV, ×1,5 — la distance de visionnage
aplatit la perception des courbes.

## Typographie

**Sora** (fallback `system-ui, sans-serif`) pour tout. **Space Mono** pour les
métadonnées techniques uniquement : débit, codec, horodatage, code
d'activation, compteurs.

| Rôle | Taille | Graisse | Interlignage | Approche |
|---|---|---|---|---|
| `display` | 32 | 600 | 1.1 | −0.02em |
| `title` | 20 | 600 | 1.2 | — |
| `body` | 15 | 300 | 1.5 | — |
| `detail` | 12 | 500 | 1.4 | +0.1em, capitales |

La graisse 300 en corps de texte est un choix, pas un oubli : c'est ce qui
distingue Lumo des lecteurs IPTV existants, tous en 400/500 serré.

## Mouvement

| Token | Valeur | Portée |
|---|---|---|
| `duration-fast` | 120 ms | focus, survol |
| `duration-base` | 260 ms | transitions de vue, rails |
| `duration-slow` | 420 ms | entrée du lecteur, overlays |
| `easing-standard` | `cubic-bezier(0.3,0.7,0,1)` | — |
| `easing-exit` | `cubic-bezier(0.4,0,1,1)` | — |

## Élévation et focus

Transparence superposée, jamais d'ombre portée. Le focus est un
**outline 2 px `accent-cyan`, offset 3 px**, posé sur `surface-2`.

## Dérivations

| Plateforme | Type | Espace | Rayon | Notes |
|---|---|---|---|---|
| mobile | ×1,0 | ×1,0 | ×1,0 | cible tactile ≥ 44 |
| tv | ×1,75 | ×1,5 | ×1,5 | focus obligatoire, AAA partout, overscan 5 % — `body` ≥ 24 px non négociable à 3 m |
| web | ×1,1 | ×1,0 | ×1,0 | deux thèmes : `dark`, `light-marketing` |

Valeurs web dérivées : `display` 35, `title` 22, `body` 16, `detail` 13.

## État de l'implémentation

`apps/web/src/app/globals.css` porte aujourd'hui **la palette shadcn par
défaut** (`oklch(1 0 0)`, `oklch(0.145 0 0)`, …), pas la direction Spectre.
S0-07 est coché au backlog au sens « des tokens existent », pas au sens
« ce sont ceux-là ». Reprendre `globals.css` depuis ce document est un travail
en soi, à scoper comme tel — pas à glisser dans une story d'écran.
