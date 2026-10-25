/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package avilaos.gui;

import avilaos.enums.ProcessType;
import avilaos.model.hardware.Cluster;
import avilaos.model.hardware.Computer;
import avilaos.model.process.PCB;
import avilaos.model.process.ProcessFactory;
import avilaos.persistence.ConfigDTO;
import avilaos.sync.BoundedBuffer;

/**
 * Crea buffers y procesos nuevos mientras la simulacion esta en marcha (I05).
 * No dibuja nada: la GUI solo le entrega los datos y muestra el mensaje de error si algo
 * no es valido. Asi las mismas reglas se pueden probar sin abrir ventanas.
 * Para los datos de un proceso se reutiliza ConfigDTO.ProcessConfig (el mismo formato de
 * la configuracion inicial); el campo bufferId es el id REAL del buffer en el cluster.
 */
public final class CreadorManual {

    private CreadorManual() {}

    /**
     * Crea un buffer en el computador anfitrion indicado (reserva su espacio en la RAM).
     * @param cluster cluster donde se crea
     * @param capacity capacidad del buffer (mayor que 0)
     * @param hostComputerId id del computador anfitrion
     * @return el buffer creado
     * @throws IllegalArgumentException si los datos no son validos o no cabe en la RAM del anfitrion
     */
    public static BoundedBuffer crearBuffer(Cluster cluster, int capacity, int hostComputerId) {
        if (cluster == null) {
            throw new IllegalArgumentException("Falta el cluster");
        }
        Validador.rango(capacity, "La capacidad", 1, Validador.MAX_VALOR);
        if (!cluster.existsComputer(hostComputerId)) {
            throw new IllegalArgumentException("No existe el computador " + hostComputerId);
        }
        try {
            return cluster.getBufferManager().createBuffer(capacity, hostComputerId);
        } catch (IllegalStateException e) {
            // no cabe en la RAM: se avisa con el mismo tipo de error que los demas datos malos
            throw new IllegalArgumentException(e.getMessage());
        }
    }

    /**
     * Valida los datos, crea el proceso con ProcessFactory (llega en el ciclo actual) y lo
     * entrega a su computador. Un productor o consumidor queda conectado a su buffer.
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
            case IO_BOUND:
                Validador.rango(d.cycles, "Los ciclos de ejecucion", 1, Validador.MAX_VALOR);
                Validador.rango(d.ioInterval, "El intervalo de E/S", 1, Validador.MAX_VALOR);
                Validador.rango(d.ioDuration, "La duracion de la E/S", 1, Validador.MAX_VALOR);
                pcb = ProcessFactory.createIoBound(nombre, d.priority, d.memorySize, d.cycles,
                        d.ioInterval, d.ioDuration, d.deadline, d.computerId, llegada);
                break;
            default:   // PRODUCER o CONSUMER
                Validador.rango(d.cyclesPerItem, "Los ciclos por elemento", 1, Validador.MAX_VALOR);
                Validador.rango(d.itemsRequired, "Los elementos a procesar", 1, Validador.MAX_VALOR);
                if ((long) d.cyclesPerItem * d.itemsRequired > Validador.MAX_VALOR) {
                    throw new IllegalArgumentException("Ciclos por elemento x elementos no puede pasar de "
                            + Validador.MAX_VALOR + " instrucciones");
                }
                if (!cluster.getBufferManager().exists(d.bufferId)) {
                    throw new IllegalArgumentException("El buffer B" + d.bufferId + " no existe: crealo primero");
                }
                pcb = d.type == ProcessType.PRODUCER
                        ? ProcessFactory.createProducer(nombre, d.priority, d.memorySize, d.bufferId,
                                d.cyclesPerItem, d.itemsRequired, d.deadline, d.computerId, llegada)
                        : ProcessFactory.createConsumer(nombre, d.priority, d.memorySize, d.bufferId,
                                d.cyclesPerItem, d.itemsRequired, d.deadline, d.computerId, llegada);
                // igual que ConfigLoader: el ejecutor se registra antes de entregar el proceso
                pc.registerRunner(cluster.getBufferManager().createRunner(pcb));
                break;
        }
        cluster.submit(pcb);
        return pcb;
    }
}