#!/usr/bin/env bash
# Self-test for scope-check.sh. Each case builds a small git repo, makes one synthetic
# change on a feature branch, then checks the exit code and a key line of output.
# Run locally with: bash .github/scripts/scope-check-test.sh
set -euo pipefail

script_dir="$(cd "$(dirname "$0")" && pwd)"
check="$script_dir/scope-check.sh"
work="$(mktemp -d)"
trap 'rm -rf "$work"' EXIT
failures=0

commit() {
  git add -A
  git -c user.name=test -c user.email=test@example.invalid commit -q -m "$1"
}

# new_repo <name> creates a repo whose base branch holds one source file and one
# test file, then switches to a feature branch and enters the repo.
new_repo() {
  mkdir -p "$work/$1"
  cd "$work/$1"
  git init -q
  git symbolic-ref HEAD refs/heads/base
  mkdir -p app/src/main/java/com/callguard app/src/test/java/com/callguard
  printf 'fun allow(n: Int): Boolean = n > 0\n' > app/src/main/java/com/callguard/Policy.kt
  cat > app/src/test/java/com/callguard/PolicyTest.kt <<'EOF'
class PolicyTest {
    @Test fun allowsPositive() {
        assertTrue(allow(1))
        assertEquals(true, allow(2))
    }
}
EOF
  commit base
  git checkout -q -b feature
}

# expect <case> <want-exit> <is-test-only> [text the output must contain]
expect() {
  local name="$1" want="$2" flag="$3" must="${4:-}" out code=0
  out="$(bash "$check" base "$flag" 2>&1)" || code=$?
  if [ "$code" -ne "$want" ]; then
    echo "FAIL $name: exit $code, want $want"
    printf '%s\n' "$out" | sed 's/^/    /'
    failures=$((failures + 1))
    return 0
  fi
  case "$out" in
    *"$must"*) ;;
    *)
      if [ -n "$must" ]; then
        echo "FAIL $name: output lacks '$must'"
        printf '%s\n' "$out" | sed 's/^/    /'
        failures=$((failures + 1))
        return 0
      fi
      ;;
  esac
  echo "PASS $name"
}

# 1. A new test file only. Passes.
new_repo add-test
cat > app/src/test/java/com/callguard/NewTest.kt <<'EOF'
class NewTest {
    @Test fun works() {
        assertTrue(true)
    }
}
EOF
commit "add test"
expect add-test-file 0 true "Scope check passed"

# 2. An existing test gets one more assertion. Passes.
new_repo extend-test
cat > app/src/test/java/com/callguard/PolicyTest.kt <<'EOF'
class PolicyTest {
    @Test fun allowsPositive() {
        assertTrue(allow(1))
        assertEquals(true, allow(2))
        assertFalse(allow(-1))
    }
}
EOF
commit "extend test"
expect extend-test-file 0 true "Scope check passed"

# 3. A source file changes. Fails.
new_repo source-change
printf 'fun allow(n: Int): Boolean = n >= 0\n' > app/src/main/java/com/callguard/Policy.kt
commit "change source"
expect source-change 1 true "app/src/main/java/com/callguard/Policy.kt"

# 4. A new file outside the test folders. Fails.
new_repo new-source-file
printf 'fun extra() = 1\n' > app/src/main/java/com/callguard/Extra.kt
commit "add source"
expect new-source-file 1 true "Extra.kt"

# 5. An existing test file is deleted. Fails.
new_repo delete-test
git rm -q app/src/test/java/com/callguard/PolicyTest.kt
commit "delete test"
expect delete-test 1 true "PolicyTest.kt"

# 6. An existing test file is renamed. Fails, because the old path shows as a delete.
new_repo rename-test
git mv app/src/test/java/com/callguard/PolicyTest.kt app/src/test/java/com/callguard/RenamedTest.kt
commit "rename test"
expect rename-test 1 true "PolicyTest.kt"

# 7. A test gets @Ignore. Fails.
new_repo add-ignore
cat > app/src/test/java/com/callguard/PolicyTest.kt <<'EOF'
class PolicyTest {
    @Ignore
    @Test fun allowsPositive() {
        assertTrue(allow(1))
        assertEquals(true, allow(2))
    }
}
EOF
commit "add ignore"
expect add-ignore 1 true "@Ignore"

# 8. A test gets @Disabled. Fails.
new_repo add-disabled
cat > app/src/test/java/com/callguard/PolicyTest.kt <<'EOF'
class PolicyTest {
    @Disabled
    @Test fun allowsPositive() {
        assertTrue(allow(1))
        assertEquals(true, allow(2))
    }
}
EOF
commit "add disabled"
expect add-disabled 1 true "@Disabled"

# 9. An assertion line is removed. Fails.
new_repo remove-assert
cat > app/src/test/java/com/callguard/PolicyTest.kt <<'EOF'
class PolicyTest {
    @Test fun allowsPositive() {
        assertTrue(allow(1))
    }
}
EOF
commit "remove assert"
expect remove-assert 1 true "removes:"

# 10. An assertion line is edited. Fails too, because the old line is removed.
new_repo edit-assert
cat > app/src/test/java/com/callguard/PolicyTest.kt <<'EOF'
class PolicyTest {
    @Test fun allowsPositive() {
        assertTrue(allow(1))
        assertEquals(false, allow(2))
    }
}
EOF
commit "edit assert"
expect edit-assert 1 true "assertEquals"

# 11. Not a test-only PR, so a source change passes with a skip message.
new_repo not-test-only
printf 'fun allow(n: Int): Boolean = n >= 0\n' > app/src/main/java/com/callguard/Policy.kt
commit "change source"
expect not-test-only 0 false "not a test-only PR"

# 12. An unknown base ref must fail, not pass silently.
new_repo bad-base
code=0
bash "$check" no-such-base true > /dev/null 2>&1 || code=$?
if [ "$code" -ne 0 ]; then
  echo "PASS bad-base-ref (exit $code)"
else
  echo "FAIL bad-base-ref: exit 0, want non-zero"
  failures=$((failures + 1))
fi

if [ "$failures" -ne 0 ]; then
  echo "$failures case(s) failed"
  exit 1
fi
echo "All scope-check cases passed."
