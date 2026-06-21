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

        // Sub-panel para el contador y el botón de limpiar historial
        JPanel panelInferiorIzq = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        lblTotalSoluciones = new JLabel("Cantidad de soluciones almacenadas en el historial: 0");
        JButton btnLimpiar = new JButton("🗑️ Limpiar Historial");
        btnLimpiar.setFont(new Font("SansSerif", Font.PLAIN, 12));
        btnLimpiar.setMargin(new Insets(2, 5, 2, 5));
        btnLimpiar.addActionListener(e -> limpiarHistorial());

        panelInferiorIzq.add(lblTotalSoluciones);
        panelInferiorIzq.add(Box.createHorizontalStrut(15)); // Espacio separador
        panelInferiorIzq.add(btnLimpiar);

        panelInferior.add(panelInferiorIzq, BorderLayout.WEST);

        // Botón de Gráfico de Barras
        JButton btnGrafico = new JButton("Visualizar Gráfico de Tiempos");
        btnGrafico.setFont(new Font("SansSerif", Font.BOLD, 12));
        btnGrafico.addActionListener(e -> mostrarGraficoBarras());

        // Botón del Árbol de Decisiones (Dinámico)
        JButton btnArbol = new JButton("Explorar Árbol de Decisiones (Rutas)");
        btnArbol.setFont(new Font("SansSerif", Font.BOLD, 12));
        btnArbol.addActionListener(e -> {
            if (modeloActual == null) {
                JOptionPane.showMessageDialog(this, "Carga un modelo .txt primero.");
            } else {
                String solverSeleccionado = comboSolvers.getSelectedItem().toString();
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
        StringBuilder sbErrores = new StringBuilder(); // Para capturar si algún solver falla

        // === ARQUITECTURA TOLERANTE A FALLOS (FAULT TOLERANCE) ===
        for (SolverMILP solver : solversAEjecutar) {
            try {
                // Intenta ejecutar el solver
                resultadosCorrida.add(solver.resolver());
            } catch (Throwable ex) {
                // Atrapamos Throwable para interceptar problemas de licencias vencidas, DLLs faltantes o ejecutables rotos.
                // Esto asegura que el programa NUNCA se congele y siga con el próximo solver disponible.
                String nombreSolver = solver.getClass().getSimpleName().replace("Solver", "");
                sbErrores.append(" -> ").append(nombreSolver).append(" falló: ").append(ex.getMessage()).append("\n");
            }
        }

        // GUARDAR LA ÚLTIMA CORRIDA EN MEMORIA PARA LAS GRÁFICAS
        ultimaCorrida = new ArrayList<>(resultadosCorrida);

        if (!resultadosCorrida.isEmpty()) {
            ServicioPersistencia.guardarMetricasJSON(ARCHIVO_JSON, resultadosCorrida);
        }

        StringBuilder sb = new StringBuilder();

        // Si hubo errores (ej: falta licencia de Gurobi), informamos en el área de texto sin crashear
        if (sbErrores.length() > 0) {
            sb.append("==================================================\n");
            sb.append("   ⚠️ SOLVERS OMITIDOS (ERROR DE LICENCIA / DLL)  \n");
            sb.append("==================================================\n");
            sb.append(sbErrores.toString());
            sb.append("--------------------------------------------------\n\n");
        }

        sb.append("==================================================\n");
        sb.append("      REPORTE DE VARIABLES ACTIVAS ACTUALES       \n");
        sb.append("==================================================\n");

        if (resultadosCorrida.isEmpty()) {
            sb.append("Ningún motor matemático pudo completarse con éxito.\n");
            sb.append("Verifique los requerimientos técnicos de los solvers.\n");
        } else {
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
                        // Formateo visual para que la tabla luzca profesional
                        String factibleFormat = factible.equalsIgnoreCase("true") ? "Sí" : "No";
                        String costoFormat = factible.equalsIgnoreCase("true") ? costo : "N/A (Infactible)";

                        modeloTabla.addRow(new Object[]{solver, factibleFormat, costoFormat, tiempo});
                        contadorSoluciones++;
                        solver = ""; factible = ""; costo = ""; tiempo = "";
                    }
                }
            }
            lblTotalSoluciones.setText("Cantidad de soluciones almacenadas en el historial: " + contadorSoluciones);
        } catch (Exception ignored) {}

        // Centrar el texto en las columnas para mejor estética
        javax.swing.table.DefaultTableCellRenderer centerRenderer = new javax.swing.table.DefaultTableCellRenderer();
        centerRenderer.setHorizontalAlignment(JLabel.CENTER);
        for(int i = 1; i < 4; i++) {
            tablaAnalitica.getColumnModel().getColumn(i).setCellRenderer(centerRenderer);
        }
    }

    private void limpiarHistorial() {
        int confirm = JOptionPane.showConfirmDialog(this,
                "¿Estás seguro de que deseas borrar todo el historial de soluciones?",
                "Limpiar Historial", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
        if (confirm == JOptionPane.YES_OPTION) {
            File archivo = new File(ARCHIVO_JSON);
            if (archivo.exists()) {
                archivo.delete();
            }
            modeloTabla.setRowCount(0);
            lblTotalSoluciones.setText("Cantidad de soluciones almacenadas en el historial: 0");
            ultimaCorrida.clear();
            JOptionPane.showMessageDialog(this, "Historial limpiado correctamente.");
        }
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
                    "Ejecuta una optimización exitosa primero para generar datos.\n" +
                            "(Si hubo errores de licencia, resuelve el problema o elige otro motor).",
                    "Sin datos para graficar", JOptionPane.WARNING_MESSAGE);
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

        // --- APLICAR ESTILO MODERNO Y PROFESIONAL ---
        aplicarEstiloGrafico(chartTiempos, new Color(41, 128, 185)); // Azul elegante para tiempos
        aplicarEstiloGrafico(chartCostos, new Color(39, 174, 96));   // Verde esmeralda para costos

        JDialog dialog = new JDialog(this, "Analítica Visual - Resultados", true);
        dialog.setSize(850, 550);
        dialog.setLocationRelativeTo(this);

        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab("Tiempos de Ejecución", new ChartPanel(chartTiempos));
        tabs.addTab("Valor Objetivo (Z)", new ChartPanel(chartCostos));

        dialog.add(tabs);
        dialog.setVisible(true);
    }

    // --- NUEVO MÉTODO: Formateo estético del gráfico ---
    private void aplicarEstiloGrafico(JFreeChart chart, Color colorPrincipal) {
        // Fondo blanco general
        chart.setBackgroundPaint(Color.WHITE);
        chart.getTitle().setFont(new Font("SansSerif", Font.BOLD, 18));

        org.jfree.chart.plot.CategoryPlot plot = chart.getCategoryPlot();
        plot.setBackgroundPaint(Color.WHITE); // Fondo blanco interior
        plot.setDomainGridlinePaint(new Color(230, 230, 230)); // Rejilla suave
        plot.setRangeGridlinePaint(new Color(230, 230, 230));
        plot.setOutlineVisible(false); // Quitar borde perimetral feo

        org.jfree.chart.renderer.category.BarRenderer renderer = (org.jfree.chart.renderer.category.BarRenderer) plot.getRenderer();

        // ¡LA MAGIA AQUÍ!: Quitar el gradiente 3D anticuado y hacerlo Flat Design
        renderer.setBarPainter(new org.jfree.chart.renderer.category.StandardBarPainter());

        // Asignar el color sólido profesional
        renderer.setSeriesPaint(0, colorPrincipal);

        // Ajustar el ancho de las barras
        renderer.setItemMargin(0.1);

        // Fuentes modernas para los ejes
        plot.getDomainAxis().setLabelFont(new Font("SansSerif", Font.BOLD, 14));
        plot.getDomainAxis().setTickLabelFont(new Font("SansSerif", Font.PLAIN, 12));
        plot.getRangeAxis().setLabelFont(new Font("SansSerif", Font.BOLD, 14));
        plot.getRangeAxis().setTickLabelFont(new Font("SansSerif", Font.PLAIN, 12));
    }
}