# ---- build ----
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn -q -B dependency:go-offline
COPY src ./src
RUN mvn -q -B -DskipTests package

# ---- run ----
FROM eclipse-temurin:17-jre
RUN useradd --system --create-home civicollect
WORKDIR /app
COPY --from=build /app/target/civicollect-api-1.0.0.jar app.jar
RUN mkdir -p /app/uploads && chown -R civicollect /app
USER civicollect
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
