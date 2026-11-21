package avilaos.pruebas;

import avilaos.enums.BlockReason;
import avilaos.enums.DiskSchedulingPolicy;
import avilaos.enums.ExecutionMode;
import avilaos.enums.HeadDirection;
import avilaos.enums.LogCategory;
import avilaos.enums.Permission;
import avilaos.enums.ProcessState;
import avilaos.enums.SchedulingAlgorithm;
import avilaos.enums.UserMode;
import avilaos.log.EventLogger;
import avilaos.model.hardware.BlockMap;
import avilaos.model.hardware.Cluster;
import avilaos.model.hardware.Computer;
import avilaos.model.hardware.Disk;
import avilaos.model.hardware.DiskHead;
import avilaos.model.process.PCB;
import avilaos.model.process.ProcessFactory;
import avilaos.os.disk.DiskQueue;
import avilaos.os.disk.DiskRecordLog;
import avilaos.os.disk.DiskScheduler;
import avilaos.os.disk.policy.DiskPolicyFactory;
import avilaos.os.filesystem.ActiveUser;
import avilaos.os.filesystem.ChainAllocator;
import avilaos.os.filesystem.DiskIoCoordinator;
import avilaos.os.filesystem.DiskIoProcessRunner;
import avilaos.os.filesystem.FileNode;
import avilaos.os.filesystem.FileSystem;
import avilaos.os.filesystem.FileTree;
import avilaos.os.filesystem.PermissionMatrix;
import avilaos.os.filesystem.SecuredFileSystem;
import avilaos.persistence.FileSystemData;
import avilaos.persistence.FileSystemJsonManager;
import avilaos.scheduler.PolicyFactory;

/**
 * Pruebas integrales de los procesos de E/S (E03, E04, E05, E06, E07, E08, E09 y E10).
 */
public class PruebasProcesosEs {

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

    public static void main(String[] args) throws Exception {
        colasCpuModoKernelYBloqueo();
        efectoDiferidoYDesbloqueoAListo();
        tablaDeCostosYCrudCompleto();
        politicasCpuConProcesosEs();
        fallosControladosNoDetienenSimulacion();

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

    private static class Entorno {
        final Cluster cluster;
        final Computer computer;
        final Disk disk;
        final DiskQueue diskQueue;
        final DiskScheduler diskScheduler;
        final SecuredFileSystem securedFs;
        final DiskRecordLog recordLog;
        final DiskIoCoordinator coordinator;
        long ciclo = 0;

        Entorno(String jsonFs, SchedulingAlgorithm cpuAlg, DiskSchedulingPolicy diskPol, int headSpeed) throws Exception {
            FileSystemData fsData = FileSystemJsonManager.parseJson(jsonFs);
            cluster = new Cluster(100, 0);
            computer = cluster.addComputer("Comp-1", PolicyFactory.create(cpuAlg, 2), 1024);
            disk = fsData.createDisk(headSpeed, HeadDirection.UP);
            diskQueue = new DiskQueue();
            diskScheduler = new DiskScheduler(diskQueue, DiskPolicyFactory.create(diskPol), cluster);
           securedFs = new SecuredFileSystem(fsData.getFileSystem(), new PermissionMatrix(fsData.getFileTree()));
            recordLog = new DiskRecordLog();
            coordinator = new DiskIoCoordinator(computer, disk, diskScheduler, securedFs, recordLog, cluster.getLogger());
        }

        Entorno(SchedulingAlgorithm cpuAlg, DiskSchedulingPolicy diskPol, int headSpeed) {
            BlockMap map = new BlockMap(200);
            FileTree tree = new FileTree("admin");
            ChainAllocator allocator = new ChainAllocator(map, 200);
            FileSystem fs = new FileSystem(tree, allocator, map);
            fs.createDirectory("/sistema", "admin", Permission.PUBLIC);
            fs.createDirectory("/usuarios", "user1", Permission.PUBLIC);

            cluster = new Cluster(100, 0);
            computer = cluster.addComputer("Comp-1", PolicyFactory.create(cpuAlg, 2), 1024);
            disk = new Disk(map, new DiskHead(200, 0, headSpeed, HeadDirection.UP));
            diskQueue = new DiskQueue();
            diskScheduler = new DiskScheduler(diskQueue, DiskPolicyFactory.create(diskPol), cluster);
            securedFs = new SecuredFileSystem(fs, new PermissionMatrix(tree));
            recordLog = new DiskRecordLog();
            coordinator = new DiskIoCoordinator(computer, disk, diskScheduler, securedFs, recordLog, cluster.getLogger());
        }

        void tick() {
            ciclo++;
            computer.onTick(ciclo);
            coordinator.onTick(ciclo);
        }
    }

    private static void colasCpuModoKernelYBloqueo() throws Exception {
        titulo("E03 + E04 + E05 + E10 - Paso por colas, instruccion en modo SO, contador kernel y bloqueo DISK_IO");
        Entorno e = new Entorno(JSON_FS_ENUNCIADO, SchedulingAlgorithm.FCFS, DiskSchedulingPolicy.FIFO, 1);

        PCB proc62 = ProcessFactory.createDiskIo(
                "Proc_62", 1, 16, 2, 500, e.computer.getId(), 0L,
                "READ", "/sistema/script.py", 0, null, "admin", UserMode.ADMIN
        );
        e.coordinator.submitProcess(proc62);

        check(proc62.getState() == ProcessState.NEW && e.computer.getNewSnapshot().size() == 1,
                "E03: el proceso de E/S entra primero a la cola de Nuevos");

        e.ciclo++;
        e.computer.onTick(e.ciclo);
        check(proc62.getState() == ProcessState.RUNNING && e.computer.getRunning() == proc62,
                "E03: el proceso pasa a Listo y es despachado a Ejecucion en la CPU");
        long entradasTrasCarga = e.computer.getKernelEntries();

        e.ciclo++;
        e.computer.onTick(e.ciclo);
        check(e.computer.getMode() == ExecutionMode.S_O,
                "E04: al ejecutar la instruccion de E/S la CPU muestra modo sistema operativo (S_O)");
        check(e.computer.getKernelEntries() == entradasTrasCarga + 1,
                "E04: la instruccion de E/S suma 1 al contador de entradas a kernel");
        check(proc62.getProgramCounter() == 1 && proc62.getRemainingTime() == 1,
                "E04 + E10: la operacion de E/S cuenta como 1 instruccion ejecutada (queda 1 de 2)");
        check(e.computer.getRunning() == null && e.computer.getCpu().isFree(),
                "E05: el proceso libera la CPU al bloquearse por E/S");
        check(proc62.getState() == ProcessState.BLOCKED && proc62.getBlockReason() == BlockReason.DISK_IO
                && e.computer.getBlockedSnapshot().size() == 1,
                "E05: el PCB queda en Bloqueado con motivo DISK_IO en la cola de bloqueados");
        check(e.diskQueue.size() == 1 && e.diskQueue.get(0).getTargetBlock() == 62
                && e.diskQueue.get(0).getBlocksToTransfer() == 2,
                "E05 + E08: la solicitud entra a la cola del disco (bloque 62, 2 bloques a transferir)");
    }

    private static void efectoDiferidoYDesbloqueoAListo() throws Exception {
        titulo("E06 + E07 - El cambio en el FS ocurre al terminar el disco y el proceso despierta a Listo");
        Entorno e = new Entorno(JSON_FS_ENUNCIADO, SchedulingAlgorithm.FCFS, DiskSchedulingPolicy.FIFO, 10);

        PCB borrarImg = ProcessFactory.createDiskIo(
                "Proc_119", 1, 16, 2, 500, e.computer.getId(), 0L,
                "DELETE", "/usuarios/img1.png", 0, null, "admin", UserMode.ADMIN
        );
        DiskIoProcessRunner runner = e.coordinator.submitProcess(borrarImg);

        e.tick();
        e.tick();

        check(e.securedFs.getFileSystem().getTree().find("/usuarios/img1.png").isFound()
                && !e.disk.getMap().isFree(119),
                "E07: mientras el proceso esta Bloqueado esperando al disco, el archivo y sus bloques siguen intactos");

        for (int i = 0; i < 9; i++) {
            e.tick();
        }
        check(e.disk.isBusy() && e.securedFs.getFileSystem().getTree().find("/usuarios/img1.png").isFound(),
                "E07: un ciclo antes de terminar la transferencia en el disco, el archivo todavia existe");

        e.ciclo++;
        e.coordinator.onTick(e.ciclo);
        check(!e.securedFs.getFileSystem().getTree().find("/usuarios/img1.png").isFound()
                && e.disk.getMap().isFree(119) && e.disk.getMap().isFree(122),
                "E07: al terminar la solicitud en el disco se elimina el archivo y se liberan sus bloques 119..122");
        check(runner.getOperationResult() != null && runner.getOperationResult().isOk(),
                "E07: el runner guarda el FsResult exitoso de la operacion");
        check(borrarImg.getState() == ProcessState.READY && e.computer.getRunning() == null,
                "E06: al terminar el disco el proceso pasa a Listo (READY), no directo a Ejecucion");

        e.computer.onTick(e.ciclo);
        check(borrarImg.getState() == ProcessState.RUNNING && e.computer.getRunning() == borrarImg,
                "E06: tras pasar por Listo, el planificador de CPU lo vuelve a poner en Ejecucion");
        e.tick();
        check(borrarImg.getState() == ProcessState.TERMINATED && e.computer.getFinishedSnapshot().size() == 1
                && e.computer.getRam().getUsedSize() == 0,
                "E10: tras ejecutar su 2a instruccion en CPU el proceso pasa a Terminado y libera su RAM");
    }

    private static void tablaDeCostosYCrudCompleto() {
        titulo("E07 + E08 - Operaciones CREATE (archivo y directorio), READ, UPDATE y DELETE mediante procesos");
        Entorno e = new Entorno(SchedulingAlgorithm.FCFS, DiskSchedulingPolicy.FIFO, 50);

        e.coordinator.submitFileOperationProcess(
                "P_Crear", 1, 16, 2, 200, 0L,
                "CREATE", "/usuarios/agenda.txt", 5, null, Permission.PRIVATE, ActiveUser.user("user1")
        );
        while (e.computer.getFinishedSnapshot().size() < 1) {
            e.tick();
        }
        FileNode agenda = e.securedFs.getFileSystem().getTree().find("/usuarios/agenda.txt").getNode();
        check(agenda != null && agenda.getBlockCount() == 5 && agenda.getFirstBlock() == 0
                && "user1".equals(agenda.getOwner()) && agenda.hasCreator(),
                "CREATE por proceso crea /usuarios/agenda.txt con 5 bloques, dueno user1 y creatorProcessId");

        e.coordinator.submitCreateDirectoryProcess(
                "P_Dir", 1, 16, 2, 200, e.ciclo,
                "/usuarios/fotos", Permission.PUBLIC, ActiveUser.admin()
        );
        while (e.computer.getFinishedSnapshot().size() < 2) {
            e.tick();
        }
        FileNode dirFotos = e.securedFs.getFileSystem().getTree().find("/usuarios/fotos").getNode();
        check(dirFotos != null && dirFotos.isDirectory(),
                "CREATE directorio por proceso crea /usuarios/fotos sin llamar directo desde la GUI");

        PCB pLeer = e.coordinator.submitFileOperationProcess(
                "P_Leer", 1, 16, 2, 200, e.ciclo,
                "READ", "/usuarios/agenda.txt", 0, null, Permission.PUBLIC, ActiveUser.user("user1")
        );
        DiskIoProcessRunner runnerLeer = e.coordinator.findRunner(pLeer);
        while (e.computer.getFinishedSnapshot().size() < 3) {
            e.tick();
        }
        check(runnerLeer.getOperationResult() != null && runnerLeer.getOperationResult().isOk()
                && runnerLeer.getOperationResult().getBlocks() == 5,
                "READ por proceso recorre los 5 bloques encadenados del archivo");

        e.coordinator.submitFileOperationProcess(
                "P_Renombrar", 1, 16, 2, 200, e.ciclo,
                "UPDATE", "/usuarios/agenda.txt", 0, "agenda_final.txt", Permission.PUBLIC, ActiveUser.admin()
        );
        while (e.computer.getFinishedSnapshot().size() < 4) {
            e.tick();
        }
        check(!e.securedFs.getFileSystem().getTree().find("/usuarios/agenda.txt").isFound()
                && e.securedFs.getFileSystem().getTree().find("/usuarios/agenda_final.txt").isFound()
                && "/usuarios/agenda_final.txt".equals(e.disk.getMap().get(0).getOwner()),
                "UPDATE por proceso renombra el archivo y actualiza el dueno de sus bloques en BlockMap");

        e.coordinator.submitFileOperationProcess(
                "P_Borrar", 1, 16, 2, 200, e.ciclo,
                "DELETE", "/usuarios/agenda_final.txt", 0, null, Permission.PUBLIC, ActiveUser.admin()
        );
        while (e.computer.getFinishedSnapshot().size() < 5) {
            e.tick();
        }
        check(!e.securedFs.getFileSystem().getTree().find("/usuarios/agenda_final.txt").isFound()
                && e.disk.getMap().usedCount() == 0,
                "DELETE por proceso elimina el archivo y deja los 200 bloques libres");
    }

    private static void politicasCpuConProcesosEs() throws Exception {
        titulo("E03 - Procesos de E/S compiten bajo politicas de CPU (Prioridades)");
        Entorno e = new Entorno(JSON_FS_ENUNCIADO, SchedulingAlgorithm.PRIORITY, DiskSchedulingPolicy.FIFO, 200);
        e.coordinator.setHoldDiskDispatch(true);

        PCB baja = ProcessFactory.createDiskIo(
                "P_Baja", 5, 16, 2, 500, e.computer.getId(), 0L,
                "READ", "/sistema/config.sys", 0, null, "admin", UserMode.ADMIN
        );
        PCB alta = ProcessFactory.createDiskIo(
                "P_Alta", 1, 16, 2, 500, e.computer.getId(), 0L,
                "READ", "/sistema/script.py", 0, null, "admin", UserMode.ADMIN
        );
        e.coordinator.submitProcess(baja);
        e.coordinator.submitProcess(alta);

        e.tick();
        check(e.computer.getRunning() == alta,
                "E03 (PRIORITY): el proceso de E/S con prioridad 1 entra a la CPU antes que el de prioridad 5");

        e.tick();
        e.tick();
        check(e.diskQueue.size() == 2 && e.diskQueue.get(0).getRequester() == alta
                && e.diskQueue.get(1).getRequester() == baja,
                "E03: las solicitudes llegan a la cola del disco en el orden decidido por la politica de CPU");
    }

    private static void fallosControladosNoDetienenSimulacion() throws Exception {
        titulo("E09 - Fallos controlados y registro en EventLogger con LogCategory FS/PERMISO");
        Entorno e = new Entorno(JSON_FS_ENUNCIADO, SchedulingAlgorithm.FCFS, DiskSchedulingPolicy.FIFO, 100);
        EventLogger log = e.cluster.getLogger();

        PCB pNoExiste = ProcessFactory.createDiskIo(
                "P_Inexistente", 1, 16, 2, 300, e.computer.getId(), 0L,
                "READ", "/sistema/fantasma.bin", 0, null, "admin", UserMode.ADMIN
        );
        PCB pDuplicado = ProcessFactory.createDiskIo(
                "P_Duplicado", 1, 16, 2, 300, e.computer.getId(), 0L,
                "CREATE", "/sistema/boot.bin", 5, null, "admin", UserMode.ADMIN
        );
        PCB pSinBloques = ProcessFactory.createDiskIo(
                "P_SinEspacio", 1, 16, 2, 300, e.computer.getId(), 0L,
                "CREATE", "/usuarios/grande.bin", 10, null, "admin", UserMode.ADMIN
        );
        PCB pSinPermiso = ProcessFactory.createDiskIo(
                "P_SinPermiso", 1, 16, 2, 300, e.computer.getId(), 0L,
                "READ", "/usuarios/notas.txt", 0, null, "user2", UserMode.USER
        );
        PCB pValido = ProcessFactory.createDiskIo(
                "P_Valido", 1, 16, 2, 300, e.computer.getId(), 0L,
                "READ", "/sistema/script.py", 0, null, "admin", UserMode.ADMIN
        );

        DiskIoProcessRunner r1 = e.coordinator.submitProcess(pNoExiste);
        DiskIoProcessRunner r2 = e.coordinator.submitProcess(pDuplicado);
        DiskIoProcessRunner r3 = e.coordinator.submitProcess(pSinBloques);
        DiskIoProcessRunner r4 = e.coordinator.submitProcess(pSinPermiso);
        DiskIoProcessRunner r5 = e.coordinator.submitProcess(pValido);

        int limite = 60;
        while (e.computer.getFinishedSnapshot().size() < 5 && limite-- > 0) {
            e.tick();
        }

        check(r1.hasFailed() && pNoExiste.getState() == ProcessState.TERMINATED,
                "E09: ruta inexistente falla de forma controlada y pasa a Terminado");
        check(r2.hasFailed() && pDuplicado.getState() == ProcessState.TERMINATED,
                "E09: archivo ya existente falla de forma controlada y pasa a Terminado");
        check(r3.hasFailed() && pSinBloques.getState() == ProcessState.TERMINATED,
                "E09: falta de bloques libres falla de forma controlada sin alterar BlockMap");
        check(r4.hasFailed() && pSinPermiso.getState() == ProcessState.TERMINATED,
                "E09: operacion sin permiso en modo usuario se rechaza y pasa a Terminado");
        check(e.recordLog.size() == 1 && !r5.hasFailed() && pValido.getState() == ProcessState.TERMINATED,
                "E09: la simulacion continua tras los 4 errores y el 5to proceso valido completa su E/S");
        check(e.computer.getRam().getUsedSize() == 0 && e.disk.getMap().usedCount() == 200,
                "E09: toda la RAM fue liberada y el disco sigue consistente con sus 200 bloques");

        String logFs = log.getLastAsText(20, LogCategory.FS);
        String logPerm = log.getLastAsText(20, LogCategory.PERMISO);
        check(logFs.contains("P_Inexistente") && logFs.contains("P_Duplicado") && logFs.contains("P_SinEspacio"),
                "E09: los errores de FS se registran bajo LogCategory.FS");
        check(logPerm.contains("P_SinPermiso") && logPerm.contains("Permiso denegado"),
                "E09: el rechazo de permiso se registra bajo LogCategory.PERMISO");
    }
}
