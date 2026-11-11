/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package avilaos.gui;

import avilaos.model.hardware.Computer;
import java.awt.Dimension;
import java.awt.Window;
import javax.swing.BorderFactory;
import javax.swing.JDialog;
import javax.swing.JPanel;
import javax.swing.Timer;

/**
 * Ventana con la tabla COMPLETA de procesos (PCB) de UN computador (incluye MAR, memoria
 * y buffer). La version compacta ya esta siempre visible en la tarjeta del computador
 * (PanelComputador); esta ventana es para ver todas las columnas.
 * Se refresca sola mientras esta abierta.
 * @author Antonella Ruiz
 */
public class DialogoProcesos extends JDialog {

    private static final int REFRESH_MS = 300;

    private final PanelProcesos tabla;
    private final Timer timer;

    /** Crea la ventana (sin mostrarla) para el computador indicado. */
    public DialogoProcesos(Window owner, Computer computer) {
        super(owner, "Procesos de " + computer.getName());
        setModal(false);

        tabla = new PanelProcesos(computer, false, true);
        JPanel contenido = new JPanel(new java.awt.BorderLayout());
        contenido.setBackground(Tema.FONDO);
        contenido.setBorder(BorderFactory.createEmptyBorder(16, 18, 16, 18));
        contenido.add(tabla, java.awt.BorderLayout.CENTER);

        setContentPane(contenido);
        setPreferredSize(new Dimension(1100, 440));
        pack();
        setLocationRelativeTo(owner);

        timer = new Timer(REFRESH_MS, e -> tabla.refrescar());
    }

    /** Al mostrarla se actualiza y arranca el refresco; al ocultarla se detiene. */
    @Override
    public void setVisible(boolean visible) {
        if (visible) {
            tabla.refrescar();
            timer.start();
        } else {
            timer.stop();
        }
        super.setVisible(visible);
    }
}