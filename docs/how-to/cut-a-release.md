# Cut a release

Pushing a tag that starts with `v` builds a signed APK and publishes it as a GitHub Release. You need write access to the repository, a release keystore, and the four signing secrets already stored in the repository settings.

The release job builds and publishes; it does not run the tests. Merge to `master` and let CI pass first, since CI is what runs `ktlintCheck`, `detekt`, `lint`, `testDebugUnitTest`, and `assembleDebug`. The instrumented tests run on the push to `master`, from `instrumented.yml`.

## 1. Bump the version

Edit `app/build.gradle.kts` and raise `versionCode`, then set `versionName` to the version you are tagging. Both live in `defaultConfig`.

## 2. Create the release keystore, once

```bash
keytool -genkeypair -v \
  -keystore callguard-release.jks \
  -alias callguard \
  -keyalg RSA -keysize 2048 -validity 10000
```

Keep the alias you type here; it becomes `CALLGUARD_KEY_ALIAS`. `keytool` prompts for the store password and the key password, and then for your name and organization.

## 3. Turn the keystore into a base64 blob

```bash
base64 -i callguard-release.jks > keystore.b64
```

On macOS this writes a single unwrapped line, which is what the workflow expects to decode with `base64 -d`. GNU base64 wraps at 76 columns by default, so use `base64 -w0 callguard-release.jks > keystore.b64` on Linux.

`.gitignore` covers `*.jks` and `*.keystore`, and it does not cover `keystore.b64`. Delete the blob after step 4.

## 4. Store the four secrets

Add these under **Settings**, **Secrets and variables**, **Actions**, in the repository:

| Secret | Value |
|---|---|
| `CALLGUARD_KEYSTORE_BASE64` | The contents of `keystore.b64`. |
| `CALLGUARD_KEYSTORE_PASSWORD` | The store password you gave `keytool`. |
| `CALLGUARD_KEY_ALIAS` | The alias, `callguard` in the example above. |
| `CALLGUARD_KEY_PASSWORD` | The key password. |

With the GitHub CLI, the blob never touches your clipboard:

```bash
gh secret set CALLGUARD_KEYSTORE_BASE64 < keystore.b64
rm keystore.b64
```

## 5. Tag and push

```bash
git tag -a v0.1.0 -m "CallGuard 0.1.0"
git push origin v0.1.0
```

`git push` does not push tags, so name the tag explicitly.

## What the workflow does

`.github/workflows/release.yml` runs on any pushed tag matching `v*`, with write access to the repository contents.

It stops before building if `CALLGUARD_KEYSTORE_BASE64` is empty, so a repository without signing secrets cannot publish an unsigned APK as a release. It then decodes the blob into a temporary keystore, runs `./gradlew assembleRelease` with the four values as environment variables, and uploads `app/build/outputs/apk/release/*.apk` to a published GitHub Release with generated release notes. The temporary keystore is deleted at the end whether the build passed or not.

`app/build.gradle.kts` signs with the debug key when `CALLGUARD_KEYSTORE` is unset, which is what makes a laptop `assembleRelease` work. The workflow's early check is what keeps that debug-signed APK out of a release.

## If you lose the keystore

Every update to an installed app has to carry the same signing key, so losing the keystore ends your ability to ship updates to the existing install base. Play App Signing is the exception: when it is enabled, Google holds the app signing key and you can upload a new upload key instead. Back up the `.jks` file and both passwords somewhere you can reach without the laptop.

## Troubleshooting

- **The job fails at "Check signing secret"**: `CALLGUARD_KEYSTORE_BASE64` is missing or empty. Secrets do not travel with a fork, so add all four in the repository you are tagging.
- **Pushing the tag starts nothing**: the tag has to begin with `v`. A tag named `0.1.0` triggers no workflow; `v0.1.0` does.
- **Gradle reports a keystore error during signing**: the alias or a password does not match the keystore. Check `CALLGUARD_KEY_ALIAS` first, then both passwords.

## Related

- [Build CallGuard and run it on a device](../tutorials/get-started.md)
