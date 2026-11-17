# Java 런타임 업데이트

2026-11-17 기준 빌드, 테스트, 실행 이미지는 공개된 Temurin 17.0.21+9를 사용하며 공식 이미지의 digest로 고정한다. Java 17과 Gradle 8.11.1 wrapper는 유지한다.

첨부된 Java 원격 코드 실행 취약점 사전 권고에 대한 해결 완료를 뜻하지 않는다. [Oracle 2026년 11월 CSPU](https://www.oracle.com/security-alerts/cspunov2026.html)는 현재 사전 공지이며, [Java 릴리스 일정](https://ops.java/releases/)의 17.0.21.1은 예정 버전이다. [Temurin 10월 공개 릴리스](https://adoptium.net/en-GB/news/2026/10/eclipse-temurin-17021-21013-2505-2701-available)에 맞춘 사전 정비이며, 해결 버전과 Temurin 제공 여부가 확인되면 JDK/JRE digest를 다시 갱신해야 한다.

## 검증과 배포

- CI는 현재 checkout의 dockerfile로 백엔드 이미지를 빌드하고 최종 이미지의 Java 버전을 출력한다
- CD는 이미지 재빌드 후 기존 배포 절차로 컨테이너를 교체하고 실행 중인 Java 버전을 출력한다
- 운영 완료 판단에는 실제 이미지 ID, java.vendor, java.runtime.version, API 및 DB/Redis/GCS 연결 확인이 필요하다
- 로컬 빌드는 `docker build --pull -f dockerfile -t msg-ctf-back:java17 .`로 실행한다
- 이미지 검증은 `docker run --rm --entrypoint java msg-ctf-back:java17 -XshowSettings:properties -version`으로 실행한다

배포 전 현재 운영 이미지의 커밋 태그와 image ID를 기록한다. 호환성 문제가 생기면 기존 커밋 태그로 IMAGE_TAG를 되돌린 뒤 백엔드만 다시 생성하고 API와 의존 서비스 연결을 확인한다. 이전 이미지로 돌아가면 Java 보안 수준도 함께 되돌아가므로 취약점 조치 완료로 처리하지 않는다.
