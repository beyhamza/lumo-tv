#!/bin/sh
# =============================================================================
# I-4 (S9-07-03) — count the web's guide reads on the API side.
#
# The web renders its guide on the Next.js server, so `page.on("request")` in
# Playwright never sees the EPG call. This counts it where it lands: the API's
# own request log, one line per authenticated request.
#
# Two things must be true first, and neither is the script's job:
#
#   1. the API runs with the `epg-logging` profile, which turns the
#      DispatcherServlet logger on with its case preserved —
#      apps/api/src/main/resources/application-epg-logging.yml;
#   2. the container is named here (LUMO_API_CONTAINER overrides `lumo-api`).
#
#   SPRING_PROFILES_ACTIVE=dev,epg-logging \
#     docker compose --env-file apps/api/.env up -d --no-deps lumo-api
#   # clear the log, load the Guide in the browser, then:
#   apps/web/e2e/bench/count-epg.sh
#
# The expected number is small and independent of the number of channels drawn:
# one in the normal case, and `1 + 2 + 4` when the window is refused as too
# large and split. A count that grows with the chaînes on screen is the defect
# this proof exists to catch. Repeat at 3, 50 and 100 channels (Game D) and
# copy the three counts into the report.
# =============================================================================
set -eu

CONTAINER="${LUMO_API_CONTAINER:-lumo-api}"

# The path carries the API's `/v1` prefix, which `getRequestURI()` includes.
# `-c` prints 0 and exits 1 when nothing matches, hence the `|| true`: "no
# calls" is a legitimate answer here, not a script error.
docker logs "$CONTAINER" 2>&1 | grep -cE 'GET "/v1/sources/[0-9a-f-]+/epg' || true
