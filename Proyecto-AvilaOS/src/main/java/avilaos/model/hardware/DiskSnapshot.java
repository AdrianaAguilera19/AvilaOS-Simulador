/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package avilaos.model.hardware;

import avilaos.datastructures.DynamicArray;
import avilaos.enums.DiskSchedulingPolicy;
import avilaos.enums.HeadDirection;
import avilaos.enums.IOOperationType;
import avilaos.os.disk.DiskQueue;
import avilaos.os.disk.DiskRequest;
import avilaos.os.disk.DiskScheduler;

/**
 * Foto inmutable del disco en un instante: bloques con su dueno, posicion y sentido del cabezal,
 * politica activa y cola de solicitudes (la que se esta atendiendo va primero).
 * La GUI solo lee esta foto, nunca el disco vivo, asi no ve estados a medias.
 */
public final class DiskSnapshot {

    /** Una solicitud de la cola de E/S, ya copiada (no apunta al PCB vivo). */
    public static final class QueueEntry {
        private final String processName;
        private final int processId;
        private final IOOperationType operation;
        private final int targetBlock;
        private final String path;
        private final long arrivalCycle;
        private final int blocks;
        private final boolean inService;

        QueueEntry(String processName, int processId, IOOperationType operation, int targetBlock,
                String path, long arrivalCycle, int blocks, boolean inService) {
            this.processName = processName;
            this.processId = processId;
            this.operation = operation;
            this.targetBlock = targetBlock;
            this.path = path;
            this.arrivalCycle = arrivalCycle;
            this.blocks = blocks;
            this.inService = inService;
        }

        public String getProcessName() { return processName; }
        public int getProcessId() { return processId; }
        public IOOperationType getOperation() { return operation; }
        public int getTargetBlock() { return targetBlock; }
        /** Ruta del archivo, o "(swap)" para solicitudes de suspension/reactivacion. */
        public String getPath() { return path; }
        public long getArrivalCycle() { return arrivalCycle; }
        public int getBlocks() { return blocks; }
        public boolean isInService() { return inService; }
    }

    private final BlockInfo[] blocks;
    private final int headPosition;
    private final HeadDirection direction;
    private final DiskSchedulingPolicy policy;
    private final QueueEntry[] queue;

    private DiskSnapshot(BlockInfo[] blocks, int headPosition, HeadDirection direction,
            DiskSchedulingPolicy policy, QueueEntry[] queue) {
        this.blocks = blocks;
        this.headPosition = headPosition;
        this.direction = direction;
        this.policy = policy;
        this.queue = queue;
    }

    /** Toma la foto del disco y de su cola. Solo lee. */
    public static DiskSnapshot take(Disk disk, DiskScheduler scheduler) {
        BlockInfo[] all = disk.getMap().snapshot();
        DiskHead head = disk.getHead();
        DiskQueue q = scheduler.getQueue();
        DiskRequest inService = q.getInService();
        DynamicArray<DiskRequest> pending = q.pendingSnapshot();
        int extra = inService == null ? 0 : 1;
        QueueEntry[] entries = new QueueEntry[pending.size() + extra];
        int k = 0;
        if (inService != null) {
            entries[k++] = entryOf(inService, true);
        }
        for (int i = 0; i < pending.size(); i++) {
            entries[k++] = entryOf(pending.get(i), false);
        }
        return new DiskSnapshot(all, head.getPosition(), head.getDirection(),
                scheduler.getPolicy().getType(), entries);
    }

    private static QueueEntry entryOf(DiskRequest r, boolean inService) {
        String path = r.getOperation().isSwapOperation() ? "(swap)" : r.getRequester().getTargetPath();
        return new QueueEntry(r.getRequester().getName(), r.getRequester().getId(), r.getOperation(),
                r.getTargetBlock(), path == null ? "-" : path, r.getArrivalCycle(),
                r.getBlocksToTransfer(), inService);
    }

    public int totalBlocks() { return blocks.length; }

    /** BlockInfo es inmutable, se puede entregar directo. */
    public BlockInfo getBlock(int index) { return blocks[index]; }

    public int getHeadPosition() { return headPosition; }

    public HeadDirection getDirection() { return direction; }

    public DiskSchedulingPolicy getPolicy() { return policy; }

    public int queueSize() { return queue.length; }

    public QueueEntry getQueueEntry(int index) { return queue[index]; }

    public int freeCount() {
        int n = 0;
        for (BlockInfo b : blocks) {
            if (b.isFree()) {
                n++;
            }
        }
        return n;
    }
}
