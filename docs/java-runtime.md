# Java 런타임 업데이트

2026-11-18 기준 빌드, 테스트, 실행 이미지는 Amazon Corretto 17.0.21.1.2를 사용하며 공식 amazoncorretto:17-al2023 이미지의 digest로 고정한다. 실제 JVM 버전은 17.0.21.1+2-LTS이며 Java 17과 Gradle 8.11.1 wrapper를 유지한다.

[Corretto 공식 릴리스](https://github.com/corretto/corretto-17/releases/tag/17.0.21.1.2)와 [해당 버전 변경 기록](https://github.com/corretto/corretto-17/blob/17.0.21.1.2/CHANGELOG.md)은 2026-11-17 공개된 이 버전이 CVE-2026-114938을 수정한다고 명시한다. 이전 Temurin 17.0.21+9 사전 정비를 이 수정 버전으로 대체한다.

Ubuntu Jammy JRE에서 Amazon Linux 2023 기반 전체 JDK로 변경되므로 운영 환경의 인증서, 시간대, 외부 서비스 연결을 배포 후 확인해야 한다. 빌드와 테스트에 필요한 xargs는 java-build stage에 findutils로 설치하며 실행 이미지에는 추가하지 않는다. Dockerfile은 JVM과 벤더 버전이 예상 값과 다르면 빌드를 중단한다.

## 검증과 배포

- CI는 현재 checkout의 dockerfile로 백엔드 이미지를 빌드하고 최종 이미지의 Java 버전을 출력한다
- CD는 이미지 재빌드 후 기존 배포 절차로 컨테이너를 교체하고 실행 중인 Java 버전을 출력한다
- 운영 완료 판단에는 실제 이미지 ID, java.vendor, java.runtime.version, API 및 DB/Redis/GCS 연결 확인이 필요하다
- 로컬 빌드는 `docker build --pull -f dockerfile -t msg-ctf-back:java17 .`로 실행한다
- 이미지 검증은 `docker run --rm --entrypoint java msg-ctf-back:java17 -XshowSettings:properties -version`으로 실행한다
- 정상 값은 `java.runtime.version = 17.0.21.1+2-LTS`, `java.vendor.version = Corretto-17.0.21.1.2`다

배포 전 현재 운영 이미지의 커밋 태그와 image ID를 기록한다. 호환성 문제가 생기면 기존 커밋 태그로 IMAGE_TAG를 되돌린 뒤 백엔드만 다시 생성하고 API와 의존 서비스 연결을 확인한다. 이전 이미지로 돌아가면 Java 보안 수준도 함께 되돌아가므로 취약점 조치 완료로 처리하지 않는다.
