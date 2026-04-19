# Multi-stage build: compile with Maven, run on a slim JRE.
FROM eclipse-temurin:25-jdk-jammy AS build
WORKDIR /build
COPY .mvn/ .mvn
COPY mvnw pom.xml ./
RUN ./mvnw -B -q dependency:go-offline
COPY src ./src
RUN ./mvnw -B -q clean package -DskipTests

FROM eclipse-temurin:25-jre-jammy
WORKDIR /app
COPY --from=build /build/target/db-migration-quality-checker-*.jar app.jar
CMD ["java", "-jar", "app.jar"]
