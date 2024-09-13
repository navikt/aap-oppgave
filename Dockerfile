FROM eclipse-temurin:22-jre-alpine
ENV LANG='nb_NO.UTF-8' LANGUAGE='nb_NO:nb' LC_ALL='nb:NO.UTF-8' TZ="Europe/Oslo"
COPY /app/build/libs/app-all.jar app.jar
CMD ["java", "-XX:MaxRAMPercentage=75.0", "-XX:ActiveProcessorCount=2", "-jar", "app.jar"]
