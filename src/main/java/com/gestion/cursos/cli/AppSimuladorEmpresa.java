package com.gestion.cursos.cli;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Aplicación de consola interactiva (CLI) para la simulación de comunicaciones
 * de la empresa cliente vía API REST (Sección 4.2.2).
 *
 * Utiliza java.net.http.HttpClient para comunicarse con el servidor Spring Boot (localhost:8080).
 *
 * Permite simular:
 * 1) Aceptación de la propuesta (enviando fecha acordada).
 * 2) Rechazo de la propuesta (enviando motivo).
 * 3) Solicitud de modificaciones (enviando observaciones).
 * 4) Envío de nómina de participantes.
 * 5) Cancelación durante la preparación (dispara compensaciones automáticas).
 *
 * Solo muestra las comunicaciones habilitadas en el estado actual del proceso.
 */
public class AppSimuladorEmpresa {

    private static final String DEFAULT_BASE_URL = "http://localhost:8080/api/v1/cursos/procesos";
    private static final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();
    private static final Scanner scanner = new Scanner(System.in);

    public static void main(String[] args) {
        String baseUrl = System.getenv().getOrDefault("API_BASE_URL", DEFAULT_BASE_URL);

        imprimirEncabezado(baseUrl);

        while (true) {
            try {
                System.out.println("\n==================================================================");
                System.out.println("   SELECCIÓN DE INSTANCIA DE PROCESO (EMPRESA CLIENTE)");
                System.out.println("==================================================================");

                List<InstanciaDTO> instancias = listarInstanciasActivas(baseUrl);

                if (instancias.isEmpty()) {
                    System.out.println("\n[!] No hay instancias activas en ejecución en Flowable.");
                    System.out.println("    ¿Desea registrar una nueva solicitud de curso de prueba? (S/n): ");
                    String resp = scanner.nextLine().trim();
                    if (resp.equalsIgnoreCase("s") || resp.isEmpty()) {
                        String nuevaId = crearNuevaInstanciaPrueba(baseUrl);
                        if (nuevaId != null) {
                            gestionarInstancia(baseUrl, nuevaId);
                        }
                    } else {
                        System.out.println("Saliendo del simulador. ¡Hasta pronto!");
                        break;
                    }
                    continue;
                }

                System.out.println("\nInstancias activas disponibles:");
                for (int i = 0; i < instancias.size(); i++) {
                    InstanciaDTO inst = instancias.get(i);
                    System.out.printf("  [%d] Código: %-16s | ID Flowable: %s%n",
                            (i + 1), inst.businessKey, inst.processInstanceId);
                }
                System.out.println("  [N] Iniciar una NUEVA solicitud de curso automáticamente");
                System.out.println("  [0] Salir");

                System.out.print("\nSeleccione una opción: ");
                String input = scanner.nextLine().trim();

                if ("0".equals(input) || "salir".equalsIgnoreCase(input)) {
                    System.out.println("\nSaliendo del simulador.");
                    break;
                }

                if ("N".equalsIgnoreCase(input)) {
                    String nuevaId = crearNuevaInstanciaPrueba(baseUrl);
                    if (nuevaId != null) {
                        gestionarInstancia(baseUrl, nuevaId);
                    }
                    continue;
                }

                try {
                    int seleccion = Integer.parseInt(input);
                    if (seleccion >= 1 && seleccion <= instancias.size()) {
                        gestionarInstancia(baseUrl, instancias.get(seleccion - 1).processInstanceId);
                    } else {
                        System.out.println("Opción inválida.");
                    }
                } catch (NumberFormatException e) {
                    // Si el usuario pegó directamente un UUID
                    if (input.length() > 20) {
                        gestionarInstancia(baseUrl, input);
                    } else {
                        System.out.println("Entrada inválida.");
                    }
                }

            } catch (Exception e) {
                System.err.println("\n[ERROR] " + e.getMessage());
            }
        }
    }

    private static void gestionarInstancia(String baseUrl, String processInstanceId) {
        while (true) {
            try {
                System.out.println("\n------------------------------------------------------------------");
                System.out.println("GESTIONANDO INSTANCIA FLOWABLE: " + processInstanceId);

                // 1. Consultar estado actual
                mostrarEstadoActual(baseUrl, processInstanceId);

                // 2. Consultar comunicaciones habilitadas en este instante
                List<String> accionesHabilitadas = consultarComunicacionesHabilitadas(baseUrl, processInstanceId);

                System.out.println("\nAcciones de Empresa habilitadas para este estado:");
                if (accionesHabilitadas.isEmpty()) {
                    System.out.println("  [!] Ninguna comunicación empresarial activa en este momento.");
                    System.out.println("      (El proceso puede estar ejecutando tareas internas de Administración o Docentes)");
                } else {
                    if (accionesHabilitadas.contains("ACEPTAR_PROPUESTA")) {
                        System.out.println("  [1] ACEPTAR PROPUESTA (Acordar fecha de inicio)");
                    }
                    if (accionesHabilitadas.contains("RECHAZAR_PROPUESTA")) {
                        System.out.println("  [2] RECHAZAR PROPUESTA (Finalizar solicitud)");
                    }
                    if (accionesHabilitadas.contains("SOLICITAR_MODIFICACIONES")) {
                        System.out.println("  [3] SOLICITAR MODIFICACIONES (Enviar observaciones)");
                    }
                    if (accionesHabilitadas.contains("ENVIAR_NOMINA")) {
                        System.out.println("  [4] ENVIAR NÓMINA DE PARTICIPANTES");
                    }
                    if (accionesHabilitadas.contains("CANCELAR_PREPARACION")) {
                        System.out.println("  [5] CANCELAR DURANTE LA PREPARACIÓN (Dispara compensaciones)");
                    }
                }

                System.out.println("  [C] Consultar tareas pendientes de la institución");
                System.out.println("  [V] Volver al listado de instancias");

                System.out.print("\nSeleccione acción: ");
                String opc = scanner.nextLine().trim().toUpperCase();

                if ("V".equals(opc)) {
                    break;
                }

                if ("C".equals(opc)) {
                    consultarTareasInstancia(baseUrl, processInstanceId);
                    continue;
                }

                switch (opc) {
                    case "1" -> {
                        if (!accionesHabilitadas.contains("ACEPTAR_PROPUESTA")) {
                            System.out.println("[✕] La acción ACEPTAR no está habilitada en el estado actual.");
                            break;
                        }
                        String fechaDefault = LocalDateTime.now().plusDays(15).format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'10:00:00"));
                        System.out.print("Ingrese fecha acordada de inicio [" + fechaDefault + "]: ");
                        String fecha = scanner.nextLine().trim();
                        if (fecha.isEmpty()) fecha = fechaDefault;
                        enviarPost(baseUrl + "/instancias/" + processInstanceId + "/comunicacion/aceptar",
                                "{\"fechaInicioCurso\":\"" + fecha + "\"}");
                        System.out.println("[✓] ¡Propuesta aceptada! La instancia pasó a fase de Preparación.");
                    }
                    case "2" -> {
                        if (!accionesHabilitadas.contains("RECHAZAR_PROPUESTA")) {
                            System.out.println("[✕] La acción RECHAZAR no está habilitada en el estado actual.");
                            break;
                        }
                        System.out.print("Ingrese motivo del rechazo [Presupuesto excedido]: ");
                        String motivo = scanner.nextLine().trim();
                        if (motivo.isEmpty()) motivo = "Presupuesto excedido para este semestre";
                        enviarPost(baseUrl + "/instancias/" + processInstanceId + "/comunicacion/rechazar",
                                "{\"motivo\":\"" + motivo + "\"}");
                        System.out.println("[✓] Propuesta rechazada. El proceso continuará hacia el archivo de solicitud.");
                    }
                    case "3" -> {
                        if (!accionesHabilitadas.contains("SOLICITAR_MODIFICACIONES")) {
                            System.out.println("[✕] La acción MODIFICACIONES no está habilitada en el estado actual.");
                            break;
                        }
                        System.out.print("Ingrese modificaciones requeridas [Ajustar horario a turno tarde]: ");
                        String obs = scanner.nextLine().trim();
                        if (obs.isEmpty()) obs = "Ajustar horario a turno tarde y modalidad mixta";
                        enviarPost(baseUrl + "/instancias/" + processInstanceId + "/comunicacion/modificaciones",
                                "{\"observaciones\":\"" + obs + "\"}");
                        System.out.println("[✓] Solicitud de modificaciones enviada. El Jefe Administrativo debe revisar la propuesta.");
                    }
                    case "4" -> {
                        if (!accionesHabilitadas.contains("ENVIAR_NOMINA")) {
                            System.out.println("[✕] La acción ENVIAR NÓMINA no está habilitada en el estado actual.");
                            break;
                        }
                        System.out.println("Enviando nómina estándar de 4 participantes corporativos...");
                        String jsonNomina = "{\"nomina\":[\"Ing. Juan Pérez\",\"Lic. Mariana Gómez\",\"Tec. Carlos Benítez\",\"Dr. Roberto Sánchez\"]}";
                        enviarPost(baseUrl + "/instancias/" + processInstanceId + "/comunicacion/nomina", jsonNomina);
                        System.out.println("[✓] Nómina enviada. Se habilitó la tarea 'Confeccionar lista definitiva'.");
                    }
                    case "5" -> {
                        if (!accionesHabilitadas.contains("CANCELAR_PREPARACION")) {
                            System.out.println("[✕] La acción CANCELAR no está habilitada en el estado actual.");
                            break;
                        }
                        System.out.print("Ingrese motivo de cancelación [Prioridades corporativas cambiadas]: ");
                        String motivoCancel = scanner.nextLine().trim();
                        if (motivoCancel.isEmpty()) motivoCancel = "Prioridades corporativas cambiadas";
                        enviarPost(baseUrl + "/instancias/" + processInstanceId + "/comunicacion/cancelar",
                                "{\"motivo\":\"" + motivoCancel + "\"}");
                        System.out.println("[✓] Solicitud de cancelación enviada. Se interrumpen ramas y se ejecutan compensaciones.");
                    }
                    default -> System.out.println("Opción no reconocida.");
                }

                Thread.sleep(800); // Pausa breve para propagación asíncrona

            } catch (Exception e) {
                System.err.println("[Error en operación]: " + e.getMessage());
            }
        }
    }

    private static List<InstanciaDTO> listarInstanciasActivas(String baseUrl) throws IOException, InterruptedException {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/instancias/activas"))
                .GET()
                .build();

        HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());
        List<InstanciaDTO> lista = new ArrayList<>();

        if (resp.statusCode() == 200) {
            String body = resp.body();
            Pattern p = Pattern.compile("\\{\"processInstanceId\":\"([^\"]+)\",\"businessKey\":\"([^\"]+)\"");
            Matcher m = p.matcher(body);
            while (m.find()) {
                lista.add(new InstanciaDTO(m.group(1), m.group(2)));
            }
        }
        return lista;
    }

    private static List<String> consultarComunicacionesHabilitadas(String baseUrl, String processInstanceId) {
        List<String> habilitadas = new ArrayList<>();
        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/instancias/" + processInstanceId + "/comunicaciones-habilitadas"))
                    .GET()
                    .build();
            HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() == 200) {
                String body = resp.body();
                Pattern p = Pattern.compile("\"([A-Z_]+)\"");
                Matcher m = p.matcher(body);
                while (m.find()) {
                    String action = m.group(1);
                    if (!"DATOS".equals(action) && !"EXITO".equals(action)) {
                        habilitadas.add(action);
                    }
                }
            }
        } catch (Exception ignored) {}
        return habilitadas;
    }

    private static void mostrarEstadoActual(String baseUrl, String processInstanceId) {
        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/cursos/" + processInstanceId + "/estado"))
                    .GET()
                    .build();
            HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() == 200) {
                String body = resp.body();
                String estado = extraerCampoJson(body, "estado");
                String tarea = extraerCampoJson(body, "tareaActual");
                String curso = extraerCampoJson(body, "nombreCurso");
                System.out.printf("Curso: [%s] | Estado: [%s] | Actividad: [%s]%n", curso, estado, tarea);
            }
        } catch (Exception e) {
            System.out.println("Estado: no disponible (" + e.getMessage() + ")");
        }
    }

    private static void consultarTareasInstancia(String baseUrl, String processInstanceId) {
        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/instancias/" + processInstanceId + "/tareas"))
                    .GET()
                    .build();
            HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());
            System.out.println("\nTareas humanas pendientes en Flowable para esta instancia:");
            if (resp.statusCode() == 200) {
                String body = resp.body();
                Pattern p = Pattern.compile("\\{\"taskId\":\"([^\"]+)\",\"taskName\":\"([^\"]+)\",\"assignee\":([^,]+)");
                Matcher m = p.matcher(body);
                boolean encontro = false;
                while (m.find()) {
                    encontro = true;
                    String id = m.group(1);
                    String name = m.group(2);
                    String assignee = m.group(3).replace("\"", "");
                    System.out.printf("  - [%s] %s (Asignado a: %s)%n", id, name, assignee);
                }
                if (!encontro) {
                    System.out.println("  (No hay tareas humanas activas en este momento)");
                }
            }
        } catch (Exception e) {
            System.err.println("Error consultando tareas: " + e.getMessage());
        }
    }

    private static String crearNuevaInstanciaPrueba(String baseUrl) {
        String codigo = "CURSO-DEMO-" + (1000 + (int)(Math.random() * 9000));
        System.out.println("\nRegistrando solicitud para nuevo curso [" + codigo + "]...");

        String payload = """
                {
                  "cursoId": "%s",
                  "nombreCurso": "Capacitación en Flowable BPM y Arquitectura de Negocios",
                  "categoria": "Sistemas de Información",
                  "emailSolicitante": "rrhh@empresa-cliente.com",
                  "cupoMaximo": 25,
                  "descripcion": "Solicitud iniciada desde el simulador de comunicaciones empresariales."
                }
                """.formatted(codigo);

        try {
            HttpResponse<String> resp = enviarPost(baseUrl + "/iniciar", payload);
            if (resp.statusCode() == 201 || resp.statusCode() == 200) {
                String processId = extraerCampoJson(resp.body(), "processInstanceId");
                System.out.println("[✓] ¡Proceso iniciado exitosamente! Flowable ProcessInstanceId: " + processId);
                return processId;
            } else {
                System.err.println("[✕] Falló inicio: " + resp.body());
            }
        } catch (Exception e) {
            System.err.println("[Error]: " + e.getMessage());
        }
        return null;
    }

    private static HttpResponse<String> enviarPost(String url, String jsonBody) throws IOException, InterruptedException {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                .build();
        return client.send(req, HttpResponse.BodyHandlers.ofString());
    }

    private static String extraerCampoJson(String json, String campo) {
        Pattern p = Pattern.compile("\"" + campo + "\":\"?([^\",}]+)\"?");
        Matcher m = p.matcher(json);
        return m.find() ? m.group(1) : "N/D";
    }

    private static void imprimirEncabezado(String baseUrl) {
        System.out.println("==================================================================");
        System.out.println("   FLOWABLE BPM - SIMULADOR DE COMUNICACIONES DE EMPRESA (CLI)   ");
        System.out.println("   Requisito Sección 4.2.2: Interacción vía API REST HTTP         ");
        System.out.println("==================================================================");
        System.out.println("Destino REST: " + baseUrl);
    }

    private record InstanciaDTO(String processInstanceId, String businessKey) {}
}
