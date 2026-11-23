#!/bin/bash
# ==============================================================================
# App Auxiliar 2 - AppSimuladorEmpresa (Sección 4.2.2)
# Simula comunicaciones de empresa cliente via API REST (requiere servidor en localhost:8080)
# ==============================================================================

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_DIR="$SCRIPT_DIR/.."

cd "$PROJECT_DIR"

JAR_FILE="target/gestion-cursos-bpm-0.0.1-SNAPSHOT.jar"

if [ -f "$JAR_FILE" ]; then
    echo ">>> Iniciando Simulador con JAR empaquetado: $JAR_FILE"
    java -cp "$JAR_FILE" \
         -Dloader.main=com.gestion.cursos.cli.AppSimuladorEmpresa \
         org.springframework.boot.loader.launch.PropertiesLauncher "$@"
else
    echo ">>> JAR no encontrado. Compilando y ejecutando con Maven..."
    mvn compile exec:java -Dexec.mainClass="com.gestion.cursos.cli.AppSimuladorEmpresa" -Dexec.args="$*"
fi
