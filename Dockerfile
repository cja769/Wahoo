FROM node:22-bookworm-slim AS web-build
WORKDIR /frontend
COPY frontend/package.json frontend/package-lock.json ./
RUN npm ci --no-audit --no-fund
COPY frontend/ ./
RUN npm run build

FROM eclipse-temurin:17-jdk-jammy AS server-build
WORKDIR /backend
COPY backend/ ./
COPY --from=web-build /frontend/build /frontend
RUN bash gradlew --no-daemon bootJar -PfrontendBuildDir=/frontend

FROM eclipse-temurin:17-jre-jammy
WORKDIR /app
COPY --from=server-build /backend/build/libs/wahoo-0.0.1-SNAPSHOT.jar ./wahoo.jar
COPY models/preview.wah ./preview.wah
ENV PORT=8081 WAHOO_PREVIEW_POLICY_PATH=/app/preview.wah
EXPOSE 8081
USER 10001:10001
ENTRYPOINT ["java", "-Xmx256m", "-jar", "/app/wahoo.jar"]
