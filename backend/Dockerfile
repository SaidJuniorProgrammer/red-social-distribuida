# --- ETAPA 1: Compilación ---
# Usamos una imagen oficial con Maven y Java 17
FROM maven:3.9.6-eclipse-temurin-17 AS build
WORKDIR /app

# Copiamos el pom.xml y el código fuente
COPY pom.xml .
COPY src ./src

# Compilamos el proyecto saltando los tests (ya los probamos localmente)
RUN mvn clean package -DskipTests

# --- ETAPA 2: Ejecución ---
# Usamos una imagen de Java más ligera para producción
FROM eclipse-temurin:17-jre
WORKDIR /deployments

# Copiamos solo los archivos compilados de la Etapa 1
COPY --from=build /app/target/quarkus-app/lib/ /deployments/lib/
COPY --from=build /app/target/quarkus-app/*.jar /deployments/
COPY --from=build /app/target/quarkus-app/app/ /deployments/app/
COPY --from=build /app/target/quarkus-app/quarkus/ /deployments/quarkus/

# Forzamos a Quarkus a usar el puerto 8080 y escuchar peticiones externas
ENV QUARKUS_HTTP_HOST=0.0.0.0
ENV QUARKUS_HTTP_PORT=8080
EXPOSE 8080

# Comando de arranque (Limitamos la memoria RAM para evitar que Render nos apague el servidor por exceder los 512MB)
ENTRYPOINT ["java", "-Xmx256m", "-Xms128m", "-jar", "/deployments/quarkus-run.jar"]