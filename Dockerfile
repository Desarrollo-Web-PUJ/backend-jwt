# ---------------------------------------------------------------
# Etapa 1: compilación con Maven (no hace falta tener Maven ni el
# JAR compilado en la máquina: todo ocurre dentro del contenedor)
# ---------------------------------------------------------------
FROM maven:3.9-eclipse-temurin-17 AS build

WORKDIR /app

# Primero solo el pom: las dependencias quedan en caché mientras
# no cambie el pom.xml, lo que acelera las siguientes compilaciones.
COPY pom.xml .
RUN mvn -B -q dependency:go-offline

COPY src ./src
RUN mvn -B -q -DskipTests package

# ---------------------------------------------------------------
# Etapa 2: imagen final, solo con el JRE y el JAR
# ---------------------------------------------------------------
FROM eclipse-temurin:17-jre-alpine

WORKDIR /app

# La aplicación no se ejecuta como root
RUN addgroup -S spring && adduser -S spring -G spring
USER spring

COPY --from=build /app/target/*.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
