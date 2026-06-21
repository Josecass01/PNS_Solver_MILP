package com.udecartagena.pgraph;

import org.jfree.chart.ChartFactory;
import org.jfree.chart.ChartPanel;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.plot.PlotOrientation;
import org.jfree.data.category.DefaultCategoryDataset;

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

    // Componentes del Front-End
    private JTextField txtRutaArchivo;
    private JButton btnCargar;
    private JComboBox<String> comboSolvers;
    private JButton btnOptimizar;
    private JTextArea areaResultados;
    private JTable tablaAnalitica;
    private DefaultTableModel modeloTabla;
    private JLabel lblTotalSoluciones;

    private PGraph modeloActual = null;
    private final String ARCHIVO_JSON = "analitica_solvers.json";

    // Lista para guardar los resultados de la última corrida en memoria para los gráficos
    private List<ResultadoSolucion> ultimaCorrida = new ArrayList<>();

    public VentanaPrincipal() {
        setTitle("PNS Solver MILP - Universidad de Cartagena");
        setSize(950, 680);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);
        setLayout(new BorderLayout(10, 10));

        inicializarComponentes();
        actualizarTablaHistorial();
    }

    private void inicializarComponentes() {
        // PANEL SUPERIOR
        JPanel panelSuperior = new JPanel(new GridBagLayout());
        panelSuperior.setBorder(BorderFactory.createTitledBorder("Configuración del Problema"));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.fill = GridBagConstraints.HORIZONTAL;

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

        gbc.gridx = 0; gbc.gridy = 1; gbc.weightx = 0.0;
        panelSuperior.add(new JLabel("Seleccionar Solver MILP:"), gbc);

        gbc.gridx = 1; gbc.gridy = 1; gbc.weightx = 1.0;
        String[] opcionesSolvers = {
                "Apache Commons Math (Interno B&B)",
                "HiGHS (Ejecutable portable)",
                "GLPK (Ejecutable portable)",
                "Gurobi (Motor Comercial Avanzado)",
                "Ejecutar TODOS en lote (Comparativa)"
        };
        comboSolvers = new JComboBox<>(opcionesSolvers);
        panelSuperior.add(comboSolvers, gbc);

        gbc.gridx = 2; gbc.gridy = 1; gbc.weightx = 0.0;
        btnOptimizar = new JButton("Ejecutar Optimización");
        btnOptimizar.setEnabled(false);
        btnOptimizar.addActionListener(e -> accionEjecutarSolver());
        panelSuperior.add(btnOptimizar, gbc);

        add(panelSuperior, BorderLayout.NORTH);

        // PANEL CENTRAL
        JPanel panelCentral = new JPanel(new GridLayout(2, 1, 10, 10));
        panelCentral.setBorder(BorderFactory.createEmptyBorder(0, 10, 0, 10));

        JPanel panelResultadosActuales = new JPanel(new BorderLayout());
        panelResultadosActuales.setBorder(BorderFactory.createTitledBorder("Resultados de la Solución Actual (Variables Activas)"));
        areaResultados = new JTextArea();
        areaResultados.setEditable(false);
        areaResultados.setFont(new Font("Monospaced", Font.PLAIN, 12));
        panelResultadosActuales.add(new JScrollPane(areaResultados), BorderLayout.CENTER);
        panelCentral.add(panelResultadosActuales);

        JPanel panelComparacion = new JPanel(new BorderLayout());
        panelComparacion.setBorder(BorderFactory.createTitledBorder("Analítica Comparativa Histórica (Respaldo JSON Estructurado)"));

        String[] columnas = {"Solver Utilizado", "Factible", "Costo Óptimo (Z)", "Tiempo (ms)"};
        modeloTabla = new DefaultTableModel(columnas, 0);
        tablaAnalitica = new JTable(modeloTabla);
        panelComparacion.add(new JScrollPane(tablaAnalitica), BorderLayout.CENTER);

        panelCentral.add(panelComparacion);
        add(panelCentral, BorderLayout.CENTER);

        // PANEL INFERIOR (Con los botones de gráficos y árbol)
        JPanel panelInferior = new JPanel(new BorderLayout());
        panelInferior.setBorder(BorderFactory.createEmptyBorder(5, 10, 10, 10));

        lblTotalSoluciones = new JLabel("Cantidad de soluciones almacenadas en el historial: 0");
        panelInferior.add(lblTotalSoluciones, BorderLayout.WEST);

        // Botón de Gráfico de Barras
        JButton btnGrafico = new JButton("Visualizar Gráfico de Tiempos");
        btnGrafico.setFont(new Font("SansSerif", Font.BOLD, 12));
        btnGrafico.addActionListener(e -> mostrarGraficoBarras());

        // ---> BOTÓN DEL ÁRBOL DE DECISIONES CON EL NOMBRE DEL SOLVER DINÁMICO <---
        JButton btnArbol = new JButton("Explorar Árbol de Decisiones (Rutas)");
        btnArbol.setFont(new Font("SansSerif", Font.BOLD, 12));
        btnArbol.addActionListener(e -> {
            if (modeloActual == null) {
                JOptionPane.showMessageDialog(this, "Carga un modelo .txt primero.");
            } else {
                // Captura dinámicamente el nombre del motor seleccionado en el ComboBox
                String solverSeleccionado = comboSolvers.getSelectedItem().toString();
                // Lo envía a la clase VisorGrafo
                VisorGrafo.mostrar(this, modeloActual, solverSeleccionado);
            }
        });

        // Sub-panel para agrupar los botones a la derecha
        JPanel panelBotonesDer = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        panelBotonesDer.add(btnArbol);
        panelBotonesDer.add(btnGrafico);

        panelInferior.add(panelBotonesDer, BorderLayout.EAST);

        add(panelInferior, BorderLayout.SOUTH);
    }

    private void accionCargarArchivo() {
        JFileChooser selector = new JFileChooser();
        selector.setDialogTitle("Seleccione el archivo del modelo P-Graph");
        selector.setCurrentDirectory(new File("."));

        int resultado = selector.showOpenDialog(this);
        if (resultado == JFileChooser.APPROVE_OPTION) {
            File archivoSeleccionado = selector.getSelectedFile();
            try {
                modeloActual = LectorPGraph.leerArchivo(archivoSeleccionado.getAbsolutePath());
                txtRutaArchivo.setText(archivoSeleccionado.getName());
                areaResultados.setText("Modelo topológico cargado con éxito.\n" +
                        "Materiales detectados: " + modeloActual.getMateriales().size() + "\n" +
                        "Unidades operativas: " + modeloActual.getUnidadesOperativas().size() + "\n\n" +
                        "Selecciona un solver y presiona 'Ejecutar Optimización'.");
                btnOptimizar.setEnabled(true);
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, "Error al cargar:\n" + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private void accionEjecutarSolver() {
        if (modeloActual == null) return;

        int indiceSolver = comboSolvers.getSelectedIndex();
        List<SolverMILP> solversAEjecutar = new ArrayList<>();

        if (indiceSolver == 0) solversAEjecutar.add(new SolverApacheMath(modeloActual));
        else if (indiceSolver == 1) solversAEjecutar.add(new SolverHiGHS(modeloActual));
        else if (indiceSolver == 2) solversAEjecutar.add(new SolverGLPK(modeloActual));
        else if (indiceSolver == 3) solversAEjecutar.add(new SolverGurobi(modeloActual));
        else if (indiceSolver == 4) {
            solversAEjecutar.add(new SolverApacheMath(modeloActual));
            solversAEjecutar.add(new SolverHiGHS(modeloActual));
            solversAEjecutar.add(new SolverGLPK(modeloActual));
            solversAEjecutar.add(new SolverGurobi(modeloActual));
        }

        areaResultados.setText("Optimizando modelo matemático... Por favor espere.\n");
        List<ResultadoSolucion> resultadosCorrida = new ArrayList<>();

        for (SolverMILP solver : solversAEjecutar) {
            resultadosCorrida.add(solver.resolver());
        }

        // GUARDAR LA ÚLTIMA CORRIDA EN MEMORIA
        ultimaCorrida = new ArrayList<>(resultadosCorrida);

        ServicioPersistencia.guardarMetricasJSON(ARCHIVO_JSON, resultadosCorrida);

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
                sb.append("Variables Activas:\n");
                for (Map.Entry<String, Double> entry : r.getVariablesActivas().entrySet()) {
                    if (entry.getValue() > 0.0001) {
                        sb.append(String.format("  -> %s = %.4f%n", entry.getKey(), entry.getValue()));
                    }
                }
            }
            sb.append("--------------------------------------------------\n");
        }
        areaResultados.setText(sb.toString());
        actualizarTablaHistorial();
    }

    private void actualizarTablaHistorial() {
        File archivo = new File(ARCHIVO_JSON);
        if (!archivo.exists()) return;

        modeloTabla.setRowCount(0);
        int contadorSoluciones = 0;

        try (BufferedReader br = new BufferedReader(new FileReader(archivo))) {
            String linea, solver = "", factible = "", costo = "", tiempo = "";

            while ((linea = br.readLine()) != null) {
                linea = linea.trim();
                if (linea.contains("\"solver\":")) solver = extraerValorClave(linea);
                else if (linea.contains("\"factible\":")) factible = extraerValorClave(linea);
                else if (linea.contains("\"costo_optimo\":")) costo = extraerValorClave(linea);
                else if (linea.contains("\"tiempo_ms\":")) tiempo = extraerValorClave(linea);
                else if (linea.startsWith("}") || linea.startsWith("},")) {
                    if (!solver.isEmpty()) {
                        modeloTabla.addRow(new Object[]{solver, factible, costo, tiempo});
                        contadorSoluciones++;
                        solver = ""; factible = ""; costo = ""; tiempo = "";
                    }
                }
            }
            lblTotalSoluciones.setText("Soluciones en el historial (JSON): " + contadorSoluciones);
        } catch (Exception ignored) {}
    }

    private String extraerValorClave(String linea) {
        String[] partes = linea.split(":");
        if (partes.length >= 2) {
            String valor = partes[1].trim();
            if (valor.endsWith(",")) valor = valor.substring(0, valor.length() - 1).trim();
            if (valor.startsWith("\"") && valor.endsWith("\"")) valor = valor.substring(1, valor.length() - 1);
            return valor;
        }
        return "";
    }

    // =========================================================================
    // ANALÍTICA VISUAL CON JFREECHART (Gráfico de Barras con Pestañas)
    // =========================================================================
    private void mostrarGraficoBarras() {
        if (ultimaCorrida == null || ultimaCorrida.isEmpty()) {
            JOptionPane.showMessageDialog(this,
                    "Ejecuta una optimización primero para generar datos.",
                    "Sin datos", JOptionPane.WARNING_MESSAGE);
            return;
        }

        DefaultCategoryDataset datasetTiempos = new DefaultCategoryDataset();
        DefaultCategoryDataset datasetCostos = new DefaultCategoryDataset();

        for (ResultadoSolucion r : ultimaCorrida) {
            String nombre = r.getNombreSolver().split(" ")[0];
            datasetTiempos.addValue(r.getTiempoEjecucionMs(), "Tiempo (ms)", nombre);

            if (r.isFactible()) {
                datasetCostos.addValue(r.getCostoOptimo(), "Costo Z", nombre);
            }
        }

        JFreeChart chartTiempos = ChartFactory.createBarChart(
                "Comparativa de Tiempos de Ejecución",
                "Motor Matemático", "Tiempo (ms)", datasetTiempos,
                PlotOrientation.VERTICAL, true, true, false);

        JFreeChart chartCostos = ChartFactory.createBarChart(
                "Comparativa de Valor Objetivo Z",
                "Motor Matemático", "Costo Óptimo (Z)", datasetCostos,
                PlotOrientation.VERTICAL, true, true, false);

        JDialog dialog = new JDialog(this, "Analítica Visual - Resultados", true);
        dialog.setSize(750, 500);
        dialog.setLocationRelativeTo(this);

        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab("Tiempos de Ejecución", new ChartPanel(chartTiempos));
        tabs.addTab("Valor Objetivo (Z)", new ChartPanel(chartCostos));

        dialog.add(tabs);
        dialog.setVisible(true);
    }
}