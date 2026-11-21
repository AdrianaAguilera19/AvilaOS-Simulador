package avilaos.gui;

import avilaos.enums.IOOperationType;
import avilaos.enums.NodeType;
import avilaos.enums.Permission;
import avilaos.model.process.PCB;
import avilaos.os.filesystem.DiskIoCoordinator;
import avilaos.os.filesystem.FileNode;
import avilaos.os.filesystem.FileSnapshot;
import java.awt.FlowLayout;
import java.awt.Window;
import javax.swing.BorderFactory;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;

/**
 * Barra de acciones CRUD sobre el sistema de archivos (G13, G14 y G15):
 * - G13: crear directorio, crear archivo, leer, renombrar y eliminar SIEMPRE crean un PCB de E/S
 *   en DiskIoCoordinator; ninguna accion llama jamas a FileSystem ni SecuredFileSystem.
 * - G14: en modo usuario, las operaciones no permitidas muestran la razon clara en PanelModoUsuario
 *   (etiqueta no modal + EventLogger) sin interrumpir la simulacion.
 * - G15: deshabilita controles exclusivos del administrador cuando se cambia a modo usuario.
 */
public class PanelAccionesArchivo extends Tema.PanelRedondeado {

    private static final int MEMORIA_DEFECTO = 16;
    private static final int DEADLINE_DEFECTO = 400;
    private static final int INSTRUCCIONES_DEFECTO = 2;
    private static final int PRIORIDAD_DEFECTO = 1;

    private DiskIoCoordinator coordinator;
    private final PanelModoUsuario panelModo;
    private final PanelSistemaArchivos panelFs;
    private long cicloActual = 0;
    private int contadorProcesos = 1;

    private final Tema.Boton btnCrearDir = new Tema.Boton("Crear directorio", false);
    private final Tema.Boton btnCrearArchivo = new Tema.Boton("Crear archivo", false);
    private final Tema.Boton btnLeer = new Tema.Boton("Leer", false);
    private final Tema.Boton btnRenombrar = new Tema.Boton("Renombrar", false);
    private final Tema.Boton btnEliminar = new Tema.Boton("Eliminar", false);
    private final Tema.Boton btnNuevoProcesoEs = new Tema.Boton("Nuevo proceso E/S...", true);

    public PanelAccionesArchivo(DiskIoCoordinator coordinator, PanelModoUsuario panelModo,
            PanelSistemaArchivos panelFs) {
        super(Tema.TARJETA, Tema.BORDE, 16);
        if (coordinator == null || panelModo == null || panelFs == null) {
            throw new IllegalArgumentException("Falta el coordinador, el panel de modo o el panel del FS");
        }
        this.coordinator = coordinator;
        this.panelModo = panelModo;
        this.panelFs = panelFs;

        setLayout(new FlowLayout(FlowLayout.LEFT, 8, 6));
        setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));

        btnCrearDir.addActionListener(e -> pedirCrearDirectorio());
        btnCrearArchivo.addActionListener(e -> pedirCrearArchivo());
        btnLeer.addActionListener(e -> solicitarLeerSeleccionado());
        btnRenombrar.addActionListener(e -> pedirRenombrar());
        btnEliminar.addActionListener(e -> solicitarEliminarSeleccionado());
        btnNuevoProcesoEs.addActionListener(e -> abrirDialogoProcesoEs());

        add(btnCrearDir);
        add(btnCrearArchivo);
        add(btnLeer);
        add(btnRenombrar);
        add(btnEliminar);
        add(btnNuevoProcesoEs);

        // G15: Crear directorio es exclusivo del administrador
        panelModo.registrarControlSoloAdmin(btnCrearDir);
        panelModo.addModeChangeListener(this::actualizarEstadoBotones);
        panelFs.addSelectionListener(this::actualizarEstadoBotones);
        actualizarEstadoBotones();
    }

    public void setCicloActual(long ciclo) {
        this.cicloActual = Math.max(0, ciclo);
        panelModo.setCicloActual(this.cicloActual);
    }

    public void setCoordinator(DiskIoCoordinator nuevoCoordinator) {
        if (nuevoCoordinator != null) {
            this.coordinator = nuevoCoordinator;
        }
    }

    /** Actualiza habilitacion de botones segun modo y elemento seleccionado (G15). */
    public void actualizarEstadoBotones() {
        boolean esAdmin = panelModo.isAdminMode();
        FileSnapshot sel = panelFs.getSelectedSnapshot();
        boolean haySel = sel != null;
        boolean esRaiz = haySel && FileNode.ROOT_NAME.equals(sel.getPath());

        btnCrearDir.setEnabled(esAdmin && haySel);
        btnCrearArchivo.setEnabled(haySel);
        btnLeer.setEnabled(haySel && sel.isFile());
        btnRenombrar.setEnabled(haySel && !esRaiz);
        btnEliminar.setEnabled(haySel && !esRaiz);
        btnNuevoProcesoEs.setEnabled(true);
    }

    // ------------------------------------------------------------------ Metodos de accion (G13 + G14)

    /** G13: Crea un proceso de E/S para crear un directorio dentro del directorio seleccionado. */
    public PCB solicitarCrearDirectorio(String nombreDirectorio, Permission permiso) {
        if (!panelModo.verificarPermisoAdmin("crear directorios")) {
            return null;
        }
        String base = directorioDestinoActual();
        String nombreLimpio = validarNombreSimple(nombreDirectorio, "El nombre del directorio");
        String rutaNueva = combinarRuta(base, nombreLimpio);
        return DialogoProcesoEs.crearProcesoEs(
                coordinator, panelModo, panelFs, cicloActual,
                siguienteNombreProceso("Mkdir"), IOOperationType.CREATE, NodeType.DIRECTORY,
                rutaNueva, 1, null, permiso,
                PRIORIDAD_DEFECTO, MEMORIA_DEFECTO, INSTRUCCIONES_DEFECTO, DEADLINE_DEFECTO
        );
    }

    /** G13: Crea un proceso de E/S para crear un archivo dentro del directorio seleccionado. */
    public PCB solicitarCrearArchivo(String nombreArchivo, int bloques, Permission permiso) {
        String base = directorioDestinoActual();
        String nombreLimpio = validarNombreSimple(nombreArchivo, "El nombre del archivo");
        String rutaNueva = combinarRuta(base, nombreLimpio);
        return DialogoProcesoEs.crearProcesoEs(
                coordinator, panelModo, panelFs, cicloActual,
                siguienteNombreProceso("Create"), IOOperationType.CREATE, NodeType.FILE,
                rutaNueva, bloques, null, permiso,
                PRIORIDAD_DEFECTO, MEMORIA_DEFECTO, INSTRUCCIONES_DEFECTO, DEADLINE_DEFECTO
        );
    }

    /** G13: Crea un proceso de E/S para leer el archivo seleccionado en el JTree. */
    public PCB solicitarLeerSeleccionado() {
        FileSnapshot sel = panelFs.getSelectedSnapshot();
        if (sel == null || !sel.isFile()) {
            panelModo.mostrarRechazo("Selecciona un archivo en el arbol para leerlo");
            return null;
        }
        return DialogoProcesoEs.crearProcesoEs(
                coordinator, panelModo, panelFs, cicloActual,
                siguienteNombreProceso("Read"), IOOperationType.READ, NodeType.FILE,
                sel.getPath(), 0, null, Permission.PUBLIC,
                PRIORIDAD_DEFECTO, MEMORIA_DEFECTO, INSTRUCCIONES_DEFECTO, DEADLINE_DEFECTO
        );
    }

    /** G13: Crea un proceso de E/S para renombrar (UPDATE) el nodo seleccionado en el JTree. */
    public PCB solicitarRenombrarSeleccionado(String nuevoNombre) {
        FileSnapshot sel = panelFs.getSelectedSnapshot();
        if (sel == null || FileNode.ROOT_NAME.equals(sel.getPath())) {
            panelModo.mostrarRechazo("Selecciona un archivo o directorio distinto a la raiz para renombrar");
            return null;
        }
        String nombreLimpio = validarNombreSimple(nuevoNombre, "El nombre nuevo");
        return DialogoProcesoEs.crearProcesoEs(
                coordinator, panelModo, panelFs, cicloActual,
                siguienteNombreProceso("Update"), IOOperationType.UPDATE, NodeType.FILE,
                sel.getPath(), 0, nombreLimpio, Permission.PUBLIC,
                PRIORIDAD_DEFECTO, MEMORIA_DEFECTO, INSTRUCCIONES_DEFECTO, DEADLINE_DEFECTO
        );
    }

    /** G13: Crea un proceso de E/S para eliminar (DELETE) el nodo seleccionado en el JTree. */
    public PCB solicitarEliminarSeleccionado() {
        FileSnapshot sel = panelFs.getSelectedSnapshot();
        if (sel == null || FileNode.ROOT_NAME.equals(sel.getPath())) {
            panelModo.mostrarRechazo("Selecciona un archivo o directorio distinto a la raiz para eliminar");
            return null;
        }
        return DialogoProcesoEs.crearProcesoEs(
                coordinator, panelModo, panelFs, cicloActual,
                siguienteNombreProceso("Delete"), IOOperationType.DELETE, NodeType.FILE,
                sel.getPath(), 0, null, Permission.PUBLIC,
                PRIORIDAD_DEFECTO, MEMORIA_DEFECTO, INSTRUCCIONES_DEFECTO, DEADLINE_DEFECTO
        );
    }

    // ------------------------------------------------------------------ Interaccion con botones

    private void abrirDialogoProcesoEs() {
        Window owner = SwingUtilities.getWindowAncestor(this);
        FileSnapshot sel = panelFs.getSelectedSnapshot();
        String rutaSugerida = sel != null && !FileNode.ROOT_NAME.equals(sel.getPath())
                ? sel.getPath()
                : "/usuarios/nuevo.txt";
        DialogoProcesoEs dialogo = new DialogoProcesoEs(
                owner, coordinator, panelModo, panelFs, cicloActual, IOOperationType.READ, rutaSugerida);
        dialogo.setVisible(true);
    }

    private void pedirCrearDirectorio() {
        if (!panelModo.verificarPermisoAdmin("crear directorios")) {
            return;
        }
        JTextField txtNombre = new JTextField(14);
        JComboBox<Permission> cmbPerm = new JComboBox<>(Permission.values());
        JPanel form = new JPanel(new java.awt.GridLayout(0, 2, 6, 6));
        form.add(new JLabel("Directorio padre:"));
        form.add(new JLabel(directorioDestinoActual()));
        form.add(new JLabel("Nombre:"));
        form.add(txtNombre);
        form.add(new JLabel("Permiso:"));
        form.add(cmbPerm);
        if (JOptionPane.showConfirmDialog(this, form, "Crear directorio (via proceso E/S)",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE) == JOptionPane.OK_OPTION) {
            try {
                solicitarCrearDirectorio(txtNombre.getText(), (Permission) cmbPerm.getSelectedItem());
            } catch (IllegalArgumentException ex) {
                panelModo.mostrarRechazo(ex.getMessage());
            }
        }
    }

    private void pedirCrearArchivo() {
        JTextField txtNombre = new JTextField(14);
        JTextField txtBloques = new JTextField("4", 6);
        JComboBox<Permission> cmbPerm = new JComboBox<>(Permission.values());
        JPanel form = new JPanel(new java.awt.GridLayout(0, 2, 6, 6));
        form.add(new JLabel("Directorio padre:"));
        form.add(new JLabel(directorioDestinoActual()));
        form.add(new JLabel("Nombre del archivo:"));
        form.add(txtNombre);
        form.add(new JLabel("Tamano (bloques):"));
        form.add(txtBloques);
        form.add(new JLabel("Permiso:"));
        form.add(cmbPerm);
        if (JOptionPane.showConfirmDialog(this, form, "Crear archivo (via proceso E/S)",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE) == JOptionPane.OK_OPTION) {
            try {
                int blk = Validador.entero(txtBloques.getText(), "Los bloques", 1, Validador.MAX_VALOR);
                solicitarCrearArchivo(txtNombre.getText(), blk, (Permission) cmbPerm.getSelectedItem());
            } catch (IllegalArgumentException ex) {
                panelModo.mostrarRechazo(ex.getMessage());
            }
        }
    }

    private void pedirRenombrar() {
        FileSnapshot sel = panelFs.getSelectedSnapshot();
        if (sel == null || FileNode.ROOT_NAME.equals(sel.getPath())) {
            panelModo.mostrarRechazo("Selecciona un archivo o directorio para renombrar");
            return;
        }
        String rechazo = DialogoProcesoEs.evaluarPermisoEnVista(
                panelModo.getActiveUser(), IOOperationType.UPDATE, NodeType.FILE, sel.getPath(), panelFs);
        if (rechazo != null) {
            panelModo.mostrarRechazo(rechazo);
            return;
        }
        String nuevo = JOptionPane.showInputDialog(this,
                "Nombre nuevo para «" + sel.getPath() + "» (creara proceso UPDATE):", sel.getName());
        if (nuevo != null) {
            try {
                solicitarRenombrarSeleccionado(nuevo);
            } catch (IllegalArgumentException ex) {
                panelModo.mostrarRechazo(ex.getMessage());
            }
        }
    }

    private String directorioDestinoActual() {
        FileSnapshot sel = panelFs.getSelectedSnapshot();
        if (sel == null) {
            return FileNode.ROOT_NAME;
        }
        if (sel.isDirectory()) {
            return sel.getPath();
        }
        int slash = sel.getPath().lastIndexOf('/');
        return slash <= 0 ? FileNode.ROOT_NAME : sel.getPath().substring(0, slash);
    }

    private static String combinarRuta(String dir, String nombre) {
        return FileNode.ROOT_NAME.equals(dir) ? "/" + nombre : dir + "/" + nombre;
    }

    private static String validarNombreSimple(String texto, String campo) {
        String limpio = Validador.nombre(texto, campo);
        if (limpio.indexOf('/') >= 0) {
            throw new IllegalArgumentException(campo + " no puede contener '/'");
        }
        return limpio;
    }

    private String siguienteNombreProceso(String prefijo) {
        return prefijo + "_" + (contadorProcesos++);
    }

    // Getters de botones para verificacion de habilitacion por modo (G15)
    public Tema.Boton getBtnCrearDir() { return btnCrearDir; }
    public Tema.Boton getBtnCrearArchivo() { return btnCrearArchivo; }
    public Tema.Boton getBtnLeer() { return btnLeer; }
    public Tema.Boton getBtnRenombrar() { return btnRenombrar; }
    public Tema.Boton getBtnEliminar() { return btnEliminar; }
    public Tema.Boton getBtnNuevoProcesoEs() { return btnNuevoProcesoEs; }
}
