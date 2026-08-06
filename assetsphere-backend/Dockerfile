FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY pom.xml .
COPY src ./src
RUN mvn -q -DskipTests package

FROM eclipse-temurin:21-jre
WORKDIR /app
RUN useradd --system --create-home assetsphere
COPY --from=build /workspace/target/assetsphere-backend-*.jar app.jar
USER assetsphere
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
