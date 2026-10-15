# Index des passages de recette — 0.3.0

| Date | Passage | Objet | Base | Verdict | Captures décisives |
|---|---|---|---|---|---|
| 15/10/2026 | [`s11-06-recette-2026-10-15`](s11-06-recette-2026-10-15/RAPPORT.md) | S11-06 — recette entre appareils : favoris de source distincte, lecture sans effet sur la liste, G4 sur appareil | `dev` @ `0de950b` | ⚠️ 16 conformes · 7 non conformes · 4 non joués — 10 écarts (`BUG-S11-06-01` → `10`), dont 3 majeurs | [faux échec suppression de groupe](s11-06-recette-2026-10-15/ecrans/r08-bug01-mobile-faux-echec-suppression-groupe.png), [fiche orpheline après Continuer](s11-06-recette-2026-10-15/ecrans/r20-bug02-tv-fiche-orpheline-apres-continuer.png), [favoris web en panne](s11-06-recette-2026-10-15/ecrans/r18-bug03-web-favoris-panne-erreur-generique.png) |
| 15/10/2026 | [`s11-06-correctifs-2026-10-15`](s11-06-correctifs-2026-10-15/RAPPORT.md) | Rejeu sur émulateur des correctifs `BUG-S11-06-02`, `-04`, `-06` | `dev` @ `eea2898` | ✅ 3/3 conformes | [Continue → accueil](s11-06-correctifs-2026-10-15/ecrans/bug02-tv-film-continue-accueil.png), [fiche hors ligne](s11-06-correctifs-2026-10-15/ecrans/bug04-mobile-fr-fiche-sans-cache-hors-ligne.png), [carte indisponible](s11-06-correctifs-2026-10-15/ecrans/bug06-mobile-fr-carte-indisponible-type.png) |

## Fichiers écartés

| Passage | Fichier | Taille | Raison |
|---|---|---|---|
| `s11-06-recette-2026-10-15` | captures de connexion (`p00`→`p04`) | ~0,5 Mio | montrent l'adresse e-mail du compte de recette, sans verdict |
| `s11-06-recette-2026-10-15` | captures de navigation intermédiaires (`t00`-`t02`, `t06`, `t09`, `t12`, `t18`, `t26`, `t30`-`t32`, `p05`-`p07`, `p09`, `p11`, `p13`, `p27`, `w00`, `w03`, `w07`, `w08`, `w12`) | ~2 Mio | étapes sans verdict, ou doublons d'une capture gardée |
