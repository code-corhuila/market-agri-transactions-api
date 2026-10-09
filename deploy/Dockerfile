# Build context: the repository root (see deploy/compose.yml).

FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /src

# Whole modules, not module/src: a module without sources yet must not break the build.
COPY pom.xml .
COPY transactions-core transactions-core
COPY transactions-adapters transactions-adapters
COPY transactions-app transactions-app

# Tests run in ci.yml; the image only packages.
RUN --mount=type=cache,target=/root/.m2 mvn -B -q -DskipTests package

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

RUN addgroup -S app && adduser -S app -G app
COPY --from=build /src/transactions-app/target/transactions-app-*.jar /app/app.jar
USER app

EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app/app.jar"]
