/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package avilaos.model.hardware;

import avilaos.datastructures.CustomQueue;
import avilaos.enums.ExecutionMode;
import avilaos.enums.ProcessState;
import avilaos.interfaces.Clockable;
import avilaos.interfaces.SchedulingPolicy;
import avilaos.model.process.PCB;

/**
 *Un computador del cluster 
 * Cada instancia tiene sus propias colas y su propia politica de planificacion
 * avanza un paso cada vez que el reloj global le avisa
 * @author Antonella Ruiz
 */
public class Computer implements Clockable {
    
    //identificacion del computador
    private final int id; 
    private final String name; 
    
    //politica actual
    private volatile SchedulingPolicy policy;
    
    //fix
    private volatile boolean policyChanged=false;
    
    //colas propias de este computador
    private final CustomQueue<PCB> newQueue = new CustomQueue<>();
    private final CustomQueue<PCB> readyQueue = new CustomQueue<>();
    private final CustomQueue<PCB> blockedQueue = new CustomQueue<>();
    private final CustomQueue<PCB> finishedQueue = new CustomQueue<>();
    
    //proceso que esta usando la CPU
    private PCB running;
    
    //ciclos seguidos que lleva ese proceso en la CPU 
    private int cyclesInCpu;
    
    //Si la CPU ejecuta un programa de usuario o del so
    private ExecutionMode mode =ExecutionMode.USUARIO; 
    
    
    /**
     * Crea el computador con sus colas vacias y la CPU libre
     * @param id identificacion del computador
     * @param name nombre que se muestra en la interfaz
     * @param policy politica de planificacion inicial
     */
    public Computer (int id, String name, SchedulingPolicy policy){
        if (name== null || name.isBlank()){
            throw new IllegalArgumentException("El nombre del computador no puede estar vacio");
        }
        if (policy == null) {
            throw new IllegalArgumentException("El computador necesita una politica de planificacion");
        } 
        this.id = id;
        this.name=name;
        this.policy= policy;
    
    
    }
    
    /**
     * Recibe un proceso nuevo
     * Siempre entra por la cola de nuevos
     * El paso a listos lo hace ontick() desde el hilo del reloj
     * @param process  proceso que llega al computador
     */
    public void admit(PCB process){
        process.setState(ProcessState.NEW);
        newQueue.enqueue(process);}
    
    /**
     * Cambia la politica en caliente 
     * Solo afecta a los que entren o salgan desde ahora, no reordena los procesos q ya estan en cola
     * @param newPolicy politica nueva
     */
    public void setPolicy(SchedulingPolicy newPolicy){
        if(newPolicy==null){throw new IllegalArgumentException("La politica no puede ser nula");
        }
        this.policy=newPolicy;
        //ontick reordena la cola en el siguiente ciclo
        this.policyChanged =true;
    }
    
    /**
     * Un ciclo de este computador
     * Trabaja en tres pasos
     * 1.Admite procesos nuevos
     * 2.Ejecuta una instruccion en la CPU
     * 3.Elige el siguiente si la CPU quedo libre
     * @param cycle nro de ciclo global actual
     */
    @Override
    public void onTick(long cycle) {
        
        /**
         * Si cambiaron la politica, reordena la cola de listos con la nueva
         */
        if(policyChanged){
            policyChanged=false;
            reorderReadyQueue(policy);
        }
        SchedulingPolicy currentPolicy = policy;   // la leo una sola vez por ciclo

        // Admision
        PCB p;
        while ((p = newQueue.dequeue()) != null) {
            p.setState(ProcessState.READY);
            currentPolicy.addToReady(readyQueue, p);
        }

        // Si hay un proceso en la CPU, ejecuta una instruccion
        if (running != null) {
            running.executeInstruction();
            cyclesInCpu++;
            if (running.isFinished()) {
                running.setState(ProcessState.TERMINATED);
                finishedQueue.enqueue(running);
                running = null;
            } else if (currentPolicy.shouldPreempt(running, readyQueue, cyclesInCpu)) {
                running.setState(ProcessState.READY);
                currentPolicy.addToReady(readyQueue, running);
                running = null;
            }
        }

        // Si la CPU quedo libre, la politica elige al siguiente
        if (running == null) {
            PCB next = currentPolicy.selectNext(readyQueue);
            if (next != null) {
                running = next;
                running.setState(ProcessState.RUNNING);
                running.markFirstExecution(cycle);
                cyclesInCpu = 0;
            }
        }
    }
    
    public int getId() { return id; }
    public String getName() { return name; }
    public SchedulingPolicy getPolicy() { return policy; }
    public PCB getRunning() { return running; }
    public ExecutionMode getMode() { return mode; }

    // Copias de las colas para la GUI 
    public CustomQueue<PCB> getNewSnapshot() { return newQueue.copy(); }
    public CustomQueue<PCB> getReadySnapshot() { return readyQueue.copy(); }
    public CustomQueue<PCB> getBlockedSnapshot() { return blockedQueue.copy(); }
    public CustomQueue<PCB> getFinishedSnapshot() { return finishedQueue.copy(); }

    
    /**
     * Sca todos los procesos de la cola de listos y los vuelve a insertar con la politica dada
     * @param policy 
     */
    private void reorderReadyQueue(SchedulingPolicy policy) {
        CustomQueue<PCB> temp = new CustomQueue<>();
        PCB p;
        while ((p = readyQueue.dequeue()) != null) {
            temp.enqueue(p);
        }
        while ((p = temp.dequeue()) != null) {
            policy.addToReady(readyQueue, p);
        }
    }
    
    
    
}
