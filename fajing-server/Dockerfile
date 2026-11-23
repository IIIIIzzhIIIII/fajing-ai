# 运行镜像：先在宿主机执行 mvn package 生成 target/fajing-server-1.0.0.jar
FROM eclipse-temurin:17-jre
WORKDIR /app
COPY target/fajing-server-1.0.0.jar app.jar
EXPOSE 8081
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app/app.jar"]
