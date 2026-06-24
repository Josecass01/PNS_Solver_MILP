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

    // Separacion horizontal entre hojas del arbol.
    // Con nodos de 75px necesitamos al menos 160 unidades entre hojas.
    private static final double SEP_HOJA = 180.0;

    // Separacion vertical entre niveles del arbol.
    private static final double SEP_NIVEL = 200.0;

    private static double currentX = 0.0;
    private static Map<NodoBB, Point2D.Double> coords = new HashMap<>();

    public static void mostrar(JFrame parent, PGraph modeloActual, String nombreSolver) {
        if (modeloActual == null) return;

        if (modeloActual.getUnidadesOperativas().size() > 10) {
            JOptionPane.showMessageDialog(parent,
                    "El modelo es muy grande para renderizar el grafo visual completo.",
                    "Limite de Nodos", JOptionPane.WARNING_MESSAGE);
            return;
        }

        System.setProperty("org.graphstream.ui", "swing");

        // 1. Construir arbol matematico
        MotorArbolBB motor = new MotorArbolBB(modeloActual);
        motor.construir();
        NodoBB raiz = motor.getRaiz();

        // 2. Calcular coordenadas con separacion amplia
        currentX = 0.0;
        coords.clear();
        calcularCoordenadas(raiz);

        // 3. Armar grafo GraphStream
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

        dibujarRamas(graph, raiz, "Raiz");

        // 4. Renderizar sin layout automatico
        Viewer viewer = new SwingViewer(graph, Viewer.ThreadingModel.GRAPH_IN_ANOTHER_THREAD);
        viewer.disableAutoLayout();
        View view = viewer.addDefaultView(false);

        // Ancho proporcional al numero de hojas con la nueva separacion
        int totalHojas = (int) Math.round(currentX / SEP_HOJA);
        int anchoCanvas = Math.max(1400, totalHojas * 160);

        Component viewComponent = (Component) view;
        viewComponent.setPreferredSize(new Dimension(anchoCanvas, 750));

        JScrollPane scrollPane = new JScrollPane(viewComponent);
        scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_ALWAYS);
        scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        scrollPane.getHorizontalScrollBar().setUnitIncrement(30);
        scrollPane.getVerticalScrollBar().setUnitIncrement(30);

        // 5. Ventana principal
        JDialog dialog = new JDialog(parent, "Explorador de Rutas PNS - Arbol B&B", true);
        dialog.setSize(1300, 900);
        dialog.setLocationRelativeTo(parent);
        dialog.setLayout(new BorderLayout());
        dialog.add(scrollPane, BorderLayout.CENTER);

        // Panel inferior: leyenda de colores
        JPanel panelInferior = new JPanel(new GridLayout(3, 1, 4, 4));
        panelInferior.setBorder(BorderFactory.createEmptyBorder(8, 12, 8, 12));
        panelInferior.setBackground(new Color(235, 240, 245));

        JLabel lblSolver = new JLabel("Motor activo: " + nombreSolver, SwingConstants.CENTER);
        lblSolver.setFont(new Font("SansSerif", Font.BOLD, 15));
        lblSolver.setForeground(new Color(41, 128, 185));

        JLabel lblLeyenda = new JLabel(
                "Azul: nodo intermedio   |   Verde: camino optimo   |   " +
                        "Amarillo: hoja factible   |   Rojo: hoja infactible",
                SwingConstants.CENTER);
        lblLeyenda.setFont(new Font("SansSerif", Font.PLAIN, 13));

        JLabel lblNavegacion = new JLabel(
                "Usa la barra horizontal para desplazarte por el arbol completo",
                SwingConstants.CENTER);
        lblNavegacion.setFont(new Font("SansSerif", Font.ITALIC, 12));
        lblNavegacion.setForeground(new Color(100, 100, 100));

        panelInferior.add(lblSolver);
        panelInferior.add(lblLeyenda);
        panelInferior.add(lblNavegacion);
        dialog.add(panelInferior, BorderLayout.SOUTH);

        dialog.setVisible(true);
    }

    // -------------------------------------------------------------------------
    // Layout: asigna coordenadas (x, y) a cada nodo
    // -------------------------------------------------------------------------

    private static void calcularCoordenadas(NodoBB nodo) {
        if (nodo.hijo1 == null && nodo.hijo0 == null) {
            // Hoja: avanzar x por la separacion definida
            coords.put(nodo, new Point2D.Double(currentX, -nodo.nivel * SEP_NIVEL));
            currentX += SEP_HOJA;
        } else {
            double sumX = 0;
            int count = 0;
            if (nodo.hijo1 != null) {
                calcularCoordenadas(nodo.hijo1);
                sumX += coords.get(nodo.hijo1).x;
                count++;
            }
            if (nodo.hijo0 != null) {
                calcularCoordenadas(nodo.hijo0);
                sumX += coords.get(nodo.hijo0).x;
                count++;
            }
            // Nodo interno: centrado entre sus hijos
            coords.put(nodo, new Point2D.Double(sumX / count, -nodo.nivel * SEP_NIVEL));
        }
    }

    // -------------------------------------------------------------------------
    // Dibujo recursivo de nodos y aristas
    // -------------------------------------------------------------------------

    private static void dibujarRamas(Graph graph, NodoBB nodoBB, String parentNodeId) {
        if (nodoBB.hijo1 != null) procesarHijo(graph, nodoBB.hijo1, parentNodeId, "y=1");
        if (nodoBB.hijo0 != null) procesarHijo(graph, nodoBB.hijo0, parentNodeId, "y=0");
    }

    private static void procesarHijo(Graph graph, NodoBB hijo,
                                     String parentNodeId, String decision) {
        String nodeId = UUID.randomUUID().toString();
        Node n = graph.addNode(nodeId);

        Point2D.Double p = coords.get(hijo);
        n.setAttribute("x", p.x);
        n.setAttribute("y", p.y);

        // Etiqueta: nombre del nodo en la primera parte
        // Para hojas factibles: "y_06=1" en linea y "Z=32" separado con espacio
        String labelPrincipal = "y_" + hijo.unidadId + "=" + hijo.decision;
        String etiqueta;
        if (hijo.esHoja && hijo.factible) {
            etiqueta = labelPrincipal + "  Z=" + String.format("%.0f", hijo.valorObjetivo);
        } else {
            etiqueta = labelPrincipal;
        }
        n.setAttribute("ui.label", etiqueta);

        // Clase CSS segun tipo de nodo
        if (hijo.esHoja) {
            if (hijo.esOptimo)       n.setAttribute("ui.class", "optimo");
            else if (hijo.factible)  n.setAttribute("ui.class", "factible");
            else                     n.setAttribute("ui.class", "infactible");
        } else {
            n.setAttribute("ui.class", hijo.esOptimo ? "rutaOptima" : "normal");
        }

        // Arista padre -> hijo
        Edge e = graph.addEdge(parentNodeId + "-" + nodeId, parentNodeId, nodeId, true);
        e.setAttribute("ui.label", decision);
        if (hijo.esOptimo) e.setAttribute("ui.class", "aristaOptima");

        dibujarRamas(graph, hijo, nodeId);
    }

    // -------------------------------------------------------------------------
    // CSS de estilos GraphStream
    // -------------------------------------------------------------------------

    private static String estiloCSS() {
        return
                "graph { padding: 80px; fill-color: #f0f4f8; } " +

                        // Nodo base (intermedio, azul)
                        "node { " +
                        "   shape: circle; " +
                        "   size: 70px; " +
                        "   fill-color: #3498db; " +
                        "   text-size: 13px; " +
                        "   text-color: white; " +
                        "   text-alignment: center; " +
                        "   text-style: bold; " +
                        "   stroke-mode: plain; " +
                        "   stroke-color: #1a6fa8; " +
                        "   stroke-width: 2px; " +
                        "} " +

                        // Raiz
                        "node.raiz { " +
                        "   fill-color: #2c3e50; " +
                        "   size: 75px; " +
                        "   text-size: 14px; " +
                        "   stroke-color: #1a252f; " +
                        "} " +

                        // Nodo optimo (hoja con menor costo)
                        "node.optimo { " +
                        "   fill-color: #27ae60; " +
                        "   size: 85px; " +
                        "   stroke-mode: plain; " +
                        "   stroke-color: #1e8449; " +
                        "   stroke-width: 3px; " +
                        "   text-size: 12px; " +
                        "   text-style: bold; " +
                        "} " +

                        // Hoja factible no optima
                        "node.factible { " +
                        "   fill-color: #f1c40f; " +
                        "   size: 80px; " +
                        "   text-color: #1a1a1a; " +
                        "   text-size: 12px; " +
                        "   stroke-color: #c49b0a; " +
                        "   stroke-width: 2px; " +
                        "} " +

                        // Hoja infactible
                        "node.infactible { " +
                        "   fill-color: #e74c3c; " +
                        "   size: 60px; " +
                        "   text-color: white; " +
                        "   text-size: 11px; " +
                        "   stroke-color: #c0392b; " +
                        "   stroke-width: 2px; " +
                        "} " +

                        // Nodo intermedio en camino optimo
                        "node.rutaOptima { " +
                        "   fill-color: #27ae60; " +
                        "   size: 70px; " +
                        "   stroke-color: #1e8449; " +
                        "   stroke-width: 2px; " +
                        "} " +

                        // Arista normal
                        "edge { " +
                        "   fill-color: #aab4be; " +
                        "   size: 2px; " +
                        "   text-size: 14px; " +
                        "   text-background-mode: plain; " +
                        "   text-background-color: #f0f4f8; " +
                        "   text-padding: 4px; " +
                        "   text-color: #444444; " +
                        "} " +

                        // Arista en camino optimo
                        "edge.aristaOptima { " +
                        "   fill-color: #27ae60; " +
                        "   size: 4px; " +
                        "   text-color: #1e8449; " +
                        "   text-style: bold; " +
                        "}";
    }
}