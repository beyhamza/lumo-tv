#!/bin/sh
# Generate the Kotlin/Retrofit2 client used by both Android applications.
. "$(dirname "$0")/_common.sh"
require_deps

OUT="${1:-generated/kotlin}"

log "Kotlin retrofit2 client -> $OUT"
clean_dir "$OUT"
seed_ignore "$OUT"
"$GEN" generate -c config/kotlin.yaml -o "$OUT" >/dev/null
log "Kotlin client generated."
