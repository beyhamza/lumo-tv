#!/bin/sh
# Generate the TypeScript types consumed by apps/web through openapi-fetch.
#
# openapi-typescript emits types only — no runtime. The web client is
# openapi-fetch, which reads these types directly, so there is no generated
# JavaScript to keep in sync.
. "$(dirname "$0")/_common.sh"
[ -x "$OAS_TS" ] || die "Dependencies are missing. Run: npm ci --prefix packages/contracts"

OUT="${1:-generated/typescript}"

log "TypeScript types -> $OUT/api.d.ts"
clean_dir "$OUT"
"$OAS_TS" openapi.yaml --output "$OUT/api.d.ts" >/dev/null
log "TypeScript types generated."
