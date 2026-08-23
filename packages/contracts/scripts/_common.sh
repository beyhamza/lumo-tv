# Shared helpers. Sourced, never executed directly.

set -eu

CONTRACTS_DIR="$(cd "$(dirname "$0")/.." && pwd)"
cd "$CONTRACTS_DIR"

GEN="./node_modules/.bin/openapi-generator-cli"
OAS_TS="./node_modules/.bin/openapi-typescript"
REDOCLY="./node_modules/.bin/redocly"

log() { printf '\033[1;36m==>\033[0m %s\n' "$*"; }
die() { printf '\033[1;31mERROR:\033[0m %s\n' "$*" >&2; exit 1; }

require_deps() {
  [ -x "$GEN" ] || die "Dependencies are missing. Run: npm ci --prefix packages/contracts"
  command -v java >/dev/null 2>&1 \
    || die "Java is required by openapi-generator. Install a JDK 17+ and retry."
}

# The generator writes .openapi-generator-ignore into the output directory on
# first run only, then respects it. Seed it before generating so the ignore list
# applies from run one and the committed tree stays sources-only.
seed_ignore() {
  mkdir -p "$1"
  cp config/openapi-generator-ignore "$1/.openapi-generator-ignore"
}

# openapi-generator never deletes files it no longer emits. Removing an
# operation from the contract would otherwise leave an orphan class behind,
# still compiling, still wrong. Always regenerate from an empty directory.
clean_dir() {
  rm -rf "$1"
  mkdir -p "$1"
}
