FROM clojure:openjdk-17-tools-deps AS builder

WORKDIR /app

COPY deps.edn .
COPY build.clj .
COPY src ./src

RUN clj -T:build uber

FROM eclipse-temurin:17-jre

WORKDIR /app

COPY --from=builder /app/target/github-bot.jar ./app.jar

CMD ["java", "-jar", "app.jar"]
