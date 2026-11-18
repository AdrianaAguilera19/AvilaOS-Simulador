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
import avilaos.gui.CreadorManual;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Pruebas de que ya no hay buffers ni procesos productor/consumidor en la interfaz ni en el JSON (B02).
 * Termina con "TODAS LAS PRUEBAS PASARON" o con la lista de fallas (codigo de salida 1).
 */
public class PruebasSinBuffers {

    private static int fallas = 0;
    private static int verificaciones = 0;

    public static void main(String[] args) throws Exception {
        configuracionSinBuffers();
        jsonGuardadoSinBuffers();
        jsonViejoConBuffersSeIgnora();
        productorYConsumidorSeRechazan();
        creacionManualSoloCpuYEs();
        laInterfazYaNoTienePanelDeBuffers();

        System.out.println();
        if (fallas == 0) {
            System.out.println("TODAS LAS PRUEBAS PASARON (" + verificaciones + " verificaciones)");
        } else {
            System.out.println("HAY " + fallas + " FALLAS de " + verificaciones + " verificaciones");
            System.exit(1);
        }
    }

    private static void check(boolean ok, String texto) {
        verificaciones++;
        System.out.println((ok ? "  OK    " : "  FALLA ") + texto);
        if (!ok) {
            fallas++;
        }
    }

    private static void titulo(String texto) {
        System.out.println();
        System.out.println("== " + texto);
    }

    private static ConfigDTO base() {
        ConfigDTO c = new ConfigDTO();
        c.cycleDurationMs = 50;
        c.networkLatency = 0;
        ConfigDTO.ComputerConfig pc = new ConfigDTO.ComputerConfig();
        pc.name = "PC1";
        pc.ramSize = 200;
        pc.policy = SchedulingAlgorithm.FCFS;
        pc.quantum = 4;
        c.computers = new ConfigDTO.ComputerConfig[] {pc};
        c.processes = new ConfigDTO.ProcessConfig[] {proceso("cpu", ProcessType.CPU_BOUND), proceso("io", ProcessType.IO_BOUND)};
        c.processes[1].ioInterval = 3;
        c.processes[1].ioDuration = 2;
        return c;
    }

    private static ConfigDTO.ProcessConfig proceso(String nombre, ProcessType tipo) {
        ConfigDTO.ProcessConfig p = new ConfigDTO.ProcessConfig();
        p.name = nombre;
        p.type = tipo;
        p.priority = 1;
        p.memorySize = 10;
        p.deadline = 100;
        p.computerId = 1;
        p.cycles = 12;
        return p;
    }

    private static String mensaje(ConfigDTO c) {
        try {
            ConfigLoader.build(c);
            return null;
        } catch (ConfigException e) {
            return e.getMessage();
        }
    }

    private static void configuracionSinBuffers() {
        titulo("B02 - una configuracion sin buffers se arma y simula");
        Cluster cluster = null;
        try {
            cluster = ConfigLoader.build(base());
        } catch (ConfigException e) {
            check(false, "no se pudo armar: " + e.getMessage());
            return;
        }
        check(cluster.getComputerCount() == 1, "el cluster se arma con su computador");
        for (int i = 0; i < 40; i++) {
            cluster.tick();
        }
        check(ConfigExporter.fromCluster(cluster).processes.length == 2, "los 2 procesos (CPU-bound e IO-bound) siguen ahi tras 40 ciclos");
        check(cluster.getComputer(1).getFinishedSnapshot().size() == 2, "y los dos terminan");
    }

    private static void jsonGuardadoSinBuffers() throws Exception {
        titulo("B02 - el JSON nuevo no trae buffers");
        Path archivo = Files.createTempFile("sinbuffers", ".json");
        try {
            JSONManager.save(ConfigExporter.fromCluster(ConfigLoader.build(base())), archivo.toString());
            String texto = Files.readString(archivo, StandardCharsets.UTF_8);
            check(!texto.contains("buffers") && !texto.contains("bufferId"), "el archivo no tiene «buffers» ni «bufferId»");
            check(!texto.contains("cyclesPerItem") && !texto.contains("itemsRequired"), "ni campos de productor/consumidor");
            check(!texto.contains("PRODUCER") && !texto.contains("CONSUMER"), "ni procesos productor o consumidor");
            ConfigDTO leida = JSONManager.load(archivo.toString());
            check(leida.processes.length == 2 && leida.computers.length == 1, "se vuelve a cargar completo");
        } finally {
            Files.deleteIfExists(archivo);
        }
    }

    private static void jsonViejoConBuffersSeIgnora() throws Exception {
        titulo("B02 - un JSON viejo con la lista de buffers se carga ignorandola");
        String viejo = "{\"cycleDurationMs\":50,\"networkLatency\":0,"
                + "\"computers\":[{\"name\":\"PC1\",\"ramSize\":200,\"policy\":\"FCFS\",\"quantum\":4}],"
                + "\"buffers\":[{\"id\":1,\"capacity\":3,\"hostComputerId\":1}],"
                + "\"processes\":[{\"name\":\"cpu\",\"type\":\"CPU_BOUND\",\"priority\":1,\"memorySize\":10,"
                + "\"deadline\":100,\"computerId\":1,\"cycles\":12}]}";
        Path archivo = Files.createTempFile("viejo", ".json");
        try {
            Files.writeString(archivo, viejo, StandardCharsets.UTF_8);
            Cluster c = ConfigLoader.build(JSONManager.load(archivo.toString()));
            check(ConfigExporter.fromCluster(c).processes.length == 1, "carga el proceso y no falla por el campo viejo");
        } catch (Exception e) {
            check(false, "fallo al cargar el JSON viejo: " + e.getMessage());
        } finally {
            Files.deleteIfExists(archivo);
        }
    }

    private static void productorYConsumidorSeRechazan() {
        titulo("B02 - un productor o consumidor en el JSON se rechaza con un mensaje claro");
        ConfigDTO a = base();
        a.processes[0].type = ProcessType.PRODUCER;
        String m1 = mensaje(a);
        check(m1 != null && m1.contains("cpu") && m1.contains("PRODUCER") && m1.contains("buffers"), "PRODUCER: " + m1);
        ConfigDTO b = base();
        b.processes[1].type = ProcessType.CONSUMER;
        String m2 = mensaje(b);
        check(m2 != null && m2.contains("CONSUMER"), "CONSUMER: " + m2);
        check(mensaje(base()) == null, "y la configuracion buena sigue siendo valida");
    }

    private static void creacionManualSoloCpuYEs() throws Exception {
        titulo("B02 - crear procesos a mano: solo CPU-bound e IO-bound");
        Cluster cluster = ConfigLoader.build(base());
        ConfigDTO.ProcessConfig p = proceso("nuevo", ProcessType.PRODUCER);
        boolean rechazado = false;
        String texto = "";
        try {
            CreadorManual.crearProceso(cluster, p);
        } catch (IllegalArgumentException e) {
            rechazado = true;
            texto = e.getMessage();
        }
        check(rechazado, "un PRODUCER se rechaza: " + texto);
        check(ConfigExporter.fromCluster(cluster).processes.length == 2, "y no se crea ningun proceso");
        check(CreadorManual.crearProceso(cluster, proceso("cpu2", ProcessType.CPU_BOUND)) != null, "un CPU-bound si se crea");
        ConfigDTO.ProcessConfig io = proceso("io2", ProcessType.IO_BOUND);
        io.ioInterval = 3;
        io.ioDuration = 2;
        check(CreadorManual.crearProceso(cluster, io) != null, "un IO-bound si se crea");
        check(ConfigExporter.fromCluster(cluster).processes.length == 4, "quedan 4 procesos");
    }

    private static void laInterfazYaNoTienePanelDeBuffers() {
        titulo("B02 - la interfaz ya no tiene el panel ni la ventana de buffers");
        check(!existe("avilaos.gui.PanelBuffers"), "no existe avilaos.gui.PanelBuffers");
        check(!existe("avilaos.gui.DialogosCreacion$DialogoBuffer"), "no existe la ventana «Nuevo buffer»");
        boolean metodo = false;
        for (java.lang.reflect.Method m : avilaos.gui.DialogosCreacion.class.getDeclaredMethods()) {
            if (m.getName().toLowerCase().contains("buffer")) {
                metodo = true;
            }
        }
        check(!metodo, "DialogosCreacion no tiene ningun metodo de buffers");
        boolean crearBuffer = false;
        for (java.lang.reflect.Method m : CreadorManual.class.getDeclaredMethods()) {
            if (m.getName().toLowerCase().contains("buffer")) {
                crearBuffer = true;
            }
        }
        check(!crearBuffer, "CreadorManual no tiene crearBuffer");
    }

    private static boolean existe(String clase) {
        try {
            Class.forName(clase);
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }
}