# ===== Build stage =====
FROM maven:3.9-eclipse-temurin-17-alpine AS builder
WORKDIR /app

# Copy dependency file first for better layer caching
COPY pom.xml ./

# Create Maven source structure and copy Java sources
RUN mkdir -p src/main/java
COPY *.java src/main/java/

# Build the application (no tests)
RUN mvn -B -q package

# ===== Runtime stage =====
FROM eclipse-temurin:17-jre-alpine
WORKDIR /opt/app

# Copy built artifact
COPY --from=builder /app/target/app.jar /opt/app/app.jar

# Optionally document typical ports (HTTP/TCP) - configurable via env
EXPOSE 8080 9000

# Run as non-root for better security
RUN addgroup -S app && adduser -S -G app app
USER app

# Start the driver
ENTRYPOINT ["java","-jar","app.jar"]
