package com.udecartagena.pgraph;

import java.util.ArrayList;
import java.util.List;

public class PGraph {
    private List<Material> materiales;
    private List<UnidadOperativa> unidadesOperativas;
    private List<Flujo> flujos;

    // Constructor que inicializa las listas vacías
    public PGraph() {
        this.materiales = new ArrayList<>();
        this.unidadesOperativas = new ArrayList<>();
        this.flujos = new ArrayList<>();
    }

    // Métodos para agregar elementos a las listas
    public void agregarMaterial(Material material) {
        this.materiales.add(material);
    }

    public void agregarUnidadOperativa(UnidadOperativa unidad) {
        this.unidadesOperativas.add(unidad);
    }

    public void agregarFlujo(Flujo flujo) {
        this.flujos.add(flujo);
    }

    // Getters para obtener las listas completas
    public List<Material> getMateriales() {
        return materiales;
    }

    public List<UnidadOperativa> getUnidadesOperativas() {
        return unidadesOperativas;
    }

    public List<Flujo> getFlujos() {
        return flujos;
    }

    // Método para imprimir un resumen del P-Graph en consola
    public void imprimirResumen() {
        System.out.println("=== Resumen del Modelo P-Graph ===");
        System.out.println("Total Materiales: " + materiales.size());
        System.out.println("Total Unidades Operativas: " + unidadesOperativas.size());
        System.out.println("Total Flujos: " + flujos.size());
        System.out.println("==================================");
    }
}