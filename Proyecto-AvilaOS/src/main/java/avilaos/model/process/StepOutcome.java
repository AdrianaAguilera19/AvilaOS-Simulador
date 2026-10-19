/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package avilaos.model.process;

import avilaos.enums.StepKind;
import avilaos.model.process.PCB;

/** Resultado de un paso: lo que el Kernel necesita saber para reaccionar. */
public final class StepOutcome {

    private final StepKind kind;
    private final PCB woken;            // proceso despertado por un semSignal (puede ser null)
    private final String description;   // texto para el log de eventos

    public StepOutcome(StepKind kind, PCB woken, String description) {
        this.kind = kind;
        this.woken = woken;
        this.description = description;
    }

    public StepKind getKind() { return kind; }
    public PCB getWoken() { return woken; }
    public String getDescription() { return description; }
}
