FROM maven:3.9.9-eclipse-temurin-8 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn -B dependency:go-offline
COPY src ./src
RUN mvn -B -DskipTests package

FROM eclipse-temurin:8-jre
WORKDIR /app
COPY --from=build /app/target/quiz-system-0.0.1-SNAPSHOT.jar app.jar
ENV SPRING_PROFILES_ACTIVE=prod
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
