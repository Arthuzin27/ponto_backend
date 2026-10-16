# syntax=docker/dockerfile:1
#
# Build reproduzivel: versao do Maven fixada no wrapper (com checksum) e dependencias resolvidas pelo pom.xml.
# Em producao, fixe tambem as imagens base por digest (ex.: eclipse-temurin:21-jre-noble@sha256:...).
# Os testes NAO rodam aqui: os testes de integracao precisam de Docker (Testcontainers) e devem ser
# executados antes da geracao da imagem com "./mvnw verify".

FROM eclipse-temurin:21-jdk-noble AS build
WORKDIR /workspace

COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN chmod +x mvnw && ./mvnw -B -q dependency:go-offline

COPY src/ src/
RUN ./mvnw -B -q package -DskipTests \
 && java -Djarmode=tools -jar target/sistema-ponto-*.jar extract --layers --launcher --destination target/extracted

FROM eclipse-temurin:21-jre-noble AS runtime

RUN apt-get update \
 && apt-get install -y --no-install-recommends curl \
 && rm -rf /var/lib/apt/lists/* \
 && groupadd --system ponto \
 && useradd --system --gid ponto --no-create-home --shell /usr/sbin/nologin ponto

WORKDIR /app
COPY --from=build /workspace/target/extracted/dependencies/ ./
COPY --from=build /workspace/target/extracted/spring-boot-loader/ ./
COPY --from=build /workspace/target/extracted/snapshot-dependencies/ ./
COPY --from=build /workspace/target/extracted/application/ ./

USER ponto

ENV TZ=UTC \
    SPRING_PROFILES_ACTIVE=prod \
    JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError -Duser.timezone=UTC -Dfile.encoding=UTF-8"

EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=5s --start-period=90s --retries=3 \
  CMD curl -fsS http://localhost:8080/actuator/health/liveness || exit 1

ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]
