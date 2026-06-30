FROM maven:3.9.5-eclipse-temurin-17-alpine AS builder

WORKDIR /app

COPY pom.xml .
RUN mvn dependency:go-offline -B

COPY src ./src
RUN mvn clean package -DskipTests -B -q

FROM eclipse-temurin:17-jre-alpine

WORKDIR /app

RUN mkdir -p /app/urls /app/output /app/logs /app/checkpoints /app/data

COPY --from=builder /app/target/ifood-crawler-1.0.0.jar /app/ifood-crawler.jar
COPY src/main/resources/application.properties /app/config/application.properties
COPY src/main/resources/logback.xml /app/config/logback.xml
COPY docker-entrypoint.sh /app/docker-entrypoint.sh

RUN chmod +x /app/docker-entrypoint.sh

ENTRYPOINT ["/app/docker-entrypoint.sh"]
CMD ["sh", "-c", "java $JAVA_OPTS -jar /app/ifood-crawler.jar"]
