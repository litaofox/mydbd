# syntax=docker/dockerfile:1

# ===== 构建阶段：Maven 编译多模块工程 =====
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /workspace

# 使用阿里云 Maven 镜像加速国内构建
RUN mkdir -p /root/.m2 && \
    printf '%s\n' \
    '<settings xmlns="http://maven.apache.org/SETTINGS/1.0.0">' \
    '  <mirrors>' \
    '    <mirror>' \
    '      <id>aliyun-public</id>' \
    '      <mirrorOf>central</mirrorOf>' \
    '      <name>Aliyun Public Mirror</name>' \
    '      <url>https://maven.aliyun.com/repository/public</url>' \
    '    </mirror>' \
    '  </mirrors>' \
    '</settings>' > /root/.m2/settings.xml

COPY . .
RUN mvn -B -ntp clean package -DskipTests

# ===== 运行阶段：仅 JRE + 可执行 jar =====
# 基础镜像自带 wget，健康检查直接使用，避免 apt 在线安装依赖
FROM eclipse-temurin:17-jre-jammy

WORKDIR /app
COPY --from=build /workspace/platform-boot/target/platform-app.jar /app/platform-app.jar

EXPOSE 8080

HEALTHCHECK --interval=15s --timeout=5s --start-period=60s --retries=10 \
    CMD wget -q -O- http://localhost:8080/actuator/health || exit 1

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app/platform-app.jar"]
