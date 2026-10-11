# CLAUDE.md (mobile_app)

Build/test rules specific to this module. See the repo-root `CLAUDE.md` for architecture and coding standards.

## Commands

```bash
# Debug build
./gradlew assembleDebug

# Run unit tests
./gradlew test

# Run a single test class
./gradlew testDebug --tests "com.peppeosmio.lockate.ExampleUnitTest"

# Install on connected device
./gradlew installDebug

# Signed release APKs (one per ABI + universal); needs LOCKATE_KEYSTORE_PATH,
# LOCKATE_KEYSTORE_PASSWORD, LOCKATE_KEY_ALIAS and LOCKATE_KEY_PASSWORD in the environment
./gradlew assembleRelease -PversionName=1.2.3 -PversionCode=42
```

Releases are published by the `release` job of `.github/workflows/ci-mobile.yml` when a `mobile-v*` tag is pushed (e.g. `mobile-v1.4.3`).

Always run these from `mobile_app/` (the Gradle wrapper lives here).
