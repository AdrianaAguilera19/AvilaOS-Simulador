/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package avilaos.os.disk;

import avilaos.datastructures.DynamicArray;
import avilaos.enums.DiskSchedulingPolicy;
import avilaos.interfaces.DiskListener;
import java.util.concurrent.Semaphore;

/**
 * Registro de todas las solicitudes que el disco ya atendio, en el orden en que terminaron.
 * Es la unica fuente de las metricas del disco (desplazamiento total, esperas, atenciones por politica).
 * Protegido con semaforo: el disco escribe desde el hilo del reloj y la GUI lee un snapshot.
 * Se puede conectar directamente al disco como DiskListener.
 * @author Antonella Ruiz
 */
public class DiskRecordLog implements DiskListener {
    private final DynamicArray<DiskRecord> records = new DynamicArray<>();
    private final Semaphore mutex = new Semaphore(1);

    /**
     * Anota una solicitud atendida. La solicitud debe tener ya su politica (markServed).
     * @return el registro creado
     */
    public DiskRecord record(DiskRequest request, long startCycle, long endCycle, int headBefore) {
        if (request == null) {
            throw new IllegalArgumentException("Falta la solicitud");
        }
        if (!request.isServed()) {
            throw new IllegalArgumentException("La solicitud no tiene politica de atencion: no se puede registrar");
        }
        if (startCycle < request.getArrivalCycle()) {
            throw new IllegalArgumentException("El inicio (" + startCycle + ") no puede ser antes de la llegada ("
                    + request.getArrivalCycle() + ")");
        }
        if (endCycle < startCycle) {
            throw new IllegalArgumentException("El fin (" + endCycle + ") no puede ser antes del inicio (" + startCycle + ")");
        }
        if (headBefore < 0) {
            throw new IllegalArgumentException("La posicion del cabezal no puede ser negativa");
        }
        mutex.acquireUninterruptibly();
        try {
            DiskRecord record = new DiskRecord(records.size() + 1, request.getRequester().getName(),
                    request.getOperation(), request.getTargetBlock(), request.getServedWith(),
                    request.getArrivalCycle(), startCycle, endCycle, headBefore);
            records.add(record);
            return record;
        } finally {
            mutex.release();
        }
    }

    /** Lo llama el disco al terminar una solicitud. */
    @Override
    public void onServiceFinished(DiskRequest request, long startCycle, long endCycle, int headBefore) {
        record(request, startCycle, endCycle, headBefore);
    }

    public int size() {
        mutex.acquireUninterruptibly();
        try {
            return records.size();
        } finally {
            mutex.release();
        }
    }

    public DiskRecord get(int index) {
        mutex.acquireUninterruptibly();
        try {
            return records.get(index);
        } finally {
            mutex.release();
        }
    }

    /** Copia de los registros para que la GUI los recorra sin bloquear al disco. */
    public DiskRecord[] snapshot() {
        mutex.acquireUninterruptibly();
        try {
            DiskRecord[] copy = new DiskRecord[records.size()];
            for (int i = 0; i < copy.length; i++) {
                copy[i] = records.get(i);
            }
            return copy;
        } finally {
            mutex.release();
        }
    }

    /** Suma de los desplazamientos de todas las solicitudes atendidas. */
    public long totalDisplacement() {
        long total = 0;
        for (DiskRecord r : snapshot()) {
            total += r.getDisplacement();
        }
        return total;
    }

    /** Suma de los desplazamientos de las solicitudes atendidas con esa politica. */
    public long totalDisplacement(DiskSchedulingPolicy policy) {
        long total = 0;
        for (DiskRecord r : snapshot()) {
            if (r.getPolicy() == policy) {
                total += r.getDisplacement();
            }
        }
        return total;
    }

    /** Cuantas solicitudes se atendieron con esa politica. */
    public int count(DiskSchedulingPolicy policy) {
        int n = 0;
        for (DiskRecord r : snapshot()) {
            if (r.getPolicy() == policy) {
                n++;
            }
        }
        return n;
    }

    /** Promedio de ciclos de espera en cola (0 si no hay registros). */
    public double averageWaitCycles() {
        DiskRecord[] all = snapshot();
        if (all.length == 0) {
            return 0;
        }
        long total = 0;
        for (DiskRecord r : all) {
            total += r.getWaitCycles();
        }
        return (double) total / all.length;
    }

    /** Promedio de ciclos de atencion en el disco (0 si no hay registros). */
    public double averageServiceCycles() {
        DiskRecord[] all = snapshot();
        if (all.length == 0) {
            return 0;
        }
        long total = 0;
        for (DiskRecord r : all) {
            total += r.getServiceCycles();
        }
        return (double) total / all.length;
    }
}