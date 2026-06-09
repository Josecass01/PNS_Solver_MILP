package com.udecartagena.pgraph;

public class UnidadOperativa {
    private String id;
    private double capacityUpperBound;
    private double fixCost;
    private double proportionalCost;

    // Constructor
    public UnidadOperativa(String id, double capacityUpperBound, double fixCost, double proportionalCost) {
        this.id = id;
        this.capacityUpperBound = capacityUpperBound;
        this.fixCost = fixCost;
        this.proportionalCost = proportionalCost;
    }

    // Getters
    public String getId() {
        return id;
    }

    public double getCapacityUpperBound() {
        return capacityUpperBound;
    }

    public double getFixCost() {
        return fixCost;
    }

    public double getProportionalCost() {
        return proportionalCost;
    }

    @Override
    public String toString() {
        return "UnidadOperativa{" +
                "id='" + id + '\'' +
                ", capacityUpperBound=" + capacityUpperBound +
                ", fixCost=" + fixCost +
                ", proportionalCost=" + proportionalCost +
                '}';
    }
}