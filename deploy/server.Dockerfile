FROM maven:3.9.9-eclipse-temurin-17 AS build
WORKDIR /build
COPY server/pom.xml ./pom.xml
RUN mvn -B dependency:go-offline
COPY server/src ./src
RUN mvn -B package

FROM eclipse-temurin:17-jre-jammy
RUN apt-get update && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd --system app && useradd --system --gid app app
WORKDIR /app
COPY --from=build --chown=app:app /build/target/task-hub-server-*.jar app.jar
USER app
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
