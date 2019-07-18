FROM java:8-jdk

EXPOSE 5000
RUN mkdir app
WORKDIR /app

COPY ./target/ebsproto-1.0-SNAPSHOT-jar-with-dependencies.jar /app/ebsproto.jar

CMD ["sh", "-c", "java -jar ebsproto.jar"]
