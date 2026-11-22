package com.gestion.cursos.cli;

import org.flowable.engine.HistoryService;
import org.flowable.engine.ProcessEngine;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.TaskService;
import org.flowable.engine.history.HistoricActivityInstance;
import org.flowable.engine.history.HistoricProcessInstance;
import org.flowable.engine.runtime.ProcessInstance;
import org.flowable.task.api.Task;
import org.flowable.task.api.history.HistoricTaskInstance;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.TitledBorder;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.List;

/**
 * Interfaz Gráfica (Swing) para la Aplicación Auxiliar 1 (Sección 4.2.1).
 *
 * Permite a un administrativo de la intranet:
 * 1. Identificarse como usuario administrativo (admin_laura, admin_martin, etc.).
 * 2. Consultar las tareas de 'Preparar materiales' disponibles o asignadas mediante la API Java de Flowable.
 * 3. Reclamar tareas disponibles para el grupo Administración.
 * 4. Completar la tarea registrando las observaciones de los materiales didácticos.
 * 5. Consultar métricas históricas del motor BPM (Sección 4.3).
 */
public class AppPrepararMaterialesGUI extends JFrame {

    private final ProcessEngine processEngine;
    private final TaskService taskService;
    private final HistoryService historyService;
    private final RuntimeService runtimeService;

    // Componentes de usuario
    private JComboBox<String> cmbUsuario;
    private JLabel lblUsuarioEstado;

    // Tabla de tareas y modelo
    private JTable tablaTareas;
    private DefaultTableModel modeloTareas;
    private List<Task> listaTareasActuales = new ArrayList<>();

    // Filtros
    private JRadioButton rbTodas;
    private JRadioButton rbDisponibles;
    private JRadioButton rbMisTareas;

    private JLabel lblTareaSeleccionada;
    private JButton btnReclamar;
    private JButton btnCompletar;
    private JTextField txtObservaciones;
    private JLabel lblStatus;

    private JTextArea txtMetricaResultados;
    private JTextArea txtMetricaDuracion;
    private JTextArea txtMetricaUsuarios;

    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

    public AppPrepararMaterialesGUI(ProcessEngine processEngine) {
        super("Flowable BPM - Preparación de Materiales (App Auxiliar 1 - Secc. 4.2.1)");
        this.processEngine = processEngine;
        this.taskService = processEngine.getTaskService();
        this.historyService = processEngine.getHistoryService();
        this.runtimeService = processEngine.getRuntimeService();

        initUI();
        refrescarTareas();
    }

    private void initUI() {
        setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                cerrarAplicacion();
            }
        });

        setSize(1000, 700);
        setMinimumSize(new Dimension(850, 600));
        setLocationRelativeTo(null);

        JPanel panelPrincipal = new JPanel(new BorderLayout(10, 10));
        panelPrincipal.setBorder(new EmptyBorder(10, 10, 10, 10));

        // 1. Encabezado con Identificación
        JPanel panelNorte = crearPanelNorte();
        panelPrincipal.add(panelNorte, BorderLayout.NORTH);

        // 2. Panel Central con Pestañas
        JTabbedPane tabbedPane = new JTabbedPane();
        tabbedPane.setFont(new Font("SansSerif", Font.BOLD, 12));

        JPanel panelTareas = crearPanelTareas();
        tabbedPane.addTab("📋 Gestión de Tareas (Secc. 4.2.1)", panelTareas);

        JPanel panelMetricas = crearPanelMetricas();
        tabbedPane.addTab("📊 Métricas Históricas (Secc. 4.3)", panelMetricas);

        panelPrincipal.add(tabbedPane, BorderLayout.CENTER);

        // 3. Barra de Estado Inferior
        JPanel panelSur = new JPanel(new BorderLayout());
        panelSur.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, Color.LIGHT_GRAY),
                new EmptyBorder(5, 5, 5, 5)
        ));
        lblStatus = new JLabel("Listo. Conectado al motor Flowable BPM mediante API Java.");
        lblStatus.setFont(new Font("SansSerif", Font.PLAIN, 12));
        panelSur.add(lblStatus, BorderLayout.WEST);

        JLabel lblEngine = new JLabel("Flowable Engine v" + ProcessEngine.class.getPackage().getImplementationVersion());
        lblEngine.setFont(new Font("SansSerif", Font.ITALIC, 11));
        lblEngine.setForeground(Color.GRAY);
        panelSur.add(lblEngine, BorderLayout.EAST);

        panelPrincipal.add(panelSur, BorderLayout.SOUTH);

        setContentPane(panelPrincipal);
    }

    private JPanel crearPanelNorte() {
        JPanel panelNorte = new JPanel(new BorderLayout(10, 10));
        JPanel panelTitulo = new JPanel(new GridLayout(2, 1, 2, 2));
        JLabel lblTitulo = new JLabel("Intranet Administrativa - Preparación de Materiales");
        lblTitulo.setFont(new Font("SansSerif", Font.BOLD, 18));
        lblTitulo.setForeground(new Color(25, 45, 95));

        JLabel lblSubtitulo = new JLabel("Aplicación Auxiliar 1 (API Java Pura) • Conexión directa a base de datos de Flowable");
        lblSubtitulo.setFont(new Font("SansSerif", Font.PLAIN, 12));
        lblSubtitulo.setForeground(Color.DARK_GRAY);

        panelTitulo.add(lblTitulo);
        panelTitulo.add(lblSubtitulo);
        panelNorte.add(panelTitulo, BorderLayout.WEST);

        // Identificación de usuarios:
        JPanel panelIdentificacion = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 4));
        panelIdentificacion.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createEtchedBorder(), "Identificación Administrativa",
                TitledBorder.RIGHT, TitledBorder.TOP, new Font("SansSerif", Font.BOLD, 11)
        ));

        JLabel lblUser = new JLabel("Usuario:");
        lblUser.setFont(new Font("SansSerif", Font.BOLD, 12));
        panelIdentificacion.add(lblUser);

        String[] usuariosDefault = {"admin_martin", "admin_laura"};
        cmbUsuario = new JComboBox<>(usuariosDefault);
        cmbUsuario.setEditable(true);
        cmbUsuario.setPreferredSize(new Dimension(140, 26));
        cmbUsuario.addActionListener(e -> onUsuarioCambiado());
        panelIdentificacion.add(cmbUsuario);

        JButton btnIdentificar = new JButton("Aplicar");
        btnIdentificar.setToolTipText("Actualizar contexto al usuario seleccionado");
        btnIdentificar.addActionListener(e -> onUsuarioCambiado());
        panelIdentificacion.add(btnIdentificar);

        lblUsuarioEstado = new JLabel("✓ admin_martin (Grupo: Administración)");
        lblUsuarioEstado.setFont(new Font("SansSerif", Font.BOLD, 11));
        lblUsuarioEstado.setForeground(new Color(0, 128, 0));
        panelIdentificacion.add(lblUsuarioEstado);

        panelNorte.add(panelIdentificacion, BorderLayout.EAST);

        return panelNorte;
    }

    private JPanel crearPanelTareas() {
        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setBorder(new EmptyBorder(10, 5, 5, 5));

        // Barra superior de herramientas y filtros
        JPanel panelToolbar = new JPanel(new BorderLayout());

        JPanel panelFiltros = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 5));
        JLabel lblFiltrar = new JLabel("Filtrar tareas:");
        lblFiltrar.setFont(new Font("SansSerif", Font.BOLD, 12));
        panelFiltros.add(lblFiltrar);

        rbTodas = new JRadioButton("Todas", true);
        rbDisponibles = new JRadioButton("Disponibles para reclamar");
        rbMisTareas = new JRadioButton("Mis tareas asignadas");

        ButtonGroup bg = new ButtonGroup();
        bg.add(rbTodas);
        bg.add(rbDisponibles);
        bg.add(rbMisTareas);

        rbTodas.addActionListener(e -> aplicarFiltroTabla());
        rbDisponibles.addActionListener(e -> aplicarFiltroTabla());
        rbMisTareas.addActionListener(e -> aplicarFiltroTabla());

        panelFiltros.add(rbTodas);
        panelFiltros.add(rbDisponibles);
        panelFiltros.add(rbMisTareas);

        panelToolbar.add(panelFiltros, BorderLayout.WEST);

        JButton btnRefrescar = new JButton("Refrescar Tareas");
        btnRefrescar.setFont(new Font("SansSerif", Font.BOLD, 12));
        btnRefrescar.addActionListener(e -> refrescarTareas());
        panelToolbar.add(btnRefrescar, BorderLayout.EAST);

        panel.add(panelToolbar, BorderLayout.NORTH);

        // Tabla de tareas
        String[] columnas = {"ID Tarea", "Curso / BusinessKey", "ID Proceso", "Estado", "Asignado a", "Fecha Creación"};
        modeloTareas = new DefaultTableModel(columnas, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };

        tablaTareas = new JTable(modeloTareas);
        tablaTareas.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        tablaTareas.setRowHeight(24);
        tablaTareas.getTableHeader().setFont(new Font("SansSerif", Font.BOLD, 12));

        // Alineación y colores
        tablaTareas.getColumnModel().getColumn(0).setPreferredWidth(160);
        tablaTareas.getColumnModel().getColumn(1).setPreferredWidth(140);
        tablaTareas.getColumnModel().getColumn(2).setPreferredWidth(160);
        tablaTareas.getColumnModel().getColumn(3).setPreferredWidth(160);
        tablaTareas.getColumnModel().getColumn(4).setPreferredWidth(110);
        tablaTareas.getColumnModel().getColumn(5).setPreferredWidth(140);

        DefaultTableCellRenderer centerRenderer = new DefaultTableCellRenderer();
        centerRenderer.setHorizontalAlignment(JLabel.CENTER);
        tablaTareas.getColumnModel().getColumn(3).setCellRenderer(centerRenderer);
        tablaTareas.getColumnModel().getColumn(4).setCellRenderer(centerRenderer);

        tablaTareas.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                onTareaSeleccionada();
            }
        });

        JScrollPane scrollTabla = new JScrollPane(tablaTareas);
        scrollTabla.setBorder(BorderFactory.createTitledBorder("Tareas pendientes de 'Preparar materiales'"));
        panel.add(scrollTabla, BorderLayout.CENTER);

        // Reclamar / Completar
        JPanel panelAcciones = crearPanelAcciones();
        panel.add(panelAcciones, BorderLayout.SOUTH);

        return panel;
    }

    private JPanel crearPanelAcciones() {
        JPanel panelAcciones = new JPanel(new BorderLayout(10, 10));
        panelAcciones.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createEtchedBorder(), "Acciones de la Tarea Seleccionada",
                TitledBorder.LEFT, TitledBorder.TOP, new Font("SansSerif", Font.BOLD, 12)
        ));

        // Info de la tarea seleccionada
        JPanel panelInfo = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 5));
        lblTareaSeleccionada = new JLabel("Ninguna tarea seleccionada en la tabla.");
        lblTareaSeleccionada.setFont(new Font("SansSerif", Font.BOLD, 12));
        lblTareaSeleccionada.setForeground(Color.DARK_GRAY);
        panelInfo.add(lblTareaSeleccionada);
        panelAcciones.add(panelInfo, BorderLayout.NORTH);

        // Formulario y botones
        JPanel panelControles = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        // Botón reclamar
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.weightx = 0.0;
        btnReclamar = new JButton("✋ Reclamar Tarea");
        btnReclamar.setFont(new Font("SansSerif", Font.BOLD, 12));
        btnReclamar.setEnabled(false);
        btnReclamar.addActionListener(e -> reclamarTareaSeleccionada());
        panelControles.add(btnReclamar, gbc);

        // observaciones
        gbc.gridx = 1;
        gbc.gridy = 0;
        gbc.weightx = 0.0;
        JLabel lblObs = new JLabel("Observaciones de Materiales:");
        lblObs.setFont(new Font("SansSerif", Font.PLAIN, 12));
        panelControles.add(lblObs, gbc);

        gbc.gridx = 2;
        gbc.gridy = 0;
        gbc.weightx = 1.0;
        txtObservaciones = new JTextField("Materiales impresos y diapositivas listas por " + getUsuarioActual());
        txtObservaciones.setFont(new Font("SansSerif", Font.PLAIN, 12));
        panelControles.add(txtObservaciones, gbc);

        // Botón completar
        gbc.gridx = 3;
        gbc.gridy = 0;
        gbc.weightx = 0.0;
        btnCompletar = new JButton("✓ Completar Tarea");
        btnCompletar.setFont(new Font("SansSerif", Font.BOLD, 12));
        btnCompletar.setBackground(new Color(34, 139, 34));
        btnCompletar.setForeground(Color.WHITE);
        btnCompletar.setEnabled(false);
        btnCompletar.addActionListener(e -> completarTareaSeleccionada());
        panelControles.add(btnCompletar, gbc);

        panelAcciones.add(panelControles, BorderLayout.CENTER);

        return panelAcciones;
    }

    private JPanel crearPanelMetricas() {
        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setBorder(new EmptyBorder(10, 10, 10, 10));

        JPanel panelTop = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 5));
        JLabel lblIntro = new JLabel("Consultas y Métricas de Rendimiento Histórico (Sección 4.3 de las Especificaciones)");
        lblIntro.setFont(new Font("SansSerif", Font.BOLD, 13));
        panelTop.add(lblIntro);

        JButton btnActualizarMetricas = new JButton("📊 Actualizar Métricas");
        btnActualizarMetricas.setFont(new Font("SansSerif", Font.BOLD, 12));
        btnActualizarMetricas.addActionListener(e -> cargarMetricas());
        panelTop.add(btnActualizarMetricas);

        panel.add(panelTop, BorderLayout.NORTH);

        JPanel panelGrid = new JPanel(new GridLayout(1, 3, 10, 10));

        // Métrica 1
        txtMetricaResultados = new JTextArea();
        txtMetricaResultados.setEditable(false);
        txtMetricaResultados.setFont(new Font("Monospaced", Font.PLAIN, 12));
        JScrollPane sp1 = new JScrollPane(txtMetricaResultados);
        sp1.setBorder(BorderFactory.createTitledBorder("1. Instancias por Resultado (4.3.1)"));
        panelGrid.add(sp1);

        // Métrica 2
        txtMetricaDuracion = new JTextArea();
        txtMetricaDuracion.setEditable(false);
        txtMetricaDuracion.setFont(new Font("Monospaced", Font.PLAIN, 12));
        JScrollPane sp2 = new JScrollPane(txtMetricaDuracion);
        sp2.setBorder(BorderFactory.createTitledBorder("2. Duración Ramas Preparación (4.3.2)"));
        panelGrid.add(sp2);

        // Métrica 3
        txtMetricaUsuarios = new JTextArea();
        txtMetricaUsuarios.setEditable(false);
        txtMetricaUsuarios.setFont(new Font("Monospaced", Font.PLAIN, 12));
        JScrollPane sp3 = new JScrollPane(txtMetricaUsuarios);
        sp3.setBorder(BorderFactory.createTitledBorder("3. Tareas por Integrante (4.3.3)"));
        panelGrid.add(sp3);

        panel.add(panelGrid, BorderLayout.CENTER);

        // Cargar métricas al iniciar
        cargarMetricas();

        return panel;
    }

    // LÓGICA DE NEGOCIO Y  FLOWABLE API

    private String getUsuarioActual() {
        Object val = cmbUsuario != null ? cmbUsuario.getSelectedItem() : null;
        if (val == null || val.toString().trim().isEmpty()) {
            return "admin_martin";
        }
        return val.toString().trim();
    }

    private void onUsuarioCambiado() {
        String usuario = getUsuarioActual();
        lblUsuarioEstado.setText("✓ " + usuario + " (Grupo: Administración)");
        txtObservaciones.setText("Materiales impresos y diapositivas listas por " + usuario);
        aplicarFiltroTabla();
        onTareaSeleccionada();
    }

    public void refrescarTareas() {
        try {
            setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
            lblStatus.setText("Consultando tareas mediante Flowable TaskService...");

            // Preparar materiales
            List<Task> tareas = taskService.createTaskQuery()
                    .taskName("Preparar materiales")
                    .active()
                    .orderByTaskCreateTime().desc()
                    .list();

            if (tareas.isEmpty()) {
                tareas = taskService.createTaskQuery()
                        .taskDefinitionKey("prepararMaterialesTask")
                        .active()
                        .orderByTaskCreateTime().desc()
                        .list();
            }

            listaTareasActuales = tareas;
            aplicarFiltroTabla();

            lblStatus.setText(String.format("Consulta completada. Total de tareas 'Preparar materiales' en motor: %d", tareas.size()));
        } catch (Exception ex) {
            lblStatus.setText("Error al consultar tareas: " + ex.getMessage());
            JOptionPane.showMessageDialog(this, "Error al consultar tareas de Flowable:\n" + ex.getMessage(),
                    "Error de Conexión", JOptionPane.ERROR_MESSAGE);
        } finally {
            setCursor(Cursor.getDefaultCursor());
        }
    }

    private void aplicarFiltroTabla() {
        modeloTareas.setRowCount(0);
        String usuarioActual = getUsuarioActual();

        for (Task t : listaTareasActuales) {
            String assignee = t.getAssignee();
            boolean disponible = (assignee == null || assignee.trim().isEmpty());
            boolean esMia = usuarioActual.equalsIgnoreCase(assignee);

            if (rbDisponibles.isSelected() && !disponible) {
                continue;
            }
            if (rbMisTareas.isSelected() && !esMia) {
                continue;
            }

            String estadoDesc;
            if (disponible) {
                estadoDesc = "🟢 Disponible para reclamar";
            } else if (esMia) {
                estadoDesc = "Asignada a mí";
            } else {
                estadoDesc = "Asignada a " + assignee;
            }

            String businessKey = obtenerBusinessKey(t.getProcessInstanceId());
            String fecha = t.getCreateTime() != null ? DATE_FORMAT.format(t.getCreateTime()) : "-";

            modeloTareas.addRow(new Object[]{
                    t.getId(),
                    businessKey,
                    t.getProcessInstanceId(),
                    estadoDesc,
                    assignee != null ? assignee : "(Sin asignar)",
                    fecha
            });
        }

        onTareaSeleccionada();
    }

    private String obtenerBusinessKey(String processInstanceId) {
        if (processInstanceId == null) return "-";
        try {
            ProcessInstance pi = runtimeService.createProcessInstanceQuery()
                    .processInstanceId(processInstanceId)
                    .singleResult();
            if (pi != null && pi.getBusinessKey() != null) {
                return pi.getBusinessKey();
            }
        } catch (Exception ignored) {
        }
        return "ID-" + (processInstanceId.length() > 8 ? processInstanceId.substring(0, 8) : processInstanceId);
    }

    private Task getTaskSeleccionada() {
        int selectedRow = tablaTareas.getSelectedRow();
        if (selectedRow < 0) return null;

        String taskId = (String) modeloTareas.getValueAt(selectedRow, 0);
        return listaTareasActuales.stream()
                .filter(t -> t.getId().equals(taskId))
                .findFirst()
                .orElse(null);
    }

    private void onTareaSeleccionada() {
        Task t = getTaskSeleccionada();
        String usuario = getUsuarioActual();

        if (t == null) {
            lblTareaSeleccionada.setText("Ninguna tarea seleccionada en la tabla.");
            btnReclamar.setEnabled(false);
            btnCompletar.setEnabled(false);
            return;
        }

        String assignee = t.getAssignee();
        boolean disponible = (assignee == null || assignee.trim().isEmpty());
        boolean esMia = usuario.equalsIgnoreCase(assignee);

        lblTareaSeleccionada.setText(String.format("Tarea seleccionada: [%s] | Instancia: [%s] | Estado actual: %s",
                t.getId(), t.getProcessInstanceId(),
                disponible ? "DISPONIBLE" : ("ASIGNADA A " + assignee)));

        // Botón reclamar: habilitado si está disponible o asignada a otro (transferible en administración)
        btnReclamar.setEnabled(disponible || !esMia);
        btnReclamar.setText(disponible ? "Reclamar Tarea" : "Reasignar a " + usuario);

        // Botón completar: habilitado si está asignada a mí o si está disponible (la reclama automáticamente y completa)
        btnCompletar.setEnabled(true);
    }

    private void reclamarTareaSeleccionada() {
        Task t = getTaskSeleccionada();
        if (t == null) return;

        String usuario = getUsuarioActual();
        try {
            // Sección 4.2.1: Reclamar una tarea disponible para su grupo
            taskService.claim(t.getId(), usuario);

            lblStatus.setText(String.format("Tarea [%s] reclamada exitosamente por [%s]", t.getId(), usuario));
            JOptionPane.showMessageDialog(this,
                    String.format("La tarea [%s] ha sido reclamada con éxito por el usuario '%s'.\nAhora puedes proceder a completarla.", t.getId(), usuario),
                    "Tarea Reclamada", JOptionPane.INFORMATION_MESSAGE);

            refrescarTareas();
        } catch (Exception ex) {
            lblStatus.setText("Error al reclamar tarea: " + ex.getMessage());
            JOptionPane.showMessageDialog(this, "No se pudo reclamar la tarea:\n" + ex.getMessage(),
                    "Error al Reclamar", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void completarTareaSeleccionada() {
        Task t = getTaskSeleccionada();
        if (t == null) return;

        String usuario = getUsuarioActual();
        String obs = txtObservaciones.getText().trim();
        if (obs.isEmpty()) {
            obs = "Materiales didácticos preparados por " + usuario;
        }

        try {
            // Si no estaba asignada al usuario actual, reclamarla primero
            if (t.getAssignee() == null || !usuario.equalsIgnoreCase(t.getAssignee())) {
                taskService.claim(t.getId(), usuario);
            }

            // Sección 4.2.1: Completar la tarea
            Map<String, Object> variables = new HashMap<>();
            variables.put("materialesPreparados", true);
            variables.put("responsableMateriales", usuario);
            variables.put("observacionesMateriales", obs);

            taskService.complete(t.getId(), variables);

            lblStatus.setText(String.format("¡Tarea [%s] completada exitosamente por [%s]!", t.getId(), usuario));
            JOptionPane.showMessageDialog(this,
                    String.format("¡Tarea completada con éxito!\n\nID: %s\nResponsable: %s\nObservaciones: %s\n\nEl flujo de BPMN continuará hacia el Join de preparación.",
                            t.getId(), usuario, obs),
                    "Tarea Completada", JOptionPane.INFORMATION_MESSAGE);

            refrescarTareas();
            cargarMetricas();
        } catch (Exception ex) {
            lblStatus.setText("Error al completar tarea: " + ex.getMessage());
            JOptionPane.showMessageDialog(this, "No se pudo completar la tarea:\n" + ex.getMessage(),
                    "Error al Completar", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void cargarMetricas() {
        try {
            // Métrica 1: Instancias finalizadas por resultado
            List<HistoricProcessInstance> instancias = historyService.createHistoricProcessInstanceQuery()
                    .processDefinitionKey(AppPrepararMateriales.PROCESS_KEY)
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

            StringBuilder sb1 = new StringBuilder();
            sb1.append("Total Instancias Finalizadas: ").append(total).append("\n\n");
            sb1.append(String.format("%-22s | %-5s | %-6s%n", "Resultado", "Cant", "%"));
            sb1.append("---------------------------------------\n");
            for (Map.Entry<String, Long> entry : conteo.entrySet()) {
                double pct = total > 0 ? (entry.getValue() * 100.0) / total : 0.0;
                sb1.append(String.format("%-22s | %-5d | %5.1f%%%n", entry.getKey(), entry.getValue(), pct));
            }
            txtMetricaResultados.setText(sb1.toString());

            // Métrica 2: Duración promedio de ramas de preparación
            List<HistoricProcessInstance> validas = instancias.stream()
                    .filter(pi -> {
                        String res = (String) pi.getProcessVariables().get("resultado");
                        return res == null || !"cancelado".equalsIgnoreCase(res.trim());
                    }).toList();

            long sumInsc = 0, countInsc = 0;
            long sumMat = 0, countMat = 0;
            long sumRec = 0, countRec = 0;

            for (HistoricProcessInstance pi : validas) {
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

            StringBuilder sb2 = new StringBuilder();
            sb2.append("Cursos analizados (no cancelados): ").append(validas.size()).append("\n\n");
            sb2.append(String.format("%-20s | %-10s | %-4s%n", "Rama", "Promedio", "Muestras"));
            sb2.append("---------------------------------------\n");
            sb2.append(String.format("%-20s | %8.2f s | %-4d%n", "1. Inscripciones", countInsc > 0 ? (sumInsc / countInsc) / 1000.0 : 0.0, countInsc));
            sb2.append(String.format("%-20s | %8.2f s | %-4d%n", "2. Materiales", countMat > 0 ? (sumMat / countMat) / 1000.0 : 0.0, countMat));
            sb2.append(String.format("%-20s | %8.2f s | %-4d%n", "3. Recursos", countRec > 0 ? (sumRec / countRec) / 1000.0 : 0.0, countRec));
            txtMetricaDuracion.setText(sb2.toString());

            // Métrica 3
            List<HistoricTaskInstance> tareasHist = historyService.createHistoricTaskInstanceQuery()
                    .processDefinitionKey(AppPrepararMateriales.PROCESS_KEY)
                    .finished()
                    .list();

            Map<String, Long> porUsuario = new LinkedHashMap<>();
            porUsuario.put("admin_laura", 0L);
            porUsuario.put("admin_martin", 0L);
            porUsuario.put("docente_carlos", 0L);
            porUsuario.put("docente_ana", 0L);
            porUsuario.put("docente_pedro", 0L);

            for (HistoricTaskInstance ht : tareasHist) {
                String ass = ht.getAssignee();
                if (ass != null) {
                    porUsuario.put(ass, porUsuario.getOrDefault(ass, 0L) + 1);
                }
            }

            StringBuilder sb3 = new StringBuilder();
            sb3.append("Total tareas humanas completadas: ").append(tareasHist.size()).append("\n\n");
            sb3.append(String.format("%-15s | %-12s | %-5s%n", "Usuario", "Rol", "Tareas"));
            sb3.append("---------------------------------------\n");
            for (Map.Entry<String, Long> entry : porUsuario.entrySet()) {
                String rol = entry.getKey().startsWith("admin") ? "Administración" : "Docente";
                sb3.append(String.format("%-15s | %-12s | %-5d%n", entry.getKey(), rol, entry.getValue()));
            }
            txtMetricaUsuarios.setText(sb3.toString());

        } catch (Exception ex) {
            txtMetricaResultados.setText("Error cargando métricas: " + ex.getMessage());
            txtMetricaDuracion.setText("Error cargando métricas: " + ex.getMessage());
            txtMetricaUsuarios.setText("Error cargando métricas: " + ex.getMessage());
        }
    }

    private void cerrarAplicacion() {
        int resp = JOptionPane.showConfirmDialog(this,
                "¿Desea cerrar la aplicación de Preparación de Materiales?",
                "Confirmar Salida", JOptionPane.YES_NO_OPTION);
        if (resp == JOptionPane.YES_OPTION) {
            try {
                if (processEngine != null) {
                    processEngine.close();
                }
            } catch (Exception ignored) {
            }
            dispose();
            System.exit(0);
        }
    }
}
