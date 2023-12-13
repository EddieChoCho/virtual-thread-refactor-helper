FROM openjdk:17

RUN mkdir /app

COPY ../target/virtual-thread-refactor-helper-1.0-SNAPSHOT-jar-with-dependencies.jar /app

WORKDIR /app

CMD java -jar virtual-thread-refactor-helper-1.0-SNAPSHOT-jar-with-dependencies.jar