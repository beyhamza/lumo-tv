#!/usr/bin/env bash
# =============================================================================
# prepare-campagne.sh — provisionne le banc S9-07 pour la campagne intersurfaces.
#
# À lancer depuis un worktree qui contient le harnais S9-07-03 (c.-à-d. dont
# apps/web/e2e/bench/xmltv.awk est un FICHIER). Le doc ../../s9-07-recette.md
# et le plan de démarrage du même dossier expliquent l'ordre des passages.
#
# Ce que fait le script, de façon idempotente :
#   1. vérifie que xmltv.awk est un fichier (sinon : worktree antérieur au
#      harnais, cf. §1.1 du PLAN-DEMARRAGE.md) ;
#   2. arrête le banc de développement `lumo-bench` (port 18081 fixe) ;
#   3. supprime un `lumo-e2e-bench` orphelin (sans réseau) ;
#   4. recrée lumo-e2e-bench via compose, autour de BENCH_EPG_ANCHOR ;
#   5. vérifie les 9 URLs servies et imprime PASS/FAIL.
#
# Usage :
#   bash prepare-campagne.sh
#   BENCH_EPG_ANCHOR=2026-09-27T18:00:00Z bash prepare-campagne.sh
#
# Pour remettre le banc de développement après la campagne : docker start lumo-bench
# =============================================================================
set -euo pipefail

# --- racine du dépôt : on remonte jusqu'à docker-compose.yml -------------------
find_root() {
    local d="$1"
    while [ "$d" != "/" ] && [ ! -f "$d/docker-compose.yml" ]; do
        d="$(dirname "$d")"
    done
    printf '%s' "$d"
}
ROOT="$(find_root "$(cd "$(dirname "$0")" && pwd)")"
cd "$ROOT" || { echo "racine du dépôt introuvable"; exit 1; }

echo "dépôt : $ROOT"

# --- 1. le harnais est-il présent ? ------------------------------------------
AWK_FILE="apps/web/e2e/bench/xmltv.awk"
if [ ! -f "$AWK_FILE" ]; then
    echo "ÉCHEC : $AWK_FILE n'est pas un fichier."
    echo "        Ce worktree précède la fusion de feat/S9-07-03 (PR #7)."
    echo "        Utiliser un worktree sur origin/main, ou fusionner S9-06 d'abord."
    exit 1
fi

# --- ancre EPG ----------------------------------------------------------------
ANCHOR="${BENCH_EPG_ANCHOR:-$(date -u +%Y-%m-%dT%H:%M:%SZ)}"
# Le script exige une ancre explicite ou prend l'instant courant ; dans tous les
# cas il la fige pour que le guide et LUMO_NOW puissent partager le même instant.
export BENCH_EPG_ANCHOR="$ANCHOR"
echo "ancre EPG : $ANCHOR"

# --- secrets requis par l'interpolation de compose (le banc n'en a pas besoin) -
if [ -z "${LUMO_JWT_SECRET:-}" ]; then
    export LUMO_JWT_SECRET="$(head -c 48 /dev/urandom | base64 | tr -d '\n')"
    echo "note : LUMO_JWT_SECRET jetable généré (le banc ne l'utilise pas)."
fi
if [ -z "${LUMO_ENCRYPTION_MASTER_KEY:-}" ]; then
    export LUMO_ENCRYPTION_MASTER_KEY="$(head -c 32 /dev/urandom | base64 | tr -d '\n')"
    echo "note : LUMO_ENCRYPTION_MASTER_KEY jetable généré (le banc ne l'utilise pas)."
fi

COMPOSE=(docker compose -p lumo-e2e -f docker-compose.yml -f docker-compose.e2e.yml)

# --- 2. libérer le port 18081 : banc de dev -----------------------------------
if docker ps --format '{{.Names}}' | grep -qx lumo-bench; then
    echo "arrêt du banc de développement lumo-bench (port 18081)…"
    docker stop lumo-bench >/dev/null
    echo "  → pour le remettre après la campagne : docker start lumo-bench"
fi

# --- 3. enlever un orphelin éventuel ------------------------------------------
if docker ps -a --format '{{.Names}}' | grep -qx lumo-e2e-bench; then
    echo "suppression de lumo-e2e-bench (orphelin éventuel)…"
    docker rm -f lumo-e2e-bench >/dev/null
fi

# --- 4. recréer le banc -------------------------------------------------------
echo "création de lumo-e2e-bench…"
"${COMPOSE[@]}" --profile bench up -d --wait --no-deps bench

# --- 5. vérifier les URLs -----------------------------------------------------
echo "vérification des URLs servies :"
fail=0
check() {
    local path="$1"
    local code size
    code="$(curl -s -o /dev/null -w '%{http_code}' "http://localhost:18081/$path" || true)"
    size="$(curl -s "http://localhost:18081/$path" | wc -c)"
    if [ "$code" = "200" ]; then
        printf '  PASS  %-22s %s (%s octets)\n' "$path" "$code" "$size"
    else
        printf '  FAIL  %-22s %s\n' "$path" "$code"
        fail=1
    fi
}
check guide.xml
check guide-transition.xml
check guide-partial.xml
check guide-empty.xml
check guide-broken.xml
check guide-stale.xml
check guide-big.xml
check guide-huge.xml
check playlist-100.m3u
if [ "$(curl -s http://localhost:18081/playlist-100.m3u | grep -c '^#EXTINF')" != "100" ]; then
    echo "  FAIL  playlist-100.m3u n'a pas 100 chaînes"; fail=1
fi

echo
if [ "$fail" -eq 0 ]; then
    echo "banc prêt. Rappel du garde-fou : la recette ne démarre qu'au signal « S9-06 vert »."
else
    echo "banc incomplet — voir les FAIL ci-dessus."
    exit 1
fi
