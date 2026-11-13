#!/bin/bash
# App Auxiliar 1 - AppPrepararMateriales
# Conecta directo a Flowable via API Java (misma BD que el servidor)
# Usa H2 en archivo compartido (AUTO_SERVER=TRUE permite múltiples procesos)

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_DIR="$SCRIPT_DIR/.."

export SPRING_DATASOURCE_URL="jdbc:h2:file:/tmp/flowable_db;DB_CLOSE_DELAY=-1"
export SPRING_DATASOURCE_USERNAME="sa"
export SPRING_DATASOURCE_PASSWORD=""
export SPRING_DATASOURCE_DRIVER="org.h2.Driver"

cd "$PROJECT_DIR"

java -cp target/gestion-cursos-bpm-0.0.1-SNAPSHOT.jar \
     -Dloader.main=com.gestion.cursos.cli.AppPrepararMateriales \
     org.springframework.boot.loader.launch.PropertiesLauncher
