# syntax=docker/dockerfile:1.7
#
# Single-container QA image: Angular bundle + Spring Boot API + MariaDB + Redis
# all running together under supervisord. Build context is the repo root.
#
# Usage (on the server):
#   docker build -f orbix-engine-infra/qa/Dockerfile -t orbix:qa .
#   docker volume create orbix-data
#   docker run -d --name orbix \
#     -p 80:8081 \
#     -v orbix-data:/var/lib/mysql \
#     --restart unless-stopped \
#     orbix:qa
#
# Boot takes ~30s (MariaDB init on first run, then Flyway migrations, then JVM start).

# ---------- Stage 1: build the Angular bundle ----------
FROM node:20-alpine AS web-build
WORKDIR /web
# package-lock.json is gitignored in this repo, so the box's `git clone` doesn't
# have it — use `npm install` (resolves from package.json) rather than `npm ci`
# (which demands a committed lockfile). Commit the lockfile and switch back to
# `npm ci` if you want byte-reproducible installs.
COPY orbix-engine-web/package.json ./
RUN npm install --no-audit --no-fund
COPY orbix-engine-web/ ./
RUN npm run build
# Output lands in /web/dist/orbix-engine-web/browser/ (Angular application builder).

# ---------- Stage 2: build the Spring Boot fat jar ----------
FROM maven:3.9-eclipse-temurin-21 AS api-build
WORKDIR /api
# Cache dependencies first
COPY orbix-engine-api/pom.xml ./
RUN mvn -B -q -DskipTests dependency:go-offline
# Bring in the API sources
COPY orbix-engine-api/src ./src
# Drop the Angular bundle into the Spring Boot static path so it ships inside the jar
COPY --from=web-build /web/dist/orbix-engine-web/browser/ ./src/main/resources/static/
# -Dmaven.test.skip=true skips test *compilation* too (not just execution like
# -DskipTests). The unit suite has pre-existing compile debt (e.g. RoleAdmin
# tests reference removed pre-uid method signatures); the QA image doesn't run
# tests, so don't compile them.
RUN mvn -B -q -Dmaven.test.skip=true package \
 && cp target/*.jar /api/app.jar

# ---------- Stage 3: runtime ----------
FROM eclipse-temurin:21-jre-jammy

ENV DEBIAN_FRONTEND=noninteractive
RUN apt-get update \
 && apt-get install -y --no-install-recommends \
        mariadb-server \
        mariadb-client \
        redis-server \
        supervisor \
        tini \
        curl \
        ca-certificates \
 && rm -rf /var/lib/apt/lists/* \
 && rm -rf /var/lib/mysql/* \
 && mkdir -p /var/log/supervisor /var/run/redis

# MariaDB tuning: bind localhost only, modern timestamp semantics, utf8mb4.
COPY orbix-engine-infra/qa/mariadb.cnf /etc/mysql/mariadb.conf.d/99-orbix.cnf

# Supervisor process map.
COPY orbix-engine-infra/qa/supervisord.conf /etc/supervisor/supervisord.conf

# First-run DB bootstrap + handoff to supervisord.
COPY orbix-engine-infra/qa/entrypoint.sh /usr/local/bin/entrypoint.sh
RUN chmod +x /usr/local/bin/entrypoint.sh

# The Spring Boot fat jar.
COPY --from=api-build /api/app.jar /opt/orbix/app.jar

# Spring Boot config: QA profile points at localhost MariaDB + Redis.
COPY orbix-engine-infra/qa/application-qa.yml /opt/orbix/application-qa.yml
ENV SPRING_CONFIG_ADDITIONAL_LOCATION=file:/opt/orbix/application-qa.yml \
    SPRING_PROFILES_ACTIVE=mysql,qa \
    JWT_SIGNING_MODE=dev-in-memory

VOLUME ["/var/lib/mysql"]
EXPOSE 8081

# tini reaps zombies and forwards signals (supervisord doesn't always do this well).
ENTRYPOINT ["/usr/bin/tini", "--", "/usr/local/bin/entrypoint.sh"]
