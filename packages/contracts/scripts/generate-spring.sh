#!/bin/sh
# Generate the Spring server interfaces implemented by apps/api.
. "$(dirname "$0")/_common.sh"
require_deps

OUT="${1:-generated/spring}"

log "Spring interfaces -> $OUT"
clean_dir "$OUT"
seed_ignore "$OUT"
"$GEN" generate -c config/spring.yaml -o "$OUT" >/dev/null
log "Spring interfaces generated."
