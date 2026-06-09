package com.udecartagena.pgraph;

import java.io.*;
import java.nio.file.*;
import java.util.*;

public class SolverGLPK implements SolverMILP {

    // glpsol.exe debe estar en la raíz del proyecto, igual que highs.exe
    private static final String GLPK = new File("glpsol.exe").getAbsolutePath();

    private final PGraph pgraph;

    public SolverGLPK(PGraph pgraph) {
        this.pgraph = pgraph;
    }

    @Override
    public ResultadoSolucion resolver() {
        System.out.println("\n=== Solver: GLPK (GNU Linear Programming Kit) ===");

        try {
            // Crear archivos temporales en el directorio de trabajo del proyecto
            // (NO en %TEMP%) para evitar problemas con rutas que tienen espacios
            // o caracteres especiales en Windows
            File dirTrabajo = new File(".").getAbsoluteFile();
            File lp  = new File(dirTrabajo, "pns_glpk_temp.lp");
            File sol = new File(dirTrabajo, "pns_glpk_temp.sol");

            generarLP(lp);

            long inicio = System.currentTimeMillis();
            String errorEjecucion = ejecutar(lp, sol);
            long tiempo = System.currentTimeMillis() - inicio;

            ResultadoSolucion resultado = parsearYMostrar(sol, tiempo, errorEjecucion);

            // Limpiar archivos temporales
            lp.delete();
            sol.delete();

            return resultado;

        } catch (Exception e) {
            System.err.println("Error crítico ejecutando GLPK: " + e.getMessage());
            return new ResultadoSolucion("GLPK", false, 0.0, 0, new HashMap<>());
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
                pw.printf(Locale.US, " cap_%s: x_%s - %.4f y_%s <= 0%n",
                        u.getId(), u.getId(), u.getCapacityUpperBound(), u.getId());
            }

            for (Material m : pgraph.getMateriales()) {
                if (m.getTipo().equals("raw_material")) continue;
                StringJoiner expr = new StringJoiner(" ");
                boolean tieneTerminos = false;
                for (Flujo f : pgraph.getFlujos()) {
                    if (f.getIdMaterialSalida() != null && f.getIdMaterialSalida().equals(m.getId())) {
                        expr.add(tieneTerminos ? "+ x_" + f.getIdUnidadOperativa() : "x_" + f.getIdUnidadOperativa());
                        tieneTerminos = true;
                    }
                    if (f.getIdMaterialEntrada() != null && f.getIdMaterialEntrada().equals(m.getId())) {
                        expr.add("- x_" + f.getIdUnidadOperativa());
                        tieneTerminos = true;
                    }
                }
                if (tieneTerminos) {
                    if (m.getTipo().equals("product")) {
                        pw.printf(Locale.US, " bal_%s: %s >= %.4f%n", m.getId(), expr, m.getFlowRateLowerBound());
                    } else {
                        pw.printf(Locale.US, " bal_%s: %s >= 0.0%n", m.getId(), expr);
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

    // Retorna el stderr capturado para diagnóstico
    private String ejecutar(File lp, File sol) throws IOException, InterruptedException {
        ProcessBuilder pb = new ProcessBuilder(
                GLPK,
                "--lp", lp.getAbsolutePath(),
                "-o",   sol.getAbsolutePath()
        );
        pb.redirectErrorStream(false); // separar stdout y stderr

        Process proceso = pb.start();

        // Leer stdout (output normal de glpsol)
        StringBuilder stdout = new StringBuilder();
        try (BufferedReader br = new BufferedReader(new InputStreamReader(proceso.getInputStream()))) {
            String linea;
            while ((linea = br.readLine()) != null) stdout.append(linea).append("\n");
        }

        // Leer stderr (errores de glpsol, por ej. DLL no encontrada)
        StringBuilder stderr = new StringBuilder();
        try (BufferedReader br = new BufferedReader(new InputStreamReader(proceso.getErrorStream()))) {
            String linea;
            while ((linea = br.readLine()) != null) stderr.append(linea).append("\n");
        }

        int exitCode = proceso.waitFor();

        // Imprimir diagnóstico si algo salió mal
        if (exitCode != 0 || stderr.length() > 0) {
            System.out.println("[GLPK diagnóstico] Exit code: " + exitCode);
            if (stderr.length() > 0)
                System.out.println("[GLPK stderr]: " + stderr.toString().trim());
        }

        return stderr.toString();
    }

    private ResultadoSolucion parsearYMostrar(File sol, long tiempoMs, String errorEjecucion) throws IOException {
        if (!sol.exists() || sol.length() == 0) {
            System.out.println("GLPK no generó archivo de solución.");
            if (!errorEjecucion.isEmpty())
                System.out.println("Error del proceso: " + errorEjecucion.trim());
            System.out.println("Verifica: 1) glpsol.exe está en la raíz del proyecto");
            System.out.println("          2) Windows Defender no lo está bloqueando");
            System.out.println("          3) El .dll de GLPK está junto al .exe");
            return new ResultadoSolucion("GLPK", false, 0.0, tiempoMs, new HashMap<>());
        }

        double objetivo = Double.MAX_VALUE;
        Map<String, Double> valores = new HashMap<>();
        boolean enColumnas = false;

        try (BufferedReader br = new BufferedReader(new FileReader(sol))) {
            String linea;
            while ((linea = br.readLine()) != null) {
                linea = linea.trim();
                if (linea.isEmpty()) continue;

                // "Objective:  obj = 24 (MINimum)"
                if (linea.startsWith("Objective:")) {
                    try {
                        String num = linea.split("=")[1].trim().split("\\s+")[0];
                        objetivo = Double.parseDouble(num);
                    } catch (Exception ignored) {}
                }

                if (linea.contains("Column name") && linea.contains("Activity")) {
                    enColumnas = true;
                    continue;
                }
                if (enColumnas && linea.startsWith("------")) continue;
                if (enColumnas && (linea.startsWith("Integer") || linea.startsWith("KKT"))) {
                    enColumnas = false;
                    continue;
                }

                // Formato GLPK:
                // variable binaria:  "  3 y_02  *  1  0  1"   -> t[2]="*", valor en t[3]
                // variable continua: "  4 x_02     10  0   "   -> valor en t[2]
                if (enColumnas) {
                    String[] t = linea.split("\\s+");
                    if (t.length >= 3) {
                        String nombre = t[1];
                        try {
                            double valor;
                            if (t[2].equals("*")) {
                                valor = t.length >= 4 ? Double.parseDouble(t[3]) : 0.0;
                            } else {
                                valor = Double.parseDouble(t[2]);
                            }
                            valores.put(nombre, valor);
                        } catch (NumberFormatException ignored) {}
                    }
                }
            }
        }

        if (objetivo == Double.MAX_VALUE) {
            System.out.println("GLPK: no se encontró valor objetivo en el archivo de solución.");
            return new ResultadoSolucion("GLPK", false, 0.0, tiempoMs, new HashMap<>());
        }

        System.out.printf("Costo mínimo (Z) : %.4f%n", objetivo);
        System.out.println("Unidades operativas:");
        for (UnidadOperativa u : pgraph.getUnidadesOperativas()) {
            int    y = (int) Math.round(valores.getOrDefault("y_" + u.getId(), 0.0));
            double x = valores.getOrDefault("x_" + u.getId(), 0.0);
            System.out.printf("  %-4s  y=%d (%s)   x=%.4f%n",
                    u.getId(), y, y == 1 ? "ACTIVA  " : "inactiva", x);
        }
        System.out.printf("Tiempo de ejecución: %d ms%n", tiempoMs);

        return new ResultadoSolucion("GLPK", true, objetivo, tiempoMs, valores);
    }
}
