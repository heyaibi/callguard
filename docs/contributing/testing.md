# Testing standard

This page sets the rules for tests in CallGuard. Read it before you add or change a test.

It covers the four test types, where each one lives, how to name and run it, and the templates to copy. Rules for scope and pull requests are in [AGENTS.md](../../AGENTS.md).

## Test types

Pick the lowest type that can check the behaviour. A lower type runs faster and needs no device.

### (a) Pure JVM unit tests

- Use for: policies, parsers, models, and pure functions. Use it for any code that does not need Android.
- Where: `app/src/test/java/`, in the same package as the subject.
- Naming: `<Subject>Test`. Example: `ScreeningPolicyTest`.
- Framework: JUnit 4, with `org.junit.Test` and `org.junit.Assert`.
- Run: `./gradlew :app:testDebugUnitTest --tests com.callguard.screening.ScreeningPolicyTest`
- Time budget (target): under 1 second per test.

### (b) Robolectric and Compose UI tests

- Use for: what a screen shows, and what a control does. No device is needed.
- Where: `app/src/test/java/`, in the same package as the screen.
- Naming: `<Screen>Test`. Example: `HomeScreenTest`.
- Annotations: `@RunWith(RobolectricTestRunner::class)` and `@Config(sdk = [35])`.
- Run: `./gradlew :app:testDebugUnitTest --tests com.callguard.ui.screens.HomeScreenTest`
- Time budget (target): under 5 seconds per test.

The app targets SDK 37, but the test pins SDK 35. The note at the top of `HomeScreenTest.kt` explains why. Keep the pin unless that note changes.

### (c) Instrumented tests

- Use for: system roles, intents, and real providers. Use it for anything only a real Android system can check.
- Where: `app/src/androidTest/java/`, in the same package as the subject.
- Naming: `<Subject>Test`. Example: `MainActivityTest`.
- Framework: `AndroidJUnit4`.
- Run: `./gradlew :app:connectedDebugAndroidTest`, on a device or an emulator running Android 10 or newer. CI runs it on an API 36 emulator in the `Instrumented Tests` job.
- Time budget (target): keep the whole suite well under the job timeout of 45 minutes, which is set in `.github/workflows/ci.yml`.

### (d) Local evaluation tests

- Use for: checks on private data, such as a real export of calls or texts. The data stays on your machine.
- Where: `app/src/test/java/`, in the same package as the subject.
- Naming: `<Subject>EvalTest`.
- Data: read the file path from the `CALLGUARD_EVAL_DATA` environment variable only. Never hard-code a path.
- Skip: when the variable is not set, call `Assume.assumeTrue`. The test then skips. It does not fail.
- Run: `CALLGUARD_EVAL_DATA=/path/to/export.csv ./gradlew :app:testDebugUnitTest --tests '*EvalTest'`
- Time budget (target): under 5 minutes per run.
- Status: no test of this type exists in the repository yet. CI never sets `CALLGUARD_EVAL_DATA`, so these tests skip in CI.

## Rules for every test

1. Put each test in the same package as its subject. Name the class `<Subject>Test`. Use `<Subject>EvalTest` only for type (d).
2. Name each method in camelCase. Say what the code does. Example: `blockListIsCheckedBeforeAllowList`. Existing tests do not use spaces, underscores, or backticks.
3. Test one behaviour in each test. Several asserts are fine when they check one rule.
4. Arrange, act, and assert, in that order. Put one blank line between the parts. Existing tests do not use comments for these parts.
5. Write `assertEquals(expected, actual)`. Put the expected value first.
6. Do not sleep. Use a fixed input, a fake, or a fixed clock. In Compose tests, use `compose.waitUntil` or `compose.waitForIdle()`, or move time with `compose.mainClock.advanceTimeBy`.
7. Do not use the network. A test must not open a socket or call a web service.
8. Set the time zone and the locale in any test that depends on them. Set the default time zone to `Asia/Kolkata` and the default locale to `en-IN`. Restore both after the test.
9. Use fixed data. Do not use random values, or the current date or time, in an expected value.
10. Use fakes, not mocks. The project has no mocking library. Check `gradle/libs.versions.toml`. Write a small fake class in the test file. Do not add a mocking library unless the issue says so.
11. Add a regression test for each bug fix. The test must fail on the code before the fix, and pass after it. Run it on the unfixed code first.
12. Cover the edge cases that apply. Use empty input, null or blank input, boundary values, and Indian number formats. Examples: `9876501234`, `09876501234`, `+91 98765 01234`, `00919876501234`, and `18001234567`.
13. Use synthetic data only. Phone numbers follow the `+91 98765 0xxxx` pattern. Names are made up.
14. Keep test code lint clean. ktlint and detekt check test code too. detekt skips `MagicNumber` and some other rules in test folders. Other rules still apply, such as the 120-character line limit.

## Pure JVM tests and Android

`app/build.gradle.kts` sets `unitTests.isReturnDefaultValues = true`. In a pure JVM test, an Android call returns a default value, such as `null`, `0`, or `false`. A test that depends on that value checks nothing real. If the code calls Android APIs, write a type (b) or type (c) test instead.

## What not to test

- Generated code, such as `R` and `BuildConfig`.
- Trivial getters and setters. Also skip the generated `copy` and `equals` methods of data classes.
- Third-party libraries, such as Compose and AndroidX. Test what the app does with them.
- Private helpers. Test them through the public or internal function that uses them.

## Writing tests against existing code

Most tests check code that already exists. Follow these rules.

- Test the code as it is. Call public and internal functions in their current form.
- Never change production code to make it easier to test. This includes making a private function internal, adding a parameter only for a test, and adding an interface only for a fake.
- If code cannot be tested without a change, open an issue. Describe the code, the test you want, and what blocks it.
- If a test shows a bug, keep the failing test out of a test-only PR. Open an issue with the test code and its output. See [AGENTS.md](../../AGENTS.md#test-only-contributors).

## Templates

Copy the template for your test type. Replace each placeholder in angle brackets. Keep the imports and the shape. Each template uses the same imports as an existing test of that type.

### Template (a): pure JVM unit test

```kotlin
package com.callguard.<package>

import org.junit.Assert.assertEquals
import org.junit.Test

class <Subject>Test {
    @Test
    fun <behaviourInCamelCase>() {
        val input = <input>

        val actual = <Subject>.<function>(input)

        assertEquals(<expected>, actual)
    }
}
```

### Template (b): Robolectric and Compose UI test

```kotlin
package com.callguard.ui.screens

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.callguard.ui.theme.CallGuardTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class <Screen>Test {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun <screenShowsItsHeading>() {
        compose.setContent {
            CallGuardTheme {
                <Screen>()
            }
        }

        compose.onNodeWithText("<VISIBLE TEXT>").assertIsDisplayed()
    }
}
```

### Template (c): instrumented test

```kotlin
package com.callguard

import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class <Activity>Test {
    @Test
    fun <behaviourInCamelCase>() {
        ActivityScenario.launch(<Activity>::class.java).use { scenario ->
            assertEquals(Lifecycle.State.RESUMED, scenario.state)
        }
    }
}
```

### Template (d): local evaluation test

```kotlin
package com.callguard.<package>

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

class <Subject>EvalTest {
    @Test
    fun <checkInCamelCase>() {
        val path = System.getenv("CALLGUARD_EVAL_DATA")
        assumeTrue("Set CALLGUARD_EVAL_DATA to run this check", !path.isNullOrBlank())

        val rows = File(path!!).readLines()

        assertTrue("<what must hold>", <check on rows>)
    }
}
```

Type (d) tests must not print rows or write them to a file. Assert counts or booleans only.

## Related pages

- [Contributor docs](index.md): reading paths for contributors.
- [AGENTS.md](../../AGENTS.md): scope, test-only rules, verification commands, and the PR body.
- [CONTRIBUTING.md](../../CONTRIBUTING.md): build setup and CI gates.
- [Documentation home](../index.md): all project docs.
