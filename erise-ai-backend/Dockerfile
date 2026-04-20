# 唯一官方后端镜像构建入口

FROM maven:3.9.11-eclipse-temurin-21 AS build
WORKDIR /app

COPY settings.xml /root/.m2/settings.xml
COPY pom.xml .
COPY src ./src

# 先做依赖预热，再正式打包，降低网络抖动导致的构建失败概率。
RUN mvn -q -s /root/.m2/settings.xml -Dmaven.test.skip=true dependency:go-offline || true
RUN mvn -q -s /root/.m2/settings.xml -Dmaven.test.skip=true package

FROM eclipse-temurin:21-jre
WORKDIR /app

RUN apt-get update && apt-get install -y --no-install-recommends curl && rm -rf /var/lib/apt/lists/*

COPY --from=build /app/target/erise-ai-backend-0.1.0.jar /app/app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
