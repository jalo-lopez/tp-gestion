#!/bin/bash
# ==============================================================================
# Precarga Automática de Escenarios para la Defensa (Grupo López, De Vigili, Viglione)
# Invoca el endpoint de inicialización para dejar listos los 11 escenarios de la consigna.
# ==============================================================================

SERVER_URL="${SERVER_URL:-http://localhost:8080}"

echo "=============================================================================="
echo "🎯 PREPARANDO ESCENARIOS PARA LA DEFENSA - GESTIÓN DE CURSOS BPM"
echo "=============================================================================="
echo "Verificando conexión con el servidor en $SERVER_URL..."

HEALTH=$(curl -s --max-time 3 "$SERVER_URL/actuator/health" 2>/dev/null || curl -s --max-time 3 "$SERVER_URL/panel-admin.html" 2>/dev/null)

if [ -z "$HEALTH" ]; then
    echo "❌ ERROR: El servidor Spring Boot no parece estar activo en $SERVER_URL."
    echo "   Por favor, iniciá el servidor en otra terminal con:"
    echo "   mvn spring-boot:run"
    exit 1
fi

echo "✅ Servidor detectado. Enviando orden de precarga de escenarios..."
echo ""

RESPUESTA=$(curl -s -X POST "$SERVER_URL/api/v1/cursos/procesos/defensa/cargar-escenarios")

echo "$RESPUESTA" | grep -q '"exito":true'
if [ $? -eq 0 ]; then
    echo "=============================================================================="
    echo "🚀 ¡ESCENARIOS GENERADOS EXITOSAMENTE PARA LA DEFENSA!"
    echo "=============================================================================="
    echo ""
    echo "📋 ESTADO DE LAS CONDICIONES PREPARADAS:"
    echo "  1. ⚡ Regla DMN (Escenario 5):"
    echo "     - Hay 5 instancias activas en ejecución."
    echo "     - Al registrar una nueva solicitud en http://localhost:8080/index.html,"
    echo "       la regla DMN evaluará 'cantInstanciasActivas >= 5' y la rechazará como NO VIABLE."
    echo ""
    echo "  2. 📝 Solicitud de Modificaciones (Escenario 2):"
    echo "     - Instancia [CURSO-ESC-2-MODIF] lista esperando respuesta."
    echo "     - Enviar modificaciones para que regrese a la bandeja de Laura Gómez."
    echo ""
    echo "  3. ❌ Rechazo de Propuesta (Escenario 3):"
    echo "     - Instancia [CURSO-ESC-3-RECHAZO] lista esperando respuesta."
    echo "     - Simular rechazo para ver la tarea 'Archivar solicitud' en el carril administrativo."
    echo ""
    echo "  4. ⏰ Timeout y Recordatorios (Escenario 4):"
    echo "     - Instancia [CURSO-ESC-4-TIMEOUT] con timer de propuesta activo."
    echo ""
    echo "  5. 🛑 Cancelación, Compensación y Pausa User Task (Escenario 6):"
    echo "     - Instancia [CURSO-ESC-6-CANCEL] en preparación con Aula Física y Virtual reservadas."
    echo "     - Al cancelar, se ejecutan las compensaciones automáticas y se frena en la tarea:"
    echo "       'Dejar una actividad User para que pare' (solicitada expresamente por la cátedra)."
    echo "     - Al completar la tarea, el flujo continúa por la compuerta XOR al cierre cancelado."
    echo ""
    echo "  6. ⚖️ Balanceo de Carga (Escenario 8):"
    echo "     - Tareas pendientes listas para verificar menor carga en 'Confeccionar lista definitiva'."
    echo ""
    echo "  7. 📊 Métricas e Históricos (Sección 4.3):"
    echo "     - Dashboard disponible en: http://localhost:8080/panel-admin.html"
    echo "     - App Swing disponible con: ./scripts/run-preparar-materiales.sh"
    echo "=============================================================================="
else
    echo "⚠️ Respuesta del servidor:"
    echo "$RESPUESTA"
fi
