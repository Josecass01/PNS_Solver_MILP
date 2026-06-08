package com.udecartagena.pgraph;

import java.io.*;
import java.util.*;

public class SolverGLPK {

    private static final String GLPK = new File("glpsol.exe").getAbsolutePath();
    private final PGraph pgraph;

    public SolverGLPK(PGraph pgraph) {
        this.pgraph = pgraph;
    }

    public void resolver() {
        System.out.println("\n=== Solver: GLPK (GNU Linear Programming Kit) ===");

        try {
            File lp  = File.createTempFile("pns_glpk_", ".lp");
            File sol = File.createTempFile("pns_glpk_", ".sol");
            lp.deleteOnExit();
            sol.deleteOnExit();

            generarLP(lp);

            long inicio = System.currentTimeMillis();
            ejecutar(lp, sol);
            long tiempo = System.currentTimeMillis() - inicio;

            parsearYMostrar(sol, tiempo);

        } catch (Exception e) {
            System.err.println("Error crítico ejecutando GLPK: " + e.getMessage());
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

            for (UnidadOperativa u : us) {
                pw.printf(Locale.US, " cap_%s: x_%s - %.4f y_%s <= 0%n", u.getId(), u.getId(), u.getCapacityUpperBound(), u.getId());
            }

            for (Material m : pgraph.getMateriales()) {
                if (m.getTipo().equals("raw_material")) continue;

                StringJoiner expr = new StringJoiner(" ");
                boolean tieneTerminos = false;

                for (Flujo f : pgraph.getFlujos()) {
                    if (f.getIdMaterialSalida().equals(m.getId())) {
                        expr.add(tieneTerminos ? "+ x_" + f.getIdUnidadOperativa() : "x_" + f.getIdUnidadOperativa());
                        tieneTerminos = true;
                    }
                }
                for (Flujo f : pgraph.getFlujos()) {
                    if (f.getIdMaterialEntrada().equals(m.getId())) {
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
        ProcessBuilder pb = new ProcessBuilder(GLPK, "--lp", lp.getAbsolutePath(), "--output", sol.getAbsolutePath());
        pb.redirectErrorStream(true);
        Process proceso = pb.start();

        /* Es obligatorio vaciar el búfer del stream del proceso externo para evitar
           que se llene la memoria asignada por el SO y congele indefinidamente el ejecutable. */
        try (BufferedReader br = new BufferedReader(new InputStreamReader(proceso.getInputStream()))) {
            while (br.readLine() != null) {
                // Consumo silencioso de la consola nativa de GLPK
            }
        }
        proceso.waitFor();
    }

    private void parsearYMostrar(File sol, long tiempoMs) throws IOException {
        double objetivo = Double.MAX_VALUE;
        Map<String, Double> valoresVariables = new HashMap<>();

        try (BufferedReader br = new BufferedReader(new FileReader(sol))) {
            String linea;
            while ((linea = br.readLine()) != null) {
                linea = linea.trim();

                if (linea.startsWith("Objective:")) {
                    String[] partes = linea.split("=");
                    if (partes.length >= 2) {
                        /* Se eliminan sufijos textuales añadidos por GLPK (ej: "(MINimum)") para aislar el valor numérico */
                        String valorStr = partes[1].replaceAll("\\([^)]*\\)", "").trim();
                        try {
                            objetivo = Double.parseDouble(valorStr);
                        } catch (NumberFormatException ignored) {}
                    }
                }

                if (linea.contains("y_") || linea.contains("x_")) {
                    String[] tokens = linea.split("\\s+");
                    for (int i = 0; i < tokens.length; i++) {
                        if (tokens[i].startsWith("y_") || tokens[i].startsWith("x_")) {
                            String nombreVar = tokens[i];
                            /* GLPK formatea filas con variables agregando un token de estado ("*", "NL", "B") antes del valor real.
                               Este bloque intercepta de forma dinámica la posición correcta del dato numérico. */
                            if (i + 1 < tokens.length) {
                                try {
                                    double val = Double.parseDouble(tokens[i + 1]);
                                    valoresVariables.put(nombreVar, val);
                                } catch (NumberFormatException e) {
                                    if (i + 2 < tokens.length) {
                                        try {
                                            valoresVariables.put(nombreVar, Double.parseDouble(tokens[i + 2]));
                                        } catch (NumberFormatException ignored) {}
                                    }
                                }
                            }
                            break;
                        }
                    }
                }
            }
        }

        if (objetivo == Double.MAX_VALUE) {
            System.out.println("GLPK determinó que el modelo es infactible o no se pudo procesar.");
            return;
        }

        System.out.printf("Costo mínimo (Z) : %.4f%n", objetivo);
        System.out.println("Unidades operativas:");
        for (UnidadOperativa u : pgraph.getUnidadesOperativas()) {
            int y = (int) Math.round(valoresVariables.getOrDefault("y_" + u.getId(), 0.0));
            double x = valoresVariables.getOrDefault("x_" + u.getId(), 0.0);
            System.out.printf("  %-4s  y=%d (%s)   x=%.4f%n",
                    u.getId(), y, y == 1 ? "ACTIVA  " : "inactiva", x);
        }
        System.out.printf("Tiempo de ejecución: %d ms%n", tiempoMs);
    }
}