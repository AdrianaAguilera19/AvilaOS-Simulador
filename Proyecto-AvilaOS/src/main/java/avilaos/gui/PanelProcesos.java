/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package avilaos.gui;

import avilaos.datastructures.CustomQueue;
import avilaos.enums.ProcessState;
import avilaos.enums.ProcessType;
import avilaos.model.hardware.Computer;
import avilaos.model.process.PCB;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.SwingConstants;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;

/**
 * Tabla de todos los procesos (PCB) de UN computador
 * @author Antonella Ruiz
 */
public class PanelProcesos extends JPanel {

    private static final int[] ANCHOS = {45, 95, 100, 200, 60, 85, 75, 50, 50, 85, 150, 70};
    private static final String[] COLUMNAS = {"ID", "Nombre", "Tipo", "Estado", "Prio.",
        "Deadline", "Quedan", "PC", "MAR", "Memoria", "Bloqueo", "Buffer"};

    private static final int COL_ESTADO = 3;
    // Columnas que se muestran en la version compacta: ID, Nombre, Estado, Prio., Quedan, PC, Bloqueo
    private static final int[] COLS_COMPACTA = {0, 1, 3, 4, 6, 7, 10};
    private static final int[] COLS_COMPLETA = {0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11};
    // Columnas numericas: se centran
    private static final boolean[] CENTRADA = {true, false, false, false, true, true, true, true, true, true, false, true};

    private final Computer computer;
    private final int[] cols;            // indices (de COLUMNAS) que muestra esta tabla
    private final ModeloProcesos modelo = new ModeloProcesos();
    private final JLabel lblTotal = Tema.etiqueta(" ", Tema.NORMAL, Tema.TEXTO_SUAVE);

    /**
     * @param computer computador cuyos procesos se muestran
     * @param compacta true: pocas columnas y filas bajas (para la tarjeta); false: todas las columnas
     * @param conTitulo true: muestra arriba "Procesos de ..." y el total
     */
    public PanelProcesos(Computer computer, boolean compacta, boolean conTitulo) {
        this.computer = computer;
        this.cols = compacta ? COLS_COMPACTA : COLS_COMPLETA;
        setLayout(new BorderLayout(0, 10));
        setOpaque(false);

        if (conTitulo) {
            JPanel top = new JPanel(new BorderLayout());
            top.setOpaque(false);
            top.add(Tema.etiqueta("Procesos de " + computer.getName(), Tema.SUBTITULO, Tema.TEXTO),
                    BorderLayout.WEST);
            top.add(lblTotal, BorderLayout.EAST);
            add(top, BorderLayout.NORTH);
        }

        JTable tabla = new JTable(modelo);
        tabla.setFont(compacta ? Tema.PEQUENA : Tema.NORMAL);
        tabla.setRowHeight(compacta ? 24 : 32);
        tabla.setShowGrid(false);
        tabla.setIntercellSpacing(new Dimension(0, 0));
        tabla.setForeground(Tema.TEXTO);
        tabla.setBackground(Tema.TARJETA);
        tabla.setSelectionBackground(Tema.ROSA_SUAVE);
        tabla.setSelectionForeground(Tema.TEXTO);
        tabla.setFillsViewportHeight(true);
        tabla.setAutoResizeMode(JTable.AUTO_RESIZE_ALL_COLUMNS);
        tabla.getTableHeader().setPreferredSize(new Dimension(10, compacta ? 26 : 34));        
        tabla.getTableHeader().setPreferredSize(new Dimension(10, compacta ? 30 : 38));
        tabla.getTableHeader().setDefaultRenderer(new RenderEncabezado(cols));
        tabla.setDefaultRenderer(Object.class, new RenderCelda(cols));
        for (int i = 0; i < cols.length; i++) {
            tabla.getColumnModel().getColumn(i).setPreferredWidth(ANCHOS[cols[i]]);
            if (cols[i] == COL_ESTADO) {
                tabla.getColumnModel().getColumn(i).setCellRenderer(new RenderEstado());
            }
        }

        JScrollPane scroll = new JScrollPane(tabla);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.getViewport().setBackground(Tema.TARJETA);
        if (compacta) {
            scroll.setPreferredSize(new Dimension(10, 100));
        }
        Tema.PanelRedondeado tarjeta = new Tema.PanelRedondeado(Tema.TARJETA, Tema.BORDE, 20);
        tarjeta.setLayout(new BorderLayout());
        tarjeta.setBorder(BorderFactory.createEmptyBorder(6, 6, 6, 6));
        tarjeta.add(scroll, BorderLayout.CENTER);
        add(tarjeta, BorderLayout.CENTER);
    }

    /** Vuelve a leer los procesos del computador y redibuja la tabla. */
    public void refrescar() {
        modelo.refrescar();
    }

    /** 
     * Encabezado
     * texto pequeno en mayusculas, fondo suave y linea inferior
     */
    private static class RenderEncabezado extends DefaultTableCellRenderer {

        private final int[] cols;

        RenderEncabezado(int[] cols) {
            this.cols = cols;
        }

        @Override
        public Component getTableCellRendererComponent(JTable t, Object v, boolean sel,
                boolean foco, int fila, int col) {
            super.getTableCellRendererComponent(t, v == null ? "" : v.toString().toUpperCase(),
                    false, false, fila, col);
            setFont(Tema.PEQUENA);
            setForeground(Tema.TEXTO_SUAVE);
            setBackground(Tema.FONDO);
            setHorizontalAlignment(CENTRADA[cols[col]] ? SwingConstants.CENTER : SwingConstants.LEFT);
            setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createMatteBorder(0, 0, 1, 0, Tema.BORDE),
                    BorderFactory.createEmptyBorder(0, 10, 0, 10)));
            return this;
        }
    }

    //Celda normal: filas alternadas, margen a los lados y numeros centrados. */
    private static class RenderCelda extends DefaultTableCellRenderer {

        private final int[] cols;

        RenderCelda(int[] cols) {
            this.cols = cols;
        }

        @Override
        public Component getTableCellRendererComponent(JTable t, Object v, boolean sel,
                boolean foco, int fila, int col) {
            super.getTableCellRendererComponent(t, v, sel, false, fila, col);
            setBorder(BorderFactory.createEmptyBorder(0, 10, 0, 10));
            setHorizontalAlignment(CENTRADA[cols[col]] ? SwingConstants.CENTER : SwingConstants.LEFT);
            if (!sel) {
                setBackground(fila % 2 == 0 ? Tema.TARJETA : new Color(0xFDFAFB));
            }
            Object texto = v;
            boolean vacio = texto != null && "-".equals(texto.toString());
            setForeground(vacio ? Tema.TEXTO_SUAVE : Tema.TEXTO);
            return this;
        }
    }

    /** 
     * Columna de estado
     * una pastilla de color segun el estado del proceso
     */
    private static class RenderEstado extends DefaultTableCellRenderer {

        private Color fondo = Tema.FONDO;
        private Color letra = Tema.TEXTO_SUAVE;
        private String texto = "";
        private boolean seleccionada;
        private int fila;

        @Override
        public Component getTableCellRendererComponent(JTable t, Object v, boolean sel,
                boolean foco, int fila, int col) {
            texto = v == null ? "" : v.toString();
            seleccionada = sel;
            this.fila = fila;
            if (texto.startsWith("running")) {
                fondo = Tema.MENTA_SUAVE;
                letra = new Color(0x3E8E70);
            } else if (texto.startsWith("ready")) {
                fondo = Tema.LAVANDA_SUAVE;
                letra = Tema.LAVANDA;
            } else if (texto.startsWith("blocked")) {
                fondo = Tema.ROSA_SUAVE;
                letra = Tema.ROSA;
            } else if (texto.contains("vencido")) {
                fondo = new Color(0xFDE7E4);
                letra = new Color(0xB3261E);
            } else if (texto.startsWith("terminated")) {
                fondo = new Color(0xF1EEF2);
                letra = Tema.TEXTO_SUAVE;
            } else {
                fondo = Tema.FONDO;
                letra = Tema.TEXTO_SUAVE;
            }
            return this;
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g2.setColor(seleccionada ? Tema.ROSA_SUAVE : (fila % 2 == 0 ? Tema.TARJETA : new Color(0xFDFAFB)));
            g2.fillRect(0, 0, getWidth(), getHeight());
            g2.setFont(Tema.PEQUENA);
            int ancho = g2.getFontMetrics().stringWidth(texto.toUpperCase()) + 22;
            int alto = 22;
            int x = 8;
            int y = (getHeight() - alto) / 2;
            g2.setColor(fondo);
            g2.fillRoundRect(x, y, ancho, alto, alto, alto);
            g2.setColor(letra);
            int base = y + (alto + g2.getFontMetrics().getAscent() - g2.getFontMetrics().getDescent()) / 2;
            g2.drawString(texto.toUpperCase(), x + 11, base);
            g2.dispose();
        }
    }

    /** Modelo de la tabla: una fila por proceso, armada desde los snapshots del computador. */
    private class ModeloProcesos extends AbstractTableModel {

        private PCB[] filas = new PCB[0];

        void refrescar() {
            PCB running = computer.getRunning();
            CustomQueue<PCB> listos = computer.getReadySnapshot();
            CustomQueue<PCB> bloqueados = computer.getBlockedSnapshot();
            CustomQueue<PCB> nuevos = computer.getNewSnapshot();
            CustomQueue<PCB> terminados = computer.getFinishedSnapshot();

            int total = (running == null ? 0 : 1) + listos.size() + bloqueados.size()
                    + nuevos.size() + terminados.size();
            PCB[] nuevas = new PCB[total];
            int k = 0;
            if (running != null) {
                nuevas[k++] = running;
            }
            k = copiar(listos, nuevas, k);
            k = copiar(bloqueados, nuevas, k);
            k = copiar(nuevos, nuevas, k);
            copiar(terminados, nuevas, k);
            filas = nuevas;
            lblTotal.setText(total + (total == 1 ? " proceso" : " procesos"));
            fireTableDataChanged();
        }

        private int copiar(CustomQueue<PCB> q, PCB[] destino, int desde) {
            for (int i = 0; i < q.size(); i++) {
                destino[desde++] = q.get(i);
            }
            return desde;
        }

        @Override
        public int getRowCount() {
            return filas.length;
        }

        @Override
        public int getColumnCount() {
            return cols.length;
        }

        @Override
        public String getColumnName(int c) {
            return COLUMNAS[cols[c]];
        }

        @Override
        public Object getValueAt(int r, int c) {
            PCB p = filas[r];
            switch (cols[c]) {
                case 0: return p.getId();
                case 1: return p.getName();
                case 2: return p.getType().toString().toLowerCase().replace('_', ' ');
                case 3:
                    if (p.getState() == ProcessState.TERMINATED && p.isDeadlineMissed()) {
                        return "terminated (vencido)";
                    }
                    return p.getState().toString().toLowerCase();
                case 4: return p.getPriority();
                case 5: return p.getDeadline();
                case 6: return p.getRemainingTime();
                case 7: return p.getProgramCounter();
                case 8: return p.getMemoryAddressRegister();
                case 9: return p.getMemorySize();
                case 10:
                    return p.getState() == ProcessState.BLOCKED
                            ? p.getBlockReason().toString().toLowerCase().replace('_', ' ') : "-";
                default:
                    return (p.getType() == ProcessType.PRODUCER || p.getType() == ProcessType.CONSUMER)
                            ? "B" + p.getAssociatedBufferId() : "-";
            }
        }
    }
}