package com.udecartagena.pgraph;

public class Flujo {
    private String idUnidadOperativa;
    private String idMaterialEntrada;
    private String idMaterialSalida;

    // Constructor
    public Flujo(String idUnidadOperativa, String idMaterialEntrada, String idMaterialSalida) {
        this.idUnidadOperativa = idUnidadOperativa;
        this.idMaterialEntrada = idMaterialEntrada;
        this.idMaterialSalida = idMaterialSalida;
    }

    // Getters
    public String getIdUnidadOperativa() {
        return idUnidadOperativa;
    }

    public String getIdMaterialEntrada() {
        return idMaterialEntrada;
    }

    public String getIdMaterialSalida() {
        return idMaterialSalida;
    }

    @Override
    public String toString() {
        return "Flujo{" +
                "Unidad='" + idUnidadOperativa + '\'' +
                ", Entrada='" + idMaterialEntrada + '\'' +
                ", Salida='" + idMaterialSalida + '\'' +
                '}';
    }
}