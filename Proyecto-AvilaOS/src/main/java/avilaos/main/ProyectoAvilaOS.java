package avilaos.main;

import avilaos.gui.PanelCargaJson;
import avilaos.gui.Tema;
import avilaos.gui.VentanaSimulacion;
import java.awt.BorderLayout;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;

/**
 * Punto de entrada de AvilaOS (P2): primero se cargan los dos JSON (sistema de archivos y procesos)
 * y al pulsar "Iniciar simulacion" se abre la ventana integrada con el disco, las colas y las metricas.
 */
public class ProyectoAvilaOS {

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            JFrame ventana = new JFrame("AvilaOS - Simulador de sistema operativo");
            ventana.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

            PanelCargaJson carga = new PanelCargaJson();
            carga.setOnBundleReady(bundle -> VentanaSimulacion.mostrar(ventana, bundle, carga));

            JPanel inicio = new JPanel(new BorderLayout());
            inicio.setBackground(Tema.FONDO);
            inicio.setBorder(javax.swing.BorderFactory.createEmptyBorder(24, 24, 24, 24));
            inicio.add(Tema.etiqueta("AvilaOS - carga de la simulacion", Tema.TITULO, Tema.ROSA), BorderLayout.NORTH);
            inicio.add(carga, BorderLayout.CENTER);
            ventana.setContentPane(inicio);

            ventana.setSize(1400, 900);
            ventana.setLocationRelativeTo(null);
            ventana.setVisible(true);
        });
    }
}