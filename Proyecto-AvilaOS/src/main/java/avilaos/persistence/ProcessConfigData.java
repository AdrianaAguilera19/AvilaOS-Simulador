package avilaos.persistence;

import avilaos.datastructures.DynamicArray;
import avilaos.enums.DiskSchedulingPolicy;
import avilaos.enums.HeadDirection;
import avilaos.enums.IOOperationType;
import avilaos.enums.SchedulingAlgorithm;
import avilaos.enums.UserMode;
import avilaos.model.process.PCB;
import avilaos.model.process.ProcessFactory;

/**
 * Datos validados del JSON de procesos y configuracion (J04, J05, J06, J10 y J11).
 * Almacena las especificaciones de procesos en un DynamicArray propio (sin java.util)
 * y construye los PCB reales mediante ProcessFactory.createDiskIo cuando se arma la simulacion.
 */
public final class ProcessConfigData {

    private final int cycleDurationMs;
    private final int swapSizeBlocks;
    private final DiskSchedulingPolicy initialDiskPolicy;
    private final int headSpeed;
    private final HeadDirection initialHeadDirection;

    private final String computerName;
    private final int ramCapacity;
    private final SchedulingAlgorithm cpuPolicy;
    private final int quantum;

    private final DynamicArray<ProcessSpec> processes;

    public ProcessConfigData(int cycleDurationMs, int swapSizeBlocks, DiskSchedulingPolicy initialDiskPolicy,
            int headSpeed, HeadDirection initialHeadDirection, String computerName, int ramCapacity,
            SchedulingAlgorithm cpuPolicy, int quantum, DynamicArray<ProcessSpec> processes) {
        this.cycleDurationMs = cycleDurationMs;
        this.swapSizeBlocks = swapSizeBlocks;
        this.initialDiskPolicy = initialDiskPolicy;
        this.headSpeed = headSpeed;
        this.initialHeadDirection = initialHeadDirection;
        this.computerName = computerName;
        this.ramCapacity = ramCapacity;
        this.cpuPolicy = cpuPolicy;
        this.quantum = quantum;
        this.processes = processes.copy();
    }

    public int getCycleDurationMs() { return cycleDurationMs; }

    public int getSwapSizeBlocks() { return swapSizeBlocks; }

    public DiskSchedulingPolicy getInitialDiskPolicy() { return initialDiskPolicy; }

    public int getHeadSpeed() { return headSpeed; }

    public HeadDirection getInitialHeadDirection() { return initialHeadDirection; }

    public String getComputerName() { return computerName; }

    public int getRamCapacity() { return ramCapacity; }

    public SchedulingAlgorithm getCpuPolicy() { return cpuPolicy; }

    public int getQuantum() { return quantum; }

    public int getProcessCount() { return processes.size(); }

    public ProcessSpec getProcess(int index) { return processes.get(index); }

    public DynamicArray<ProcessSpec> getProcessesCopy() { return processes.copy(); }

    /**
     * J05: Construye todos los PCB de E/S usando ProcessFactory.createDiskIo.
     * Se invoca solo al armar la simulacion para no consumir IDs durante validaciones previas.
     */
    public DynamicArray<PCB> createProcesses(int computerId, long arrivalCycle) {
        DynamicArray<PCB> result = new DynamicArray<>();
        for (int i = 0; i < processes.size(); i++) {
            ProcessSpec s = processes.get(i);
            PCB pcb = ProcessFactory.createDiskIo(
                    s.getName(),
                    s.getPriority(),
                    s.getMemory(),
                    s.getInstructions(),
                    s.getDeadline(),
                    computerId,
                    arrivalCycle,
                    s.getOperation().name(),
                    s.getTargetPath(),
                    s.getFileSizeBlocks(),
                    s.getNewName(),
                    s.getUserName(),
                    s.getUserMode()
            );
            result.add(pcb);
        }
        return result;
    }

    /** Especificacion inmutable de un proceso de E/S leido del JSON. */
    public static final class ProcessSpec {
        private final String name;
        private final int priority;
        private final int memory;
        private final int deadline;
        private final int instructions;
        private final String targetPath;
        private final IOOperationType operation;
        private final int fileSizeBlocks;
        private final String newName;
        private final String userName;
        private final UserMode userMode;

        public ProcessSpec(String name, int priority, int memory, int deadline, int instructions,
                String targetPath, IOOperationType operation, int fileSizeBlocks, String newName,
                String userName, UserMode userMode) {
            this.name = name;
            this.priority = priority;
            this.memory = memory;
            this.deadline = deadline;
            this.instructions = instructions;
            this.targetPath = targetPath;
            this.operation = operation;
            this.fileSizeBlocks = fileSizeBlocks;
            this.newName = newName;
            this.userName = userName;
            this.userMode = userMode;
        }

        public String getName() { return name; }
        public int getPriority() { return priority; }
        public int getMemory() { return memory; }
        public int getDeadline() { return deadline; }
        public int getInstructions() { return instructions; }
        public String getTargetPath() { return targetPath; }
        public IOOperationType getOperation() { return operation; }
        public int getFileSizeBlocks() { return fileSizeBlocks; }
        public String getNewName() { return newName; }
        public String getUserName() { return userName; }
        public UserMode getUserMode() { return userMode; }
    }
}
