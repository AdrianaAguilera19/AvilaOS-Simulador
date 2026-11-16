package avilaos.os.filesystem;

/**
 * Guarda cual es el usuario activo. La interfaz lo cambia cuando el usuario elige otro modo o usuario,
 * y las demas clases lo consultan. Es volatile porque lo lee el hilo del reloj y lo escribe la ventana.
 */
public class UserSession {

    private volatile ActiveUser current = ActiveUser.admin();

    public ActiveUser getCurrent() { return current; }

    public void switchTo(ActiveUser user) {
        if (user == null) {
            throw new IllegalArgumentException("Falta el usuario");
        }
        this.current = user;
    }
}