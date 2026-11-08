.PHONY: all ci test format format-check lint check

all: format format-check lint test

ci: format-check lint test

test:
	./gradlew testDebugUnitTest :jpake:jvmTest

format:
	./gradlew spotlessApply

format-check:
	./gradlew spotlessCheck

lint:
	./gradlew :app:lintDebug

check: format-check lint
