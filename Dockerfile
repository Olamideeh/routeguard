FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

RUN addgroup -S routeguard && adduser -S routeguard -G routeguard

COPY --chown=routeguard:routeguard target/routeguard-0.0.1-SNAPSHOT.jar app.jar

USER routeguard

EXPOSE 8083

ENTRYPOINT ["java", "-jar", "app.jar"]