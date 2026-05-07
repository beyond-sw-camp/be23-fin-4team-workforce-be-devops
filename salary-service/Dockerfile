FROM eclipse-temurin:17-jdk AS stage1
WORKDIR /app

# GitHub Packages 인증
ARG GPR_USER
ARG GPR_TOKEN

# 루트 빌드 파일 복사
COPY settings.gradle .
COPY build.gradle .
COPY gradlew .
COPY gradle gradle
RUN chmod +x gradlew

# gradle.properties 생성 (GitHub Packages 인증)
RUN echo "gpr.user=${GPR_USER}" > gradle.properties && \
    echo "gpr.token=${GPR_TOKEN}" >> gradle.properties

# 모듈 복사
COPY common common
COPY salary-service salary-service

RUN ./gradlew :salary-service:bootJar --no-daemon

# 두번째 스테이지: 이미지 경량화
FROM eclipse-temurin:17-jdk
WORKDIR /app
COPY --from=stage1 /app/salary-service/build/libs/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "-Dspring.profiles.active=prod", "app.jar"]
