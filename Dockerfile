# --- Build stage --------------------------------------------------------
FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /app

COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN ./mvnw -q -B dependency:go-offline

COPY src/ src/
# Tests need Testcontainers/Docker, which isn't available inside the image
# build itself - they already ran in CI/locally before this image is built.
RUN ./mvnw -q -B clean package -DskipTests

# --- Runtime stage -------------------------------------------------------
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

RUN addgroup -S pharmasense && adduser -S pharmasense -G pharmasense
COPY --from=build /app/target/pharmasense-backend-*.jar app.jar
RUN chown pharmasense:pharmasense app.jar
USER pharmasense

EXPOSE 8080
ENTRYPOINT ["java", "-XX:+UseContainerSupport", "-jar", "app.jar"]
