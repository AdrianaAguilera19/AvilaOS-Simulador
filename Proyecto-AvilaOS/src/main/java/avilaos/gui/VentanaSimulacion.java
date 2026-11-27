/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package avilaos.gui;

import avilaos.model.hardware.Cluster;
import avilaos.model.hardware.Computer;
import avilaos.model.hardware.Disk;
import avilaos.model.process.PCB;
import avilaos.os.disk.DiskRecordLog;
import avilaos.os.filesystem.DiskIoCoordinator;
import avilaos.os.filesystem.PermissionMatrix;
import avilaos.os.filesystem.SecuredFileSystem;
import avilaos.os.filesystem.UserSession;
import avilaos.persistence.FileSystemData;
import avilaos.persistence.SimulationSetup.SimulationBundle;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import javax.swing.BorderFactory;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSlider;
import javax.swing.JSplitPane;
import javax.swing.JTabbedPane;
import javax.swing.Timer;

/**
 * G19: ventana principal integrada de P2. Un solo computador y su disco, con cuatro zonas visibles:
 * mapa de bloques con cabezal y cola de E/S (PanelDisco), tarjeta del computador con sus colas
 * (PanelComputador + PanelColas), y pestanas con sistema de archivos / tabla de asignacion,
 * metricas y log.
 *
 * Aqui tambien se cablea la simulacion: el DiskIoCoordinator se registra en el reloj del cluster
 * (despues del computador, igual que en las pruebas) y los procesos con operacion de disco entran
 * por el coordinador. G18: el reloj corre en su hilo; la ventana solo lee fotos cada 200 ms con un Timer de Swing.
 */
public class VentanaSimulacion extends JPanel {

    private static final int REFRESH_MS = 200;
    private static final int DETENIDO = 0;
    private static final int CORRIENDO = 1;
    private static final int PAUSADO = 2;

    private final SimulationBundle bundle;
    private final Cluster cluster;
    private final Computer computer;
    private final DiskIoCoordinator coordinator;
    private final DiskRecordLog recordLog = new DiskRecordLog();
    private final UserSession session = new UserSession();

    private final PanelComputador panelComputador;
    private final PanelColas panelColas;
    private final PanelDisco panelDisco;
    private final PanelSistemaArchivos panelFs;
    private final PanelModoUsuario panelModo;
    private final PanelAccionesArchivo panelAcciones;
    private final PanelMetricasIntegrado panelMetricas;
    private final PanelLog panelLog;
    private final PanelCargaJson panelCarga;   // puede ser null (para guardar el FS)

    private final Tema.Pastilla pillCiclo = new Tema.Pastilla("CICLO 0", Tema.LAVANDA_SUAVE, Tema.LAVANDA);
    private final Tema.Pastilla pillEstado = new Tema.Pastilla("DETENIDO", Tema.ROSA_SUAVE, Tema.ROSA);
    private final Tema.Boton btnStart = new Tema.Boton("Iniciar", true);
    private final Tema.Boton btnPause = new Tema.Boton("Pausar", false);
    private final Tema.Boton btnResume = new Tema.Boton("Reanudar", false);
    private final Tema.Boton btnStep = new Tema.Boton("Paso", false);
    private final Tema.Boton btnGuardar = new Tema.Boton("Guardar FS (JSON)", false);
    private final JSlider sliderVelocidad = new JSlider(1, 10, 5);
    private final JLabel lblVelocidad = Tema.etiqueta("", Tema.NORMAL, Tema.TEXTO_SUAVE);
    private final Timer refreshTimer;
    private int estado = DETENIDO;

    public VentanaSimulacion(SimulationBundle bundle) {
        this(bundle, null);
    }

    public VentanaSimulacion(SimulationBundle bundle, PanelCargaJson panelCarga) {
        if (bundle == null) {
            throw new IllegalArgumentException("Falta la simulacion armada");
        }
        this.bundle = bundle;
        this.panelCarga = panelCarga;
        this.cluster = bundle.getCluster();
        this.computer = bundle.getComputer();
        Disk disk = bundle.getDisk();
        FileSystemData fs = bundle.getFileSystemData();

        // Cableado: FS con permisos, coordinador de E/S dentro del reloj y procesos iniciales
        SecuredFileSystem sfs = new SecuredFileSystem(fs.getFileSystem(), new PermissionMatrix(fs.getFileTree()));
        coordinator = new DiskIoCoordinator(computer, disk, bundle.getDiskScheduler(), sfs, recordLog, cluster.getLogger());
        cluster.registerClockable(coordinator);
        for (int i = 0; i < bundle.getInitialProcesses().size(); i++) {
            PCB p = bundle.getInitialProcesses().get(i);
            if (p.hasDiskOperation()) {
                coordinator.submitProcess(p);
            } else {
                cluster.submit(p);
            }
        }

        // Paneles
        panelComputador = new PanelComputador(cluster, computer);
        panelColas = new PanelColas(computer);
        panelDisco = new PanelDisco(disk, bundle.getDiskScheduler(), cluster);
        panelModo = new PanelModoUsuario(session, cluster.getLogger());
        panelFs = new PanelSistemaArchivos(sfs);
        panelAcciones = new PanelAccionesArchivo(coordinator, panelModo, panelFs);
        panelMetricas = new PanelMetricasIntegrado(cluster, recordLog);
        panelLog = new PanelLog(cluster.getLogger());
        panelModo.registrarControlSoloAdmin(panelDisco.getComboPolitica());

        setLayout(new BorderLayout(0, 8));
        setBackground(Tema.FONDO);
        setBorder(BorderFactory.createEmptyBorder(10, 16, 12, 16));
        add(encabezado(), BorderLayout.NORTH);

        JPanel arriba = new JPanel(new GridLayout(1, 2, 8, 0));
        arriba.setOpaque(false);
        arriba.add(panelComputador);
        arriba.add(panelDisco);

        JPanel archivos = new JPanel(new BorderLayout(0, 6));
        archivos.setOpaque(false);
        archivos.add(panelModo, BorderLayout.NORTH);
        archivos.add(panelFs, BorderLayout.CENTER);
        archivos.add(panelAcciones, BorderLayout.SOUTH);

        JTabbedPane pestanas = new JTabbedPane();
        pestanas.addTab("Colas y suspendidos", panelColas);
        pestanas.addTab("Sistema de archivos", archivos);
        pestanas.addTab("Metricas", panelMetricas);
        pestanas.addTab("Log de eventos", panelLog);

        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, arriba, pestanas);
        split.setResizeWeight(0.6);
        split.setBorder(null);
        split.setOpaque(false);
        add(split, BorderLayout.CENTER);

        refreshTimer = new Timer(REFRESH_MS, e -> refresh());
        refreshTimer.start();
        actualizarEstado();
        refresh();
    }

    private JPanel encabezado() {
        JPanel top = new JPanel(new BorderLayout());
        top.setOpaque(false);
        JPanel titulos = new JPanel(new GridLayout(2, 1));
        titulos.setOpaque(false);
        titulos.add(Tema.etiqueta("AvilaOS", Tema.TITULO, Tema.ROSA));
        titulos.add(Tema.etiqueta("Planificacion, memoria virtual y sistema de archivos",
                Tema.NORMAL, Tema.TEXTO_SUAVE));
        top.add(titulos, BorderLayout.WEST);

        btnStart.addActionListener(e -> {
            cluster.start();
            estado = CORRIENDO;
            actualizarEstado();
        });
        btnPause.addActionListener(e -> {
            cluster.pause();
            estado = PAUSADO;
            actualizarEstado();
        });
        btnResume.addActionListener(e -> {
            cluster.resume();
            estado = CORRIENDO;
            actualizarEstado();
        });
        btnStep.addActionListener(e -> {
            cluster.tick();
            refresh();
        });
        btnGuardar.addActionListener(e -> guardarFs());

        sliderVelocidad.setOpaque(false);
        sliderVelocidad.setValue(velocidadDesdeMs(cluster.getCycleDurationMs()));
        actualizarEtiquetaVelocidad();
        sliderVelocidad.addChangeListener(e -> {
            actualizarEtiquetaVelocidad();
            if (!sliderVelocidad.getValueIsAdjusting()) {
                cluster.setCycleDurationMs(msDesdeVelocidad(sliderVelocidad.getValue()));
            }
        });

        JPanel fila = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 4));
        fila.setOpaque(false);
        fila.add(pillEstado);
        fila.add(pillCiclo);
        fila.add(btnStart);
        fila.add(btnPause);
        fila.add(btnResume);
        fila.add(btnStep);
        fila.add(btnGuardar);
        fila.add(Tema.etiqueta("Velocidad", Tema.NEGRITA, Tema.TEXTO));
        fila.add(sliderVelocidad);
        fila.add(lblVelocidad);
        top.add(fila, BorderLayout.EAST);
        return top;
    }

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

    private void guardarFs() {
        JFileChooser fc = new JFileChooser();
        if (fc.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        FileSystemData fs = bundle.getFileSystemData();
        String ruta = fc.getSelectedFile().getAbsolutePath();
        if (panelCarga != null) {
            panelCarga.guardarFsEnArchivo(fs.getFileTree(), fs.getAllocator(), fs.getTotalBlocks(),
                    bundle.getDisk().getHead().getPosition(), ruta);
        } else {
            try {
                avilaos.persistence.FileSystemJsonManager.save(fs.getFileTree(), fs.getAllocator(),
                        fs.getTotalBlocks(), bundle.getDisk().getHead().getPosition(), ruta);
            } catch (avilaos.persistence.ConfigException ex) {
                javax.swing.JOptionPane.showMessageDialog(this, ex.getMessage());
            }
        }
    }

    /** Refresca todos los paneles leyendo fotos; se llama desde el Timer (hilo de Swing). */
    public void refresh() {
        long ciclo = cluster.getCurrentCycle();
        pillCiclo.setText("CICLO " + ciclo);
        panelModo.setCicloActual(ciclo);
        panelAcciones.setCicloActual(ciclo);
        panelComputador.refresh();
        panelColas.refresh();
        panelDisco.refresh();
        panelFs.refresh();
        panelMetricas.refresh();
        panelLog.refresh();
    }

    /** Detiene el refresco y el reloj. */
    public void shutdown() {
        refreshTimer.stop();
        cluster.stop();
    }

    public static void mostrar(JFrame ventana, SimulationBundle bundle, PanelCargaJson carga) {
        if (ventana.getContentPane() instanceof VentanaSimulacion) {
            ((VentanaSimulacion) ventana.getContentPane()).shutdown();
        }
        ventana.setContentPane(new VentanaSimulacion(bundle, carga));
        ventana.revalidate();
        ventana.repaint();
    }

    public Cluster getCluster() { return cluster; }
    public DiskIoCoordinator getCoordinator() { return coordinator; }
    public DiskRecordLog getRecordLog() { return recordLog; }
    public PanelDisco getPanelDisco() { return panelDisco; }
    public PanelColas getPanelColas() { return panelColas; }
    public PanelComputador getPanelComputador() { return panelComputador; }
    public PanelSistemaArchivos getPanelFs() { return panelFs; }
    public PanelMetricasIntegrado getPanelMetricas() { return panelMetricas; }
    public PanelLog getPanelLog() { return panelLog; }
}