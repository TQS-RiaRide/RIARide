# Build stage
FROM eclipse-temurin:21-jdk-noble AS build
WORKDIR /app

# Without unzip, mvnw downloads the .tar.gz instead of the .zip and the
# distributionSha256Sum check (pinned to the .zip) fails.
RUN apt-get update && apt-get install -y --no-install-recommends unzip \
    && rm -rf /var/lib/apt/lists/*

# Dependencies first, so this layer is cached while only src/ changes
COPY mvnw pom.xml ./
COPY .mvn .mvn
RUN ./mvnw -B -q dependency:go-offline

COPY src src
# skipUnitTests, not skipTests: the pom binds Surefire's skipTests to it explicitly
RUN ./mvnw -B -q package -DskipUnitTests=true \
    && cp target/*.jar app.jar

# Runtime stage
FROM eclipse-temurin:21-jre-noble
WORKDIR /app

RUN groupadd --system app && useradd --system --gid app --no-create-home app
COPY --from=build --chown=app:app /app/app.jar app.jar
USER app

EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "app.jar"]
