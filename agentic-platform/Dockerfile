FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /workspace
COPY . .
RUN chmod +x mvnw && ./mvnw -B -pl agentic-platform -am -DskipTests package

FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S agentic && adduser -S agentic -G agentic
WORKDIR /app
COPY --from=build /workspace/agentic-platform/target/agentic-platform-*.jar app.jar
RUN mkdir -p /app/runtime-data /app/repositories && chown -R agentic:agentic /app
USER agentic
EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app/app.jar"]
