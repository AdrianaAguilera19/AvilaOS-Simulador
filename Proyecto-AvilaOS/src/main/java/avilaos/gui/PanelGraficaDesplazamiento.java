/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package avilaos.gui;

import avilaos.enums.DiskSchedulingPolicy;
import avilaos.metrics.DiskDisplacementSeries;
import avilaos.os.disk.DiskRecordLog;
import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Path2D;
import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;

/**
 * J15: grafica del desplazamiento acumulado del cabezal contra el tiempo. Eje X = ciclo en que termina cada
 * solicitud, eje Y = bloques recorridos por el cabezal. Dibuja una linea por cada politica de disco que
 * se ha usado (lo que esa politica ha aportado al desplazamiento, para poder compararlas), una linea
 * punteada con el total y una marca vertical en cada cambio de politica.
 * Dibujada con Java2D (sin librerias externas). Los datos salen de DiskDisplacementSeries, que es una foto
 * del DiskRecordLog; refresh() debe llamarse desde el hilo de Swing.
 */
public class PanelGraficaDesplazamiento extends Tema.PanelRedondeado {

    // Maximo de puntos que se dibujan por linea (el registro puede tener miles de solicitudes)
    private static final int MAX_PUNTOS = 600;

    // Un color fijo por politica de disco (segun su orden en el enum)
    private static final Color[] COLORES = {
        Tema.ROSA, Tema.LAVANDA, Tema.MENTA, new Color(0xE8B86A), new Color(0x6FA8DC)
    };

    private final DiskRecordLog recordLog;
    private final JLabel lblResumen = Tema.etiqueta("-", Tema.NORMAL, Tema.TEXTO_SUAVE);
    private final Lienzo lienzo = new Lienzo();

    // Ultima foto tomada y cuantas solicitudes tenia (para no redibujar si no hay nada nuevo)
    private DiskDisplacementSeries serie = DiskDisplacementSeries.from(null);
    private int ultimaCantidad = -1;

    public PanelGraficaDesplazamiento(DiskRecordLog recordLog) {
        super(Tema.TARJETA, Tema.BORDE, 16);
        this.recordLog = recordLog;
        setLayout(new BorderLayout(0, 8));
        setBorder(BorderFactory.createEmptyBorder(12, 14, 12, 14));

        JPanel encabezado = new JPanel(new BorderLayout(12, 0));
        encabezado.setOpaque(false);
        encabezado.add(Tema.etiqueta("Desplazamiento acumulado del cabezal", Tema.SUBTITULO, Tema.TEXTO),
                BorderLayout.WEST);
        encabezado.add(lblResumen, BorderLayout.CENTER);
        add(encabezado, BorderLayout.NORTH);
        add(lienzo, BorderLayout.CENTER);
        refresh();
    }

    /** Vuelve a tomar la foto del registro del disco y redibuja, solo si hay solicitudes nuevas. */
    public void refresh() {
        int cantidad = recordLog == null ? 0 : recordLog.size();
        if (cantidad == ultimaCantidad) {
            return;
        }
        ultimaCantidad = cantidad;
        serie = DiskDisplacementSeries.from(recordLog);
        lblResumen.setText(textoResumen(serie));
        lienzo.repaint();
    }

    private static String textoResumen(DiskDisplacementSeries s) {
        if (s.size() == 0) {
            return "Sin solicitudes atendidas todavia";
        }
        return s.size() + " solicitudes atendidas  |  desplazamiento total " + s.total() + " bloques  |  "
                + s.policyChangeIndexes().length + " cambios de politica";
    }

    /** Color fijo de cada politica de disco. */
    public static Color colorDe(DiskSchedulingPolicy politica) {
        return COLORES[politica.ordinal() % COLORES.length];
    }

    /** Politicas con las que se atendio al menos una solicitud, en el orden del enum (las que tienen linea). */
    public DiskSchedulingPolicy[] getPoliticasUsadas() {
        return politicasUsadas(serie);
    }

    private static DiskSchedulingPolicy[] politicasUsadas(DiskDisplacementSeries s) {
        DiskSchedulingPolicy[] todas = DiskSchedulingPolicy.values();
        boolean[] usada = new boolean[todas.length];
        int cuantas = 0;
        for (int i = 0; i < s.size(); i++) {
            int o = s.policyAt(i).ordinal();
            if (!usada[o]) {
                usada[o] = true;
                cuantas++;
            }
        }
        DiskSchedulingPolicy[] r = new DiskSchedulingPolicy[cuantas];
        int k = 0;
        for (int o = 0; o < todas.length; o++) {
            if (usada[o]) {
                r[k++] = todas[o];
            }
        }
        return r;
    }

    public DiskDisplacementSeries getSerie() { return serie; }

    public String getTextoResumen() { return lblResumen.getText(); }

    /** Posiciones de la serie que se dibujan: todas, o un salto fijo si son demasiadas (siempre incluye la ultima). */
    private static int[] indicesAMostrar(int n) {
        int paso = Math.max(1, n / MAX_PUNTOS);
        int cuantos = n == 0 ? 0 : (n - 1) / paso + 1;
        boolean faltaUltimo = n > 0 && (n - 1) % paso != 0;
        int[] r = new int[cuantos + (faltaUltimo ? 1 : 0)];
        for (int j = 0; j < r.length; j++) {
            r[j] = (faltaUltimo && j == r.length - 1) ? n - 1 : j * paso;
        }
        return r;
    }

    // ------------------------------------------------------------------ dibujo

    private final class Lienzo extends JPanel {

        Lienzo() {
            setOpaque(false);
            setPreferredSize(new Dimension(600, 240));
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            DiskDisplacementSeries s = serie;   // foto: no cambia mientras se dibuja
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

            int izq = 56, der = 18, arriba = 34, abajo = 34;
            int x0 = izq, y0 = arriba;
            int pw = Math.max(10, getWidth() - izq - der);
            int ph = Math.max(10, getHeight() - arriba - abajo);
            int n = s.size();

            long maxY = Math.max(4, s.total());
            long minC = n == 0 ? 0 : s.cycleAt(0);
            long maxC = n == 0 ? 1 : s.cycleAt(n - 1);
            long rango = Math.max(1, maxC - minC);

            // Cuadricula horizontal y etiquetas del eje Y
            g2.setFont(Tema.PEQUENA);
            for (int m = 0; m <= 4; m++) {
                int y = y0 + ph - (int) Math.round(ph * m / 4.0);
                g2.setColor(m == 0 ? Tema.TEXTO_SUAVE : Tema.BORDE);
                g2.drawLine(x0, y, x0 + pw, y);
                g2.setColor(Tema.TEXTO_SUAVE);
                String t = String.valueOf(Math.round(maxY * m / 4.0));
                g2.drawString(t, x0 - 8 - g2.getFontMetrics().stringWidth(t), y + 4);
            }
            g2.setColor(Tema.TEXTO_SUAVE);
            g2.drawString("Bloques", 4, y0 - 8);

            if (n == 0) {
                g2.setFont(Tema.NORMAL);
                String t = "Todavia no hay solicitudes atendidas: inicia la simulacion";
                g2.drawString(t, x0 + (pw - g2.getFontMetrics().stringWidth(t)) / 2, y0 + ph / 2);
                g2.dispose();
                return;
            }

            // Eje X: 5 marcas con el numero de ciclo
            g2.setFont(Tema.PEQUENA);
            g2.setColor(Tema.TEXTO_SUAVE);
            for (int m = 0; m <= 4; m++) {
                long c = minC + Math.round(rango * m / 4.0);
                int x = x0 + (int) Math.round(pw * m / 4.0);
                String t = String.valueOf(c);
                int ancho = g2.getFontMetrics().stringWidth(t);
                g2.drawLine(x, y0 + ph, x, y0 + ph + 4);
                g2.drawString(t, Math.max(x0 - ancho / 2, Math.min(x - ancho / 2, x0 + pw - ancho)), y0 + ph + 18);
            }
            String ejeX = "Ciclo";
            g2.drawString(ejeX, x0 + pw - g2.getFontMetrics().stringWidth(ejeX), y0 + ph + 31);

            // Marcas verticales donde cambia la politica
            int[] cambios = s.policyChangeIndexes();
            g2.setStroke(new BasicStroke(1f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10f,
                    new float[] {3f, 3f}, 0f));
            int ultimoRotulo = -1000;
            for (int k = 0; k < cambios.length; k++) {
                int x = x0 + (int) Math.round(pw * (double) (s.cycleAt(cambios[k]) - minC) / rango);
                g2.setColor(Tema.TEXTO_SUAVE);
                g2.drawLine(x, y0, x, y0 + ph);
                if (x - ultimoRotulo > 44) {   // evita encimar los rotulos de marcas muy juntas
                    g2.drawString(s.policyAt(cambios[k]).toString(), x + 3, y0 + 11);
                    ultimoRotulo = x;
                }
            }

            // Una linea por politica usada
            int[] idx = indicesAMostrar(n);
            DiskSchedulingPolicy[] usadas = politicasUsadas(s);
            g2.setStroke(new BasicStroke(2.4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            for (int k = 0; k < usadas.length; k++) {
                long[] ys = s.cumulativeOf(usadas[k]);
                g2.setColor(colorDe(usadas[k]));
                linea(g2, s, ys, idx, x0, y0, pw, ph, minC, rango, maxY);
            }

            // Linea punteada con el total
            g2.setStroke(new BasicStroke(1.8f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10f,
                    new float[] {6f, 4f}, 0f));
            g2.setColor(Tema.TEXTO);
            long[] total = new long[n];
            for (int i = 0; i < n; i++) {
                total[i] = s.cumulativeAt(i);
            }
            linea(g2, s, total, idx, x0, y0, pw, ph, minC, rango, maxY);

            // Leyenda arriba: cada politica con su aporte y el total
            g2.setFont(Tema.NEGRITA);
            int cursor = x0;
            for (int k = 0; k < usadas.length; k++) {
                long[] ys = s.cumulativeOf(usadas[k]);
                g2.setColor(colorDe(usadas[k]));
                g2.fillOval(cursor, 8, 11, 11);
                g2.setColor(Tema.TEXTO);
                String t = usadas[k] + "  " + ys[n - 1];
                g2.drawString(t, cursor + 16, 18);
                cursor += 16 + g2.getFontMetrics().stringWidth(t) + 22;
            }
            g2.setColor(Tema.TEXTO);
            g2.fillRect(cursor, 12, 11, 3);
            g2.drawString("Total  " + s.total(), cursor + 16, 18);
            g2.dispose();
        }

        /** Dibuja una serie de valores (uno por solicitud) como una linea; con pocos puntos tambien los marca. */
        private void linea(Graphics2D g2, DiskDisplacementSeries s, long[] valores, int[] idx,
                int x0, int y0, int pw, int ph, long minC, long rango, long maxY) {
            Path2D.Double ruta = new Path2D.Double();
            for (int j = 0; j < idx.length; j++) {
                double x = x0 + pw * (double) (s.cycleAt(idx[j]) - minC) / rango;
                double y = y0 + ph - ph * (double) valores[idx[j]] / maxY;
                if (j == 0) {
                    ruta.moveTo(x, y);
                } else {
                    ruta.lineTo(x, y);
                }
                if (idx.length <= 40) {
                    g2.fillOval((int) Math.round(x) - 3, (int) Math.round(y) - 3, 6, 6);
                }
            }
            g2.draw(ruta);
        }
    }
}