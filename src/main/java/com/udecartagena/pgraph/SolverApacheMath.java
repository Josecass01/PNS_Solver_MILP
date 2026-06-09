package com.udecartagena.pgraph;

import org.apache.commons.math3.optim.MaxIter;
import org.apache.commons.math3.optim.PointValuePair;
import org.apache.commons.math3.optim.linear.*;
import org.apache.commons.math3.optim.nonlinear.scalar.GoalType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SolverApacheMath implements SolverMILP {

    private final PGraph pgraph;
    private int n;
    private List<UnidadOperativa> unidades;
    private List<LinearConstraint> restriccionesBase;
    private LinearObjectiveFunction funcionObjetivo;

    private double mejorValor = Double.MAX_VALUE;
    private double[] mejorSolucion = null;
    private int nodos = 0;
    private int soluciones = 0;

    public SolverApacheMath(PGraph pgraph) {
        this.pgraph = pgraph;
    }

    @Override
    public ResultadoSolucion resolver() {
        System.out.println("\n=== Solver: Apache Commons Math (Branch & Bound) ===");

        long inicio = System.currentTimeMillis();

        unidades = pgraph.getUnidadesOperativas();
        n = unidades.size();
        int numVars = 2 * n;

        double[] coefObj = new double[numVars];
        for (int i = 0; i < n; i++) {
            coefObj[i]     = unidades.get(i).getFixCost();
            coefObj[i + n] = unidades.get(i).getProportionalCost();
        }
        funcionObjetivo = new LinearObjectiveFunction(coefObj, 0);

        restriccionesBase = generarRestriccionesBase(numVars);

        mejorValor = Double.MAX_VALUE;
        mejorSolucion = null;
        nodos = 0;
        soluciones = 0;

        double[] asignacionInicial = new double[n];
        branchAndBound(asignacionInicial, 0, numVars);

        long tiempo = System.currentTimeMillis() - inicio;

        return procesarYRetornar(tiempo);
    }

    private void branchAndBound(double[] asignacion, int nivel, int numVars) {
        nodos++;

        if (nivel == n) {
            List<LinearConstraint> resNodo = new ArrayList<>(restriccionesBase);
            for (int i = 0; i < n; i++) {
                double[] c = new double[numVars];
                c[i] = 1.0;
                resNodo.add(new LinearConstraint(c, Relationship.EQ, asignacion[i]));
            }

            try {
                SimplexSolver solver = new SimplexSolver();
                PointValuePair solucion = solver.optimize(
                        new MaxIter(10000),
                        funcionObjetivo,
                        new LinearConstraintSet(resNodo),
                        GoalType.MINIMIZE,
                        new NonNegativeConstraint(true)
                );

                soluciones++;
                double valor = solucion.getValue();
                if (valor < mejorValor) {
                    mejorValor = valor;
                    mejorSolucion = solucion.getPoint();
                }
            } catch (NoFeasibleSolutionException ignored) {
                // Nodo infactible en la relajación continua, se poda
            }
            return;
        }

        // Rama 1: Desactivar unidad operativa (y_i = 0)
        asignacion[nivel] = 0.0;
        branchAndBound(asignacion, nivel + 1, numVars);

        // Rama 2: Activar unidad operativa (y_i = 1)
        asignacion[nivel] = 1.0;
        branchAndBound(asignacion, nivel + 1, numVars);
    }

    private List<LinearConstraint> generarRestriccionesBase(int numVars) {
        List<LinearConstraint> r = new ArrayList<>();

        for (int i = 0; i < n; i++) {
            UnidadOperativa u = unidades.get(i);
            double[] c = new double[numVars];
            c[i + n] = 1.0;
            c[i]     = -u.getCapacityUpperBound();
            r.add(new LinearConstraint(c, Relationship.LEQ, 0.0));
        }

        for (Material m : pgraph.getMateriales()) {
            if (m.getTipo().equals("raw_material")) continue;

            double[] c = new double[numVars];
            for (Flujo f : pgraph.getFlujos()) {
                int idx = indexUnidad(f.getIdUnidadOperativa());
                if (idx == -1) continue;
                if (f.getIdMaterialSalida().equals(m.getId()))  c[idx + n] += 1.0;
                if (f.getIdMaterialEntrada().equals(m.getId())) c[idx + n] -= 1.0;
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

    private ResultadoSolucion procesarYRetornar(long tiempoMs) {
        if (mejorSolucion == null) {
            System.out.println("Apache Commons Math determinó que el modelo es infactible.");
            return new ResultadoSolucion("Apache Commons Math", false, 0.0, tiempoMs, new HashMap<>());
        }

        // Construimos el mapa dinámico para empacar las variables activas del B&B continuo
        Map<String, Double> mapaVariables = new HashMap<>();

        System.out.printf("%nCosto mínimo (Z) : %.4f%n", mejorValor);
        System.out.println("Unidades operativas:");
        for (int i = 0; i < n; i++) {
            String idUnidad = unidades.get(i).getId();
            double valY = mejorSolucion[i];
            double valX = mejorSolucion[i + n];

            // Almacenamos los valores indexados con la clave exacta del modelo matemático (y_U1, x_U1, etc.)
            mapaVariables.put("y_" + idUnidad, valY);
            mapaVariables.put("x_" + idUnidad, valX);

            int y = (int) Math.round(valY);
            System.out.printf("  %-4s  y=%d (%s)   x=%.4f%n", idUnidad, y, y == 1 ? "ACTIVA  " : "inactiva", valX);
        }
        System.out.printf("Tiempo de ejecución: %d ms%n", tiempoMs);
        System.out.printf("[B&B] Nodos explorados: %d | Hojas factibles: %d%n", nodos, soluciones);

        // Retornamos el DTO inyectando el mapa estructurado
        return new ResultadoSolucion("Apache Commons Math", true, mejorValor, tiempoMs, mapaVariables);
    }
}