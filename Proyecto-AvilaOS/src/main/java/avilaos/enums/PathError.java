/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package avilaos.enums;

/**
 * Motivo por el que una ruta del sistema de archivos no se pudo resolver.
 * @author Antonella Ruiz
 */
public enum PathError {
    /** La ruta es nula o esta vacia. */
    EMPTY,
    /** La ruta no empieza con «/». */
    NOT_ABSOLUTE,
    /** La ruta tiene una parte vacia (por ejemplo «//» o una «/» al final). */
    EMPTY_PART,
    /** Alguna parte de la ruta no existe. */
    NOT_FOUND,
    /** La ruta pasa por un archivo como si fuera un directorio. */
    NOT_A_DIRECTORY
}
