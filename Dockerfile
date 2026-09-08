FROM maven:3.9.14-eclipse-temurin-21 AS build
WORKDIR /build
COPY pom.xml .
COPY src ./src
RUN mvn -q -DskipTests package

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /build/target/myredis-0.1.0-SNAPSHOT.jar /app/myredis.jar
COPY myredis.conf /app/myredis.conf
COPY logback.xml /app/logback.xml
RUN mkdir -p /app/data \
    && useradd --system --uid 10001 --home-dir /app --no-create-home myredis \
    && chown -R myredis:myredis /app
VOLUME ["/app/data"]
EXPOSE 6379
USER myredis
HEALTHCHECK --interval=30s --timeout=3s --start-period=5s --retries=3 \
    CMD ["java", "-cp", "/app/myredis.jar", "com.myredis.HealthCheck"]
ENTRYPOINT ["java", "-Dlogback.configurationFile=/app/logback.xml", "-jar", "/app/myredis.jar"]
