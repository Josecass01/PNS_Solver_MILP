package com.udecartagena.pgraph;

import org.apache.commons.math3.optim.MaxIter;
import org.apache.commons.math3.optim.PointValuePair;
import org.apache.commons.math3.optim.linear.*;
import org.apache.commons.math3.optim.nonlinear.scalar.GoalType;

import java.util.ArrayList;
import java.util.List;

/**
 * Motor de Branch and Bound reutilizable basado en Apache Commons Math.
 *
 * Permite que cualquier solver (GLPK, HiGHS, Gurobi) construya el árbol
 * de decisiones ABB sin duplicar código. Evalúa cada hoja del árbol
 * resolviendo la relajación LP continua con las variables binarias fijas.
 *
 * El árbol resultante muestra:
 *   - Cada nodo interno: decisión y_i = 0 (inactiva) o y_i = 1 (activa)
 *   - Cada hoja: combinación completa evaluada, con costo si es factible
 *   - Camino óptimo resaltado: la rama que produce el menor costo
 */
public class MotorArbolBB {

    private final PGraph pgraph;
    private List<UnidadOperativa> unidades;
    private int n;
    private LinearObjectiveFunction funcionObjetivo;
    private List<LinearConstraint> restriccionesBase;

    private double mejorValor;
    private double[] mejorSolucion;
    private NodoBB raiz;

    public MotorArbolBB(PGraph pgraph) {
        this.pgraph = pgraph;
    }

    /**
     * Construye el árbol completo de Branch and Bound y retorna la raíz.
     * Después de llamar a este método, usa {@link #getRaiz()} para visualizar.
     */
    public NodoBB construir() {
        unidades = pgraph.getUnidadesOperativas();
        n = unidades.size();
        int numVars = 2 * n;

        // Función objetivo: sum(fi * yi) + sum(cj * xj)
        double[] coefObj = new double[numVars];
        for (int i = 0; i < n; i++) {
            coefObj[i]     = unidades.get(i).getFixCost();
            coefObj[i + n] = unidades.get(i).getProportionalCost();
        }
        funcionObjetivo = new LinearObjectiveFunction(coefObj, 0);
        restriccionesBase = generarRestriccionesBase(numVars);

        mejorValor = Double.MAX_VALUE;
        mejorSolucion = null;

        raiz = new NodoBB(0, null, -1);
        explorar(raiz, new double[n], 0, numVars);
        marcarCaminoOptimo();

        return raiz;
    }

    public NodoBB getRaiz() {
        return raiz;
    }

    /** El mejor valor objetivo encontrado, o Double.MAX_VALUE si no hay solución factible. */
    public double getMejorValor() {
        return mejorValor;
    }

    // -------------------------------------------------------------------------
    // Internos
    // -------------------------------------------------------------------------

    private void explorar(NodoBB nodo, double[] asignacion, int nivel, int numVars) {
        if (nivel == n) {
            // Hoja: evaluar la combinación fija con LP continuo
            nodo.esHoja = true;
            List<LinearConstraint> restriccionesNodo = new ArrayList<>(restriccionesBase);
            for (int i = 0; i < n; i++) {
                double[] c = new double[numVars];
                c[i] = 1.0;
                restriccionesNodo.add(new LinearConstraint(c, Relationship.EQ, asignacion[i]));
            }
            try {
                PointValuePair sol = new SimplexSolver().optimize(
                        new MaxIter(10000),
                        funcionObjetivo,
                        new LinearConstraintSet(restriccionesNodo),
                        GoalType.MINIMIZE,
                        new NonNegativeConstraint(true)
                );
                nodo.factible = true;
                nodo.valorObjetivo = sol.getValue();
                if (sol.getValue() < mejorValor) {
                    mejorValor = sol.getValue();
                    mejorSolucion = sol.getPoint();
                }
            } catch (NoFeasibleSolutionException e) {
                nodo.factible = false;
                nodo.valorObjetivo = null;
            }
            return;
        }

        String idUnidad = unidades.get(nivel).getId();

        // Rama y_i = 0
        asignacion[nivel] = 0.0;
        nodo.hijo0 = new NodoBB(nivel + 1, idUnidad, 0);
        explorar(nodo.hijo0, asignacion, nivel + 1, numVars);

        // Rama y_i = 1
        asignacion[nivel] = 1.0;
        nodo.hijo1 = new NodoBB(nivel + 1, idUnidad, 1);
        explorar(nodo.hijo1, asignacion, nivel + 1, numVars);
    }

    private void marcarCaminoOptimo() {
        if (mejorSolucion == null || raiz == null) return;
        NodoBB nodo = raiz;
        nodo.esOptimo = true;
        for (int i = 0; i < n; i++) {
            int decision = (int) Math.round(mejorSolucion[i]);
            nodo = (decision == 0) ? nodo.hijo0 : nodo.hijo1;
            if (nodo == null) break;
            nodo.esOptimo = true;
        }
    }

    private List<LinearConstraint> generarRestriccionesBase(int numVars) {
        List<LinearConstraint> r = new ArrayList<>();

        // Restricciones de capacidad: x_i <= M * y_i
        for (int i = 0; i < n; i++) {
            UnidadOperativa u = unidades.get(i);
            double[] c = new double[numVars];
            c[i + n] = 1.0;
            c[i]     = -u.getCapacityUpperBound();
            r.add(new LinearConstraint(c, Relationship.LEQ, 0.0));
        }

        // Balance de masa por material
        for (Material m : pgraph.getMateriales()) {
            if (m.getTipo().equals("raw_material")) continue;

            double[] c = new double[numVars];
            for (Flujo f : pgraph.getFlujos()) {
                int idx = indexUnidad(f.getIdUnidadOperativa());
                if (idx == -1) continue;
                if (f.getIdMaterialSalida() != null && f.getIdMaterialSalida().equals(m.getId()))
                    c[idx + n] += 1.0;
                if (f.getIdMaterialEntrada() != null && f.getIdMaterialEntrada().equals(m.getId()))
                    c[idx + n] -= 1.0;
            }

            double rhs = m.getTipo().equals("product") ? m.getFlowRateLowerBound() : 0.0;
            r.add(new LinearConstraint(c, Relationship.GEQ, rhs));
        }

        return r;
    }

    private int indexUnidad(String id) {
        for (int i = 0; i < n; i++) {
            if (unidades.get(i).getId().equals(id)) return i;
        }
        return -1;
    }
}