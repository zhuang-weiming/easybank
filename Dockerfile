FROM eclipse-temurin:21-jdk-jammy

WORKDIR /app

# Copy the JAR file
COPY target/*.jar app.jar

# Copy the wait-for-it script
COPY wait-for-it.sh /wait-for-it.sh

# Update and install dependencies without using proxy
RUN unset http_proxy https_proxy && \
    chmod +x /wait-for-it.sh && \
    apt-get update && \
    apt-get install -y netcat-openbsd curl && \
    rm -rf /var/lib/apt/lists/*

# Environment variables with defaults
ENV SPRING_DATASOURCE_URL=jdbc:postgresql://easybank-db.cd2ma6ye0kiw.us-west-2.rds.amazonaws.com:5432/easybank
ENV SPRING_DATASOURCE_USERNAME=postgres
ENV SPRING_DATASOURCE_PASSWORD=postgres
ENV SPRING_REDIS_HOST=easybank-redis.r1nket.0001.usw2.cache.amazonaws.com
ENV SPRING_REDIS_PORT=6379
ENV SERVER_PORT=8080
ENV DB_HOST=easybank-db.cd2ma6ye0kiw.us-west-2.rds.amazonaws.com
ENV DB_PORT=5432
ENV REDIS_HOST=easybank-redis.r1nket.0001.usw2.cache.amazonaws.com
ENV REDIS_PORT=6379

EXPOSE 8080

# Wait for PostgreSQL and Redis to be ready before starting the application
ENTRYPOINT ["/bin/sh", "-c", "/wait-for-it.sh \"$DB_HOST\" \"$DB_PORT\" /wait-for-it.sh \"$REDIS_HOST\" \"$REDIS_PORT\" java -jar app.jar"] 