FROM eclipse-temurin:17-jdk-alpine AS builder
WORKDIR /app
COPY . .
RUN ./mvnw clean package -DskipTests --no-transfer-progress 2>/dev/null || \
    (apk add --no-cache maven && mvn clean package -DskipTests --no-transfer-progress)

FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY --from=builder /app/report-hub-api/target/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
