/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package avilaos.gui;

import avilaos.datastructures.CustomLinkedList;
import avilaos.model.hardware.Cluster;
import avilaos.model.hardware.Computer;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSlider;
import javax.swing.Timer;

/**
 * Pantalla principal de la simulacion: encabezado con el estado del reloj, el
 * ciclo global, los controles (Iniciar / Pausar / Reanudar / Paso) y la velocidad,
 * y una tarjeta por cada computador.
 * Se refresca con un javax.swing.Timer, que ejecuta su codigo en el hilo de
 * Swing, asi que nunca se toca la pantalla desde el hilo del reloj.
 * @author Antonella Ruiz
 */
public class PanelCluster extends JPanel {

    // Cada cuantos ms se redibuja la pantalla (independiente de la velocidad del reloj)
    private static final int REFRESH_MS = 200;

    // Estados del reloj segun los botones
    private static final int DETENIDO = 0;
    private static final int CORRIENDO = 1;
    private static final int PAUSADO = 2;

    private final Cluster cluster;
    private final PanelComputador[] panels;
          private final PanelBuffers panelBuffers;   // S09: buffers y semaforos
        private final PanelLog panelLog;   // S11 / I06: log de eventos
    private int estado = DETENIDO;

    private final Tema.Pastilla pillCiclo =
            new Tema.Pastilla("CICLO 0", Tema.LAVANDA_SUAVE, Tema.LAVANDA);
    private final Tema.Pastilla pillEstado =
            new Tema.Pastilla("DETENIDO", Tema.ROSA_SUAVE, Tema.ROSA);
    private final Tema.Boton btnStart = new Tema.Boton("Iniciar", true);
    private final Tema.Boton btnPause = new Tema.Boton("Pausar", false);
    private final Tema.Boton btnResume = new Tema.Boton("Reanudar", false);
    private final Tema.Boton btnStep = new Tema.Boton("Paso", false);
    private final JSlider sliderVelocidad = new JSlider(1, 10, 5);
    private final JLabel lblVelocidad = Tema.etiqueta("", Tema.NORMAL, Tema.TEXTO_SUAVE);
    private final Timer refreshTimer;

    /** Arma la pantalla para un cluster ya creado (todavia sin iniciar). */
    public PanelCluster(Cluster cluster) {
        this.cluster = cluster;
        setLayout(new BorderLayout(0, 18));
        setBackground(Tema.FONDO);
        setBorder(BorderFactory.createEmptyBorder(22, 26, 26, 26));

        // Encabezado: titulo a la izquierda, controles a la derecha
        JPanel top = new JPanel(new BorderLayout());
        top.setOpaque(false);
        JPanel titulos = new JPanel(new GridLayout(2, 1));
        titulos.setOpaque(false);
        titulos.add(Tema.etiqueta("AvilaOS", Tema.TITULO, Tema.ROSA));
        titulos.add(Tema.etiqueta("Simulador de sistema operativo distribuido",
                Tema.NORMAL, Tema.TEXTO_SUAVE));
        top.add(titulos, BorderLayout.WEST);

        btnStart.addActionListener(e -> iniciar());
        btnPause.addActionListener(e -> pausar());
        btnResume.addActionListener(e -> reanudar());
        btnStep.addActionListener(e -> paso());

        JPanel fila1 = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 4));
        fila1.setOpaque(false);
        fila1.add(pillEstado);
        fila1.add(pillCiclo);
        fila1.add(btnStart);
        fila1.add(btnPause);
        fila1.add(btnResume);
        fila1.add(btnStep);

        // Velocidad: del 1 (lento) al 10 (rapido). Se arranca con el valor real del cluster.
        sliderVelocidad.setOpaque(false);
        sliderVelocidad.setValue(velocidadDesdeMs(cluster.getCycleDurationMs()));
        actualizarEtiquetaVelocidad();
        sliderVelocidad.addChangeListener(e -> {
            actualizarEtiquetaVelocidad();
            if (!sliderVelocidad.getValueIsAdjusting()) {
                cluster.setCycleDurationMs(msDesdeVelocidad(sliderVelocidad.getValue()));
            }
        });
        JPanel fila2 = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        fila2.setOpaque(false);
        fila2.add(Tema.etiqueta("Velocidad", Tema.NEGRITA, Tema.TEXTO));
        fila2.add(sliderVelocidad);
        fila2.add(lblVelocidad);

        JPanel controles = new JPanel();
        controles.setOpaque(false);
        controles.setLayout(new BoxLayout(controles, BoxLayout.Y_AXIS));
        controles.add(fila1);
        controles.add(fila2);
        
        // I05: crear procesos y buffers con la simulacion en marcha
        Tema.Boton btnNuevoProceso = new Tema.Boton("Nuevo proceso", false);
        Tema.Boton btnNuevoBuffer = new Tema.Boton("Nuevo buffer", false);
        btnNuevoProceso.addActionListener(e -> nuevoProceso());
        btnNuevoBuffer.addActionListener(e -> nuevoBuffer());
        JPanel fila3 = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 4));
        fila3.setOpaque(false);
        fila3.add(btnNuevoBuffer);
        fila3.add(btnNuevoProceso);
        controles.add(fila3);
        top.add(controles, BorderLayout.EAST);
        add(top, BorderLayout.NORTH);

        // Una tarjeta por computador, una al lado de la otra
        CustomLinkedList<Computer> computers = cluster.getComputersSnapshot();
        panels = new PanelComputador[computers.size()];
        JPanel center = new JPanel(new GridLayout(1, panels.length, 18, 0));
        center.setOpaque(false);
        for (int i = 0; i < panels.length; i++) {
            panels[i] = new PanelComputador(cluster, computers.get(i));
            center.add(panels[i]);
        }
        // Con scroll: si la ventana es baja, las tarjetas no se cortan
        javax.swing.JScrollPane scroll = new javax.swing.JScrollPane(center);
        scroll.setBorder(null);
        scroll.setOpaque(false);
        scroll.getViewport().setOpaque(false);
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        add(scroll, BorderLayout.CENTER);

        
                  // Debajo de las tarjetas: buffers (S09) a la izquierda y log de eventos a la derecha
          panelBuffers = new PanelBuffers(cluster);
          panelLog = new PanelLog(cluster.getLogger());
          JPanel sur = new JPanel(new GridLayout(1, 2, 18, 0));
          sur.setOpaque(false);
          sur.add(panelBuffers);
          sur.add(panelLog);
          add(sur, BorderLayout.SOUTH);
        actualizarEstado();
        refreshTimer = new Timer(REFRESH_MS, e -> refresh());
        refreshTimer.start();
    }

    // Velocidad 1..10 <-> ms por ciclo (10 = 100 ms, 1 = 1900 ms)
    private static int msDesdeVelocidad(int v) {
        return 2100 - v * 200;
    }

    private static int velocidadDesdeMs(int ms) {
        int v = Math.round((2100 - ms) / 200f);
        return Math.max(1, Math.min(10, v));
    }

    private void actualizarEtiquetaVelocidad() {
        lblVelocidad.setText(msDesdeVelocidad(sliderVelocidad.getValue()) + " ms por ciclo");
    }

    /** Inicia el reloj (solo se puede una vez). */
    private void iniciar() {
        cluster.start();
        estado = CORRIENDO;
        actualizarEstado();
    }

    private void pausar() {
        cluster.pause();
        estado = PAUSADO;
        actualizarEstado();
    }

    private void reanudar() {
        cluster.resume();
        estado = CORRIENDO;
        actualizarEstado();
    }

    /** Avanza un solo ciclo a mano (solo cuando el reloj no esta corriendo). */
    private void paso() {
        cluster.tick();
        refresh();
    }
    
        /** I05: abre la ventana para crear un proceso nuevo (con el reloj corriendo o en pausa). */
    private void nuevoProceso() {
        DialogosCreacion.nuevoProceso(javax.swing.SwingUtilities.getWindowAncestor(this), cluster);
        refresh();
    }

    /** I05: abre la ventana para crear un buffer nuevo. */
    private void nuevoBuffer() {
        DialogosCreacion.nuevoBuffer(javax.swing.SwingUtilities.getWindowAncestor(this), cluster);
        refresh();
    }

    /** Activa o desactiva los botones y cambia la pastilla segun el estado. */
    private void actualizarEstado() {
        btnStart.setEnabled(estado == DETENIDO);
        btnPause.setEnabled(estado == CORRIENDO);
        btnResume.setEnabled(estado == PAUSADO);
        btnStep.setEnabled(estado != CORRIENDO);
        if (estado == CORRIENDO) {
            pillEstado.setText("EN EJECUCION");
            pillEstado.cambiarColores(Tema.MENTA_SUAVE, new java.awt.Color(0x3E8E70));
        } else if (estado == PAUSADO) {
            pillEstado.setText("PAUSADO");
            pillEstado.cambiarColores(Tema.LAVANDA_SUAVE, Tema.LAVANDA);
        } else {
            pillEstado.setText("DETENIDO");
            pillEstado.cambiarColores(Tema.ROSA_SUAVE, Tema.ROSA);
        }
    }

    /** Actualiza el ciclo y todas las tarjetas de computador. */
    public void refresh() {
        pillCiclo.setText("CICLO " + cluster.getCurrentCycle());
        for (int i = 0; i < panels.length; i++) {
            panels[i].refresh();
        }
                  panelBuffers.refresh();
        panelLog.refresh();
    }

    /** Detiene el refresco y el reloj (llamar al cerrar la ventana). */
    public void shutdown() {
        refreshTimer.stop();
        cluster.stop();
    }
}