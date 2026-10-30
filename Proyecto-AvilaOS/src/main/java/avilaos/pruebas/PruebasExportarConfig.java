/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package avilaos.pruebas;

import avilaos.enums.ProcessType;
import avilaos.enums.SchedulingAlgorithm;
import avilaos.model.hardware.Cluster;
import avilaos.persistence.ConfigDTO;
import avilaos.persistence.ConfigException;
import avilaos.persistence.ConfigExporter;
import avilaos.persistence.ConfigLoader;
import avilaos.persistence.JSONManager;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Prueba de guardar la configuracion de un cluster en marcha: se arma un cluster, se deja
 * correr, se exporta con ConfigExporter, se guarda y se carga el JSON, y se comprueba que
 * el cluster nuevo tiene los mismos computadores, buffers y procesos (con su deadline inicial).
 * Se ejecuta como programa (main); termina con "TODAS LAS PRUEBAS PASARON" o con codigo 1.
 */
public class PruebasExportarConfig {

    private static int fallas = 0;
    private static int verificaciones = 0;

    public static void main(String[] args) throws Exception {
        ConfigDTO original = configuracion();
        Cluster cluster = ConfigLoader.build(original);
        for (int i = 0; i < 12; i++) {
            cluster.tick();   // el cluster avanza: los deadlines actuales ya no son los iniciales
        }

        System.out.println("--- exportar un cluster en marcha ---");
        ConfigDTO exportada = ConfigExporter.fromCluster(cluster);
        check(exportada.cycleDurationMs == 250 && exportada.networkLatency == 2, "ciclo y latencia");
        check(exportada.computers.length == 3, "3 computadores");
        check(exportada.computers[0].name.equals("PC1") && exportada.computers[0].ramSize == 300
                && exportada.computers[0].policy == SchedulingAlgorithm.FCFS, "PC1: nombre, RAM y politica");
        check(exportada.computers[1].policy == SchedulingAlgorithm.ROUND_ROBIN && exportada.computers[1].quantum == 3,
                "PC2: Round Robin con quantum 3");
        check(exportada.computers[2].policy == SchedulingAlgorithm.EDF && exportada.computers[2].ramSize == 150,
                "PC3: EDF y RAM 150");
        check(exportada.buffers.length == 1 && exportada.buffers[0].capacity == 4
                && exportada.buffers[0].hostComputerId == 2, "buffer: capacidad 4 en PC2");
        check(exportada.processes.length == 5, "5 procesos (en cualquier estado)");
        check(todosConDeadlineInicial(exportada, original), "cada proceso conserva su deadline inicial");
        check(tipos(exportada), "tipos y datos propios de cada tipo (ciclos, E/S, buffer)");

        System.out.println("--- guardar, cargar y reconstruir ---");
        Path archivo = Files.createTempFile("avilaos-config", ".json");
        try {
            JSONManager.save(exportada, archivo.toString());
            ConfigDTO cargada = JSONManager.load(archivo.toString());
            Cluster nuevo = ConfigLoader.build(cargada);
            check(nuevo.getComputerCount() == 3, "el cluster cargado tiene 3 computadores");
            check(nuevo.getCurrentCycle() == 0, "el cluster cargado empieza en el ciclo 0");
            check(nuevo.getComputer(2).getQuantum() == 3
                    && nuevo.getComputer(2).getAlgorithm() == SchedulingAlgorithm.ROUND_ROBIN, "PC2 cargado igual");
            check(nuevo.getBufferManager().getSnapshot().size() == 1, "el buffer se reconstruyo");
            check(totalProcesos(nuevo) == 5, "los 5 procesos se reconstruyeron");
            ConfigDTO otraVez = ConfigExporter.fromCluster(nuevo);
            check(sonIguales(exportada, otraVez), "exportar el cluster cargado da la misma configuracion");
        } finally {
            Files.deleteIfExists(archivo);
        }

        System.out.println();
        if (fallas == 0) {
            System.out.println("TODAS LAS PRUEBAS PASARON (" + verificaciones + " verificaciones)");
        } else {
            System.out.println("HAY " + fallas + " FALLAS de " + verificaciones + " verificaciones");
            System.exit(1);
        }
    }

    private static ConfigDTO configuracion() {
        ConfigDTO c = new ConfigDTO();
        c.cycleDurationMs = 250;
        c.networkLatency = 2;
        c.computers = new ConfigDTO.ComputerConfig[] {
            computador("PC1", 300, SchedulingAlgorithm.FCFS, 4),
            computador("PC2", 300, SchedulingAlgorithm.ROUND_ROBIN, 3),
            computador("PC3", 150, SchedulingAlgorithm.EDF, 4)};
        ConfigDTO.BufferConfig b = new ConfigDTO.BufferConfig();
        b.id = 1;
        b.capacity = 4;
        b.hostComputerId = 2;
        c.buffers = new ConfigDTO.BufferConfig[] {b};
        c.processes = new ConfigDTO.ProcessConfig[] {
            proceso("cpu", ProcessType.CPU_BOUND, 1, 10, 80, 1, 20, 0, 0, 0, 0, 0),
            proceso("io", ProcessType.IO_BOUND, 2, 10, 90, 1, 12, 3, 2, 0, 0, 0),
            proceso("prod", ProcessType.PRODUCER, 1, 10, 100, 1, 0, 0, 0, 1, 2, 5),
            proceso("cons", ProcessType.CONSUMER, 1, 10, 110, 3, 0, 0, 0, 1, 2, 5),
            proceso("cpu2", ProcessType.CPU_BOUND, 3, 10, 60, 2, 8, 0, 0, 0, 0, 0)};
        return c;
    }

    private static ConfigDTO.ComputerConfig computador(String n, int ram, SchedulingAlgorithm p, int q) {
        ConfigDTO.ComputerConfig c = new ConfigDTO.ComputerConfig();
        c.name = n;
        c.ramSize = ram;
        c.policy = p;
        c.quantum = q;
        return c;
    }

    private static ConfigDTO.ProcessConfig proceso(String n, ProcessType t, int prio, int mem, int deadline,
            int pc, int cycles, int ioInt, int ioDur, int buf, int cpi, int items) {
        ConfigDTO.ProcessConfig p = new ConfigDTO.ProcessConfig();
        p.name = n;
        p.type = t;
        p.priority = prio;
        p.memorySize = mem;
        p.deadline = deadline;
        p.computerId = pc;
        p.cycles = cycles;
        p.ioInterval = ioInt;
        p.ioDuration = ioDur;
        p.bufferId = buf;
        p.cyclesPerItem = cpi;
        p.itemsRequired = items;
        return p;
    }

    private static ConfigDTO.ProcessConfig buscar(ConfigDTO c, String nombre) {
        for (int i = 0; i < c.processes.length; i++) {
            if (c.processes[i].name.equals(nombre)) {
                return c.processes[i];
            }
        }
        return null;
    }

    private static boolean todosConDeadlineInicial(ConfigDTO exportada, ConfigDTO original) {
        for (int i = 0; i < original.processes.length; i++) {
            ConfigDTO.ProcessConfig e = buscar(exportada, original.processes[i].name);
            if (e == null || e.deadline != original.processes[i].deadline) {
                return false;
            }
        }
        return true;
    }

    private static boolean tipos(ConfigDTO e) {
        ConfigDTO.ProcessConfig cpu = buscar(e, "cpu");
        ConfigDTO.ProcessConfig io = buscar(e, "io");
        ConfigDTO.ProcessConfig prod = buscar(e, "prod");
        ConfigDTO.ProcessConfig cons = buscar(e, "cons");
        return cpu != null && cpu.cycles == 20 && cpu.type == ProcessType.CPU_BOUND
                && io != null && io.cycles == 12 && io.ioInterval == 3 && io.ioDuration == 2
                && prod != null && prod.type == ProcessType.PRODUCER && prod.bufferId == 1
                && prod.cyclesPerItem == 2 && prod.itemsRequired == 5
                && cons != null && cons.computerId == 3 && cons.itemsRequired == 5;
    }

    private static int totalProcesos(Cluster c) {
        return ConfigExporter.fromCluster(c).processes.length;
    }

    private static boolean sonIguales(ConfigDTO a, ConfigDTO b) {
        if (a.cycleDurationMs != b.cycleDurationMs || a.networkLatency != b.networkLatency
                || a.computers.length != b.computers.length || a.buffers.length != b.buffers.length
                || a.processes.length != b.processes.length) {
            return false;
        }
        for (int i = 0; i < a.processes.length; i++) {
            ConfigDTO.ProcessConfig x = a.processes[i];
            ConfigDTO.ProcessConfig y = buscar(b, x.name);
            if (y == null || x.type != y.type || x.deadline != y.deadline || x.cycles != y.cycles
                    || x.memorySize != y.memorySize || x.priority != y.priority || x.computerId != y.computerId
                    || x.cyclesPerItem != y.cyclesPerItem || x.itemsRequired != y.itemsRequired
                    || x.ioInterval != y.ioInterval || x.ioDuration != y.ioDuration) {
                return false;
            }
        }
        return true;
    }

    private static void check(boolean ok, String texto) {
        verificaciones++;
        System.out.println((ok ? "  OK    " : "  FALLA ") + texto);
        if (!ok) {
            fallas++;
        }
    }
}