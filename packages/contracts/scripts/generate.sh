#!/bin/sh
# Lint the contract, then regenerate all three clients.
#
# This is the command to run after every edit to openapi.yaml. Commit the
# contract and the regenerated output together: CI rejects a commit where they
# disagree (scripts/check.sh).
. "$(dirname "$0")/_common.sh"

log "Linting the contract"
"$REDOCLY" lint --config redocly.yaml openapi.yaml

sh scripts/generate-spring.sh
sh scripts/generate-kotlin.sh
sh scripts/generate-typescript.sh

log "All three clients regenerated. Review the diff, then commit it with the contract."
