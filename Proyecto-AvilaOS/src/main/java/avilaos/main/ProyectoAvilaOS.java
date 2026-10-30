package avilaos.main;

import avilaos.gui.DialogoInicio;
import avilaos.gui.PanelCluster;
import avilaos.model.hardware.Cluster;
import javax.swing.JFrame;
import javax.swing.SwingUtilities;

/**
 * Punto de entrada de AvilaOS: pide la configuracion inicial (o un archivo guardado) y muestra la ventana principal.
 * Desde la ventana se crean procesos y buffers, se cambia la politica y se controla el reloj.
 */
public class ProyectoAvilaOS {

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            // El usuario elige cuantos computadores, su RAM, el ciclo y la latencia (o carga un archivo)
            Cluster cluster = DialogoInicio.pedir();
            if (cluster == null) {
                return;
            }
            JFrame ventana = new JFrame("AvilaOS - Simulador de sistema operativo distribuido");
            ventana.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            PanelCluster.mostrar(ventana, cluster);
            ventana.setSize(1400, 900);
            ventana.setLocationRelativeTo(null);
            ventana.setVisible(true);
        });
    }
}