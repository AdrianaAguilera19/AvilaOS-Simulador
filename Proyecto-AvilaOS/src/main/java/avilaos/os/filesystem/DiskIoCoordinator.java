package avilaos.os.filesystem;

import avilaos.datastructures.DynamicArray;
import avilaos.enums.BlockReason;
import avilaos.enums.IOOperationType;
import avilaos.enums.LogCategory;
import avilaos.enums.NodeType;
import avilaos.enums.Permission;
import avilaos.enums.ProcessState;
import avilaos.enums.StepKind;
import avilaos.interfaces.Clockable;
import avilaos.interfaces.DiskListener;
import avilaos.log.EventLogger;
import avilaos.model.hardware.Computer;
import avilaos.model.hardware.Disk;
import avilaos.model.process.PCB;
import avilaos.model.process.ProcessFactory;
import avilaos.model.process.StepOutcome;
import avilaos.os.disk.DiskDecisionLogger;
import avilaos.os.disk.DiskQueue;
import avilaos.os.disk.DiskRecordLog;
import avilaos.os.disk.DiskRequest;
import avilaos.os.disk.DiskScheduler;

/**
 * Coordinador del subsistema de procesos de E/S y disco (E03-E10).
 * Conecta Computer, Disk, DiskScheduler, SecuredFileSystem, DiskRecordLog y EventLogger.
 */
public final class DiskIoCoordinator implements Clockable, DiskListener {

    private final Computer computer;
    private final Disk disk;
    private final DiskScheduler diskScheduler;
    private final SecuredFileSystem securedFs;
    private final DiskRecordLog recordLog;
    private final EventLogger logger;
    private final DynamicArray<DiskIoProcessRunner> runners = new DynamicArray<>();

    private long currentCycle = 0;
    private volatile boolean holdDiskDispatch = false;

    public DiskIoCoordinator(Computer computer, Disk disk, DiskScheduler diskScheduler,
            SecuredFileSystem securedFs, DiskRecordLog recordLog, EventLogger logger) {
        if (computer == null || disk == null || diskScheduler == null || securedFs == null) {
            throw new IllegalArgumentException("Faltan componentes para coordinar los procesos de E/S");
        }
        this.computer = computer;
        this.disk = disk;
        this.diskScheduler = diskScheduler;
        this.securedFs = securedFs;
        this.recordLog = recordLog != null ? recordLog : new DiskRecordLog();
        this.logger = logger;

        this.disk.setListener(this);
        if (logger != null) {
            this.diskScheduler.setDecisionListener(new DiskDecisionLogger(logger, disk.totalBlocks()));
        }
    }

    public Computer getComputer() { return computer; }
    public Disk getDisk() { return disk; }
    public DiskScheduler getDiskScheduler() { return diskScheduler; }
    public DiskQueue getDiskQueue() { return diskScheduler.getQueue(); }
    public SecuredFileSystem getSecuredFileSystem() { return securedFs; }
    public DiskRecordLog getRecordLog() { return recordLog; }

    public void setHoldDiskDispatch(boolean holdDiskDispatch) {
        this.holdDiskDispatch = holdDiskDispatch;
    }

    public boolean isHoldDiskDispatch() { return holdDiskDispatch; }

    /** E03: Registra el runner del PCB de E/S (archivo) y lo admite en la cola de Nuevos. */
    public DiskIoProcessRunner submitProcess(PCB pcb) {
        return submitProcess(pcb, NodeType.FILE, Permission.PUBLIC);
    }

    /** E03: Registra el runner indicando tipo de nodo y permiso para CREATE, y lo admite en el computador. */
    public DiskIoProcessRunner submitProcess(PCB pcb, NodeType createNodeType, Permission createPermission) {
        if (pcb == null || !pcb.hasDiskOperation()) {
            throw new IllegalArgumentException("El proceso debe tener una operacion de E/S de disco");
        }
        DiskIoProcessRunner runner = new DiskIoProcessRunner(pcb, this, createNodeType, createPermission);
        runners.add(runner);
        computer.registerRunner(runner);
        computer.admit(pcb);
        return runner;
    }

    /** Admite una lista de procesos de E/S (por ejemplo los cargados desde el JSON de procesos). */
    public DynamicArray<DiskIoProcessRunner> submitAll(DynamicArray<PCB> processes) {
        DynamicArray<DiskIoProcessRunner> created = new DynamicArray<>();
        if (processes == null) {
            return created;
        }
        for (int i = 0; i < processes.size(); i++) {
            created.add(submitProcess(processes.get(i)));
        }
        return created;
    }

    /** Helper para la GUI (G12, G13): crea y admite un proceso de E/S para crear un directorio. */
    public PCB submitCreateDirectoryProcess(String processName, int priority, int memorySize,
            int instructions, int deadline, long arrivalCycle, String directoryPath,
            Permission permission, ActiveUser actor) {
        ActiveUser who = actor != null ? actor : ActiveUser.admin();
        PCB pcb = ProcessFactory.createDiskIo(processName, priority, memorySize, instructions,
                deadline, computer.getId(), arrivalCycle, "CREATE", directoryPath, 1, null,
                who.getName(), who.getMode());
        submitProcess(pcb, NodeType.DIRECTORY, permission != null ? permission : Permission.PUBLIC);
        return pcb;
    }

    /** Helper para la GUI (G12, G13): crea y admite un proceso de E/S para cualquier operacion CRUD. */
    public PCB submitFileOperationProcess(String processName, int priority, int memorySize,
            int instructions, int deadline, long arrivalCycle, String operationText,
            String targetPath, int fileSizeBlocks, String newName, Permission createPermission,
            ActiveUser actor) {
        ActiveUser who = actor != null ? actor : ActiveUser.admin();
        PCB pcb = ProcessFactory.createDiskIo(processName, priority, memorySize, instructions,
                deadline, computer.getId(), arrivalCycle, operationText, targetPath,
                fileSizeBlocks, newName, who.getName(), who.getMode());
        submitProcess(pcb, NodeType.FILE, createPermission != null ? createPermission : Permission.PUBLIC);
        return pcb;
    }

    public DiskIoProcessRunner findRunner(PCB pcb) {
        for (int i = 0; i < runners.size(); i++) {
            if (runners.get(i).getPcb() == pcb) {
                return runners.get(i);
            }
        }
        return null;
    }

    /**
     * Invocado por DiskIoProcessRunner en la CPU (modo sistema operativo, E04).
     * Valida la operacion bajo el semaforo del FS (F13); si falla (E09), registra el error
     * con la categoria correspondiente y termina el proceso; si es valida (E05), encola en disco.
     */
    StepOutcome initiateIoFromCpu(DiskIoProcessRunner runner) {
        PCB pcb = runner.getPcb();
        DiskIoCostTable.IoPlan plan = securedFs.planIo(pcb, runner.getCreateNodeType(), disk.getMap());

        if (!plan.isValid()) {
            String msg = "Error controlado en " + pcb.getName() + " (" + pcb.getDiskOperation()
                    + " " + pcb.getTargetPath() + "): " + plan.getErrorMessage();
            runner.markControlledFailure(plan.getErrorMessage());
            if (logger != null) {
                LogCategory cat = plan.isPermissionDenied() ? LogCategory.PERMISO : LogCategory.FS;
                logger.logSystem(currentCycle, cat, msg);
            }
            return new StepOutcome(StepKind.SYSTEM_INSTRUCTION, null, msg);
        }

        DiskRequest request = new DiskRequest(
                plan.getTargetBlock(),
                pcb.getDiskOperation(),
                pcb,
                currentCycle,
                plan.getBlocksToTransfer()
        );
        diskScheduler.getQueue().add(request);
        pcb.setState(ProcessState.BLOCKED);
        pcb.setBlockReason(BlockReason.DISK_IO);
        runner.markBlockedOnDisk(request, plan.getResolvedNewName());

        String desc = pcb.getName() + " ejecuta E/S en modo SO (" + pcb.getDiskOperation()
                + " " + pcb.getTargetPath() + " -> bloque " + plan.getTargetBlock()
                + ", " + plan.getBlocksToTransfer() + " bloques) y pasa a Bloqueado (DISK_IO)";
        return new StepOutcome(StepKind.BLOCKED, null, desc);
    }

    /**
     * Avanza un ciclo del subsistema de disco: respeta la pausa del disco (D16), despacha la
     * siguiente solicitud si el disco esta libre y ejecuta exactamente 1 tick de disco.
     */
    @Override
    public void onTick(long cycle) {
        this.currentCycle = cycle;
        if (!disk.isPaused() && !holdDiskDispatch && !disk.isBusy() && !diskScheduler.getQueue().isEmpty()) {
            DiskRequest next = diskScheduler.startNext(
                    cycle,
                    disk.getHead().getPosition(),
                    disk.getHead().getDirection()
            );
            if (next != null) {
                disk.begin(next, cycle);
            }
        }
        disk.onTick(cycle);
    }

    /**
     * Al terminar el disco una solicitud: registra metricas, aplica el cambio en SecuredFileSystem (E07),
     * registra el resultado en el log con LogCategory.FS y despierta al proceso a Listo (E06).
     */
    @Override
    public void onServiceFinished(DiskRequest request, long startCycle, long endCycle, int headBefore) {
        recordLog.onServiceFinished(request, startCycle, endCycle, headBefore);
        
        // S07: SUSPEND/REACTIVATE no son de un proceso de E/S: las cierra el planificador de mediano plazo
        if (request.getOperation().isSwapOperation()) {
            computer.getMediumTermScheduler().onSwapServiceFinished(request);
            diskScheduler.completeCurrent();
            return;
        }

        PCB pcb = request.getRequester();
        DiskIoProcessRunner runner = findRunner(pcb);

        if (request.getOperation().isFileOperation()) {
            FsResult result = applyFileSystemEffect(pcb, runner);
            if (runner != null) {
                runner.markDiskIoCompleted(result);
            }
            if (logger != null && result != null) {
                boolean permDenied = !result.isOk() && result.getMessage() != null
                        && result.getMessage().startsWith("Permiso denegado");
                LogCategory cat = permDenied ? LogCategory.PERMISO : LogCategory.FS;
                String status = result.isOk() ? "Completa E/S de " : "Error al completar E/S de ";
                logger.logSystem(endCycle, cat, status + pcb.getName() + ": " + result.getMessage());
            }
        }

        // E06: el proceso pasa a Listo (READY), nunca directo a Ejecucion
        pcb.setState(ProcessState.READY);
        diskScheduler.completeCurrent();
    }

    private FsResult applyFileSystemEffect(PCB pcb, DiskIoProcessRunner runner) {
        ActiveUser who = DiskIoCostTable.resolveUser(pcb);
        String path = pcb.getTargetPath().trim();
        IOOperationType op = pcb.getDiskOperation();

        switch (op) {
            case CREATE:
                NodeType nodeType = runner != null ? runner.getCreateNodeType() : NodeType.FILE;
                Permission perm = runner != null ? runner.getCreatePermission() : Permission.PUBLIC;
                if (nodeType == NodeType.DIRECTORY) {
                    return securedFs.createDirectory(who, path, perm);
                }
                int slash = path.lastIndexOf('/');
                String parentPath = slash == 0 ? "/" : path.substring(0, slash);
                String name = path.substring(slash + 1);
                return securedFs.createFile(who, parentPath, name, pcb.getFileSizeBlocks(), perm, pcb.getId());

            case READ:
                return securedFs.read(who, path);

            case UPDATE:
                String newName = runner != null && runner.getResolvedNewName() != null
                        ? runner.getResolvedNewName()
                        : pcb.getNewName();
                if (newName == null || newName.isBlank()) {
                    PathResult found = securedFs.getFileSystem().getTree().find(path);
                    newName = found.isFound()
                            ? DiskIoCostTable.resolveUpdateName(found.getNode(), null)
                            : "renombrado";
                }
                return securedFs.rename(who, path, newName);

            case DELETE:
                return securedFs.delete(who, path);

            default:
                return FsResult.rejected("Operacion no aplicable al sistema de archivos: " + op);
        }
    }
}