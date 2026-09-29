# syntax=docker/dockerfile:1

# QueueFlow backend: built with the project's Maven Wrapper on a JDK, run on
# a JRE only, as a non-root user. Configuration (database, JWT secret, CORS
# origins) comes from environment variables at run time - nothing secret is
# ever part of the image.

# ---------------------------------------------------------------- build ----
FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /workspace

# The wrapper and the POM first: this layer only changes when they do.
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN chmod +x mvnw

COPY src/ src/

# The Maven distribution the wrapper downloads and the dependency repository
# live in a BuildKit cache mount, reused by later builds instead of being
# downloaded again (and never stored in an image layer). Tests are skipped
# here only: they need a PostgreSQL database, and run separately (-DskipTests
# still compiles them, so a broken test does not go unnoticed).
RUN --mount=type=cache,target=/root/.m2 \
    ./mvnw --batch-mode --no-transfer-progress package -DskipTests \
    && cp target/queueflow-backend-*.jar application.jar \
    # Spring Boot's layered layout: dependencies change rarely, the
    # application's own classes often, so each gets its own image layer.
    && java -Djarmode=tools -jar application.jar extract --layers --launcher --destination extracted

# -------------------------------------------------------------- runtime ----
FROM eclipse-temurin:21-jre-alpine

# An unprivileged system user with a fixed id: the process never runs as root.
RUN addgroup -S -g 10001 queueflow \
    && adduser -S -u 10001 -G queueflow -h /app -s /sbin/nologin queueflow

WORKDIR /app

# Least-changing layers first, so a code change only replaces the last one.
COPY --from=build --chown=queueflow:queueflow /workspace/extracted/dependencies/ ./
COPY --from=build --chown=queueflow:queueflow /workspace/extracted/spring-boot-loader/ ./
COPY --from=build --chown=queueflow:queueflow /workspace/extracted/snapshot-dependencies/ ./
COPY --from=build --chown=queueflow:queueflow /workspace/extracted/application/ ./

USER queueflow:queueflow

EXPOSE 8080

# The JVM sizes its heap from the container's memory limit; override with
# JDK_JAVA_OPTIONS at run time if needed.
ENV JDK_JAVA_OPTIONS="-XX:MaxRAMPercentage=75"

# Healthy once the application is up AND its database answers (the health
# endpoint includes the datasource check). The start period covers JVM
# startup and Flyway's migrations.
HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
    CMD wget --quiet --spider http://127.0.0.1:8080/actuator/health || exit 1

# Exec form: the JVM is PID 1 and receives SIGTERM directly, so Spring Boot's
# graceful shutdown (the default) finishes in-flight requests before exiting.
ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]
