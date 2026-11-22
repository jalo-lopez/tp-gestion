package com.gestion.cursos.cli;

import org.flowable.engine.HistoryService;
import org.flowable.engine.ProcessEngine;
import org.flowable.engine.ProcessEngineConfiguration;
import org.flowable.engine.TaskService;
import org.flowable.engine.history.HistoricActivityInstance;
import org.flowable.engine.history.HistoricProcessInstance;
import org.flowable.task.api.Task;
import org.flowable.task.api.history.HistoricTaskInstance;

import javax.swing.*;
import java.awt.GraphicsEnvironment;
import java.util.*;

/**
 * Aplicación Auxiliar 1: Preparación de Materiales mediante la API Java de Flowable.
 * Conexión directa a la BD del motor BPM local a la intranet.
 *
 * Cumple con:
 * - Sección 4.2.1:
 *     - Interfaz Gráfica (Swing) local a la intranet con alcance a la BD del motor Flowable.
 *     - API Java de Flowable (TaskService, HistoryService, RuntimeService).
 *     - Permite a un administrativo identificarse, consultar tareas 'Preparar materiales', reclamar y completar la tarea.
 * - Modo fallback por consola (CLI) disponible mediante argumento '--cli' o en entornos headless.
 * - Sección 4.3: Métricas y consultas históricas.
 */
public class AppPrepararMateriales {

    private static final String DEFAULT_JDBC_URL = "jdbc:h2:file:/tmp/flowable_db;AUTO_SERVER=TRUE;DB_CLOSE_DELAY=-1";
    private static final String DEFAULT_JDBC_USER = "sa";
    private static final String DEFAULT_JDBC_PASS = "";
    private static final String DEFAULT_JDBC_DRIVER = "org.h2.Driver";
    public static final String PROCESS_KEY = "procesoGestionCursos";

    public static void main(String[] args) {
        System.out.println("==================================================================");
        System.out.println("   FLOWABLE BPM - APP AUXILIAR 1 (API JAVA)");
        System.out.println("   Preparación de Materiales & Métricas Históricas");
        System.out.println("==================================================================");

        boolean forzarCli = false;
        for (String arg : args) {
            if ("--cli".equalsIgnoreCase(arg) || "-c".equalsIgnoreCase(arg) || "cli".equalsIgnoreCase(arg)) {
                forzarCli = true;
                break;
            }
        }

        String jdbcUrl = System.getenv().getOrDefault("SPRING_DATASOURCE_URL", DEFAULT_JDBC_URL);
        String jdbcUser = System.getenv().getOrDefault("SPRING_DATASOURCE_USERNAME", DEFAULT_JDBC_USER);
        String jdbcPassword = System.getenv().getOrDefault("SPRING_DATASOURCE_PASSWORD", DEFAULT_JDBC_PASS);
        String jdbcDriver = System.getenv().getOrDefault("SPRING_DATASOURCE_DRIVER", DEFAULT_JDBC_DRIVER);

        System.out.println("Conectando al motor Flowable: " + jdbcUrl);

        ProcessEngine processEngine = null;

        try {
            ProcessEngineConfiguration cfg = ProcessEngineConfiguration.createStandaloneProcessEngineConfiguration()
                    .setJdbcUrl(jdbcUrl)
                    .setJdbcUsername(jdbcUser)
                    .setJdbcPassword(jdbcPassword)
                    .setJdbcDriver(jdbcDriver)
                    .setDatabaseSchemaUpdate(ProcessEngineConfiguration.DB_SCHEMA_UPDATE_TRUE);

            processEngine = cfg.buildProcessEngine();
            System.out.println("[✓] Motor Flowable inicializado exitosamente.\n");

            boolean esHeadless = GraphicsEnvironment.isHeadless();

            if (!forzarCli && !esHeadless) {
                // Modo Interfaz Gráfica (Swing) exigido por la Sección 4.2.1
                System.out.println("[✓] Iniciando Interfaz Gráfica (GUI Swing - Sección 4.2.1)...");
                try {
                    UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
                } catch (Exception ignored) {
                }

                final ProcessEngine engineParaGui = processEngine;
                SwingUtilities.invokeLater(() -> {
                    try {
                        AppPrepararMaterialesGUI gui = new AppPrepararMaterialesGUI(engineParaGui);
                        gui.setVisible(true);
                    } catch (Exception e) {
                        System.err.println("[✕] Error al inicializar interfaz gráfica: " + e.getMessage());
                        e.printStackTrace();
                        System.out.println("Iniciando modo consola de respaldo...");
                        ejecutarConsola(engineParaGui);
                    }
                });
            } else {
                // Modo Consola CLI (fallback si es headless o si se pasa --cli)
                if (esHeadless) {
                    System.out.println("[!] Entorno headless detectado (sin servidor X/pantalla). Iniciando modo consola CLI.");
                } else {
                    System.out.println("[i] Modo CLI solicitado mediante parámetro.");
                }
                ejecutarConsola(processEngine);
                if (processEngine != null) {
                    processEngine.close();
                }
            }

        } catch (Exception e) {
            System.err.println("\n[ERROR CRÍTICO] " + e.getMessage());
            e.printStackTrace();
            if (processEngine != null) {
                processEngine.close();
            }
        }
    }

    private static void ejecutarConsola(ProcessEngine processEngine) {
        TaskService taskService = processEngine.getTaskService();
        HistoryService historyService = processEngine.getHistoryService();
        Scanner scanner = new Scanner(System.in);

        try {
            while (true) {
                System.out.println("==================================================================");
                System.out.println("                    MENÚ PRINCIPAL (API JAVA)                     ");
                System.out.println("==================================================================");
                System.out.println("  [1] Tarea: Consultar, Reclamar y Completar 'Preparar materiales'");
                System.out.println("  [2] Métrica 1: Instancias finalizadas por resultado (cant. y %)");
                System.out.println("  [3] Métrica 2: Duración promedio de ramas de preparación");
                System.out.println("  [4] Métrica 3: Tareas completadas por integrante institucional");
                System.out.println("  [0] Salir");
                System.out.print("\nSeleccione una opción: ");

                String opcion = scanner.nextLine().trim();

                if ("0".equals(opcion) || "salir".equalsIgnoreCase(opcion)) {
                    System.out.println("\nFinalizando aplicación auxiliar. ¡Hasta luego!");
                    break;
                }

                switch (opcion) {
                    case "1" -> gestionarPreparacionMateriales(taskService, scanner);
                    case "2" -> mostrarMetricaResultados(historyService);
                    case "3" -> mostrarMetricaDuracionRamas(historyService);
                    case "4" -> mostrarMetricaTareasPorUsuario(historyService);
                    default -> System.out.println("[✕] Opción inválida.");
                }

                System.out.println();
            }
        } finally {
            scanner.close();
        }
    }

    private static void gestionarPreparacionMateriales(TaskService taskService, Scanner scanner) {
        String nombreTarea = "Preparar materiales";
        String grupoCandidato = "Administración";

        System.out.printf("%nBuscando tareas pendientes de '%s' para el grupo '%s'...%n", nombreTarea, grupoCandidato);

        List<Task> tareas = taskService.createTaskQuery()
                .taskName(nombreTarea)
                .taskCandidateGroup(grupoCandidato)
                .active()
                .orderByTaskCreateTime().desc()
                .list();

        if (tareas.isEmpty()) {
            tareas = taskService.createTaskQuery()
                    .taskName(nombreTarea)
                    .active()
                    .orderByTaskCreateTime().desc()
                    .list();
        }

        if (tareas.isEmpty()) {
            System.out.println("\n[!] No hay tareas de 'Preparar materiales' pendientes en este momento.");
            return;
        }

        System.out.println("\n--------------------------------------------------------------------------------");
        System.out.printf("%-38s | %-20s | %-12s%n", "ID de Tarea", "Instancia Proceso", "Creación");
        System.out.println("--------------------------------------------------------------------------------");
        for (Task t : tareas) {
            System.out.printf("%-38s | %-20s | %s%n", t.getId(), t.getProcessInstanceId(), t.getCreateTime());
        }
        System.out.println("--------------------------------------------------------------------------------");

        System.out.print("\nIngrese ID de usuario administrativo (ej: admin_laura, admin_martin) [admin_martin]: ");
        String usuario = scanner.nextLine().trim();
        if (usuario.isEmpty()) usuario = "admin_martin";

        System.out.print("Ingrese ID de la tarea a reclamar [ENTER para la primera]: ");
        String taskId = scanner.nextLine().trim();
        if (taskId.isEmpty()) taskId = tareas.getFirst().getId();

        System.out.printf(">>> Reclamando tarea [%s] para [%s]...%n", taskId, usuario);
        taskService.claim(taskId, usuario);
        System.out.println("[✓] Tarea reclamada exitosamente.");

        System.out.print("Ingrese observaciones del material didáctico [Materiales impresos y diapositivas listas]: ");
        String obs = scanner.nextLine().trim();
        if (obs.isEmpty()) obs = "Materiales impresos y diapositivas listas por " + usuario;

        Map<String, Object> vars = new HashMap<>();
        vars.put("materialesPreparados", true);
        vars.put("responsableMateriales", usuario);
        vars.put("observacionesMateriales", obs);

        System.out.printf(">>> Completando tarea [%s] en Flowable...%n", taskId);
        taskService.complete(taskId, vars);
        System.out.println("[✓] ¡Tarea 'Preparar materiales' completada exitosamente!");
    }

    private static void mostrarMetricaResultados(HistoryService historyService) {
        System.out.println("\n==================================================================");
        System.out.println("   MÉTRICA 1: INSTANCIAS FINALIZADAS POR RESULTADO (Secc. 4.3.1)  ");
        System.out.println("==================================================================");

        List<HistoricProcessInstance> instancias = historyService.createHistoricProcessInstanceQuery()
                .processDefinitionKey(PROCESS_KEY)
                .finished()
                .includeProcessVariables()
                .list();

        long total = instancias.size();
        Map<String, Long> conteo = new LinkedHashMap<>();
        conteo.put("finalizado", 0L);
        conteo.put("cancelado", 0L);
        conteo.put("rechazado", 0L);
        conteo.put("no viable", 0L);
        conteo.put("cerrado por falta de respuesta", 0L);

        for (HistoricProcessInstance pi : instancias) {
            String res = (String) pi.getProcessVariables().get("resultado");
            if (res == null) res = "finalizado";
            res = res.toLowerCase().trim();
            conteo.put(res, conteo.getOrDefault(res, 0L) + 1);
        }

        System.out.printf("Total de instancias finalizadas analizadas: %d%n%n", total);
        System.out.println("------------------------------------------------------------------");
        System.out.printf("%-35s | %-12s | %-12s%n", "Resultado", "Cantidad", "Porcentaje");
        System.out.println("------------------------------------------------------------------");
        for (Map.Entry<String, Long> entry : conteo.entrySet()) {
            double pct = total > 0 ? (entry.getValue() * 100.0) / total : 0.0;
            System.out.printf("%-35s | %-12d | %6.2f %%%n", entry.getKey(), entry.getValue(), pct);
        }
        System.out.println("------------------------------------------------------------------");
    }

    private static void mostrarMetricaDuracionRamas(HistoryService historyService) {
        System.out.println("\n==================================================================");
        System.out.println("   MÉTRICA 2: DURACIÓN PROMEDIO DE RAMAS DE PREPARACIÓN (Secc. 4.3.2)");
        System.out.println("   (Excluyendo instancias canceladas)                              ");
        System.out.println("==================================================================");

        List<HistoricProcessInstance> instanciasValidas = historyService.createHistoricProcessInstanceQuery()
                .processDefinitionKey(PROCESS_KEY)
                .finished()
                .includeProcessVariables()
                .list()
                .stream()
                .filter(pi -> {
                    String res = (String) pi.getProcessVariables().get("resultado");
                    return res == null || !"cancelado".equalsIgnoreCase(res.trim());
                })
                .toList();

        long sumInsc = 0, countInsc = 0;
        long sumMat = 0, countMat = 0;
        long sumRec = 0, countRec = 0;

        for (HistoricProcessInstance pi : instanciasValidas) {
            List<HistoricActivityInstance> acts = historyService.createHistoricActivityInstanceQuery()
                    .processInstanceId(pi.getId())
                    .finished()
                    .list();

            for (HistoricActivityInstance a : acts) {
                Long dur = a.getDurationInMillis();
                if (dur == null) continue;

                if ("confeccionarListaDefinitivaTask".equals(a.getActivityId())) {
                    sumInsc += dur;
                    countInsc++;
                } else if ("prepararMaterialesTask".equals(a.getActivityId())) {
                    sumMat += dur;
                    countMat++;
                } else if ("evaluarRecursosTask".equals(a.getActivityId())
                        || "reservarAulaTask".equals(a.getActivityId())
                        || "solicitarAulaVirtualTask".equals(a.getActivityId())
                        || "contratarEquipamientoTask".equals(a.getActivityId())) {
                    sumRec += dur;
                    countRec++;
                }
            }
        }

        System.out.printf("Instancias de cursos completadas analizadas: %d%n%n", instanciasValidas.size());
        System.out.println("------------------------------------------------------------------");
        System.out.printf("%-30s | %-18s | %-12s%n", "Rama Paralela", "Duración Prom. (s)", "Mediciones");
        System.out.println("------------------------------------------------------------------");
        System.out.printf("%-30s | %18.2f | %-12d%n", "1. Inscripciones", countInsc > 0 ? (sumInsc / countInsc) / 1000.0 : 0.0, countInsc);
        System.out.printf("%-30s | %18.2f | %-12d%n", "2. Materiales Didácticos", countMat > 0 ? (sumMat / countMat) / 1000.0 : 0.0, countMat);
        System.out.printf("%-30s | %18.2f | %-12d%n", "3. Recursos y Aulas", countRec > 0 ? (sumRec / countRec) / 1000.0 : 0.0, countRec);
        System.out.println("------------------------------------------------------------------");
    }

    private static void mostrarMetricaTareasPorUsuario(HistoryService historyService) {
        System.out.println("\n==================================================================");
        System.out.println("   MÉTRICA 3: TAREAS COMPLETADAS POR INTEGRANTE (Secc. 4.3.3)      ");
        System.out.println("==================================================================");

        List<HistoricTaskInstance> tareas = historyService.createHistoricTaskInstanceQuery()
                .processDefinitionKey(PROCESS_KEY)
                .finished()
                .list();

        Map<String, Long> tareasPorUsuario = new LinkedHashMap<>();
        tareasPorUsuario.put("admin_laura", 0L);
        tareasPorUsuario.put("admin_martin", 0L);
        tareasPorUsuario.put("docente_carlos", 0L);
        tareasPorUsuario.put("docente_ana", 0L);
        tareasPorUsuario.put("docente_pedro", 0L);

        for (HistoricTaskInstance t : tareas) {
            String assignee = t.getAssignee();
            if (assignee != null) {
                tareasPorUsuario.put(assignee, tareasPorUsuario.getOrDefault(assignee, 0L) + 1);
            }
        }

        System.out.printf("Total acumulado de tareas humanas completadas: %d%n%n", tareas.size());
        System.out.println("------------------------------------------------------------------");
        System.out.printf("%-18s | %-18s | %-16s%n", "Usuario", "Área / Rol", "Tareas Realizadas");
        System.out.println("------------------------------------------------------------------");
        for (Map.Entry<String, Long> entry : tareasPorUsuario.entrySet()) {
            String rol = entry.getKey().startsWith("admin") ? "Administración" : "Equipo Docente";
            System.out.printf("%-18s | %-18s | %-16d%n", entry.getKey(), rol, entry.getValue());
        }
        System.out.println("------------------------------------------------------------------");
    }
}
