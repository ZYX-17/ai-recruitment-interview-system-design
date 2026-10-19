# 通用多阶段构建（ARG MODULE 指定模块名；供 infra/docker-compose.app.yml 复用）
# 用法：docker build --build-arg MODULE=auth-service -t recruit/auth-service:dev backend/

FROM maven:3.9-eclipse-temurin-17 AS build
ARG MODULE
WORKDIR /build
# 先拷父 pom 与公共模块（利用层缓存）
COPY pom.xml .
COPY common ./common
COPY contracts ./contracts
COPY ${MODULE} ./${MODULE}
RUN mvn -q -B -DskipTests -pl ${MODULE} -am package

FROM eclipse-temurin:17-jre
ARG MODULE
WORKDIR /app
COPY --from=build /build/${MODULE}/target/${MODULE}.jar app.jar
ENV JAVA_OPTS="-Xms256m -Xmx512m"
EXPOSE 8080
ENTRYPOINT ["sh","-c","java $JAVA_OPTS -jar app.jar"]
