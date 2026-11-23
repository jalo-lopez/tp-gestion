#!/usr/bin/env node

/**
 * ============================================================================
 * SIMULADOR DE COMUNICACIONES DE LA EMPRESA (API REST - Sección 4.2.2)
 * ============================================================================
 * Cumple con los requerimientos de la Sección 4.2.2:
 * 1. Seleccionar una solicitud/instancia activa de Flowable o crear una nueva.
 * 2. Consultar y mostrar ÚNICAMENTE las comunicaciones habilitadas en el estado actual.
 * 3. Enviar las 5 comunicaciones de negocio mediante la API REST:
 *    - [1] Aceptación de la propuesta (con fecha acordada)
 *    - [2] Rechazo de la propuesta (con motivo)
 *    - [3] Solicitud de modificaciones (con observaciones)
 *    - [4] Envío de nómina de participantes (avanza rama de inscripciones)
 *    - [5] Cancelación durante la preparación (interrumpe y dispara compensaciones)
 * 4. Alternar simulación del Escenario 7 (Falta de aulas disponibles).
 * ============================================================================
 */

import readline from 'node:readline/promises';
import { stdin as input, stdout as output } from 'node:process';

const colors = {
  reset: '\x1b[0m',
  bold: '\x1b[1m',
  cyan: '\x1b[36m',
  green: '\x1b[32m',
  yellow: '\x1b[33m',
  red: '\x1b[31m',
  magenta: '\x1b[35m',
  gray: '\x1b[90m'
};

const DEFAULT_BASE_URL = process.env.API_BASE_URL || 'http://localhost:8080/api/v1/cursos/procesos';
const rl = readline.createInterface({ input, output });

async function main() {
  console.clear();
  console.log(`${colors.cyan}${colors.bold}====================================================================${colors.reset}`);
  console.log(`${colors.cyan}${colors.bold}   FLOWABLE BPM - SIMULADOR DE COMUNICACIONES REST (Sección 4.2.2)  ${colors.reset}`);
  console.log(`${colors.cyan}${colors.bold}====================================================================${colors.reset}`);
  console.log(`${colors.gray}Destino API REST: ${DEFAULT_BASE_URL}${colors.reset}\n`);

  try {
    while (true) {
      // 1. Seleccionar o crear instancia de proceso
      const processInstanceId = await seleccionarOIniciarInstancia();
      if (!processInstanceId) break;

      // 2. Gestionar comunicaciones sobre la instancia seleccionada
      let enMismaInstancia = true;
      while (enMismaInstancia) {
        // Consultar estado en la base institucional
        await mostrarEstadoInstancia(processInstanceId);

        // Consultar comunicaciones habilitadas desde el backend
        const habilitadas = await consultarComunicacionesHabilitadas(processInstanceId);

        console.log(`\n${colors.magenta}${colors.bold}--------------------------------------------------------------------${colors.reset}`);
        console.log(`${colors.bold}COMUNICACIONES DE EMPRESA HABILITADAS EN EL ESTADO ACTUAL:${colors.reset}`);
        
        if (habilitadas.length === 0) {
          console.log(`  ${colors.yellow}[!] Ninguna comunicación empresarial activa en este momento.${colors.reset}`);
          console.log(`      ${colors.gray}(El proceso está en manos de Administración o Docentes)${colors.reset}`);
        } else {
          if (habilitadas.includes('ACEPTAR_PROPUESTA')) {
            console.log(`  ${colors.green}[1] Aceptar propuesta${colors.reset}           -> Acuerda fecha y pasa a Preparación`);
          }
          if (habilitadas.includes('RECHAZAR_PROPUESTA')) {
            console.log(`  ${colors.red}[2] Rechazar propuesta${colors.reset}          -> Desestima la capacitación y archiva`);
          }
          if (habilitadas.includes('SOLICITAR_MODIFICACIONES')) {
            console.log(`  ${colors.yellow}[3] Solicitar modificaciones${colors.reset}    -> Pide ajustes de cronograma/temario`);
          }
          if (habilitadas.includes('ENVIAR_NOMINA')) {
            console.log(`  ${colors.cyan}[4] Enviar nómina de inscriptos${colors.reset} -> Envía participantes y habilita lista definitiva`);
          }
          if (habilitadas.includes('CANCELAR_PREPARACION')) {
            console.log(`  ${colors.magenta}[5] Cancelar preparación${colors.reset}        -> Interrumpe y ejecuta compensaciones`);
          }
        }

        console.log(`${colors.magenta}${colors.bold}--------------------------------------------------------------------${colors.reset}`);
        console.log(`  ${colors.bold}[T] Ver tareas activas de la institución${colors.reset}`);
        console.log(`  ${colors.bold}[A] Avanzar temporizador activo (Simular paso de 5 días - Escenario 4)${colors.reset}`);
        console.log(`  ${colors.bold}[F] Alternar simulación de falta de aulas (Escenario 7)${colors.reset}`);
        console.log(`  ${colors.bold}[V] Volver / Cambiar de curso${colors.reset}`);
        console.log(`  [0] Salir`);
        console.log(`${colors.magenta}${colors.bold}--------------------------------------------------------------------${colors.reset}`);

        const opcion = (await rl.question(`${colors.bold}Seleccione una opción: ${colors.reset}`)).trim().toUpperCase();

        if (opcion === '0') {
          console.log(`\n${colors.cyan}Saliendo del simulador. ¡Hasta luego!${colors.reset}`);
          rl.close();
          return;
        }

        if (opcion === 'V') {
          enMismaInstancia = false;
          break;
        }

        if (opcion === 'T') {
          await consultarTareasInstancia(processInstanceId);
          await rl.question(`\n${colors.bold}Presione ENTER para continuar...${colors.reset}`);
          continue;
        }

        if (opcion === 'A') {
          await avanzarTemporizador(processInstanceId);
          await rl.question(`\n${colors.bold}Presione ENTER para continuar...${colors.reset}`);
          continue;
        }

        if (opcion === 'F') {
          await alternarFaltaAulas();
          await rl.question(`\n${colors.bold}Presione ENTER para continuar...${colors.reset}`);
          continue;
        }

        // Ejecutar comunicación seleccionada
        await procesarComunicacion(opcion, habilitadas, processInstanceId);
        await new Promise(r => setTimeout(r, 600)); // Pausa breve para propagación BPMN
        await rl.question(`\n${colors.bold}Presione ENTER para continuar...${colors.reset}`);
      }
    }
  } catch (error) {
    console.error(`\n${colors.red}[ERROR CRÍTICO]: ${error.message}${colors.reset}`);
  } finally {
    rl.close();
  }
}

/**
 * Consulta y lista instancias activas o permite crear una nueva de prueba
 */
async function seleccionarOIniciarInstancia() {
  console.log(`\n${colors.bold}Consultando instancias de proceso activas en el servidor...${colors.reset}`);

  let instancias = [];
  try {
    const res = await fetch(`${DEFAULT_BASE_URL}/instancias/activas`);
    if (res.ok) {
      const json = await res.json();
      instancias = json.datos || [];
    }
  } catch (e) {
    console.log(`${colors.red}[!] No se pudo conectar con el backend en ${DEFAULT_BASE_URL}.${colors.reset}`);
    console.log(`${colors.yellow}Asegúrese de haber iniciado la aplicación con 'mvn spring-boot:run'.${colors.reset}\n`);
    return null;
  }

  if (instancias.length > 0) {
    console.log(`\n${colors.green}${colors.bold}Instancias activas disponibles:${colors.reset}`);
    instancias.forEach((inst, index) => {
      console.log(`  [${index + 1}] ID: ${colors.cyan}${inst.processInstanceId}${colors.reset} | Curso: ${inst.businessKey || 'S/D'}`);
    });
  } else {
    console.log(`\n${colors.yellow}No hay instancias activas actualmente.${colors.reset}`);
  }

  console.log(`\n  [N] Iniciar un nuevo curso de prueba automáticamente`);
  console.log(`  [M] Ingresar ID de instancia manualmente`);
  console.log(`  [0] Salir`);

  const resp = (await rl.question(`\n${colors.bold}Seleccione una opción: ${colors.reset}`)).trim();

  if (resp === '0') return null;

  if (resp.toUpperCase() === 'N') {
    return await crearNuevaInstanciaAutomatica();
  }

  if (resp.toUpperCase() === 'M') {
    return (await rl.question(`${colors.bold}Ingrese el ID de la instancia de proceso: ${colors.reset}`)).trim();
  }

  const num = parseInt(resp, 10);
  if (!isNaN(num) && num >= 1 && num <= instancias.length) {
    return instancias[num - 1].processInstanceId;
  }

  console.log(`${colors.red}Opción inválida.${colors.reset}`);
  return null;
}

/**
 * Consulta comunicaciones habilitadas para la instancia
 */
async function consultarComunicacionesHabilitadas(processInstanceId) {
  try {
    const res = await fetch(`${DEFAULT_BASE_URL}/instancias/${processInstanceId}/comunicaciones-habilitadas`);
    if (res.ok) {
      const json = await res.json();
      return json.datos || [];
    }
  } catch (e) {
    console.error(`Error al consultar comunicaciones: ${e.message}`);
  }
  return [];
}

/**
 * Muestra el estado del curso en la base institucional
 */
async function mostrarEstadoInstancia(processInstanceId) {
  try {
    const res = await fetch(`${DEFAULT_BASE_URL}/cursos/${encodeURIComponent(processInstanceId)}/estado`);
    if (res.ok) {
      const json = await res.json();
      const d = json.datos;
      console.log(`\n${colors.bold}CURSO SELECCIONADO:${colors.reset} ${colors.cyan}${d.nombreCurso || 'S/T'}${colors.reset} [${d.cursoId || '-'}]`);
      console.log(`${colors.bold}ESTADO GENERAL:${colors.reset}     ${colors.yellow}${d.estado ? d.estado.toUpperCase() : 'PENDIENTE'}${colors.reset}`);
      if (d.empresaEmail) {
        console.log(`${colors.bold}EMPRESA:${colors.reset}            ${d.empresaEmail}`);
      }
    }
  } catch (ignored) {}
}

/**
 * Consulta tareas activas
 */
async function consultarTareasInstancia(processInstanceId) {
  try {
    const res = await fetch(`${DEFAULT_BASE_URL}/instancias/${processInstanceId}/tareas`);
    if (res.ok) {
      const json = await res.json();
      const tareas = json.datos || [];
      console.log(`\n${colors.bold}Tareas activas en la institución para este curso:${colors.reset}`);
      if (tareas.length === 0) {
        console.log(`  (Sin tareas humanas activas en este instante)`);
      } else {
        tareas.forEach(t => {
          console.log(`  • ${colors.yellow}${t.taskName}${colors.reset} (Asignado: ${t.assignee || 'Grupo Administración'})`);
        });
      }
    }
  } catch (e) {
    console.error('Error al consultar tareas:', e.message);
  }
}

/**
 * Avanza cualquier temporizador activo (Timer Job) en Flowable
 */
async function avanzarTemporizador(processInstanceId) {
  try {
    const res = await fetch(`${DEFAULT_BASE_URL}/instancias/${processInstanceId}/avanzar-temporizador`, { method: 'POST' });
    const json = await res.json();
    if (json.datos) {
      console.log(`\n${colors.green}${colors.bold}[✓] Temporizador avanzado exitosamente en Flowable. Se simuló el vencimiento de los días.${colors.reset}`);
    } else {
      console.log(`\n${colors.yellow}[!] No hay ningún temporizador activo esperando en este momento para esta instancia.${colors.reset}`);
    }
  } catch (e) {
    console.error('Error al avanzar temporizador:', e.message);
  }
}

/**
 * Alterna el toggle de falta de aulas (Escenario 7)
 */
async function alternarFaltaAulas() {
  try {
    const res = await fetch(`${DEFAULT_BASE_URL}/simulacion/toggle-falta-aula`, { method: 'POST' });
    const json = await res.json();
    if (json.datos) {
      console.log(`\n${colors.red}${colors.bold}[⚡] Falta de aulas forzada ACTIVADA (Escenario 7). La próxima reserva presencial fallará.${colors.reset}`);
    } else {
      console.log(`\n${colors.green}${colors.bold}[✓] Disponibilidad de aulas restablecida a NORMAL.${colors.reset}`);
    }
  } catch (e) {
    console.error('Error al alternar falta de aulas:', e.message);
  }
}

/**
 * Ejecuta la comunicación de negocio elegida
 */
async function procesarComunicacion(opcion, habilitadas, processInstanceId) {
  switch (opcion) {
    case '1': {
      if (!habilitadas.includes('ACEPTAR_PROPUESTA')) {
        console.log(`${colors.red}[✕] La acción ACEPTAR PROPUESTA no está habilitada en este estado.${colors.reset}`);
        return;
      }
      const fechaDef = new Date(Date.now() + 15 * 86400000).toISOString().split('T')[0] + 'T09:00:00';
      const fInput = (await rl.question(`Ingrese fecha acordada de inicio [${fechaDef}]: `)).trim();
      const fechaInicioCurso = fInput || fechaDef;

      console.log(`\nEnviando aceptación de propuesta con fecha ${fechaInicioCurso}...`);
      await postJson(`${DEFAULT_BASE_URL}/instancias/${processInstanceId}/comunicacion/aceptar`, { fechaInicioCurso });
      console.log(`${colors.green}${colors.bold}[✓] ¡Propuesta aceptada! El curso ingresó en la fase de Preparación en 3 ramas paralelas.${colors.reset}`);
      break;
    }

    case '2': {
      if (!habilitadas.includes('RECHAZAR_PROPUESTA')) {
        console.log(`${colors.red}[✕] La acción RECHAZAR PROPUESTA no está habilitada en este estado.${colors.reset}`);
        return;
      }
      const mInput = (await rl.question('Ingrese motivo de rechazo [Presupuesto no aprobado]: ')).trim();
      const motivo = mInput || 'Presupuesto no aprobado para este período';

      await postJson(`${DEFAULT_BASE_URL}/instancias/${processInstanceId}/comunicacion/rechazar`, { motivo });
      console.log(`${colors.red}${colors.bold}[✓] Notificación de rechazo enviada. La instancia se dirigirá al archivo.${colors.reset}`);
      break;
    }

    case '3': {
      if (!habilitadas.includes('SOLICITAR_MODIFICACIONES')) {
        console.log(`${colors.red}[✕] La acción SOLICITAR MODIFICACIONES no está habilitada en este estado.${colors.reset}`);
        return;
      }
      const oInput = (await rl.question('Ingrese modificaciones solicitadas [Ajustar horario a turno tarde]: ')).trim();
      const observaciones = oInput || 'Ajustar horario a turno tarde y modalidad mixta';

      await postJson(`${DEFAULT_BASE_URL}/instancias/${processInstanceId}/comunicacion/modificaciones`, { observaciones });
      console.log(`${colors.yellow}${colors.bold}[✓] Modificaciones enviadas. Se habilitó la tarea de Revisar Propuesta para el Jefe Administrativo.${colors.reset}`);
      break;
    }

    case '4': {
      if (!habilitadas.includes('ENVIAR_NOMINA')) {
        console.log(`${colors.red}[✕] La acción ENVIAR NÓMINA no está habilitada en este estado.${colors.reset}`);
        return;
      }
      console.log('\nEnviando nómina de participantes corporativos...');
      const nomina = [
        'Ing. Carlos Menéndez (Desarrollo)',
        'Lic. Valeria Ruiz (Arquitectura)',
        'Tec. Fernando Silva (Infraestructura)',
        'Dra. Patricia Blanco (Calidad)'
      ];
      await postJson(`${DEFAULT_BASE_URL}/instancias/${processInstanceId}/comunicacion/nomina`, { nomina });
      console.log(`${colors.cyan}${colors.bold}[✓] Nómina enviada exitosamente. Se activó la confección de lista definitiva con balanceo de carga.${colors.reset}`);
      break;
    }

    case '5': {
      if (!habilitadas.includes('CANCELAR_PREPARACION')) {
        console.log(`${colors.red}[✕] La acción CANCELAR PREPARACIÓN no está habilitada en este estado.${colors.reset}`);
        return;
      }
      const cInput = (await rl.question('Ingrese motivo de la cancelación [Reestructuración del cronograma]: ')).trim();
      const motivo = cInput || 'Reestructuración interna de prioridades de la empresa';

      await postJson(`${DEFAULT_BASE_URL}/instancias/${processInstanceId}/comunicacion/cancelar`, { motivo });
      console.log(`${colors.magenta}${colors.bold}[✓] Cancelación enviada. Se interrumpen las 3 ramas y se ejecutan las compensaciones para liberar recursos gestionados.${colors.reset}`);
      break;
    }

    default:
      console.log(`${colors.red}Opción no reconocida.${colors.reset}`);
  }
}

async function postJson(url, payload) {
  const res = await fetch(url, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(payload)
  });
  if (!res.ok) {
    const err = await res.json().catch(() => ({}));
    throw new Error(err.mensaje || `Error HTTP ${res.status}`);
  }
  return await res.json().catch(() => ({}));
}

/**
 * Crea una nueva instancia con datos aleatorios
 */
async function crearNuevaInstanciaAutomatica() {
  const randomId = 'CURSO-' + Math.floor(1000 + Math.random() * 9000);
  console.log(`\n${colors.cyan}Iniciando nueva solicitud automática para [${randomId}]...${colors.reset}`);

  try {
    const json = await postJson(`${DEFAULT_BASE_URL}/iniciar`, {
      cursoId: randomId,
      nombreCurso: `Capacitación de Procesos BPMN y Microservicios (${randomId})`,
      categoria: 'Tecnologías y Sistemas',
      emailSolicitante: 'capacitacion@empresa-demo.com',
      cupoMaximo: 25,
      descripcion: 'Curso empresarial intensivo creado desde el simulador de comunicaciones.'
    });

    console.log(`${colors.green}${colors.bold}[✓] Proceso iniciado con éxito.${colors.reset}`);
    return json.datos?.processInstanceId;
  } catch (e) {
    console.error(`Error al iniciar curso: ${e.message}`);
    return null;
  }
}

main();
