package com.udecartagena.pgraph;

public class Material {
    private String id;
    private String tipo; // Puede ser: raw_material, intermediate, product
    private double flowRateLowerBound;

    // Constructor
    public Material(String id, String tipo, double flowRateLowerBound) {
        this.id = id;
        this.tipo = tipo;
        this.flowRateLowerBound = flowRateLowerBound;
    }

    // Getters
    public String getId() {
        return id;
    }

    public String getTipo() {
        return tipo;
    }

    public double getFlowRateLowerBound() {
        return flowRateLowerBound;
    }

    @Override
    public String toString() {
        return "Material{" +
                "id='" + id + '\'' +
                ", tipo='" + tipo + '\'' +
                ", flowRateLowerBound=" + flowRateLowerBound +
                '}';
    }
}