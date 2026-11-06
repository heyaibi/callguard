#!/usr/bin/env bash
# Enforces the test-only PR rule: a test-only PR may add or extend tests and nothing else.
#
# Usage: scope-check.sh <base-ref> <is-test-only>
#   <base-ref>      revision to diff against, for example origin/master
#   <is-test-only>  "true" for a test-only PR. Any other value passes at once.
#
# A test-only PR fails when it:
#   1. changes a file outside app/src/test/ and app/src/androidTest/,
#   2. deletes or renames an existing file, or
#   3. adds an @Ignore or @Disabled line, or removes a line that contains "assert".
set -euo pipefail

if [ "$#" -ne 2 ]; then
  echo "usage: scope-check.sh <base-ref> <is-test-only>" >&2
  exit 2
fi

base="$1"
is_test_only="$2"

if [ "$is_test_only" != "true" ]; then
  echo "not a test-only PR; scope check skipped"
  exit 0
fi

range="$base...HEAD"
work="$(mktemp -d)"
trap 'rm -rf "$work"' EXIT

# Write git output to files, not to a process substitution: a failing git command must stop the script.
git diff -z --name-status --no-renames "$range" > "$work/names"
git diff --no-renames -U0 "$range" > "$work/patch"

outside=""
deleted=""
while IFS= read -r -d '' status && IFS= read -r -d '' path; do
  case "$path" in
    app/src/test/*|app/src/androidTest/*) ;;
    *) outside="$outside  $path"$'\n' ;;
  esac
  case "$status" in
    D*|R*) deleted="$deleted  $status  $path"$'\n' ;;
  esac
done < "$work/names"

# "--- " and "+++ " are headers only outside a hunk. Inside a hunk, a removed line
# that starts with "-- " is content, so it still counts.
weakened="$(awk '
  /^diff --git / { in_hunk = 0; next }
  /^@@/ { in_hunk = 1; next }
  !in_hunk && /^--- / { old = substr($0, 7); next }
  !in_hunk && /^\+\+\+ / { file = ($0 ~ /^\+\+\+ \/dev\/null/) ? old : substr($0, 7); next }
  in_hunk && /^\+/ && /@Ignore|@Disabled/ { print "  adds:    " file ": " substr($0, 2) }
  in_hunk && /^-/ && /assert/ { print "  removes: " file ": " substr($0, 2) }
' "$work/patch")"

failed=0
if [ -n "$outside" ]; then
  failed=1
  echo "FAIL: a test-only PR may change only app/src/test/ and app/src/androidTest/. Files outside those paths:"
  printf '%s' "$outside"
fi
if [ -n "$deleted" ]; then
  failed=1
  echo "FAIL: a test-only PR may not delete or rename an existing file. Affected files:"
  printf '%s' "$deleted"
fi
if [ -n "$weakened" ]; then
  failed=1
  echo "FAIL: a test-only PR may not add @Ignore or @Disabled, or remove an assertion. Offending lines:"
  printf '%s\n' "$weakened"
fi
if [ "$failed" -ne 0 ]; then
  echo "Scope check failed for $range. Test-only PRs may only add or extend tests."
  exit 1
fi
echo "Scope check passed for $range."
