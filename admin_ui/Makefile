.PHONY: all test format format-check lint check ci build

all: ci

test:
	npx tsc --noEmit

format:
	npx prettier --write src

format-check:
	npx prettier --check src

lint:
	npm run lint

check: format-check lint

ci: format-check lint test

build:
	npm run build
