FROM gradle:8.1.1-jdk17 AS builder
WORKDIR /app
COPY Back/ /app

# CRLF → LF
RUN sed -i 's/\r$//' gradlew

# 실행 권한
RUN chmod +x gradlew

RUN ./gradlew clean bootJar -x test
# ...

FROM eclipse-temurin:17-jre
WORKDIR /app

# 빌드 산출물만 복사
COPY --from=builder /app/build/libs/*.jar app.jar

# 컨테이너 8080 포트 개방
EXPOSE 8080

# Spring Boot 실행 (환경 변수로 DB 정보, 프로파일 등 주입)
ENTRYPOINT ["java", "-jar", "app.jar"]
