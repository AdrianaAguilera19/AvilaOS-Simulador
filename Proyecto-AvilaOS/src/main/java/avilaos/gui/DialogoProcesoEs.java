package avilaos.gui;

import avilaos.enums.IOOperationType;
import avilaos.enums.NodeType;
import avilaos.enums.Permission;
import avilaos.model.process.PCB;
import avilaos.os.filesystem.ActiveUser;
import avilaos.os.filesystem.DiskIoCoordinator;
import avilaos.os.filesystem.FileSnapshot;
import avilaos.os.filesystem.PermissionMatrix;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Window;
import javax.swing.BorderFactory;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;

/**
 * Ventana de creacion de proceso de E/S (G12):
 * permite elegir operacion (CREATE, READ, UPDATE, DELETE), ruta, tamano en bloques (CREATE),
 * nombre nuevo (UPDATE), permiso y parametros del PCB.
 *
 * Regla clave (G12/G13): SOLO crea y admite un PCB de E/S en DiskIoCoordinator.
 * Nunca llama a FileSystem ni a SecuredFileSystem para modificar archivos o directorios.
 */
public class DialogoProcesoEs extends JDialog {

    private static final IOOperationType[] OPERACIONES_ARCHIVO = {
        IOOperationType.CREATE, IOOperationType.READ, IOOperationType.UPDATE, IOOperationType.DELETE
    };

    private final DiskIoCoordinator coordinator;
    private final PanelModoUsuario panelModo;
    private final PanelSistemaArchivos panelFs;
    private final long cicloActual;

    private final JTextField txtNombreProceso = new JTextField("Proc_ES", 14);
    private final JComboBox<IOOperationType> cmbOperacion = new JComboBox<>(OPERACIONES_ARCHIVO);
    private final JTextField txtRuta = new JTextField("/usuarios/nuevo.txt", 18);
    private final JTextField txtBloques = new JTextField("4", 8);
    private final JTextField txtNombreNuevo = new JTextField("", 14);
    private final JComboBox<Permission> cmbPermiso = new JComboBox<>(Permission.values());
    private final JTextField txtMemoria = new JTextField("16", 8);
    private final JTextField txtDeadline = new JTextField("300", 8);
    private final JTextField txtInstrucciones = new JTextField("2", 8);
    private final JTextField txtPrioridad = new JTextField("1", 8);
    private final JLabel lblError = Tema.etiqueta(" ", Tema.PEQUENA, Tema.ROSA);

    private PCB creado;

    public DialogoProcesoEs(Window owner, DiskIoCoordinator coordinator, PanelModoUsuario panelModo,
            PanelSistemaArchivos panelFs, long cicloActual, IOOperationType operacionInicial,
            String rutaInicial) {
        super(owner, "Nuevo proceso de E/S", ModalityType.APPLICATION_MODAL);
        if (coordinator == null || panelModo == null) {
            throw new IllegalArgumentException("Falta el coordinador de E/S o el panel de modo");
        }
        this.coordinator = coordinator;
        this.panelModo = panelModo;
        this.panelFs = panelFs;
        this.cicloActual = Math.max(0, cicloActual);

        if (operacionInicial != null && operacionInicial.isFileOperation()) {
            cmbOperacion.setSelectedItem(operacionInicial);
        }
        if (rutaInicial != null && !rutaInicial.isBlank()) {
            txtRuta.setText(rutaInicial.trim());
        }

        JPanel contenido = new JPanel(new BorderLayout(0, 12));
        contenido.setBackground(Tema.FONDO);
        contenido.setBorder(BorderFactory.createEmptyBorder(16, 20, 14, 20));
        contenido.add(Tema.etiqueta("Crear proceso de E/S (" + panelModo.getActiveUser() + ")",
                Tema.SUBTITULO, Tema.TEXTO), BorderLayout.NORTH);

        JPanel formulario = new JPanel(new GridBagLayout());
        formulario.setOpaque(false);
        int fila = 0;
        fila = agregarFila(formulario, fila, "Nombre del proceso", txtNombreProceso);
        fila = agregarFila(formulario, fila, "Operacion", cmbOperacion);
        fila = agregarFila(formulario, fila, "Ruta absoluta", txtRuta);
        fila = agregarFila(formulario, fila, "Tamano en bloques (CREATE)", txtBloques);
        fila = agregarFila(formulario, fila, "Nombre nuevo (UPDATE)", txtNombreNuevo);
        fila = agregarFila(formulario, fila, "Permiso (CREATE)", cmbPermiso);
        fila = agregarFila(formulario, fila, "Memoria (RAM)", txtMemoria);
        fila = agregarFila(formulario, fila, "Deadline (ciclos)", txtDeadline);
        fila = agregarFila(formulario, fila, "Instrucciones", txtInstrucciones);
        agregarFila(formulario, fila, "Prioridad", txtPrioridad);

        cmbOperacion.addActionListener(e -> actualizarCamposPorOperacion());
        actualizarCamposPorOperacion();

        JPanel pie = new JPanel(new BorderLayout(0, 8));
        pie.setOpaque(false);
        pie.add(lblError, BorderLayout.NORTH);

        Tema.Boton btnCancelar = new Tema.Boton("Cancelar", false);
        Tema.Boton btnCrear = new Tema.Boton("Crear proceso", true);
        btnCancelar.addActionListener(e -> dispose());
        btnCrear.addActionListener(e -> onCrear());

        JPanel botones = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        botones.setOpaque(false);
        botones.add(btnCancelar);
        botones.add(btnCrear);
        pie.add(botones, BorderLayout.SOUTH);

        contenido.add(formulario, BorderLayout.CENTER);
        contenido.add(pie, BorderLayout.SOUTH);

        setContentPane(contenido);
        pack();
        setResizable(false);
        setLocationRelativeTo(owner);
    }

    public PCB getProcesoCreado() { return creado; }

    private void actualizarCamposPorOperacion() {
        IOOperationType op = (IOOperationType) cmbOperacion.getSelectedItem();
        boolean esCreate = op == IOOperationType.CREATE;
        boolean esUpdate = op == IOOperationType.UPDATE;
        txtBloques.setEnabled(esCreate);
        cmbPermiso.setEnabled(esCreate);
        txtNombreNuevo.setEnabled(esUpdate);
    }

    private void onCrear() {
        try {
            IOOperationType op = (IOOperationType) cmbOperacion.getSelectedItem();
            int bloques = op == IOOperationType.CREATE
                    ? Validador.entero(txtBloques.getText(), "Los bloques", 1, Validador.MAX_VALOR)
                    : 0;
            String nombreNuevo = op == IOOperationType.UPDATE ? txtNombreNuevo.getText().trim() : null;
            if (nombreNuevo != null && nombreNuevo.isEmpty()) {
                nombreNuevo = null;
            }
            int ramMax = coordinator.getComputer().getRam().getTotalSize();
            int memoria = Validador.entero(txtMemoria.getText(), "La memoria", 1, ramMax);
            int deadline = Validador.entero(txtDeadline.getText(), "El deadline", 1, Validador.MAX_VALOR);
            int instrucciones = Validador.entero(txtInstrucciones.getText(), "Las instrucciones", 1, Validador.MAX_VALOR);
            int prioridad = Validador.entero(txtPrioridad.getText(), "La prioridad", 1, Validador.MAX_VALOR);
            String nombreProc = Validador.nombre(txtNombreProceso.getText(), "El nombre del proceso");
            Permission perm = (Permission) cmbPermiso.getSelectedItem();

            PCB pcb = crearProcesoEs(
                    coordinator, panelModo, panelFs, cicloActual,
                    nombreProc, op, NodeType.FILE, txtRuta.getText(),
                    bloques, nombreNuevo, perm, prioridad, memoria, instrucciones, deadline
            );
            if (pcb != null) {
                this.creado = pcb;
                dispose();
            } else {
                lblError.setText(panelModo.getTextoAviso());
                pack();
            }
        } catch (IllegalArgumentException ex) {
            lblError.setText(ex.getMessage());
            pack();
        }
    }

    /**
     * Logica reutilizable (usada por el dialogo y por PanelAccionesArchivo / pruebas sin abrir modales):
     * 1. Valida los datos del proceso y los permisos del usuario activo sobre la foto (FileSnapshot).
     * 2. Si el modo usuario no tiene permiso, muestra el motivo en PanelModoUsuario (G14) y devuelve null.
     * 3. Si es valido, crea y encola un PCB de E/S en DiskIoCoordinator (G12/G13). Nunca modifica el FS.
     */
    public static PCB crearProcesoEs(DiskIoCoordinator coordinator, PanelModoUsuario panelModo,
            PanelSistemaArchivos panelFs, long cicloActual, String nombreProceso,
            IOOperationType operacion, NodeType tipoCrear, String ruta, int fileSizeBlocks,
            String newName, Permission permisoCrear, int prioridad, int memoria,
            int instrucciones, int deadline) {
        if (coordinator == null || panelModo == null) {
            throw new IllegalArgumentException("Falta el coordinador de E/S o el panel de modo");
        }
        String nombreLimpio = Validador.nombre(nombreProceso, "El nombre del proceso");
        if (operacion == null || !operacion.isFileOperation()) {
            throw new IllegalArgumentException("Elige una operacion valida (CREATE, READ, UPDATE o DELETE)");
        }
        if (ruta == null || ruta.trim().isEmpty()) {
            throw new IllegalArgumentException("La ruta no puede quedar vacia");
        }
        String rutaLimpia = ruta.trim();
        if (!rutaLimpia.startsWith("/") || "/".equals(rutaLimpia)
                || rutaLimpia.endsWith("/") || rutaLimpia.contains("//")) {
            throw new IllegalArgumentException("Ruta absoluta invalida: " + rutaLimpia);
        }
        int ramMax = coordinator.getComputer().getRam().getTotalSize();
        Validador.rango(memoria, "La memoria", 1, ramMax);
        Validador.rango(deadline, "El deadline", 1, Validador.MAX_VALOR);
        Validador.rango(instrucciones, "Las instrucciones", 1, Validador.MAX_VALOR);
        Validador.rango(prioridad, "La prioridad", 1, Validador.MAX_VALOR);

        NodeType tipo = tipoCrear != null ? tipoCrear : NodeType.FILE;
        if (operacion == IOOperationType.CREATE && tipo == NodeType.FILE && fileSizeBlocks < 1) {
            throw new IllegalArgumentException("Para crear un archivo debes indicar un tamano mayor a 0 bloques");
        }

        // Validacion preventiva en modo usuario usando SOLO FileSnapshot (sin llamar al FS real)
        ActiveUser actor = panelModo.getActiveUser();
        String rechazo = evaluarPermisoEnVista(actor, operacion, tipo, rutaLimpia, panelFs);
        if (rechazo != null) {
            panelModo.setCicloActual(cicloActual);
            panelModo.mostrarRechazo(rechazo);
            return null;
        }

        PCB pcb;
        if (operacion == IOOperationType.CREATE && tipo == NodeType.DIRECTORY) {
            pcb = coordinator.submitCreateDirectoryProcess(
                    nombreLimpio, prioridad, memoria, instrucciones, deadline, cicloActual,
                    rutaLimpia, permisoCrear != null ? permisoCrear : Permission.PUBLIC, actor
            );
        } else {
            pcb = coordinator.submitFileOperationProcess(
                    nombreLimpio, prioridad, memoria, instrucciones, deadline, cicloActual,
                    operacion.name(), rutaLimpia,
                    operacion == IOOperationType.CREATE ? fileSizeBlocks : 0,
                    operacion == IOOperationType.UPDATE ? newName : null,
                    permisoCrear != null ? permisoCrear : Permission.PUBLIC, actor
            );
        }
        panelModo.mostrarInfo("Proceso " + pcb.getName() + " (" + operacion + " " + rutaLimpia + ") enviado a cola de procesos");
        return pcb;
    }

    /**
     * Evalua las reglas de PermissionMatrix usando exclusivamente el FileSnapshot de la vista.
     * Devuelve null si esta permitido; de lo contrario, el mensaje claro para mostrar en la GUI (G14).
     */
    public static String evaluarPermisoEnVista(ActiveUser who, IOOperationType op, NodeType tipoCrear,
            String ruta, PanelSistemaArchivos panelFs) {
        if (who == null) {
            return "No hay usuario activo";
        }
        if (who.isAdmin()) {
            return null;
        }
        if (op == IOOperationType.CREATE && tipoCrear == NodeType.DIRECTORY) {
            return "Solo el administrador puede crear directorios";
        }
        if (panelFs == null) {
            return null;
        }

        if (op == IOOperationType.CREATE) {
            int slash = ruta.lastIndexOf('/');
            String parentPath = slash <= 0 ? "/" : ruta.substring(0, slash);
            ModeloArbolArchivos.NodoArbol nodoPadre = panelFs.getModeloArbol().buscarPorRuta(parentPath);
            if (nodoPadre == null) {
                return null; // ruta inexistente: la manejara el proceso como fallo controlado E09
            }
            FileSnapshot padre = nodoPadre.getSnapshot();
            if (!padre.isDirectory()) {
                return "«" + parentPath + "» es un archivo: no se puede crear nada dentro";
            }
            if (!who.getName().equals(padre.getOwner())) {
                return "No puedes crear archivos en «" + parentPath + "» porque pertenece a «"
                        + padre.getOwner() + "» (solo puedes crear en tus propios directorios)";
            }
            return null;
        }

        ModeloArbolArchivos.NodoArbol nodo = panelFs.getModeloArbol().buscarPorRuta(ruta);
        if (nodo == null) {
            return null; // si no existe, el proceso E/S lo reportara como fallo controlado E09
        }
        FileSnapshot target = nodo.getSnapshot();

        if (op == IOOperationType.READ) {
            if (target.isDirectory() || target.getPermission() == Permission.PUBLIC
                    || who.getName().equals(target.getOwner())) {
                return null;
            }
            return "No puedes leer «" + ruta + "»: es un archivo privado de «" + target.getOwner() + "»";
        }

        String verbo = op == IOOperationType.UPDATE ? "renombrar" : "eliminar";
        if (esRutaDeSistema(ruta)) {
            return "No puedes " + verbo + " «" + ruta + "»: es un archivo del sistema";
        }
        if (target.isDirectory()) {
            return "Solo el administrador puede " + verbo + " directorios";
        }
        if (!who.getName().equals(target.getOwner())) {
            return "No puedes " + verbo + " archivos de otro usuario («" + ruta + "» pertenece a «"
                    + target.getOwner() + "»)";
        }
        return null;
    }

    private static boolean esRutaDeSistema(String path) {
        return PermissionMatrix.SYSTEM_DIRECTORY.equals(path)
                || path.startsWith(PermissionMatrix.SYSTEM_DIRECTORY + "/");
    }

    private static int agregarFila(JPanel panel, int fila, String texto, JComponent campo) {
        GridBagConstraints g = new GridBagConstraints();
        g.gridy = fila;
        g.insets = new Insets(4, 0, 4, 10);
        g.anchor = GridBagConstraints.WEST;
        g.gridx = 0;
        panel.add(Tema.etiqueta(texto, Tema.NORMAL, Tema.TEXTO), g);
        g.gridx = 1;
        g.fill = GridBagConstraints.HORIZONTAL;
        g.weightx = 1;
        panel.add(campo, g);
        return fila + 1;
    }
}