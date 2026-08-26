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

| Surface | État |
|---|---|
| `apps/web` | ✅ aligné le 26 août 2026 |
| `apps/android` | ❌ palette différente — tâche `S2-00` |

**Web.** `apps/web/src/app/globals.css` porte la charte : palette complète dans
les deux thèmes, Sora et Space Mono via `next/font`, rayons, durées et courbes,
et la signature de focus en outline cyan décollé de 3 px.

Deux décisions de mise en œuvre valent d'être connues avant d'y toucher.

**Le thème clair est sur `:root`, le sombre sur `.dark`** — l'inverse de la
lecture littérale de la charte. Sur le web, et seulement là, le marketing est
clair et l'applicatif est sombre ; garder la convention shadcn évite de
réécrire le variant `dark:` que les composants utilisent déjà. La classe `dark`
est posée par les layouts des zones compte, authentification et activation, sur
un conteneur qui remplit la hauteur : `<body>` prend le fond de `:root`, donc un
conteneur sombre qui ne couvre pas tout laisse une bande claire.

**`--radius` vaut 14 px et l'échelle shadcn se dérive de là.** Les trois valeurs
de la charte retombent sur `rounded-sm` (8), `rounded-lg` (14) et `rounded-xl`
(20). Écrire 8/14/20 directement dans `--radius-sm/md/lg` paraît plus direct et
donne un résultat faux : `sm`, `md`, `lg` sont des noms de tokens, pas des noms
d'utilitaires Tailwind, et cette lecture a doublé le rayon de tous les champs de
saisie d'un coup.

**Une dérivation assumée.** La charte ne définit `danger` que pour le sombre.
`#FF7A8A` sur `#F7F6FA` tombe à 2,5:1, illisible ; le thème clair utilise
`#C2334A`, même teinte, assombrie jusqu'à passer AA en texte courant.

**Android.** `LumoTokens.kt` porte une palette qui n'est pas Spectre — un bleu
froid `#4CB8FF`, une encre `#07090F`. À aligner **avant le premier écran** du
sprint 2, sinon la reprise coûte dix fois plus. La typographie est un point
ouvert : la charte impose Sora, `LumoTypography.kt` argumente pour la police
système sur une box TV. À trancher, pas à décider seul.
