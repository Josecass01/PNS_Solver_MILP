package com.udecartagena.pgraph;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;

public class LectorPGraph {

    public static PGraph leerArchivo(String rutaArchivo) {
        PGraph modelo = new PGraph();

        try (BufferedReader br = new BufferedReader(new FileReader(rutaArchivo))) {
            String linea;
            String seccionActual = "";

            while ((linea = br.readLine()) != null) {
                linea = linea.trim(); // Quitar espacios en blanco

                if (linea.isEmpty()) continue; // Ignorar líneas vacías

                // Detectar cambio de sección
                if (linea.equals("materials:")) {
                    seccionActual = "materiales";
                    continue;
                } else if (linea.equals("operating_units:")) {
                    seccionActual = "unidades";
                    continue;
                } else if (linea.equals("material_to_operating_unit_flow_rates:")) {
                    seccionActual = "flujos";
                    continue;
                }

                // Lógica de extracción según la sección
                if (seccionActual.equals("materiales")) {
                    String[] partes = linea.split(":");
                    String id = partes[0].trim();
                    String resto = partes[1].trim();

                    String tipo = "";
                    double limiteFlujo = 0.0;

                    if (resto.contains(",")) {
                        String[] atributos = resto.split(",");
                        tipo = atributos[0].trim();
                        // Extraer el número después del '='
                        String limiteStr = atributos[1].split("=")[1].trim();
                        limiteFlujo = Double.parseDouble(limiteStr);
                    } else {
                        tipo = resto;
                    }
                    modelo.agregarMaterial(new Material(id, tipo, limiteFlujo));

                } else if (seccionActual.equals("unidades")) {
                    String[] partes = linea.split(":");
                    String id = partes[0].trim();
                    String[] atributos = partes[1].split(",");

                    double capacidad = 0, costoFijo = 0, costoProp = 0;

                    for (String attr : atributos) {
                        String[] par = attr.split("=");
                        String clave = par[0].trim();
                        double valor = Double.parseDouble(par[1].trim());

                        if (clave.equals("capacity_upper_bound")) capacidad = valor;
                        else if (clave.equals("fix_cost")) costoFijo = valor;
                        else if (clave.equals("proportional_cost")) costoProp = valor;
                    }
                    modelo.agregarUnidadOperativa(new UnidadOperativa(id, capacidad, costoFijo, costoProp));

                } else if (seccionActual.equals("flujos")) {
                    String[] partes = linea.split(":");
                    String idUnidad = partes[0].trim();
                    String[] flujo = partes[1].split("=>");

                    String entrada = flujo[0].trim();
                    String salida = flujo[1].trim();

                    modelo.agregarFlujo(new Flujo(idUnidad, entrada, salida));
                }
            }

            System.out.println("¡Archivo leído y procesado exitosamente!");

        } catch (IOException e) {
            System.err.println("Error al intentar leer el archivo: " + e.getMessage());
        }

        return modelo;
    }
}