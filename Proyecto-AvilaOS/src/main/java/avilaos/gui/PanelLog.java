/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package avilaos.gui;

import avilaos.enums.LogCategory;
import avilaos.log.EventLogger;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import javax.swing.BorderFactory;
import javax.swing.JComboBox;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;

/**
 * Pantalla del log de eventos 
 */
public class PanelLog extends Tema.PanelRedondeado {

    // Cuantos eventos se muestran a la vez (el logger guarda mas)
    private static final int LINEAS_VISIBLES = 200;

    private final EventLogger logger;
    private final JTextArea area = new JTextArea();
    private final JScrollPane scroll = new JScrollPane(area);
    private final JComboBox<String> filtro = new JComboBox<>();
    private long ultimoTotal = -1;   // total de eventos que habia la ultima vez que se dibujo
    private LogCategory categoria = null;   // null = todas

    public PanelLog(EventLogger logger) {
        super(Tema.TARJETA, Tema.BORDE, 24);
        if (logger == null) {
            throw new IllegalArgumentException("El panel del log necesita un logger");
        }
        this.logger = logger;
        setLayout(new BorderLayout(0, 8));
        setBorder(BorderFactory.createEmptyBorder(14, 18, 14, 18));
        setPreferredSize(new Dimension(100, 210));

        filtro.addItem("Todas las categorias");
        for (LogCategory c : LogCategory.values()) {
            filtro.addItem(c.getLabel());
        }
        filtro.setFont(Tema.NORMAL);
        filtro.addActionListener(e -> {
            int i = filtro.getSelectedIndex();
            categoria = i <= 0 ? null : LogCategory.values()[i - 1];
            ultimoTotal = -1;   // fuerza a redibujar con el filtro nuevo
            refresh();
        });
        JPanel encabezado = new JPanel(new BorderLayout(8, 0));
        encabezado.setOpaque(false);
        encabezado.add(Tema.etiqueta("Log de eventos", Tema.SUBTITULO, Tema.TEXTO), BorderLayout.WEST);
        JPanel derecha = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        derecha.setOpaque(false);
        derecha.add(filtro);
        encabezado.add(derecha, BorderLayout.CENTER);
        add(encabezado, BorderLayout.NORTH);

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
        area.setText(logger.getLastAsText(LINEAS_VISIBLES, categoria));
        area.setCaretPosition(area.getDocument().getLength());   // baja hasta el evento mas reciente
    }
}