# syntax=docker/dockerfile:1

FROM eclipse-temurin:25-jdk-noble AS builder

WORKDIR /workspace

COPY gradle ./gradle
COPY gradlew settings.gradle ./
COPY app/build.gradle ./app/build.gradle
COPY app/src ./app/src

RUN chmod +x gradlew \
    && ./gradlew :app:bootJar --no-daemon

FROM eclipse-temurin:25-jre-noble AS runtime

RUN groupadd --system --gid 10001 townai \
    && useradd --system --uid 10001 --gid townai \
        --home-dir /app --shell /usr/sbin/nologin townai

WORKDIR /app

COPY --from=builder --chown=townai:townai \
    /workspace/app/build/libs/app.jar /app/app.jar

ENV JAVA_TOOL_OPTIONS="-Dfile.encoding=UTF-8 -XX:MaxRAMPercentage=75.0"

USER townai

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
