package avilaos.pruebas;

import avilaos.datastructures.DynamicArray;
import avilaos.enums.DiskSchedulingPolicy;
import avilaos.enums.HeadDirection;
import avilaos.enums.IOOperationType;
import avilaos.enums.Permission;
import avilaos.enums.SchedulingAlgorithm;
import avilaos.enums.UserMode;
import avilaos.model.hardware.DiskHead;
import avilaos.model.process.PCB;
import avilaos.os.filesystem.FileCreator;
import avilaos.persistence.ConfigException;
import avilaos.persistence.ProcessConfigData;
import avilaos.persistence.ProcessConfigJsonManager;
import avilaos.persistence.SimulationSetup;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Pruebas del JSON de procesos y configuracion y de la carga conjunta (J04, J05, J06, J07, J08, J09, J11).
 */
public class PruebasJsonProcesosConfig {

    private static int fallas = 0;
    private static int verificaciones = 0;

    private static final String JSON_FS_ENUNCIADO = """
            {
              "diskConfig": { "totalBlocks": 200, "initialHead": 50 },
              "fileSystem": [
                { "path": "/mbr.bin", "type": "FILE", "startBlock": 0, "blocks": 11, "owner": "admin", "permissions": "public" },
                { "path": "/sistema", "type": "DIRECTORY", "owner": "admin", "permissions": "public" },
                { "path": "/usuarios", "type": "DIRECTORY", "owner": "admin", "permissions": "public" },
                { "path": "/sistema/boot.bin", "type": "FILE", "startBlock": 11, "blocks": 23, "owner": "admin", "permissions": "public" },
                { "path": "/usuarios/notas.txt", "type": "FILE", "startBlock": 34, "blocks": 28, "owner": "user1", "permissions": "private" },
                { "path": "/sistema/script.py", "type": "FILE", "startBlock": 62, "blocks": 2, "owner": "admin", "permissions": "public" },
                { "path": "/sistema/core.dll", "type": "FILE", "startBlock": 64, "blocks": 31, "owner": "admin", "permissions": "public" },
                { "path": "/sistema/config.sys", "type": "FILE", "startBlock": 95, "blocks": 24, "owner": "admin", "permissions": "public" },
                { "path": "/usuarios/img1.png", "type": "FILE", "startBlock": 119, "blocks": 4, "owner": "user1", "permissions": "private" },
                { "path": "/usuarios/data.csv", "type": "FILE", "startBlock": 123, "blocks": 57, "owner": "user2", "permissions": "public" },
                { "path": "/usuarios/video.mp4", "type": "FILE", "startBlock": 180, "blocks": 20, "owner": "user1", "permissions": "public" }
              ]
            }
            """;

    private static final String JSON_PROCESOS_ENUNCIADO = """
            {
              "globalConfig": {
                "cycleDurationMs": 300,
                "swapSizeBlocks": 100,
                "initialDiskPolicy": "FIFO"
              },
              "computer": {
                "ramCapacity": 1024,
                "policy": "FCFS",
                "quantum": 0
              },
              "processes": [
                { "name": "Proc_95", "memory": 16, "deadline": 500, "instructions": 2, "targetPath": "/sistema/config.sys", "operation": "READ" },
                { "name": "Proc_180", "memory": 16, "deadline": 500, "instructions": 2, "targetPath": "/usuarios/video.mp4", "operation": "READ" },
                { "name": "Proc_34", "memory": 16, "deadline": 500, "instructions": 2, "targetPath": "/usuarios/notas.txt", "operation": "UPDATE" },
                { "name": "Proc_119", "memory": 16, "deadline": 500, "instructions": 2, "targetPath": "/usuarios/img1.png", "operation": "DELETE" },
                { "name": "Proc_11", "memory": 16, "deadline": 500, "instructions": 2, "targetPath": "/sistema/boot.bin", "operation": "READ" },
                { "name": "Proc_123", "memory": 16, "deadline": 500, "instructions": 2, "targetPath": "/usuarios/data.csv", "operation": "UPDATE" },
                { "name": "Proc_62", "memory": 16, "deadline": 500, "instructions": 2, "targetPath": "/sistema/script.py", "operation": "READ" },
                { "name": "Proc_64", "memory": 16, "deadline": 500, "instructions": 2, "targetPath": "/sistema/core.dll", "operation": "READ" }
              ]
            }
            """;

    public static void main(String[] args) throws Exception {
        cargaEnunciadoYCreacionPcbs();
        camposOpcionalesConValoresPorDefecto();
        exigirAmbosJsonYConfigVisible();
        reiniciarConservandoDiscoModificado();
        jsonProcesosDanados();

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

    private static boolean falla(String json, String etiqueta) {
        try {
            ProcessConfigJsonManager.parseJson(json);
            return false;
        } catch (ConfigException e) {
            System.out.println("          -> [" + etiqueta + "] " + e.getMessage());
            return true;
        }
    }

    private static void cargaEnunciadoYCreacionPcbs() throws Exception {
        titulo("J04 + J05 - Carga del JSON del enunciado y creacion de los 8 PCB");
        Path temp = Files.createTempFile("avilaos_proc_", ".json");
        try {
            Files.writeString(temp, JSON_PROCESOS_ENUNCIADO);
            ProcessConfigData cfg = ProcessConfigJsonManager.load(temp.toString());

            check(cfg.getCycleDurationMs() == 300, "cycleDurationMs = 300");
            check(cfg.getSwapSizeBlocks() == 100, "swapSizeBlocks = 100");
            check(cfg.getInitialDiskPolicy() == DiskSchedulingPolicy.FIFO, "initialDiskPolicy = FIFO");
            check(cfg.getRamCapacity() == 1024, "ramCapacity = 1024");
            check(cfg.getCpuPolicy() == SchedulingAlgorithm.FCFS, "policy = FCFS");
            check(cfg.getQuantum() == 0, "quantum = 0 aceptado con FCFS");
            check(cfg.getHeadSpeed() == DiskHead.DEFAULT_SPEED, "headSpeed usa valor por defecto (1)");
            check(cfg.getInitialHeadDirection() == HeadDirection.UP, "direccion inicial por defecto (UP)");

            DynamicArray<PCB> pcbs = cfg.createProcesses(1, 0L);
            check(pcbs.size() == 8, "se crearon los 8 PCB de ejemplo");

            PCB p0 = pcbs.get(0);
            check("Proc_95".equals(p0.getName()) && p0.getDiskOperation() == IOOperationType.READ
                    && "/sistema/config.sys".equals(p0.getTargetPath())
                    && p0.getMemorySize() == 16 && p0.getTotalInstructions() == 2 && p0.getDeadline() == 500,
                    "Proc_95 tiene todos sus campos exactos");
            check("admin".equals(p0.getUserName()) && p0.getUserMode() == UserMode.ADMIN,
                    "usuario por defecto en procesos del enunciado es admin (ADMIN)");

            PCB p2 = pcbs.get(2);
            PCB p3 = pcbs.get(3);
            check(p2.getDiskOperation() == IOOperationType.UPDATE && "/usuarios/notas.txt".equals(p2.getTargetPath()),
                    "Proc_34 es UPDATE sobre /usuarios/notas.txt");
            check(p3.getDiskOperation() == IOOperationType.DELETE && "/usuarios/img1.png".equals(p3.getTargetPath()),
                    "Proc_119 es DELETE sobre /usuarios/img1.png");
        } finally {
            Files.deleteIfExists(temp);
        }
    }

    private static void camposOpcionalesConValoresPorDefecto() throws Exception {
        titulo("J06 - Campos opcionales (headSpeed, CREATE con fileSizeBlocks, UPDATE con newName, user)");
        String jsonOpcional = """
                {
                  "globalConfig": {
                    "cycleDurationMs": 200,
                    "swapSizeBlocks": 50,
                    "initialDiskPolicy": "C-SCAN",
                    "headSpeed": 5,
                    "initialHeadDirection": "DOWN"
                  },
                  "computer": {
                    "name": "Nodo Principal",
                    "ramCapacity": 512,
                    "policy": "ROUND_ROBIN",
                    "quantum": 3
                  },
                  "processes": [
                    {
                      "name": "CrearArchivo",
                      "memory": 32,
                      "deadline": 300,
                      "instructions": 4,
                      "targetPath": "/usuarios/nuevo.dat",
                      "operation": "CREATE",
                      "fileSizeBlocks": 8,
                      "priority": 2,
                      "user": "user1",
                      "userMode": "USER"
                    },
                    {
                      "name": "RenombrarNotas",
                      "memory": 16,
                      "deadline": 400,
                      "instructions": 2,
                      "targetPath": "/usuarios/notas.txt",
                      "operation": "UPDATE",
                      "newName": "notas_v2.txt"
                    }
                  ]
                }
                """;
        ProcessConfigData cfg = ProcessConfigJsonManager.parseJson(jsonOpcional);
        check(cfg.getInitialDiskPolicy() == DiskSchedulingPolicy.C_SCAN, "C-SCAN parseado con guion");
        check(cfg.getHeadSpeed() == 5 && cfg.getInitialHeadDirection() == HeadDirection.DOWN,
                "headSpeed=5 y direccion DOWN aplicados");
        check("Nodo Principal".equals(cfg.getComputerName()) && cfg.getCpuPolicy() == SchedulingAlgorithm.ROUND_ROBIN
                && cfg.getQuantum() == 3, "computador con nombre y Round Robin q=3");

        DynamicArray<PCB> pcbs = cfg.createProcesses(1, 0L);
        PCB crear = pcbs.get(0);
        PCB renombrar = pcbs.get(1);
        check(crear.getDiskOperation() == IOOperationType.CREATE && crear.getFileSizeBlocks() == 8
                && crear.getPriority() == 2 && "user1".equals(crear.getUserName())
                && crear.getUserMode() == UserMode.USER,
                "CREATE conserva fileSizeBlocks=8, priority=2 y user1 (USER)");
        check(renombrar.getDiskOperation() == IOOperationType.UPDATE
                && "notas_v2.txt".equals(renombrar.getNewName()),
                "UPDATE conserva newName=notas_v2.txt");
    }

    private static void exigirAmbosJsonYConfigVisible() throws Exception {
        titulo("J08 + J11 - Exigir ambos JSON antes de iniciar y mostrar los 4 valores nuevos");
        SimulationSetup setup = new SimulationSetup();
        check(!setup.isReady() && setup.missingMessage() != null, "sin archivos no esta listo");

        setup.loadFileSystemJson(JSON_FS_ENUNCIADO);
        check(!setup.isReady() && setup.missingMessage().contains("procesos"),
                "con solo FS avisa que falta el JSON de procesos");

        setup.loadProcessConfigJson(JSON_PROCESOS_ENUNCIADO);
        check(setup.isReady() && setup.missingMessage() == null, "con ambos JSON queda listo");

        String resumen = setup.configSummary();
        System.out.println("          -> Resumen: " + resumen);
        check(resumen.contains("200") && resumen.contains("100") && resumen.contains("FIFO") && resumen.contains("50"),
                "resumen muestra totalBlocks, swapSizeBlocks, initialDiskPolicy e initialHead");

        SimulationSetup.SimulationBundle bundle = setup.build();
        check(bundle.getCluster().getComputerCount() == 1, "Cluster armado con 1 solo computador");
        check(bundle.getDisk().totalBlocks() == 300 && bundle.getDisk().getHead().getPosition() == 50,
                "Disk tiene 200 bloques FS + 100 swap y cabezal en 50");
        check(bundle.getDiskScheduler().getPolicy().getType() == DiskSchedulingPolicy.FIFO,
                "DiskScheduler inicia con politica FIFO");
        check(bundle.getInitialProcesses().size() == 8, "bundle listo con los 8 PCB");
    }

    private static void reiniciarConservandoDiscoModificado() throws Exception {
        titulo("J09 - Reiniciar con nuevos procesos preservando cambios del disco");
        SimulationSetup setup = new SimulationSetup();
        setup.loadFileSystemJson(JSON_FS_ENUNCIADO);
        setup.loadProcessConfigJson(JSON_PROCESOS_ENUNCIADO);
        SimulationSetup.SimulationBundle primera = setup.build();

        // Simulamos cambios en el disco: borrar img1.png y crear nuevo.txt
        check(primera.getFileSystemData().getFileSystem().delete("/usuarios/img1.png").isOk(),
                "se elimina /usuarios/img1.png en la primera corrida");
        FileCreator creator = new FileCreator(
                primera.getFileSystemData().getFileTree(),
                primera.getFileSystemData().getAllocator()
        );
        check(creator.create("/usuarios", "extra.txt", 3, "admin", UserMode.ADMIN, Permission.PUBLIC, 42).isOk(),
                "se crea /usuarios/extra.txt de 3 bloques");

        // Guardamos/preservamos el estado modificado y cargamos un lote nuevo de procesos
        setup.preserveModifiedFileSystem(
                primera.getFileSystemData().getFileTree(),
                primera.getFileSystemData().getAllocator(),
                primera.getFileSystemData().getTotalBlocks(),
                77
        );
        String segundoLote = """
                {
                  "globalConfig": { "cycleDurationMs": 150, "swapSizeBlocks": 80, "initialDiskPolicy": "SSTF" },
                  "computer": { "ramCapacity": 512, "policy": "EDF", "quantum": 0 },
                  "processes": [
                    { "name": "LeerExtra", "memory": 16, "deadline": 200, "instructions": 2, "targetPath": "/usuarios/extra.txt", "operation": "READ" }
                  ]
                }
                """;
        setup.loadProcessConfigJson(segundoLote);
        SimulationSetup.SimulationBundle segunda = setup.build();

        check(!segunda.getFileSystemData().getFileTree().find("/usuarios/img1.png").isFound(),
                "la segunda corrida conserva la eliminacion de img1.png");
        check(segunda.getFileSystemData().getFileTree().find("/usuarios/extra.txt").isFound(),
                "la segunda corrida conserva el archivo nuevo /usuarios/extra.txt");
        check(segunda.getDisk().getHead().getPosition() == 77,
                "la segunda corrida conserva el cabezal en 77");
        check(segunda.getDiskScheduler().getPolicy().getType() == DiskSchedulingPolicy.SSTF
                && segunda.getInitialProcesses().size() == 1,
                "la segunda corrida aplica SSTF y el nuevo lote de 1 proceso");
    }

    private static void jsonProcesosDanados() {
        titulo("J07 - Rechazo controlado de JSON de procesos malformados o invalidos");

        check(falla("{ roto", "sintaxis rota"), "rechaza JSON malformado");
        check(falla("{ \"computer\": {}, \"processes\": [] }", "sin globalConfig"),
                "rechaza falta de globalConfig");
        check(falla("""
                {
                  "globalConfig": { "cycleDurationMs": 300, "swapSizeBlocks": 100, "initialDiskPolicy": "RANDOM" },
                  "computer": { "ramCapacity": 1024, "policy": "FCFS", "quantum": 0 },
                  "processes": [ { "name": "P1", "memory": 16, "deadline": 100, "instructions": 2, "targetPath": "/a.txt", "operation": "READ" } ]
                }
                """, "politica disco invalida"), "rechaza politica de disco desconocida");
        check(falla("""
                {
                  "globalConfig": { "cycleDurationMs": 300, "swapSizeBlocks": 100, "initialDiskPolicy": "FIFO" },
                  "computer": { "ramCapacity": 1024, "policy": "ROUND_ROBIN", "quantum": 0 },
                  "processes": [ { "name": "P1", "memory": 16, "deadline": 100, "instructions": 2, "targetPath": "/a.txt", "operation": "READ" } ]
                }
                """, "RR con quantum 0"), "rechaza Round Robin con quantum 0");
        check(falla("""
                {
                  "globalConfig": { "cycleDurationMs": 300, "swapSizeBlocks": 100, "initialDiskPolicy": "FIFO" },
                  "computer": { "ramCapacity": 1024, "policy": "FCFS", "quantum": 0 },
                  "processes": [ { "name": "P1", "memory": 16, "deadline": 100, "instructions": 2, "targetPath": "/usuarios/nuevo.txt", "operation": "CREATE" } ]
                }
                """, "CREATE sin fileSizeBlocks"), "rechaza CREATE sin fileSizeBlocks > 0");
        check(falla("""
                {
                  "globalConfig": { "cycleDurationMs": 300, "swapSizeBlocks": 100, "initialDiskPolicy": "FIFO" },
                  "computer": { "ramCapacity": 1024, "policy": "FCFS", "quantum": 0 },
                  "processes": [ { "name": "P1", "memory": 16, "deadline": 100, "instructions": 2, "targetPath": "/a.txt", "operation": "SUSPEND" } ]
                }
                """, "operacion SUSPEND en JSON"), "rechaza operacion de swap en JSON de procesos");
        check(falla("""
                {
                  "globalConfig": { "cycleDurationMs": 300, "swapSizeBlocks": 100, "initialDiskPolicy": "FIFO" },
                  "computer": { "ramCapacity": 1024, "policy": "FCFS", "quantum": 0 },
                  "processes": [ { "name": "P1", "memory": 16, "deadline": 100, "instructions": 2, "targetPath": "relativa/a.txt", "operation": "READ" } ]
                }
                """, "ruta relativa"), "rechaza targetPath que no empieza con /");
        check(falla("""
                {
                  "globalConfig": { "cycleDurationMs": 300, "swapSizeBlocks": 100, "initialDiskPolicy": "FIFO" },
                  "computer": { "ramCapacity": 1024, "policy": "FCFS", "quantum": 0 },
                  "processes": []
                }
                """, "processes vacio"), "rechaza arreglo processes vacio");
    }
}
