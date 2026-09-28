# =========================
# Build stage
# =========================
FROM maven:3.9.16-eclipse-temurin-21 AS build

WORKDIR /app

COPY pom.xml .

RUN mvn dependency:go-offline -DskipTests

COPY src/ src/

RUN mvn clean package -DskipTests


# =========================
# Runtime stage
# =========================
FROM eclipse-temurin:21-jre

WORKDIR /app

COPY --from=build /app/target/backend-0.0.1-SNAPSHOT.jar app.jar

# Sri Lanka time for logs as well (the app sets it for itself too).
ENV TZ=Asia/Colombo

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/app.jar"]