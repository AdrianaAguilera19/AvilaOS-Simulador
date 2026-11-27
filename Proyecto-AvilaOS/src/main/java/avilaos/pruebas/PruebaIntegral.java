/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package avilaos.pruebas;

import avilaos.enums.ProcessState;
import avilaos.enums.ProcessType;
import avilaos.enums.SchedulingAlgorithm;
import avilaos.gui.PanelCluster;
import avilaos.metrics.Metrics;
import avilaos.metrics.MetricsCalculator;
import avilaos.model.hardware.Cluster;
import avilaos.model.hardware.Computer;
import avilaos.model.process.PCB;
import avilaos.persistence.ConfigDTO;
import avilaos.persistence.ConfigExporter;
import avilaos.persistence.ConfigLoader;
import avilaos.persistence.JSONManager;
import java.awt.GraphicsEnvironment;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * I13: prueba integral con el computador unico. Un mismo escenario junta CPU, RAM con cola de nuevos,
 * las politicas FCFS, Round Robin y EDF, dos cambios de politica en caliente, un deadline que
 * vence, las metricas, la configuracion guardada en JSON y la interfaz grafica.
 * Se ejecuta como programa (main), con ticks manuales (siempre da el mismo resultado);
 * termina con "TODAS LAS PRUEBAS PASARON" o con codigo de salida 1.
 */
public class PruebaIntegral {

    private static final int MAX_CICLOS = 2000;
    private static int fallas = 0;
    private static int verificaciones = 0;

    public static void main(String[] args) throws Exception {
        ConfigDTO config = escenario();

        System.out.println("--- armado desde la configuracion ---");
        Cluster cluster = ConfigLoader.build(config);
        check(cluster.getComputerCount() == 1, "el cluster tiene el computador unico");
        check(cluster.getComputer(1).getAlgorithm() == SchedulingAlgorithm.FCFS, "el computador arranca con FCFS");

        System.out.println("--- ejecucion ---");
        cluster.tick();
        check(cluster.getComputer(1).getNewSnapshot().size() >= 1,
                "RAM de PC1 justa: un proceso espera en la cola de nuevos");
        cluster.setPolicy(1, SchedulingAlgorithm.ROUND_ROBIN);
        check(cluster.getComputer(1).getAlgorithm() == SchedulingAlgorithm.ROUND_ROBIN,
                "cambio de politica en caliente: de FCFS a Round Robin");
        int ciclos = correrConCambios(cluster);
        check(cluster.getComputer(1).getAlgorithm() == SchedulingAlgorithm.EDF,
                "segundo cambio en caliente: de Round Robin a EDF");
        check(ciclos > 1, "todos los procesos terminan (" + ciclos + " ciclos)");

        System.out.println("--- resultados ---");
        PCB[] todos = procesos(cluster);
        check(todos.length == 7, "los 7 procesos quedaron en la cola de terminados");
        check(soloVenceEdf1(todos), "solo edf1 (deadline 4) termino por vencimiento");
        check(cluster.getComputer(1).getRam().getUsedSize() == 0, "al terminar toda la RAM queda libre");

        System.out.println("--- metricas ---");
        Metrics sistema = MetricsCalculator.forSystem(cluster);
        check(sistema.getCompleted() >= 6, "el sistema cuenta los procesos terminados (" + sistema.getCompleted() + ")");
        check(sistema.getMissedDeadline() == 1, "una violacion de deadline en el sistema");
        check(sistema.getThroughput() > 0, "throughput positivo");
        check(sistema.getCpuUtilization() > 0 && sistema.getCpuUtilization() <= 1, "utilizacion de CPU entre 0 y 1");
        check(sistema.getDeadlineCompliance() > 0 && sistema.getDeadlineCompliance() < 1,
                "cumplimiento de deadlines entre 0 y 1");
        check(sistema.getFairness() > 0 && sistema.getFairness() <= 1, "equidad (Jain) entre 0 y 1");
        check(cluster.getComputer(1).getUtilizationSeries().size() == cluster.getCurrentCycle(),
                "el historial de utilizacion tiene una muestra por ciclo");

        System.out.println("--- configuracion en JSON ---");
        Path archivo = Files.createTempFile("integral", ".json");
        try {
            JSONManager.save(ConfigExporter.fromCluster(cluster), archivo.toString());
            Cluster recargado = ConfigLoader.build(JSONManager.load(archivo.toString()));
            check(recargado.getComputerCount() == 1
                    && ConfigExporter.fromCluster(recargado).processes.length == 7,
                    "guardar y cargar reconstruye el computador y los 7 procesos");
            recargado.tick();
            recargado.setPolicy(1, SchedulingAlgorithm.ROUND_ROBIN);
            int otraVez = correrConCambios(recargado);
            check(otraVez == ciclos, "la simulacion cargada del JSON repite el resultado (" + otraVez
                    + " ciclos, igual que la original)");
        } finally {
            Files.deleteIfExists(archivo);
        }

        System.out.println("--- interfaz grafica ---");
        if (GraphicsEnvironment.isHeadless()) {
            System.out.println("  (sin pantalla: se omite)");
        } else {
            Cluster visible = ConfigLoader.build(config);
            boolean ok = true;
            try {
                javax.swing.SwingUtilities.invokeAndWait(() -> {
                    PanelCluster panel = new PanelCluster(visible);
                    for (int i = 0; i < 20; i++) {
                        visible.tick();
                        panel.refresh();
                    }
                    panel.shutdown();
                });
            } catch (Exception e) {
                ok = false;
                e.printStackTrace();
            }
            check(ok, "la interfaz dibuja el cluster y se actualiza ciclo a ciclo sin errores");
        }

        System.out.println();
        if (fallas == 0) {
            System.out.println("TODAS LAS PRUEBAS PASARON (" + verificaciones + " verificaciones)");
        } else {
            System.out.println("HAY " + fallas + " FALLAS de " + verificaciones + " verificaciones");
            System.exit(1);
        }
        System.exit(0);
    }

    /** Un computador con RAM 100 (FCFS, quantum 4) y 7 procesos que no caben todos a la vez. */
    private static ConfigDTO escenario() {
        ConfigDTO c = new ConfigDTO();
        c.cycleDurationMs = 10;
        c.networkLatency = 0;
        c.computers = new ConfigDTO.ComputerConfig[] {computador("PC1", SchedulingAlgorithm.FCFS, 4)};

        c.processes = new ConfigDTO.ProcessConfig[] {
            proceso("cpuA", ProcessType.CPU_BOUND, 2, 60, 500, 1, 10),
            proceso("cpuB", ProcessType.CPU_BOUND, 3, 60, 500, 1, 10),
            proceso("rr1", ProcessType.CPU_BOUND, 2, 20, 500, 1, 12),
            proceso("rr2", ProcessType.CPU_BOUND, 2, 20, 500, 1, 12),
            proceso("edf1", ProcessType.CPU_BOUND, 1, 10, 4, 1, 10),
            proceso("edf2", ProcessType.CPU_BOUND, 1, 10, 500, 1, 6),
            proceso("io", ProcessType.CPU_BOUND, 2, 10, 500, 1, 6)};
        return c;
    }

    private static ConfigDTO.ComputerConfig computador(String n, SchedulingAlgorithm p, int q) {
        ConfigDTO.ComputerConfig c = new ConfigDTO.ComputerConfig();
        c.name = n;
        c.ramSize = 100;
        c.policy = p;
        c.quantum = q;
        return c;
    }

    private static ConfigDTO.ProcessConfig proceso(String n, ProcessType t, int prio, int mem, int deadline,
            int pc, int cycles) {
        ConfigDTO.ProcessConfig p = new ConfigDTO.ProcessConfig();
        p.name = n;
        p.type = t;
        p.priority = prio;
        p.memorySize = mem;
        p.deadline = deadline;
        p.computerId = pc;
        p.cycles = cycles;
        return p;
    }

    /**
     * Sigue desde Round Robin: 5 ciclos mas, cambia a EDF y corre hasta que todos terminen.
     * @return ciclos totales (contando el tick y los 5 ciclos de antes de terminar)
     */
    private static int correrConCambios(Cluster c) {
        for (int i = 0; i < 5; i++) {
            c.tick();
        }
        c.setPolicy(1, SchedulingAlgorithm.EDF);
        int resto = correr(c);
        return resto < 0 ? -1 : resto + 6;   // +1 del tick inicial y +5 de arriba
    }

    /** Avanza ciclo a ciclo hasta que todos los procesos terminen. @return ciclos usados, o -1 */
    private static int correr(Cluster c) {
        for (int i = 1; i <= MAX_CICLOS; i++) {
            c.tick();
            if (procesos(c).length == 7) {
                return i;
            }
        }
        return -1;
    }

    /** Todos los procesos que ya estan en alguna cola de terminados. */
    private static PCB[] procesos(Cluster c) {
        PCB[] r = new PCB[0];
        for (int i = 1; i <= c.getComputerCount(); i++) {
            avilaos.datastructures.CustomQueue<PCB> fin = c.getComputer(i).getFinishedSnapshot();
            PCB[] n = new PCB[r.length + fin.size()];
            for (int j = 0; j < r.length; j++) {
                n[j] = r[j];
            }
            for (int j = 0; j < fin.size(); j++) {
                n[r.length + j] = fin.get(j);
            }
            r = n;
        }
        return r;
    }

    private static boolean soloVenceEdf1(PCB[] todos) {
        for (int i = 0; i < todos.length; i++) {
            boolean debeVencer = todos[i].getName().equals("edf1");
            if (todos[i].getState() != ProcessState.TERMINATED || todos[i].isDeadlineMissed() != debeVencer) {
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