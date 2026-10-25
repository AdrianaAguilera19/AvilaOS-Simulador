/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package avilaos.gui;

/**
 * Reglas comunes para validar lo que el usuario escribe en la interfaz (I07): tipo
 * (numero entero, texto) y rango. Cuando algo no es valido lanza IllegalArgumentException
 * con un mensaje claro; la ventana lo muestra y el simulador sigue corriendo.
 */
public final class Validador {

    /** Valor maximo que se acepta en cualquier campo numerico (evita desbordes de enteros). */
    public static final int MAX_VALOR = 1_000_000;

    /** Largo maximo del nombre de un proceso. */
    public static final int MAX_NOMBRE = 20;

    private Validador() {}

    /**
     * Convierte el texto de un campo en un entero dentro del rango.
     * @param texto lo que escribio el usuario
     * @param campo nombre del campo para el mensaje (por ejemplo "La memoria")
     * @param min minimo permitido
     * @param max maximo permitido
     * @return el numero
     * @throws IllegalArgumentException si esta vacio, no es un entero o esta fuera del rango
     */
    public static int entero(String texto, String campo, int min, int max) {
        if (texto == null || texto.isBlank()) {
            throw new IllegalArgumentException(campo + " no puede quedar vacio");
        }
        int valor;
        try {
            valor = Integer.parseInt(texto.trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(campo + " debe ser un numero entero (sin letras ni decimales)");
        }
        return rango(valor, campo, min, max);
    }

    /**
     * Comprueba que un numero este dentro del rango.
     * @return el mismo valor, si es valido
     * @throws IllegalArgumentException si esta fuera del rango
     */
    public static int rango(int valor, String campo, int min, int max) {
        if (valor < min || valor > max) {
            throw new IllegalArgumentException(campo + " debe estar entre " + min + " y " + max);
        }
        return valor;
    }

    /**
     * Valida un nombre: no vacio y de largo razonable.
     * @return el nombre sin espacios sobrantes
     * @throws IllegalArgumentException si esta vacio o es muy largo
     */
    public static String nombre(String texto, String campo) {
        if (texto == null || texto.isBlank()) {
            throw new IllegalArgumentException(campo + " no puede quedar vacio");
        }
        String limpio = texto.trim();
        if (limpio.length() > MAX_NOMBRE) {
            throw new IllegalArgumentException(campo + " no puede tener mas de " + MAX_NOMBRE + " caracteres");
        }
        return limpio;
    }
}