# syntax=docker/dockerfile:1
#
# Um Dockerfile para todos os serviços. O estágio "builder" compila todos os jars uma vez
# (o BuildKit reaproveita o estágio entre os serviços); o estágio "service" copia só o jar
# do serviço indicado em SERVICE. Assim quem faz o workshop precisa apenas de Docker.

FROM eclipse-temurin:21-jdk AS builder
WORKDIR /workspace
COPY . .
RUN --mount=type=cache,target=/root/.gradle ./gradlew bootJar --no-daemon --quiet

FROM eclipse-temurin:21-jre AS service
ARG SERVICE
WORKDIR /app
COPY --from=builder /workspace/${SERVICE}/build/libs/app.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
