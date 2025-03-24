FROM --platform=linux/amd64 public.ecr.aws/docker/library/openjdk:21-slim

WORKDIR /app

# Copy the JAR file
COPY target/*.jar app.jar

# Environment variables with defaults
ENV SPRING_DATASOURCE_URL=jdbc:postgresql://easybank-db.cd2ma6ye0kiw.us-west-2.rds.amazonaws.com:5432/easybank
ENV SPRING_DATASOURCE_USERNAME=postgres
ENV SPRING_DATASOURCE_PASSWORD=postgres
ENV SPRING_REDIS_HOST=easybank-redis.r1nket.0001.usw2.cache.amazonaws.com
ENV SPRING_REDIS_PORT=6379
ENV SERVER_PORT=8080

# Add connection retry settings
ENV SPRING_DATASOURCE_HIKARI_MAXIMUM-POOL-SIZE=5
ENV SPRING_DATASOURCE_HIKARI_MINIMUM-IDLE=2
ENV SPRING_DATASOURCE_HIKARI_CONNECTION-TIMEOUT=30000
ENV SPRING_DATASOURCE_HIKARI_INITIALIZATION-FAIL-TIMEOUT=60000
ENV SPRING_DATASOURCE_HIKARI_CONNECTION-RETRY-ATTEMPTS=3

# Redis retry settings
ENV SPRING_REDIS_TIMEOUT=30000
ENV SPRING_REDIS_CONNECT-TIMEOUT=30000

# Debug logging
ENV LOGGING_LEVEL_ROOT=INFO
ENV LOGGING_LEVEL_COM_ZAXXER_HIKARI=DEBUG
ENV LOGGING_LEVEL_ORG_SPRINGFRAMEWORK_DATA_REDIS=DEBUG
ENV SPRING_JPA_PROPERTIES_HIBERNATE_SHOW_SQL=true
ENV SPRING_JPA_PROPERTIES_HIBERNATE_FORMAT_SQL=true

EXPOSE 8080

# Start the application with debug options
ENTRYPOINT ["java", "-Xms256m", "-Xmx512m", "-XX:+HeapDumpOnOutOfMemoryError", "-Dspring.profiles.active=production", "-jar", "app.jar"] 