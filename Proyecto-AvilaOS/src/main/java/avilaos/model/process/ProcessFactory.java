/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package avilaos.model.process;

import avilaos.enums.ProcessType;

/**
 *Crea procesos de cada tipo con los parametros que le corresponden
 * De esta forma quien cree un proceso no tiene que conocer los detalles de cada tipo ni armar el PCB a mano
 * @author Antonella Ruiz
 */
public class ProcessFactory {
    
    private ProcessFactory(){}
    
    /**
     * Crea un Proceso CPU bound, que solo consume procesador
     * @param name nombre del proceso
     * @param priority prioridad del proc
     * @param memorySize memoria q ocupa la RAM
     * @param cycles instrucciones que necesita para terminar
     * @param deadline ciclos que tiene para terminar antes de vencerse
     * @param computerId id de la computadora a la que pertence
     * @param arrivalCycle ciclo del reloj en el que llega
     * @return  el PCB del proceso creado
     */
    public static PCB createCpuBound(String name, int priority, int memorySize, int cycles,int deadline, int computerId, long arrivalCycle) {
        return new PCB(name, ProcessType.CPU_BOUND, priority, memorySize, cycles,
                       deadline, computerId, arrivalCycle);
    }
    
    /**
     * Crea un proceso I/O bound, que pide e/s cada cierto numero de instrucciones
     * @param name
     * @param priority
     * @param memorySize
     * @param cycles
     * @param ioInterval
     * @param ioDuration
     * @param deadline
     * @param computerId
     * @param arrivalCycle
     * @return 
     */
    public static PCB createIoBound(String name, int priority, int memorySize, int cycles,int ioInterval, int ioDuration,int deadline, int computerId, long arrivalCycle) {
        
        PCB p = new PCB(name, ProcessType.IO_BOUND, priority, memorySize, cycles,deadline, computerId, arrivalCycle);
        p.setIoParameters(ioInterval, ioDuration);
        return p;
    }
    
    
    /**
     * Crea un productor> produce un elemento cada cyclesPerItem ciclos
     * @param name nombre del proceso
     * @param priority prioridad del proceso
     * @param memorySize memoria que ocupa en la RAM
     * @param bufferId buffer en el que produce
     * @param cyclesPerItem cada cuantos ciclos produce un elemento
     * @param itemsRequired elementos que debe producir para terminar
     * @param deadline ciclos que tiene para terminar antes de vencerse
     * @param computerId id de la computadora a la que pertenece
     * @param arrivalCycle ciclo del reloj en el que llega
     * @return el PCB del productor creado
     */
    public static PCB createProducer(String name, int priority, int memorySize, int bufferId,int cyclesPerItem, int itemsRequired,int deadline, int computerId, long arrivalCycle){
        
        return createBufferProcess(ProcessType.PRODUCER, name, priority, memorySize, bufferId,cyclesPerItem, itemsRequired, deadline, computerId, arrivalCycle);
        }
    
    /**
     * Crea un consumidor> consume un elemento cada cyclesPerItem ciclos
      * @param name nombre del proceso
     * @param priority prioridad del proceso
     * @param memorySize memoria que ocupa en la RAM
     * @param bufferId buffer del que consume
     * @param cyclesPerItem cada cuantos ciclos consume un elemento
     * @param itemsRequired elementos que debe consumir para terminar
     * @param deadline ciclos que tiene para terminar antes de vencerse
     * @param computerId id de la computadora a la que pertenece
     * @param arrivalCycle ciclo del reloj en el que llega
     * @return el PCB del consumidor creado
     */
    public static PCB createConsumer(String name, int priority, int memorySize, int bufferId,int cyclesPerItem, int itemsRequired,int deadline, int computerId, long arrivalCycle) {
        return createBufferProcess(ProcessType.CONSUMER, name, priority, memorySize, bufferId, cyclesPerItem, itemsRequired, deadline, computerId, arrivalCycle);
    }
    
    
    /**
     * Arma un productor o consumidor
     * Cada elemento cuesta cyclesPerItem instrucciones de usuario
     * El total de instrucciones es cyclesPerItem * itemsRequired
     * @param type PRODUCER o CONSUMER
     * @param name 
     * @param priority 
     * @param memorySize
     * @param bufferId
     * @param cyclesPerItem
     * @param itemsRequired
     * @param deadline
     * @param computerId
     * @param arrivalCycle
     * @return  el PCB creado
     */
    private static PCB createBufferProcess(ProcessType type, String name, int priority, int memorySize,int bufferId, int cyclesPerItem, int itemsRequired,int deadline, int computerId, long arrivalCycle) {
        if (cyclesPerItem <= 0 || itemsRequired <= 0) {
            throw new IllegalArgumentException("Los ciclos por elemento y los elementos requeridos deben ser mayores que 0");
        }
        PCB p = new PCB(name, type, priority, memorySize, Math.multiplyExact(cyclesPerItem, itemsRequired),
                        deadline, computerId, arrivalCycle);
        p.setBufferParameters(bufferId, cyclesPerItem, itemsRequired);
        return p;
    }
    
}
