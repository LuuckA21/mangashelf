# Build context: the repository root  →  docker build -f backend/Dockerfile .
# The React app is built first and served by Spring Boot as static files (11-frontend.md).
FROM node:24-alpine AS frontend
WORKDIR /src/frontend
COPY frontend/package.json frontend/package-lock.json ./
RUN npm ci
COPY frontend/ ./
RUN npm run build

FROM eclipse-temurin:25-jdk AS build
WORKDIR /src
COPY backend/.mvn .mvn
COPY backend/mvnw backend/pom.xml ./
RUN ./mvnw -B -q dependency:go-offline
COPY backend/src src
COPY --from=frontend /src/frontend/dist src/main/resources/static
# Tests and format check already ran in CI (./mvnw verify)
RUN ./mvnw -B -q package -DskipTests

FROM eclipse-temurin:25-jre
# curl: used by the compose healthcheck
RUN apt-get update && apt-get install -y --no-install-recommends curl && rm -rf /var/lib/apt/lists/* \
 && useradd --system --uid 10001 app && mkdir -p /app/uploads && chown app /app/uploads
USER app
WORKDIR /app
COPY --from=build /src/target/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "app.jar"]
