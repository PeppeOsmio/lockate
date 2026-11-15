# The 25 tag is stuck on GraalVM 25.0.2, which builds a much larger binary. ol9 because the default Oracle Linux 10 image needs x86-64-v3.
FROM ghcr.io/graalvm/native-image-community:25i4-25.0.4.1.1-ol9 AS builder

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

# The release version, answered by GET /api/version; set last so it doesn't invalidate the build cache
ARG LOCKATE_VERSION=dev
ENV LOCKATE_VERSION=$LOCKATE_VERSION

USER lockate

EXPOSE 3118

ENTRYPOINT ["./lockate"]
