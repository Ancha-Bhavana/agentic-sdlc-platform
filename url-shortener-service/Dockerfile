FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /workspace
COPY . .
RUN chmod +x mvnw && ./mvnw -B -pl url-shortener-service -am -DskipTests package

FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S shortener && adduser -S shortener -G shortener
WORKDIR /app
COPY --from=build /workspace/url-shortener-service/target/url-shortener-service-*.jar app.jar
RUN chown -R shortener:shortener /app
USER shortener
EXPOSE 8081
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app/app.jar"]
