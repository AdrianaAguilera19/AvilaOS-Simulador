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
import avilaos.model.hardware.UtilizationSeries;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Path2D;
import javax.swing.JPanel;

/**
 * Grafica comparativa de la utilizacion de la CPU de todos los computadores a lo largo
 * del tiempo (I08): una linea por computador, eje X = ciclo global, eje Y = 0 a 100 %.
 * Dibujada con Java2D (sin librerias externas). Lee solo copias del historial de P12
 * (UtilizationSeries); refresh() debe llamarse desde el hilo de Swing.
 */
public class PanelGrafica extends JPanel {

    /** Ventana (en ciclos) del modo "ultimos ciclos". */
    public static final int VENTANA_RECIENTE = 20;

    // Maximo de puntos que se dibujan por linea (el historial puede tener 10000 muestras)
    private static final int MAX_PUNTOS = 800;

    private static final Color[] COLORES = {
        Tema.ROSA, Tema.LAVANDA, Tema.MENTA,
        new Color(0xE8B86A), new Color(0x6FA8DC), new Color(0xC48B6F)
    };

    private final Cluster cluster;
    private boolean recientes = false;

    // Datos ya preparados para dibujar (se actualizan en refresh)
    private String[] nombres = new String[0];
    private long[][] ciclos = new long[0][];
    private double[][] valores = new double[0][];

    public PanelGrafica(Cluster cluster) {
        if (cluster == null) {
            throw new IllegalArgumentException("La grafica necesita un cluster");
        }
        this.cluster = cluster;
        setBackground(Tema.TARJETA);
        setOpaque(true);
    }

    /**
     * Elige que se grafica.
     * @param recientes true = utilizacion de los ultimos VENTANA_RECIENTE ciclos;
     *                  false = utilizacion acumulada desde el inicio
     */
    public void setRecientes(boolean recientes) {
        this.recientes = recientes;
        refresh();
    }

    public boolean isRecientes() {
        return recientes;
    }

    /** Vuelve a leer el historial de cada computador y redibuja. */
    public void refresh() {
        CustomLinkedList<Computer> pcs = cluster.getComputersSnapshot();
        int n = pcs.size();
        String[] nom = new String[n];
        long[][] cic = new long[n][];
        double[][] val = new double[n][];
        for (int k = 0; k < n; k++) {
            Computer c = pcs.get(k);
            nom[k] = c.getName();
            UtilizationSeries s = c.getUtilizationSeries();
            int paso = Math.max(1, s.size() / MAX_PUNTOS);
            int cuantos = s.size() == 0 ? 0 : (s.size() - 1) / paso + 1;
            boolean faltaUltimo = s.size() > 0 && (s.size() - 1) % paso != 0;
            int total = cuantos + (faltaUltimo ? 1 : 0);
            cic[k] = new long[total];
            val[k] = new double[total];
            for (int j = 0; j < total; j++) {
                int i = (faltaUltimo && j == total - 1) ? s.size() - 1 : j * paso;   // siempre incluye la ultima
                cic[k][j] = s.getCycle(i);
                val[k][j] = recientes ? s.getRecentUtilization(i, VENTANA_RECIENTE) : s.getUtilization(i);
            }
        }
        nombres = nom;
        ciclos = cic;
        valores = val;
        repaint();
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        int izq = 46, der = 18, arriba = 40, abajo = 34;
        int x0 = izq, y0 = arriba;
        int pw = Math.max(10, getWidth() - izq - der);
        int ph = Math.max(10, getHeight() - arriba - abajo);

        // Rango de ciclos de todas las lineas
        long minC = Long.MAX_VALUE, maxC = Long.MIN_VALUE;
        for (int k = 0; k < ciclos.length; k++) {
            if (ciclos[k].length > 0) {
                minC = Math.min(minC, ciclos[k][0]);
                maxC = Math.max(maxC, ciclos[k][ciclos[k].length - 1]);
            }
        }
        boolean hayDatos = maxC != Long.MIN_VALUE;

        // Cuadricula horizontal y etiquetas del eje Y
        g2.setFont(Tema.PEQUENA);
        for (int p = 0; p <= 100; p += 25) {
            int y = y0 + ph - (int) Math.round(ph * p / 100.0);
            g2.setColor(p == 0 ? Tema.TEXTO_SUAVE : Tema.BORDE);
            g2.drawLine(x0, y, x0 + pw, y);
            g2.setColor(Tema.TEXTO_SUAVE);
            String t = p + "%";
            g2.drawString(t, x0 - 8 - g2.getFontMetrics().stringWidth(t), y + 4);
        }

        if (!hayDatos) {
            g2.setColor(Tema.TEXTO_SUAVE);
            g2.setFont(Tema.NORMAL);
            String t = "Todavia no hay datos: inicia la simulacion";
            g2.drawString(t, x0 + (pw - g2.getFontMetrics().stringWidth(t)) / 2, y0 + ph / 2);
            dibujarLeyenda(g2, x0, 12);
            g2.dispose();
            return;
        }

        // Eje X: 5 marcas con el numero de ciclo
        long rango = Math.max(1, maxC - minC);
        g2.setFont(Tema.PEQUENA);
        g2.setColor(Tema.TEXTO_SUAVE);
        for (int m = 0; m <= 4; m++) {
            long c = minC + Math.round(rango * m / 4.0);
            int x = x0 + (int) Math.round(pw * m / 4.0);
            String t = String.valueOf(c);
            g2.drawLine(x, y0 + ph, x, y0 + ph + 4);
            int ancho = g2.getFontMetrics().stringWidth(t);
            g2.drawString(t, Math.max(x0 - ancho / 2, Math.min(x - ancho / 2, x0 + pw - ancho)), y0 + ph + 18);
        }
        String ejeX = "Ciclo";
        g2.drawString(ejeX, x0 + pw - g2.getFontMetrics().stringWidth(ejeX), y0 + ph + 31);

        // Una linea por computador
        g2.setStroke(new BasicStroke(2.2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        for (int k = 0; k < ciclos.length; k++) {
            int n = ciclos[k].length;
            if (n == 0) {
                continue;
            }
            g2.setColor(color(k));
            Path2D.Double ruta = new Path2D.Double();
            double ultimoX = 0, ultimoY = 0;
            for (int j = 0; j < n; j++) {
                double x = x0 + pw * (double) (ciclos[k][j] - minC) / rango;
                double y = y0 + ph - ph * Math.max(0.0, Math.min(1.0, valores[k][j]));
                if (j == 0) {
                    ruta.moveTo(x, y);
                } else {
                    ruta.lineTo(x, y);
                }
                ultimoX = x;
                ultimoY = y;
            }
            g2.draw(ruta);
            g2.fillOval((int) Math.round(ultimoX) - 4, (int) Math.round(ultimoY) - 4, 8, 8);
        }
        dibujarLeyenda(g2, x0, 12);
        g2.dispose();
    }

    /** Leyenda arriba: un punto de color, el nombre y el valor mas reciente de cada computador. */
    private void dibujarLeyenda(Graphics2D g2, int x, int y) {
        g2.setFont(Tema.NEGRITA);
        int cursor = x;
        for (int k = 0; k < nombres.length; k++) {
            g2.setColor(color(k));
            g2.fillOval(cursor, y, 11, 11);
            g2.setColor(Tema.TEXTO);
            String t = nombres[k];
            if (k < valores.length && valores[k].length > 0) {
                t += "  " + Math.round(valores[k][valores[k].length - 1] * 100) + "%";
            }
            g2.drawString(t, cursor + 16, y + 10);
            cursor += 16 + g2.getFontMetrics().stringWidth(t) + 22;
        }
    }

    /** Color fijo de cada computador (se repite si hay mas computadores que colores). */
    static Color color(int k) {
        return COLORES[k % COLORES.length];
    }
}
