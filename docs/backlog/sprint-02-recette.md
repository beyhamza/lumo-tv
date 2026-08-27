# Recette — Sprint 2

Plan de qualification de la verticale sur les deux applications Android. Il se
déroule **à la main, sur des devices réels**, et il ne remplace ni les tests
unitaires ni la CI : il vérifie ce qu'aucun des deux ne voit — qu'un utilisateur qui
n'a pas écrit le code arrive au bout.

Chaque cas renvoie à la story du [sprint 1](./sprint-01.md), dont les critères
Gherkin font foi. Un cas rouge bloque la Definition of Done de sa story.

---

## 1. Prérequis

Sans ces quatre points, la recette n'est pas exécutable — et surtout, la moitié
« erreurs » ne l'est pas du tout.

**Le banc d'essai des sources (S2-03) tourne, et le téléphone l'atteint.** C'est lui
qui permet de provoquer un refus d'identifiants, une playlist vide ou une réponse
hors plafond. Six chemins, et les six sont servis :

| Chemin | URL sur le banc | Sert à | Code attendu |
|---|---|---|---|
| playlist valide | `/playlist.m3u` | R-20, R-30 | — |
| playlist vide | `/empty.m3u` | R-32 | `SOURCE_EMPTY` |
| réponse non M3U | `/not-a-playlist.html` | R-31 | `SOURCE_INVALID_FORMAT` |
| panel Xtream en 401 | `/xtream-401/` | R-22 | `SOURCE_AUTH_FAILED` |
| hôte qui ne répond pas | `192.0.2.1` | R-23 | `SOURCE_UNREACHABLE` |
| réponse hors plafond | `/oversized.m3u` | R-33 | `SOURCE_TOO_LARGE` |

L'hôte muet n'est pas sur le banc et n'a pas à l'être : `192.0.2.1` est TEST-NET-1,
réservé par la RFC 5737 et routé nulle part. Meilleure reproduction que tout ce qu'un
conteneur pourrait simuler.

**Le démarrer, avec l'adresse que le téléphone verra :**

```bash
BENCH_PUBLIC_URL=http://192.168.1.20:18081 LUMO_INGEST_ALLOW_PRIVATE_HOSTS=true docker compose --profile bench --env-file apps/api/.env up -d
```

Remplacer `192.168.1.20` par l'adresse de la machine **sur le réseau local**. Les deux
variables répondent à deux problèmes différents, et oublier l'une ou l'autre produit
une panne qui ne ressemble pas à sa cause :

- **`BENCH_PUBLIC_URL`** est l'adresse écrite dans les URL de flux de la playlist.
  Sans elle, la playlist nomme `localhost:18081` — qui, depuis un téléphone, est le
  téléphone. La source s'importe très bien et **aucune chaîne ne se lance**.
- **`LUMO_INGEST_ALLOW_PRIVATE_HOSTS`** lève le refus des adresses privées. L'API
  refuse par défaut qu'une source pointe à l'intérieur de son propre réseau — et doit
  continuer de le refuser en production, où c'est un moyen de cartographier notre
  infrastructure. Sans elle, le banc sur le réseau local est refusé comme
  `SOURCE_UNREACHABLE`, ce qui ressemble à un banc éteint.

Le vérifier avant de commencer, depuis le téléphone lui-même, dans un navigateur :
`http://192.168.1.20:18081/playlist.m3u` doit s'afficher et nommer cinq chaînes.

**Matériel.** Un téléphone Android physique, une box ou un téléviseur Android TV
physique **avec sa télécommande**. L'émulateur ne vaut que pour un pré-test : il ne
révèle ni un élément inatteignable au D-pad, ni un texte illisible à trois mètres.

**Comptes.** Deux comptes de test, l'un vierge, l'autre avec une source déjà
enregistrée. Un compte Google de test pour R-15 et R-16.

**Langue.** Chaque cas se rejoue en français **et** en anglais. Le français est
environ 30 % plus long : c'est là que les mises en page cassent.

---

## 2. La règle qui prime sur la recette elle-même

**Aucune source réelle, à aucun moment.** Pas de vraie playlist, pas d'identifiants
d'un vrai fournisseur, pas de nom de chaîne ni de logo de bouquet — ni dans le banc
d'essai, ni dans les captures d'écran jointes à un rapport de recette, ni dans un
ticket ouvert pendant la session (AGENTS.md §1).

Les flux de test sont libres de droits : Big Buck Bunny, flux de test Apple HLS,
assets de test Mux.

Un recetteur qui saisit son propre abonnement pour « voir si ça marche » vient de
mettre des identifiants dans une base de test et, potentiellement, dans une capture.
C'est un incident, pas un raccourci.

---

## 3. Authentification

**R-10 · Inscription nominale** · US-01 · téléphone
Compte vierge. Saisir un email valide et un mot de passe d'au moins 10 caractères.
→ Le compte est créé, la session est ouverte immédiatement, un email de vérification
part.

**R-11 · Mot de passe refusé avant soumission** · US-01 · téléphone
Saisir un mot de passe trop court.
→ Le bouton reste **désactivé** et la règle non respectée s'affiche **avant** toute
soumission. Un message qui n'apparaît qu'après avoir appuyé est un échec.

**R-12 · Email déjà enregistré** · US-01 · téléphone
Reprendre l'email de R-10.
→ Message invitant à se connecter ou à réinitialiser. **Il ne doit pas être possible
de conclure que l'adresse existe** — ni par le texte, ni par un délai de réponse
visiblement différent.

**R-13 · Connexion nominale** · US-02 · téléphone
→ Écran d'accueil. Tuer l'application, la rouvrir : toujours connecté.

**R-14 · Identifiants invalides** · US-02 · téléphone
→ Message générique. Après cinq échecs, l'attente imposée s'affiche comme une
attente, pas comme une panne.

**R-15 · Google, premier usage** · US-03 · téléphone
→ Compte créé depuis l'email Google, session ouverte.

**R-16 · Google sur un email déjà enregistré** · US-03 · téléphone
Compte créé en R-10, puis Google avec le même email.
→ L'identité Google est rattachée au compte existant. **Aucun doublon.** À vérifier
côté serveur, pas seulement à l'écran.

**R-17 · Rafraîchissement transparent** · US-04 · téléphone
Laisser l'access token expirer (15 min), puis agir.
→ L'action aboutit, rien n'est visible pour l'utilisateur.

**R-18 · API injoignable ≠ déconnexion** · US-04 · téléphone
Couper l'API, agir, la remettre.
→ L'utilisateur **reste connecté**. Déconnecter quelqu'un parce que le serveur
redémarre est précisément le bug que ce cas cherche.

---

## 4. Sources

**R-20 · Playlist M3U valide** · US-07 · téléphone
→ Chaînes importées avec groupes et logos, **nombre de chaînes trouvées affiché**.
Les chaînes sans `group-title` tombent dans « Non classé ».

**R-21 · Source Xtream valide** · US-06 · téléphone
→ Validée en moins de dix secondes, nombre de chaînes et date d'expiration du compte
affichés, statut `READY`.

**R-22 · Identifiants Xtream refusés** · US-06 · téléphone
→ « Vos identifiants ont été refusés par le serveur ». **Le formulaire conserve
l'hôte** : on ne retape que ce qui est faux.

**R-23 · Serveur injoignable** · US-06 · téléphone
→ Message qu'on ne peut **pas confondre** avec R-22, et un bouton Réessayer. Ce cas
et le précédent se relisent côte à côte : si les deux messages pourraient être
intervertis sans que ça choque, c'est rouge.

**R-24 · Tolérance de saisie de l'hôte** · US-06 · téléphone
Saisir l'hôte sans schéma, puis avec un port, puis avec un slash final.
→ Les trois sont acceptés et normalisés. Aucun rejet.

**R-30 · Ingestion asynchrone visible** · US-06, US-07 · téléphone
→ L'écran montre un état d'attente pendant `PENDING`/`SYNCING` puis bascule seul.
Aucun blocage de l'interface.

**R-31 · Contenu non conforme** · US-07 · téléphone
→ `SOURCE_INVALID_FORMAT`, avec une explication de ce à quoi ressemble une URL M3U
correcte. Un code brut à l'écran est rouge.

**R-32 · Playlist vide** · US-07 · téléphone
→ `SOURCE_EMPTY`. **Jamais d'écran de succès sur une liste vide.**

**R-33 · Playlist trop grande** · US-07 · téléphone
→ `SOURCE_TOO_LARGE`, message actionnable.

**R-34 · Le mot de passe ne revient jamais** · US-06 · téléphone
Rouvrir la source en édition.
→ Hôte et utilisateur pré-remplis, **champ mot de passe vide**. Aucun écran, à aucun
moment, n'affiche le mot de passe Xtream.

---

## 5. Catalogue et lecture — téléphone

**R-40 · Catégories et comptes** · US-08 · téléphone
→ Catégories listées avec le nombre de chaînes de chacune.

**R-41 · Pagination fluide** · US-08 · téléphone
Source de plus de 500 chaînes.
→ Défilement sans à-coup, sans écran blanc intermédiaire.

**R-42 · Hors ligne** · US-08 · téléphone
Synchroniser, couper le réseau, rouvrir.
→ La liste s'affiche depuis le cache, **un indicateur discret signale le mode hors
ligne**. Discret : il informe, il ne s'excuse pas en plein écran.

**R-43 · Lecture** · US-09 · téléphone
→ Image en moins de cinq secondes sur une connexion normale.

**R-44 · Plein écran et rotation** · US-09 · téléphone
→ Passage en plein écran et rotation **sans interrompre la lecture**.

**R-45 · Flux indisponible** · US-09 · téléphone
Chaîne pointant une URL morte.
→ Message clair et bouton Réessayer. Ni plantage, **ni écran noir muet**.

**R-46 · Limite de connexions atteinte** · US-09 · téléphone
Ouvrir autant de flux que l'abonnement de test en autorise, puis un de plus.
→ Message expliquant que l'abonnement limite les flux simultanés — pas une erreur
technique.

**R-47 · Interruptions** · US-09 · téléphone
Appel entrant, coupure réseau, mise en veille.
→ Comportement propre au retour, pas de lecture fantôme, pas de wake lock resté armé.

---

## 6. Télévision

Tous ces cas se font **télécommande en main**. Une souris sur un émulateur invalide
le résultat.

**R-50 · Code et QR** · US-05 · TV
→ Code à 8 caractères et QR très grands, lisibles à trois mètres, avec l'instruction
« rendez-vous sur lumo.tv/activate ».

**R-51 · Activation nominale** · US-05 · TV + téléphone
Saisir le code sur `lumo.tv/activate` en étant connecté.
→ La TV se connecte **en moins de dix secondes**, sans action sur la télécommande.

**R-52 · Le QR évite la saisie** · US-05 · TV + téléphone
Scanner le QR.
→ La page s'ouvre avec le code **déjà rempli**.

**R-53 · Code expiré** · US-05 · TV
Attendre l'expiration (dix minutes).
→ La télé affiche un **nouveau code toute seule**, sans intervention.

**R-54 · Code déjà utilisé** · US-05 · téléphone
Ressaisir un code déjà consommé.
→ Message explicite, distinct de « code inconnu ».

**R-55 · Code inconnu, puis temporisation** · US-05 · téléphone
Cinq codes invalides.
→ Message d'erreur explicite, puis temporisation annoncée.

**R-60 · Rien n'est inatteignable au D-pad** · US-08, US-10 · TV
Parcourir chaque écran uniquement aux flèches et à OK.
→ **Chaque élément interactif est atteignable.** Si on ne peut pas décrire la
séquence de touches pour l'atteindre, c'est rouge. À croiser avec la carte du
parcours de focus livrée en S2-13.

**R-61 · Le focus est évident** · US-10 · TV
À trois mètres, écran mal calibré si possible.
→ L'élément focalisé est identifiable **instantanément**. Une simple variation de
couleur est rouge.

**R-62 · Overscan** · US-08 · TV
→ Aucun élément critique dans les 5 % de bord.

**R-63 · Lecture à la télécommande** · US-10 · TV
→ D-pad puis OK démarre la lecture en plein écran.

**R-64 · Overlay d'information** · US-10 · TV
OK pendant la lecture.
→ Barre avec le nom de la chaîne, **disparition après cinq secondes** d'inactivité.

**R-65 · Retour positionné** · US-10 · TV
BACK depuis le lecteur.
→ Retour à la liste, **positionné sur la chaîne qu'on regardait**. Un retour en tête
de liste est rouge.

---

## 7. Cas transverses

Ceux-là ne portent aucune story et bloquent quand même la Definition of Done.

**R-70 · Aucun secret dans les logs** · les deux surfaces
Dérouler un parcours complet, application en `DEBUG`, puis :

```bash
adb logcat -d | grep -iE "m3u8?|player_api|password|Bearer |refresh_token"
```

→ **Aucune ligne.** Ni URL de flux, ni mot de passe Xtream, ni token. Ce cas se
rejoue après chaque tâche qui touche à la lecture ou à la session.

**R-71 · Les deux langues tiennent** · les deux surfaces
Rejouer les écrans en FR puis en EN.
→ Aucun texte tronqué, aucun débordement, aucune chaîne non traduite.

**R-72 · Aucune chaîne en dur**
Basculer la langue du système en cours de session.
→ Tout suit. Un libellé resté figé est une chaîne codée en dur.

**R-73 · Aucun contenu réel dans le livrable**
Relire les captures et les données de test avant de clore la session.
→ Aucun nom de chaîne, aucun logo de bouquet, aucune URL réelle.

---

## 8. Critère de sortie

La recette est verte quand **tous les cas ci-dessus passent, dans les deux langues,
sur device réel, télécommande en main pour la section 6**.

Un cas rouge bloque sa story, pas le sprint entier — sauf R-70 et R-73, qui bloquent
la livraison quelle que soit la story concernée.

Le rapport de session note, pour chaque cas : vert, rouge, ou non joué avec la raison.
« Non joué » est une réponse acceptable et traçable ; « probablement bon » ne l'est
pas.
