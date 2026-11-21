# ==============================================================================
# Multi-Stage Non-Root Image: Portfolio Public Server (server/server-public)
# ==============================================================================
FROM eclipse-temurin:25-jdk-noble AS builder

WORKDIR /workspace
ENV KOTLIN_CLI_NO_WELCOME_BANNER=1

RUN apt-get update \
    && apt-get install -y --no-install-recommends curl ca-certificates \
    && rm -rf /var/lib/apt/lists/*

COPY . /workspace
RUN --mount=type=cache,target=/root/.cache/JetBrains/Kotlin \
    chmod +x ./kotlin \
    && KOTLIN_CLI_JAVA_HOME="${JAVA_HOME}" ./kotlin package -m server-public -f executable-jar

FROM eclipse-temurin:25-jre-alpine

LABEL org.opencontainers.image.title="portfolio-server-public" \
      org.opencontainers.image.description="Ktor Netty public read API, HTML Reader View, and Wasm SEO server" \
      org.opencontainers.image.source="https://github.com/cy/Portfolio"

RUN apk add --no-cache curl \
    && addgroup -S portfolio \
    && adduser -S portfolio -G portfolio

WORKDIR /app
COPY --from=builder --chown=portfolio:portfolio \
    /workspace/build/tasks/_server-public_executableJarJvm/server-public-jvm-executable.jar \
    /app/app.jar

USER portfolio
EXPOSE 8080

HEALTHCHECK --interval=10s --timeout=5s --start-period=15s --retries=6 \
    CMD curl -fsS http://127.0.0.1:8080/health/ready >/dev/null || exit 1

ENTRYPOINT ["java", "-XX:+UseContainerSupport", "-XX:MaxRAMPercentage=75.0", "-jar", "/app/app.jar"]
