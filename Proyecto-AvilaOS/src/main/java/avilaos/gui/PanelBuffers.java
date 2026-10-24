/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package avilaos.gui;

import avilaos.datastructures.CustomLinkedList;
import avilaos.model.hardware.Cluster;
import avilaos.model.hardware.Computer;
import avilaos.sync.BoundedBuffer;
import avilaos.sync.BufferMetrics;
import avilaos.sync.BufferSnapshot;
import java.awt.BorderLayout;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;

/**
 * Pantalla de los buffers (I05 / S09): una tarjeta por buffer con su contenido y capacidad,
 * el valor de cada semaforo y los procesos que esperan en cada uno.
 * Solo lee fotos (BufferSnapshot); nunca toca la logica de la simulacion.
 * refresh() se llama desde el Timer de PanelCluster (hilo de Swing). Si aparecen o
 * desaparecen buffers, las tarjetas se vuelven a armar solas.
 */
public class PanelBuffers extends Tema.PanelRedondeado {

    private final Cluster cluster;
    private final JPanel lista = new JPanel();
    private final JLabel lblVacio = Tema.etiqueta("Todavia no hay buffers", Tema.NORMAL, Tema.TEXTO_SUAVE);
    private TarjetaBuffer[] tarjetas = new TarjetaBuffer[0];

    public PanelBuffers(Cluster cluster) {
        super(Tema.TARJETA, Tema.BORDE, 24);
        if (cluster == null) {
            throw new IllegalArgumentException("El panel de buffers necesita un cluster");
        }
        this.cluster = cluster;
        setLayout(new BorderLayout(0, 8));
        setBorder(BorderFactory.createEmptyBorder(14, 18, 14, 18));
                setPreferredSize(new java.awt.Dimension(100, 290));

        add(Tema.etiqueta("Buffers", Tema.SUBTITULO, Tema.TEXTO), BorderLayout.NORTH);

        lista.setOpaque(false);
        lista.setLayout(new BoxLayout(lista, BoxLayout.Y_AXIS));
        JPanel envoltorio = new JPanel(new BorderLayout());
        envoltorio.setOpaque(false);
        envoltorio.add(lista, BorderLayout.NORTH);
        JScrollPane scroll = new JScrollPane(envoltorio);
        scroll.setBorder(null);
        scroll.setOpaque(false);
        scroll.getViewport().setOpaque(false);
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        add(scroll, BorderLayout.CENTER);

        refresh();
    }

    /** Vuelve a leer los buffers y actualiza todas las tarjetas. */
    public void refresh() {
        CustomLinkedList<BoundedBuffer> buffers = cluster.getBufferManager().getSnapshot();
        if (cambioLaLista(buffers)) {
            reconstruir(buffers);
        }
        for (int i = 0; i < tarjetas.length; i++) {
            tarjetas[i].refresh();
        }
    }

    /** true si hay buffers nuevos o ya no esta alguno de los que se dibujan. */
    private boolean cambioLaLista(CustomLinkedList<BoundedBuffer> buffers) {
        if (buffers.size() != tarjetas.length) {
            return true;
        }
        for (int i = 0; i < tarjetas.length; i++) {
            if (buffers.get(i) != tarjetas[i].buffer) {
                return true;
            }
        }
        return false;
    }

    private void reconstruir(CustomLinkedList<BoundedBuffer> buffers) {
        lista.removeAll();
        tarjetas = new TarjetaBuffer[buffers.size()];
        if (tarjetas.length == 0) {
            lista.add(lblVacio);
        }
        for (int i = 0; i < tarjetas.length; i++) {
            BoundedBuffer b = buffers.get(i);
            Computer host = cluster.getComputer(b.getHostComputerId());
            String nombreHost = host != null ? host.getName() : "PC" + b.getHostComputerId();
            tarjetas[i] = new TarjetaBuffer(b, nombreHost);
            lista.add(tarjetas[i]);
            lista.add(Box.createVerticalStrut(8));
        }
        lista.revalidate();
        lista.repaint();
    }

    /** Tarjeta de UN buffer. */
    private static class TarjetaBuffer extends Tema.PanelRedondeado {
        private final BoundedBuffer buffer;
        private final JLabel lblElementos = Tema.etiqueta("0 / 0", Tema.NEGRITA, Tema.TEXTO_SUAVE);
        private final JProgressBar barElementos = Tema.barra(Tema.MENTA, true);
        private final JLabel lblContenido = Tema.etiqueta("-", Tema.NORMAL, Tema.TEXTO);
        private final JLabel lblMutex = Tema.etiqueta(" ", Tema.NORMAL, Tema.TEXTO);
        private final JLabel lblItems = Tema.etiqueta(" ", Tema.NORMAL, Tema.TEXTO);
        private final JLabel lblSpaces = Tema.etiqueta(" ", Tema.NORMAL, Tema.TEXTO);
                // S10: metricas del buffer
        private final JLabel lblProduccion = Tema.etiqueta(" ", Tema.NORMAL, Tema.TEXTO);
        private final JLabel lblPromedios = Tema.etiqueta(" ", Tema.NORMAL, Tema.TEXTO);
        private final JLabel lblEsperas = Tema.etiqueta(" ", Tema.NORMAL, Tema.TEXTO);

        TarjetaBuffer(BoundedBuffer buffer, String nombreHost) {
            super(Tema.FONDO, Tema.BORDE, 16);
            this.buffer = buffer;
            setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
            setBorder(BorderFactory.createEmptyBorder(10, 14, 10, 14));

            JPanel titulo = new JPanel(new BorderLayout());
            titulo.setOpaque(false);
            titulo.add(Tema.etiqueta("B" + buffer.getId() + "   en " + nombreHost,
                    Tema.NEGRITA, Tema.TEXTO), BorderLayout.WEST);
            titulo.add(lblElementos, BorderLayout.EAST);
            agregar(titulo);

            barElementos.setMinimum(0);
            barElementos.setMaximum(buffer.getCapacity());
            agregar(Box.createVerticalStrut(4));
            agregar(barElementos);
            agregar(Box.createVerticalStrut(6));
            agregar(lblContenido);
            agregar(Box.createVerticalStrut(6));
            // Fuente de ancho fijo para que las tres filas de semaforos queden alineadas
            java.awt.Font mono = new java.awt.Font(java.awt.Font.MONOSPACED, java.awt.Font.PLAIN, 12);
            lblMutex.setFont(mono);
            lblItems.setFont(mono);
            lblSpaces.setFont(mono);
            agregar(lblMutex);
            agregar(lblItems);
            agregar(lblSpaces);
                        agregar(Box.createVerticalStrut(6));
            lblPromedios.setFont(mono);
            lblEsperas.setFont(mono);
            agregar(lblProduccion);
            agregar(lblPromedios);
            agregar(lblEsperas);
        }

        private void agregar(java.awt.Component c) {
            if (c instanceof JLabel) {
                ((JLabel) c).setAlignmentX(LEFT_ALIGNMENT);
            }
            if (c instanceof JPanel || c instanceof JProgressBar) {
                ((javax.swing.JComponent) c).setAlignmentX(LEFT_ALIGNMENT);
                ((javax.swing.JComponent) c).setMaximumSize(
                        new java.awt.Dimension(Integer.MAX_VALUE, c.getPreferredSize().height));
            }
            add(c);
        }

        void refresh() {
            BufferSnapshot s = buffer.takeSnapshot();
            lblElementos.setText(s.getSize() + " / " + s.getCapacity() + " elementos");
            barElementos.setValue(s.getSize());
            lblContenido.setText("Contenido: " + texto(s.getContents()));
            lblMutex.setText("mutex      valor " + valor(s.getMutexValue())
                    + "   esperando: " + texto(s.getMutexWaiting()));
            lblItems.setText("elementos  valor " + valor(s.getItemsValue())
                    + "   esperando: " + texto(s.getItemsWaiting()));
            lblSpaces.setText("espacios   valor " + valor(s.getSpacesValue())
                    + "   esperando: " + texto(s.getSpacesWaiting()));
            
            // S10: produccion y tiempo promedio de bloqueo en cada semaforo (en ciclos)
            BufferMetrics m = buffer.takeMetrics();
            lblProduccion.setText("Producidos " + m.getProduced() + "   Consumidos " + m.getConsumed());
            lblPromedios.setText("bloqueo promedio (ciclos)   mutex " + promedio(m.getMutexAverageWait())
                    + "   elementos " + promedio(m.getItemsAverageWait())
                    + "   espacios " + promedio(m.getSpacesAverageWait()));
            lblEsperas.setText("esperas terminadas           mutex " + String.format("%4d", m.getMutexWaits())
                    + "   elementos " + String.format("%4d", m.getItemsWaits())
                    + "   espacios " + String.format("%4d", m.getSpacesWaits()));
        }
                /** Promedio con un decimal y ancho fijo; "-" mientras todavia no hubo esperas. */
        private static String promedio(double v) {
            return v == 0.0 ? "   -" : String.format("%4.1f", v);
        }

        /** Valor del semaforo con ancho fijo (2 caracteres) para alinear las filas. */
        private static String valor(int v) {
            return String.format("%2d", v);
        }

        private static String texto(int[] valores) {
            if (valores.length == 0) {
                return "vacio";
            }
            String t = "";
            for (int i = 0; i < valores.length; i++) {
                t += (i > 0 ? "  ›  " : "") + valores[i];
            }
            return t;
        }

        private static String texto(String[] nombres) {
            if (nombres.length == 0) {
                return "-";
            }
            String t = "";
            for (int i = 0; i < nombres.length; i++) {
                t += (i > 0 ? ", " : "") + nombres[i];
            }
            return t;
        }
    }
}
