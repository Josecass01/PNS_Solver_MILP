package com.udecartagena.pgraph;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeCellRenderer;
import java.awt.*;

public class VisorArbol {

    public static void mostrar(JFrame parent, PGraph modeloActual) {
        if (modeloActual == null) return;

        // Validar tamaño para no congelar la PC (2^n combinaciones)
        if (modeloActual.getUnidadesOperativas().size() > 12) {
            JOptionPane.showMessageDialog(parent,
                    "El modelo es demasiado grande para renderizar el árbol completo en memoria.",
                    "Límite de Nodos", JOptionPane.WARNING_MESSAGE);
            return;
        }

        // Generar el árbol en segundo plano usando la lógica de Apache Math
        MotorArbolBB motor = new MotorArbolBB(modeloActual);
        motor.construir();
        NodoBB raiz = motor.getRaiz();

        // Convertir la estructura matemática a un árbol visual de Java Swing
        DefaultMutableTreeNode rootNode = crearNodoJTree(raiz);
        JTree arbolUI = new JTree(rootNode);

        // Mejorar la apariencia del árbol
        DefaultTreeCellRenderer renderer = (DefaultTreeCellRenderer) arbolUI.getCellRenderer();
        renderer.setClosedIcon(null);
        renderer.setOpenIcon(null);
        renderer.setLeafIcon(null);
        arbolUI.setFont(new Font("Monospaced", Font.PLAIN, 14));

        // Expandir las primeras ramas por defecto
        for (int i = 0; i < arbolUI.getRowCount(); i++) {
            arbolUI.expandRow(i);
        }

        // Crear la ventana emergente
        JDialog dialog = new JDialog(parent, "Explorador de Rutas - Branch & Bound", true);
        dialog.setSize(600, 500);
        dialog.setLocationRelativeTo(parent);
        dialog.add(new JScrollPane(arbolUI), BorderLayout.CENTER);
        dialog.setVisible(true);
    }

    private static DefaultMutableTreeNode crearNodoJTree(NodoBB nodo) {
        String texto = nodo.etiqueta();

        if (nodo.esHoja) {
            if (nodo.factible) {
                texto += String.format("  ---> [FACTIBLE] Costo Z = %.4f", nodo.valorObjetivo);
                if (nodo.esOptimo) texto += "  ⭐⭐⭐ RUTA ÓPTIMA ⭐⭐⭐";
            } else {
                texto += "  ---> [INFACTIBLE] (Viola restricciones de flujo)";
            }
        } else {
            if (nodo.esOptimo && nodo.nivel > 0) {
                texto += " (Ruta hacia el óptimo)";
            }
        }

        DefaultMutableTreeNode treeNode = new DefaultMutableTreeNode(texto);

        // Agregar las ramas hijas (Rama 1 activa primero, luego Rama 0 inactiva)
        if (nodo.hijo1 != null) treeNode.add(crearNodoJTree(nodo.hijo1));
        if (nodo.hijo0 != null) treeNode.add(crearNodoJTree(nodo.hijo0));

        return treeNode;
    }
}