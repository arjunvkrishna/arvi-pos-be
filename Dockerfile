# ── Stage 1: Build (cross-compiles on host architecture) ──────────────────────
FROM --platform=$BUILDPLATFORM maven:3.9.9-eclipse-temurin-17 AS build
ARG TARGETPLATFORM
ARG BUILDPLATFORM
WORKDIR /app

# Cache dependencies separately from source code
COPY pom.xml .
RUN mvn dependency:go-offline -B || true

COPY src ./src
RUN mvn package -DskipTests -B

# ── Stage 2: Minimal Java runtime for the TARGET platform (jlink) ─────────────
# Deliberately no --platform flag: this stage runs once per target architecture so the JRE matches it.
# Only the modules the app needs are kept (the full JRE is ~136 MB, this is roughly half).
FROM eclipse-temurin:17-jdk-noble AS jre-build
RUN "$JAVA_HOME/bin/jlink" \
      --add-modules java.base,java.datatransfer,java.desktop,java.instrument,java.logging,java.management,java.management.rmi,java.naming,java.net.http,java.prefs,java.rmi,java.scripting,java.security.jgss,java.security.sasl,java.sql,java.sql.rowset,java.transaction.xa,java.xml,java.xml.crypto,jdk.charsets,jdk.crypto.ec,jdk.localedata,jdk.management,jdk.unsupported,jdk.zipfs \
      --strip-debug --no-man-pages --no-header-files --compress=2 \
      --output /javaruntime

# ── Stage 3: Runtime (same Ubuntu 24.04 release as the Temurin JDK stage, so glibc matches) ──
FROM ubuntu:24.04 AS runtime
RUN apt-get update \
 && apt-get install -y --no-install-recommends ca-certificates curl fontconfig tzdata \
 && rm -rf /var/lib/apt/lists/*

ENV JAVA_HOME=/opt/java/openjdk
ENV PATH="${JAVA_HOME}/bin:${PATH}"
ENV LANG=C.UTF-8
COPY --from=jre-build /javaruntime ${JAVA_HOME}

WORKDIR /app

# Set timezone
ENV TZ=Asia/Kolkata
# Default JVM sizing for small hosts (docker-compose overrides with the same values). Serial GC + C1-only JIT keep RSS low.
ENV JAVA_OPTS="-XX:+UseSerialGC -Xms64m -Xmx320m -XX:MaxMetaspaceSize=160m -Xss512k -XX:ReservedCodeCacheSize=64m -XX:TieredStopAtLevel=1 -XX:MaxHeapFreeRatio=30 -XX:MinHeapFreeRatio=10 -XX:+ExitOnOutOfMemoryError"

COPY --from=build /app/target/*.jar app.jar
COPY logo-05.png .

# Run the application without root privileges. The jar is read-only at runtime;
# writable temporary files are created under the image's world-writable /tmp.
USER 10001:10001

EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 CMD curl -fsS http://127.0.0.1:8080/api/health || exit 1

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -Djava.security.egd=file:/dev/./urandom -jar app.jar"]
