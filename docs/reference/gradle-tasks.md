# Gradle tasks reference

The Gradle tasks CallGuard's CI and contributors run, what each one does, when to run it, and where its output lands.

Run every command from the repository root, which is the Gradle root (`settings.gradle.kts` includes the single module `:app`). CI uses JDK 21 (`temurin`) and installs the Android SDK on the runner, because `local.properties` is gitignored.

## Build facts

| Item | Value | Source |
|---|---|---|
| Modules | `:app` only | `settings.gradle.kts` |
| Package and namespace | `com.callguard` | `app/build.gradle.kts` |
| Version | `versionName` `0.1.0`, `versionCode` `1` | `app/build.gradle.kts` |
| SDK levels | `minSdk` 29, `targetSdk` 37, `compileSdk` 37 | `app/build.gradle.kts` |
| Android Gradle plugin | 9.3.1 | `gradle/libs.versions.toml` |
| Kotlin | 2.4.10 | `gradle/libs.versions.toml` |
| ktlint plugin and engine | 14.2.0 / 1.8.0 | `gradle/libs.versions.toml` |
| detekt | 2.0.0-alpha.6, plugin id `dev.detekt` | `gradle/libs.versions.toml` |
| JDK in CI | 21 | `.github/actions/setup/action.yml` |

## Tasks

| Task | What it does | When to run it | Output |
|---|---|---|---|
| `assembleDebug` | Compiles and packages the debug build, signed with the debug key | Before installing or testing on a device; CI runs it last | `app/build/outputs/apk/debug/` |
| `assembleRelease` | Packages the release build, signed with the release key when the four `CALLGUARD_*` variables are set, and with the debug key when they are not | Releasing; CI runs it on a `v*` tag | `app/build/outputs/apk/release/` |
| `testDebugUnitTest` | Runs every JVM test under `app/src/test`, including the Robolectric tests that need Android resources | Before every commit that changes a policy, a parser, or formatting | `app/build/reports/tests/testDebugUnitTest/` |
| `connectedDebugAndroidTest` | Installs the debug app and runs the instrumented tests on a connected device or emulator | After a change to a screen or a service that unit tests cannot reach | `app/build/reports/androidTests/` |
| `lint` | Runs Android lint on the default variant | Before a PR; CI runs it | `app/build/reports/lint-results-debug.html` and `.xml` |
| `ktlintCheck` | Checks Kotlin formatting and style against the `.editorconfig` rules | Before a PR; CI runs it first | `app/build/reports/ktlint/<sourceSet>Check/<sourceSet>Check.txt` |
| `detekt` | Runs detekt static analysis over `src/main`, `src/test` and `src/androidTest` | Before a PR; CI runs it after ktlint | `app/build/reports/detekt/` as HTML, Markdown, XML, and SARIF |

The unit test source set uses Robolectric 4.17 with `unitTests.isReturnDefaultValues = true` and `unitTests.isIncludeAndroidResources = true`, so a test can call Android framework code and read resources without a device. Calling `testDebugUnitTest --offline` on a machine that has never resolved the Robolectric artifacts fails at `mergeDebugUnitTestManifest` with `No cached version of org.robolectric:robolectric:4.17 available for offline mode`.

There are 20 test classes in `app/src/test/java`, covering the policies, the parsers, formatting, the bridge, and one Compose screen under Robolectric (`HomeScreenTest`, the only caller of `setContent`). `app/src/androidTest/java` holds one instrumented test, `MainActivityTest.kt`, so `connectedDebugAndroidTest` runs that one test and needs a device or emulator to complete.

## Fixer tasks

| Task | What it does | Note |
|---|---|---|
| `ktlintFormat` | Applies the ktlint rules and rewrites the files | Fixes formatting only; a naming violation still fails `ktlintCheck` |
| `detektBaseline` | Writes a detekt baseline at the configured path | The build script points at `config/detekt/baseline.xml`, which exists in the tree |
| `detektGenerateConfig` | Writes a default detekt config from the current version | The build script points at `config/detekt/detekt.yml`, which exists in the tree and is the stock generated default |

The detekt block in `app/build.gradle.kts` sets `buildUponDefaultConfig = true`, so `config/detekt/detekt.yml` adds to the ruleset that ships with detekt rather than replacing it. It also pins the analysis JVM target to 17, older than the JDK that runs Gradle, because detekt otherwise analyses against the running JDK, which is newer than the bytecode the app compiles to. Analysis runs in parallel.

## The CI pipeline

`.github/workflows/ci.yml` runs on a push or pull request to `master`, and on demand. On a pull request, five jobs run in parallel. The `Scope` job runs only on pull requests. It checks test-only PRs and passes quickly for every other PR.

- `Static checks` runs `./gradlew ktlintCheck`, then `./gradlew detekt`. The detekt step runs even when ktlint fails.
- `Unit tests` runs `./gradlew testDebugUnitTest`.
- `Android lint` runs `./gradlew lint`.
- `Debug build` runs `./gradlew assembleDebug`.

Each job uploads its reports with `if: always()`, so they survive a failed step: `detekt-report`, `unit-test-reports`, `lint-results`, and `debug-apk`.

`.github/workflows/instrumented.yml` runs the `Instrumented Tests` job. It runs on a push to `master`, nightly at 02:00 IST, on demand, and on a `/instrumented` comment from a maintainer on a pull request. The job enables KVM when the runner exposes `/dev/kvm`, starts an API 36 `google_apis` x86_64 `pixel_6` emulator with animations disabled, and runs `./gradlew connectedDebugAndroidTest` under a 45 minute job timeout. It uploads `instrumented-test-reports`.

## The release build

`.github/workflows/release.yml` runs on a tag that matches `v*`. It fails before building when the repository secret `CALLGUARD_KEYSTORE_BASE64` is absent, so a tag cannot publish an unsigned APK as a release. It decodes the keystore to `$RUNNER_TEMP/callguard-release.jks`, runs `./gradlew assembleRelease`, creates a GitHub release with the APK and generated release notes, and deletes the decoded keystore in a step that runs even on failure.

The release signing config is built from four environment variables, all read by `app/build.gradle.kts`:

| Variable | Purpose |
|---|---|
| `CALLGUARD_KEYSTORE` | Path to the keystore file. When it is unset, the release build is signed with the debug key and `assembleRelease` still succeeds. |
| `CALLGUARD_KEYSTORE_PASSWORD` | Keystore password |
| `CALLGUARD_KEY_ALIAS` | Key alias |
| `CALLGUARD_KEY_PASSWORD` | Key password |

Release builds are not minified: `isMinifyEnabled` is `false`.

## Failure modes

- `ktlintCheck` fails the build on any violation and writes one report file per source set under `app/build/reports/ktlint/`: `ktlintMainSourceSetCheck`, `ktlintTestSourceSetCheck`, `ktlintAndroidTestSourceSetCheck`, and `ktlintKotlinScriptCheck`. Each report is empty when its source set is clean. Each failing source set is reported as its own task failure, so one run lists all of them.
- `detekt` moved to the `dev.detekt` coordinates because the 1.23.x line bundles a Kotlin compiler that throws `java.lang.IllegalArgumentException` on a JDK 25 or newer `java.runtime.version`. With 2.0.0-alpha.6 the task completes on JDK 26.0.2.1.
- `assembleRelease` on a laptop without the keystore variables succeeds and produces a debug-signed APK. That artifact cannot be published as an update to an existing install.
- `connectedDebugAndroidTest` needs a running emulator or device. A tag build does not run it; only the CI instrumented job and a local device do.

## Related

- [Architecture explained](../explanation/architecture.md) for what the module contains.
- [Permissions and roles reference](permissions-and-roles.md) for the grants a connected test has to arrange before a call or SMS path can run.
