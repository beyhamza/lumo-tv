# S15-00 — Essai Cast sur le Default Media Receiver — 19 octobre 2026

Version **0.4.0**, sprint 15, story [US-027](../../../../backlog/stories/US-027-cast.md), décision
[ADR 0013](../../../../adr/0013-advanced-playback-pip-cast.md). Lecture directe, sans relais ni
transcodage ([ADR 0007](../../../../adr/0007-web-playback-direct-only.md)).

## En une phrase

**Aucun Chromecast n'était disponible**, donc rien n'a été lu sur un vrai récepteur. Le tableau
s'appuie sur la documentation officielle Google Cast, sur le comportement HTTP mesuré du banc et
sur une sonde dans un Chromium de bureau. Ce Chromium remplace mal le récepteur, et le tableau
distingue ces trois sources. La recommandation est de **garder le Default Media Receiver pour S16**,
sans nouvel ADR, avec une première tâche bloquante : rejouer ce tableau sur un appareil réel.

## Conditions de l'essai

| Élément | Valeur |
|---|---|
| Récepteur réel | **Aucun.** Une requête mDNS `_googlecast._tcp.local` sur les trois interfaces IPv4 du poste (dont le réseau domestique) n'a reçu aucune réponse. L'émetteur prototype voit `NO_DEVICES_AVAILABLE`, et `requestSession()` est rejeté avec `session_error`. L'émulateur Android TV n'embarque pas de récepteur Cast. |
| Banc | `lumo-bench`, `http://localhost:18081`, fixtures libres de droits : six secondes de mire et de sinus. H.264 baseline et AAC en HLS (segments TS), en MP4 faststart et en MKV ([README du banc](../../../../../apps/web/e2e/bench/README.md)). |
| Émetteur prototype | [`prototype/sender.html`](prototype/sender.html), hors code produit. Il charge le Cast Web Sender, utilise `DEFAULT_MEDIA_RECEIVER_APP_ID`, propose un bouton par fixture et journalise tous les signaux d'échec. Il contient aussi une sonde qui tourne sans appareil. |
| Navigateur de la sonde | Chromium 152 (navigateur intégré de l'app Claude, Windows 11). L'API Cast y est disponible. |
| Traces brutes | [`probe-2026-10-19.txt`](probe-2026-10-19.txt) |

Légende de la colonne *Source* :

- **M-banc** : comportement HTTP du banc, mesuré avec `curl`.
- **M-sonde** : mesuré dans le Chromium de bureau. C'est un indice sur le Chromium du récepteur, **pas une mesure du récepteur**.
- **D** : documentation officielle Google Cast (liens en fin de rapport).
- **I** : inférence ou pratique connue, non documentée par Google. À confirmer sur appareil.

## Tableau format × condition × résultat

| # | Format | Condition (fixture du banc) | Résultat attendu sur le Default Media Receiver | Source |
|---|---|---|---|---|
| 1 | HLS, segments TS, H.264 + AAC | `https`, CORS `*` (`/stream/`) | **Accepté.** HLS est lu par MSE ; sans indication de format, le lecteur suppose des segments TS. Envoyer `contentType: application/x-mpegurl`. | D |
| 2 | HLS | `http`, CORS `*` (`/stream/`, `/live/…`) | **Inconnu.** Le récepteur est une page `https`, et un fetch MSE en `http` est un contenu mixte actif. Google n'en parle nulle part. | I, à mesurer en premier |
| 3 | HLS | **sans CORS** (`/stream-nocors/`) | **Refusé.** Google écrit « you must implement CORS » pour les protocoles adaptatifs. Dans la sonde, le fetch cross-origin est bloqué (`TypeError`). Le Chromium de bureau lit pourtant ce flux en `<video>` natif : la lecture locale et la lecture sur Cast ne se ressemblent donc pas. Erreur attendue : 311/312 sans statut HTTP, ou 301. | D + M-sonde (fetch bloqué) + M-banc (pas d'`Access-Control-Allow-Origin`) |
| 4 | HLS, segments fMP4/CMAF | CORS | Accepté si l'émetteur envoie `hlsSegmentFormat` / `hlsVideoSegmentFormat` = `fmp4`. Sinon le lecteur suppose du TS, et l'échec attendu est 315/316. Le banc n'a pas de fixture fMP4. | D |
| 5 | MP4, H.264 + AAC | Range (`/film/`, `/movie/…`) | **Accepté**, avec recherche dans le temps. MP4 est un conteneur documenté et la lecture progressive aussi. La sonde lit le fichier et le parcourt jusqu'à 6,00 s. | D + M-sonde + M-banc (206, `Accept-Ranges`) |
| 6 | MP4 | **sans Range** (`/film-norange/`) | **Lu depuis le début, sans avance ni recul.** Google ne dit rien sur Range. Dans la sonde, la lecture marche mais la zone parcourable s'arrête à 0. Reprendre à une position (« Continuer ») est donc impossible. | M-sonde + M-banc (200, corps entier) ; I pour le récepteur |
| 7 | MP4 | `http` | **Probablement accepté.** La lecture progressive passe par `<video src>`, un contenu mixte passif que Chromium tolère. Des outils libres courants envoient du `http` local au récepteur par défaut. Google ne l'écrit pas. | I |
| 8 | MKV (H.264 + AAC) | Range (`/film/la-traversee.mkv`) | **Non garanti.** MKV ne figure pas dans la liste des conteneurs pris en charge (MP2T, MP3, MP4, OGG, WAV, WebM). La sonde le lit (`canPlayType` « maybe », métadonnées lues), mais le récepteur peut répondre 104 `MEDIA_SRC_NOT_SUPPORTED`. Résultat probablement variable selon l'appareil. | D (absent de la liste) + M-sonde ; I pour le récepteur |
| 9 | MPEG-TS brut progressif (`.ts`, chaîne en direct d'un panel Xtream) | CORS (`/stream/seg0.ts` en stand-in) | **Refus probable.** MP2T n'est documenté que comme conteneur de segments HLS. Dans la sonde, `canPlayType("video/mp2t")` est vide et `<video src>` tombe en erreur 4 (`MEDIA_ERR_SRC_NOT_SUPPORTED`) ; seul MSE l'accepte, après un démultiplexage que le récepteur par défaut ne fait pas hors HLS. HEVC en TS est exclu explicitement. | D + M-sonde ; I pour le récepteur |
| 10 | Audio AC-3 / E-AC-3 | n'importe lequel | **Passthrough seulement**, quand le téléviseur ou l'ampli le prend en charge par HDMI. Aucun décodage sur le Chromecast. Sinon : pas de son ou erreur de décodage. La vérification `canDisplayType()` n'existe que côté récepteur et l'émetteur ne la voit pas. La sonde de bureau ne décode ni l'un ni l'autre. Le banc n'a pas de fixture Dolby. | D + M-sonde |
| 11 | Vidéo HEVC / 4K / H.264 au-delà du niveau de l'appareil | n'importe lequel | Dépend du modèle. Chromecast 3ᵉ génération : H.264 jusqu'à 4.2 (1080p60), sans HEVC. Chromecast with Google TV : HEVC Main10 jusqu'à 5.1. Échec attendu : 102 / 104 / 110. | D |
| 12 | Tout format | flux qui exige un `User-Agent` (`user_agent` non nul dans `PlaybackInfo`) | **Refusé ou bloqué.** Le récepteur par défaut envoie son propre User-Agent et l'émetteur ne peut pas le changer. | D (aucun champ dans `MediaInfo`) + I |
| 13 | Tout format | abonnement `max_connections = 1` | Échec si le téléphone ou l'onglet lit encore. Le Chromecast ouvre **sa propre** connexion au fournisseur ; la lecture locale doit s'arrêter **avant** le `LOAD`. | I (déduit du contrat et d'ADR 0007) |

**Lecture du tableau pour un catalogue IPTV réel.** Les films et épisodes en MP4 passent, sans
avance ni recul quand le serveur ignore Range. Les films en MKV ou AVI, fréquents chez les panels
Xtream, sont incertains au mieux. Les chaînes en direct d'un panel Xtream sont le point faible :
en `.ts` brut, refus probable ; en `.m3u8`, elles passent seulement si le fournisseur envoie
l'en-tête CORS, ce qui est rare (« majority case in the wild », commentaire du banc sur
`/stream-nocors/`). **Une part importante des chaînes ne pourra pas être envoyée, et c'est attendu.**
Le critère d'US-027 qui exige de nommer le refus est donc le critère central de S16, pas un cas
limite.

## Comment l'émetteur détecte un refus

Le message « Ce flux ne peut pas être envoyé sur ce Chromecast » s'affiche à **quatre** signaux,
tous documentés. La lecture reste sur l'appareil (ADR 0013 §3) et reprend à la position atteinte.

| Signal | Web (Cast Web Sender) | Android (Cast SDK) |
|---|---|---|
| 1. Chargement refusé | Promesse de `CastSession.loadMedia()` rejetée avec un `chrome.cast.ErrorCode`, en pratique `LOAD_MEDIA_FAILED` | `RemoteMediaClient.load()` → `MediaChannelResult` en échec : `2100 FAILED` ou `2104 MEDIA_ERROR` |
| 2. Erreur détaillée | Message sur l'espace `urn:x-cast:com.google.cast.media`, `type` = `LOAD_FAILED` / `ERROR` / `INVALID_REQUEST`, avec `detailedErrorCode` (écouté par `session.addMessageListener`, comme dans le prototype) | `RemoteMediaClient.Callback.onMediaError(MediaError)` : `getType()` = `LOAD_FAILED`…, `getDetailedErrorCode()`, `getReason()` |
| 3. Échec après un démarrage | `RemotePlayer.playerState` passe à `IDLE` avec `idleReason = ERROR` (événement `PLAYER_STATE_CHANGED`) | `Callback.onStatusUpdated()` : `MediaStatus.getPlayerState() == IDLE` et `getIdleReason() == IDLE_REASON_ERROR` |
| 4. Silence | Ni lecture ni erreur après un délai : **15 s proposées**. C'est le cas d'un hôte injoignable ou d'un contenu mixte bloqué sans erreur remontée. | idem |

Codes `detailedErrorCode` utiles, d'après la page *Error codes* de Google :

- **Format** : `102 MEDIA_DECODE`, `104 MEDIA_SRC_NOT_SUPPORTED`, `110 SOURCE_BUFFER_FAILURE`,
  `315 HLS_NETWORK_INVALID_SEGMENT`, `316 HLS_SEGMENT_PARSING`, `411`/`412` (manifeste HLS
  illisible).
- **Réseau / CORS** : `103 MEDIA_NETWORK`, `300 NETWORK_UNKNOWN`, `301 SEGMENT_NETWORK`,
  `311 HLS_NETWORK_MASTER_PLAYLIST`, `312 HLS_NETWORK_PLAYLIST`. Les codes 301, 311 et 312 se
  suffixent d'un type d'erreur réseau et du statut HTTP (ex. `3016404`). **Un refus CORS ressemble
  à une erreur réseau sans statut HTTP** : l'émetteur ne le distingue pas d'un hôte injoignable.
- **Général** : `905 LOAD_FAILED`, `904 LOAD_INTERRUPTED` (un autre `LOAD` l'a remplacé : **pas**
  un refus, ne rien afficher), `909 GENERIC`.

Recommandations pour S16 :

- **Un seul message pour tous les refus.** Les codes ne permettent pas de distinguer CORS d'une
  panne réseau, et le critère d'acceptation ne demande qu'une phrase.
- **Garder le code détaillé.** Le journaliser sans l'URL (AGENTS.md §5 ; `stream_url` est
  `format: password`). Il sert au diagnostic de la recette S17.
- **Ignorer `LOAD_CANCELLED` / `904`**, ainsi que le refus de `requestSession()` quand
  l'utilisateur ferme le sélecteur (`cancel`).

## Faut-il un champ de contrat ?

Le contrat **n'a pas été modifié** (consigne de la tâche ; AGENTS.md §3). Aujourd'hui,
`PlaybackInfo`, `VodPlaybackInfo` et `EpisodePlaybackInfo` donnent `stream_url`, `user_agent`,
`max_connections` et `expires_at`. Avec l'URL seule, l'émetteur doit deviner le `contentType`
d'après l'extension, et les panels Xtream ajoutent souvent un jeton après l'extension (fixture
`/movie/` du banc). Deux ajouts seraient utiles. Ils sont proposés pour une décision en ouverture
de S16, à faire passer par `openapi.yaml` (ADR 0013 §4) :

1. **Utile : un indice de conteneur**, par exemple `container: hls | mp4 | mkv | ts | webm | other`,
   nullable, en lecture seule, sur les trois schémas. Le serveur le connaît déjà sans sonder :
   `container_extension` d'Xtream pour les films et épisodes, format de sortie pour les chaînes,
   extension de l'URL pour M3U. L'émetteur peut alors :
   - envoyer le bon `contentType` ;
   - signaler `ts` et `mkv` comme « peut ne pas passer » avant même un `LOAD` ;
   - ne plus déduire le format d'une URL sensible côté client.
2. **À discuter : une variante HLS des chaînes Xtream.** Beaucoup de panels annoncent
   `allowed_output_formats: ["m3u8","ts"]`. Une chaîne servie en `.ts` peut souvent l'être en
   `.m3u8`, et c'est la seule forme qui a une chance de passer sur Cast. Il s'agit d'une URL de plus
   (ou d'un paramètre `?format=hls` sur `GET /channels/{id}/playback`), pas d'un relais. Ça ne vaut
   que si le fournisseur envoie CORS, donc le gain se mesure sur appareil avant de toucher au
   contrat.

Pas de champ « castable ». Le serveur n'a pas les moyens de le savoir : CORS et codecs ne se
lisent qu'à la lecture, et le résultat dépend du modèle de Chromecast.

## Recommandation de périmètre pour S16

**Le Default Media Receiver suffit. Pas de nouvel ADR pour un récepteur personnalisé.**

Un récepteur personnalisé ne lèverait aucun des deux blocages majeurs :

- **CORS** reste exigé : un récepteur personnalisé est une page web qui lit par MSE, comme celui de
  Google.
- **Le `.ts` brut** pourrait être démultiplexé dans un récepteur à nous, mais seulement avec CORS.
  Cela couvre une petite minorité de flux, au prix d'un hébergement, d'un enregistrement chez Google
  et d'un second lecteur à maintenir (alternative déjà écartée par ADR 0013).
- **La seule vraie parade est un relais**, interdit par ADR 0007.

Le réexamen prévu par l'ADR (« si le récepteur par défaut refuse la plupart des flux ») ne se
justifie donc pas : un récepteur à nous refuserait les mêmes.

Périmètre proposé :

1. **S16-00 (bloquant, ½ jour) : rejouer ce tableau sur un appareil réel.** Prêter ou acheter un
   Chromecast with Google TV, ou un téléviseur Google TV avec Cast intégré, et lancer
   [`prototype/sender.html`](prototype/sender.html) avec l'adresse LAN du banc. Lignes à trancher
   en priorité : 2 (HLS en `http`), 7 (MP4 en `http`), 8 (MKV), 9 (`.ts` brut). Ajouter au banc
   une fixture AC-3 et une fixture HLS fMP4, libres de droits et générées par ffmpeg comme les
   autres (lignes 4 et 10). Un tag `https` du banc (certificat local) sert les lignes 1 et 5.
2. **Émetteur web et Android sur le Default Media Receiver**, `contentType` choisi d'après le
   conteneur : HLS (`application/x-mpegurl`, `hlsSegmentFormat` si connu), MP4 (`video/mp4`), MKV
   tenté (`video/x-matroska`). Titre et affiche dans `GenericMediaMetadata`, `streamType` `LIVE`
   pour une chaîne, `BUFFERED` sinon.
3. **Les quatre signaux de refus** ci-dessus, avec le message unique et le retour à la lecture
   locale à la position atteinte.
4. **Ordre de passage** : arrêter la lecture locale avant le `LOAD` (`max_connections`), reprendre
   en local si le `LOAD` échoue.
5. **Avance et recul désactivés** pour un film dont le serveur ignore Range, comme en local. Pour
   « Continuer », démarrer à 0 et le dire plutôt que d'envoyer une `currentTime` ignorée.
6. **Sources avec `user_agent`** : ne pas proposer Cast, ou afficher le message de refus d'emblée.
7. **Confidentialité** : l'URL envoyée contient souvent les identifiants Xtream. Elle quitte
   l'appareil vers le Chromecast et peut apparaître dans l'état de session de tout émetteur du
   réseau local. C'est inhérent à Cast et cohérent avec ADR 0007, mais à écrire dans l'aide et à ne
   jamais journaliser.
8. **Décision de contrat** (indice `container`) en ouverture de S16, avant le code émetteur.

## Ce que ce rapport ne prouve pas

- Aucun `LOAD` n'a atteint un récepteur. Chaque « accepté » du tableau vient de la documentation,
  aucun n'est mesuré.
- Le Chromium de bureau lit nativement du HLS sans CORS ; le récepteur passe par MSE. La sonde est
  donc **plus permissive** que le récepteur sur la ligne 3 et ne vaut pas preuve pour les lignes
  2, 7 et 8.
- Le banc ne sert ni `https`, ni HLS fMP4, ni AC-3/E-AC-3, ni `.ts` brut long ; ces lignes sont
  seulement documentées.

## Sources (documentation officielle Google Cast, consultée le 19 octobre 2026)

- Supported Media for Google Cast — <https://developers.google.com/cast/docs/media>
- Web Receiver, Streaming Protocols — <https://developers.google.com/cast/docs/web_receiver/streaming_protocols>
- Custom Web Receiver (CORS, TLS) — <https://developers.google.com/cast/docs/web_receiver/basic>
- Web Receiver error codes — <https://developers.google.com/cast/docs/web_receiver/error_codes>
- `cast.framework.messages` (`ErrorType`, `ErrorReason`) — <https://developers.google.com/cast/docs/reference/web_receiver/cast.framework.messages>
- Web Sender integration — <https://developers.google.com/cast/docs/web_sender/integrate>
- `chrome.cast.ErrorCode` — <https://developers.google.com/cast/docs/reference/web_sender/chrome.cast>
- Android `MediaError`, `CastStatusCodes`, `RemoteMediaClient.Callback` — <https://developers.google.com/android/reference/com/google/android/gms/cast/MediaError>
