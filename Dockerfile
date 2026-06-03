# ── SmartHire · Dockerfile ──
# Stage 1: Maven build
FROM maven:3.9.7-eclipse-temurin-21-alpine AS builder
WORKDIR /build
COPY pom.xml .
RUN mvn dependency:go-offline -q
COPY src ./src
RUN mvn package -DskipTests -q

# Stage 2: Minimal JRE runtime
FROM eclipse-temurin:21-jre-alpine AS production
RUN addgroup -S smarthire && adduser -S smarthire -G smarthire
WORKDIR /app

COPY --from=builder /build/target/smarthire-*.jar app.jar
RUN chown smarthire:smarthire app.jar

USER smarthire
EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
    CMD wget -qO- http://localhost:8080/actuator/health | grep -q '"status":"UP"' || exit 1

ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -XX:+ExitOnOutOfMemoryError"
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
