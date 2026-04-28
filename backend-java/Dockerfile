FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn dependency:go-offline -q
COPY src ./src
RUN mvn package -DskipTests -q

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar

ENV PORT=8080
EXPOSE ${PORT}
ENV JAVA_OPTS="-Xmx200m -Xms100m -XX:+UseSerialGC -XX:MaxMetaspaceSize=80m -XX:+TieredCompilation -XX:TieredStopAtLevel=1"
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -Dserver.port=$PORT -jar app.jar --spring.profiles.active=postgres"]
