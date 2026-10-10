# R020-01 — web (§3.3 W-1, W-2) — 10/10/2026

- Objet : `W-1` et `W-2` du plan [`r020-01-isolation-matrix.md`](../../r020-01-isolation-matrix.md).
- Builds : web depuis `fix/BUG-R020-01-01-signout-purge` (code web identique à `dev`), joué deux
  fois — **serveur de dev** (`pnpm dev`, port 3000) puis **build de production** (`pnpm start`,
  port 3001, celui de la CI e2e). API locale reconstruite depuis `dev`. Navigateur intégré
  (Chromium).
- Mêmes comptes de recette que les passages TV et mobile ; connexion par le formulaire.

## W-1 — A connecté, déconnexion, B connecté : ✅ conforme

- A connecté : accueil avec la source « Banc R020-01 A », favori « Chaîne 05 », récente
  « Chaîne 03 HD ». Côté JavaScript : `document.cookie` vide, `localStorage` et `sessionStorage`
  vides (session en cookie httpOnly, comme prévu).
- Déconnexion (Réglages › Compte › Se déconnecter) : redirection vers `/fr/login`.
- B connecté dans le même navigateur : accueil, Réglages et bibliothèque relus sans cache
  (`fetch … cache: 'no-store'`) — **aucune occurrence** de l'email, de la source ou des chaînes de A ;
  profil et source de B.

## W-2 — retour arrière après déconnexion : ✅ conforme en production, ⚠️ pas en dev

| Build | `Cache-Control` des pages `/app` | Retour arrière après déconnexion |
|---|---|---|
| dev (`pnpm dev`) | `no-cache, must-revalidate` | la page Réglages **de A** réapparaît depuis le cache (navigation `back_forward`, 0 octet transféré) ; une requête fraîche, elle, redirige vers la connexion |
| production (`pnpm start`) | `private, no-cache, no-store, max-age=0, must-revalidate` | page rechargée depuis le serveur (5 492 octets), **redirection vers `/fr/login`**, rien de A |

Le défaut n'existe que sur le serveur de dev, qui ne sort pas. Aucun lot ouvert ; à garder en tête
pour une recette jouée sur `pnpm dev`, qui pourrait conclure à tort à une fuite.

## Écart `BUG-R020-01-02` — cookies de préférence du compte après déconnexion web (par lecture de code)

`signOut` (`apps/web/src/actions/auth.ts`) appelle `closeSession()`, qui n'efface que le cookie de
session. Restent dans le navigateur, httpOnly :

- `lumo_active_source_<userId>` — un an ; son **nom porte l'identifiant du compte**, sa valeur
  l'identifiant d'une source ;
- `lumo_direct_view_<sourceId>` — la vue Direct mémorisée par source.

Ils ne sont écrits que par une action explicite (choix d'une source, changement de vue) : le compte
de recette A, qui n'en a fait aucune, n'en avait probablement pas, et le navigateur intégré masque
l'en-tête `Cookie` — **constat par lecture du code, non observé**. B ne les lit pas (clés propres à A
et à ses sources) : rien n'est visible ni réutilisable, mais « sans reste » n'est pas tenu, comme pour
`BUG-R020-01-01` sur Android. Gravité faible : identifiants opaques, pas de contenu. Correctif
probable : effacer `lumo_active_source_*` et `lumo_direct_view_*` dans `signOut`.

## Non joué

- `W-4` (jeton A rejoué après déconnexion) : couvert côté API pour le refresh (A-7/A-8) ; le jeton
  d'accès reste accepté jusqu'à son expiration (≤ 15 min), observation toujours à arbitrer.
