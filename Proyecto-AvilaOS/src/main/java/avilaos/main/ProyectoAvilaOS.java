package avilaos.main;

import avilaos.gui.PanelCluster;
import avilaos.model.hardware.Cluster;
import avilaos.scheduler.FCFSPolicy;
import avilaos.scheduler.RoundRobinPolicy;
import javax.swing.JFrame;
import javax.swing.SwingUtilities;

/**
 * Punto de entrada de AvilaOS: arma un cluster de dos computadores y muestra la ventana principal.
 * Desde la ventana se crean procesos y buffers, se cambia la politica y se controla el reloj.
 */
public class ProyectoAvilaOS {

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            // 100 ms por ciclo y 3 ciclos de latencia de red (ambos se pueden cambiar desde la ventana)
            Cluster cluster = new Cluster(100, 3);
            cluster.addComputer("PC1", new FCFSPolicy(), 512);
            cluster.addComputer("PC2", new RoundRobinPolicy(4), 512);

            JFrame ventana = new JFrame("AvilaOS - Simulador de sistema operativo distribuido");
            ventana.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            ventana.setContentPane(new PanelCluster(cluster));
            ventana.setSize(1400, 900);
            ventana.setLocationRelativeTo(null);
            ventana.setVisible(true);
        });
    }
}