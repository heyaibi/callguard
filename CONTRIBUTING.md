# Contributing to CallGuard

Build and test CallGuard on your machine, keep the CI gates green, and land every change on `master` through a pull request.

## Before you start

- JDK 21.
- Android SDK Platform 37 (the `compileSdk` in `app/build.gradle.kts`), reachable through `ANDROID_HOME` or an `sdk.dir` line in `local.properties`.
- A device or emulator on Android 10 (API 29) or newer for the instrumented test job.

The full toolchain list is in [Requirements](README.md#requirements).

## Build and test locally

```bash
./gradlew :app:assembleDebug          # debug APK in app/build/outputs/apk/debug/
./gradlew :app:testDebugUnitTest      # JVM unit tests
```

The unit test report is written to `app/build/reports/tests/testDebugUnitTest/index.html`. Most tests cover the pure policies (`ScreeningPolicy`, `MessageFilterPolicy`, `SpamPolicy`, `ResolutionPolicy`), so you can run a single class while you work:

```bash
./gradlew :app:testDebugUnitTest --tests com.callguard.screening.ScreeningPolicyTest
```

Instrumented tests run on a connected device or emulator:

```bash
./gradlew :app:connectedDebugAndroidTest
```

The suite is `app/src/androidTest/java/com/callguard/MainActivityTest.kt`.

## CI gates

CI runs on every pull request and every push to `master`. The jobs are defined in `.github/workflows/ci.yml`. On a pull request, the jobs run in parallel. A pull request is ready for review when all required CI checks pass.

| Gate | Command | Job |
|---|---|---|
| ktlint | `./gradlew ktlintCheck` | Static checks |
| detekt | `./gradlew detekt` | Static checks |
| Android Lint | `./gradlew lint` | Android lint |
| Unit tests | `./gradlew testDebugUnitTest` | Unit tests |
| Debug build | `./gradlew assembleDebug` | Debug build |
| Scope: test-only PRs may only add or extend tests | `.github/scripts/scope-check.sh` | Scope |
| Instrumented tests | `./gradlew connectedDebugAndroidTest` | Instrumented Tests (`instrumented.yml`, not on every PR) |

The Static checks, Unit tests, Android lint, and Debug build jobs upload their reports as artifacts. You can download them from a failed run.

A pull request is test-only when its branch name starts with `test/`, or when its author is listed in the `TEST_ONLY_AUTHORS` repository variable. A test-only PR may change only files under `app/src/test/` and `app/src/androidTest/`. It may not delete or rename a file, add `@Ignore` or `@Disabled`, or remove an assertion. To run the check on your branch, use `bash .github/scripts/scope-check.sh origin/master true`.

The Instrumented Tests job runs from `.github/workflows/instrumented.yml`. It does not run on every pull request. It runs:

- On every push to `master`.
- Every night at 02:00 IST (cron `30 20 * * *`, in UTC).
- When someone starts it by hand from the Actions tab.
- When a maintainer comments `/instrumented` on a pull request.

Only owners, members, and collaborators can start a run with a comment. The run adds an eyes reaction to your comment. It sets an `Instrumented Tests` status on the pull request head. When the run ends, it posts a comment with the run link. The job boots an API 36 Pixel 6 emulator and runs the test task against it.

Keep each CI job under 3.5 minutes. A job that grows past that moves to the `instrumented.yml` triggers: on demand, nightly, and on master.

Run the style gates before you push. ktlint fixes most formatting complaints on its own:

```bash
./gradlew ktlintFormat
```

detekt reads its rules from `config/detekt/detekt.yml` and its baseline from `config/detekt/baseline.xml`; both files are in the tree.

The detekt pin is `2.0.0-alpha.6`, a pre-release, and that is deliberate rather than an oversight. The only stable line, 1.23.x, ships a Kotlin 2.0-era compiler and cannot parse this project's Kotlin 2.4.10 sources; its bundled copy of the IntelliJ `JavaVersion` parser also throws `IllegalArgumentException: 26.0.2.1` on JDK 25 and newer, and upstream has stated that JDK 25 support will not be backported to 1.x. The 2.0.0 alphas are the only releases that match Kotlin 2.4.10, AGP 9.3.1 and a modern JDK. Leave the exception when a Dependabot or reviewer flags it, and move to `2.0.0` once it goes final.

## Commit style

- One change per commit, with a subject in the imperative mood ("Add block list export").
- Keep the subject under 72 characters; put the reason in the body when the diff does not explain it.
- Put the issue reference in the body.

`.gitignore` already excludes `local.properties`, `*.jks`, `*.keystore`, and build output. Signing material stays out of the repository; the release workflow reads it from repository secrets.

## Pull requests

All changes land on `master` through a pull request. Direct pushes to `master` are blocked, so branch from `master` and open a PR for every change, including one-line fixes.

- Keep the PR to one concern, and say what changed for a user of the app.
- Wait for all required CI checks to pass before you request review.
- Docs change with code. A PR that changes a setting, a default, a permission, or a role updates the page that documents it: [settings reference](docs/reference/settings.md), [permissions and roles](docs/reference/permissions-and-roles.md), or the matching page under [docs](docs/index.md).

## Working from an issue

Read [AGENTS.md](AGENTS.md) before you start. It sets the rules for issues, scope, branches, and pull requests, including the rules for test-only work. Test rules are in [docs/contributing/testing.md](docs/contributing/testing.md).

## License

CallGuard is Apache-2.0. By opening a pull request you agree that your contribution is licensed under the same terms. See [LICENSE](LICENSE).
