package avilaos.persistence;

import avilaos.datastructures.DynamicArray;
import avilaos.model.hardware.Cluster;
import avilaos.model.hardware.Computer;
import avilaos.model.hardware.Disk;
import avilaos.model.process.PCB;
import avilaos.os.disk.DiskQueue;
import avilaos.os.disk.DiskScheduler;
import avilaos.os.disk.policy.DiskPolicyFactory;
import avilaos.os.filesystem.ChainAllocator;
import avilaos.os.filesystem.FileTree;
import avilaos.scheduler.PolicyFactory;

/**
 * Coordina la carga obligatoria de los dos archivos JSON antes de arrancar el reloj (J08, J09, J11).
 * Mantiene separado el estado del sistema de archivos del lote de procesos para poder reiniciar
 * la simulacion con procesos nuevos preservando las modificaciones hechas en el disco.
 */
public final class SimulationSetup {

    private String fileSystemJsonSnapshot;
    private FileSystemData fileSystemPreview;
    private ProcessConfigData processConfig;

    public boolean hasFileSystem() { return fileSystemPreview != null; }

    public boolean hasProcessConfig() { return processConfig != null; }

    /** J08: Solo devuelve true cuando ambos JSON estan cargados y validados. */
    public boolean isReady() { return hasFileSystem() && hasProcessConfig(); }

    /** J08: Mensaje claro para la GUI indicando que JSON falta (null si ya esta listo). */
    public String missingMessage() {
        if (isReady()) {
            return null;
        }
        if (!hasFileSystem() && !hasProcessConfig()) {
            return "Falta cargar ambos archivos JSON: el del sistema de archivos y el de procesos.";
        }
        if (!hasFileSystem()) {
            return "Falta cargar el JSON del sistema de archivos antes de iniciar.";
        }
        return "Falta cargar el JSON de procesos y configuracion antes de iniciar.";
    }

    public FileSystemData getFileSystemPreview() { return fileSystemPreview; }

    public ProcessConfigData getProcessConfig() { return processConfig; }

    /** Carga y valida el JSON del sistema de archivos desde archivo. */
    public FileSystemData loadFileSystemFile(String path) throws ConfigException {
        FileSystemData loaded = FileSystemJsonManager.load(path);
        String normalized = FileSystemJsonManager.toJson(
                loaded.getFileTree(), loaded.getAllocator(), loaded.getTotalBlocks(), loaded.getInitialHead());
        this.fileSystemPreview = loaded;
        this.fileSystemJsonSnapshot = normalized;
        return loaded;
    }

    /** Carga y valida el JSON del sistema de archivos desde texto (util para pruebas). */
    public FileSystemData loadFileSystemJson(String jsonText) throws ConfigException {
        FileSystemData loaded = FileSystemJsonManager.parseJson(jsonText);
        String normalized = FileSystemJsonManager.toJson(
                loaded.getFileTree(), loaded.getAllocator(), loaded.getTotalBlocks(), loaded.getInitialHead());
        this.fileSystemPreview = loaded;
        this.fileSystemJsonSnapshot = normalized;
        return loaded;
    }

    /**
     * J09: Toma el estado actual (modificado) del disco/FS como nueva base para reiniciar
     * la simulacion con otro JSON de procesos sin perder los cambios en el disco.
     */
    public FileSystemData preserveModifiedFileSystem(FileTree tree, ChainAllocator allocator,
            int totalBlocks, int currentHead) throws ConfigException {
        String savedJson = FileSystemJsonManager.toJson(tree, allocator, totalBlocks, currentHead);
        return loadFileSystemJson(savedJson);
    }

    /** Carga y valida el JSON de procesos y configuracion desde archivo. */
    public ProcessConfigData loadProcessConfigFile(String path) throws ConfigException {
        ProcessConfigData loaded = ProcessConfigJsonManager.load(path);
        this.processConfig = loaded;
        return loaded;
    }

    /** Carga y valida el JSON de procesos y configuracion desde texto (util para pruebas). */
    public ProcessConfigData loadProcessConfigJson(String jsonText) throws ConfigException {
        ProcessConfigData loaded = ProcessConfigJsonManager.parseJson(jsonText);
        this.processConfig = loaded;
        return loaded;
    }

    /**
     * J11: Resumen en texto de la configuracion completa (4 parametros nuevos + parametros P1)
     * listo para mostrarse en la interfaz.
     */
    public String configSummary() {
        if (!isReady()) {
            return missingMessage();
        }
        return "Disco: " + fileSystemPreview.getTotalBlocks() + " bloques"
                + " | Swap: " + processConfig.getSwapSizeBlocks() + " bloques"
                + " | Politica disco: " + processConfig.getInitialDiskPolicy()
                + " | Cabezal inicial: " + fileSystemPreview.getInitialHead()
                + " (k=" + processConfig.getHeadSpeed() + ", " + processConfig.getInitialHeadDirection() + ")"
                + " | Ciclo: " + processConfig.getCycleDurationMs() + " ms"
                + " | RAM: " + processConfig.getRamCapacity()
                + " | CPU: " + processConfig.getCpuPolicy()
                + (processConfig.getQuantum() > 0 ? " (q=" + processConfig.getQuantum() + ")" : "")
                + " | Procesos: " + processConfig.getProcessCount();
    }

    /**
     * Arma todos los componentes de la simulacion (sin encender el reloj):
     * FileSystemData con su area de swap, Disk, Cluster de 1 computador, DiskQueue,
     * DiskScheduler y los PCB listos para admitir.
     */
    public SimulationBundle build() throws ConfigException {
        if (!isReady()) {
            throw new ConfigException(missingMessage());
        }
        FileSystemData fsData = FileSystemJsonManager.parseJson(
                fileSystemJsonSnapshot, processConfig.getSwapSizeBlocks());
        Disk disk = fsData.createDisk(processConfig.getHeadSpeed(), processConfig.getInitialHeadDirection());

        Cluster cluster = new Cluster(processConfig.getCycleDurationMs(), 0);
        Computer computer = cluster.addComputer(
                processConfig.getComputerName(),
                PolicyFactory.create(processConfig.getCpuPolicy(), processConfig.getQuantum()),
                processConfig.getRamCapacity()
        );
        if (processConfig.getQuantum() > 0) {
            computer.setQuantum(processConfig.getQuantum());
        }

        DiskQueue diskQueue = new DiskQueue();
        DiskScheduler diskScheduler = new DiskScheduler(
                diskQueue,
                DiskPolicyFactory.create(processConfig.getInitialDiskPolicy()),
                cluster
        );

        DynamicArray<PCB> processes = processConfig.createProcesses(computer.getId(), 0L);

        return new SimulationBundle(cluster, computer, disk, fsData, diskQueue, diskScheduler,
                processConfig, processes);
    }

    /** Paquete con la simulacion ensamblada y lista para conectar E/S y arrancar el reloj. */
    public static final class SimulationBundle {
        private final Cluster cluster;
        private final Computer computer;
        private final Disk disk;
        private final FileSystemData fileSystemData;
        private final DiskQueue diskQueue;
        private final DiskScheduler diskScheduler;
        private final ProcessConfigData processConfig;
        private final DynamicArray<PCB> initialProcesses;

        public SimulationBundle(Cluster cluster, Computer computer, Disk disk, FileSystemData fileSystemData,
                DiskQueue diskQueue, DiskScheduler diskScheduler, ProcessConfigData processConfig,
                DynamicArray<PCB> initialProcesses) {
            this.cluster = cluster;
            this.computer = computer;
            this.disk = disk;
            this.fileSystemData = fileSystemData;
            this.diskQueue = diskQueue;
            this.diskScheduler = diskScheduler;
            this.processConfig = processConfig;
            this.initialProcesses = initialProcesses;
        }

        public Cluster getCluster() { return cluster; }
        public Computer getComputer() { return computer; }
        public Disk getDisk() { return disk; }
        public FileSystemData getFileSystemData() { return fileSystemData; }
        public DiskQueue getDiskQueue() { return diskQueue; }
        public DiskScheduler getDiskScheduler() { return diskScheduler; }
        public ProcessConfigData getProcessConfig() { return processConfig; }
        public DynamicArray<PCB> getInitialProcesses() { return initialProcesses.copy(); }

        /** Entrega todos los procesos iniciales al unico computador del cluster. */
        public void submitAllProcesses() {
            for (int i = 0; i < initialProcesses.size(); i++) {
                cluster.submit(initialProcesses.get(i));
            }
        }
    }
}
