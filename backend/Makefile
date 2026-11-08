.PHONY: all ci test format format-check lint check

test:
	./mvnw clean test -Pdev

format:
	./mvnw spotless:apply

format-check:
	./mvnw spotless:check

lint:
	./mvnw checkstyle:check

check: format-check lint

all: format format-check lint test

ci: format-check lint test
