# -------------------------------------------------------
# Stage 1 — Build the JAR with Maven
# -------------------------------------------------------
FROM maven:3.9-eclipse-temurin-17 AS builder

WORKDIR /build

# Copy POM first to download dependencies in a separate cache layer
COPY pom.xml .
RUN mvn dependency:go-offline -B --quiet

# Copy source and build (skip tests — tests run in CI)
COPY src ./src
RUN mvn clean package -DskipTests -B --quiet

# -------------------------------------------------------
# Stage 2 — Minimal JRE runtime image
# -------------------------------------------------------
FROM eclipse-temurin:17-jre-alpine

# Non-root user for rootless / Podman compatibility
RUN addgroup -S appgroup && adduser -S appuser -G appgroup
WORKDIR /app

COPY --from=builder /build/target/*.jar app.jar

RUN chown appuser:appgroup app.jar
USER appuser

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
