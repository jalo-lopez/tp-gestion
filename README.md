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
    │   │   │   └── CursoProcesoController.java  # Endpoints REST para iniciar, listar y completar tareas
    │   │   ├── dto/
    │   │   │   ├── request/
    │   │   │   │   ├── IniciarCursoRequest.java # Payload del formulario web
    │   │   │   │   └── CompletarTareaRequest.java
    │   │   │   └── response/
    │   │   │       ├── ApiResponse.java         # Envoltorio estándar REST
    │   │   │       ├── ProcesoIniciadoResponse.java
    │   │   │       └── TareaResponse.java
    │   │   ├── listener/
    │   │   │   ├── AsignacionBalanceoTaskListener.java       # TaskListener para balanceo de carga
    │   │   │   └── ActualizarEstadoCursoExecutionListener.java # ExecutionListener para sincronización BD externa
    │   │   └── service/
    │   │       ├── CursoExternalService.java    # Comunicación con BD externa / Supabase
    │   │       └── CursoProcessService.java     # Lógica orquestadora con Flowable APIs
    │   └── resources/
    │       ├── application.yml                  # Configuración de base de datos y Flowable
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

---

## 🛠️ Herramientas Auxiliares (CLI & Scripting)

### 1. Aplicación Java Pura: `AppPrepararMateriales`
Conexión directa a Flowable mediante `TaskService` para buscar tareas del grupo `Administración`, reclamarlas y completarlas por consola:

```bash
# Compilar y ejecutar con Maven
mvn compile exec:java -Dexec.mainClass="com.gestion.cursos.cli.AppPrepararMateriales"
```

### 2. Simulador Interactivo Node.js: `simulador.js`
Cliente de consola interactivo que simula las respuestas empresariales mediante `fetch()`:

```bash
# Ejecutar menú interactivo
node simulador.js
```

---

## 🧪 Pruebas Automatizadas

Para compilar y ejecutar los tests con base de datos H2 en memoria:

```bash
mvn clean test
```
