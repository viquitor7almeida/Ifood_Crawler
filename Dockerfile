#build com maven
FROM maven:3.9.5-eclipse-temurin-17-alpine AS builder

WORKDIR /app

#copiar arquivos de configuraçao do maven
COPY pom.xml .

#baixar dependencias (cache layer para builds futuros)
RUN mvn dependency:go-offline -B

#copiar codigo fonte
COPY src ./src

#compilar e empacotar (pula testes para velocidade)
RUN mvn clean package -DskipTests -B

#imagem final
FROM eclipse-temurin:17-jre-alpine

#instalar dependencias do sistema para playwright
#chromium e fontes necessarias para renderizaçao
RUN apk add --no-cache \
    chromium \
    nss \
    freetype \
    harfbuzz \
    ca-certificates \
    ttf-freefont \
    fontconfig \
    && rm -rf /var/cache/apk/*

#configurar variaveis de ambiente para o playwright
ENV PLAYWRIGHT_SKIP_BROWSER_DOWNLOAD=1
ENV PLAYWRIGHT_BROWSERS_PATH=/usr/lib/chromium

#riar usuario nao root para seguranca
RUN addgroup -S appgroup && adduser -S appuser -G appgroup

#criar diretorios da aplicaçao
WORKDIR /app
RUN mkdir -p /app/urls /app/output /app/logs /app/checkpoints && \
    chown -R appuser:appgroup /app

#copiar JAR do estágio de build
COPY --from=builder /app/target/*.jar /app/ifood-crawler.jar

# copiar arquivos de configuraçao
COPY src/main/resources/application.properties /app/config/application.properties
COPY src/main/resources/logback.xml /app/config/logback.xml

#copiar script de entrada
COPY docker-entrypoint.sh /app/docker-entrypoint.sh
RUN chmod +x /app/docker-entrypoint.sh && \
    chown appuser:appgroup /app/docker-entrypoint.sh

#trocar para usuario nao root
USER appuser

#porta exposta 
EXPOSE 8080

#ponto de entrada
ENTRYPOINT ["/app/docker-entrypoint.sh"]

#comando padrao
CMD ["java", "-jar", "/app/ifood-crawler.jar"]