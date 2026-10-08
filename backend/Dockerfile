# syntax=docker/dockerfile:1
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /src
COPY pom.xml .
RUN mvn -q -B dependency:go-offline
COPY src ./src
# Tests need Docker (Testcontainers); they run in CI and locally with ./mvnw verify, not inside the image build.
RUN mvn -q -B package -DskipTests

FROM eclipse-temurin:21-jre
RUN groupadd --system warden && useradd --system --gid warden --no-create-home warden
WORKDIR /app
COPY --from=build /src/target/warden-*.jar /app/warden.jar
USER warden
EXPOSE 8080
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75"
ENTRYPOINT ["java", "-jar", "/app/warden.jar"]
