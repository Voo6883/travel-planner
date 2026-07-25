# syntax=docker/dockerfile:1

# Travel Planner backend — multi-stage build (PLAN §4.0.0 Docker rules).
# Build context is apps/backend/.

# ---------- build ----------
FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /workspace

# Wrapper and build scripts first: dependency resolution is cached and only re-runs when the
# build definition changes, not on every source edit.
COPY gradle/ gradle/
COPY gradlew settings.gradle.kts build.gradle.kts ./
RUN chmod +x ./gradlew && ./gradlew --no-daemon dependencies --quiet || true

COPY src/ src/
# Tests run in CI (tasks/05), not in the image build — a build that also tests is slow and
# gives no extra safety here.
RUN ./gradlew --no-daemon clean bootJar -x test \
    && mv build/libs/*.jar /workspace/app.jar

# ---------- runtime ----------
FROM eclipse-temurin:21-jre-alpine AS runtime

# wget comes from busybox and is used by HEALTHCHECK; no curl install needed.
RUN addgroup -S app && adduser -S -G app app
WORKDIR /app

COPY --from=build --chown=app:app /workspace/app.jar /app/app.jar

USER app
EXPOSE 8080

ENV JAVA_OPTS="-XX:MaxRAMPercentage=75.0"

# Liveness only. Readiness (which includes the database) is Compose's concern, so a database
# outage does not restart a perfectly healthy container.
HEALTHCHECK --interval=15s --timeout=3s --start-period=45s --retries=5 \
    CMD wget -qO- http://127.0.0.1:8080/api/v1/health || exit 1

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
