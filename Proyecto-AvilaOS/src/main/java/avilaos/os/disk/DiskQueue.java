/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package avilaos.os.disk;

import avilaos.datastructures.DynamicArray;
import java.util.concurrent.Semaphore;

/**
 * Cola unica de E/S del disco. Guarda las solicitudes pendientes en orden de llegada y lleva
 * la cuenta de la que esta en servicio: el disco atiende una sola a la vez.
 * Todo acceso pasa por un semaforo; la GUI lee con pendingSnapshot().
 * @author Antonella Ruiz
 */
public class DiskQueue {
    private final DynamicArray<DiskRequest> pending = new DynamicArray<>();
    private DiskRequest inService;
    private final Semaphore lock = new Semaphore(1);

    /** Agrega una solicitud al final de la cola. */
    public void add(DiskRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("No se puede encolar una solicitud vacia");
        }
        lock.acquireUninterruptibly();
        try {
            if (pending.contains(request) || request == inService) {
                throw new IllegalStateException("La solicitud ya esta en el disco");
            }
            pending.add(request);
        } finally {
            lock.release();
        }
    }

    public int size() {
        lock.acquireUninterruptibly();
        try {
            return pending.size();
        } finally {
            lock.release();
        }
    }

    public boolean isEmpty() {
        return size() == 0;
    }

    /** Solicitud pendiente en esa posicion (0 = la que llego primero). */
    public DiskRequest get(int index) {
        lock.acquireUninterruptibly();
        try {
            return pending.get(index);
        } finally {
            lock.release();
        }
    }

    public boolean isBusy() {
        lock.acquireUninterruptibly();
        try {
            return inService != null;
        } finally {
            lock.release();
        }
    }

    /** Solicitud que el disco esta atendiendo, o null. */
    public DiskRequest getInService() {
        lock.acquireUninterruptibly();
        try {
            return inService;
        } finally {
            lock.release();
        }
    }

    /**
     * Saca la solicitud de esa posicion de la cola y la pone en servicio.
     * Falla si el disco ya esta atendiendo otra.
     */
    public DiskRequest startService(int index) {
        lock.acquireUninterruptibly();
        try {
            if (inService != null) {
                throw new IllegalStateException("El disco ya esta atendiendo una solicitud");
            }
            inService = pending.removeAt(index);
            return inService;
        } finally {
            lock.release();
        }
    }

    /** Termina el servicio actual y devuelve la solicitud atendida. */
    public DiskRequest finishService() {
        lock.acquireUninterruptibly();
        try {
            if (inService == null) {
                throw new IllegalStateException("El disco no esta atendiendo nada");
            }
            DiskRequest done = inService;
            inService = null;
            return done;
        } finally {
            lock.release();
        }
    }

    /** Copia de las pendientes en orden de llegada (para la GUI y las politicas). */
    public DynamicArray<DiskRequest> pendingSnapshot() {
        lock.acquireUninterruptibly();
        try {
            return pending.copy();
        } finally {
            lock.release();
        }
    }
}