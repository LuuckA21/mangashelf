# ---- build ----
FROM maven:3-eclipse-temurin-24@sha256:a137a467ec89b5713d0be817b55bdba6b4d6ef16e3d05565a79bc08d8e775a1c AS build
WORKDIR /build

# Dependency cache: copy only the pom first, so a source change does not
# invalidate the layer that downloaded the whole dependency tree.
COPY pom.xml .
RUN mvn -B dependency:go-offline

COPY src ./src
# Tests are skipped here on purpose: they need a Docker daemon to bring up
# Postgres, and this runs inside a build container that has none. CI runs
# them instead.
RUN mvn -B clean package -DskipTests

# ---- runtime ----
FROM eclipse-temurin:25-jre-alpine@sha256:3137541deb3cac6626b5d9a4a2187bc0d6a34312f858bd2c67dd01e732e6b682
WORKDIR /app

# The pinned image predates the available Alpine fixes. Keep repository
# signature verification enabled and stay on the image's Alpine branch.
RUN apk upgrade --no-cache libcrypto3 libssl3 openssl libexpat

RUN addgroup -S app && adduser -S app -G app \
    && mkdir -p /app/data/covers && chown -R app:app /app

COPY --from=build /build/target/*.jar app.jar
USER app

EXPOSE 8080
ENV JAVA_OPTS="-XX:MaxRAMPercentage=70"

HEALTHCHECK --interval=30s --timeout=3s --start-period=40s \
    CMD wget -qO- http://localhost:8080/actuator/health | grep -q UP || exit 1

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
