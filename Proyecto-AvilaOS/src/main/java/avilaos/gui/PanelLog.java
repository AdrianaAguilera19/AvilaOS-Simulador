/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package avilaos.gui;

import avilaos.log.EventLogger;
import java.awt.BorderLayout;
import java.awt.Dimension;
import javax.swing.BorderFactory;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;

/**
 * Pantalla del log de eventos (I06): muestra los ultimos eventos del sistema
 * (bloqueos, despertares, accesos remotos, seleccion de procesos...).
 * Solo lee copias del EventLogger; nunca toca la logica de la simulacion.
 * Se redibuja desde el Timer de PanelCluster (hilo de Swing), y solo cuando hay
 * eventos nuevos.
 */
public class PanelLog extends Tema.PanelRedondeado {

    // Cuantos eventos se muestran a la vez (el logger guarda mas)
    private static final int LINEAS_VISIBLES = 200;

    private final EventLogger logger;
    private final JTextArea area = new JTextArea();
    private final JScrollPane scroll = new JScrollPane(area);
    private long ultimoTotal = -1;   // total de eventos que habia la ultima vez que se dibujo

    public PanelLog(EventLogger logger) {
        super(Tema.TARJETA, Tema.BORDE, 24);
        if (logger == null) {
            throw new IllegalArgumentException("El panel del log necesita un logger");
        }
        this.logger = logger;
        setLayout(new BorderLayout(0, 8));
        setBorder(BorderFactory.createEmptyBorder(14, 18, 14, 18));
        setPreferredSize(new Dimension(100, 210));

        add(Tema.etiqueta("Log de eventos", Tema.SUBTITULO, Tema.TEXTO), BorderLayout.NORTH);

        area.setEditable(false);
        area.setFont(new java.awt.Font(java.awt.Font.MONOSPACED, java.awt.Font.PLAIN, 12));
        area.setForeground(Tema.TEXTO);
        area.setBackground(Tema.TARJETA);
        scroll.setBorder(null);
        scroll.getViewport().setBackground(Tema.TARJETA);
        add(scroll, BorderLayout.CENTER);
    }

    /** Redibuja el texto solo si el logger registro algo nuevo desde la ultima vez. */
    public void refresh() {
        long total = logger.getTotalLogged();
        if (total == ultimoTotal) {
            return;
        }
        ultimoTotal = total;
        area.setText(logger.getLastAsText(LINEAS_VISIBLES));
        area.setCaretPosition(area.getDocument().getLength());   // baja hasta el evento mas reciente
    }
}
