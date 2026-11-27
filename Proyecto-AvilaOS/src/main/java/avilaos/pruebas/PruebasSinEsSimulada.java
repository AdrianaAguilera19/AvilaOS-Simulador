/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package avilaos.pruebas;

import avilaos.enums.ProcessState;
import avilaos.enums.ProcessType;
import avilaos.enums.BlockReason;
import avilaos.enums.SchedulingAlgorithm;
import avilaos.model.hardware.Cluster;
import avilaos.model.hardware.Computer;
import avilaos.model.process.PCB;
import avilaos.model.process.ProcessFactory;
import avilaos.persistence.ConfigDTO;
import avilaos.persistence.ConfigException;
import avilaos.persistence.ConfigExporter;
import avilaos.persistence.ConfigLoader;
import avilaos.persistence.JSONManager;
import avilaos.scheduler.PolicyFactory;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * E11: el simulador ya no tiene E/S simulada. La unica E/S es la del disco (procesos de archivo). No
 * existen el tipo IO_BOUND, el motivo de bloqueo IO, los parametros de E/S del PCB, la fabrica
 * createIoBound, los campos del JSON, la espera simulada del computador ni los campos del formulario.
 * Un proceso comun nunca se bloquea solo. Un JSON viejo con un proceso IO_BOUND se rechaza con un
 * mensaje claro; los campos ioInterval/ioDuration sueltos en un proceso CPU_BOUND se ignoran.
 * Se ejecuta como programa (main); termina con "TODAS LAS PRUEBAS PASARON" o con codigo 1.
 */
public class PruebasSinEsSimulada {

    private static int fallas = 0;
    private static int verificaciones = 0;

    public static void main(String[] args) throws Exception {
        yaNoExisteNadaDeEsSimulada();
        unProcesoComunNuncaSeBloquea();
        jsonViejoConIoBound();
        camposViejosSeIgnoran();

        System.out.println();
        if (fallas == 0) {
            System.out.println("TODAS LAS PRUEBAS PASARON (" + verificaciones + " verificaciones)");
        } else {
            System.out.println("HAY " + fallas + " FALLAS de " + verificaciones + " verificaciones");
            System.exit(1);
        }
    }

    private static void yaNoExisteNadaDeEsSimulada() {
        titulo("Ya no existe nada de E/S simulada");
        boolean tipo = false;
        for (ProcessType t : ProcessType.values()) {
            tipo = tipo || t.name().equals("IO_BOUND");
        }
        check(!tipo, "el enum ProcessType no tiene IO_BOUND");
        boolean motivo = false;
        for (BlockReason r : BlockReason.values()) {
            motivo = motivo || r.name().equals("IO");
        }
        check(!motivo, "el enum BlockReason no tiene IO (queda DISK_IO para el disco)");
        check(tiene(BlockReason.class, "DISK_IO"), "BlockReason sigue teniendo DISK_IO");
        check(!tieneMetodo(PCB.class, "getIoInterval") && !tieneMetodo(PCB.class, "getIoDuration")
                && !tieneMetodo(PCB.class, "setIoParameters"), "el PCB no tiene intervalo ni duracion de E/S");
        check(!tieneMetodo(ProcessFactory.class, "createIoBound"), "ProcessFactory no tiene createIoBound");
        check(!tieneCampo(ConfigDTO.ProcessConfig.class, "ioInterval") && !tieneCampo(ConfigDTO.ProcessConfig.class, "ioDuration"),
                "el JSON de configuracion no tiene ioInterval ni ioDuration");
        check(!tieneCampo(Computer.class, "ioWaits"), "el computador no tiene la lista de esperas de E/S simulada");
        boolean campoForm = false;
        for (Class<?> c : avilaos.gui.DialogosCreacion.class.getDeclaredClasses()) {
            campoForm = campoForm || tieneCampo(c, "txtIntervalo") || tieneCampo(c, "txtDuracionEs");
        }
        check(!campoForm, "el formulario de proceso no pide intervalo ni duracion de E/S");
    }

    private static void unProcesoComunNuncaSeBloquea() {
        titulo("Un proceso comun nunca se bloquea por si solo");
        Cluster c = new Cluster(100, 0);
        Computer pc = c.addComputer("PC1", PolicyFactory.create(SchedulingAlgorithm.FCFS, 2), 50);
        PCB p = ProcessFactory.createCpuBound("A", 1, 10, 12, 500, pc.getId(), 0);
        c.submit(p);
        boolean seBloqueo = false;
        for (int i = 0; i < 40; i++) {
            c.tick();
            seBloqueo = seBloqueo || p.getState() == ProcessState.BLOCKED;
        }
        check(!seBloqueo, "A nunca estuvo Bloqueado");
        check(p.getState() == ProcessState.TERMINATED && !p.isDeadlineMissed(), "A corrio sus 12 instrucciones y termino");
        check(pc.getBlockedSnapshot().isEmpty(), "la cola de bloqueados esta vacia");
    }

    private static void jsonViejoConIoBound() throws Exception {
        titulo("Un JSON viejo con un proceso IO_BOUND se rechaza con un mensaje claro");
        String viejo = "{\"cycleDurationMs\":50,\"networkLatency\":0,"
                + "\"computers\":[{\"name\":\"PC1\",\"ramSize\":200,\"policy\":\"FCFS\",\"quantum\":4}],"
                + "\"processes\":[{\"name\":\"io\",\"type\":\"IO_BOUND\",\"priority\":1,\"memorySize\":10,"
                + "\"deadline\":100,\"computerId\":1,\"cycles\":12,\"ioInterval\":3,\"ioDuration\":2}]}";
        Path archivo = Files.createTempFile("conio", ".json");
        boolean rechazado = false;
        String mensaje = "";
        try {
            Files.writeString(archivo, viejo, StandardCharsets.UTF_8);
            ConfigLoader.build(JSONManager.load(archivo.toString()));
        } catch (ConfigException e) {
            rechazado = true;
            mensaje = e.getMessage();
        } finally {
            Files.deleteIfExists(archivo);
        }
        check(rechazado, "se rechaza con ConfigException: " + mensaje);
        check(mensaje != null && !mensaje.isBlank(), "el mensaje no esta vacio");
    }

    private static void camposViejosSeIgnoran() throws Exception {
        titulo("Los campos ioInterval/ioDuration sueltos en un CPU_BOUND se ignoran");
        String viejo = "{\"cycleDurationMs\":50,\"networkLatency\":0,"
                + "\"computers\":[{\"name\":\"PC1\",\"ramSize\":200,\"policy\":\"FCFS\",\"quantum\":4}],"
                + "\"processes\":[{\"name\":\"cpu\",\"type\":\"CPU_BOUND\",\"priority\":1,\"memorySize\":10,"
                + "\"deadline\":100,\"computerId\":1,\"cycles\":12,\"ioInterval\":3,\"ioDuration\":2}]}";
        Path archivo = Files.createTempFile("camposio", ".json");
        try {
            Files.writeString(archivo, viejo, StandardCharsets.UTF_8);
            Cluster c = ConfigLoader.build(JSONManager.load(archivo.toString()));
            check(ConfigExporter.fromCluster(c).processes.length == 1, "se carga el proceso sin fallar");
        } catch (Exception e) {
            check(false, "fallo al cargar: " + e.getMessage());
        } finally {
            Files.deleteIfExists(archivo);
        }
    }

    private static boolean tiene(Class<? extends Enum<?>> e, String nombre) {
        for (Enum<?> v : e.getEnumConstants()) {
            if (v.name().equals(nombre)) {
                return true;
            }
        }
        return false;
    }

    private static boolean tieneMetodo(Class<?> c, String nombre) {
        for (java.lang.reflect.Method m : c.getDeclaredMethods()) {
            if (m.getName().equals(nombre)) {
                return true;
            }
        }
        return false;
    }

    private static boolean tieneCampo(Class<?> c, String nombre) {
        for (java.lang.reflect.Field f : c.getDeclaredFields()) {
            if (f.getName().equals(nombre)) {
                return true;
            }
        }
        return false;
    }

    private static void titulo(String texto) {
        System.out.println("--- " + texto + " ---");
    }

    private static void check(boolean ok, String texto) {
        verificaciones++;
        System.out.println((ok ? "  OK    " : "  FALLA ") + texto);
        if (!ok) {
            fallas++;
        }
    }
}