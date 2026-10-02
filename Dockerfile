FROM --platform=$BUILDPLATFORM maven:3.9.9-eclipse-temurin-17 AS build
WORKDIR /workspace

COPY pom.xml .
COPY src ./src
RUN mvn -B -DskipTests package

FROM eclipse-temurin:17-jre
ARG APP_COMMIT=local
LABEL org.opencontainers.image.source="https://github.com/PetRoad/BackEnd" \
      org.opencontainers.image.revision="$APP_COMMIT"
ENV APP_COMMIT=$APP_COMMIT
RUN apt-get update && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/* \
    && useradd --uid 10001 --create-home app
WORKDIR /app

COPY --from=build /workspace/target/petroad-backend-0.0.1-SNAPSHOT.jar app.jar

EXPOSE 8080
USER 10001
HEALTHCHECK --interval=10s --timeout=5s --start-period=60s --retries=6 \
    CMD curl -fsS http://localhost:8080/api/health || exit 1
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-jar", "/app/app.jar"]
