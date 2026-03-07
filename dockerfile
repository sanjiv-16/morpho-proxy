# 1. Define global arguments used in FROM instructions
ARG JAVA_VERSION=21

# ==========================================
# STAGE 1: Builder (Using standard Ubuntu-based JDK)
# ==========================================
FROM eclipse-temurin:${JAVA_VERSION}-jdk AS builder
WORKDIR /app

# Copy everything into the build container
COPY gradlew .
COPY gradle/ gradle/
COPY build.gradle settings.gradle ./

# Ensure Gradle wrapper has execute permissions and build the project
RUN chmod +x gradlew
RUN ./gradlew clean build -x test

# ==========================================
# STAGE 2: Runtime (Using standard Ubuntu-based JRE)
# ==========================================
FROM eclipse-temurin:${JAVA_VERSION}-jre
WORKDIR /app

# Bring in the build arguments
ARG APP_NAME
ARG APP_VERSION
ARG EXPOSE_PORT=8080

# Persist them as runtime environment variables
ENV APP_NAME=${APP_NAME}
ENV APP_VERSION=${APP_VERSION}
ENV PORT=${EXPOSE_PORT}

# Copy the built jar from the builder stage
COPY --from=builder /app/build/libs/*.jar app.jar

# Expose the configured port
EXPOSE ${PORT}

# Run the application
ENTRYPOINT ["java", "-jar", "app.jar"]