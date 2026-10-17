/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package avilaos.model.hardware;

import java.util.concurrent.Semaphore;

/**
 *Memoria Principal de un computador
 * No guarda direcciones ni particiones, solo controla la memoria usada 
 * @author Antonella Ruiz
 */
public class RAM {
    private final int totalSize;
    private int usedSize =0;
    //Semaforo que protege el espacio usado(usedSize)
    private final Semaphore mutex = new Semaphore(1);
    
    /**
     * 
     * @param totalSize tamano total de la RAM
     */
    public RAM(int totalSize){
        if (totalSize <=0){throw new IllegalArgumentException("El tamano de la RAM debe ser mayor a 0");}
        this.totalSize=totalSize;
    }
    
    /**
     * Intenta reservar memoria
     * @param size memoria necesaria
     * @return true si habia espacio y reservo, false si no cabe
     */
    public boolean allocate(int size) {
        if (size <= 0) {
            throw new IllegalArgumentException("La memoria a reservar debe ser mayor que 0");
        }
        mutex.acquireUninterruptibly();
        try {
            if (usedSize + size > totalSize) {
                return false;
            }
            usedSize += size;
            return true;
        } finally {
            mutex.release();
        }
    }
    
    /**
     * Libera la memoria que se habia reservado
     * @param size 
     */
    public void release (int size){
        if (size <= 0) {
            throw new IllegalArgumentException("La memoria a liberar debe ser mayor que 0");
        }
        mutex.acquireUninterruptibly();
        try {
            if (size > usedSize) {
                throw new IllegalStateException("Se intenta liberar mas memoria de la que esta usada");
            }
            usedSize -= size;
        } finally {
            mutex.release();
        }
    
   
    }
    
    public int getTotalSize(){return totalSize;}
    
    public int getUsedSize() {
        mutex.acquireUninterruptibly();
        try {
            return usedSize;
        } finally {
            mutex.release();
        }
    }

    public int getFreeSize() {
        return totalSize - getUsedSize();
    }
}
    
    

