package com.udecartagena.pgraph;

import java.io.File;
import java.io.RandomAccessFile;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class ServicioPersistencia {

    public static void guardarMetricasJSON(String rutaArchivo, List<ResultadoSolucion> resultados) {
        File archivo = new File(rutaArchivo);
        boolean esArchivoNuevo = !archivo.exists();

        // 1. Si el archivo ya existe, removemos el último ']' de forma estática
        if (!esArchivoNuevo && archivo.length() > 0) {
            removerUltimoCaracter(archivo);
        }

        try (PrintWriter pw = new PrintWriter(new FileWriter(archivo, true))) {

            if (esArchivoNuevo) {
                pw.print("[\n");
            } else {
                pw.print(",\n");
            }

            // CORRECCIÓN: Usamos .size() y .get(i) adecuados para un java.util.List
            for (int i = 0; i < resultados.size(); i++) {
                ResultadoSolucion res = resultados.get(i);
                StringBuilder json = new StringBuilder();

                json.append("  {\n");
                json.append(String.format("    \"solver\": \"%s\",\n", res.getNombreSolver()));
                json.append(String.format("    \"factible\": %b,\n", res.isFactible()));
                json.append(String.format(Locale.US, "    \"costo_optimo\": %.4f,\n", res.isFactible() ? res.getCostoOptimo() : 0.0));
                json.append(String.format("    \"tiempo_ms\": %d,\n", res.getTiempoEjecucionMs()));

                json.append("    \"variables_activas\": {\n");

                Map<String, Double> vars = res.getVariablesActivas();
                int totalVars = vars.size();
                int contador = 0;

                for (Map.Entry<String, Double> entry : vars.entrySet()) {
                    contador++;
                    String sufijo = (contador == totalVars) ? "" : ",";
                    json.append(String.format(Locale.US, "      \"%s\": %.4f%s%n", entry.getKey(), entry.getValue(), sufijo));
                }

                json.append("    }\n");
                json.append("  }");

                if (i < resultados.size() - 1) {
                    json.append(",\n");
                } else {
                    json.append("\n");
                }

                pw.print(json.toString());
            }

            pw.print("]");
            System.out.println("[Persistencia] Guardado exitoso en: " + rutaArchivo);

        } catch (IOException e) {
            System.err.println("Error escribiendo el archivo JSON: " + e.getMessage());
        }
    }

    // CORRECCIÓN: Añadida la palabra clave 'static' para que pueda ser invocado correctamente
    private static void removerUltimoCaracter(File archivo) {
        try (RandomAccessFile raf = new RandomAccessFile(archivo, "rw")) {
            long longitud = raf.length();
            if (longitud > 0) {
                long pos = longitud - 1;
                while (pos > 0) {
                    raf.seek(pos);
                    char c = (char) raf.read();
                    if (c == ']') {
                        raf.setLength(pos);
                        break;
                    }
                    pos--;
                }
            }
        } catch (IOException e) {
            System.err.println("Error al ajustar el cierre del archivo JSON: " + e.getMessage());
        }
    }
}