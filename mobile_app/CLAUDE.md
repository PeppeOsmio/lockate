# CLAUDE.md (mobile_app)

Build/test rules specific to this module. See the repo-root `CLAUDE.md` for the security invariants, the coding standards and which `docs/` page to read for each area.

## Commands

```bash
# Format (ktfmt), check formatting, Android lint and test (CI runs `make ci`: the same without formatting)
make all

# Debug build
./gradlew assembleProdDebug

# Run unit tests
./gradlew test

# Run a single test class
./gradlew testProdDebugUnitTest --tests "com.peppeosmio.lockate.ExampleUnitTest"

# Install on connected device (installStagingDebug is the staging flavor, installed beside it)
./gradlew installProdDebug

# Signed release APKs (one per ABI + universal); needs LOCKATE_KEYSTORE_PATH,
# LOCKATE_KEYSTORE_PASSWORD, LOCKATE_KEY_ALIAS and LOCKATE_KEY_PASSWORD in the environment
./gradlew assembleProdRelease -PversionName=1.2.3 -PversionCode=42
```

Releases are published by the `release` job of `.github/workflows/release-mobile.yml` when a `mobile-v*` tag is pushed (e.g. `mobile-v1.4.3`).

The EC-JPAKE native library (`jpake-native`, built from the Mbed TLS submodule in `third_party/mbedtls`) needs, once:

```bash
git submodule update --init --recursive
```

Android builds need the NDK and CMake 3.22.1 (Gradle installs them when the SDK licenses are accepted). `:jpake:jvmTest` and the app unit tests build the same C code for the host (`:jpake:buildJpakeHost`) with the system `cmake` and a C compiler.

Always run these from `mobile_app/` (the Gradle wrapper lives here).

Two flavors, `prod` (`com.peppeosmio.lockate`) and `staging` (`.staging`, named "Lockate Staging"), install side by side with separate data, Keystore keys and FCM tokens. Releases build `prod` only. Without the `lockate.firebase.*` properties (`lockate.firebase.staging.*` for staging) the app has no FCM token and gets no pushes; see `docs/architecture/environments.md`.

Emulators can't show Activity Recognition (it never reports there) or the CPU sleeping, so still mode and wake-lock bugs need a real phone. The send loop writes `send-loop.log` in the app's external files directory (`Android/data/<package>/files/`): activity transitions and the mode of each send loop.
