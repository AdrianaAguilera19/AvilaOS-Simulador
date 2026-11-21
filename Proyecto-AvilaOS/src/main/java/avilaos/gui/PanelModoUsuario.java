package avilaos.gui;

import avilaos.datastructures.DynamicArray;
import avilaos.enums.LogCategory;
import avilaos.enums.UserMode;
import avilaos.log.EventLogger;
import avilaos.os.filesystem.ActiveUser;
import avilaos.os.filesystem.UserSession;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.FlowLayout;
import javax.swing.BorderFactory;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;

/**
 * Barra de modo de usuario (G01, G14 y G15):
 * - G01: selector de modo (ADMIN / USER) y usuario activo (user1, user2...) en caliente,
 *   con pastilla siempre visible.
 * - G14: etiqueta/banner no modal para mostrar rechazos en modo usuario sin detener la simulacion,
 *   registrandolos ademas en el EventLogger bajo LogCategory.PERMISO.
 * - G15: habilita o deshabilita automaticamente los controles solo-administrador registrados
 *   (por ejemplo, selector de politica de disco/CPU y acciones de administrador).
 */
public class PanelModoUsuario extends Tema.PanelRedondeado {

    private static final Color VERDE_TEXTO = new Color(0x3E8E70);
    private static final Color ERROR_TEXTO = new Color(0x9E2A4B);

    private final UserSession session;
    private EventLogger logger;
    private long cicloActual = 0;

    private final JComboBox<UserMode> cmbModo = new JComboBox<>(UserMode.values());
    private final JComboBox<String> cmbUsuario = new JComboBox<>(new String[] { "user1", "user2" });
    private final Tema.Pastilla pillModoActivo =
            new Tema.Pastilla("MODO: ADMINISTRADOR (admin)", Tema.MENTA_SUAVE, VERDE_TEXTO);
    private final JLabel lblAviso = Tema.etiqueta(" ", Tema.PEQUENA, ERROR_TEXTO);

    private final DynamicArray<Runnable> modeListeners = new DynamicArray<>();
    private final DynamicArray<JComponent> controlesSoloAdmin = new DynamicArray<>();
    private boolean actualizandoControles = false;

    public PanelModoUsuario(UserSession session, EventLogger logger) {
        super(Tema.TARJETA, Tema.BORDE, 16);
        if (session == null) {
            throw new IllegalArgumentException("Falta UserSession para el selector de modo");
        }
        this.session = session;
        this.logger = logger;

        setLayout(new BorderLayout(8, 4));
        setBorder(BorderFactory.createEmptyBorder(8, 12, 8, 12));

        JPanel filaControles = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        filaControles.setOpaque(false);

        cmbModo.setFont(Tema.NORMAL);
        cmbUsuario.setFont(Tema.NORMAL);
        cmbUsuario.setEditable(true);

        cmbModo.addActionListener(e -> aplicarCambioDesdeCombo());
        cmbUsuario.addActionListener(e -> {
            if (!actualizandoControles && cmbModo.getSelectedItem() == UserMode.USER) {
                aplicarCambioDesdeCombo();
            }
        });

        filaControles.add(Tema.etiqueta("Modo:", Tema.NEGRITA, Tema.TEXTO));
        filaControles.add(cmbModo);
        filaControles.add(Tema.etiqueta("Usuario activo:", Tema.NEGRITA, Tema.TEXTO));
        filaControles.add(cmbUsuario);
        filaControles.add(pillModoActivo);

        add(filaControles, BorderLayout.CENTER);
        add(lblAviso, BorderLayout.SOUTH);

        sincronizarConSesion();
    }

    public UserSession getSession() { return session; }

    public ActiveUser getActiveUser() { return session.getCurrent(); }

    public boolean isAdminMode() { return session.getCurrent().isAdmin(); }

    public void setLogger(EventLogger logger) { this.logger = logger; }

    /** Actualiza el ciclo usado al registrar rechazos de permisos en el EventLogger. */
    public void setCicloActual(long ciclo) { this.cicloActual = Math.max(0, ciclo); }

    /** Agrega un nombre de usuario disponible en el desplegable (sin duplicarlo). */
    public void agregarUsuarioDisponible(String nombre) {
        if (nombre == null || nombre.trim().isEmpty()) {
            return;
        }
        String limpio = nombre.trim();
        if (ActiveUser.ADMIN_NAME.equalsIgnoreCase(limpio)) {
            return;
        }
        for (int i = 0; i < cmbUsuario.getItemCount(); i++) {
            if (limpio.equals(cmbUsuario.getItemAt(i))) {
                return;
            }
        }
        cmbUsuario.addItem(limpio);
    }

    /** Cambia el modo a ADMINISTRADOR en caliente (G01). */
    public void cambiarAAdmin() {
        session.switchTo(ActiveUser.admin());
        limpiarAviso();
        sincronizarConSesion();
        notificarCambioModo();
    }

    /** Cambia a modo USUARIO con el nombre indicado en caliente (G01). */
    public boolean cambiarAUsuario(String nombreUsuario) {
        try {
            ActiveUser nuevo = ActiveUser.user(nombreUsuario);
            agregarUsuarioDisponible(nuevo.getName());
            session.switchTo(nuevo);
            limpiarAviso();
            sincronizarConSesion();
            notificarCambioModo();
            return true;
        } catch (IllegalArgumentException e) {
            mostrarRechazo(e.getMessage());
            sincronizarConSesion();
            return false;
        }
    }

    /**
     * G15: Registra cualquier control que solo debe estar habilitado en modo administrador
     * (selector de politica de disco, selector de politica de CPU, crear directorio, etc.).
     */
    public void registrarControlSoloAdmin(JComponent control) {
        if (control != null && !controlesSoloAdmin.contains(control)) {
            controlesSoloAdmin.add(control);
            control.setEnabled(isAdminMode());
        }
    }

    /**
     * G14 + G15: Verifica si una accion de solo-administrador esta permitida.
     * Si el sistema esta en modo usuario, muestra el motivo en la etiqueta y lo anota en el log
     * sin interrumpir la simulacion.
     */
    public boolean verificarPermisoAdmin(String descripcionAccion) {
        if (isAdminMode()) {
            return true;
        }
        String motivo = "Permiso denegado en modo usuario (" + getActiveUser().getName()
                + "): solo el administrador puede " + descripcionAccion;
        mostrarRechazo(motivo);
        return false;
    }

    /**
     * G14: Muestra un rechazo en la etiqueta no modal y lo registra bajo LogCategory.PERMISO.
     * Nunca abre dialogos modales ni detiene el reloj.
     */
    public void mostrarRechazo(String motivo) {
        if (motivo == null || motivo.isBlank()) {
            return;
        }
        String texto = motivo.trim();
        lblAviso.setForeground(ERROR_TEXTO);
        lblAviso.setText("Rechazado: " + texto);
        if (logger != null) {
            logger.logSystem(cicloActual, LogCategory.PERMISO, texto);
        }
    }

    /** Muestra una confirmacion breve (por ejemplo cuando se encola un proceso de E/S). */
    public void mostrarInfo(String mensaje) {
        lblAviso.setForeground(VERDE_TEXTO);
        lblAviso.setText(mensaje == null ? " " : mensaje);
    }

    public void limpiarAviso() {
        lblAviso.setText(" ");
    }

    public String getTextoAviso() { return lblAviso.getText(); }

    public String getTextoModoActivo() { return pillModoActivo.getText(); }

    public void addModeChangeListener(Runnable listener) {
        if (listener != null && !modeListeners.contains(listener)) {
            modeListeners.add(listener);
        }
    }

    private void aplicarCambioDesdeCombo() {
        if (actualizandoControles) {
            return;
        }
        UserMode elegido = (UserMode) cmbModo.getSelectedItem();
        if (elegido == UserMode.ADMIN) {
            cambiarAAdmin();
            return;
        }
        Object item = cmbUsuario.getEditor().getItem();
        String nombre = item == null ? "" : item.toString().trim();
        if (nombre.isEmpty() && cmbUsuario.getItemCount() > 0) {
            nombre = cmbUsuario.getItemAt(0);
        }
        cambiarAUsuario(nombre);
    }

    private void sincronizarConSesion() {
        actualizandoControles = true;
        try {
            ActiveUser actual = session.getCurrent();
            cmbModo.setSelectedItem(actual.getMode());
            cmbUsuario.setEnabled(!actual.isAdmin());
            if (!actual.isAdmin()) {
                agregarUsuarioDisponible(actual.getName());
                cmbUsuario.setSelectedItem(actual.getName());
                pillModoActivo.setText("MODO: USUARIO (" + actual.getName() + ")");
                pillModoActivo.cambiarColores(Tema.ROSA_SUAVE, Tema.ROSA);
            } else {
                pillModoActivo.setText("MODO: ADMINISTRADOR (admin)");
                pillModoActivo.cambiarColores(Tema.MENTA_SUAVE, VERDE_TEXTO);
            }
            for (int i = 0; i < controlesSoloAdmin.size(); i++) {
                controlesSoloAdmin.get(i).setEnabled(actual.isAdmin());
            }
        } finally {
            actualizandoControles = false;
        }
    }

    private void notificarCambioModo() {
        for (int i = 0; i < modeListeners.size(); i++) {
            modeListeners.get(i).run();
        }
    }
}