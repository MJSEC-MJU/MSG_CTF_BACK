FROM eclipse-temurin:17.0.21_9-jdk-jammy@sha256:76751c16a2a07ec0960e659a2603491f8c79d95592173f6c0df8bf8ebab257cc AS builder
WORKDIR /app
COPY Back/ /app

# CRLF → LF
RUN sed -i 's/\r$//' gradlew

# 실행 권한
RUN chmod +x gradlew

RUN ./gradlew clean bootJar -x test
# ...

FROM eclipse-temurin:17.0.21_9-jre-jammy@sha256:aa6381384f8db99762fa7d0d3c2b5de58874e5f12c96df4c13a1455376ae0ed1
WORKDIR /app

# 빌드 산출물만 복사
COPY --from=builder /app/build/libs/*.jar app.jar

# 컨테이너 8080 포트 개방
EXPOSE 8080

# Spring Boot 실행 (환경 변수로 DB 정보, 프로파일 등 주입)
ENTRYPOINT ["java", "-jar", "app.jar"]
