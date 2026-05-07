FROM eclipse-temurin:17-jdk AS stage1
WORKDIR /app

# 루트 빌드 파일 복사
COPY settings.gradle .
COPY build.gradle .
COPY gradlew .
COPY gradle gradle
RUN chmod +x gradlew

# 모듈 복사
COPY common common
COPY approval-service approval-service

RUN ./gradlew :approval-service:bootJar --no-daemon

# 두번째 스테이지: 이미지 경량화
FROM eclipse-temurin:17-jdk
WORKDIR /app
COPY --from=stage1 /app/approval-service/build/libs/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "-Dspring.profiles.active=prod", "app.jar"]
