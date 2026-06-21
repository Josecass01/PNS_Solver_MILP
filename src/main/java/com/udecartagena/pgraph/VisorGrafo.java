package com.udecartagena.pgraph;

import org.graphstream.graph.Edge;
import org.graphstream.graph.Graph;
import org.graphstream.graph.Node;
import org.graphstream.graph.implementations.SingleGraph;
import org.graphstream.ui.swing_viewer.SwingViewer;
import org.graphstream.ui.view.View;
import org.graphstream.ui.view.Viewer;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.Point2D;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class VisorGrafo {

    private static double currentX = 0.0;
    private static Map<NodoBB, Point2D.Double> coords = new HashMap<>();

    public static void mostrar(JFrame parent, PGraph modeloActual, String nombreSolver) {
        if (modeloActual == null) return;

        if (modeloActual.getUnidadesOperativas().size() > 10) {
            JOptionPane.showMessageDialog(parent,
                    "El modelo es muy grande para renderizar el grafo visual completo.",
                    "Límite de Nodos", JOptionPane.WARNING_MESSAGE);
            return;
        }

        System.setProperty("org.graphstream.ui", "swing");

        // 1. Calcular el árbol matemático
        MotorArbolBB motor = new MotorArbolBB(modeloActual);
        motor.construir();
        NodoBB raiz = motor.getRaiz();

        // 2. Coordenadas a gran escala
        currentX = 0.0;
        coords.clear();
        calcularCoordenadasAmplias(raiz);

        // 3. Configurar Grafo
        Graph graph = new SingleGraph("BranchAndBound");
        graph.setAttribute("ui.stylesheet", estiloCSS());
        graph.setAttribute("ui.quality");
        graph.setAttribute("ui.antialias");

        Node nodoRaiz = graph.addNode("Raiz");
        nodoRaiz.setAttribute("ui.label", "Inicio PNS");
        nodoRaiz.setAttribute("ui.class", "raiz");

        Point2D.Double pRoot = coords.get(raiz);
        nodoRaiz.setAttribute("x", pRoot.x);
        nodoRaiz.setAttribute("y", pRoot.y);

        dibujarRamasDinamicas(graph, raiz, "Raiz");

        // 4. Apagar físicas
        Viewer viewer = new SwingViewer(graph, Viewer.ThreadingModel.GRAPH_IN_ANOTHER_THREAD);
        viewer.disableAutoLayout();
        View view = viewer.addDefaultView(false);

        // =========================================================================
        // LA SOLUCIÓN DEFINITIVA AL AMONTONAMIENTO: Forzar un lienzo gigante y Scroll
        // =========================================================================
        int totalHojas = (int) (currentX / 100.0);
        int anchoRequerido = Math.max(1150, totalHojas * 90); // Garantiza ~90 pixeles por cada rama final

        Component viewComponent = (Component) view;
        viewComponent.setPreferredSize(new Dimension(anchoRequerido, 650));

        JScrollPane scrollPane = new JScrollPane(viewComponent);
        scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_ALWAYS);
        scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_NEVER);
        scrollPane.getHorizontalScrollBar().setUnitIncrement(25); // Hace que el scroll se mueva fluido
        // =========================================================================

        // 5. Configurar Ventana
        JDialog dialog = new JDialog(parent, "Explorador de Rutas PNS - Árbol Estático B&B", true);
        dialog.setSize(1200, 800);
        dialog.setLocationRelativeTo(parent);
        dialog.setLayout(new BorderLayout());

        // Agregamos el JScrollPane en vez de la vista directa
        dialog.add(scrollPane, BorderLayout.CENTER);

        // PANEL INFERIOR (Mantiene tu funcionalidad dinámica)
        JPanel panelInferior = new JPanel(new GridLayout(2, 1, 5, 5));
        panelInferior.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        panelInferior.setBackground(new Color(240, 240, 240));

        JLabel lblSolver = new JLabel("🔍 MOTOR SELECCIONADO: " + nombreSolver, SwingConstants.CENTER);
        lblSolver.setFont(new Font("SansSerif", Font.BOLD, 16));
        lblSolver.setForeground(new Color(41, 128, 185));

        JLabel lblLeyenda = new JLabel("🟢 Verde: Ruta Óptima  |  🔴 Rojo: Infactible  |  🟡 Amarillo: Factible (Usa la barra inferior para desplazarte 👉)", SwingConstants.CENTER);
        lblLeyenda.setFont(new Font("SansSerif", Font.BOLD, 13));

        panelInferior.add(lblSolver);
        panelInferior.add(lblLeyenda);

        dialog.add(panelInferior, BorderLayout.SOUTH);
        dialog.setVisible(true);
    }

    private static void calcularCoordenadasAmplias(NodoBB nodo) {
        if (nodo.hijo1 == null && nodo.hijo0 == null) {
            coords.put(nodo, new Point2D.Double(currentX, -nodo.nivel * 150.0));
            currentX += 100.0;
        } else {
            double sumX = 0;
            int count = 0;
            if (nodo.hijo1 != null) {
                calcularCoordenadasAmplias(nodo.hijo1);
                sumX += coords.get(nodo.hijo1).x;
                count++;
            }
            if (nodo.hijo0 != null) {
                calcularCoordenadasAmplias(nodo.hijo0);
                sumX += coords.get(nodo.hijo0).x;
                count++;
            }
            coords.put(nodo, new Point2D.Double(sumX / count, -nodo.nivel * 150.0));
        }
    }

    private static void dibujarRamasDinamicas(Graph graph, NodoBB nodoBB, String parentNodeId) {
        if (nodoBB.hijo1 != null) procesarHijoDinamico(graph, nodoBB.hijo1, parentNodeId, "y=1");
        if (nodoBB.hijo0 != null) procesarHijoDinamico(graph, nodoBB.hijo0, parentNodeId, "y=0");
    }

    private static void procesarHijoDinamico(Graph graph, NodoBB hijo, String parentNodeId, String decision) {
        String nodeId = UUID.randomUUID().toString();
        Node n = graph.addNode(nodeId);

        Point2D.Double p = coords.get(hijo);
        n.setAttribute("x", p.x);
        n.setAttribute("y", p.y);

        String etiqueta = "y_" + hijo.unidadId + "=" + hijo.decision;
        if (hijo.esHoja && hijo.factible) etiqueta += String.format("\nZ=%.0f", hijo.valorObjetivo);

        n.setAttribute("ui.label", etiqueta);

        if (hijo.esHoja) {
            n.setAttribute("ui.class", hijo.factible ? (hijo.esOptimo ? "optimo" : "factible") : "infactible");
        } else {
            n.setAttribute("ui.class", hijo.esOptimo ? "rutaOptima" : "normal");
        }

        Edge e = graph.addEdge(parentNodeId + "-" + nodeId, parentNodeId, nodeId, true);
        e.setAttribute("ui.label", decision);
        if (hijo.esOptimo) e.setAttribute("ui.class", "aristaOptima");

        dibujarRamasDinamicas(graph, hijo, nodeId);
    }

    private static String estiloCSS() {
        return "graph { padding: 40px; fill-color: white; } " +
                "node { " +
                "   shape: circle; size: 45px; fill-color: #3498db; text-size: 11px; text-color: white; text-alignment: center; " +
                "} " +
                "node.raiz { fill-color: #2c3e50; size: 55px; text-size: 13px; } " +
                "node.optimo { fill-color: #27ae60; size: 55px; stroke-mode: plain; stroke-color: #1e8449; stroke-width: 3px; text-size: 12px; text-style: bold; } " +
                "node.factible { fill-color: #f1c40f; text-color: black; } " +
                "node.infactible { fill-color: #e74c3c; size: 30px; text-color: white; } " +
                "node.rutaOptima { fill-color: #27ae60; } " +
                "edge { fill-color: #95a5a6; size: 2px; text-size: 13px; text-background-mode: plain; text-background-color: white; } " +
                "edge.aristaOptima { fill-color: #27ae60; size: 4px; text-color: #27ae60; text-style: bold; }";
    }
}