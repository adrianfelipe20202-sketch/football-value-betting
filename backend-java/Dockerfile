FROM eclipse-temurin:21-jre
WORKDIR /app
COPY app.jar app.jar
ENV PORT=10000
EXPOSE ${PORT}
ENTRYPOINT ["sh", "-c", "java -Xmx200m -Xms100m -XX:+UseSerialGC -XX:MaxMetaspaceSize=80m -XX:TieredStopAtLevel=1 -Dserver.port=$PORT -jar app.jar --spring.profiles.active=postgres"]
