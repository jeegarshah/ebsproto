FROM maven:3.6.1-jdk-8

EXPOSE 5000
RUN mkdir app
WORKDIR /app

COPY . /app
RUN mvn clean package

CMD ["sh", "-c", "java -jar /app/target/ebsproto-1.0-SNAPSHOT-jar-with-dependencies.jar"]
