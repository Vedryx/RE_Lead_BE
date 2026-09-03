# syntax=docker/dockerfile:1

# ---------------------------------------------------------------- build
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build

# Dependencies resolve from the pom alone, so this layer survives every change
# that does not touch it. Without the split, one edited Java file re-downloads
# the whole dependency tree.
COPY pom.xml .
RUN --mount=type=cache,target=/root/.m2 mvn -q dependency:go-offline

COPY src ./src
RUN --mount=type=cache,target=/root/.m2 mvn -q clean package -DskipTests

# Split the fat jar into layers that change at different rates: dependencies
# rarely, application code every push. Docker then reuses the big layer.
RUN java -Djarmode=tools -jar target/*.jar extract --layers --launcher --destination extracted

# ---------------------------------------------------------------- runtime
FROM eclipse-temurin:21-jre-alpine AS runtime

# A JVM that does not know it is in a container sizes its heap from the host's
# memory and gets OOM-killed on a small instance. These two flags are what make
# it survive a 512 MB plan.
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:InitialRAMPercentage=50 -XX:+UseSerialGC -XX:TieredStopAtLevel=1"

# Nothing here needs root. A compromised process should not be able to write to
# the image.
RUN addgroup -S app && adduser -S -G app app
WORKDIR /app

COPY --from=build --chown=app:app /build/extracted/dependencies/ ./
COPY --from=build --chown=app:app /build/extracted/spring-boot-loader/ ./
COPY --from=build --chown=app:app /build/extracted/snapshot-dependencies/ ./
COPY --from=build --chown=app:app /build/extracted/application/ ./

USER app

# Documentation only — the platform assigns the real port through PORT, which
# application.yml reads as ${PORT:8082}.
EXPOSE 8082

# Actuator is already public in SecurityConfig, so this needs no credentials.
HEALTHCHECK --interval=30s --timeout=3s --start-period=60s --retries=3 \
  CMD wget -qO- "http://127.0.0.1:${PORT:-8082}/actuator/health" | grep -q '"UP"' || exit 1

ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]
