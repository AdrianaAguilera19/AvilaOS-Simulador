/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package avilaos.persistence;

import avilaos.enums.ProcessType;
import avilaos.enums.SchedulingAlgorithm;
import avilaos.model.hardware.Cluster;

/**
 *Datos de una configuracion inicial tal como se guardan en el archivo 
 * solo contiene datos simples para que la interfaz pueda llenarlo, el JSONManager lo guarda y ConfigLoader lo convierte
 * @author Antonella Ruiz
 */
public class ConfigDTO {
    /** 
     * Duracion de un ciclo de ejecucion en milisegundos. 
     */
    public int cycleDurationMs;
    
    /** 
     * Latencia de la red en ciclos
     */
    public int networkLatency;
    /** 
     * Computadores del cluster
     */
    public ComputerConfig[] computers;
    
    /** 
     * Carga inicial de procesos
     */
    public ProcessConfig[] processes;
    
    /** 
     * Buffers iniciales 
     */
    public BufferConfig[] buffers;

    /** 
     * Datos de un computador
     */
    public static class ComputerConfig {
        public String name;
        public int ramSize;
        public SchedulingAlgorithm policy;
        public int quantum;
    }

    /**
     * Datos de un proceso inicial. Los campos de cada tipo se llenan solo si le corresponde
     */
    public static class ProcessConfig {
        public String name;
        public ProcessType type;
        public int priority;
        public int memorySize;
        public int deadline;
        public int computerId;
        public int cycles;
        public int ioInterval;
        public int ioDuration;
        public int bufferId;
        public int cyclesPerItem;
        public int itemsRequired;
    }

    /** 
     * Datos de un buffer inicial
     */
    public static class BufferConfig {
        public int id;
        public int capacity;
        public int hostComputerId;
    }

    /**
     * Revisa que la configuracion sea coherente. Se llama antes de guardar y despues de cargar,
     * asi un archivo malo se rechaza con un mensaje claro y no llega a la simulacion.
     * @throws ConfigException si algun dato falta o no tiene sentido
     */
    public void validate() throws ConfigException {
        if (cycleDurationMs <= 0) {
            throw new ConfigException("La duracion del ciclo debe ser mayor que 0");
        }
        if (networkLatency < 0) {
            throw new ConfigException("La latencia de red no puede ser negativa");
        }
        if (computers == null || computers.length < Cluster.MIN_COMPUTERS) {
            throw new ConfigException("Se necesitan al menos " + Cluster.MIN_COMPUTERS + " computadores");
        }
        // Cada computador
        for (int i = 0; i < computers.length; i++) {
            validateComputer(computers[i], i + 1);
        }
        // Los buffers se revisan antes que los procesos porque los productores y consumidores los usan
        if (buffers != null) {
            for (int i = 0; i < buffers.length; i++) {
                validateBuffer(buffers[i], i + 1);
            }
        }
        if (processes != null) {
            for (int i = 0; i < processes.length; i++) {
                validateProcess(processes[i], i + 1);
            }
        }
    }

    /**
     * @param c computador a revisar
     * @param number posicion (desde 1), solo para el mensaje de error
     */
    private void validateComputer(ComputerConfig c, int number) throws ConfigException {
        if (c == null) {
            throw new ConfigException("El computador " + number + " esta vacio");
        }
        if (c.name == null || c.name.isBlank()) {
            throw new ConfigException("El computador " + number + " necesita un nombre");
        }
        if (c.ramSize <= 0) {
            throw new ConfigException("El computador " + number + " debe tener una RAM mayor que 0");
        }
        if (c.policy == null) {
            throw new ConfigException("El computador " + number + " tiene una politica invalida o vacia");
        }
        if (c.quantum <= 0) {
            throw new ConfigException("El computador " + number + " debe tener un quantum mayor que 0");
        }
    }

    /**
     * @param b buffer a revisar
     * @param number posicion (desde 1), solo para el mensaje de error
     */
    private void validateBuffer(BufferConfig b, int number) throws ConfigException {
        if (b == null) {
            throw new ConfigException("El buffer " + number + " esta vacio");
        }
        if (b.id < 0) {
            throw new ConfigException("El buffer " + number + " tiene un id negativo");
        }
        if (b.capacity <= 0) {
            throw new ConfigException("El buffer " + b.id + " debe tener una capacidad mayor que 0");
        }
        if (b.hostComputerId < 1 || b.hostComputerId > computers.length) {
            throw new ConfigException("El buffer " + b.id + " tiene un computador anfitrion que no existe");
        }
        // No puede haber dos buffers con el mismo id
        for (int j = 0; j < number - 1; j++) {
            if (buffers[j] != null && buffers[j].id == b.id) {
                throw new ConfigException("El id de buffer " + b.id + " esta repetido");
            }
        }
    }

    /**
     * @param p proceso a revisar
     * @param number posicion (desde 1), solo para el mensaje de error
     */
    private void validateProcess(ProcessConfig p, int number) throws ConfigException {
        if (p == null) {
            throw new ConfigException("El proceso " + number + " esta vacio");
        }
        if (p.name == null || p.name.isBlank()) {
            throw new ConfigException("El proceso " + number + " necesita un nombre");
        }
        String who = "El proceso " + p.name;
        if (p.type == null) {
            throw new ConfigException(who + " tiene un tipo invalido o vacio");
        }
        if (p.computerId < 1 || p.computerId > computers.length) {
            throw new ConfigException(who + " esta asignado a un computador que no existe");
        }
        if (p.memorySize <= 0) {
            throw new ConfigException(who + " debe ocupar una memoria mayor que 0");
        }
        // Un proceso mas grande que la RAM de su computador nunca podria entrar
        if (p.memorySize > computers[p.computerId - 1].ramSize) {
            throw new ConfigException(who + " ocupa mas memoria que la RAM de su computador");
        }
        if (p.deadline <= 0) {
            throw new ConfigException(who + " debe tener un deadline mayor que 0");
        }
        // Datos propios de cada tipo
        if (p.type == ProcessType.CPU_BOUND) {
            if (p.cycles <= 0) {
                throw new ConfigException(who + " debe necesitar mas de 0 ciclos");
            }
        } else if (p.type == ProcessType.IO_BOUND) {
            if (p.cycles <= 0 || p.ioInterval <= 0 || p.ioDuration <= 0) {
                throw new ConfigException(who + " necesita ciclos, intervalo y duracion de E/S mayores que 0");
            }
        } else {
            // PRODUCER o CONSUMER
            if (p.cyclesPerItem <= 0 || p.itemsRequired <= 0) {
                throw new ConfigException(who + " necesita ciclos por elemento y elementos mayores que 0");
            }
            if (!bufferExists(p.bufferId)) {
                throw new ConfigException(who + " usa un buffer que no esta en la configuracion");
            }
        }
    }

    /**
     * @param id id de buffer a buscar
     * @return true si hay un buffer con ese id en la configuracion
     */
    private boolean bufferExists(int id) {
        if (buffers == null) {
            return false;
        }
        for (int i = 0; i < buffers.length; i++) {
            if (buffers[i] != null && buffers[i].id == id) {
                return true;
            }
        }
        return false;
    }
}

