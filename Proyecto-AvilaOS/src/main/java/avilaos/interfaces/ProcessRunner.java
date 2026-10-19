/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Interface.java to edit this template
 */
package avilaos.interfaces;

import avilaos.model.process.PCB;
import avilaos.model.process.StepOutcome;

/**
 * Ejecuta, paso a paso, el programa de un proceso que no es una simple lista de
 * instrucciones de usuario (por ejemplo, un productor o consumidor que usa semáforos).
 * Cada llamada a step() representa un ciclo. El computador reacciona al resultado.
 */
public interface ProcessRunner {

    /** Ejecuta un paso (un ciclo) y devuelve qué pasó. */
    StepOutcome step();

    /** @return true si el proceso ya completó todo su trabajo. */
    boolean isFinished();

    /** @return el proceso que ejecuta este runner. */
    PCB getPcb();
}
