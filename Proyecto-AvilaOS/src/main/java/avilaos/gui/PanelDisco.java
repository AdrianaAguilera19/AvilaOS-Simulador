/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package avilaos.gui;

import avilaos.enums.BlockUse;
import avilaos.enums.DiskSchedulingPolicy;
import avilaos.enums.HeadDirection;
import avilaos.model.hardware.BlockInfo;
import avilaos.model.hardware.Cluster;
import avilaos.model.hardware.Disk;
import avilaos.model.hardware.DiskSnapshot;
import avilaos.os.disk.DiskScheduler;
import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.MouseEvent;
import javax.swing.BorderFactory;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.ToolTipManager;
import javax.swing.table.AbstractTableModel;

/**
 * Zona del disco de la ventana principal:
 * G04 mapa de bloques (libre / archivo por color / swap), G05 cabezal con su sentido,
 * G08 cola de E/S, G09 selector de politica en caliente y G10 indicador de la politica activa.
 * Solo lee DiskSnapshot; lo unico que cambia en el sistema es la politica, via DiskScheduler.
 */
public class PanelDisco extends Tema.PanelRedondeado {

    private static final Color LIBRE = new Color(0xF1ECEF);
    private static final Color SWAP = new Color(0xE8A24B);
    private static final Color[] PALETA_ARCHIVOS = {
        new Color(0xD98BA3), new Color(0x9C8FCF), new Color(0x7DC4A8), new Color(0x6FA8DC),
        new Color(0xE58F7B), new Color(0xB5C766), new Color(0xC58BD9), new Color(0x5FB8C2)
    };

    private final Disk disk;
    private final DiskScheduler scheduler;
    private final Cluster cluster;

    private final MapaBloques mapa = new MapaBloques();
    private final JLabel lblCabezal = Tema.etiqueta("-", Tema.NEGRITA, Tema.TEXTO);
    private final JLabel lblBloques = Tema.etiqueta("-", Tema.NORMAL, Tema.TEXTO_SUAVE);
    private final Tema.Pastilla pillPolitica = new Tema.Pastilla("POLITICA DISCO: -", Tema.LAVANDA_SUAVE, Tema.LAVANDA);
    private final JComboBox<DiskSchedulingPolicy> comboPolitica = new JComboBox<>(DiskSchedulingPolicy.values());
    private final ModeloCola modeloCola = new ModeloCola();
    private final JTable tablaCola = new JTable(modeloCola);
    private boolean actualizando = false;
    private DiskSnapshot ultima;

    public PanelDisco(Disk disk, DiskScheduler scheduler, Cluster cluster) {
        super(Tema.TARJETA, Tema.BORDE, 16);
        if (disk == null || scheduler == null || cluster == null) {
            throw new IllegalArgumentException("Falta el disco, su planificador o el cluster");
        }
        this.disk = disk;
        this.scheduler = scheduler;
        this.cluster = cluster;
        setLayout(new BorderLayout(0, 8));
        setBorder(BorderFactory.createEmptyBorder(12, 14, 12, 14));

        // Encabezado: titulo + cabezal (G05) + politica (G09/G10)
        JPanel cab = new JPanel(new BorderLayout(0, 4));
        cab.setOpaque(false);
        cab.add(Tema.etiqueta("Disco", Tema.SUBTITULO, Tema.TEXTO), BorderLayout.NORTH);
        JPanel fila = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        fila.setOpaque(false);
        fila.add(lblCabezal);
        fila.add(lblBloques);
        fila.add(pillPolitica);
        fila.add(Tema.etiqueta("Cambiar politica:", Tema.NORMAL, Tema.TEXTO_SUAVE));
        fila.add(comboPolitica);
        cab.add(fila, BorderLayout.CENTER);
        add(cab, BorderLayout.NORTH);

        comboPolitica.addActionListener(e -> {
            if (actualizando) {
                return;
            }
            DiskSchedulingPolicy elegida = (DiskSchedulingPolicy) comboPolitica.getSelectedItem();
            if (elegida != null && elegida != scheduler.getPolicy().getType()) {
                scheduler.changePolicy(elegida, cluster.getCurrentCycle());
                refresh();
            }
        });

        ToolTipManager.sharedInstance().registerComponent(mapa);
        add(mapa, BorderLayout.CENTER);

        // Leyenda + cola de E/S (G08)
        JPanel abajo = new JPanel(new BorderLayout(0, 4));
        abajo.setOpaque(false);
        abajo.add(leyenda(), BorderLayout.NORTH);
        tablaCola.setFont(Tema.NORMAL);
        tablaCola.setRowHeight(20);
        tablaCola.setFillsViewportHeight(true);
        JScrollPane sp = new JScrollPane(tablaCola);
        sp.setPreferredSize(new Dimension(300, 130));
        sp.setBorder(BorderFactory.createLineBorder(Tema.BORDE));
        abajo.add(sp, BorderLayout.CENTER);
        add(abajo, BorderLayout.SOUTH);
        refresh();
    }

    private JPanel leyenda() {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        p.setOpaque(false);
        p.add(muestra(LIBRE, "libre"));
        p.add(muestra(PALETA_ARCHIVOS[0], "archivo (un color por archivo)"));
        p.add(muestra(SWAP, "swap (proceso suspendido)"));
        p.add(Tema.etiqueta("borde oscuro = cabezal", Tema.PEQUENA, Tema.TEXTO_SUAVE));
        return p;
    }

    private static JLabel muestra(Color c, String texto) {
        JLabel l = Tema.etiqueta(" " + texto, Tema.PEQUENA, Tema.TEXTO_SUAVE);
        l.setIcon(new javax.swing.Icon() {
            @Override public void paintIcon(java.awt.Component x, Graphics g, int px, int py) {
                g.setColor(c);
                g.fillRect(px, py, 12, 12);
            }
            @Override public int getIconWidth() { return 12; }
            @Override public int getIconHeight() { return 12; }
        });
        return l;
    }

    /** Toma una foto del disco y repinta todo (se llama desde el Timer de la ventana). */
    public void refresh() {
        refresh(DiskSnapshot.take(disk, scheduler));
    }

    /** Dibuja una foto ya tomada. */
    public void refresh(DiskSnapshot s) {
        ultima = s;
        String sentido = s.getDirection() == HeadDirection.UP ? "↑ sube" : "↓ baja";
        lblCabezal.setText("Cabezal: bloque " + s.getHeadPosition() + "  " + sentido);
        lblBloques.setText(s.freeCount() + " libres de " + s.totalBlocks());
        pillPolitica.setText("POLITICA DISCO: " + s.getPolicy());
        actualizando = true;
        comboPolitica.setSelectedItem(s.getPolicy());
        actualizando = false;
        modeloCola.set(s);
        mapa.repaint();
    }

    public JComboBox<DiskSchedulingPolicy> getComboPolitica() { return comboPolitica; }
    public JTable getTablaCola() { return tablaCola; }
    public String getTextoCabezal() { return lblCabezal.getText(); }
    public String getTextoPolitica() { return pillPolitica.getText(); }
    public DiskSnapshot getUltimaFoto() { return ultima; }

    /** Color de un bloque segun su uso (para pruebas y leyenda). */
    public static Color colorDe(BlockInfo b) {
        if (b.isFree()) {
            return LIBRE;
        }
        if (b.getUse() == BlockUse.SUSPENDED_PROCESS) {
            return SWAP;
        }
        String dueno = b.getOwner() == null ? "" : b.getOwner();
        return PALETA_ARCHIVOS[Math.floorMod(dueno.hashCode(), PALETA_ARCHIVOS.length)];
    }

    /** Texto que se ve al pasar el mouse sobre un bloque. */
    public static String detalleDe(BlockInfo b) {
        if (b.isFree()) {
            return "Bloque " + b.getIndex() + ": libre";
        }
        String sig = b.getNext() == BlockInfo.NO_NEXT ? "fin de cadena" : "siguiente: " + b.getNext();
        if (b.getUse() == BlockUse.SUSPENDED_PROCESS) {
            return "Bloque " + b.getIndex() + ": swap del proceso " + b.getOwner() + " (" + sig + ")";
        }
        return "Bloque " + b.getIndex() + ": archivo " + b.getOwner() + " (" + sig + ")";
    }

    // ------------------------------------------------------------------ mapa de bloques

    private final class MapaBloques extends JPanel {
        private int cols = 1;
        private int celda = 10;

        MapaBloques() {
            setOpaque(false);
            setPreferredSize(new Dimension(520, 260));
        }

        private void calcular(int total) {
            int w = Math.max(getWidth(), 50);
            int h = Math.max(getHeight(), 50);
            int mejor = 4;
            int mejorCol = 1;
            for (int c = 1; c <= total; c++) {
                int filas = (total + c - 1) / c;
                int t = Math.min(w / c, h / filas);
                if (t > mejor) {
                    mejor = t;
                    mejorCol = c;
                }
            }
            cols = mejorCol;
            celda = mejor;
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            DiskSnapshot s = ultima;
            if (s == null) {
                return;
            }
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int total = s.totalBlocks();
            calcular(total);
            boolean conNumero = celda >= 16;
            g2.setFont(new Font("Segoe UI", Font.PLAIN, Math.max(7, Math.min(11, celda / 2))));
            FontMetrics fm = g2.getFontMetrics();
            for (int i = 0; i < total; i++) {
                int x = (i % cols) * celda;
                int y = (i / cols) * celda;
                BlockInfo b = s.getBlock(i);
                g2.setColor(colorDe(b));
                g2.fillRect(x + 1, y + 1, celda - 2, celda - 2);
                if (b.getUse() == BlockUse.SUSPENDED_PROCESS) {
                    g2.setColor(new Color(0x9A6414));
                    g2.drawLine(x + 2, y + celda - 3, x + celda - 3, y + 2);
                }
                if (conNumero) {
                    g2.setColor(b.isFree() ? Tema.TEXTO_SUAVE : Color.WHITE);
                    String t = String.valueOf(i);
                    g2.drawString(t, x + (celda - fm.stringWidth(t)) / 2, y + (celda + fm.getAscent()) / 2 - 2);
                }
            }
            // cabezal (G05): borde oscuro y flecha de su sentido
            int hp = s.getHeadPosition();
            if (hp >= 0 && hp < total) {
                int x = (hp % cols) * celda;
                int y = (hp / cols) * celda;
                g2.setColor(Tema.TEXTO);
                g2.setStroke(new BasicStroke(Math.max(2f, celda / 8f)));
                g2.drawRect(x + 1, y + 1, celda - 3, celda - 3);
                g2.setFont(new Font("Segoe UI", Font.BOLD, Math.max(10, celda - 4)));
                String flecha = s.getDirection() == HeadDirection.UP ? "↑" : "↓";
                FontMetrics f2 = g2.getFontMetrics();
                g2.drawString(flecha, x + (celda - f2.stringWidth(flecha)) / 2, y + (celda + f2.getAscent()) / 2 - 2);
            }
            g2.dispose();
        }

        @Override
        public String getToolTipText(MouseEvent e) {
            DiskSnapshot s = ultima;
            if (s == null || celda <= 0) {
                return null;
            }
            int c = e.getX() / celda;
            int f = e.getY() / celda;
            int i = f * cols + c;
            if (c >= cols || i < 0 || i >= s.totalBlocks()) {
                return null;
            }
            return detalleDe(s.getBlock(i));
        }
    }

    // ------------------------------------------------------------------ cola de E/S (G08)

    private static final class ModeloCola extends AbstractTableModel {
        private static final String[] COLS = {"Proceso", "Operacion", "Ruta / destino", "Bloque", "Llegada", "Estado"};
        private DiskSnapshot s;

        void set(DiskSnapshot nueva) {
            this.s = nueva;
            fireTableDataChanged();
        }

        @Override public int getRowCount() { return s == null ? 0 : s.queueSize(); }
        @Override public int getColumnCount() { return COLS.length; }
        @Override public String getColumnName(int c) { return COLS[c]; }

        @Override
        public Object getValueAt(int row, int col) {
            DiskSnapshot.QueueEntry q = s.getQueueEntry(row);
            switch (col) {
                case 0: return q.getProcessName() + " #" + q.getProcessId();
                case 1: return q.getOperation();
                case 2: return q.getPath();
                case 3: return q.getTargetBlock();
                case 4: return q.getArrivalCycle();
                default: return q.isInService() ? "EN SERVICIO" : "en espera";
            }
        }
    }
}
