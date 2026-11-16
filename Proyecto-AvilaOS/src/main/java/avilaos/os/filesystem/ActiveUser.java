package avilaos.os.filesystem;

import avilaos.enums.UserMode;

/**
 * Quien usa el sistema en este momento: un nombre (admin, user1, user2...) y su modo (ADMIN o USER).
 * Un archivo es "propio" cuando su dueno tiene el mismo nombre que el usuario activo.
 * Es inmutable: para cambiar de usuario se crea otro y se guarda en UserSession.
 */
public final class ActiveUser {

    /** Nombre del administrador: es el dueno de los archivos del sistema. */
    public static final String ADMIN_NAME = "admin";

    private final String name;
    private final UserMode mode;

    public ActiveUser(String name, UserMode mode) {
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("El usuario necesita un nombre");
        }
        if (name.indexOf('/') >= 0) {
            throw new IllegalArgumentException("El nombre de usuario no puede contener «/»");
        }
        if (mode == null) {
            throw new IllegalArgumentException("El usuario necesita un modo (ADMIN o USER)");
        }
        String clean = name.trim();
        if (mode == UserMode.USER && ADMIN_NAME.equalsIgnoreCase(clean)) {
            throw new IllegalArgumentException("El nombre «" + ADMIN_NAME + "» esta reservado para el administrador");
        }
        this.name = clean;
        this.mode = mode;
    }

    public static ActiveUser admin() {
        return new ActiveUser(ADMIN_NAME, UserMode.ADMIN);
    }

    public static ActiveUser user(String name) {
        return new ActiveUser(name, UserMode.USER);
    }

    public String getName() { return name; }

    public UserMode getMode() { return mode; }

    public boolean isAdmin() { return mode.isAdmin(); }

    @Override
    public String toString() {
        return name + " (" + mode + ")";
    }
}