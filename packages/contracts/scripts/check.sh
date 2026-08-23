#!/bin/sh
# CI gate: fail if the committed generated code diverges from the contract.
#
# ADR 0001: "Generation runs in CI. A build fails if generated output differs
# from what is committed." Regeneration happens in a throwaway directory and is
# compared against the committed tree, so the check works on a dirty working
# copy and does not depend on git.
. "$(dirname "$0")/_common.sh"

log "Linting the contract"
"$REDOCLY" lint --config redocly.yaml openapi.yaml

TMP="$(mktemp -d 2>/dev/null || mktemp -d -t lumo-contract)"
trap 'rm -rf "$TMP"' EXIT INT TERM

sh scripts/generate-spring.sh     "$TMP/spring"
sh scripts/generate-kotlin.sh     "$TMP/kotlin"
sh scripts/generate-typescript.sh "$TMP/typescript"

STATUS=0
for target in spring kotlin typescript; do
  if [ ! -d "generated/$target" ]; then
    printf '\033[1;31mMISSING:\033[0m generated/%s does not exist.\n' "$target" >&2
    STATUS=1
    continue
  fi
  if diff -r -q \
       --exclude='.openapi-generator-ignore' \
       "generated/$target" "$TMP/$target" >/dev/null 2>&1; then
    log "generated/$target is up to date."
  else
    printf '\033[1;31mDRIFT:\033[0m generated/%s does not match openapi.yaml.\n' "$target" >&2
    diff -r -u \
      --exclude='.openapi-generator-ignore' \
      "generated/$target" "$TMP/$target" 2>&1 | head -200 >&2
    STATUS=1
  fi
done

if [ "$STATUS" -ne 0 ]; then
  cat >&2 <<'MSG'

The committed clients no longer match packages/contracts/openapi.yaml.

  npm --prefix packages/contracts run generate

then commit the regenerated output together with the contract change.
Never hand-edit anything under packages/contracts/generated/.
MSG
  exit 1
fi

log "Contract and generated clients agree."
