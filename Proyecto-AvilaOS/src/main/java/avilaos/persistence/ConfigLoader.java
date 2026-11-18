package avilaos.persistence;

import avilaos.model.hardware.Cluster;
import avilaos.model.process.PCB;
import avilaos.model.process.ProcessFactory;
import avilaos.scheduler.PolicyFactory;

/**
 * Convierte una configuracion (datos) en un Cluster listo para simular
 * Crea los computadores y los procesos iniciales
 * @author Antonella Ruiz
 */
public class ConfigLoader {
    private ConfigLoader() {}

    /**
     * Arma el cluster descrito por la configuracion (sin encender el reloj)
     * @param config configuracion a aplicar
     * @return el cluster con sus computadores y procesos iniciales
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

        // La carga inicial llega en el ciclo 0
        if (config.processes != null) {
            for (int i = 0; i < config.processes.length; i++) {
                cluster.submit(createProcess(config.processes[i]));
            }
        }
        return cluster;
    }

    /**
     * Crea el PCB de un proceso con ProcessFactory segun su tipo.
     * @param p datos del proceso
     * @return el PCB creado
     */
    private static PCB createProcess(ConfigDTO.ProcessConfig p) {
        switch (p.type) {
            case CPU_BOUND:
                return ProcessFactory.createCpuBound(p.name, p.priority, p.memorySize, p.cycles,
                        p.deadline, p.computerId, 0);
            case IO_BOUND:
                return ProcessFactory.createIoBound(p.name, p.priority, p.memorySize, p.cycles,
                        p.ioInterval, p.ioDuration, p.deadline, p.computerId, 0);
            default:
                throw new IllegalArgumentException("Tipo de proceso no admitido: " + p.type);
        }
    }
}