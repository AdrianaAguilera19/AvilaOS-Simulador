/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package avilaos.enums;

/**
 * Categoria de un evento del log
 * @author Antonella Ruiz
 */
public enum LogCategory {
    GENERAL("General"),
    DISCO("Disco"),
    FS("Sistema de archivos"),
    SUSPENSION("Suspension"),
    PERMISO("Permisos");

    private final String label;

    LogCategory(String label) {
        this.label = label;
    }

    /** Nombre para mostrar en el filtro de la pantalla. */
    public String getLabel() { return label; }

    /** Etiqueta que va en la linea del log; vacia para GENERAL para no cambiar el formato del P1. */
    public String tag() {
        return this == GENERAL ? "" : "[" + name() + "] ";
    }
}