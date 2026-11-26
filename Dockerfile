FROM eclipse-temurin:21-jdk AS build
WORKDIR /app

ARG GITHUB_ACTOR

COPY gradlew settings.gradle.kts build.gradle.kts ./
COPY gradle gradle

RUN --mount=type=secret,id=github_token \
    GITHUB_ACTOR="$GITHUB_ACTOR" GITHUB_TOKEN="$(cat /run/secrets/github_token)" \
    ./gradlew dependencies --no-daemon

COPY src src
RUN --mount=type=secret,id=github_token \
    GITHUB_ACTOR="$GITHUB_ACTOR" GITHUB_TOKEN="$(cat /run/secrets/github_token)" \
    ./gradlew bootJar --no-daemon


FROM eclipse-temurin:21-jre
WORKDIR /app

RUN useradd --system --no-create-home --shell /usr/sbin/nologin app
COPY --from=build --chown=app:app /app/build/libs/*SNAPSHOT.jar app.jar
COPY --from=build --chown=app:app /app/build/newrelic/newrelic.jar newrelic.jar
COPY --chown=app:app newrelic/newrelic.yml newrelic.yml
USER app

ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -javaagent:/app/newrelic.jar"

# El puerto es configuración: el stack lo puede pisar con SERVER_PORT. Spring y el
# healthcheck leen esta misma variable, así nunca apuntan a puertos distintos.
ENV SERVER_PORT=8080
EXPOSE ${SERVER_PORT}

# Swarm da por lista la tarea recién cuando Spring contesta, no cuando arranca Java, y
# reinicia la app si deja de contestar. start-period cubre el arranque en frío de la VM.
# /actuator/health/** entra sin token (SecurityConfiguration).
HEALTHCHECK --interval=10s --timeout=3s --start-period=60s --retries=3 \
    CMD wget -q -O /dev/null "http://127.0.0.1:${SERVER_PORT}/actuator/health/readiness" || exit 1

ENTRYPOINT ["java", "-jar", "app.jar"]