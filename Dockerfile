# ===== 1단계: 빌드 스테이지 (JDK로 jar 생성) =====
FROM eclipse-temurin:17-jdk AS builder
WORKDIR /workspace

COPY . .

RUN chmod +x gradlew && ./gradlew bootJar --no-daemon

# ===== 2단계: 실행 스테이지 (JRE만 든 가벼운 이미지) =====
FROM eclipse-temurin:17-jre

EXPOSE 8080

COPY --from=builder /workspace/build/libs/*.jar app.jar

ENTRYPOINT ["java", "-Xmx300m", "-Xss256k", "-jar", "/app.jar"]