/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package avilaos.model.hardware;

import avilaos.datastructures.CustomLinkedList;
import avilaos.interfaces.SchedulingPolicy;
import avilaos.model.process.PCB;
import avilaos.enums.SchedulingAlgorithm;
import avilaos.interfaces.ProcessWaker;
import avilaos.log.EventLogger;
import avilaos.sync.BufferManager;

/**
 *Cluster de Avila OS
 * reune las computadoras y el reloj global que las sincrniza
 * todas las computadoras son instancias de la misma clase 
 * @author Antonella Ruiz
 */
public class Cluster implements ProcessWaker {
    public static final int MIN_COMPUTERS=2;
    
    //un unico reloj para todo el sistema
    private final GlobalClock clock;
    
    
    private final BufferManager bufferManager;
    
    // S11: registro global de eventos, compartido por todas las computadoras
    private final EventLogger logger = new EventLogger();
    
    //computadoras del cluster
    private final CustomLinkedList<Computer>computers = new CustomLinkedList<>();
    
    //latencia de red en ciclos
    private volatile int networkLatency;
    
    
    /**
     * 
     * @param cycleDurationMs duracion de un ciclo en miliseg
     * @param networkLatency  latencia de la red en ciclos 
     */
    public Cluster(int cycleDurationMs, int networkLatency){
        if(networkLatency <0){throw new IllegalArgumentException("La latencia de red no puede ser negativa");}
        
        this.clock = new GlobalClock (cycleDurationMs);
        this.networkLatency = networkLatency;
        this.bufferManager = new BufferManager(this);
    }

    /** 
     * 
     * @return el gestor de buffers del cluster 
     */
    public BufferManager getBufferManager() {
        return bufferManager;
    }
    
    /**
     * @return el registro global de eventos del sistema (S11)
     */
    public EventLogger getLogger() {
        return logger;
    }
    
    
    /**
     * Crea una computadora nueva, con cierto id secuencial y la registra en el reloj para que reciba cada ciclo
     * @param name nombre de la computadora
     * @param policy politica de planificacion inicial
     * @param ramSize tamano de la ram para esta computadora
     * @return la computadora creada 
     */
    public Computer addComputer(String name, SchedulingPolicy policy, int ramSize) {
        Computer c = new Computer(computers.size() + 1, name, policy, ramSize);
        c.setWaker(this);   // S05: para despertar procesos de otros computadores
                c.setLogger(logger);   // S11: todos escriben en el mismo log
        computers.addLast(c);
        clock.register(c);   // desde aqui el reloj le avisa de cada ciclo
        return c;
        }
    
    /** Crea una computadora con la RAM por defecto (para pruebas). */
    public Computer addComputer(String name, SchedulingPolicy policy) {
        return addComputer(name, policy, Computer.DEFAULT_RAM_SIZE);
    }
    
    /**
     * Busca una computadora por ID, devuelve null si no existe
     * @param id
     * @return 
     */
    public Computer getComputer(int id){
        CustomLinkedList<Computer>snapshot =computers.copy();
        
        for (int i =0; i < snapshot.size();i++){
            if(snapshot.get(i).getId() == id){return snapshot.get(i);}}
    
        return null;
    }
    
    
        /** Despierta a un proceso bloqueado: avisa al computador donde vive (no hay migración). */
    @Override
    public void wake(PCB pcb) {
        Computer c = getComputer(pcb.getComputerId());
        if (c == null) {
            throw new IllegalArgumentException("No existe el computador " + pcb.getComputerId());
        }
        c.requestWake(pcb);
    }
        /**
     * Indica si existe una computadora con ese id (P07). La GUI debe llamarlo ANTES de crear
     * el PCB, para validar la computadora elegida por el usuario sin gastar un ID de proceso.
     * @param computerId id elegido por el usuario
     * @return true si la computadora existe
     */
    public boolean existsComputer(int computerId) {
        return getComputer(computerId) != null;
    }
    
    /**
     * Entrga un proceso a la computadora que indica su propio computerID
     * el computerId del PCB no cambia nunca y el cluster no tiene ninguna operacion para mover procesos 
     * asi que un proceso no migra despues de asignarse
     * @param process proceso a ejecutar
     */
        public void submit (PCB process){
        if (process == null) {
            throw new IllegalArgumentException("El proceso no puede ser nulo");
        }
        Computer target =getComputer(process.getComputerId());
    
        if(target ==null){
            throw new IllegalArgumentException("No existe la computadora "+ process.getComputerId());}
        
        target.admit(process);
   
    }
    
    /**
     * Cambia la politica de UNA computadora en caliente; las demas no se tocan.
     * @param computerId id de la computadora
     * @param algorithm politica nueva
     */
    public void setPolicy(int computerId, SchedulingAlgorithm algorithm) {
        Computer c = getComputer(computerId);
        if (c == null) {
            throw new IllegalArgumentException("No existe la computadora " + computerId);
        }
        c.setPolicy(algorithm);
    }

    /**
     * Cambia el quantum de round robin de Una computadora en caliente
     * @param computerId id de la computadora
     * @param quantum nuevo (mayor a 0)
     */
    public void setQuantum(int computerId, int quantum) {
        Computer c = getComputer(computerId);
        if (c == null) {
            throw new IllegalArgumentException("No existe la computadora " + computerId);
        }
        c.setQuantum(quantum);
    }
    /** 
     * Enciende el reloj. Exige tener al menos MIN_COMPUTERS computadoras
     */
    public void start() {
        if (computers.size() < MIN_COMPUTERS) {
            throw new IllegalStateException("Se necesitan al menos " + MIN_COMPUTERS + " computadoras");
        }
        clock.start();
    }

    /**
     * Pausa el reloj de todo el cluster
     */
    public void pause() { clock.pause(); }
    
    /**
     * renauda el reloj despues de una pausa
     */
    public void resume() { clock.resume(); }
    
    /**
     * apaga el reloj
     */
    public void stop() { clock.stop(); }

    /** 
     * Avanza un solo ciclo (modo paso a paso y pruebas)
     */
    public void tick() { clock.tick(); }

    public long getCurrentCycle() { return clock.getCurrentCycle(); }
    public int getNetworkLatency() { return networkLatency; }

    /**
     * Cambia la latencia de red en caliente
     * @param cycles latencia en ciclos 
     */
    public void setNetworkLatency(int cycles) {
        if (cycles < 0) {
            throw new IllegalArgumentException("La latencia de red no puede ser negativa");
        }
        this.networkLatency = cycles;
    }
    
    /**
     * Cambia la duracion de un ciclo para todas las computadoras
     * @param ms duracion en miliseg
     */
    public void setCycleDurationMs(int ms) { clock.setCycleDurationMS(ms); }

    public int getCycleDurationMs() { return clock.getCycleDurationMs(); }

    public int getComputerCount() { return computers.size(); }

    /** 
     * Copia de la lista de computadoras para recorrerla sin modificar el cluster
     */
    public CustomLinkedList<Computer> getComputersSnapshot() { return computers.copy(); }
    
    
    
}
