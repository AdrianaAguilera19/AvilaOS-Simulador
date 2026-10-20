/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package avilaos.gui;


import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.SwingConstants;
import javax.swing.plaf.basic.BasicProgressBarUI;

/**
 *
 * @author Antonella Ruiz
 */



/**
 * Colores, fuentes y componentes visuales compartidos por toda la interfaz.
 * Un solo lugar para cambiar el estilo de AvilaOS.
 * @author Antonella Ruiz
 */
public final class Tema {

    private Tema() { }

    // Paleta minimalista: crema, rosa polvo, lavanda y menta
    public static final Color FONDO = new Color(0xFAF6F7);
    public static final Color TARJETA = Color.WHITE;
    public static final Color BORDE = new Color(0xEDE3E8);
    public static final Color ROSA = new Color(0xD98BA3);
    public static final Color ROSA_SUAVE = new Color(0xFBE9EF);
    public static final Color LAVANDA = new Color(0x9C8FCF);
    public static final Color LAVANDA_SUAVE = new Color(0xEFEBFA);
    public static final Color MENTA = new Color(0x7DC4A8);
    public static final Color MENTA_SUAVE = new Color(0xE6F5EE);
    public static final Color TEXTO = new Color(0x3F3844);
    public static final Color TEXTO_SUAVE = new Color(0x9A919E);

    // Fuentes (Segoe UI viene en Windows; si no existe, Java usa otra parecida)
    public static final Font TITULO = new Font("Segoe UI", Font.BOLD, 24);
    public static final Font SUBTITULO = new Font("Segoe UI", Font.BOLD, 16);
    public static final Font NORMAL = new Font("Segoe UI", Font.PLAIN, 13);
    public static final Font NEGRITA = new Font("Segoe UI", Font.BOLD, 13);
    public static final Font PEQUENA = new Font("Segoe UI", Font.BOLD, 11);
    public static final Font NUMERO = new Font("Segoe UI", Font.BOLD, 22);

    /** Activa el suavizado de bordes para dibujar formas redondeadas. */
    private static Graphics2D suave(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        return g2;
    }

    /** Etiqueta con el estilo indicado. */
    public static JLabel etiqueta(String texto, Font fuente, Color color) {
        JLabel l = new JLabel(texto);
        l.setFont(fuente);
        l.setForeground(color);
        return l;
    }

    /** Panel con esquinas redondeadas, relleno y borde fino. */
    public static class PanelRedondeado extends JPanel {
        private final Color relleno;
        private final Color borde;
        private final int radio;

        public PanelRedondeado(Color relleno, Color borde, int radio) {
            this.relleno = relleno;
            this.borde = borde;
            this.radio = radio;
            setOpaque(false);
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = suave(g);
            g2.setColor(relleno);
            g2.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, radio, radio);
            if (borde != null) {
                g2.setColor(borde);
                g2.setStroke(new BasicStroke(1f));
                g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, radio, radio);
            }
            g2.dispose();
            super.paintComponent(g);
        }
    }

    /** Etiqueta en forma de pastilla (por ejemplo la politica o el modo). */
    public static class Pastilla extends JLabel {
        private Color relleno;

        public Pastilla(String texto, Color relleno, Color letra) {
            super(texto, SwingConstants.CENTER);
            this.relleno = relleno;
            setFont(PEQUENA);
            setForeground(letra);
            setBorder(BorderFactory.createEmptyBorder(4, 12, 4, 12));
            setOpaque(false);
        }

        public void cambiarColores(Color relleno, Color letra) {
            this.relleno = relleno;
            setForeground(letra);
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = suave(g);
            g2.setColor(relleno);
            g2.fillRoundRect(0, 0, getWidth(), getHeight(), getHeight(), getHeight());
            g2.dispose();
            super.paintComponent(g);
        }
    }

    /** Boton redondeado; principal = relleno rosa, secundario = relleno suave. */
    public static class Boton extends JButton {
        private final boolean principal;

        public Boton(String texto, boolean principal) {
            super(texto);
            this.principal = principal;
            setFont(NEGRITA);
            setForeground(principal ? Color.WHITE : ROSA);
            setContentAreaFilled(false);
            setBorderPainted(false);
            setFocusPainted(false);
            setOpaque(false);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            setBorder(BorderFactory.createEmptyBorder(8, 20, 8, 20));
        }
        
        /** Al deshabilitarlo se ve gris para que se note que no se puede usar. */
        @Override
        public void setEnabled(boolean activo) {
            super.setEnabled(activo);
            if (activo) {
                setForeground(principal ? Color.WHITE : ROSA);
            } else {
                setForeground(TEXTO_SUAVE);
            }
            setCursor(Cursor.getPredefinedCursor(activo ? Cursor.HAND_CURSOR : Cursor.DEFAULT_CURSOR));
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = suave(g);
            boolean encima = getModel().isRollover() && isEnabled();
            Color c;
            if (!isEnabled()) {
                c = new Color(0xF1ECEF);
            } else if (principal) {
                c = encima ? ROSA.darker() : ROSA;
            } else {
                c = encima ? new Color(0xF6D5E0) : ROSA_SUAVE;
            }
            g2.setColor(c);
            g2.fillRoundRect(0, 0, getWidth(), getHeight(), getHeight(), getHeight());
            g2.dispose();
            super.paintComponent(g);
        }
    }

    /** Barra de progreso delgada y redondeada. */
    public static JProgressBar barra() {
    return barra(Tema.LAVANDA, true);
    }

    /** Barra con el color indicado; avisaLlena la pinta de rosa al pasar 85%. */
    public static JProgressBar barra(Color relleno, boolean avisaLlena) {
        JProgressBar b = new JProgressBar();
        b.setBorderPainted(false);
        b.setOpaque(false);
        b.setPreferredSize(new Dimension(100, 12));
        b.setUI(new BasicProgressBarUI() {
            @Override
            public void paint(Graphics g, javax.swing.JComponent c) {
                Graphics2D g2 = suave(g);
                int w = c.getWidth();
                int h = c.getHeight();
                g2.setColor(LAVANDA_SUAVE);
                g2.fillRoundRect(0, 0, w, h, h, h);
                double fraccion = b.getMaximum() == 0 ? 0
                        : (double) b.getValue() / b.getMaximum();
                int ancho = (int) Math.round(w * fraccion);
                if (ancho > 0) {
                    // Lavanda normal; se vuelve rosa si la RAM esta casi llena
                    g2.setColor(avisaLlena && fraccion > 0.85 ? ROSA : relleno);
                    g2.fillRoundRect(0, 0, Math.max(ancho, h), h, h, h);
                }
                g2.dispose();
            }
        });
        return b;
    }

    /** Cajita con un numero grande y una leyenda (para los contadores de colas). */
    public static class Contador extends PanelRedondeado {
        private final JLabel numero = etiqueta("0", NUMERO, TEXTO);

        public Contador(String leyenda, Color fondo) {
            super(fondo, null, 16);
            setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
            setBorder(BorderFactory.createEmptyBorder(10, 14, 10, 14));
            JLabel l = etiqueta(leyenda, PEQUENA, TEXTO_SUAVE);
            numero.setAlignmentX(LEFT_ALIGNMENT);
            l.setAlignmentX(LEFT_ALIGNMENT);
            add(numero);
            add(l);
        }

        public void setValor(int valor) {
            numero.setText(String.valueOf(valor));
        }
    }

    /** Fila horizontal transparente alineada a la izquierda. */
    public static JPanel fila() {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        p.setOpaque(false);
        return p;
    }
}