/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Enum.java to edit this template
 */
package avilaos.enums;

/**
 * Politicas de planificacion de disco.
 * Para agregar una extra basta una constante nueva aqui, su clase y un caso en DiskPolicyFactory
 * (asi se agrego LOOK, sin tocar el planificador)
 * @author Antonella Ruiz
 */
public enum DiskSchedulingPolicy {
    FIFO("FIFO"),
    SSTF("SSTF"),
    SCAN("SCAN"),
    C_SCAN("C-SCAN"),
    LOOK("LOOK");

    private final String etiqueta;

    DiskSchedulingPolicy(String etiqueta) {
        this.etiqueta = etiqueta;
    }

    /** Texto para mostrar en la GUI y en el log. */
    @Override
    public String toString() {
        return etiqueta;
    }

    /**
     * Convierte un texto ("C-SCAN", "c_scan", "fifo") en la politica.
     * @return la politica, o null si el texto no corresponde a ninguna
     */
    public static DiskSchedulingPolicy desdeTexto(String texto) {
        if (texto == null) {
            return null;
        }
        String limpio = texto.trim().replace('-', '_').toUpperCase();
        for (DiskSchedulingPolicy p : values()) {
            if (p.name().equals(limpio)) {
                return p;
            }
        }
        return null;
    }
}
