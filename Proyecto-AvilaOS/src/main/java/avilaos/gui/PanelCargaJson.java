package avilaos.gui;

import avilaos.os.filesystem.ChainAllocator;
import avilaos.os.filesystem.FileTree;
import avilaos.persistence.ConfigException;
import avilaos.persistence.FileSystemData;
import avilaos.persistence.FileSystemJsonManager;
import avilaos.persistence.ProcessConfigData;
import avilaos.persistence.SimulationSetup;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.io.File;
import javax.swing.BorderFactory;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.filechooser.FileNameExtensionFilter;

/**
 * Panel de carga obligatoria de los dos JSON, configuracion visible y reinicio preservando el disco
 * (J08, J09 y J11).
 * - J08: mantiene el boton "Iniciar simulacion" deshabilitado con un mensaje claro de lo que falta
 *   hasta que ambos archivos JSON esten cargados y validados.
 * - J11: muestra de forma visible y validada los 4 parametros nuevos (bloques del disco, swap,
 *   politica inicial del disco y posicion inicial del cabezal) junto a la configuracion del P1.
 * - J09: permite guardar el estado actual del FS y reiniciar con un nuevo JSON de procesos
 *   preservando todos los cambios hechos en el disco.
 */
public class PanelCargaJson extends Tema.PanelRedondeado {

    /** Callback sin depender de java.util.function para entregar el bundle recien armado. */
    public interface OnBundleReady {
        void accept(SimulationSetup.SimulationBundle bundle);
    }

    private static final Color VERDE_TEXTO = new Color(0x3E8E70);
    private static final Color ERROR_TEXTO = new Color(0x9E2A4B);

    private final SimulationSetup setup;
    private OnBundleReady onBundleReady;

    private final Tema.Boton btnCargarFs = new Tema.Boton("Cargar FS (JSON)", false);
    private final Tema.Boton btnCargarProcesos = new Tema.Boton("Cargar Procesos (JSON)", false);
    private final Tema.Boton btnGuardarFs = new Tema.Boton("Guardar FS (JSON)", false);
    private final Tema.Boton btnReiniciar = new Tema.Boton("Reiniciar conservando disco", false);
    private final Tema.Boton btnIniciar = new Tema.Boton("Iniciar simulacion", true);

    private final Tema.Pastilla pillFs = new Tema.Pastilla("FS: PENDIENTE", Tema.ROSA_SUAVE, Tema.ROSA);
    private final Tema.Pastilla pillProcesos = new Tema.Pastilla("PROCESOS: PENDIENTE", Tema.ROSA_SUAVE, Tema.ROSA);
    private final JLabel lblEstado = Tema.etiqueta("", Tema.NEGRITA, ERROR_TEXTO);

    // J11: los 4 valores nuevos + resumen de P1 siempre visibles
    private final JLabel lblTotalBloques = Tema.etiqueta("-", Tema.NEGRITA, Tema.TEXTO);
    private final JLabel lblSwap = Tema.etiqueta("-", Tema.NEGRITA, Tema.TEXTO);
    private final JLabel lblPoliticaDisco = Tema.etiqueta("-", Tema.NEGRITA, Tema.TEXTO);
    private final JLabel lblCabezal = Tema.etiqueta("-", Tema.NEGRITA, Tema.TEXTO);
    private final JLabel lblConfigP1 = Tema.etiqueta("Sin configuracion de computador cargada", Tema.PEQUENA, Tema.TEXTO_SUAVE);

    private SimulationSetup.SimulationBundle ultimoBundle;

    public PanelCargaJson() {
        this(new SimulationSetup());
    }

    public PanelCargaJson(SimulationSetup setup) {
        super(Tema.TARJETA, Tema.BORDE, 18);
        this.setup = setup != null ? setup : new SimulationSetup();

        setLayout(new BorderLayout(10, 8));
        setBorder(BorderFactory.createEmptyBorder(10, 14, 10, 14));

        // Arriba: pastillas de estado + botones de carga/inicio
        JPanel filaSuperior = new JPanel(new BorderLayout(8, 0));
        filaSuperior.setOpaque(false);

        JPanel estadoPills = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        estadoPills.setOpaque(false);
        estadoPills.add(Tema.etiqueta("Carga JSON", Tema.SUBTITULO, Tema.TEXTO));
        estadoPills.add(pillFs);
        estadoPills.add(pillProcesos);

        btnCargarFs.addActionListener(e -> elegirYCargarFs());
        btnCargarProcesos.addActionListener(e -> elegirYCargarProcesos());
        btnGuardarFs.addActionListener(e -> elegirYGuardarFsActual());
        btnReiniciar.addActionListener(e -> elegirYReiniciarConNuevosProcesos());
        btnIniciar.addActionListener(e -> iniciarSimulacion());

        JPanel botones = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        botones.setOpaque(false);
        botones.add(btnCargarFs);
        botones.add(btnCargarProcesos);
        botones.add(btnGuardarFs);
        botones.add(btnReiniciar);
        botones.add(btnIniciar);

        filaSuperior.add(estadoPills, BorderLayout.WEST);
        filaSuperior.add(botones, BorderLayout.EAST);
        add(filaSuperior, BorderLayout.NORTH);

        // Centro: tarjeta con los 4 parametros nuevos validados (J11)
        Tema.PanelRedondeado tarjetaConfig = new Tema.PanelRedondeado(Tema.FONDO, Tema.BORDE, 12);
        tarjetaConfig.setLayout(new BorderLayout(0, 4));
        tarjetaConfig.setBorder(BorderFactory.createEmptyBorder(8, 12, 8, 12));

        JPanel grid4 = new JPanel(new GridLayout(1, 4, 12, 0));
        grid4.setOpaque(false);
        grid4.add(celdaParametro("Bloques del disco", lblTotalBloques));
        grid4.add(celdaParametro("Area de swap", lblSwap));
        grid4.add(celdaParametro("Politica inicial disco", lblPoliticaDisco));
        grid4.add(celdaParametro("Cabezal inicial", lblCabezal));

        tarjetaConfig.add(grid4, BorderLayout.CENTER);
        tarjetaConfig.add(lblConfigP1, BorderLayout.SOUTH);
        add(tarjetaConfig, BorderLayout.CENTER);

        // Abajo: mensaje claro de lo que falta o errores (J08)
        add(lblEstado, BorderLayout.SOUTH);

        actualizarVista();
    }

    private static JPanel celdaParametro(String titulo, JLabel valor) {
        JPanel p = new JPanel(new GridLayout(2, 1, 0, 2));
        p.setOpaque(false);
        p.add(Tema.etiqueta(titulo, Tema.PEQUENA, Tema.TEXTO_SUAVE));
        p.add(valor);
        return p;
    }

    public void setOnBundleReady(OnBundleReady listener) {
        this.onBundleReady = listener;
    }

    public SimulationSetup getSetup() { return setup; }

    public SimulationSetup.SimulationBundle getUltimoBundle() { return ultimoBundle; }

    // ------------------------------------------------------------------ Metodos de carga/reinicio (J08, J09, J11)

    public boolean cargarFsDesdeArchivo(String ruta) {
        try {
            setup.loadFileSystemFile(ruta);
            actualizarVista();
            return true;
        } catch (ConfigException ex) {
            mostrarError(ex.getMessage());
            return false;
        }
    }

    public boolean cargarFsDesdeTexto(String json) {
        try {
            setup.loadFileSystemJson(json);
            actualizarVista();
            return true;
        } catch (ConfigException ex) {
            mostrarError(ex.getMessage());
            return false;
        }
    }

    public boolean cargarProcesosDesdeArchivo(String ruta) {
        try {
            setup.loadProcessConfigFile(ruta);
            actualizarVista();
            return true;
        } catch (ConfigException ex) {
            mostrarError(ex.getMessage());
            return false;
        }
    }

    public boolean cargarProcesosDesdeTexto(String json) {
        try {
            setup.loadProcessConfigJson(json);
            actualizarVista();
            return true;
        } catch (ConfigException ex) {
            mostrarError(ex.getMessage());
            return false;
        }
    }

    /**
     * J08: Construye el SimulationBundle solo si ambos JSON estan cargados.
     * Devuelve null y deja el mensaje en lblEstado si falta alguno.
     */
    public SimulationSetup.SimulationBundle iniciarSimulacion() {
        if (!setup.isReady()) {
            actualizarVista();
            return null;
        }
        try {
            SimulationSetup.SimulationBundle bundle = setup.build();
            this.ultimoBundle = bundle;
            actualizarVista();
            lblEstado.setForeground(VERDE_TEXTO);
            lblEstado.setText("Simulacion lista para iniciar: " + setup.configSummary());
            if (onBundleReady != null) {
                onBundleReady.accept(bundle);
            }
            return bundle;
        } catch (ConfigException ex) {
            mostrarError(ex.getMessage());
            return null;
        }
    }

    /** J03 + J09: Guarda el estado modificado del sistema de archivos en un archivo JSON. */
    public boolean guardarFsEnArchivo(FileTree tree, ChainAllocator allocator,
            int totalBlocks, int currentHead, String destino) {
        try {
            FileSystemJsonManager.save(tree, allocator, totalBlocks, currentHead, destino);
            lblEstado.setForeground(VERDE_TEXTO);
            lblEstado.setText("Estado del sistema de archivos guardado en " + destino);
            return true;
        } catch (ConfigException ex) {
            mostrarError(ex.getMessage());
            return false;
        }
    }

    /**
     * J09: Preserva el estado modificado del disco/FS actual, carga el nuevo JSON de procesos
     * (desde ruta o texto si esJsonDirecto es true) y arma una nueva simulacion conservando el disco.
     */
    public SimulationSetup.SimulationBundle reiniciarConservandoDisco(FileTree tree,
            ChainAllocator allocator, int totalBlocks, int currentHead,
            String nuevoJsonProcesos, boolean esTextoJson) {
        try {
            setup.preserveModifiedFileSystem(tree, allocator, totalBlocks, currentHead);
            if (esTextoJson) {
                setup.loadProcessConfigJson(nuevoJsonProcesos);
            } else {
                setup.loadProcessConfigFile(nuevoJsonProcesos);
            }
            return iniciarSimulacion();
        } catch (ConfigException ex) {
            mostrarError(ex.getMessage());
            return null;
        }
    }

    private void actualizarVista() {
        boolean fsOk = setup.hasFileSystem();
        boolean procOk = setup.hasProcessConfig();

        if (fsOk) {
            FileSystemData fs = setup.getFileSystemPreview();
            pillFs.setText("FS: CARGADO (" + fs.getFileTree().countFiles() + " archivos)");
            pillFs.cambiarColores(Tema.MENTA_SUAVE, VERDE_TEXTO);
            lblTotalBloques.setText(fs.getTotalBlocks() + " bloques");
            lblCabezal.setText("Bloque " + fs.getInitialHead());
        } else {
            pillFs.setText("FS: PENDIENTE");
            pillFs.cambiarColores(Tema.ROSA_SUAVE, Tema.ROSA);
            lblTotalBloques.setText("-");
            lblCabezal.setText("-");
        }

        if (procOk) {
            ProcessConfigData p = setup.getProcessConfig();
            pillProcesos.setText("PROCESOS: CARGADO (" + p.getProcessCount() + ")");
            pillProcesos.cambiarColores(Tema.MENTA_SUAVE, VERDE_TEXTO);
            lblSwap.setText(p.getSwapSizeBlocks() + " bloques");
            lblPoliticaDisco.setText(p.getInitialDiskPolicy().toString());
            if (fsOk) {
                lblCabezal.setText("Bloque " + setup.getFileSystemPreview().getInitialHead()
                        + " (k=" + p.getHeadSpeed() + ", " + p.getInitialHeadDirection() + ")");
            }
            lblConfigP1.setText("Ciclo: " + p.getCycleDurationMs() + " ms  |  RAM: " + p.getRamCapacity()
                    + "  |  CPU: " + p.getCpuPolicy()
                    + (p.getQuantum() > 0 ? " (quantum=" + p.getQuantum() + ")" : ""));
        } else {
            pillProcesos.setText("PROCESOS: PENDIENTE");
            pillProcesos.cambiarColores(Tema.ROSA_SUAVE, Tema.ROSA);
            lblSwap.setText("-");
            lblPoliticaDisco.setText("-");
            lblConfigP1.setText("Sin configuracion de computador cargada");
        }

        // J08: Iniciar deshabilitado mientras falte cualquiera de los dos JSON
        btnIniciar.setEnabled(setup.isReady());
        btnGuardarFs.setEnabled(ultimoBundle != null || fsOk);
        btnReiniciar.setEnabled(ultimoBundle != null);

        if (setup.isReady()) {
            lblEstado.setForeground(VERDE_TEXTO);
            lblEstado.setText("Ambos JSON validados. Ya puedes iniciar la simulacion.");
        } else {
            lblEstado.setForeground(ERROR_TEXTO);
            lblEstado.setText(setup.missingMessage());
        }
    }

    private void mostrarError(String mensaje) {
        lblEstado.setForeground(ERROR_TEXTO);
        lblEstado.setText("Error: " + mensaje);
    }

    // ------------------------------------------------------------------ Selectores de archivo para la GUI

    private void elegirYCargarFs() {
        File archivo = elegirArchivo("Cargar JSON del sistema de archivos", false);
        if (archivo != null) {
            cargarFsDesdeArchivo(archivo.getPath());
        }
    }

    private void elegirYCargarProcesos() {
        File archivo = elegirArchivo("Cargar JSON de procesos y configuracion", false);
        if (archivo != null) {
            cargarProcesosDesdeArchivo(archivo.getPath());
        }
    }

    private void elegirYGuardarFsActual() {
        File destino = elegirArchivo("Guardar estado del sistema de archivos", true);
        if (destino == null) {
            return;
        }
        String ruta = destino.getPath().toLowerCase().endsWith(".json")
                ? destino.getPath()
                : destino.getPath() + ".json";
        if (ultimoBundle != null) {
            FileSystemData fs = ultimoBundle.getFileSystemData();
            int head = Math.min(ultimoBundle.getDisk().getHead().getPosition(), fs.getTotalBlocks() - 1);
            guardarFsEnArchivo(fs.getFileTree(), fs.getAllocator(), fs.getTotalBlocks(), head, ruta);
        } else if (setup.hasFileSystem()) {
            FileSystemData fs = setup.getFileSystemPreview();
            guardarFsEnArchivo(fs.getFileTree(), fs.getAllocator(), fs.getTotalBlocks(), fs.getInitialHead(), ruta);
        }
    }

    private void elegirYReiniciarConNuevosProcesos() {
        if (ultimoBundle == null) {
            return;
        }
        File archivo = elegirArchivo("Seleccionar nuevo JSON de procesos (se conservara el disco)", false);
        if (archivo == null) {
            return;
        }
        FileSystemData fs = ultimoBundle.getFileSystemData();
        int head = Math.min(ultimoBundle.getDisk().getHead().getPosition(), fs.getTotalBlocks() - 1);
        reiniciarConservandoDisco(fs.getFileTree(), fs.getAllocator(), fs.getTotalBlocks(), head,
                archivo.getPath(), false);
    }

    private File elegirArchivo(String titulo, boolean guardar) {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle(titulo);
        chooser.setFileFilter(new FileNameExtensionFilter("Archivos JSON (*.json)", "json"));
        int res = guardar ? chooser.showSaveDialog(this) : chooser.showOpenDialog(this);
        return res == JFileChooser.APPROVE_OPTION ? chooser.getSelectedFile() : null;
    }

    // Getters de apoyo para pruebas y para la ventana principal
    public Tema.Boton getBtnIniciar() { return btnIniciar; }
    public Tema.Boton getBtnGuardarFs() { return btnGuardarFs; }
    public Tema.Boton getBtnReiniciar() { return btnReiniciar; }
    public String getTextoEstado() { return lblEstado.getText(); }
    public String getTextoTotalBloques() { return lblTotalBloques.getText(); }
    public String getTextoSwap() { return lblSwap.getText(); }
    public String getTextoPoliticaDisco() { return lblPoliticaDisco.getText(); }
    public String getTextoCabezal() { return lblCabezal.getText(); }
}