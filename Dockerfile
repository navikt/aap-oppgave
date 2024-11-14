FROM gcr.io/distroless/java21-debian12:nonroot

WORKDIR /app
COPY /app/build/libs/app-all.jar /app/app.jar

ENV LANG='nb_NO.UTF-8' LC_ALL='nb_NO.UTF-8' TZ="Europe/Oslo"
ENV JDK_JAVA_OPTIONS="-XX:MaxRAMPercentage=75 -XX:ActiveProcessorCount=2"

CMD ["app.jar"]
