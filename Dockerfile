# syntax=docker/dockerfile:1

# 1. Build the React frontend. The generated SDK is committed, so no TypeSpec
#    toolchain is needed here.
FROM node:22-bookworm-slim AS frontend
WORKDIR /build/frontend
COPY frontend/package.json frontend/package-lock.json ./
RUN npm ci
COPY frontend/ ./
RUN npm run build

# 2. Build the Spring Boot jar. Maven copies frontend/dist into
#    target/classes/static, so one jar serves both the API and the UI.
FROM maven:3.9-eclipse-temurin-21 AS backend
WORKDIR /build
COPY backend/pom.xml backend/pom.xml
COPY contract/tsp-output contract/tsp-output
RUN mvn -q -f backend/pom.xml -DskipTests dependency:go-offline
COPY backend/src backend/src
COPY --from=frontend /build/frontend/dist frontend/dist
RUN mvn -q -f backend/pom.xml -DskipTests package

# 3. Runtime: only the JRE and the jar.
FROM eclipse-temurin:21-jre
WORKDIR /app
RUN groupadd --system app \
    && useradd --system --gid app app \
    && mkdir -p /app/data \
    && chown -R app:app /app
COPY --from=backend /build/backend/target/call-booking-1.0.0.jar /app/app.jar

# application.yml resolves server.port as ${PORT:8080}.
ENV PORT=8080
EXPOSE 8080
USER app

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
