/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Enum.java to edit this template
 */
package avilaos.enums;

/** Qué pasó en un paso del productor o consumidor, para que el Kernel reaccione. */
public enum StepKind {
    USER_INSTRUCTION,    // producir o consumir: instrucción de usuario
    SYSTEM_INSTRUCTION,  // semWait, semSignal, insertar o extraer: modo SO
    BLOCKED,             // el semWait bloqueó al proceso (también cuenta como instrucción de SO)
    FINISHED             // el proceso ya terminó todos sus elementos
}
