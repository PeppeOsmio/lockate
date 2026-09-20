JAVA21 := /usr/lib/jvm/java-21-openjdk

.PHONY: all test format format-check lint check

test:
	./mvnw clean test -Pdev

format:
	JAVA_HOME=$(JAVA21) ./mvnw spotless:apply

format-check:
	JAVA_HOME=$(JAVA21) ./mvnw spotless:check

lint:
	./mvnw checkstyle:check

check: format-check lint

all: format-check lint test
