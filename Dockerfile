FROM maven:3.9-eclipse-temurin-26 AS build
WORKDIR /app
COPY pom.xml .
COPY src ./src
RUN mvn -q package -DskipTests

FROM eclipse-temurin:26-jdk AS index-builder
WORKDIR /app
COPY --from=build /app/target/rinha-backend-2026-1.0-SNAPSHOT.jar app.jar
COPY references.json.gz .
RUN java --add-modules jdk.incubator.vector \
      --enable-native-access=ALL-UNNAMED \
      --add-opens jdk.incubator.vector/jdk.incubator.vector=ALL-UNNAMED \
      -Xmx2g \
      -cp app.jar dev.denisarruda.rinha.IndexBuilder \
      references.json.gz index.bin labels.bin

FROM eclipse-temurin:26-jdk AS jlink
COPY --from=build /app/target/rinha-backend-2026-1.0-SNAPSHOT.jar /app/app.jar
RUN jdeps --ignore-missing-deps --multi-release 26 --print-module-deps /app/app.jar \
      | tr -d '[:space:]' > /modules.txt \
    && jlink \
      --no-header-files \
      --no-man-pages \
      --strip-debug \
      --compress zip-6 \
      --add-modules "$(cat /modules.txt),jdk.incubator.vector,java.logging,jdk.httpserver" \
      --output /custom-jre

FROM debian:bookworm-slim
RUN useradd --no-create-home --shell /bin/false app
COPY --from=jlink /custom-jre /opt/java
COPY --chown=app:app --from=build /app/target/rinha-backend-2026-1.0-SNAPSHOT.jar /app/app.jar
COPY --chown=app:app --from=index-builder /app/index.bin /app/index.bin
COPY --chown=app:app --from=index-builder /app/labels.bin /app/labels.bin
USER app
EXPOSE 8080
ENTRYPOINT ["/opt/java/bin/java", \
            "--add-modules", "jdk.incubator.vector", "--enable-preview", \
            "--enable-native-access=ALL-UNNAMED", \
            "--add-opens", "jdk.incubator.vector/jdk.incubator.vector=ALL-UNNAMED", \
            "-XX:MaxRAMPercentage=75", "-XX:InitialRAMPercentage=50", \
            "-XX:+UseZGC", "-XX:ConcGCThreads=1", \
            "-XX:+AlwaysPreTouch", "-Xss256k", \
            "-XX:ReservedCodeCacheSize=64m", "-XX:+TieredCompilation", \
            "-cp", "/app/app.jar", "dev.denisarruda.rinha.Application"]
