FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /workspace
COPY .mvn .mvn
COPY mvnw pom.xml ./
RUN ./mvnw -q -DskipTests dependency:go-offline
COPY src src
RUN ./mvnw -q -DskipTests package

FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S odoc && adduser -S odoc -G odoc
WORKDIR /app
COPY --from=build /workspace/target/odoc-*.jar app.jar
USER odoc
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
