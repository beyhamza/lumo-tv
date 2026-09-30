#!/usr/bin/env bash
# =============================================================================
# recreate-api-epg-logging.sh — relance lumo-e2e-api avec le profil
# `epg-logging` (I-4), en préservant les secrets de la pile déjà debout.
#
# Le compose recrée le conteneur ; s'il régénérait les secrets, les sessions
# web déjà émises seraient invalidées. On relit donc les secrets dans le
# conteneur courant et on les repasse explicitement.
#
# Prérequis : la pile e2e est debout (E2E_KEEP_STACK=1) et lumo-e2e-api healthy.
# =============================================================================
set -euo pipefail

CONTAINER=lumo-e2e-api
docker inspect "$CONTAINER" >/dev/null 2>&1 || {
    echo "ÉCHEC : $CONTAINER absent — lancer d'abord une passe e2e avec E2E_KEEP_STACK=1."
    exit 1
}

getenv() {
    docker inspect "$CONTAINER" --format '{{range .Config.Env}}{{println .}}{{end}}' \
        | sed -n "s/^$1=//p" | head -1
}

LUMO_JWT_SECRET="$(getenv LUMO_JWT_SECRET)"
LUMO_ENCRYPTION_MASTER_KEY="$(getenv LUMO_ENCRYPTION_MASTER_KEY)"
POSTGRES_PASSWORD="$(getenv SPRING_DATASOURCE_PASSWORD)"
LUMO_PLANS_FREE_MAX_SOURCES="$(getenv LUMO_PLANS_FREE_MAX_SOURCES)"
LUMO_WEB_BASE_URL="$(getenv LUMO_WEB_BASE_URL)"
LUMO_CORS_ALLOWED_ORIGINS="$(getenv LUMO_CORS_ALLOWED_ORIGINS)"

echo "recréation de $CONTAINER avec SPRING_PROFILES_ACTIVE=dev,epg-logging (secrets préservés)…"

SPRING_PROFILES_ACTIVE=dev,epg-logging \
POSTGRES_PASSWORD="$POSTGRES_PASSWORD" \
POSTGRES_DB=lumo_e2e \
POSTGRES_USER=lumo \
POSTGRES_PORT=55432 \
LUMO_API_PORT=18080 \
LUMO_JWT_SECRET="$LUMO_JWT_SECRET" \
LUMO_ENCRYPTION_MASTER_KEY="$LUMO_ENCRYPTION_MASTER_KEY" \
LUMO_WEB_BASE_URL="$LUMO_WEB_BASE_URL" \
LUMO_CORS_ALLOWED_ORIGINS="$LUMO_CORS_ALLOWED_ORIGINS" \
LUMO_PLANS_FREE_MAX_SOURCES="$LUMO_PLANS_FREE_MAX_SOURCES" \
LUMO_INGEST_ALLOW_PRIVATE_HOSTS=true \
LUMO_RATE_LIMIT_AUTH_ATTEMPTS_PER_MINUTE=1000 \
LUMO_RATE_LIMIT_DEVICE_APPROVE_ATTEMPTS_PER_MINUTE=1000 \
docker compose -p lumo-e2e -f docker-compose.yml -f docker-compose.e2e.yml \
    up -d --no-deps --force-recreate lumo-api

echo "attente de la santé…"
for i in $(seq 1 60); do
    if curl -sf http://localhost:18080/actuator/health | grep -q '"status":"UP"'; then
        echo "lumo-e2e-api UP avec epg-logging."
        exit 0
    fi
    sleep 2
done

echo "ÉCHEC : lumo-e2e-api n'est pas remonté à temps."
docker logs --tail 40 "$CONTAINER" || true
exit 1
