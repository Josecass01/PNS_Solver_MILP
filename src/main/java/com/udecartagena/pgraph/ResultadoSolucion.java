package com.udecartagena.pgraph;

import java.util.Map;
import java.util.HashMap;

public class ResultadoSolucion {
    private final String nombreSolver;
    private final boolean factible;
    private final double costoOptimo;
    private final long tiempoEjecucionMs;
    // Añadimos el mapa dinámico para almacenar las variables de decisión (x_i, y_i) y sus valores reales
    private final Map<String, Double> variablesActivas;

    public ResultadoSolucion(String nombreSolver, boolean factible, double costoOptimo, long tiempoEjecucionMs, Map<String, Double> variablesActivas) {
        this.nombreSolver = nombreSolver;
        this.factible = factible;
        this.costoOptimo = costoOptimo;
        this.tiempoEjecucionMs = tiempoEjecucionMs;
        // Si el mapa viene nulo (por ejemplo, en caso de infactibilidad), inicializamos uno vacío para evitar NullPointerException
        this.variablesActivas = variablesActivas != null ? variablesActivas : new HashMap<>();
    }

    // Getters estándar
    public String getNombreSolver() { return nombreSolver; }
    public boolean isFactible() { return factible; }
    public double getCostoOptimo() { return costoOptimo; }
    public long getTiempoEjecucionMs() { return tiempoEjecucionMs; }
    public Map<String, Double> getVariablesActivas() { return variablesActivas; }
}