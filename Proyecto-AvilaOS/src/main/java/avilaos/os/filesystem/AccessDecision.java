package avilaos.os.filesystem;

/**
 * Respuesta de la autorizacion: permitido o rechazado, y en caso de rechazo el motivo en texto
 * (se le muestra al usuario, sin interrumpir la simulacion).
 */
public final class AccessDecision {

    private static final AccessDecision ALLOWED = new AccessDecision(true, "");

    private final boolean allowed;
    private final String reason;

    private AccessDecision(boolean allowed, String reason) {
        this.allowed = allowed;
        this.reason = reason;
    }

    public static AccessDecision allow() {
        return ALLOWED;
    }

    /** @param reason por que se rechaza (no puede ser vacio) */
    public static AccessDecision deny(String reason) {
        if (reason == null || reason.trim().isEmpty()) {
            throw new IllegalArgumentException("Un rechazo necesita explicar el motivo");
        }
        return new AccessDecision(false, reason);
    }

    public boolean isAllowed() { return allowed; }

    /** @return el motivo del rechazo, o texto vacio si fue permitido */
    public String getReason() { return reason; }

    @Override
    public String toString() {
        return allowed ? "PERMITIDO" : "RECHAZADO: " + reason;
    }
}
