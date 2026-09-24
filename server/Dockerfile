FROM maven:3.9.9-eclipse-temurin-17 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn -q -DskipTests dependency:go-offline
COPY src ./src
RUN mvn -q -DskipTests package

FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /app/target/galactic-8ball-server-1.0.0.jar /app/server.jar
EXPOSE 10000
CMD ["java","-Xms128m","-Xmx384m","-jar","/app/server.jar"]
