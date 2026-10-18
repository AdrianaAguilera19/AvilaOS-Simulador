/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package avilaos.persistence;

/**
 *Error al guardar, cargar o validar una config
 * El mensaje explica que paso en palabras, lo que la interfaz le mostrara al usuario
 * @author Antonella Ruiz
 */
public class ConfigException extends Exception{
    
    /**
     * @param message explicacion del problema
     */
    public ConfigException(String message) {
        super(message);
    }

    /**
     * @param message explicacion del problema
     * @param cause error original (por ejemplo un fallo de lectura del archivo)
     */
    public ConfigException(String message, Throwable cause) {
        super(message, cause);
    }
}
