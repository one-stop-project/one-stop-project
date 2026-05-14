# 빌드 스테이지
FROM gradle:8.14-jdk17 AS build
WORKDIR /app
COPY . .
RUN gradle bootJar --no-daemon

# 실행 스테이지
FROM openjdk:17-jdk-slim
WORKDIR /app

RUN useradd --create-home --shell /usr/sbin/nologin appuser

COPY --from=build /app/build/libs/app.jar app.jar

EXPOSE 8080
USER appuser
ENTRYPOINT ["java", "-jar", "app.jar"]
