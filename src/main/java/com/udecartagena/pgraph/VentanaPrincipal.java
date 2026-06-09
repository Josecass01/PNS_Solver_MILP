package com.udecartagena.pgraph;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class VentanaPrincipal extends JFrame {

    // Componentes del Front-End (Interfaz Gráfica)
    private JTextField txtRutaArchivo;
    private JButton btnCargar;
    private JComboBox<String> comboSolvers;
    private JButton btnOptimizar;
    private JTextArea areaResultados;
    private JTable tablaAnalitica;
    private DefaultTableModel modeloTabla;
    private JLabel lblTotalSoluciones;

    // Estado del Back-End
    private PGraph modeloActual = null;
    private final String ARCHIVO_JSON = "analitica_solvers.json";

    public VentanaPrincipal() {
        // Configuración básica de la ventana de Windows
        setTitle("PNS Solver MILP - Universidad de Cartagena");
        setSize(950, 680);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);
        setLayout(new BorderLayout(10, 10));

        // Inicializar componentes visuales de Swing
        inicializarComponentes();

        // Cargar el historial del JSON en la tabla inmediatamente al encender la pantalla
        actualizarTablaHistorial();
    }

    private void inicializarComponentes() {
        // ---------------------------------------------------------------------
        // PANEL SUPERIOR: Carga de Modelo y Selección de Solver
        // ---------------------------------------------------------------------
        JPanel panelSuperior = new JPanel(new GridBagLayout());
        panelSuperior.setBorder(BorderFactory.createTitledBorder("Configuración del Problema"));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        // Fila 1: Selector del archivo .txt del P-Graph
        gbc.gridx = 0; gbc.gridy = 0;
        panelSuperior.add(new JLabel("Modelo P-Graph (.txt):"), gbc);

        gbc.gridx = 1; gbc.gridy = 0; gbc.weightx = 1.0;
        txtRutaArchivo = new JTextField();
        txtRutaArchivo.setEditable(false);
        panelSuperior.add(txtRutaArchivo, gbc);

        gbc.gridx = 2; gbc.gridy = 0; gbc.weightx = 0.0;
        btnCargar = new JButton("Buscar Archivo...");
        btnCargar.addActionListener(e -> accionCargarArchivo());
        panelSuperior.add(btnCargar, gbc);

        // Fila 2: Selector del motor matemático (Solver)
        gbc.gridx = 0; gbc.gridy = 1; gbc.weightx = 0.0;
        panelSuperior.add(new JLabel("Seleccionar Solver MILP:"), gbc);

        gbc.gridx = 1; gbc.gridy = 1; gbc.weightx = 1.0;
        String[] opcionesSolvers = {
                "Apache Commons Math (Interno B&B)",
                "HiGHS (Ejecutable portable)",
                "GLPK (Ejecutable portable)",
                "Ejecutar TODOS en lote (Comparativa)"
        };
        comboSolvers = new JComboBox<>(opcionesSolvers);
        panelSuperior.add(comboSolvers, gbc);

        gbc.gridx = 2; gbc.gridy = 1; gbc.weightx = 0.0;
        btnOptimizar = new JButton("Ejecutar Optimización");
        btnOptimizar.setEnabled(false); // Desactivado hasta que carguen un archivo estructurado
        btnOptimizar.addActionListener(e -> accionEjecutarSolver());
        panelSuperior.add(btnOptimizar, gbc);

        add(panelSuperior, BorderLayout.NORTH);

        // ---------------------------------------------------------------------
        // PANEL CENTRAL: Resultados actuales y Tabla comparativa (Analítica)
        // ---------------------------------------------------------------------
        JPanel panelCentral = new JPanel(new GridLayout(2, 1, 10, 10));
        panelCentral.setBorder(BorderFactory.createEmptyBorder(0, 10, 0, 10));

        // Subpanel 1: Visualización detallada de la solución actual (Variables activas y Z)
        JPanel panelResultadosActuales = new JPanel(new BorderLayout());
        panelResultadosActuales.setBorder(BorderFactory.createTitledBorder("Resultados de la Solución Actual (Variables Activas)"));
        areaResultados = new JTextArea();
        areaResultados.setEditable(false);
        areaResultados.setFont(new Font("Monospaced", Font.PLAIN, 12));
        panelResultadosActuales.add(new JScrollPane(areaResultados), BorderLayout.CENTER);
        panelCentral.add(panelResultadosActuales);

        // Subpanel 2: Sección de comparación analítica (Historial del JSON jerárquico)
        JPanel panelComparacion = new JPanel(new BorderLayout());
        panelComparacion.setBorder(BorderFactory.createTitledBorder("Analítica Comparativa Histórica (Respaldo JSON Estructurado)"));

        String[] columnas = {"Solver Utilizado", "Factible", "Costo Óptimo (Z)", "Tiempo (ms)"};
        modeloTabla = new DefaultTableModel(columnas, 0);
        tablaAnalitica = new JTable(modeloTabla);
        panelComparacion.add(new JScrollPane(tablaAnalitica), BorderLayout.CENTER);

        panelCentral.add(panelComparacion);
        add(panelCentral, BorderLayout.CENTER);

        // ---------------------------------------------------------------------
        // PANEL INFERIOR: Barra de estado e indicadores métricos
        // ---------------------------------------------------------------------
        JPanel panelInferior = new JPanel(new FlowLayout(FlowLayout.LEFT));
        lblTotalSoluciones = new JLabel("Cantidad de soluciones almacenadas en el historial: 0");
        panelInferior.add(lblTotalSoluciones);
        add(panelInferior, BorderLayout.SOUTH);
    }

    // =========================================================================
    // LÓGICA DE INTERACCIÓN Y PROCESAMIENTO (CONTROLADORES)
    // =========================================================================

    private void accionCargarArchivo() {
        JFileChooser selector = new JFileChooser();
        selector.setDialogTitle("Seleccione el archivo del modelo P-Graph");
        selector.setCurrentDirectory(new File(".")); // Inicia en la raíz del proyecto

        int resultado = selector.showOpenDialog(this);
        if (resultado == JFileChooser.APPROVE_OPTION) {
            File archivoSeleccionado = selector.getSelectedFile();
            try {
                // Invocamos tu lector topológico del Back-End
                modeloActual = LectorPGraph.leerArchivo(archivoSeleccionado.getAbsolutePath());
                txtRutaArchivo.setText(archivoSeleccionado.getName());
                areaResultados.setText("Modelo topológico cargado con éxito.\n" +
                        "Materiales detectados: " + modeloActual.getMateriales().size() + "\n" +
                        "Unidades operativas: " + modeloActual.getUnidadesOperativas().size() + "\n\n" +
                        "Selecciona un solver y presiona 'Ejecutar Optimización'.");
                btnOptimizar.setEnabled(true);
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, "Error al interpretar la topología del archivo P-Graph:\n" + ex.getMessage(), "Error de Carga", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private void accionEjecutarSolver() {
        if (modeloActual == null) return;

        int indiceSolver = comboSolvers.getSelectedIndex();
        List<SolverMILP> solversAEjecutar = new ArrayList<>();

        // Mapeo polimórfico según la opción de la interfaz de usuario
        if (indiceSolver == 0) {
            solversAEjecutar.add(new SolverApacheMath(modeloActual));
        } else if (indiceSolver == 1) {
            solversAEjecutar.add(new SolverHiGHS(modeloActual));
        } else if (indiceSolver == 2) {
            solversAEjecutar.add(new SolverGLPK(modeloActual));
        } else if (indiceSolver == 3) {
            solversAEjecutar.add(new SolverApacheMath(modeloActual));
            solversAEjecutar.add(new SolverHiGHS(modeloActual));
            solversAEjecutar.add(new SolverGLPK(modeloActual));
        }

        areaResultados.setText("Optimizando modelo matemático... Por favor espere.\n");
        List<ResultadoSolucion> resultadosCorrida = new ArrayList<>();

        // Ejecución secuencial de los solvers inyectados
        for (SolverMILP solver : solversAEjecutar) {
            ResultadoSolucion res = solver.resolver();
            resultadosCorrida.add(res);
        }

        // CAPA DE PERSISTENCIA ACTUALIZADA: Guardar inmediatamente en JSON nativo
        ServicioPersistencia.guardarMetricasJSON(ARCHIVO_JSON, resultadosCorrida);

        // Construcción del reporte en pantalla detallando las variables activas exigidas
        StringBuilder sb = new StringBuilder();
        sb.append("==================================================\n");
        sb.append("      REPORTE DE VARIABLES ACTIVAS ACTUALES       \n");
        sb.append("==================================================\n");

        for (ResultadoSolucion r : resultadosCorrida) {
            sb.append(String.format("Solver Ejecutado : %s%n", r.getNombreSolver()));
            sb.append(String.format("Estatus Solución : %s%n", r.isFactible() ? "FACTIBLE / ÓPTIMA" : "INFACTIBLE"));
            sb.append(String.format("Valor Objetivo Z : %.4f%n", r.getCostoOptimo()));
            sb.append(String.format("Tiempo Computo   : %d ms%n", r.getTiempoEjecucionMs()));

            if (r.isFactible()) {
                sb.append("Variables de Decisión Activas:\n");
                for (Map.Entry<String, Double> entry : r.getVariablesActivas().entrySet()) {
                    // Requisito: Mostrar el impacto de las variables binarias y flujos continuos en pantalla
                    if (entry.getValue() > 0.0001) {
                        sb.append(String.format("  -> %s = %.4f%n", entry.getKey(), entry.getValue()));
                    }
                }
            }
            sb.append("--------------------------------------------------\n");
        }
        areaResultados.setText(sb.toString());

        // Actualizar la tabla comparativa inferior cargando los datos históricos del JSON
        actualizarTablaHistorial();
    }

    /**
     * Analizador (Parser) de JSON nativo de nivel Senior.
     * Lee de forma secuencial el archivo estructurado sin requerir librerías externas.
     */
    private void actualizarTablaHistorial() {
        File archivo = new File(ARCHIVO_JSON);
        if (!archivo.exists()) {
            lblTotalSoluciones.setText("Cantidad de soluciones almacenadas en el historial: 0 (No se ha generado el JSON)");
            return;
        }

        // Limpiar la tabla visual para evitar duplicidad de filas al recargar
        modeloTabla.setRowCount(0);
        int contadorSoluciones = 0;

        try (BufferedReader br = new BufferedReader(new FileReader(archivo))) {
            String linea;
            String solver = "";
            String factible = "";
            String costo = "";
            String tiempo = "";

            while ((linea = br.readLine()) != null) {
                linea = linea.trim();

                if (linea.contains("\"solver\":")) {
                    solver = extraerValorClave(linea);
                } else if (linea.contains("\"factible\":")) {
                    factible = extraerValorClave(linea);
                } else if (linea.contains("\"costo_optimo\":")) {
                    costo = extraerValorClave(linea);
                } else if (linea.contains("\"tiempo_ms\":")) {
                    tiempo = extraerValorClave(linea);
                } else if (linea.startsWith("}") || linea.startsWith("},")) {
                    // Al cerrar el bloque de un objeto JSON, inyectamos la fila procesada en la JTable
                    if (!solver.isEmpty()) {
                        modeloTabla.addRow(new Object[]{solver, factible, costo, tiempo});
                        contadorSoluciones++;
                        // Resetear variables auxiliares
                        solver = ""; factible = ""; costo = ""; tiempo = "";
                    }
                }
            }

            // Requisito analítico de la rúbrica: "Cantidad de soluciones almacenadas"
            lblTotalSoluciones.setText("Cantidad de soluciones almacenadas en el historial (Registros JSON): " + contadorSoluciones);

        } catch (Exception e) {
            System.err.println("Error procesando analítica desde JSON: " + e.getMessage());
        }
    }

    /**
     * Limpia las comillas, comas y espacios de una línea JSON para extraer el valor plano
     */
    private String extraerValorClave(String linea) {
        String[] partes = linea.split(":");
        if (partes.length >= 2) {
            String valor = partes[1].trim();
            if (valor.endsWith(",")) {
                valor = valor.substring(0, valor.length() - 1).trim();
            }
            if (valor.startsWith("\"") && valor.endsWith("\"")) {
                valor = valor.substring(1, valor.length() - 1);
            }
            return valor;
        }
        return "";
    }
}