package avilaos.gui;

import avilaos.enums.ProcessType;
import avilaos.model.hardware.Cluster;
import avilaos.model.hardware.Computer;
import avilaos.model.process.PCB;
import avilaos.model.process.ProcessFactory;
import avilaos.persistence.ConfigDTO;

/**
 * Crea procesos nuevos mientras la simulacion esta en marcha (I05).
 * No dibuja nada: la GUI solo le entrega los datos y muestra el mensaje de error si algo
 * no es valido. Asi las mismas reglas se pueden probar sin abrir ventanas.
 * Para los datos de un proceso se reutiliza ConfigDTO.ProcessConfig (el mismo formato de
 * la configuracion inicial).
 */
public final class CreadorManual {

    private CreadorManual() {}

    /**
     * Valida los datos, crea el proceso con ProcessFactory (llega en el ciclo actual) y lo
     * entrega a su computador. Solo hay procesos CPU-bound e IO-bound (B02).
     * @param cluster cluster donde se crea
     * @param d datos del proceso (los campos de otros tipos se ignoran)
     * @return el PCB creado
     * @throws IllegalArgumentException si algun dato no es valido; en ese caso no se crea nada
     */
    public static PCB crearProceso(Cluster cluster, ConfigDTO.ProcessConfig d) {
        if (cluster == null || d == null) {
            throw new IllegalArgumentException("Faltan los datos del proceso");
        }
        String nombre = Validador.nombre(d.name, "El nombre del proceso");
        if (d.type == null) {
            throw new IllegalArgumentException("Elige el tipo de proceso");
        }
        Computer pc = cluster.getComputer(d.computerId);
        if (pc == null) {
            throw new IllegalArgumentException("No existe el computador " + d.computerId);
        }
        Validador.rango(d.priority, "La prioridad", 1, Validador.MAX_VALOR);
        Validador.rango(d.memorySize, "La memoria", 1, pc.getRam().getTotalSize());
        Validador.rango(d.deadline, "El deadline", 1, Validador.MAX_VALOR);

        long llegada = cluster.getCurrentCycle();
        PCB pcb;
        switch (d.type) {
            case CPU_BOUND:
                Validador.rango(d.cycles, "Los ciclos de ejecucion", 1, Validador.MAX_VALOR);
                pcb = ProcessFactory.createCpuBound(nombre, d.priority, d.memorySize, d.cycles,
                        d.deadline, d.computerId, llegada);
                break;
            
            default:
                throw new IllegalArgumentException("El tipo " + d.type
                        + " ya no existe: el simulador no usa productores, consumidores ni buffers");
        }
        cluster.submit(pcb);
        return pcb;
    }
}