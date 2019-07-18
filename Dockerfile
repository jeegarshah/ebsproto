FROM maven:3.6.1-jdk-8

EXPOSE 5000
RUN mkdir app
WORKDIR /app

COPY . /app
RUN mvn clean package

#COPY ./target/ebsproto-1.0-SNAPSHOT-jar-with-dependencies.jar /app/ebsproto.jar

CMD ["sh", "-c", "java -jar ./target/ebsproto-1.0-SNAPSHOT-jar-with-dependencies.jar"]
