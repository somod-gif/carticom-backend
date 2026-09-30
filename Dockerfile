FROM maven:3.9-eclipse-temurin-21
WORKDIR /app
COPY pom.xml .
RUN mvn -q dependency:go-offline
COPY src/ src/
RUN mvn package -DskipTests && cp target/*.jar /app/app.jar && ls -la /app/app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
