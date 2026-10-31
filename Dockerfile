# syntax=docker/dockerfile:1
#
# Backend Spring Boot (Java 25) para o Render.
# Build em duas etapas: a imagem final so tem o JRE e o jar.

# ---------- build ----------
FROM eclipse-temurin:25-jdk AS build
WORKDIR /app

# Camada de dependencias separada: so refaz o download quando o pom.xml muda.
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
# Garante LF e permissao de execucao mesmo se o checkout veio do Windows.
# go-offline e so cache: se algum plugin nao resolver aqui, o 'package' abaixo baixa o que faltar.
RUN sed -i 's/\r$//' mvnw && chmod +x mvnw \
 && (./mvnw -B -q dependency:go-offline || true)

COPY src/ src/
# Os testes rodam fora daqui (precisam de banco); o deploy so empacota.
RUN ./mvnw -B -q -DskipTests package \
 && cp target/*.jar app.jar

# ---------- runtime ----------
FROM eclipse-temurin:25-jre
WORKDIR /app

# Nao roda como root.
RUN groupadd --system app && useradd --system --gid app --no-create-home app
COPY --from=build /app/app.jar app.jar
USER app

# Plano gratuito do Render tem 512 MB: limita o heap a uma fracao da memoria do container.
ENV JAVA_OPTS="-XX:MaxRAMPercentage=70 -XX:+ExitOnOutOfMemoryError"

# O Render injeta PORT; a aplicacao le server.port=${PORT:8080}.
EXPOSE 8080

# DB_URL, DB_USER, DB_PASSWORD, CORS_ALLOWED_ORIGINS etc. vem de variaveis de ambiente
# (painel do Render). Nunca coloque credenciais nesta imagem.
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]
