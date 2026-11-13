# Same GraalVM image as the backend's native build
FROM ghcr.io/graalvm/native-image-community:25i4-25.0.4.1.1-ol9 AS builder

WORKDIR /app

COPY mvnw .
COPY .mvn .mvn
COPY pom.xml .

RUN ./mvnw dependency:go-offline -B

COPY src src

RUN ./mvnw -Pnative package -DskipTests -B

FROM debian:stable-slim AS app

WORKDIR /app

RUN useradd --system --no-create-home relay \
    && apt-get update \
    && apt-get install -y --no-install-recommends ca-certificates \
    && rm -rf /var/lib/apt/lists/*

COPY --from=builder /app/target/lockate-push-relay-runner relay

USER relay

EXPOSE 8080

ENTRYPOINT ["./relay"]
