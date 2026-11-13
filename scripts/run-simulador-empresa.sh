#!/bin/bash
# App Auxiliar 2 - AppSimuladorEmpresa
# Simula comunicaciones de empresa cliente via API REST (requiere servidor en localhost:8080)

cd /home/lautaro/Descargas/gestion

java -cp target/gestion-cursos-bpm-0.0.1-SNAPSHOT.jar \
     -Dloader.main=com.gestion.cursos.cli.AppSimuladorEmpresa \
     org.springframework.boot.loader.launch.PropertiesLauncher
