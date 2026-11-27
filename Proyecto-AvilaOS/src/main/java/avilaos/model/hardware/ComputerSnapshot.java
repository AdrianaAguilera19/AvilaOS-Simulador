package avilaos.model.hardware;

import avilaos.datastructures.CustomQueue;
import avilaos.enums.ExecutionMode;
import avilaos.model.process.PCB;

/**
 * Foto consistente del estado de un computador en un instante (entre dos ciclos).
 * La GUI la usa para dibujar CPU y colas sin ver estados a medias. No modifica nada.
 */
public final class ComputerSnapshot {

    private final PCB running;
    private final ExecutionMode mode;
    private final CustomQueue<PCB> newQueue;
    private final CustomQueue<PCB> readyQueue;
    private final CustomQueue<PCB> blockedQueue;
    private final CustomQueue<PCB> finishedQueue;
        private final CustomQueue<PCB> readySuspendedQueue;
    private final CustomQueue<PCB> blockedSuspendedQueue;
        private final DiskSnapshot disk;   // G20: foto del disco (null si no hay disco conectado)

    public ComputerSnapshot(PCB running, ExecutionMode mode, CustomQueue<PCB> newQueue,
                            CustomQueue<PCB> readyQueue, CustomQueue<PCB> blockedQueue,
                                                        CustomQueue<PCB> finishedQueue, CustomQueue<PCB> readySuspendedQueue,
                            CustomQueue<PCB> blockedSuspendedQueue) {
        this.running = running;
        this.mode = mode;
        this.newQueue = newQueue;
        this.readyQueue = readyQueue;
        this.blockedQueue = blockedQueue;
        this.finishedQueue = finishedQueue;
                this.readySuspendedQueue = readySuspendedQueue;
        this.blockedSuspendedQueue = blockedSuspendedQueue;
                this.disk = null;
    }

    /** @return proceso en la CPU, o null si la CPU esta libre */
    public PCB getRunning() { return running; }
    public ExecutionMode getMode() { return mode; }
    public CustomQueue<PCB> getNewQueue() { return newQueue; }
    public CustomQueue<PCB> getReadyQueue() { return readyQueue; }
    public CustomQueue<PCB> getBlockedQueue() { return blockedQueue; }
    public CustomQueue<PCB> getFinishedQueue() { return finishedQueue; }
        public CustomQueue<PCB> getReadySuspendedQueue() { return readySuspendedQueue; }
    public CustomQueue<PCB> getBlockedSuspendedQueue() { return blockedSuspendedQueue; }
    
    /** G20: foto del disco, o null si esta foto se tomo sin disco. */
    public DiskSnapshot getDisk() { return disk; }

    /** G20: devuelve una foto igual a esta pero con la foto del disco incluida. */
    public ComputerSnapshot withDisk(DiskSnapshot diskSnapshot) {
        return new ComputerSnapshot(running, mode, newQueue, readyQueue, blockedQueue, finishedQueue,
                readySuspendedQueue, blockedSuspendedQueue, diskSnapshot);
    }

    private ComputerSnapshot(PCB running, ExecutionMode mode, CustomQueue<PCB> newQueue,
            CustomQueue<PCB> readyQueue, CustomQueue<PCB> blockedQueue, CustomQueue<PCB> finishedQueue,
            CustomQueue<PCB> readySuspendedQueue, CustomQueue<PCB> blockedSuspendedQueue, DiskSnapshot disk) {
        this.running = running;
        this.mode = mode;
        this.newQueue = newQueue;
        this.readyQueue = readyQueue;
        this.blockedQueue = blockedQueue;
        this.finishedQueue = finishedQueue;
        this.readySuspendedQueue = readySuspendedQueue;
        this.blockedSuspendedQueue = blockedSuspendedQueue;
        this.disk = disk;
    }

    /** @return cantidad total de procesos de este computador (CPU + todas las colas) */
    public int totalProcesses() {
        return (running == null ? 0 : 1) + newQueue.size() + readyQueue.size()
                                + blockedQueue.size() + finishedQueue.size()
                + readySuspendedQueue.size() + blockedSuspendedQueue.size();
    }
}