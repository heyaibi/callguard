# Working from an issue

This file sets the rules for anyone who works from a GitHub issue in this repository. The same rules apply to human and automated contributors.

Read this file before you create a branch. Test rules are in [docs/contributing/testing.md](docs/contributing/testing.md).

## Test-only contributors

Some contributors add or change tests only. This section sets their rules.

You are a test-only contributor when one of these is true:

- Your branch is named `test/<short-name>`.
- The maintainer marks your account as test-only.

A test-only contributor may change only these paths:

- `app/src/test/**`
- `app/src/androidTest/**`

A test-only contributor must not:

- Change any file outside those two paths.
- Delete or rename any file, including a test file.
- Add `@Ignore`, or make any other change that stops a test from running.
- Remove or weaken an assertion.
- Change an expected value so that a failing test passes.

If a new test finds a bug in source code, do this:

1. Do not fix the source code.
2. Keep the failing test out of your PR. Do not mark it ignored.
3. Open an issue. Paste the failing test code and its output into the issue.

The CI job `Scope` rejects a PR that breaks these rules.

## Start from an issue

- Do read the issue's Goal, Allowed paths, Acceptance criteria, and Out of scope sections before you start.
- Do the work the issue asks for, and nothing more.
- Do ask on the issue when the task is unclear, or when it needs a path you may not change.
- Do not start work without an issue.
- Do not fix problems you find outside the issue. List them in the PR body.

The task issue form sets Allowed paths to the two test paths by default. Source code changes are for the maintainer. Other contributors change source code only when the maintainer names the source path in the issue.

## Workflow

1. Take one issue. Make one branch and one pull request for it.
2. Branch from `master`. Name the branch `<type>/<short-name>`. The type is one of `feat`, `fix`, `test`, `docs`, `ci`, or `refactor`. Example: `test/screening-silence-mode`.
3. Do not push to `master`. Direct pushes are blocked.
4. Keep the PR to one concern.
5. Put `Closes #<issue number>` in the PR body.
6. Wait for both CI jobs to pass before you ask for review.

## Off-limits paths

Do not change these paths. The only exception is an issue that names the exact path.

- `.github/`, including workflows, CODEOWNERS, and templates.
- `gradle/`, every `*.gradle.kts` file, and `gradle.properties`.
- `config/detekt/`. Never regenerate `config/detekt/baseline.xml` to hide new findings.
- `app/src/main/AndroidManifest.xml`. Do not add, remove, or change a permission or a role entry.
- Signing and release files. This covers the `signingConfigs` block in `app/build.gradle.kts`, `.github/workflows/release.yml`, and any `*.jks` or `*.keystore` file.
- `store/`.
- `docs/blueprint/`.
- `CHANGELOG.md`. Add lines only under `## [Unreleased]`. Never edit a released section.
- `LICENSE`.

Do not add a dependency. Do not change a version in `gradle/libs.versions.toml`. Do either one only when the issue says so.

## Privacy

- Never commit a real phone number, SMS text, contact, or call log entry. Never commit data made from them.
- Use synthetic data. Phone numbers follow the `+91 98765 0xxxx` pattern, for example `+919876501234`.
- Use made-up names.
- Never commit a secret. This includes keys, tokens, passwords, keystores, and `local.properties`.

## Verification before you open a PR

Run each command from the repository root. Each one must pass.

```bash
./gradlew ktlintFormat
./gradlew ktlintCheck
./gradlew detekt
./gradlew lint
./gradlew testDebugUnitTest
./gradlew assembleDebug
```

Run one test class while you work:

```bash
./gradlew :app:testDebugUnitTest --tests com.callguard.screening.ScreeningPolicyTest
```

Count the unit tests after `testDebugUnitTest`:

```bash
python3 -I - <<'EOF'
import glob
import xml.etree.ElementTree as ET

totals = {"tests": 0, "failures": 0, "errors": 0, "skipped": 0}
for path in glob.glob("app/build/test-results/testDebugUnitTest/*.xml"):
    suite = ET.parse(path).getroot()
    for key in totals:
        totals[key] += int(suite.get(key, "0"))
print(totals)
EOF
```

Emulator tests run with `./gradlew :app:connectedDebugAndroidTest`. That command needs a device or an emulator. CI runs these tests in the `Instrumented Tests` job on an API 36 emulator. If you do not run them, say so in the PR body.

Never say a check passed unless you ran it and saw it pass.

## PR body

The PR template in `.github/pull_request_template.md` has the headings. The body has four parts, in this order:

1. What changed. Describe the change for a user of the app. If no user sees it, describe it for a maintainer.
2. How it was verified. List each command you ran, its result, and the test counts (tests, failures, errors, skipped).
3. What was not verified. Name each check you did not run, and say why.
4. Out-of-scope findings. List each problem you saw and did not fix. Write "None" if there are none.

## Commits

- Write the subject in the imperative mood. Example: `Add scope rules for test-only contributors`.
- Keep the subject under 72 characters.
- Put the issue reference in the body.
- Put the reason in the body when the diff does not explain it.
- Sign every commit with `git commit -s`. Each commit needs a `Signed-off-by:` line.

## Definition of done

Check each box before you ask for review.

- [ ] Every acceptance criterion in the issue is met.
- [ ] Only allowed paths changed.
- [ ] No off-limits path changed.
- [ ] Each behaviour change has a test. Each bug fix has a test that failed before the fix.
- [ ] The verification commands passed on your machine. Or the PR body names each one that did not run.
- [ ] Docs changed with the code. See [CONTRIBUTING.md](CONTRIBUTING.md).
- [ ] No real personal data appears in any file, commit, or PR text.
- [ ] Every commit has a `Signed-off-by:` line.
- [ ] The PR body has the four parts and `Closes #<issue number>`.
