FROM maven:3.9-eclipse-temurin-26 AS build
WORKDIR /app
COPY pom.xml .
COPY src ./src
RUN mvn -q package -DskipTests

FROM eclipse-temurin:26-jdk AS jlink
COPY --from=build /app/target/rinha-backend-2026-1.0-SNAPSHOT.jar /app/app.jar
RUN jdeps --ignore-missing-deps --print-module-deps /app/app.jar \
      | tr -d '[:space:]' > /modules.txt \
    && jlink \
      --no-header-files \
      --no-man-pages \
      --strip-debug \
      --compress zip-6 \
      --add-modules "$(cat /modules.txt)" \
      --output /custom-jre

FROM debian:bookworm-slim
RUN useradd --no-create-home --shell /bin/false app
COPY --from=jlink /custom-jre /opt/java
COPY --chown=app:app --from=build /app/target/rinha-backend-2026-1.0-SNAPSHOT.jar /app/app.jar
USER app
EXPOSE 8080
ENTRYPOINT ["/opt/java/bin/java", "-cp", "/app/app.jar", "dev.denisarruda.rinha.Application"]
