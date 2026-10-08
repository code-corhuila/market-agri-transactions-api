FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY pom.xml .
COPY domain/pom.xml domain/pom.xml
COPY application/pom.xml application/pom.xml
COPY infrastructure/pom.xml infrastructure/pom.xml
RUN mvn -B -ntp -pl infrastructure -am dependency:go-offline
COPY domain/src domain/src
COPY application/src application/src
COPY infrastructure/src infrastructure/src
RUN mvn -B -ntp -pl infrastructure -am -DskipTests package

FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S app && adduser -S -G app app
WORKDIR /app
COPY --from=build /workspace/infrastructure/target/transactions-infrastructure-0.1.0-SNAPSHOT.jar app.jar
USER app
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
