# Gestión de Cursos BPM - Spring Boot & Flowable

Proyecto base desarrollado con **Spring Boot 3.3.5**, **Flowable BPM 7.1.0** y **PostgreSQL / Supabase** para la materia *"Gestión y Tecnologías de Procesos de Negocio"*.

---

## 📁 Estructura del Proyecto

```text
gestion/
├── pom.xml                                      # Configuración Maven con Spring Boot, Flowable y PostgreSQL
├── README.md
└── src/
    ├── main/
    │   ├── java/com/gestion/cursos/
    │   │   ├── GestionCursosApplication.java    # Clase principal Spring Boot
    │   │   ├── controller/
    │   │   │   └── CursoProcesoController.java  # Endpoints REST para ciclo de vida, simulaciones y métricas
    │   │   ├── cli/
    │   │   │   ├── AppPrepararMateriales.java   # App Auxiliar 1 (Sección 4.2.1): GUI Swing & CLI API Java pura
    │   │   │   ├── AppPrepararMaterialesGUI.java# Ventana gráfica Swing para administrativos
    │   │   │   └── AppSimuladorEmpresa.java     # Simulador de comunicaciones REST
    │   │   ├── listener/
    │   │   │   ├── AsignacionBalanceoTaskListener.java       # TaskListener para balanceo de carga
    │   │   │   ├── ActualizarEstadoCursoExecutionListener.java # ExecutionListener para sincronización BD externa
    │   │   │   └── PrepararViabilidadExecutionListener.java   # ExecutionListener con DMN
    │   │   └── service/
    │   │       ├── CursoExternalService.java    # Comunicación con BD institucional
    │   │       ├── CursoProcessService.java     # Lógica orquestadora con Flowable APIs
    │   │       └── PdfGeneratorService.java     # Generación de PDFs oficiales (Propuesta e Informe)
    │   └── resources/
    │       ├── application.yml                  # Configuración de base de datos y Flowable
    │       ├── static/                          # Portales web (Empresas, Admin, Docentes)
    │       ├── dmn/                             # Tabla de decisiones DMN de viabilidad
    │       └── processes/
    │           └── proceso-gestion-cursos.bpmn20.xml # Definición del proceso BPMN 2.0
    └── test/
        ├── java/com/gestion/cursos/
        │   ├── GestionCursosApplicationTests.java
        │   └── CursoProcesoControllerTest.java
        └── resources/
            └── application-test.yml             # Perfil de test con H2 en memoria
```

---

## ⚙️ Configuración de Conexión (Supabase / PostgreSQL)

Modificá las variables en `src/main/resources/application.yml` o definí las variables de entorno correspondientes:

```yaml
spring:
  datasource:
    # Supabase (Modo Conexión Directa):
    url: jdbc:postgresql://db.[TU_PROYECTO_REF].supabase.co:5432/postgres?sslmode=require
    
    # O Supabase (Connection Pooler transaccional, puerto 6543):
    # url: jdbc:postgresql://aws-0-[REGION].pooler.supabase.com:6543/postgres?sslmode=require
    username: postgres
    password: tu_contraseña_supabase
```

Al iniciar, Flowable creará automáticamente las tablas del motor (`ACT_RU_*`, `ACT_HI_*`, `ACT_RE_*`, etc.) gracias a `flowable.database-schema-update: true`.

---

## 🚀 Endpoints REST

### 1. Iniciar Proceso (Recepción de Formulario Web)
* **Método:** `POST`
* **URL:** `/api/v1/cursos/procesos/iniciar`
* **Body:**
```json
{
  "cursoId": "CURSO-BPM-101",
  "nombreCurso": "Gestión y Tecnologías de Procesos de Negocio",
  "categoria": "Ingeniería de Sistemas",
  "emailSolicitante": "profesor@universidad.edu",
  "cupoMaximo": 35,
  "descripcion": "Curso intensivo de modelado BPMN y motores de workflow"
}
```

### 2. Consultar Tareas Pendientes por Usuario
* **Método:** `GET`
* **URL:** `/api/v1/cursos/procesos/tareas/pendientes?usuario=admin_laura`

### 3. Completar Tarea
* **Método:** `POST`
* **URL:** `/api/v1/cursos/procesos/tareas/{taskId}/completar`
* **Body:**
```json
{
  "aprobado": true,
  "observaciones": "Cumple con los requisitos académicos.",
  "variablesAdicionales": {
    "fechaRevision": "2026-11-06"
  }
}
```

### 4. Reclamar Tarea (Claim)
* **Método:** `POST`
* **URL:** `/api/v1/cursos/procesos/tareas/{taskId}/reclamar?usuario=admin_laura`

### 5. Consultar Estado en Tiempo Real (Para Portal Empresa)
* **Método:** `GET`
* **URL:** `/api/v1/cursos/procesos/cursos/{identificador}/estado`

---

## 🌐 Frontend Web (HTML5, CSS BEM & JS Vanilla)

Al iniciar la aplicación (`mvn spring-boot:run`), los archivos se sirven automáticamente en:

* **Solicitud de Capacitación (Público):** [http://localhost:8080/index.html](http://localhost:8080/index.html)
* **Portal de Seguimiento para Empresas:** [http://localhost:8080/panel-empresa.html](http://localhost:8080/panel-empresa.html)
* **Panel de Control Administrativo (Dashboard):** [http://localhost:8080/panel-admin.html](http://localhost:8080/panel-admin.html)
* **Portal Docente:** [http://localhost:8080/panel-docente.html](http://localhost:8080/panel-docente.html)

---

## 🛠️ Herramientas Auxiliares

### 1. App Auxiliar 1: Preparación de Materiales (`AppPrepararMateriales` - Secc. 4.2.1)
Aplicación con **interfaz gráfica Swing** (y modo consola alternativo) que interactúa directamente con el motor Flowable utilizando la **API Java pura** (`TaskService`, `HistoryService`, `RuntimeService`):
* **Identificación administrativa:** Permite seleccionar o ingresar el usuario administrativo (`admin_martin`, `admin_laura`).
* **Consulta de tareas:** Filtra tareas de "Preparar materiales" (todas, disponibles para el grupo Administración, o asignadas).
* **Reclamo de tareas:** Reclama tareas para el usuario activo con un solo clic.
* **Completitud de tarea:** Registra observaciones de materiales didácticos y completa la tarea en Flowable.
* **Métricas históricas (Sección 4.3):** Pestaña dedicada con los cálculos de resultados, duración de ramas y tareas por integrante.

```bash
# Opción A: Ejecutar mediante script
./scripts/run-preparar-materiales.sh

# Opción B: Ejecutar interfaz gráfica con Maven
mvn compile exec:java -Dexec.mainClass="com.gestion.cursos.cli.AppPrepararMateriales"

# Opción C: Forzar modo consola CLI (por ejemplo en terminal pura o sin entorno gráfico)
mvn compile exec:java -Dexec.mainClass="com.gestion.cursos.cli.AppPrepararMateriales" -Dexec.args="--cli"
```

### 2. App Auxiliar 2: Simulador de Comunicaciones Empresariales (Secc. 4.2.2)
Permite interactuar mediante API REST simulando las comunicaciones habilitadas de la empresa:
```bash
# Con Node.js:
node simulador.js

# O con Java CLI:
./scripts/run-simulador-empresa.sh
```

---

## 🧪 Pruebas Automatizadas

Para compilar y ejecutar los tests con base de datos H2 en memoria:

```bash
mvn clean test
```
