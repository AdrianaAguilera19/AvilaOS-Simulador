/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package avilaos.model.hardware;

import avilaos.datastructures.CustomLinkedList;
import avilaos.interfaces.Clockable;
import java.util.concurrent.Semaphore;

/**
 *Reloj global de la simulacion 
 * Implementa Runnable para poder ejecutarse dentro de un hilo (Thread)
 * @author Antonella Ruiz
 */
public class GlobalClock implements Runnable {
    private final CustomLinkedList<Clockable> listeners = new CustomLinkedList<>();
    private final Semaphore cycleMutex = new Semaphore(1);
    private long currentCycle =0;
    
    private volatile int cycleDurationMs;
    private volatile boolean running =false;
    private volatile boolean paused=false;
    private Thread thread;
    
    
    /**
     * Crea el reloj sin encenderlo
     * @param cycleDurationMs duracion de un ciclo en miliseg
     */
    public GlobalClock(int cycleDurationMs){
        if (cycleDurationMs <=0){throw new IllegalArgumentException("La duracion del ciclo debe ser mayor a 0");}
        this.cycleDurationMs=cycleDurationMs;}
    
    /**
     * Agrega un componente a la lista de los que reciben el aviso de cada ciclo
     * @param c 
     */
    public void register(Clockable c){listeners.addLast(c);}
    
    /**
     * Quita un componente de la lista
     * @param c 
     */
    public void unregister (Clockable c){listeners.remove(c);}
    
    /**
     * Devuelve el ciclo actual
     * Usa el semaforo porque puede llamarse desde otro hilo
     * @return 
     */
    public long getCurrentCycle(){
        cycleMutex.acquireUninterruptibly();
        try{
            return currentCycle;
        }finally{
            cycleMutex.release();}           
    }
    
    /**
     * Cambia la duracion del ciclo mientras el reloj corre
     * @param ms 
     */
    public void setCycleDurationMS(int ms){
        if(ms <= 0){
            throw new IllegalArgumentException("La duracion del ciclo debe ser mayor a 0");
        }
        this.cycleDurationMs=ms;}
    
    /**
     * obtiene la duracion del ciclo
     * @return 
     */
    public int getCycleDurationMs(){return cycleDurationMs;}
    
    /**
     * Avanza un ciclo, sube el contador y avisa a cada componente
     */
    public void tick(){
        long cycle;
        cycleMutex.acquireUninterruptibly();
        try{
            currentCycle++;
            cycle=currentCycle;}
        finally{
            cycleMutex.release();
        }
        CustomLinkedList<Clockable> snapshot = listeners.copy();
        for (int i = 0; i < snapshot.size(); i++) {
            snapshot.get(i).onTick(cycle);
        }
    
    }
    /**
     * Enciende el reloj creando su hilo
     * Si ya esta encendido no hace nada
     */
    public void start() {
        if (running) return;
        running = true;
        paused = false;
        thread = new Thread(this, "GlobalClock");
        thread.start();
    }
    
    /**
     * Pause el reloj, el hilo sigue vivo pero no avanza ciclos
     */
    public void pause() { paused = true; }

    /**
     * Reanuda el reloj despues de una pausa
     */
    public void resume() { paused = false; }
    
    /**
     * Apaga el reloj
     * si esta dormido el hilo lo despierta
     */
    public void stop() {
        running = false;
        if (thread != null) thread.interrupt();
    }

    public boolean isRunning() { return running; }

    public boolean isPaused() { return paused; }

    
    /**
     * Lo que hace el hilo del reloj
     * Espera un ciclo y avanza, repetidamente
     */
    @Override
    public void run() {
        while (running) {
            try {
                Thread.sleep(cycleDurationMs);
            } catch (InterruptedException e) {
                return;
            }
            if (running && !paused) {
                tick();
            }
        }
    }
    
    
    
}
