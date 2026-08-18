FROM eclipse-temurin:25-jdk-alpine AS build
WORKDIR /workspace
COPY .mvn .mvn
COPY mvnw pom.xml ./
RUN ./mvnw -q -DskipTests dependency:go-offline
COPY src src
RUN ./mvnw -q -DskipTests package

FROM eclipse-temurin:25-jre-alpine
LABEL org.opencontainers.image.title="odoc-api" \
      org.opencontainers.image.description="Odoc Spring Boot API, worker, and parser runtime" \
      org.opencontainers.image.licenses="Apache-2.0"
RUN apk add --no-cache curl \
    && addgroup -S -g 10001 odoc \
    && adduser -S -u 10001 -G odoc odoc
WORKDIR /app
COPY --from=build /workspace/target/odoc-*.jar app.jar
USER 10001:10001
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75.0 -XX:InitialRAMPercentage=25.0 -Djava.io.tmpdir=/tmp"
EXPOSE 8080
STOPSIGNAL SIGTERM
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
