FROM amazoncorretto:17-al2023@sha256:9f2fde515fa060e6218b680d0f5045940d915af43af266145586344bddb30320 AS java-runtime
RUN java -XshowSettings:properties -version > /tmp/java-version.txt 2>&1 \
    && cat /tmp/java-version.txt \
    && grep -F 'java.runtime.version = 17.0.21.1+2-LTS' /tmp/java-version.txt \
    && grep -F 'java.vendor.version = Corretto-17.0.21.1.2' /tmp/java-version.txt \
    && rm /tmp/java-version.txt

FROM java-runtime AS java-build
RUN dnf install -y findutils && dnf clean all

FROM java-build AS builder
WORKDIR /app
COPY Back/ /app

# CRLF → LF
RUN sed -i 's/\r$//' gradlew

# 실행 권한
RUN chmod +x gradlew

RUN ./gradlew clean bootJar -x test
# ...

FROM java-runtime
WORKDIR /app

# 빌드 산출물만 복사
COPY --from=builder /app/build/libs/*.jar app.jar

# 컨테이너 8080 포트 개방
EXPOSE 8080

# Spring Boot 실행 (환경 변수로 DB 정보, 프로파일 등 주입)
ENTRYPOINT ["java", "-jar", "app.jar"]
