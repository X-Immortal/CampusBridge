FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /workspace
COPY pom.xml ./
COPY src src
RUN mvn -B -ntp package -Dmaven.test.skip=true

FROM eclipse-temurin:17-jre-jammy
WORKDIR /app
COPY --from=build /workspace/target/*.jar app.jar
ENV TZ=Asia/Shanghai
EXPOSE 8081
ENTRYPOINT ["java", "-jar", "app.jar"]
