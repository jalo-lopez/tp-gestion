#!/usr/bin/env node

/**
 * ============================================================================
 * SIMULADOR DE ACCIONES DE LA EMPRESA (CLIENTE BPM INTERACTIVO)
 * ============================================================================
 * Permite seleccionar una instancia activa de Flowable o iniciar una nueva
 * automáticamente, y simular las 4 decisiones empresariales:
 * 1) Aceptar propuesta
 * 2) Rechazar
 * 3) Pedir modificaciones
 * 4) Cancelar
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
  console.log(`${colors.cyan}${colors.bold}   FLOWABLE BPM - SIMULADOR INTERACTIVO DE DECISIONES DE EMPRESA    ${colors.reset}`);
  console.log(`${colors.cyan}${colors.bold}====================================================================${colors.reset}`);
  console.log(`${colors.gray}Backend destino: ${DEFAULT_BASE_URL}${colors.reset}\n`);

  try {
    while (true) {
      // 1. Obtener o seleccionar el ID de instancia de proceso
      const processInstanceId = await seleccionarOIniciarInstancia();
      if (!processInstanceId) break;

      // 2. Buscar tareas activas para esta instancia
      const tarea = await obtenerTareaActiva(processInstanceId);
      if (!tarea) {
        console.log(`\n${colors.yellow}[!] La instancia [${processInstanceId}] no tiene tareas pendientes activas (puede haber concluido o estar en un evento final).${colors.reset}`);
        const reintentar = await rl.question(`${colors.bold}¿Desea seleccionar otra instancia? (s/n): ${colors.reset}`);
        if (reintentar.toLowerCase() !== 's') break;
        continue;
      }

      // 3. Menú interactivo de opciones empresariales
      let continuarConMismaInstancia = true;
      while (continuarConMismaInstancia) {
        mostrarMenu(processInstanceId, tarea);
        const opcion = (await rl.question(`${colors.bold}Seleccione una opción (1-4, 5 para cambiar de instancia, 0 para salir): ${colors.reset}`)).trim();

        if (opcion === '0') {
          console.log(`\n${colors.cyan}Saliendo del simulador. ¡Hasta luego!${colors.reset}`);
          rl.close();
          return;
        }

        if (opcion === '5') {
          continuarConMismaInstancia = false;
          break;
        }

        const payload = await generarPayloadPorOpcion(opcion);
        if (!payload) {
          console.log(`${colors.red}[✕] Opción inválida. Intente nuevamente.${colors.reset}\n`);
          continue;
        }

        // 4. Enviar payload al backend Spring Boot / Flowable
        console.log(`\n${colors.yellow}>>> Enviando decisión empresarial a Flowable...${colors.reset}`);
        console.log(`${colors.gray}Payload JSON:${colors.reset}`, JSON.stringify(payload, null, 2));

        const exito = await enviarDecision(tarea.taskId, payload);
        if (exito) {
          console.log(`\n${colors.green}${colors.bold}[✓] ¡Decisión registrada con éxito en Flowable BPM!${colors.reset}`);
          console.log(`${colors.gray}La tarea [${tarea.taskId}] fue completada y el flujo BPM continuó su ejecución.${colors.reset}\n`);
          continuarConMismaInstancia = false; // Tarea finalizada, volvemos a listar instancias
        }

        await rl.question(`${colors.bold}Presione ENTER para continuar...${colors.reset}`);
      }
    }
  } catch (error) {
    console.error(`\n${colors.red}[ERROR CRÍTICO]: ${error.message}${colors.reset}`);
  } finally {
    rl.close();
  }
}

/**
 * Consulta las instancias activas disponibles y permite al usuario elegir una,
 * ingresar un ID manualmente o crear una nueva automáticamente.
 */
async function seleccionarOIniciarInstancia() {
  console.log(`\n${colors.bold}Consultando instancias de proceso activas en el servidor...${colors.reset}`);

  let instanciasActivas = [];
  try {
    const res = await fetch(`${DEFAULT_BASE_URL}/instancias/activas`);
    if (res.ok) {
      const json = await res.json();
      instanciasActivas = json.datos || [];
    }
  } catch (e) {
    console.log(`${colors.red}[!] No se pudo conectar con el servidor en ${DEFAULT_BASE_URL}.${colors.reset}`);
    console.log(`${colors.yellow}Asegúrese de haber iniciado el backend con 'mvn spring-boot:run' en otra terminal.${colors.reset}\n`);
  }

  if (instanciasActivas.length > 0) {
    console.log(`\n${colors.green}${colors.bold}Instancias activas encontradas en el motor:${colors.reset}`);
    instanciasActivas.forEach((inst, index) => {
      console.log(`  ${colors.bold}[${index + 1}]${colors.reset} Código: ${colors.cyan}${inst.businessKey || 'S/C'}${colors.reset} | ID Flowable: ${colors.yellow}${inst.processInstanceId}${colors.reset}`);
    });
    console.log(`  ${colors.bold}[N]${colors.reset} Iniciar una ${colors.green}NUEVA${colors.reset} solicitud de curso automáticamente`);
    console.log(`  ${colors.gray}O pegue directamente un ID de instancia UUID${colors.reset}`);

    const seleccion = (await rl.question(`\n${colors.bold}Seleccione un número, 'N' para crear una nueva o pegue un ID: ${colors.reset}`)).trim();
    if (!seleccion || seleccion.toLowerCase() === 'salir') return null;

    if (seleccion.toUpperCase() === 'N') {
      return await crearNuevaInstanciaAutomatica();
    }

    const num = parseInt(seleccion, 10);
    if (!isNaN(num) && num >= 1 && num <= instanciasActivas.length) {
      return instanciasActivas[num - 1].processInstanceId;
    }

    return seleccion; // Si pegó un UUID directamente
  } else {
    console.log(`\n${colors.yellow}[!] Actualmente no hay instancias de procesos activas en el motor Flowable.${colors.reset}`);
    console.log(`${colors.bold}¿Desea iniciar una NUEVA solicitud de capacitación ahora mismo?${colors.reset}`);
    const respuesta = (await rl.question(`${colors.cyan}[S/n] (o pegue un ID): ${colors.reset}`)).trim();

    if (!respuesta || respuesta.toLowerCase() === 's') {
      return await crearNuevaInstanciaAutomatica();
    } else if (respuesta.toLowerCase() === 'n' || respuesta.toLowerCase() === 'salir') {
      return null;
    }

    return respuesta; // Si pegó un UUID directamente
  }
}

/**
 * Inicia una nueva solicitud de curso de prueba en Spring Boot / Flowable
 */
async function crearNuevaInstanciaAutomatica() {
  const randomCode = 'CURSO-CLI-' + Math.floor(1000 + Math.random() * 9000);
  console.log(`\n${colors.cyan}Iniciando nueva instancia de prueba con código [${randomCode}]...${colors.reset}`);

  try {
    const payload = {
      cursoId: randomCode,
      nombreCurso: 'Capacitación en Automatización y BPMN 2.0',
      categoria: 'Ingeniería y Procesos',
      emailSolicitante: 'empresa.demo@corporativo.com',
      cupoMaximo: 20,
      descripcion: 'Instancia generada automáticamente desde el simulador de consola.'
    };

    const res = await fetch(`${DEFAULT_BASE_URL}/iniciar`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(payload)
    });

    const json = await res.json();
    if (!res.ok) throw new Error(json.mensaje || 'Error al iniciar instancia');

    const nuevaInstanciaId = json.datos.processInstanceId;
    console.log(`${colors.green}[✓] ¡Instancia creada exitosamente!${colors.reset}`);
    console.log(`    Código (BusinessKey): ${colors.bold}${json.datos.businessKey}${colors.reset}`);
    console.log(`    ID de Instancia Flowable: ${colors.yellow}${nuevaInstanciaId}${colors.reset}`);

    return nuevaInstanciaId;
  } catch (err) {
    console.error(`${colors.red}[✕] Error al crear instancia: ${err.message}${colors.reset}`);
    return null;
  }
}

/**
 * Consulta la API REST para obtener las tareas activas de la instancia
 */
async function obtenerTareaActiva(processInstanceId) {
  try {
    const url = `${DEFAULT_BASE_URL}/instancias/${encodeURIComponent(processInstanceId)}/tareas`;
    const res = await fetch(url);
    if (!res.ok) return null;
    const data = await res.json();
    const tareas = data.datos || [];
    return tareas.length > 0 ? tareas[0] : null;
  } catch (err) {
    console.error(`${colors.red}Error conectando al backend: ${err.message}${colors.reset}`);
    return null;
  }
}

/**
 * Imprime el menú de opciones con formato visual
 */
function mostrarMenu(processInstanceId, tarea) {
  console.log(`\n${colors.magenta}${colors.bold}--------------------------------------------------------------------${colors.reset}`);
  console.log(`${colors.bold}PROCESO FLOWABLE:${colors.reset} ${colors.cyan}${processInstanceId}${colors.reset}`);
  console.log(`${colors.bold}TAREA ACTIVA:${colors.reset}     ${colors.yellow}${tarea.taskName} (ID: ${tarea.taskId})${colors.reset}`);
  if (tarea.variables?.nombreCurso) {
    console.log(`${colors.bold}CURSO:${colors.reset}            ${tarea.variables.nombreCurso}`);
  }
  console.log(`${colors.magenta}${colors.bold}--------------------------------------------------------------------${colors.reset}`);
  console.log(`${colors.bold}ACCIONES DISPONIBLES DE LA EMPRESA:${colors.reset}`);
  console.log(`  ${colors.green}[1] Aceptar propuesta${colors.reset}       -> Valida la propuesta formativa y aprueba el curso`);
  console.log(`  ${colors.red}[2] Rechazar propuesta${colors.reset}      -> Desestima la capacitación y finaliza como Rechazado`);
  console.log(`  ${colors.yellow}[3] Pedir modificaciones${colors.reset}    -> Solicita ajustes en fechas, temario o presupuesto`);
  console.log(`  ${colors.magenta}[4] Cancelar solicitud${colors.reset}      -> Cancela voluntariamente el proceso`);
  console.log(`  ${colors.cyan}[5] Cambiar de instancia${colors.reset}`);
  console.log(`  [0] Salir`);
  console.log(`${colors.magenta}${colors.bold}--------------------------------------------------------------------${colors.reset}`);
}

/**
 * Construye el payload JSON de acuerdo a la acción empresarial seleccionada
 */
async function generarPayloadPorOpcion(opcion) {
  const timestamp = new Date().toISOString();

  switch (opcion) {
    case '1': { // Aceptar propuesta
      console.log(`\n${colors.green}${colors.bold}>>> Opción 1: ACEPTAR PROPUESTA${colors.reset}`);
      return {
        aprobado: true,
        observaciones: 'Propuesta académica y condiciones aceptadas formalmente por la empresa.',
        variablesAdicionales: {
          decisionEmpresa: 'ACEPTADA',
          estado: 'finalizado',
          confirmacionPresupuesto: true,
          fechaRespuestaEmpresa: timestamp
        }
      };
    }

    case '2': { // Rechazar
      console.log(`\n${colors.red}${colors.bold}>>> Opción 2: RECHAZAR PROPUESTA${colors.reset}`);
      const motivo = (await rl.question(`${colors.bold}Ingrese el motivo del rechazo: ${colors.reset}`)).trim() 
        || 'Presupuesto no aprobado por gerencia.';
      return {
        aprobado: false,
        observaciones: `Propuesta rechazada por la empresa: ${motivo}`,
        variablesAdicionales: {
          decisionEmpresa: 'RECHAZADA',
          estado: 'rechazado',
          motivoRechazo: motivo,
          fechaRespuestaEmpresa: timestamp
        }
      };
    }

    case '3': { // Pedir modificaciones
      console.log(`\n${colors.yellow}${colors.bold}>>> Opción 3: SOLICITAR MODIFICACIONES${colors.reset}`);
      const cambios = (await rl.question(`${colors.bold}Detalle los cambios o ajustes requeridos: ${colors.reset}`)).trim() 
        || 'Ajuste en los horarios y reducción del cupo inicial.';
      return {
        aprobado: false,
        observaciones: `Modificaciones requeridas por la empresa: ${cambios}`,
        variablesAdicionales: {
          decisionEmpresa: 'MODIFICACIONES_SOLICITADAS',
          estado: 'en_preparacion',
          requiereRevision: true,
          detalleModificaciones: cambios,
          fechaRespuestaEmpresa: timestamp
        }
      };
    }

    case '4': { // Cancelar
      console.log(`\n${colors.magenta}${colors.bold}>>> Opción 4: CANCELAR SOLICITUD${colors.reset}`);
      const motivoCancel = (await rl.question(`${colors.bold}Ingrese motivo de cancelación: ${colors.reset}`)).trim() 
        || 'Cancelado por reestructuración interna de la empresa.';
      return {
        aprobado: false,
        observaciones: `Proceso cancelado por la empresa: ${motivoCancel}`,
        variablesAdicionales: {
          decisionEmpresa: 'CANCELADA',
          estado: 'cancelado',
          canceladoPorEmpresa: true,
          motivoCancelacion: motivoCancel,
          fechaRespuestaEmpresa: timestamp
        }
      };
    }

    default:
      return null;
  }
}

/**
 * Realiza el POST HTTP a la API REST de Spring Boot para completar la tarea
 */
async function enviarDecision(taskId, payload) {
  try {
    const url = `${DEFAULT_BASE_URL}/tareas/${encodeURIComponent(taskId)}/completar`;
    const res = await fetch(url, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'Accept': 'application/json'
      },
      body: JSON.stringify(payload)
    });

    const data = await res.json();
    if (!res.ok) {
      console.error(`\n${colors.red}[✕] Error del servidor: ${data.mensaje || res.statusText}${colors.reset}`);
      return false;
    }

    return true;
  } catch (error) {
    console.error(`\n${colors.red}[✕] Error de conexión: ${error.message}${colors.reset}`);
    return false;
  }
}

// Iniciar aplicación
main();
