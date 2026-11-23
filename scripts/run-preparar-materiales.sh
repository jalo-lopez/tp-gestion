#!/bin/bash
# ==============================================================================
# App Auxiliar 1 - AppPrepararMateriales (Sección 4.2.1)
# Conecta directo a Flowable via API Java pura (misma BD que el servidor)
# Usa H2 en archivo compartido (AUTO_SERVER=TRUE permite múltiples procesos)
# ==============================================================================

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_DIR="$SCRIPT_DIR/.."

export SPRING_DATASOURCE_URL="jdbc:h2:file:/tmp/flowable_db;AUTO_SERVER=TRUE;DB_CLOSE_DELAY=-1"
export SPRING_DATASOURCE_USERNAME="sa"
export SPRING_DATASOURCE_PASSWORD=""
export SPRING_DATASOURCE_DRIVER="org.h2.Driver"

cd "$PROJECT_DIR"

JAR_FILE="target/gestion-cursos-bpm-0.0.1-SNAPSHOT.jar"

if [ -f "$JAR_FILE" ]; then
    echo ">>> Iniciando con JAR empaquetado: $JAR_FILE"
    java -cp "$JAR_FILE" \
         -Dloader.main=com.gestion.cursos.cli.AppPrepararMateriales \
         org.springframework.boot.loader.launch.PropertiesLauncher "$@"
else
    echo ">>> JAR no encontrado. Compilando y ejecutando con Maven..."
    mvn compile exec:java -Dexec.mainClass="com.gestion.cursos.cli.AppPrepararMateriales" -Dexec.args="$*"
fi
