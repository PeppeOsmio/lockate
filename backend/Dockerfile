# GraalVM CE 25.0.4 (25i4). The 25 tag is stuck on 25.0.2, which builds a much larger binary.
FROM ghcr.io/graalvm/native-image-community:25i4 AS builder

WORKDIR /app

COPY mvnw .
COPY .mvn .mvn
COPY pom.xml .

RUN ./mvnw dependency:go-offline -B

COPY src src

RUN ./mvnw -Pnative native:compile -DskipTests -B

FROM debian:stable-slim AS app

WORKDIR /app

RUN useradd --system --no-create-home lockate

COPY --from=builder /app/target/lockate lockate

USER lockate

EXPOSE 3118

ENTRYPOINT ["./lockate"]
