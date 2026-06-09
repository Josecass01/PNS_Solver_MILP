package com.udecartagena.pgraph;

import javax.swing.SwingUtilities;
import javax.swing.UIManager;

public class Main {
    public static void main(String[] args) {
        // 1. Aplicamos el diseño visual nativo del sistema operativo (Windows) para que se vea profesional
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) {
            // Si falla en algún entorno, usa el estilo por defecto de Java sin romper nada
        }

        // 2. Encendemos la interfaz gráfica en su hilo de ejecución seguro
        SwingUtilities.invokeLater(() -> {
            VentanaPrincipal ventana = new VentanaPrincipal();
            ventana.setVisible(true);
        });
    }
}