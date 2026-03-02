FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

# Install required native libraries for RocksDB
RUN apk add --no-cache libstdc++

COPY streammetric-streams/target/streammetric-streams-0.0.1-SNAPSHOT.jar app.jar

RUN addgroup -S spring && adduser -S spring -G spring
USER spring:spring

EXPOSE 8082

HEALTHCHECK --interval=30s --timeout=3s --start-period=40s \
  CMD wget --quiet --tries=1 --spider http://localhost:8082/actuator/health || exit 1

ENTRYPOINT ["java", "-jar", "app.jar"]