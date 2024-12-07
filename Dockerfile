FROM clojure:openjdk-17-tools-deps

WORKDIR /app

COPY deps.edn .
COPY src ./src

RUN clj -M:deps

CMD ["clj", "-M:run"]
