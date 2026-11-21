FROM eclipse-temurin:21-jdk-alpine

WORKDIR /app

COPY board ./board
COPY card ./card
COPY dice ./dice
COPY player ./player
COPY square ./square
COPY trade ./trade
COPY server ./server
COPY public ./public

RUN mkdir -p out && \
    javac -encoding UTF-8 -d out board/*.java card/*.java dice/*.java player/*.java square/*.java trade/*.java server/*.java

ENV PORT=8080
EXPOSE 8080

CMD ["java", "-cp", "out", "ProjectOop.web.WebServerMain"]
