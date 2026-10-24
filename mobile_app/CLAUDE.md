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

Releases are published by the `release` job of `.github/workflows/release-mobile.yml` when a `mobile-v*` tag is pushed (e.g. `mobile-v1.4.3`).

The EC-JPAKE native library (`jpake-native`, built from the Mbed TLS submodule in `third_party/mbedtls`) needs, once:

```bash
git submodule update --init --recursive
```

Android builds need the NDK and CMake 3.22.1 (Gradle installs them when the SDK licenses are accepted). `:jpake:jvmTest` and the app unit tests build the same C code for the host (`:jpake:buildJpakeHost`) with the system `cmake` and a C compiler.

Always run these from `mobile_app/` (the Gradle wrapper lives here).
