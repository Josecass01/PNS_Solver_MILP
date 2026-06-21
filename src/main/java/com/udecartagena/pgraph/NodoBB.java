package com.udecartagena.pgraph;

/**
 * Representa un nodo del árbol de Branch and Bound (algoritmo ABB) usado para
 * resolver el problema MILP de síntesis de redes de procesos (PNS).
 *
 * Cada nodo (salvo la raíz) corresponde a la decisión binaria y_i = 0 o y_i = 1
 * sobre una unidad operativa específica. Las hojas del árbol (nivel == n)
 * representan una combinación completa de unidades activas/inactivas, sobre
 * la cual se resuelve la relajación lineal continua para conocer su costo.
 */
public class NodoBB {

    /** Nivel del árbol (0 = raíz). Corresponde al índice de la unidad operativa decidida. */
    public int nivel;

    /** Id de la unidad operativa decidida para llegar a este nodo (null en la raíz). */
    public String unidadId;

    /** Decisión tomada: 0 (unidad inactiva), 1 (unidad activa), -1 en la raíz. */
    public int decision;

    /** Solo aplica a hojas: indica si la combinación resultó factible. */
    public boolean esHoja;
    public boolean factible;
    public Double valorObjetivo; // null si no aplica o es infactible

    /** Marca si este nodo pertenece al camino que lleva a la solución óptima encontrada. */
    public boolean esOptimo = false;

    public NodoBB hijo0; // rama y_i = 0
    public NodoBB hijo1; // rama y_i = 1

    public NodoBB(int nivel, String unidadId, int decision) {
        this.nivel = nivel;
        this.unidadId = unidadId;
        this.decision = decision;
    }

    public String etiqueta() {
        if (unidadId == null) return "Inicio";
        return "y_" + unidadId + "=" + decision;
    }
}
