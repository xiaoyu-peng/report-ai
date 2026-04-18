FROM eclipse-temurin:17-jre
WORKDIR /app
COPY report-hub-api/target/report-hub-api-1.0.0.jar app.jar
EXPOSE 8080
ENV TZ=Asia/Shanghai JAVA_OPTS=""
ENTRYPOINT ["sh","-c","java $JAVA_OPTS -jar app.jar"]
