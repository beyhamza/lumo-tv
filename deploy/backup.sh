#!/bin/sh
# A pg_dump a day into /backups (deploy/backups on the host), the last KEEP_DAYS
# days kept (S16B-01). Custom format, so one file restores with pg_restore —
# see docs/ops/deploy-vps.md, "Restaurer une sauvegarde". Copying ./backups off
# the VPS is the operator's job: a backup on the same disk is not a backup of
# the disk.
set -eu
mkdir -p /backups
while true; do
  file="/backups/lumo-$(date -u +%Y%m%dT%H%M%SZ).dump"
  if pg_dump --format=custom --file="$file.part"; then
    mv "$file.part" "$file"
    echo "backup: wrote $file ($(wc -c < "$file") bytes)"
  else
    rm -f "$file.part"
    echo "backup: pg_dump failed" >&2
  fi
  find /backups -name 'lumo-*.dump' -mtime "+${KEEP_DAYS:-14}" -delete
  sleep 86400
done
