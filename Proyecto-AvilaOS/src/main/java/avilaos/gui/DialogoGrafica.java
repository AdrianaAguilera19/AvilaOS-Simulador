/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */

package avilaos.gui;

import avilaos.model.hardware.Cluster;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Window;
import javax.swing.BorderFactory;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JPanel;
import javax.swing.Timer;

/**
 * Ventana con la grafica comparativa de utilizacion de CPU (I08). No bloquea la
 * pantalla principal y se refresca sola mientras esta abierta.
 */
public class DialogoGrafica extends JDialog {

    private static final int REFRESH_MS = 300;
    private static final String ACUMULADA = "Utilizacion acumulada";
    private static final String RECIENTE = "Ultimos " + PanelGrafica.VENTANA_RECIENTE + " ciclos";

    private final PanelGrafica grafica;
    private final Timer timer;

    /** Crea la ventana (sin mostrarla) para el cluster indicado. */
    public DialogoGrafica(Window owner, Cluster cluster) {
        super(owner, "Utilizacion de CPU");
        setModal(false);
        grafica = new PanelGrafica(cluster);

        JPanel contenido = new JPanel(new BorderLayout(0, 10));
        contenido.setBackground(Tema.FONDO);
        contenido.setBorder(BorderFactory.createEmptyBorder(16, 18, 16, 18));

        JPanel top = new JPanel(new BorderLayout());
        top.setOpaque(false);
        top.add(Tema.etiqueta("Utilizacion de CPU por computador", Tema.SUBTITULO, Tema.TEXTO),
                BorderLayout.WEST);
        JComboBox<String> modo = new JComboBox<>(new String[] {ACUMULADA, RECIENTE});
        modo.addActionListener(e -> grafica.setRecientes(RECIENTE.equals(modo.getSelectedItem())));
        JPanel derecha = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        derecha.setOpaque(false);
        derecha.add(modo);
        top.add(derecha, BorderLayout.EAST);
        contenido.add(top, BorderLayout.NORTH);

        Tema.PanelRedondeado tarjeta = new Tema.PanelRedondeado(Tema.TARJETA, Tema.BORDE, 20);
        tarjeta.setLayout(new BorderLayout());
        tarjeta.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        tarjeta.add(grafica, BorderLayout.CENTER);
        contenido.add(tarjeta, BorderLayout.CENTER);

        setContentPane(contenido);
        setPreferredSize(new Dimension(860, 460));
        pack();
        setLocationRelativeTo(owner);

        timer = new Timer(REFRESH_MS, e -> grafica.refresh());
    }

    /** Al mostrarla se actualiza y arranca el refresco; al ocultarla se detiene. */
    @Override
    public void setVisible(boolean visible) {
        if (visible) {
            grafica.refresh();
            timer.start();
        } else {
            timer.stop();
        }
        super.setVisible(visible);
    }
}
