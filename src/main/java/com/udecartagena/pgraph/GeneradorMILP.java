package com.udecartagena.pgraph;

import java.util.List;

public class GeneradorMILP {
    private PGraph pgraph;

    public GeneradorMILP(PGraph pgraph) {
        this.pgraph = pgraph;
    }

    public void imprimirFuncionObjetivo() {
        List<UnidadOperativa> unidades = pgraph.getUnidadesOperativas();
        StringBuilder funcion = new StringBuilder("Minimizar Z = ");

        for (int i = 0; i < unidades.size(); i++) {
            UnidadOperativa u = unidades.get(i);
            funcion.append(u.getFixCost()).append(" y_").append(u.getId())
                    .append(" + ")
                    .append(u.getProportionalCost()).append(" x_").append(u.getId());

            if (i < unidades.size() - 1) {
                funcion.append(" + ");
            }
        }

        System.out.println("\n--- Modelo MILP Generado ---");
        System.out.println("Función Objetivo:");
        System.out.println(funcion.toString());
    }

    public void imprimirRestricciones() {
        System.out.println("\nRestricciones:");
        System.out.println("// 1. Capacidad máxima de las unidades operativas");

        for (UnidadOperativa u : pgraph.getUnidadesOperativas()) {
            System.out.println("x_" + u.getId() + " - " + u.getCapacityUpperBound() + " y_" + u.getId() + " <= 0");
        }

        System.out.println("\n// 2. Balance de masa y demanda de productos");

        for (Material m : pgraph.getMateriales()) {
            if (m.getTipo().equals("raw_material")) continue;

            StringBuilder ecuacion = new StringBuilder();
            boolean primero = true;

            for (Flujo f : pgraph.getFlujos()) {
                if (f.getIdMaterialSalida().equals(m.getId())) {
                    if (!primero) ecuacion.append(" + ");
                    ecuacion.append("x_").append(f.getIdUnidadOperativa());
                    primero = false;
                }
            }

            for (Flujo f : pgraph.getFlujos()) {
                if (f.getIdMaterialEntrada().equals(m.getId())) {
                    ecuacion.append(" - x_").append(f.getIdUnidadOperativa());
                }
            }

            if (m.getTipo().equals("product")) {
                ecuacion.append(" >= ").append(m.getFlowRateLowerBound());
            } else {
                ecuacion.append(" >= 0.0");
            }

            System.out.println(m.getId() + ": " + ecuacion.toString());
        }
    }
}