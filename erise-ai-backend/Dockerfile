# 基础镜像（推荐用开发版JDK，或带工具的镜像）

FROM maven:3.9.11-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
COPY src ./src
# 下载依赖（缓存层优化）

RUN mvn -q -DskipTests package

FROM eclipse-temurin:21-jre
# WORKDIR /app
COPY --from=build /app/target/erise-ai-backend-0.1.0.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
