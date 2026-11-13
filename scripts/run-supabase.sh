#!/bin/bash

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_DIR="$SCRIPT_DIR/.."

set -a
source "$PROJECT_DIR/.env"
set +a

echo ">>> Conectando a: $SPRING_DATASOURCE_URL"
echo ">>> Usuario: $SPRING_DATASOURCE_USERNAME"
echo ""

cd "$PROJECT_DIR"
mvn spring-boot:run -Dspring-boot.run.jvmArguments="-Djava.net.preferIPv4Stack=true"
