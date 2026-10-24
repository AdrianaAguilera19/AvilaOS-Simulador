/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package avilaos.model.process;
import avilaos.enums.BlockReason;
import avilaos.enums.ProcessType;
import avilaos.enums.ProcessState;


/**
 *Representa el bloque de control del proceso
 * @author Antonella Ruiz
 */
public class PCB {
    
    public static final int NO_BUFFER = -1;
    public static final long NOT_STARTED = -1;
    public static final long NOT_BLOCKED = -1;
    
    
    // Identificacion
    private final int id;
    private final String name;
    private final ProcessType type;
    private final int priority;
    private final int memorySize;
    private final int totalInstructions;
    private final int computerId;
    private final long arrivalCycle;
    
    // Planificación 
    private ProcessState state;
    private int deadline;
    private int remainingTime;
    private long firstExecutionCycle;
    private boolean deadlineMissed = false;
    
    // Contexto de ejecución (se guarda al cambiar de proceso)
    private int programCounter;
    private int memoryAddressRegister;
    
    //parametros segun tipo de proceso 
    //IO BOUND es para contar cada cuantas instrucciones pide e/s y cuantos ciclos queda bloqueado
    private int ioInterval;
    private int ioDuration;
    
    //productos/consumidor: cuantos ciclos produce o consume un elemento
    //y cuantos elementos necesita para terminar
    private int cyclesPerItem;
    private int itemsRequired;
    
    
    // Bloqueo 
    private BlockReason blockReason;
    private int associatedBufferId;
    private int blockedCycles;
          private long blockedSinceCycle = NOT_BLOCKED;   // S10: ciclo en que se bloqueó en un semáforo
    
    public PCB(String name, ProcessType type, int priority, int memorySize,int totalInstructions, int deadline, int computerId, long arrivalCycle){
        
        if(name ==null || name.isBlank()){
            throw new IllegalArgumentException("El nombre del proceso no puede quedar vacio");
        
        }
        if(type ==null){
            throw new IllegalArgumentException("El tipo de proceso no puede quedar vacio");
        }
        if(memorySize <= 0){
            throw new IllegalArgumentException("La memoria debe ser mayor a 0");
        }
        if(totalInstructions <= 0){
            throw new IllegalArgumentException("Las instrucciones deben ser mayores a 0");
        }
        if(deadline <=0){
            throw new IllegalArgumentException("El deadline debe ser mayor que 0");}
        
        this.id = IdGenerator.nextId();
        this.name = name;
        this.type = type;
        this.priority = priority;
        this.memorySize = memorySize;
        this.totalInstructions = totalInstructions;
        this.deadline = deadline;
        this.computerId = computerId;
        this.arrivalCycle = arrivalCycle;

        this.state = ProcessState.NEW;
        this.remainingTime = totalInstructions;
        this.firstExecutionCycle = NOT_STARTED;
        this.programCounter = 0;
        this.memoryAddressRegister = 0;
        this.blockReason = BlockReason.NONE;
        this.associatedBufferId = NO_BUFFER;
        this.blockedCycles = 0;
    
    }
    
    // Operaciones que usan el Kernel y la CPU
    
    /**
     *Ejecucion de una instruccion
     */
    
    public void executeInstruction(){
        programCounter++;
        memoryAddressRegister++;
        remainingTime--;
    }
    
    /**
     * Resta un ciclo al deadline (se llama una unica vez por ciclo)
     */
    
    public void decrementDeadline(){
        deadline--;}   
    
    /**
     * Suma 1 al contador de ciclos que el proceso ha pasado bloqueado
     */
    
    public void incrementBlockedCycles(){
        blockedCycles++;}

    /**
     * Registra el ciclo de la primera vez usado en la CPU
     */
    public void markFirstExecution(long cycle){
        if(firstExecutionCycle == NOT_STARTED){
            firstExecutionCycle= cycle;}}
    
    /**
     * retorna verdadero si ya se ejecutaron todas sus instrucciones
     */
    public boolean isFinished(){
        return remainingTime <=0;}
    
    /**
     * retorna verdadero si el deadline llego a 0 (el proceso debe terminarse)
     */
    public boolean isDeadlineExpired(){
       return deadline <=0;
    }
    
    //Parametros por tipo
    
    /**
     * Guarda los parametros de un proceso IO_BOUND
     * @param ioInterval
     * @param ioDuration 
     */
    public void setIoParameters(int ioInterval, int ioDuration){
        if(type != ProcessType.IO_BOUND){throw new IllegalStateException("Solo un proceso IO_BOUND tiene parametros de E/S");}
        
        if(ioInterval <=0 || ioDuration <=0){
            throw new IllegalStateException("El intervalo y la duracion de la E/S deben ser mayores que 0");
        }
        this.ioInterval =ioInterval;
        this.ioDuration=ioDuration;
    
    }
    /**
     * Guarda los parametros de un prodctor o consumidor
     * @param bufferId buffer con el que trabaja
     * @param cyclesPerItem cada cuantos ciclo produce/consume un elemento
     * @param itemsRequired elementos que necesita para terminar
     */
    public void setBufferParameters(int bufferId, int cyclesPerItem, int itemsRequired) {
        if (type != ProcessType.PRODUCER && type != ProcessType.CONSUMER) {
            throw new IllegalStateException("Solo un productor o consumidor trabaja con un buffer");
        }
        if (bufferId < 0 || cyclesPerItem <= 0 || itemsRequired <= 0) {
            throw new IllegalArgumentException("El buffer debe ser valido y los ciclos y elementos mayores que 0");
        }
        this.associatedBufferId = bufferId;
        this.cyclesPerItem = cyclesPerItem;
        this.itemsRequired = itemsRequired;
    }
    
    public void markDeadlineMissed(){this.deadlineMissed=true;}
    
    //Getters
    public int getId() { return id; }
    public String getName() { return name; }
    public ProcessType getType() { return type; }
    public int getPriority() { return priority; }
    public int getMemorySize() { return memorySize; }
    public int getTotalInstructions() { return totalInstructions; }
    public int getComputerId() { return computerId; }
    public long getArrivalCycle() { return arrivalCycle; }
    public ProcessState getState() { return state; }
    public int getDeadline() { return deadline; }
    public int getRemainingTime() { return remainingTime; }
    public long getFirstExecutionCycle() { return firstExecutionCycle; }
    public int getProgramCounter() { return programCounter; }
    public int getMemoryAddressRegister() { return memoryAddressRegister; }
    public BlockReason getBlockReason() { return blockReason; }
          public long getBlockedSinceCycle() { return blockedSinceCycle; }
    public int getAssociatedBufferId() { return associatedBufferId; }
    public int getBlockedCycles() { return blockedCycles; }
    public int getIoInterval() { return ioInterval; }
    public int getIoDuration() { return ioDuration; }
    public int getCyclesPerItem() { return cyclesPerItem; }
    public int getItemsRequired() { return itemsRequired; }
    public boolean isDeadlineMissed() { return deadlineMissed; }
    
    
    // Setters
    public void setState(ProcessState state) { this.state = state; }
    public void setBlockReason(BlockReason blockReason) { this.blockReason = blockReason; }
          public void setBlockedSinceCycle(long cycle) { this.blockedSinceCycle = cycle; }
    public void setAssociatedBufferId(int associatedBufferId) { this.associatedBufferId = associatedBufferId; }

    @Override
    public String toString(){ return "p"+id+"name"+"["+state+"]";}
}
