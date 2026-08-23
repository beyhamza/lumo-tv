# ==============================================================================
# lumo-api container image.
#
# BUILD CONTEXT IS THE REPOSITORY ROOT, not apps/api:
#   docker build -f apps/api/Dockerfile .
#
# It has to be. packages/contracts/openapi.yaml is an input to the build — the
# server interfaces are generated from it (ADR 0001) — so a context rooted at
# apps/api could not see it. The repository layout is reproduced inside the image
# so the relative path in build.gradle.kts resolves unchanged.
#
# Two stages: the build stage needs a full JDK and a Gradle cache, the runtime
# stage needs neither. No source, no Gradle and no build cache ship in the image.
# ==============================================================================

# ---- build -------------------------------------------------------------------
FROM eclipse-temurin:25-jdk AS build
WORKDIR /repo/apps/api

# The contract and the build declarations first, on their own layers: they change
# far less often than source, so editing a controller reuses the dependency
# download instead of repeating it.
COPY packages/contracts /repo/packages/contracts
COPY apps/api/gradlew /repo/apps/api/
COPY apps/api/gradle /repo/apps/api/gradle
COPY apps/api/settings.gradle.kts apps/api/build.gradle.kts apps/api/gradle.properties /repo/apps/api/

RUN --mount=type=cache,target=/root/.gradle \
    ./gradlew --no-daemon --quiet dependencies || true

COPY apps/api/src /repo/apps/api/src
RUN --mount=type=cache,target=/root/.gradle \
    ./gradlew --no-daemon bootJar -x test

# Unpack the fat jar so the runtime image can layer it: dependencies (large,
# stable) underneath application classes (small, rebuilt every time).
RUN java -Djarmode=tools -jar build/libs/lumo-api-*.jar extract --layers --destination /extracted

# ---- runtime -----------------------------------------------------------------
FROM eclipse-temurin:25-jre AS runtime

# curl backs the container health check in docker-compose.yml. Removing it
# without changing that probe leaves the container in "starting" forever, and
# lumo-api never becomes ready.
RUN apt-get update \
 && apt-get install -y --no-install-recommends curl \
 && rm -rf /var/lib/apt/lists/*

# Never root: this process opens outbound connections to arbitrary third-party
# IPTV servers.
RUN useradd --system --uid 10001 --create-home --shell /usr/sbin/nologin lumo
USER lumo
WORKDIR /app

COPY --from=build --chown=lumo:lumo /extracted/dependencies/ ./
COPY --from=build --chown=lumo:lumo /extracted/spring-boot-loader/ ./
COPY --from=build --chown=lumo:lumo /extracted/snapshot-dependencies/ ./
COPY --from=build --chown=lumo:lumo /extracted/application/ ./

EXPOSE 8080

# Size the heap from the CONTAINER limit. Without this the JVM reads host memory
# and gets OOM-killed by the cgroup.
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError"

ENTRYPOINT ["java", "-jar", "app.jar"]
