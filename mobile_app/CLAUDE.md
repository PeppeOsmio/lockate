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
```

Always run these from `mobile_app/` (the Gradle wrapper lives here).
