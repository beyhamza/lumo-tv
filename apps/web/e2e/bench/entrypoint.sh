#!/bin/sh
# =============================================================================
# The source test bench — start-up (S2-03, extended by S3-01).
#
# Two things the fixtures cannot be on their own, done here before nginx is
# allowed to answer anything:
#
#   1. The playlist has to name a host the PLAYER can reach, and that host is
#      not the same for a browser on this machine and for a phone on the local
#      network. So the committed playlist is a template and BENCH_PUBLIC_URL is
#      substituted into it here.
#
#   2. The oversized payload has to be oversized — larger than the API's
#      ingestion cap, which is 200 MB by default. Committing a 200 MB file would
#      be absurd; generating one is a second's work, and gzip makes it a few
#      hundred kilobytes on the wire while still expanding past the cap. That is
#      also the honest reproduction: the API caps the bytes it READS, precisely
#      because a compressed body expands after its Content-Length was written.
#
# nginx is exec'd at the end, so it is PID 1 and stops when the container does.
# Everything above runs first, which is what the container's healthcheck relies
# on: healthy means generated.
# =============================================================================
set -eu

SRC=/bench/fixtures
WEB=/usr/share/nginx/html

# Where the player should come back to, from wherever the player is running.
#
# The default is the browser's view on the developer's own machine, which is
# what the end-to-end suite needs and what it must keep getting without setting
# anything. A qualification run on a real phone sets this to the machine's
# address on the local network — see docs/backlog/sprint-02-recette.md §1.
: "${BENCH_PUBLIC_URL:=http://localhost:18081}"

# The API's ingestion cap is 200 MB (LUMO_INGEST_MAX_PAYLOAD_MB). Twenty over is
# enough to cross it without making the generation itself slow.
: "${BENCH_OVERSIZED_MB:=220}"

echo "bench: public URL is ${BENCH_PUBLIC_URL}"

mkdir -p "$WEB"
cp -R "$SRC"/. "$WEB"/

# The templates are the only files that are rewritten. Everything else is copied
# as committed, so what a qualification run reads is what review saw.
sed -i "s|__BENCH_PUBLIC_URL__|${BENCH_PUBLIC_URL}|g" "$WEB/playlist.m3u"
sed -i "s|__BENCH_PUBLIC_URL__|${BENCH_PUBLIC_URL}|g" "$WEB/mixed.m3u"

# ---- The two films of /mixed.m3u --------------------------------------------
#
# Concatenated from the stream segments, which is enough for what this path is
# for: ADR 0009 classifies on the URL, so what matters is that `.mp4` and `.mkv`
# answer 200 and that the ingestion files them as films.
#
# **These are MPEG-TS bytes under a film's name, and that is stated rather than
# hidden.** A player that sniffs its input will decode them; a browser's `<video>`
# will not. Real film playback against this bench needs a real container and is
# not covered here — the recette says so instead of a fixture pretending
# otherwise.
mkdir -p "$WEB/film"
cat "$WEB"/stream/seg*.ts > "$WEB/film/le-voyage.mp4"
cp "$WEB/film/le-voyage.mp4" "$WEB/film/la-traversee.mkv"

echo "bench: two film fixtures generated ($(wc -c < "$WEB/film/le-voyage.mp4") bytes each, MPEG-TS)"

# ---- The Xtream panel --------------------------------------------------------
#
# Nothing to generate: the JSON under fixtures/xtream is copied as committed,
# like every other fixture, and nginx routes an action to a file. This block
# exists to say so at start-up, because a panel that answers is new and a
# qualification run should see it in the log rather than discover it.
#
# **Only `user_info` is read by the API.** `server_info` is in the account
# fixture because a real panel sends one; nothing consumes it, and its
# host and port are decorative.
echo "bench: xtream panel at /player_api.php ($(ls "$WEB/xtream" | wc -l) fixtures)"

# ---- The payload past the cap -----------------------------------------------
#
# `#EXTM3U` first, so the parser accepts it as a playlist and keeps reading —
# without it the response is refused as SOURCE_INVALID_FORMAT on its first line
# and the cap is never reached, which would test the wrong thing entirely.
#
# Then comment lines, and comments on purpose: the M3U parser skips them without
# allocating, so the API reads two hundred megabytes and holds none of it. Fill
# it with channel entries instead and the cap would fire on an API that had
# already built five million objects in memory, which is a different failure and
# a much less pleasant one.
{
    echo '#EXTM3U'
    yes '# padding. Nothing here is a channel; see entrypoint.sh.'
} | head -c "$((BENCH_OVERSIZED_MB * 1024 * 1024))" | gzip -6 > "$WEB/oversized.m3u.gz"

echo "bench: oversized payload is ${BENCH_OVERSIZED_MB} MB, served gzipped as $(wc -c < "$WEB/oversized.m3u.gz") bytes"

exec nginx -g 'daemon off;'
