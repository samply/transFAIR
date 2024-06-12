FROM maven:3.8.6-eclipse-temurin-17-focal AS build

WORKDIR /app

COPY . ./

ENV TZ=Europe/Berlin
RUN rm -rf target/*
RUN mvn install
RUN mv target/*.jar target/transFAIR.jar


FROM eclipse-temurin:17-focal

COPY --from=build /app/target/transFAIR.jar /app/
COPY --from=build /app/*.json /app/

WORKDIR /app
RUN apt -y remove curl
RUN apt -y auto-remove
USER 1001

CMD ["java", "-jar", "transFAIR.jar"]
