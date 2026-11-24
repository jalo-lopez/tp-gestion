# 🎓 Guía Oficial para la Defensa - Gestión de Cursos BPM

**Grupo:** López, De Vigili, Viglione  
**Materia:** Gestión y Tecnologías de Procesos de Negocio  
**Stack:** Spring Boot 3.3.5, Flowable BPM 7.1.0, Flowable DMN, Flowable IDM, PostgreSQL / H2, Java Swing, REST API.

---

## ⚡ Preparación Previa en 1 Minuto

Antes de que comience la reunión con los evaluadores:

1. **Iniciar el servidor backend:**
   ```bash
   mvn spring-boot:run
   ```
   *(Verificar que responda en `http://localhost:8080/index.html`)*.

2. **Precargar todos los escenarios de demostración:**
   - **Opción A (Desde el navegador):** Ingresar a [http://localhost:8080/panel-admin.html](http://localhost:8080/panel-admin.html) y hacer clic en el botón verde superior: **`🎯 Cargar Escenarios Defensa`**.
   - **Opción B (Desde la terminal):**
     ```bash
     ./scripts/preparar-escenarios-defensa.sh
     ```
   *Esto genera al instante 5 instancias en ejecución activa (dejando la regla DMN armada), instancias con recursos reservados para compensación, y estados para todas las variantes.*

3. **Tener abiertas las siguientes pestañas del navegador:**
   - **Pestaña 1:** Portal de Solicitud de Cursos → [http://localhost:8080/index.html](http://localhost:8080/index.html)
   - **Pestaña 2:** Bandeja Administrativa → [http://localhost:8080/panel-admin.html](http://localhost:8080/panel-admin.html)
   - **Pestaña 3:** Portal de Seguimiento Empresa → [http://localhost:8080/panel-empresa.html](http://localhost:8080/panel-empresa.html)
   - **Pestaña 4:** Portal Docente → [http://localhost:8080/panel-docente.html](http://localhost:8080/panel-docente.html)

---

## 🧭 Demostración Paso a Paso de los 11 Escenarios

---

### Escenario 1: Flujo Completo con Aceptación, Preparación Paralela y Cierre
* **Objetivo:** Demostrar el camino feliz (Happy Path) de punta a punta.
* **Paso a paso:**
  1. En `panel-admin.html`, seleccionar operadora **Laura Gómez**.
  2. Completar la tarea `Registrar y Elaborar propuesta` con presupuesto, fechas y programa.
  3. En `panel-empresa.html`, iniciar sesión con `contacto@techsolutions.com` / `empresa123`.
  4. Simular la aceptación mediante API REST o simulador indicando la fecha de inicio acordada (`2027-03-01T09:00:00`).
  5. Se dispara el subproceso paralelo de preparación con sus 3 ramas:
     - **Rama 1 (Inscripción):** Enviar nómina → se activa la tarea `Confeccionar lista definitiva`.
     - **Rama 2 (Materiales):** Tarea `Preparar materiales` (grupo Administración).
     - **Rama 3 (Recursos):** Tarea `Evaluar Recursos` (seleccionar Aula Física, Virtual o Equipamiento).
  6. Al concluir las 3 ramas, se sincroniza en el Parallel Gateway y pasa a fase docente:
     - Carlos Rodríguez define al docente dictante (`docente_ana`).
     - Temporizador de fecha programada.
     - Docente desarrolla el curso y elabora informe de resultados.
     - Laura envía informe a la empresa y archiva expediente.
  7. **Qué destacar al profesor:** El Execution Listener finaliza el proceso y actualiza el estado en la base de datos externa a `finalizado`.

---

### Escenario 2: Solicitud de Modificaciones y Envío de Nueva Propuesta
* **Objetivo:** Demostrar la renegociación asíncrona mediante el Event Gateway.
* **Instancia preparada:** `CURSO-ESC-2-MODIF` (propuesta enviada esperando en el Event-Based Gateway).
* **Paso a paso:**
  1. Enviar la comunicación de modificaciones con una petición REST o desde el simulador:
     ```bash
     curl -X POST http://localhost:8080/api/v1/cursos/procesos/instancias/{PID}/comunicacion/modificaciones \
          -H "Content-Type: application/json" \
          -d '{"observaciones": "Solicitamos ajustar el temario para incluir módulos de Cloud y reducir a 20 cupos."}'
     ```
  2. En `panel-admin.html` con operadora **Laura Gómez**, refrescar la lista:
     - Aparece la tarea **`Revisar propuesta`**.
     - Al abrir el diálogo, se visualizan las observaciones enviadas por la empresa.
     - Laura corrige el presupuesto/fechas y reenvía la propuesta corregida.
  3. El proceso vuelve al Event Gateway a la espera de la nueva respuesta.

---

### Escenario 3: Rechazo de la Propuesta
* **Objetivo:** Demostrar la salida por rechazo de la propuesta.
* **Instancia preparada:** `CURSO-ESC-3-RECHAZO`.
* **Paso a paso:**
  1. Simular rechazo de la empresa mediante API REST:
     ```bash
     curl -X POST http://localhost:8080/api/v1/cursos/procesos/instancias/{PID}/comunicacion/rechazar \
          -H "Content-Type: application/json" \
          -d '{"motivo": "Presupuesto no aprobado por la dirección ejecutiva."}'
     ```
  2. En `panel-admin.html` con **Laura Gómez**, se habilita la tarea **`Archivar solicitud`**.
  3. Laura completa la tarea y el `ActualizarEstadoCursoExecutionListener` registra en la BD externa el estado `rechazado`.

---

### Escenario 4: Cierre por Falta de Respuesta tras Recordatorios
* **Objetivo:** Demostrar el temporizador que dispara hasta 2 recordatorios antes de archivar por timeout.
* **Instancia preparada:** `CURSO-ESC-4-TIMEOUT`.
* **Paso a paso:**
  1. Avanzar manualmente el temporizador del motor mediante el endpoint:
     ```bash
     curl -X POST http://localhost:8080/api/v1/cursos/procesos/instancias/{PID}/avanzar-temporizador
     ```
  2. **Primer avance:** El motor incrementa `recordatoriosEnviados = 1`, ejecuta el Service Task `Enviar recordatorio` y vuelve al temporizador.
  3. **Segundo avance:** `recordatoriosEnviados = 2`, envía el segundo recordatorio.
  4. **Tercer avance:** La compuerta detecta que se superó el límite de 2 recordatorios, ejecuta `Archivar solicitud sin respuesta` y finaliza con estado `cerrado por falta de respuesta`.

---

### Escenario 5: Regla de Negocio DMN (Límite de 5 Cursos en Ejecución)
* **Objetivo:** Demostrar la Decision Table de viabilidad implementada en DMN según Sección 3.1.
* **Condición precargada:** El inicializador de defensa ya dejó **5 instancias activas** en el motor.
* **Paso a paso:**
  1. Abrir `index.html` y completar una solicitud de curso cualquiera (ej: "Curso Demo DMN", empresa Globant).
  2. Presionar **Enviar Solicitud**.
  3. **Explicación técnica para el profesor:**
     - El Execution Listener `PrepararViabilidadExecutionListener` consulta al motor:
       `cantInstanciasActivas = runtimeService.createProcessInstanceQuery().active().count() - 1`.
     - Detecta que ya hay 5 instancias activas en ejecución.
     - La tabla DMN `src/main/resources/dmn/viabilidad-reglas.dmn` evalúa la regla:
       `cantInstanciasActivas >= 5` $\rightarrow$ `esViable = false`, `resultado = "no viable"`.
     - El proceso sigue por el camino no viable, ejecuta `Notificar curso no viable a la empresa` y termina en `endEventNoViable` con estado `no viable` en la BD institucional.

---

### Escenario 6: Cancelación durante la Preparación, Compensación y Pausa User Task
* **Objetivo:** Demostrar la interrupción del subproceso, las compensaciones de recursos gestionados, la tarea User de pausa requerida por la cátedra y la salida por compuerta XOR.
* **Instancia preparada:** `CURSO-ESC-6-CANCEL` (tiene `aulaFisicaReservada = true` y `aulaVirtualReservada = true`).
* **Paso a paso:**
  1. En `panel-empresa.html`, mostrar que el curso está en preparación y tiene los recursos gestionados.
  2. Enviar la cancelación de preparación desde el simulador o REST:
     ```bash
     curl -X POST http://localhost:8080/api/v1/cursos/procesos/instancias/{PID}/comunicacion/cancelar \
          -H "Content-Type: application/json" \
          -d '{"motivo": "Cancelación institucional imprevista por la empresa cliente."}'
     ```
  3. **Lo que sucede en Flowable (¡Punto clave solicitado por el profesor!):**
     - El Event Subprocess con `isInterrupting="true"` interrumpe las ramas de preparación.
     - El `<intermediateThrowEvent>` de compensación dispara la liberación de los recursos gestionados:
       - `liberarAulaTask`: Libera el aula física.
       - `liberarAulaVirtualTask`: Libera el aula virtual.
     - **Punto de pausa:** El proceso se detiene en la tarea de usuario:
       👉 **`Dejar una actividad User para que pare`** (asignada a Administración).
  4. En `panel-admin.html`, mostrar la tarea pendiente **"Dejar una actividad User para que pare"**.
  5. Completar la tarea desde el panel:
     - El Event Subprocess finaliza.
     - A la salida de `subProcessPrepararCurso`, el flujo llega a la **compuerta XOR** (`xorSalidaPreparacion`).
     - Al evaluar `${resultado == 'cancelado' || cancelado == true}`, se desvía por la salida de cancelación hacia `Notificar Cierre` y finaliza en `Curso Cancelado` con estado `cancelado` en la base de datos institucional.

---

### Escenario 7: Falta de Aulas Disponibles y Manejo de Excepción
* **Objetivo:** Demostrar el fallo de reserva y la robustez ante indisponibilidad física.
* **Paso a paso:**
  1. En `panel-admin.html`, presionar el botón **`Simular Falta de Aulas`** (activa el badge rojo de alerta).
  2. Tomar una tarea de `Evaluar Recursos` y seleccionar la opción **Aula Física**.
  3. Al enviar la evaluación, el Service Task `reservarAula` detecta la indisponibilidad y lanza la excepción controlada de negocio `ERROR_AULA_NO_DISPONIBLE`.
  4. Presionar nuevamente el botón para restablecer las aulas a estado normal.

---

### Escenario 8: Balanceo de Carga (Least-Loaded) para Administrativos
* **Objetivo:** Demostrar el TaskListener que asigna tareas al administrativo con menor carga de trabajo pendiente.
* **Paso a paso:**
  1. Mostrar en `panel-admin.html` que Laura y Martín tienen distinta cantidad de tareas pendientes asignadas.
  2. Enviar la nómina de participantes para una instancia en preparación:
     ```bash
     curl -X POST http://localhost:8080/api/v1/cursos/procesos/instancias/{PID}/comunicacion/nomina \
          -H "Content-Type: application/json" \
          -d '{"nomina": ["Juan Pérez", "Ana Silva", "Carlos Benítez"]}'
     ```
  3. **Explicación técnica:** El TaskListener `AsignacionBalanceoTaskListener` consulta en Flowable mediante `TaskQuery`:
     - Tareas asignadas a `admin_laura`.
     - Tareas asignadas a `admin_martin`.
     - Asigna automáticamente la nueva tarea **`Confeccionar lista definitiva`** al usuario que tenga menor cantidad.

---

### Escenario 9: Registro Web de Solicitud de Capacitación
* **Objetivo:** Demostrar la interfaz pública de postulación y la inicialización del proceso.
* **Paso a paso:**
  1. Abrir `http://localhost:8080/index.html`.
  2. Completar los campos del formulario con una nueva empresa.
  3. Presionar **Enviar Solicitud**.
  4. Se muestra el número de solicitud y Process Instance ID asignado en Flowable.
  5. En `panel-admin.html`, la jefa administrativa **Laura Gómez** recibe inmediatamente la tarea `Registrar y Elaborar propuesta`.

---

### Escenario 10: Portal Empresa con Autenticación Flowable IDM
* **Objetivo:** Demostrar la autenticación de empresas con Flowable Identity Service y el seguimiento visual de ramas paralelas.
* **Paso a paso:**
  1. Abrir `http://localhost:8080/panel-empresa.html`.
  2. Iniciar sesión con:
     - **Email:** `contacto@techsolutions.com`
     - **Contraseña:** `empresa123`
  3. Se listan únicamente los cursos pertenecientes a esa empresa.
  4. Al hacer clic en un curso en preparación, se despliega el visualizador con 3 tarjetas en tiempo real:
     - **Rama 1:** Estado de la nómina y lista definitiva.
     - **Rama 2:** Estado de la preparación de materiales didácticos.
     - **Rama 3:** Recursos asignados (aula física, virtual, equipamiento).

---

### Escenario 11: App Auxiliar Swing (Preparar Materiales) y Simulador REST
* **Objetivo:** Demostrar la aplicación de escritorio Swing que interactúa con la API Java de Flowable y las métricas históricas.
* **Paso a paso:**
  1. Abrir la aplicación Swing en la terminal:
     ```bash
     ./scripts/run-preparar-materiales.sh
     ```
  2. En la ventana Swing:
     - Seleccionar usuario `admin_martin`.
     - La tabla lista las tareas activas de **Preparar materiales**.
     - Seleccionar una fila y presionar **Reclamar tarea**.
     - Ingresar notas pedagógicas y presionar **Completar tarea**.
  3. Cambiar a la pestaña **Métricas Históricas (Sección 4.3)**:
     - **Métrica 1:** Distribución y porcentaje de resultados (`finalizado`, `cancelado`, `rechazado`, `no viable`, `sin respuesta`).
     - **Métrica 2:** Duración promedio de las 3 ramas paralelas (excluyendo canceladas).
     - **Métrica 3:** Tareas completadas por integrante del equipo administrativo.
  4. Ejecutar el simulador REST interactivo si se desea:
     ```bash
     ./scripts/run-simulador-empresa.sh
     ```

---

## 🎯 Resumen de Credenciales Rápidas para la Demo

| Rol | Usuario | Contraseña | Funciones |
| :--- | :--- | :--- | :--- |
| **Jefa Administrativa** | `admin_laura` | `password` | Elaborar propuestas, evaluar recursos, archivar expedientes, ver métricas. |
| **Administrativo** | `admin_martin` | `password` | Preparar materiales (Swing/Web), confeccionar listas definitivas. |
| **Jefe Docente** | `docente_carlos` | `password` | Asignar docentes, elaborar informe pedagógico final. |
| **Docente** | `docente_ana` | `password` | Desarrollar curso asignado. |
| **Empresa Cliente** | `contacto@techsolutions.com` | `empresa123` | Portal de seguimiento, ver ramas paralelas, descargar PDFs. |

---

¡Muchos éxitos en la defensa! Todo el sistema está probado, integrado y 100% conforme a los requerimientos de la cátedra.
