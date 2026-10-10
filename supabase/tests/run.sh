#!/usr/bin/env bash
# The database tests: applies the Supabase stand-in (supabase_stub.sql), then
# every file in supabase/migrations in order, then the tests (*.test.sql),
# to the database the libpq environment names (PGHOST, PGPORT, PGUSER,
# PGPASSWORD, PGDATABASE; a superuser, and an empty database). Prints one
# line per migration applied and per test passed, and exits non-zero on the
# first failure. CI runs it on the official postgres:16 image. A test may
# start psql sessions of its own in the background (pairing.test.sql's
# test 17), which connect with the same variables: psql must be on the PATH.
#
#   supabase/tests/run.sh                  every migration, then the tests
#   supabase/tests/run.sh --through 0005   only the migrations up to 0005
#                                          (to see a test catch an old bug)
set -euo pipefail
export LC_ALL=C

here="$(cd "$(dirname "$0")" && pwd)"
through=9999
if [ "${1:-}" = "--through" ]; then through=${2:?--through needs a version, like 0005}; fi

# Warnings such as "wal_level is insufficient to publish logical changes"
# (the realtime publication on a plain Postgres) are not failures.
export PGOPTIONS="${PGOPTIONS:-} -c client_min_messages=error"
psql_() { psql -X -q -v ON_ERROR_STOP=1 "$@"; }

psql_ -f "$here/supabase_stub.sql"

for f in "$here"/../migrations/[0-9]*.sql; do
  name=$(basename "$f")
  [ "$((10#${name:0:4}))" -le "$((10#$through))" ] || continue
  if ! psql_ -f "$f"; then
    echo "not ok - $name did not apply" >&2
    exit 1
  fi
  echo "applied $name"
done

for t in "$here"/*.test.sql; do
  # -At: only the tests' "ok N - ..." lines; a failing test stops the file with its error.
  if ! psql_ -At -f "$t"; then
    echo "not ok - $(basename "$t") failed (the error is above)" >&2
    exit 1
  fi
done
