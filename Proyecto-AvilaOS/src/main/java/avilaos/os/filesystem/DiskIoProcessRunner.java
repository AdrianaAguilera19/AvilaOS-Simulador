package avilaos.os.filesystem;

import avilaos.enums.NodeType;
import avilaos.enums.Permission;
import avilaos.enums.StepKind;
import avilaos.interfaces.ProcessRunner;
import avilaos.model.process.PCB;
import avilaos.model.process.StepOutcome;
import avilaos.os.disk.DiskRequest;

/**
 * Ejecutor paso a paso de un proceso de E/S sobre el sistema de archivos (E03-E10).
 * Usa la interfaz ProcessRunner para integrarse con Computer.java sin modificarlo.
 */
public final class DiskIoProcessRunner implements ProcessRunner {

    private enum Phase { REQUEST_IO, POST_IO_CPU, DONE }

    private final PCB pcb;
    private final DiskIoCoordinator coordinator;
    private final NodeType createNodeType;
    private final Permission createPermission;

    private Phase phase = Phase.REQUEST_IO;
    private DiskRequest request;
    private String resolvedNewName;
    private FsResult operationResult;
    private boolean failed = false;

    public DiskIoProcessRunner(PCB pcb, DiskIoCoordinator coordinator) {
        this(pcb, coordinator, NodeType.FILE, Permission.PUBLIC);
    }

    public DiskIoProcessRunner(PCB pcb, DiskIoCoordinator coordinator,
            NodeType createNodeType, Permission createPermission) {
        if (pcb == null || !pcb.hasDiskOperation()) {
            throw new IllegalArgumentException("Se necesita un PCB con operacion de E/S");
        }
        if (coordinator == null) {
            throw new IllegalArgumentException("Falta el coordinador de E/S de disco");
        }
        if (createNodeType == null || createPermission == null) {
            throw new IllegalArgumentException("Falta el tipo de nodo o el permiso para CREATE");
        }
        this.pcb = pcb;
        this.coordinator = coordinator;
        this.createNodeType = createNodeType;
        this.createPermission = createPermission;
    }

    @Override
    public StepOutcome step() {
        if (isFinished()) {
            return new StepOutcome(StepKind.FINISHED, null, pcb.getName() + " termino");
        }

        if (phase == Phase.REQUEST_IO) {
            // E04 + E10: la llamada de E/S cuenta como 1 instruccion ejecutada en modo SO
            pcb.executeInstruction();
            return coordinator.initiateIoFromCpu(this);
        }

        if (phase == Phase.POST_IO_CPU) {
            if (pcb.getRemainingTime() > 0) {
                if (pcb.getRemainingTime() == 1) {
                    phase = Phase.DONE;
                }
                return new StepOutcome(StepKind.USER_INSTRUCTION, null,
                        pcb.getName() + " completa procesamiento tras E/S");
            }
            phase = Phase.DONE;
            return new StepOutcome(StepKind.SYSTEM_INSTRUCTION, null,
                    pcb.getName() + " cierra llamada de E/S sobre " + pcb.getTargetPath());
        }

        return new StepOutcome(StepKind.FINISHED, null, pcb.getName() + " termino");
    }

    void markBlockedOnDisk(DiskRequest diskRequest, String updateNewName) {
        this.request = diskRequest;
        this.resolvedNewName = updateNewName;
        this.phase = Phase.POST_IO_CPU;
    }

    void markControlledFailure(String errorMessage) {
        this.failed = true;
        this.operationResult = FsResult.rejected(errorMessage);
        while (!pcb.isFinished()) {
            pcb.executeInstruction();
        }
        this.phase = Phase.DONE;
    }

    void markDiskIoCompleted(FsResult result) {
        this.operationResult = result;
        if (result != null && !result.isOk()) {
            this.failed = true;
        }
    }

    @Override
    public boolean isFinished() {
        return phase == Phase.DONE && pcb.isFinished();
    }

    @Override
    public PCB getPcb() { return pcb; }

    public NodeType getCreateNodeType() { return createNodeType; }

    public Permission getCreatePermission() { return createPermission; }

    public DiskRequest getRequest() { return request; }

    public String getResolvedNewName() { return resolvedNewName; }

    public FsResult getOperationResult() { return operationResult; }

    public boolean hasFailed() { return failed; }
}