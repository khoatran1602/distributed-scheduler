# Multi-stage build for efficient Docker image

# Stage 1: Build
FROM eclipse-temurin:21-jdk-jammy as builder
WORKDIR /app
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
# Resolve dependencies first (caching layer)
RUN ./mvnw dependency:go-offline
COPY src ./src
# Build the application
RUN ./mvnw package -DskipTests

# Stage 2: Runtime
FROM eclipse-temurin:21-jre-jammy
WORKDIR /app
COPY --from=builder /app/target/*.jar app.jar

# Expose port
EXPOSE 8080

# Environment variables with defaults
ENV SCHEDULER_BROKER_TYPE=kafka
ENV SCHEDULER_QUEUE_NAME=task-queue

# Run application
ENTRYPOINT ["java", "-jar", "app.jar"]
