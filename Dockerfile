# =========================
# Build stage
# =========================
FROM maven:3.9.16-eclipse-temurin-21 AS build

WORKDIR /app

# Dependencies first so they stay cached until pom.xml changes.
COPY pom.xml .
RUN mvn -B dependency:go-offline -DskipTests

COPY src/ src/
RUN mvn -B clean package -DskipTests


# =========================
# Runtime stage
# =========================
FROM eclipse-temurin:21-jre

WORKDIR /app

# Run as an unprivileged user rather than root.
RUN groupadd --system mynix && useradd --system --gid mynix --no-create-home mynix

COPY --from=build --chown=mynix:mynix /app/target/backend-0.0.1-SNAPSHOT.jar app.jar

# Sri Lanka time for logs as well (the app sets it for itself too).
ENV TZ=Asia/Colombo
# Size the heap from the container's memory limit; override with -e JAVA_TOOL_OPTIONS=...
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75.0 -XX:+ExitOnOutOfMemoryError"

USER mynix

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
