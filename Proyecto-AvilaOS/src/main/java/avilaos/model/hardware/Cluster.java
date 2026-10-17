/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package avilaos.model.hardware;

import avilaos.datastructures.CustomLinkedList;
import avilaos.interfaces.SchedulingPolicy;
import avilaos.model.process.PCB;
import avilaos.enums.SchedulingAlgorithm;

/**
 *Cluster de Avila OS
 * reune las computadoras y el reloj global que las sincrniza
 * todas las computadoras son instancias de la misma clase 
 * @author Antonella Ruiz
 */
public class Cluster {
    public static final int MIN_COMPUTERS=2;
    
    //un unico reloj para todo el sistema
    private final GlobalClock clock;
    
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
    }
    
    
    /**
     * Crea una computadora nueva, le pone el siguiente ID y la registra en el reloj para que reciba cada ciclo
     * @param name nombre de la computadora
     * @param policy politica de planificacion inicial
     * @return computadora creada
     */
    public Computer addComputer (String name, SchedulingPolicy policy){
        Computer c= new Computer (computers.size() + 1,name,policy);
        computers.addLast(c);
        clock.register(c);
        return c;}
    
    
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
    
    /**
     * Entrga un proceso a la computadora que indica su propio computerID
     * el computerId del PCB no cambia nunca y el cluster no tiene ninguna operacion para mover procesos 
     * asi que un proceso no migra despues de asignarse
     * @param process proceso a ejecutar
     */
    public void submit (PCB process){
        Computer target =getComputer(process.getComputerId());
    
        if(target ==null){
            throw new IllegalArgumentException("No existe la computadora"+ process.getComputerId());}
        
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
     * Cambia el quantum de Round Robin de UNA computadora en caliente.
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

    public void pause() { clock.pause(); }
    public void resume() { clock.resume(); }
    public void stop() { clock.stop(); }

    /** 
     * Avanza un solo ciclo (modo paso a paso y pruebas)
     */
    public void tick() { clock.tick(); }

    public long getCurrentCycle() { return clock.getCurrentCycle(); }
    public int getNetworkLatency() { return networkLatency; }

    public void setNetworkLatency(int cycles) {
        if (cycles < 0) {
            throw new IllegalArgumentException("La latencia de red no puede ser negativa");
        }
        this.networkLatency = cycles;
    }

    public void setCycleDurationMs(int ms) { clock.setCycleDurationMS(ms); }

    public int getCycleDurationMs() { return clock.getCycleDurationMs(); }

    public int getComputerCount() { return computers.size(); }

    /** 
     * Copia de la lista de computadoras para recorrerla sin modificar el cluster
     */
    public CustomLinkedList<Computer> getComputersSnapshot() { return computers.copy(); }
    
    
    
}
