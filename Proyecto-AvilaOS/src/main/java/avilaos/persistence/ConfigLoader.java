/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package avilaos.persistence;

import avilaos.model.hardware.Cluster;
import avilaos.model.process.PCB;
import avilaos.model.process.ProcessFactory;
import avilaos.scheduler.PolicyFactory;
import avilaos.sync.BoundedBuffer;
import avilaos.enums.ProcessType;

/**
 * Convierte una configuracion (datos) en un Cluster listo para simular
 * Crea los computadores, los buffers y los procesos (conectando a los productores
 * y consumidores con su buffer)
 * @author Antonella Ruiz
 */
public class ConfigLoader {
    private ConfigLoader() {}

    /**
     * Arma el cluster descrito por la configuracion (sin encender el reloj)
     * @param config configuracion a aplicar
     * @return el cluster con sus computadores, buffers y procesos iniciales
     * @throws ConfigException si la configuracion no es valida
     */
    public static Cluster build(ConfigDTO config) throws ConfigException {
        config.validate();
        Cluster cluster = new Cluster(config.cycleDurationMs, config.networkLatency);

        // Los computadores reciben ids 1, 2, 3... en el mismo orden del arreglo
        for (int i = 0; i < config.computers.length; i++) {
            ConfigDTO.ComputerConfig c = config.computers[i];
            cluster.addComputer(c.name, PolicyFactory.create(c.policy, c.quantum), c.ramSize);
        }

        // Los buffers se crean antes que los procesos. El BufferManager les asigna ids
        // 1, 2, 3...; el id del JSON puede ser otro, por eso se guarda la equivalencia.
        int cantidad = (config.buffers == null) ? 0 : config.buffers.length;
        int[] idsJson = new int[cantidad];
        int[] idsReales = new int[cantidad];
        for (int i = 0; i < cantidad; i++) {
            ConfigDTO.BufferConfig b = config.buffers[i];
            try {
                BoundedBuffer creado = cluster.getBufferManager().createBuffer(b.capacity, b.hostComputerId);
                idsJson[i] = b.id;
                idsReales[i] = creado.getId();
            } catch (IllegalArgumentException | IllegalStateException e) {
                throw new ConfigException("No se pudo crear el buffer " + b.id + ": " + e.getMessage());
            }
        }

        // La carga inicial llega en el ciclo 0
        if (config.processes != null) {
            for (int i = 0; i < config.processes.length; i++) {
                ConfigDTO.ProcessConfig p = config.processes[i];
                boolean usaBuffer = p.type == ProcessType.PRODUCER || p.type == ProcessType.CONSUMER;
                PCB pcb = createProcess(p, usaBuffer ? realBufferId(p.bufferId, idsJson, idsReales) : 0);
                if (usaBuffer) {
                    cluster.getComputer(p.computerId)
                            .registerRunner(cluster.getBufferManager().createRunner(pcb));
                }
                cluster.submit(pcb);
            }
        }
        return cluster;
    }

    /** Traduce el id de buffer escrito en el JSON al id real que dio el BufferManager. */
    private static int realBufferId(int idJson, int[] idsJson, int[] idsReales) {
        for (int i = 0; i < idsJson.length; i++) {
            if (idsJson[i] == idJson) {
                return idsReales[i];
            }
        }
        return idJson;
    }

    /**
     * Crea el PCB de un proceso con ProcessFactory segun su tipo.
     * @param p datos del proceso
     * @param bufferId id real del buffer (solo para productor y consumidor)
     * @return el PCB creado
     */
    private static PCB createProcess(ConfigDTO.ProcessConfig p, int bufferId) {
        switch (p.type) {
            case CPU_BOUND:
                return ProcessFactory.createCpuBound(p.name, p.priority, p.memorySize, p.cycles,
                        p.deadline, p.computerId, 0);
            case IO_BOUND:
                return ProcessFactory.createIoBound(p.name, p.priority, p.memorySize, p.cycles,
                        p.ioInterval, p.ioDuration, p.deadline, p.computerId, 0);
            case PRODUCER:
                return ProcessFactory.createProducer(p.name, p.priority, p.memorySize, bufferId,
                        p.cyclesPerItem, p.itemsRequired, p.deadline, p.computerId, 0);
            default:
                return ProcessFactory.createConsumer(p.name, p.priority, p.memorySize, bufferId,
                        p.cyclesPerItem, p.itemsRequired, p.deadline, p.computerId, 0);
        }
    }
}