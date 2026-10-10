#!/usr/bin/env bash
# Applies the files in supabase/migrations that the database has not had yet,
# in order. Each file runs in one transaction together with the row that
# records it, so a file is either applied and recorded, or not applied at all.
# GitHub Actions runs this on every push that adds a migration (see
# .github/workflows/supabase-migrate.yml); it works the same from a laptop.
#
#   DATABASE_URL=postgresql://... supabase/migrate.sh            apply what is new
#   DATABASE_URL=postgresql://... supabase/migrate.sh --dry-run  only list it
#
# What a database has had is kept in nutricart_migrations.applied, a schema
# the API does not serve. Files 0001-0006 were run by hand in the SQL editor
# before this script existed: on a database that already has NutriCart's
# tables, the first run records those as applied after checking that each
# one's objects are really there. A file the check finds missing is applied
# when it is safe to run again (0004-0006) and stops the run otherwise.
#
# Migrations are append-only: a file that changed after it was applied is
# reported, never run again. A change goes in a new file.
#
# The log may be public (the repository's Actions logs are): nothing here
# prints the connection string or a query's rows, and under GitHub Actions a
# database error prints only its SQLSTATE (elsewhere its message too, which
# can quote a value, as a failed cast does), never DETAIL or CONTEXT.
set -euo pipefail
export LC_ALL=C
# Associative arrays need bash 4 or later (macOS's own /bin/bash is 3.2: brew install bash).
if (( BASH_VERSINFO[0] < 4 )); then echo "$0 needs bash 4 or later" >&2; exit 2; fi

: "${DATABASE_URL:?set DATABASE_URL to the connection string of the database}"
DRY_RUN=false
case "$#:${1-}" in
  0:) ;;
  1:--dry-run) DRY_RUN=true ;;
  *) echo "usage: $0 [--dry-run]" >&2; exit 2 ;;
esac

cd "$(dirname "$0")/migrations"

# The files run by hand before this script, and the object each one leaves.
# (0001 as it is now already carries 0004's search_path change, so 0004's
# marker confirms the state 0004 leaves rather than that it ran; 0004 is
# safe to run again either way.)
BASELINE_THROUGH=0006
RERUNNABLE="0004 0005 0006"
declare -A MARKER=(
  [0001]="to_regclass('public.partner_links') is not null"
  [0002]="to_regclass('public.profile_details') is not null"
  [0003]="to_regclass('public.food_log_entries_updated_idx') is not null"
  [0004]="exists (select 1 from pg_proc where proname = 'is_partner_of' and array_to_string(proconfig, ',') like '%extensions%')"
  [0005]="to_regclass('public.custom_products') is not null"
  [0006]="exists (select 1 from pg_proc where proname = 'redeem_pairing_code' and prosrc like '%#variable_conflict use_column%')"
)

# -w: never prompt for a password. Errors without DETAIL or CONTEXT; in a
# (public) Actions log, the SQLSTATE alone.
VERBOSITY=terse
[ -n "${GITHUB_ACTIONS:-}" ] && VERBOSITY=sqlstate
psql_() { psql "$DATABASE_URL" -X -q -w -v ON_ERROR_STOP=1 -v VERBOSITY=$VERBOSITY -v SHOW_CONTEXT=never "$@"; }
query() { psql_ -At -c "$1"; }
annotate() { if [ -n "${GITHUB_ACTIONS:-}" ]; then echo "::$1::$2"; else echo "$1: $2"; fi; }

# Every file must be NNNN_lower_snake.sql, with no version twice.
files=()
declare -A seen=()
for f in [0-9]*.sql; do
  if [[ ! "$f" =~ ^[0-9]{4}_[a-z0-9_]+\.sql$ ]]; then annotate error "$f: migration files are named NNNN_lower_snake.sql"; exit 1; fi
  v=${f:0:4}
  if [ -n "${seen[$v]:-}" ]; then annotate error "two migrations have version $v: ${seen[$v]} and $f"; exit 1; fi
  # The script wraps each file in its own transaction; one inside would end it early.
  # (A bare "end;" is left alone: it closes a block in PL/pgSQL; the check after each file catches the rest,
  # "and chain" included.)
  if grep -Eiq '^[[:space:]]*(begin([[:space:]]+(work|transaction))?|start[[:space:]]+transaction[^;]*|(commit|abort|rollback)([[:space:]]+(work|transaction))?([[:space:]]+and[[:space:]]+(no[[:space:]]+)?chain)?|end[[:space:]]+((work|transaction)([[:space:]]+and[[:space:]]+(no[[:space:]]+)?chain)?|and[[:space:]]+(no[[:space:]]+)?chain))[[:space:]]*;' "$f"; then
    annotate error "$f has its own begin/commit; leave transactions to the script"; exit 1
  fi
  seen[$v]=$f
  files+=("$f")
done

# A connection string that does not parse (a password with an unencoded % / @ ?)
# makes libpq quote it in its error: try it once with every word of that kept out.
if ! psql "$DATABASE_URL" -X -q -w -c 'select 1' >/dev/null 2>&1; then
  annotate error "cannot connect with DATABASE_URL: it should be the Session pooler connection string with the database password in it, percent-encoded if it has characters such as % / @ ? # (see supabase/README.md)"
  exit 1
fi

# A dry run changes nothing, not even this bookkeeping table.
if ! $DRY_RUN; then
  psql_ <<'SQL'
create schema if not exists nutricart_migrations;
revoke all on schema nutricart_migrations from public;
create table if not exists nutricart_migrations.applied (
    version     text primary key,
    name        text not null,
    sha256      text not null,
    applied_by  text not null check (applied_by in ('script', 'hand')),
    applied_at  timestamptz not null default now()
);
revoke all on nutricart_migrations.applied from public;
SQL
fi

declare -A recorded=() recorded_name=()
if [ "$(query "select to_regclass('nutricart_migrations.applied') is not null")" = t ]; then
  rows=$(query "select version, sha256, name from nutricart_migrations.applied")
  while IFS='|' read -r v sha name; do
    [ -n "$v" ] && recorded[$v]=$sha && recorded_name[$v]=$name
  done <<< "$rows"
fi

if [ ${#recorded[@]} -eq 0 ] && [ "$(query "select to_regclass('public.profiles') is not null")" = t ]; then
  echo "First run on a database set up by hand: checking files through $BASELINE_THROUGH."
  baseline=()
  rerun_rest=false
  for f in "${files[@]}"; do
    v=${f:0:4}
    [[ "$v" > "$BASELINE_THROUGH" ]] && break
    # After a missing file, the later ones run again after it, in order (all of
    # them are safe to run again), so an old file never lands on a newer one.
    if $rerun_rest; then
      echo "  $f: to be applied again after the missing one"
    elif [ "$(query "select ${MARKER[$v]}")" = t ]; then
      baseline+=("-c" "insert into nutricart_migrations.applied (version, name, sha256, applied_by) values ('$v', '$f', '$(sha256sum "$f" | cut -c1-64)', 'hand')")
      recorded[$v]=$(sha256sum "$f" | cut -c1-64)
      if $DRY_RUN; then echo "  $f: already there, would be recorded"; else echo "  $f: already there"; fi
    elif [[ " $RERUNNABLE " == *" $v "* ]]; then
      echo "  $f: missing, to be applied"
      rerun_rest=true
    else
      annotate error "$f is missing from this database and is not safe to run on top of the others; run it by hand, then run this again"
      exit 1
    fi
  done
  # All of them or none: a run cut short here starts the check again next time.
  if ! $DRY_RUN && [ ${#baseline[@]} -gt 0 ]; then
    psql_ --single-transaction "${baseline[@]}" >/dev/null
    echo "  recorded $(( ${#baseline[@]} / 2 )) file(s) as run by hand"
  fi
fi

applied=0
pending=0
for f in "${files[@]}"; do
  v=${f:0:4}
  sha=$(sha256sum "$f" | cut -c1-64)
  if [ -n "${recorded[$v]:-}" ]; then
    if [ -n "${recorded_name[$v]:-}" ] && [ "${recorded_name[$v]}" != "$f" ]; then
      annotate error "version $v was applied as ${recorded_name[$v]}; $f needs a version of its own"; exit 1
    fi
    [ "${recorded[$v]}" = "$sha" ] || annotate warning "$f changed after it was applied; it is not run again (put a change in a new file)"
    continue
  fi
  if $DRY_RUN; then echo "would apply $f"; pending=$((pending + 1)); continue; fi
  echo "applying $f"
  # One transaction: a lock wait gives up after 10 s rather than queueing the
  # app's requests behind it (run again later); the transaction's id is noted
  # first and must be the same after the file, or the file ended it (commit,
  # rollback, "and chain" too). Query output goes nowhere: a file's own select
  # or returning would otherwise print rows into the log.
  psql_ --single-transaction -o /dev/null \
    -c "set local lock_timeout = '10s'" \
    -c "select set_config('nutricart.migration_xid', pg_current_xact_id()::text, true)" \
    -f "$f" \
    -c "do \$\$ begin if current_setting('nutricart.migration_xid', true) is distinct from pg_current_xact_id_if_assigned()::text then raise exception 'the file ended the transaction itself'; end if; end \$\$" \
    -c "insert into nutricart_migrations.applied (version, name, sha256, applied_by) values ('$v', '$f', '$sha', 'script')"
  applied=$((applied + 1))
done

if [ $applied -gt 0 ]; then
  # Have the API pick up new tables and functions now rather than later.
  query "notify pgrst, 'reload schema'" >/dev/null
  echo "applied $applied migration(s)"
elif [ $pending -eq 0 ]; then
  echo "nothing new to apply"
fi
