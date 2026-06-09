package com.udecartagena.pgraph;

public interface SolverMILP {
    /**
     * Ejecuta el proceso de optimización del P-Graph.
     * @return DTO unificado con las métricas de rendimiento y resultados del costo.
     */
    ResultadoSolucion resolver();
}