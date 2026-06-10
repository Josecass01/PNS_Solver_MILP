package com.udecartagena.pgraph;

import com.gurobi.gurobi.*;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SolverGurobi implements SolverMILP {

    private final PGraph pgraph;

    public SolverGurobi(PGraph pgraph) {
        this.pgraph = pgraph;
    }

    @Override
    public ResultadoSolucion resolver() {
        System.out.println("\n=== Solver: Gurobi (Motor Comercial) ===");
        long inicio = System.currentTimeMillis();

        try {
            // Iniciar entorno Gurobi sin que llene la consola de texto basura
            GRBEnv env = new GRBEnv(true);
            env.set("LogFile", "gurobi.log");
            env.set(GRB.IntParam.OutputFlag, 0);
            env.start();

            GRBModel model = new GRBModel(env);
            List<UnidadOperativa> us = pgraph.getUnidadesOperativas();

            Map<String, GRBVar> yVars = new HashMap<>();
            Map<String, GRBVar> xVars = new HashMap<>();
            GRBLinExpr funcionObjetivo = new GRBLinExpr();

            // 1. Crear Variables (y_i, x_i) y Función Objetivo
            for (UnidadOperativa u : us) {
                GRBVar y = model.addVar(0.0, 1.0, 0.0, GRB.BINARY, "y_" + u.getId());
                GRBVar x = model.addVar(0.0, GRB.INFINITY, 0.0, GRB.CONTINUOUS, "x_" + u.getId());

                yVars.put(u.getId(), y);
                xVars.put(u.getId(), x);

                funcionObjetivo.addTerm(u.getFixCost(), y);
                funcionObjetivo.addTerm(u.getProportionalCost(), x);
            }
            model.setObjective(funcionObjetivo, GRB.MINIMIZE);

            // 2. Restricciones de Capacidad
            for (UnidadOperativa u : us) {
                GRBLinExpr capExpr = new GRBLinExpr();
                capExpr.addTerm(1.0, xVars.get(u.getId()));
                capExpr.addTerm(-u.getCapacityUpperBound(), yVars.get(u.getId()));
                model.addConstr(capExpr, GRB.LESS_EQUAL, 0.0, "cap_" + u.getId());
            }

            // 3. Balance de Masa
            for (Material m : pgraph.getMateriales()) {
                if (m.getTipo().equals("raw_material")) continue;

                GRBLinExpr balExpr = new GRBLinExpr();
                for (Flujo f : pgraph.getFlujos()) {
                    if (f.getIdMaterialSalida() != null && f.getIdMaterialSalida().equals(m.getId())) {
                        balExpr.addTerm(1.0, xVars.get(f.getIdUnidadOperativa()));
                    }
                    if (f.getIdMaterialEntrada() != null && f.getIdMaterialEntrada().equals(m.getId())) {
                        balExpr.addTerm(-1.0, xVars.get(f.getIdUnidadOperativa()));
                    }
                }

                double rhs = m.getTipo().equals("product") ? m.getFlowRateLowerBound() : 0.0;
                model.addConstr(balExpr, GRB.GREATER_EQUAL, rhs, "bal_" + m.getId());
            }

            // 4. Ejecutar optimización matemática
            model.optimize();
            long tiempoMs = System.currentTimeMillis() - inicio;

            int status = model.get(GRB.IntAttr.Status);
            if (status == GRB.Status.OPTIMAL) {
                double objVal = model.get(GRB.DoubleAttr.ObjVal);
                Map<String, Double> valores = new HashMap<>();

                System.out.printf("Costo mínimo (Z) : %.4f%n", objVal);
                System.out.println("Unidades operativas:");

                for (UnidadOperativa u : us) {
                    String claveY = "y_" + u.getId();
                    String claveX = "x_" + u.getId();

                    double valY = yVars.get(u.getId()).get(GRB.DoubleAttr.X);
                    double valX = xVars.get(u.getId()).get(GRB.DoubleAttr.X);

                    valores.put(claveY, valY);
                    valores.put(claveX, valX);

                    int yEntero = (int) Math.round(valY);
                    System.out.printf("  %-4s  y=%d (%s)   x=%.4f%n",
                            u.getId(), yEntero, yEntero == 1 ? "ACTIVA  " : "inactiva", valX);
                }

                model.dispose();
                env.dispose();
                return new ResultadoSolucion("Gurobi", true, objVal, tiempoMs, valores);
            } else {
                model.dispose();
                env.dispose();
                System.out.println("Gurobi determinó que el modelo es infactible.");
                return new ResultadoSolucion("Gurobi", false, 0.0, tiempoMs, new HashMap<>());
            }

        } catch (GRBException e) {
            System.err.println("Error ejecutando Gurobi: " + e.getMessage());
            return new ResultadoSolucion("Gurobi", false, 0.0, 0, new HashMap<>());
        }
    }
}