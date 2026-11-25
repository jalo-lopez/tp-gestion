# Descripción del Proyecto: Gestión y Aprobación de Cursos BPM

## 1. Descripción General

El proyecto es un sistema de gestión y automatización del ciclo de vida de cursos de capacitación a medida para empresas clientes. El objetivo principal es coordinar de manera controlada y auditable las actividades administrativas, académicas y de comunicación entre la institución educativa y las empresas solicitantes.

El flujo general del proceso comprende:

1. Recepción de la solicitud de capacitación enviada por la empresa.
2. Evaluación automática de viabilidad del curso mediante reglas de negocio.
3. Elaboración y envío de la propuesta comercial/académica.
4. Espera asíncrona de la respuesta de la empresa (aceptación, pedido de modificaciones, rechazo o falta de respuesta tras recordatorios).
5. Preparación concurrente del curso aceptado (confección de nómina de alumnos, preparación de materiales didácticos y reserva de recursos físicos o virtuales).
6. Asignación del docente, dictado del curso en la fecha acordada, elaboración del informe de resultados y cierre del expediente.

### Stack

- Lenguaje: Java y javascrpipt
- Framework: Spring Boot 3.3.5
- Motor de Procesos: Flowable BPM 7.1.0 (motores BPMN 2.0, DMN e IDM)
- Base de Datos: PostgreSQL / Supabase y H2
- Interfaces: API REST, HTML5 / JavaScript, aplicaciones de consola y escritorio (Java Swing).

## 2. Variables del Proceso

Las variables de proceso representan los datos que viajan a través de la instancia en Flowable y determinan los desvíos del flujo y las decisiones operativas:

### Identificación y Solicitud

- `cursoId`: Identificador único de negocio de la solicitud de curso.
- `nombreCurso`: Nombre o título de la capacitación solicitada.
- `categoria`: Área temática o disciplina del curso.
- `emailSolicitante`: Correo electrónico del contacto en la empresa cliente.
- `cupoMaximo`: Cantidad máxima de estudiantes admitidos.
- `descripcion`: Alcance y detalles de la solicitud inicial.

### Viabilidad y Reglas DMN

- `cantInstanciasActivas`: Número de otras instancias de cursos activas en el motor al momento de evaluar viabilidad (calculada automáticamente).
- `esViable`: Resultado booleano de la tabla de decisión DMN (indica si la institución tiene capacidad operativa para recibir el curso).
- `resultado`: Texto con el estado de salida de etapas clave (`viable`, `no viable`, `cancelado`, `rechazado`, `finalizado`).

### Negociación y Propuesta

- `timerDurationPropuesta`: Duración de espera entre recordatorios a la empresa (configurable, por defecto 5 días: `P5D`).
- `recordatoriosEnviados`: Contador de avisos automáticos enviados ante falta de respuesta (límite: 2).
- `observaciones`: Motivos de cambios, notas de rechazo o comentarios administrativos.
- `aprobado`: Indicador booleano de aprobación o aceptación.

### Preparación y Gestión de Recursos

- `requiereAulaFisica`: Indica si el curso necesita reserva de aula física.
- `requiereAulaVirtual`: Indica si el curso se dictará mediante plataforma virtual.
- `requiereEquipamiento`: Indica si se debe contratar equipamiento especial de laboratorio.
- `aulaFisicaReservada`: Flag de control que confirma la reserva efectiva del aula presencial.
- `aulaVirtualReservada`: Flag de control de alta de sala o entorno virtual.
- `equipamientoReservado`: Flag de contratación del equipamiento.
- `nominaEstudiantes`: Lista o detalle de participantes inscriptos provista por la empresa.
- `cancelado`: Flag que indica si la empresa canceló el curso durante la etapa de preparación.

### Ejecución Docente y Cierre

- `fechaInicioCurso`: Fecha y hora acordada para el inicio del dictado.
- `docenteAsignado`: Identificador de usuario del docente designado para el dictado.
- `informeDocente`: Resumen evaluativo y métricas académicas confeccionadas por el docente.

## 3. Listeners (Escuchadores del Motor)

Los listeners permiten inyectar lógica Java en momentos exactos del ciclo de vida del proceso o de las tareas:

### PrepararViabilidadExecutionListener

- **Tipo:** `ExecutionListener` (evento: `start`).
- **Ubicación:** Inmediatamente antes de ejecutar la tarea DMN *Evaluar Viabilidad*.
- **Función:** Consulta en el motor Flowable (`RuntimeService`) cuántas instancias del proceso se encuentran activas en cualquier versión desplegada, excluyendo la instancia actual. Inyecta este valor en la variable `cantInstanciasActivas` para que la tabla DMN evalúe la capacidad sin intervención manual. También inicializa contadores y banderas por defecto.

### AsignacionBalanceoTaskListener

- **Tipo:** `TaskListener` (evento: `create`).
- **Ubicación:** User Task *Confeccionar lista definitiva*.
- **Función:** Implementa balanceo de carga entre los integrantes del grupo `Administración`. Consulta en el servicio de tareas (`TaskService`) cuántas tareas activas pendientes tiene asignadas cada administrativo (`admin_laura`, `admin_martin`) y asigna automáticamente la nueva tarea a la persona con menor carga de trabajo (*least loaded*).

### ActualizarEstadoCursoExecutionListener

- **Tipo:** `ExecutionListener` (evento: `end`).
- **Ubicación:** Eventos de finalización del proceso (*End Events*).
- **Función:** Se dispara cuando la instancia llega a su término (ya sea por finalización exitosa, rechazo, inviabilidad, cancelación o falta de respuesta). Lee las variables finales de la instancia y sincroniza el estado definitivo en la base de datos externa de la institución a través de `CursoExternalService`.

## 4. Decisiones de Implementación

### Motor BPMN Embebido en Spring Boot

Se optó por integrar Flowable dentro del backend Spring Boot. Esto simplifica el despliegue en un único artefacto ejecutable, garantiza transaccionalidad entre el motor de procesos y la aplicación, y permite inyectar servicios Spring directamente en las expresiones del proceso BPMN.

### Reglas de Negocio Desacopladas con DMN

La condición de viabilidad (máximo de 5 cursos en simultáneo) no está programada de forma rígida en código Java ni en compuertas condicionales del BPMN. Se implementó una tabla de decisión DMN estándar (`decision-viabilidad.dmn`), permitiendo modificar los umbrales o agregar condiciones futuras sin alterar el flujo del proceso.

### Compuerta Basada en Eventos para Negociación Asíncrona

La interacción con la empresa luego de enviar la propuesta utiliza un *Event-Based Gateway*. Esta estructura permite esperar distintos eventos externos que compiten entre sí (mensajes de aceptación, rechazo, observaciones de cambio o vencimiento de temporizador) de forma no bloqueante y respetando el estándar BPMN.

### Subproceso Paralelo con Mecanismos de Compensación

La fase de preparación del curso se estructuró como un subproceso embebido con tres ramas simultáneas (Inscripción, Materiales y Recursos):

- Para los recursos (aulas físicas, virtuales o equipamiento) se utilizaron compuertas inclusivas asociadas a eventos intermedios de compensación.
- Si el curso es cancelado por la empresa durante la preparación, un *Event Subprocess* interrumpible captura el evento de cancelación, dispara las compensaciones automáticas para liberar los recursos ya reservados, detiene el flujo transitoriamente en una tarea de usuario para verificación administrativa, y desvía la finalización mediante una compuerta exclusiva (XOR).

### Sincronización con Base de Datos Institucional

Se mantuvo la separación conceptual entre las tablas internas del motor Flowable (que gestionan el estado de ejecución, historial y variables de BPMN) y la base de datos de dominio institucional (que almacena los cursos de cara a los usuarios y portales externos). Los listeners actúan como puente entre ambos, evitando acoplamientos innecesarios.
