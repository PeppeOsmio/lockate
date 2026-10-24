.PHONY: all test format format-check lint check ci build

all: ci

test:
	yarn tsc --noEmit

format:
	yarn prettier --write src

format-check:
	yarn prettier --check src

lint:
	yarn lint

check: format-check lint

ci: format-check lint test

build:
	yarn build
