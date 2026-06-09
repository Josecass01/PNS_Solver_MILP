package com.udecartagena.pgraph;

import java.io.*;
import java.util.*;

public class SolverHiGHS implements SolverMILP {

    private static final String HIGHS = new File("highs.exe").getAbsolutePath();
    private final PGraph pgraph;

    public SolverHiGHS(PGraph pgraph) {
        this.pgraph = pgraph;
    }

    @Override
    public ResultadoSolucion resolver() {
        System.out.println("\n=== Solver: HiGHS ===");

        try {
            File lp  = File.createTempFile("pns_", ".lp");
            File sol = File.createTempFile("pns_", ".sol");
            lp.deleteOnExit();
            sol.deleteOnExit();

            generarLP(lp);

            long inicio = System.currentTimeMillis();
            ejecutar(lp, sol);
            long tiempo = System.currentTimeMillis() - inicio;

            return parsearYMostrar(sol, tiempo);

        } catch (Exception e) {
            System.err.println("Error ejecutando HiGHS: " + e.getMessage());
            return new ResultadoSolucion("HiGHS", false, 0.0, 0, new HashMap<>());
        }
    }

    private void generarLP(File archivo) throws IOException {
        List<UnidadOperativa> us = pgraph.getUnidadesOperativas();
        try (PrintWriter pw = new PrintWriter(archivo)) {
            pw.println("Minimize");
            StringJoiner obj = new StringJoiner(" + ", " obj: ", "");
            for (UnidadOperativa u : us) {
                obj.add(String.format(Locale.US, "%.0f y_%s", u.getFixCost(), u.getId()));
                obj.add(String.format(Locale.US, "%.4f x_%s", u.getProportionalCost(), u.getId()));
            }
            pw.println(obj.toString());

            pw.println("Subject To");
            // 1. Restricción de Capacidad
            for (UnidadOperativa u : us) {
                pw.printf(Locale.US, " cap_%s: x_%s - %.4f y_%s <= 0%n", u.getId(), u.getId(), u.getCapacityUpperBound(), u.getId());
            }

            // 2. BALANCE DE MASA DINÁMICO (Soporta cualquier caso de estudio automáticamente)
            for (Material m : pgraph.getMateriales()) {
                if (m.getTipo().equals("raw_material")) continue;

                StringJoiner expr = new StringJoiner(" ");
                boolean tieneTerminos = false;

                for (Flujo f : pgraph.getFlujos()) {
                    // Si el material 'm' es la SALIDA de esta unidad, el flujo SUMA al nodo
                    if (f.getIdMaterialSalida() != null && f.getIdMaterialSalida().equals(m.getId())) {
                        expr.add(tieneTerminos ? "+ x_" + f.getIdUnidadOperativa() : "x_" + f.getIdUnidadOperativa());
                        tieneTerminos = true;
                    }

                    // Si el material 'm' es la ENTRADA de esta unidad, el flujo RESTA del nodo
                    if (f.getIdMaterialEntrada() != null && f.getIdMaterialEntrada().equals(m.getId())) {
                        expr.add("- x_" + f.getIdUnidadOperativa());
                        tieneTerminos = true;
                    }
                }

                if (tieneTerminos) {
                    if (m.getTipo().equals("product")) {
                        pw.printf(Locale.US, " bal_%s: %s >= %.4f%n", m.getId(), expr.toString(), m.getFlowRateLowerBound());
                    } else {
                        pw.printf(Locale.US, " bal_%s: %s >= 0.0%n", m.getId(), expr.toString());
                    }
                }
            }

            pw.println("Bounds");
            for (UnidadOperativa u : us) {
                pw.printf(Locale.US, " x_%s >= 0%n", u.getId());
            }

            pw.println("Binaries");
            for (UnidadOperativa u : us) {
                pw.printf(" y_%s%n", u.getId());
            }
            pw.println("End");
        }
    }

    private void ejecutar(File lp, File sol) throws IOException, InterruptedException {
        ProcessBuilder pb = new ProcessBuilder(HIGHS, "--model_file", lp.getAbsolutePath(), "--solution_file", sol.getAbsolutePath());
        pb.redirectErrorStream(true);
        Process proceso = pb.start();

        try (BufferedReader br = new BufferedReader(new InputStreamReader(proceso.getInputStream()))) {
            while (br.readLine() != null) {}
        }
        proceso.waitFor();
    }

    private ResultadoSolucion parsearYMostrar(File sol, long tiempoMs) throws IOException {
        double objetivo = Double.MAX_VALUE;
        Map<String, Double> valores = new HashMap<>();

        try (BufferedReader br = new BufferedReader(new FileReader(sol))) {
            String linea;
            boolean enColumnas = false;
            while ((linea = br.readLine()) != null) {
                linea = linea.trim();

                if (linea.startsWith("Objective")) {
                    String[] partes = linea.split("\\s+");
                    if (partes.length >= 2) {
                        try {
                            objetivo = Double.parseDouble(partes[1]);
                        } catch (Exception ignored) {}
                    }
                } else if (linea.startsWith("# Columns")) {
                    enColumnas = true;
                } else if (linea.startsWith("# Rows")) {
                    enColumnas = false;
                } else if (enColumnas && !linea.startsWith("Name")) {
                    String[] partes = linea.split("\\s+");
                    if (partes.length >= 2) {
                        try {
                            valores.put(partes[0], Double.parseDouble(partes[1]));
                        } catch (Exception ignored) {}
                    }
                }
            }
        }

        if (objetivo == Double.MAX_VALUE) {
            System.out.println("HiGHS determinó que el modelo es infactible.");
            return new ResultadoSolucion("HiGHS", false, 0.0, tiempoMs, new HashMap<>());
        }

        System.out.println("Costo mínimo (Z) : " + objetivo);
        System.out.println("Unidades operativas:");
        for (UnidadOperativa u : pgraph.getUnidadesOperativas()) {
            int y = (int) Math.round(valores.getOrDefault("y_" + u.getId(), 0.0));
            double x = valores.getOrDefault("x_" + u.getId(), 0.0);
            System.out.printf("  %-4s  y=%d (%s)   x=%.4f%n", u.getId(), y, y == 1 ? "ACTIVA  " : "inactiva", x);
        }
        System.out.printf("Tiempo de ejecución: %d ms%n", tiempoMs);

        // Retornamos el objeto unificado incluyendo el mapa completo con las variables de decisión (x_i, y_i)
        return new ResultadoSolucion("HiGHS", true, objetivo, tiempoMs, valores);
    }
}