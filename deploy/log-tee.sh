#!/bin/sh
# Runs a service with its output copied to /logs/<UTC date>.log (LUMO_DATA_DIR/
# logs/<service> on the host) and still sent to `docker compose logs`. Files older
# than LOG_KEEP_DAYS are deleted when the day changes. See docs/ops/deploy-vps.md,
# "Journaux". A named pipe rather than `cmd | tee`, so that the service stays PID 1
# and is the one that receives Docker's stop signal.
set -eu
umask 027
fifo=/tmp/log-tee.fifo
rm -f "$fifo"
mkfifo "$fifo"
# mawk (the API image's awk) otherwise reads its input in blocks: lines arrive late.
case "$(awk -W version 2>&1)" in
  *mawk*) interactive="-W interactive" ;;
  *) interactive="" ;;
esac
{
  # shellcheck disable=SC2086
  awk $interactive -v keep="${LOG_KEEP_DAYS:-14}" '
    {
      day = strftime("%Y-%m-%d")
      if (day != current) {
        if (current != "") close(file)
        current = day
        file = "/logs/" day ".log"
        system("find /logs -name \"*.log\" -mtime +" keep " -delete")
      }
      print
      fflush()
      print >> file
      fflush(file)
    }' || true
  # awk stops when it cannot write (disk full). Keep draining the pipe: a lost log
  # file must not take the service down with a broken pipe.
  cat
} < "$fifo" &
exec "$@" > "$fifo" 2>&1
