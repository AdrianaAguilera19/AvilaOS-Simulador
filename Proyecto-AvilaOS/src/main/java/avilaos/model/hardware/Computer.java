/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package avilaos.model.hardware;

import avilaos.datastructures.CustomQueue;
import avilaos.enums.ExecutionMode;
import avilaos.enums.SchedulingAlgorithm;
import avilaos.enums.ProcessState;
import avilaos.interfaces.Clockable;
import avilaos.interfaces.SchedulingPolicy;
import avilaos.model.process.PCB;
import avilaos.scheduler.PolicyFactory;
import avilaos.scheduler.RoundRobinPolicy;
import java.util.Comparator;

/**
 * Un computador del cluster. Cada instancia tiene sus propias colas y su
 * propia politica de planificacion, y avanza un paso cada vez que el reloj
 * global le avisa (por eso implementa Clockable).
 * @author Antonella Ruiz
 */
public class Computer implements Clockable {

    // Identificacion del computador (no cambia)
    private final int id;
    private final String name;

    public static final int DEFAULT_QUANTUM = 4;

    // Politica elegida. Es volatile porque la GUI la cambia mientras el reloj corre
    private volatile SchedulingPolicy policy;
    // Ultima politica con la que se ordeno la cola de listos. Solo la toca el hilo del reloj:
    // si policy != appliedPolicy, hay que reordenar (asi no hace falta una bandera aparte)
    private SchedulingPolicy appliedPolicy;
    // Quantum de Round Robin de este computador (se recuerda aunque la politica activa sea otra)
    private volatile int quantum = DEFAULT_QUANTUM;

    // La CPU de este computador
    private final CPU cpu = new CPU();

    // Colas propias de este computador
    private final CustomQueue<PCB> newQueue = new CustomQueue<>();      // procesos que acaban de llegar
    private final CustomQueue<PCB> readyQueue = new CustomQueue<>();    // listos para usar la CPU
    private final CustomQueue<PCB> blockedQueue = new CustomQueue<>();  // esperando E/S o un semaforo
    private final CustomQueue<PCB> finishedQueue = new CustomQueue<>(); // ya terminaron

    /**
     * Crea el computador con sus colas vacias y la CPU libre.
     * @param id identificador del computador
     * @param name nombre que se muestra en la interfaz (no puede estar vacio)
     * @param policy politica de planificacion inicial (no puede ser null)
     */
    public Computer(int id, String name, SchedulingPolicy policy) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("El nombre del computador no puede estar vacio");
        }
        if (policy == null) {
            throw new IllegalArgumentException("El computador necesita una politica de planificacion");
        }
        this.id = id;
        this.name = name;
        this.policy = policy;
        this.appliedPolicy = policy;
        if (policy instanceof RoundRobinPolicy) {
            this.quantum = ((RoundRobinPolicy) policy).getQuantum();
        }
    }

    /**
     * Recibe un proceso nuevo. Siempre entra por la cola de nuevos; el paso a
     * listos lo hace onTick() desde el hilo del reloj, no quien llama a este metodo.
     * @param process proceso que llega al computador
     */
    public void admit(PCB process) {
        process.setState(ProcessState.NEW);
        newQueue.enqueue(process);
    }

    /**
     * Cambia la politica en caliente (sin reiniciar nada). La cola de listos se
     * reordena con la politica nueva al inicio del siguiente ciclo, y el proceso
     * que esta en la CPU la conserva hasta que la politica nueva decida desalojarlo.
     * @param newPolicy politica nueva (no puede ser null)
     */
    public void setPolicy(SchedulingPolicy newPolicy) {
        if (newPolicy == null) {
            throw new IllegalArgumentException("La politica no puede ser nula");
        }
        this.policy = newPolicy;
    }

    /**
     * Version para la GUI: elige la politica por su enum (usa el quantum actual del computador).
     * @param algorithm algoritmo elegido en el selector
     */
    public void setPolicy(SchedulingAlgorithm algorithm) {
        setPolicy(PolicyFactory.create(algorithm, quantum));
    }

    /**
     * Cambia el quantum de Round Robin en caliente. Si la politica activa no es RR
     * el valor se guarda y se usara cuando se elija RR.
     * @param newQuantum nuevo quantum (mayor a 0)
     */
    public void setQuantum(int newQuantum) {
        if (newQuantum <= 0) {
            throw new IllegalArgumentException("El quantum debe ser mayor a 0");
        }
        this.quantum = newQuantum;
        SchedulingPolicy current = policy;
        if (current instanceof RoundRobinPolicy) {
            ((RoundRobinPolicy) current).setQuantum(newQuantum);
        }
    }

    /**
     * Un ciclo de este computador. Hace tres pasos en orden: admitir procesos
     * nuevos, ejecutar una instruccion en la CPU y elegir al siguiente si
     * la CPU quedo libre.
     * @param cycle numero de ciclo global actual
     */
    @Override
    public void onTick(long cycle) {
        // La politica se lee una sola vez por ciclo. Si es distinta a la ultima aplicada,
        // reordeno la cola de listos con ella (solo el hilo del reloj toca la cola)
        SchedulingPolicy currentPolicy = policy;
        if (currentPolicy != appliedPolicy) {
            reorderReadyQueue(currentPolicy);
            appliedPolicy = currentPolicy;
        }
        cpu.startCycle();                          // la CPU cuenta el ciclo

        // 1. Admision: paso los procesos nuevos a listos (A13 lo condicionara a la RAM)
        PCB p;
        while ((p = newQueue.dequeue()) != null) {
            p.setState(ProcessState.READY);
            currentPolicy.addToReady(readyQueue, p);
        }

        // 2. Si hay un proceso en la CPU, ejecuta una instruccion
        if (!cpu.isFree()) {
            cpu.executeUserInstruction();
            PCB running = cpu.getRunning();
            if (running.isFinished()) {
                // Termino todas sus instrucciones: va a la cola de terminados
                cpu.unload();
                running.setState(ProcessState.TERMINATED);
                finishedQueue.enqueue(running);
            } else if (currentPolicy.shouldPreempt(running, readyQueue, cpu.getCyclesInCpu())) {
                // La politica pide sacarlo de la CPU: vuelve a la cola de listos
                cpu.unload();
                running.setState(ProcessState.READY);
                currentPolicy.addToReady(readyQueue, running);
            }
        }

        // 3. Si la CPU quedo libre, la politica elige al siguiente
        if (cpu.isFree()) {
            PCB next = currentPolicy.selectNext(readyQueue);
            if (next != null) {
                cpu.load(next, cycle);   // la CPU marca el estado y el primer ciclo
            }
        }
    }

    /**
     * Saca todos los procesos de la cola de listos y los vuelve a insertar con la politica dada.
     * Primero se pasan por un orden base (llegada, luego ID) para que FCFS/RR recuperen el
     * orden de llegada y EDF/Prioridades desempaten por llegada.
     */
    private void reorderReadyQueue(SchedulingPolicy pol) {
        Comparator<PCB> byArrival = new Comparator<PCB>() {
            @Override
            public int compare(PCB a, PCB b) {
                int c = Long.compare(a.getArrivalCycle(), b.getArrivalCycle());
                return c != 0 ? c : Integer.compare(a.getId(), b.getId());
            }
        };
        CustomQueue<PCB> temp = new CustomQueue<>();
        PCB p;
        while ((p = readyQueue.dequeue()) != null) {
            temp.insertSorted(p, byArrival);
        }
        while ((p = temp.dequeue()) != null) {
            pol.addToReady(readyQueue, p);
        }
    }

    public int getId() { return id; }
    public String getName() { return name; }
    public SchedulingPolicy getPolicy() { return policy; }
    public SchedulingAlgorithm getAlgorithm() { return policy.getAlgorithm(); }
    public int getQuantum() { return quantum; }
    public CPU getCpu() { return cpu; }
    public PCB getRunning() { return cpu.getRunning(); }
    public ExecutionMode getMode() { return cpu.getMode(); }

    // Copias de las colas para la GUI: asi la interfaz mira sin tocar las colas reales
    public CustomQueue<PCB> getNewSnapshot() { return newQueue.copy(); }
    public CustomQueue<PCB> getReadySnapshot() { return readyQueue.copy(); }
    public CustomQueue<PCB> getBlockedSnapshot() { return blockedQueue.copy(); }
    public CustomQueue<PCB> getFinishedSnapshot() { return finishedQueue.copy(); }
}