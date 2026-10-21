FROM eclipse-temurin:21.0.12.1_1-jdk-alpine-3.24 AS build
WORKDIR /workspace

COPY gradlew settings.gradle.kts build.gradle.kts gradle.properties gradle.lockfile settings-gradle.lockfile ./
COPY gradle/ gradle/
RUN chmod +x gradlew && ./gradlew --no-daemon dependencies > /dev/null

COPY src/ src/
RUN ./gradlew bootJar --no-daemon \
    && cp "$(ls build/libs/*.jar | grep -v -- '-plain' | head -n 1)" /workspace/app.jar

FROM eclipse-temurin:21.0.12.1_1-jre-alpine-3.24
WORKDIR /app

COPY --from=build --chown=1000:1000 /workspace/app.jar /app/app.jar

USER 1000:1000
EXPOSE 8080

HEALTHCHECK --interval=10s --timeout=3s --start-period=30s --retries=3 \
    CMD wget -q -O /dev/null http://127.0.0.1:8080/actuator/health || exit 1

CMD ["java", "-jar", "/app/app.jar"]
