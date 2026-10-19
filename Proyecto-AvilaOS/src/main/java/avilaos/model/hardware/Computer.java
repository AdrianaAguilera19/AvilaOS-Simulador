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
import avilaos.datastructures.CustomLinkedList;
import avilaos.interfaces.ProcessRunner;
import avilaos.model.process.StepOutcome;

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

    
    
     // Tamano de RAM por defecto cuando no se indica uno (pruebas)
    public static final int DEFAULT_RAM_SIZE = 1024;
    
    // La CPU de este computador
    private final CPU cpu = new CPU();
    private final RAM ram;

    // Colas propias de este computador
    private final CustomQueue<PCB> newQueue = new CustomQueue<>();      // procesos que acaban de llegar
    private final CustomQueue<PCB> readyQueue = new CustomQueue<>();    // listos para usar la CPU
    private final CustomQueue<PCB> blockedQueue = new CustomQueue<>();  // esperando E/S o un semaforo
    private final CustomQueue<PCB> finishedQueue = new CustomQueue<>(); // ya terminaron
    
    
    // S04/P10: ejecutores de los procesos que usan semáforos (productores y consumidores)
    private final CustomLinkedList<ProcessRunner> runners = new CustomLinkedList<>();

    /**
     * Crea el computador con la RAM por defecto (PRUEBAS)
     * @param id
     * @param name
     * @param policy 
     */
    public Computer(int id, String name, SchedulingPolicy policy) {
        this(id,name,policy,DEFAULT_RAM_SIZE);
    }
    
    /**
     * Crea el computador con sus colas vacias y la CPU libre.
     * @param id identificador del computador
     * @param name nombre que se muestra en la interfaz (no puede estar vacio)
     * @param policy politica de planificacion inicial (no puede ser null)
     */
    public Computer(int id, String name, SchedulingPolicy policy, int ramSize) {
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
        this.ram = new RAM(ramSize);
    }

    /**
     * Recibe un proceso nuevo. Siempre entra por la cola de nuevos; el paso a
     * listos lo hace onTick() desde el hilo del reloj, no quien llama a este metodo.
     * @param process proceso que llega al computador
     */
    public void admit(PCB process) {
        if (process.getMemorySize() > ram.getTotalSize()) {
                    if (process == null) {
            throw new IllegalArgumentException("El proceso no puede ser nulo");
        }
        // P08: un proceso solo puede entrar a la computadora a la que fue asignado (no hay migracion)
        if (process.getComputerId() != id) {
            throw new IllegalArgumentException("El proceso " + process.getName()
                    + " pertenece a la computadora " + process.getComputerId()
                    + " y no puede ser admitido en la " + id + " (no hay migracion)");
        }
            throw new IllegalArgumentException("El proceso necesita mas memoria que toda la RAM del computador");
        }
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
        admitFromNewQueue(currentPolicy);

        // 2. Si hay un proceso en la CPU, ejecuta una instruccion
        if (!cpu.isFree()) {
            PCB running = cpu.getRunning();
            ProcessRunner runner = findRunner(running);   // S04/P10: null si no usa semáforos
            boolean blocked = false;
            if (runner == null) {
                cpu.executeUserInstruction();
            } else {
                blocked = executeRunnerStep(runner);
            }
            if (blocked) {
                // el proceso ya salió de la CPU y está en la cola de bloqueados
            } else if (runner != null ? runner.isFinished() : running.isFinished()) {
                // Termino todas sus instrucciones: va a la cola de terminados
                cpu.unload();
                running.setState(ProcessState.TERMINATED);
                finishedQueue.enqueue(running);
                ram.release(running.getMemorySize());// libera su memoria
                admitFromNewQueue(currentPolicy); // y revisa si ya cabe alguno de los nuevo
                
            } else if (currentPolicy.shouldPreempt(running, readyQueue, cpu.getCyclesInCpu())) {
                // La politica pide sacarlo de la CPU: vuelve a la cola de listos
                cpu.unload();
                running.setState(ProcessState.READY);
                currentPolicy.addToReady(readyQueue, running);
            }
        }
        //3. deadlines: a cada proceso vivo se le resta 1 ciclo
        updateDeadlines(currentPolicy);
        // 4. Si la CPU quedo libre, la politica elige al siguiente
        if (cpu.isFree()) {
            PCB next = currentPolicy.selectNext(readyQueue);
            if (next != null) {
                cpu.load(next, cycle);   // la CPU marca el estado y el primer ciclo
            }
        }
    }
    
    /**
     * Resta 1 ciclo de deadline a todos los procesos que siguen vivos y termina a los que lleguen a 0. Los que ya
     * terminaron normalmente no se tocan. Si alguno libero memoria, se revisa la cola de nuevos.
     */
    private void updateDeadlines(SchedulingPolicy currentPolicy) {
        boolean freedMemory = false;

        // El proceso que esta en la CPU
        PCB running = cpu.getRunning();
        if (running != null) {
            running.decrementDeadline();
            if (running.isDeadlineExpired()) {
                cpu.unload();
                terminateByDeadline(running, true);
                freedMemory = true;
            }
        }

        // Las colas. En nuevos todavia no tienen RAM reservada, en las otras si.
        if (expireInQueue(readyQueue, true)) { freedMemory = true; }
        if (expireInQueue(blockedQueue, true)) { freedMemory = true; }
        expireInQueue(newQueue, false);

        if (freedMemory) {
            admitFromNewQueue(currentPolicy);   // se libero memoria:
        }
    }

    /**
     * Resta 1 al deadline de cada proceso de la cola y termina a los vencidos
     * Recorre una copia para poder sacar procesos de la cola real mientras recorre
     * @param queue cola a revisar
     * @param hasRam true si los procesos de esa cola ya tienen RAM reservada
     * @return true si libero memoria
     */
    private boolean expireInQueue(CustomQueue<PCB> queue, boolean hasRam) {
        boolean released = false;
        CustomQueue<PCB> snapshot = queue.copy();
        for (int i = 0; i < snapshot.size(); i++) {
            PCB p = snapshot.get(i);
            p.decrementDeadline();
            if (p.isDeadlineExpired()) {
                queue.remove(p);
                terminateByDeadline(p, hasRam);
                released = released || hasRam;
            }
        }
        return released;
    }

    /**
     * Termina un proceso porque se le vencio el deadline
     * lo marca, lo pasa a terminados y libera su memoria si la tenia
     */
    private void terminateByDeadline(PCB p, boolean hasRam) {
        p.setState(ProcessState.TERMINATED);
        p.markDeadlineMissed();
        finishedQueue.enqueue(p);
        if (hasRam) {
            ram.release(p.getMemorySize());
        }
    }
    
    /**
     * Pasa de nuevos a listos los procesos que quepan en la RAM, en orden de llegada.
     * Si el primero no cabe, se detiene y espera: asi un proceso grande no se queda
     * esperando para siempre porque otros mas chicos le pasan por delante.
     */
    private void admitFromNewQueue(SchedulingPolicy currentPolicy) {
        PCB p;
        while ((p = newQueue.peek()) != null && ram.allocate(p.getMemorySize())) {
            newQueue.dequeue();
            p.setState(ProcessState.READY);
            currentPolicy.addToReady(readyQueue, p);
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

        /**
     * Registra el ejecutor de un proceso que usa semáforos. Debe hacerse antes de admitir
     * el proceso. El proceso tiene que pertenecer a este computador (no hay migración).
     * @param runner ejecutor del productor o consumidor
     */
    public void registerRunner(ProcessRunner runner) {
        if (runner == null) {
            throw new IllegalArgumentException("El ejecutor no puede ser nulo");
        }
        if (runner.getPcb().getComputerId() != id) {
            throw new IllegalArgumentException("El proceso " + runner.getPcb().getName()
                    + " pertenece a la computadora " + runner.getPcb().getComputerId()
                    + " y no puede registrarse en la " + id);
        }
        runners.addLast(runner);
    }

    /** @return el ejecutor del proceso, o null si es un proceso normal (sin semáforos) */
    private ProcessRunner findRunner(PCB pcb) {
        CustomLinkedList<ProcessRunner> snapshot = runners.copy();
        for (int i = 0; i < snapshot.size(); i++) {
            if (snapshot.get(i).getPcb() == pcb) {
                return snapshot.get(i);
            }
        }
        return null;
    }

    /**
     * Ejecuta un ciclo de un proceso con semáforos y reacciona a lo que devuelve.
     * @return true si el proceso se bloqueó (ya salió de la CPU y está en la cola de bloqueados)
     */
    private boolean executeRunnerStep(ProcessRunner runner) {
        StepOutcome outcome = runner.step();
        switch (outcome.getKind()) {
            case USER_INSTRUCTION:
                cpu.executeUserInstruction();
                return false;
            case SYSTEM_INSTRUCTION:
                cpu.executeSystemInstruction();
                // S05: si outcome.getWoken() != null, ese proceso debe pasar a listos
                return false;
            case BLOCKED:
                // el semWait que bloquea también gasta un ciclo en modo SO
                cpu.executeSystemInstruction();
                blockedQueue.enqueue(cpu.unload());   // P10: libera la CPU y pasa a bloqueados
                return true;
            default:
                // FINISHED: no gasta ciclo; onTick lo termina porque runner.isFinished() es true
                return false;
        }
    }
    
    public int getId() { return id; }
    public String getName() { return name; }
    public SchedulingPolicy getPolicy() { return policy; }
    public SchedulingAlgorithm getAlgorithm() { return policy.getAlgorithm(); }
    public int getQuantum() { return quantum; }
    public CPU getCpu() { return cpu; }
    public RAM getRam() { return ram; }
    public PCB getRunning() { return cpu.getRunning(); }
    public ExecutionMode getMode() { return cpu.getMode(); }

    // Copias de las colas para la GUI: asi la interfaz mira sin tocar las colas reales
    public CustomQueue<PCB> getNewSnapshot() { return newQueue.copy(); }
    public CustomQueue<PCB> getReadySnapshot() { return readyQueue.copy(); }
    public CustomQueue<PCB> getBlockedSnapshot() { return blockedQueue.copy(); }
    public CustomQueue<PCB> getFinishedSnapshot() { return finishedQueue.copy(); }
}