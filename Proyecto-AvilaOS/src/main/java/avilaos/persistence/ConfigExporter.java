package avilaos.persistence;

import avilaos.datastructures.CustomLinkedList;
import avilaos.datastructures.CustomQueue;
import avilaos.enums.ProcessType;
import avilaos.model.hardware.Cluster;
import avilaos.model.hardware.Computer;
import avilaos.model.process.PCB;
import avilaos.sync.BoundedBuffer;

/**
 * Hace lo contrario de ConfigLoader: describe un cluster como una ConfigDTO, lista para
 * guardarla con JSONManager. Guarda los parametros del cluster (ciclo, latencia, computadores,
 * RAM, politica y quantum), sus buffers y todos sus procesos con los datos con que se crearon
 * (el deadline inicial, no el que les queda), de modo que al cargarla se repite la carga inicial.
 */
public class ConfigExporter {

    private ConfigExporter() { }

    /**
     * @param cluster cluster que se quiere guardar
     * @return la configuracion que lo describe
     */
    public static ConfigDTO fromCluster(Cluster cluster) {
        ConfigDTO config = new ConfigDTO();
        config.cycleDurationMs = cluster.getCycleDurationMs();
        config.networkLatency = cluster.getNetworkLatency();

        CustomLinkedList<Computer> computers = cluster.getComputersSnapshot();
        config.computers = new ConfigDTO.ComputerConfig[computers.size()];
        for (int i = 0; i < computers.size(); i++) {
            Computer c = computers.get(i);
            ConfigDTO.ComputerConfig cc = new ConfigDTO.ComputerConfig();
            cc.name = c.getName();
            cc.ramSize = c.getRam().getTotalSize();
            cc.policy = c.getAlgorithm();
            cc.quantum = c.getQuantum();
            config.computers[i] = cc;
        }

        CustomLinkedList<BoundedBuffer> buffers = cluster.getBufferManager().getSnapshot();
        config.buffers = new ConfigDTO.BufferConfig[buffers.size()];
        for (int i = 0; i < buffers.size(); i++) {
            BoundedBuffer b = buffers.get(i);
            ConfigDTO.BufferConfig bc = new ConfigDTO.BufferConfig();
            bc.id = b.getId();
            bc.capacity = b.getCapacity();
            bc.hostComputerId = b.getHostComputerId();
            config.buffers[i] = bc;
        }

        // Todos los procesos de todos los computadores, en el orden en que se crearon (por id)
        PCB[] all = new PCB[0];
        for (int i = 0; i < computers.size(); i++) {
            all = collect(all, computers.get(i));
        }
        config.processes = new ConfigDTO.ProcessConfig[all.length];
        for (int i = 0; i < all.length; i++) {
            config.processes[i] = describe(all[i]);
        }
        return config;
    }

    /** Agrega a la lista los procesos del computador que todavia no estan en ella, ordenados por id. */
    private static PCB[] collect(PCB[] soFar, Computer computer) {
        CustomQueue<PCB> found = new CustomQueue<>();
        PCB running = computer.getRunning();
        if (running != null) {
            found.enqueue(running);
        }
        addAll(found, computer.getNewSnapshot());
        addAll(found, computer.getReadySnapshot());
        addAll(found, computer.getBlockedSnapshot());
        addAll(found, computer.getFinishedSnapshot());

        PCB[] result = new PCB[soFar.length + found.size()];
        int n = 0;
        for (int i = 0; i < soFar.length; i++) {
            result[n++] = soFar[i];
        }
        for (int i = 0; i < found.size(); i++) {
            if (!contains(result, n, found.get(i))) {
                result[n++] = found.get(i);
            }
        }
        PCB[] exact = new PCB[n];
        for (int i = 0; i < n; i++) {
            exact[i] = result[i];
        }
        // ordenamiento por insercion segun el id (los arreglos son chicos)
        for (int i = 1; i < n; i++) {
            PCB key = exact[i];
            int j = i - 1;
            while (j >= 0 && exact[j].getId() > key.getId()) {
                exact[j + 1] = exact[j];
                j--;
            }
            exact[j + 1] = key;
        }
        return exact;
    }

    private static void addAll(CustomQueue<PCB> target, CustomQueue<PCB> source) {
        for (int i = 0; i < source.size(); i++) {
            target.enqueue(source.get(i));
        }
    }

    private static boolean contains(PCB[] array, int count, PCB p) {
        for (int i = 0; i < count; i++) {
            if (array[i] == p) {
                return true;
            }
        }
        return false;
    }

    private static ConfigDTO.ProcessConfig describe(PCB p) {
        ConfigDTO.ProcessConfig pc = new ConfigDTO.ProcessConfig();
        pc.name = p.getName();
        pc.type = p.getType();
        pc.priority = p.getPriority();
        pc.memorySize = p.getMemorySize();
        pc.deadline = p.getInitialDeadline();
        pc.computerId = p.getComputerId();
        if (p.getType() == ProcessType.PRODUCER || p.getType() == ProcessType.CONSUMER) {
            pc.bufferId = p.getAssociatedBufferId();
            pc.cyclesPerItem = p.getCyclesPerItem();
            pc.itemsRequired = p.getItemsRequired();
        } else {
            pc.cycles = p.getTotalInstructions();
            if (p.getType() == ProcessType.IO_BOUND) {
                pc.ioInterval = p.getIoInterval();
                pc.ioDuration = p.getIoDuration();
            }
        }
        return pc;
    }
}