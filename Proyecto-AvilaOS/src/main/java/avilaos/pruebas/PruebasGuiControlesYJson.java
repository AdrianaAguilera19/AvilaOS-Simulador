package avilaos.pruebas;

import avilaos.enums.DiskSchedulingPolicy;
import avilaos.enums.HeadDirection;
import avilaos.enums.IOOperationType;
import avilaos.enums.LogCategory;
import avilaos.enums.NodeType;
import avilaos.enums.Permission;
import avilaos.enums.ProcessState;
import avilaos.enums.SchedulingAlgorithm;
import avilaos.enums.UserMode;
import avilaos.gui.DialogoProcesoEs;
import avilaos.gui.PanelAccionesArchivo;
import avilaos.gui.PanelCargaJson;
import avilaos.gui.PanelModoUsuario;
import avilaos.gui.PanelSistemaArchivos;
import avilaos.model.hardware.Cluster;
import avilaos.model.hardware.Computer;
import avilaos.model.hardware.Disk;
import avilaos.model.process.PCB;
import avilaos.os.disk.DiskQueue;
import avilaos.os.disk.DiskRecordLog;
import avilaos.os.disk.DiskScheduler;
import avilaos.os.disk.policy.DiskPolicyFactory;
import avilaos.os.filesystem.DiskIoCoordinator;
import avilaos.os.filesystem.PermissionMatrix;
import avilaos.os.filesystem.SecuredFileSystem;
import avilaos.os.filesystem.UserSession;
import avilaos.persistence.FileSystemData;
import avilaos.persistence.FileSystemJsonManager;
import avilaos.persistence.SimulationSetup;
import avilaos.scheduler.PolicyFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.swing.JComboBox;

/**
 * Pruebas integrales de G01, G12, G13, G14, G15, J08, J09 y J11.
 */
public class PruebasGuiControlesYJson {

    private static int fallas = 0;
    private static int verificaciones = 0;

    private static final String JSON_FS_ENUNCIADO = """
            {
              "diskConfig": { "totalBlocks": 200, "initialHead": 50 },
              "fileSystem": [
                { "path": "/mbr.bin", "type": "FILE", "startBlock": 0, "blocks": 11, "owner": "admin", "permissions": "public" },
                { "path": "/sistema", "type": "DIRECTORY", "owner": "admin", "permissions": "public" },
                { "path": "/usuarios", "type": "DIRECTORY", "owner": "user1", "permissions": "public" },
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
                { "name": "Proc_119", "memory": 16, "deadline": 500, "instructions": 2, "targetPath": "/usuarios/img1.png", "operation": "DELETE" }
              ]
            }
            """;

    public static void main(String[] args) throws Exception {
        selectorModoYUsuarioEnCaliente();
        ventanaYCreacionProcesoEsNoModificaFs();
        accionesAdminSoloCreanProcesos();
        rechazosModoUsuarioSinInterrumpir();
        cargaAmbosJsonConfigVisibleYReinicio();

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

    private static class EntornoGui {
        final Cluster cluster;
        final Computer comp;
        final Disk disk;
        final SecuredFileSystem sfs;
        final DiskIoCoordinator coord;
        final UserSession session = new UserSession();
        final PanelModoUsuario panelModo;
        final PanelSistemaArchivos panelFs;
        final PanelAccionesArchivo panelAcciones;
        long ciclo = 0;

        EntornoGui() throws Exception {
            FileSystemData fsData = FileSystemJsonManager.parseJson(JSON_FS_ENUNCIADO);
            cluster = new Cluster(100, 0);
            comp = cluster.addComputer("PC1", PolicyFactory.create(SchedulingAlgorithm.FCFS, 2), 1024);
            disk = fsData.createDisk(100, HeadDirection.UP);
            DiskScheduler scheduler = new DiskScheduler(new DiskQueue(),
                    DiskPolicyFactory.create(DiskSchedulingPolicy.FIFO), cluster);
            sfs = new SecuredFileSystem(fsData.getFileSystem(), new PermissionMatrix(fsData.getFileTree()));
            coord = new DiskIoCoordinator(comp, disk, scheduler, sfs, new DiskRecordLog(), cluster.getLogger());

            panelModo = new PanelModoUsuario(session, cluster.getLogger());
            panelFs = new PanelSistemaArchivos(sfs);
            panelAcciones = new PanelAccionesArchivo(coord, panelModo, panelFs);
        }

        void correrHastaTerminar(int totalEsperados) {
            int limite = 200;
            while (comp.getFinishedSnapshot().size() < totalEsperados && limite-- > 0) {
                ciclo++;
                comp.onTick(ciclo);
                coord.onTick(ciclo);
            }
            panelFs.refresh();
            panelAcciones.setCicloActual(ciclo);
        }
    }

    private static void selectorModoYUsuarioEnCaliente() throws Exception {
        titulo("G01 + G15 - Selector de modo de usuario en caliente y controles solo-admin");
        EntornoGui e = new EntornoGui();
        JComboBox<DiskSchedulingPolicy> comboPoliticaDisco = new JComboBox<>(DiskSchedulingPolicy.values());
        e.panelModo.registrarControlSoloAdmin(comboPoliticaDisco);

        check(e.panelModo.isAdminMode() && e.panelModo.getTextoModoActivo().contains("ADMINISTRADOR"),
                "inicia en modo administrador con indicador visible");
        check(comboPoliticaDisco.isEnabled() && e.panelAcciones.getBtnCrearDir().isEnabled(),
                "en modo admin estan habilitados el selector de politica y crear directorio");

        check(e.panelModo.cambiarAUsuario("user1"), "cambia en caliente a modo usuario (user1)");
        check(!e.panelModo.isAdminMode() && "user1".equals(e.session.getCurrent().getName())
                && e.panelModo.getTextoModoActivo().contains("user1"),
                "la sesion y la etiqueta visible reflejan user1 (USER)");
        check(!comboPoliticaDisco.isEnabled() && !e.panelAcciones.getBtnCrearDir().isEnabled(),
                "G15: en modo usuario se deshabilitan el cambio de politica y crear directorio");

        check(!e.panelModo.cambiarAUsuario("admin"), "rechaza usar 'admin' como nombre en modo usuario");
        e.panelModo.cambiarAAdmin();
        check(e.panelModo.isAdminMode() && comboPoliticaDisco.isEnabled()
                && e.panelAcciones.getBtnCrearDir().isEnabled(),
                "al volver a admin se rehabilitan los controles de administrador");
    }

    private static void ventanaYCreacionProcesoEsNoModificaFs() throws Exception {
        titulo("G12 - Crear proceso de E/S solo encola el PCB y no modifica el sistema de archivos");
        EntornoGui e = new EntornoGui();

        // Primero liberamos 4 bloques borrando img1.png mediante proceso para tener espacio para CREATE
        PCB pDel = DialogoProcesoEs.crearProcesoEs(
                e.coord, e.panelModo, e.panelFs, 0L,
                "ProcDel", IOOperationType.DELETE, NodeType.FILE, "/usuarios/img1.png",
                0, null, Permission.PUBLIC, 1, 16, 2, 300
        );
        check(pDel != null && pDel.getState() == ProcessState.NEW,
                "crear proceso DELETE genera un PCB en estado NEW");
        check(e.comp.getNewSnapshot().size() == 1,
                "el proceso aparece en la cola de procesos del computador");
        check(e.sfs.getFileSystem().getTree().find("/usuarios/img1.png").isFound(),
                "crear el proceso NO elimina todavia el archivo del FS");

        e.correrHastaTerminar(1);
        check(!e.sfs.getFileSystem().getTree().find("/usuarios/img1.png").isFound(),
                "recien cuando el proceso termina su E/S se aplica la eliminacion");

        // Ahora creamos un proceso CREATE sin avanzar el reloj todavia
        PCB pCreate = DialogoProcesoEs.crearProcesoEs(
                e.coord, e.panelModo, e.panelFs, e.ciclo,
                "ProcCrear", IOOperationType.CREATE, NodeType.FILE, "/usuarios/bitacora.txt",
                3, null, Permission.PUBLIC, 1, 16, 2, 300
        );
        check(pCreate != null && pCreate.getFileSizeBlocks() == 3 && e.comp.getNewSnapshot().size() == 1,
                "CREATE con 3 bloques queda encolado como PCB");
        check(!e.sfs.getFileSystem().getTree().find("/usuarios/bitacora.txt").isFound(),
                "crear el proceso CREATE NO modifica el FS hasta que corra en el disco");

        e.correrHastaTerminar(2);
        check(e.sfs.getFileSystem().getTree().find("/usuarios/bitacora.txt").isFound(),
                "al completarse el proceso CREATE aparece /usuarios/bitacora.txt en el FS");
    }

    private static void accionesAdminSoloCreanProcesos() throws Exception {
        titulo("G13 - Acciones de administrador (dir, archivo, leer, renombrar, eliminar) solo crean PCBs");
        EntornoGui e = new EntornoGui();

        // 1. Eliminar img1.png via boton de accion (solo crea PCB)
        e.panelFs.selectPath("/usuarios/img1.png");
        PCB del = e.panelAcciones.solicitarEliminarSeleccionado();
        check(del != null && del.getDiskOperation() == IOOperationType.DELETE
                && e.sfs.getFileSystem().getTree().find("/usuarios/img1.png").isFound(),
                "accion Eliminar crea PCB DELETE sin borrar directo del FS");
        e.correrHastaTerminar(1);

        // 2. Crear directorio via accion
        e.panelFs.selectPath("/usuarios");
        PCB mkdir = e.panelAcciones.solicitarCrearDirectorio("proyectos", Permission.PUBLIC);
        check(mkdir != null && !e.sfs.getFileSystem().getTree().find("/usuarios/proyectos").isFound(),
                "accion Crear directorio crea PCB sin modificar el arbol directamente");
        e.correrHastaTerminar(2);
        check(e.sfs.getFileSystem().getTree().find("/usuarios/proyectos").isFound(),
                "el directorio /usuarios/proyectos aparece tras completar el proceso");

        // 3. Crear archivo via accion
        e.panelFs.selectPath("/usuarios/proyectos");
        PCB crear = e.panelAcciones.solicitarCrearArchivo("plan.txt", 2, Permission.PUBLIC);
        check(crear != null && !e.sfs.getFileSystem().getTree().find("/usuarios/proyectos/plan.txt").isFound(),
                "accion Crear archivo crea PCB CREATE sin ocupar bloques directamente");
        e.correrHastaTerminar(3);
        check(e.sfs.getFileSystem().getTree().find("/usuarios/proyectos/plan.txt").isFound(),
                "el archivo /usuarios/proyectos/plan.txt aparece tras completar el proceso");

        // 4. Leer archivo via accion
        e.panelFs.selectPath("/usuarios/proyectos/plan.txt");
        PCB leer = e.panelAcciones.solicitarLeerSeleccionado();
        check(leer != null && leer.getDiskOperation() == IOOperationType.READ,
                "accion Leer crea PCB READ");
        e.correrHastaTerminar(4);

        // 5. Renombrar archivo via accion
        e.panelFs.selectPath("/usuarios/proyectos/plan.txt");
        PCB ren = e.panelAcciones.solicitarRenombrarSeleccionado("plan_final.txt");
        check(ren != null && ren.getDiskOperation() == IOOperationType.UPDATE
                && e.sfs.getFileSystem().getTree().find("/usuarios/proyectos/plan.txt").isFound(),
                "accion Renombrar crea PCB UPDATE sin renombrar directo");
        e.correrHastaTerminar(5);
        check(e.sfs.getFileSystem().getTree().find("/usuarios/proyectos/plan_final.txt").isFound(),
                "el archivo pasa a llamarse plan_final.txt tras terminar el proceso UPDATE");
    }

    private static void rechazosModoUsuarioSinInterrumpir() throws Exception {
        titulo("G14 + G15 - Rechazos en modo usuario con razon clara en etiqueta/log sin detener simulacion");
        EntornoGui e = new EntornoGui();
        e.panelModo.cambiarAUsuario("user2");

        // 1. user2 intenta eliminar archivo de otro usuario (/usuarios/notas.txt es de user1)
        e.panelFs.selectPath("/usuarios/notas.txt");
        PCB intento1 = e.panelAcciones.solicitarEliminarSeleccionado();
        check(intento1 == null && e.panelModo.getTextoAviso().contains("otro usuario"),
                "rechaza eliminar archivo de otro usuario con mensaje claro");

        // 2. user2 intenta leer archivo privado de user1
        PCB intento2 = e.panelAcciones.solicitarLeerSeleccionado();
        check(intento2 == null && e.panelModo.getTextoAviso().contains("privado"),
                "rechaza leer archivo privado de otro usuario con mensaje claro");

        // 3. user2 intenta renombrar archivo de /sistema
        e.panelFs.selectPath("/sistema/boot.bin");
        PCB intento3 = e.panelAcciones.solicitarRenombrarSeleccionado("hack.bin");
        check(intento3 == null && e.panelModo.getTextoAviso().contains("sistema"),
                "rechaza renombrar archivo de /sistema con mensaje claro");

        // 4. user2 intenta crear un directorio (solo admin)
        e.panelFs.selectPath("/usuarios");
        PCB intento4 = e.panelAcciones.solicitarCrearDirectorio("carpeta", Permission.PUBLIC);
        check(intento4 == null && e.panelModo.getTextoAviso().contains("administrador"),
                "rechaza crear directorio en modo usuario");

        // 5. user2 intenta accion de admin (cambio de politica)
        check(!e.panelModo.verificarPermisoAdmin("cambiar la politica de disco"),
                "G15: rechaza intento de cambiar politica en modo usuario");

        // 6. Verificar que ningun PCB invalido se encolo y todos los rechazos quedaron en LogCategory.PERMISO
        check(e.comp.getNewSnapshot().isEmpty(),
                "ninguna operacion rechazada crea procesos ni interrumpe la simulacion");
        String logPerm = e.cluster.getLogger().getLastAsText(20, LogCategory.PERMISO);
        check(logPerm.contains("otro usuario") && logPerm.contains("privado") && logPerm.contains("sistema"),
                "todos los motivos de rechazo quedan registrados en EventLogger bajo LogCategory.PERMISO");

        // 7. La simulacion sigue funcionando: user2 SI puede leer un archivo publico (/sistema/script.py)
        e.panelFs.selectPath("/sistema/script.py");
        PCB permitido = e.panelAcciones.solicitarLeerSeleccionado();
        e.correrHastaTerminar(1);
        check(permitido != null && permitido.getState() == ProcessState.TERMINATED,
                "la simulacion continua normal y user2 completa una lectura publica permitida");
    }

    private static void cargaAmbosJsonConfigVisibleYReinicio() throws Exception {
        titulo("J08 + J09 + J11 - Exigir ambos JSON, mostrar 4 valores nuevos y reiniciar conservando disco");
        PanelCargaJson panel = new PanelCargaJson();

        // J08: sin archivos, Iniciar deshabilitado con mensaje claro
        check(!panel.getBtnIniciar().isEnabled() && panel.getTextoEstado().contains("ambos"),
                "J08: sin JSON cargados, Iniciar esta deshabilitado y explica que faltan ambos");

        // Cargar solo FS: Iniciar sigue deshabilitado avisando que faltan procesos
        check(panel.cargarFsDesdeTexto(JSON_FS_ENUNCIADO), "carga JSON del sistema de archivos");
        check(!panel.getBtnIniciar().isEnabled() && panel.getTextoEstado().contains("procesos"),
                "J08: con solo FS, Iniciar sigue deshabilitado y avisa que falta el JSON de procesos");

        // Cargar procesos: Iniciar se habilita y J11 muestra los 4 valores nuevos
        check(panel.cargarProcesosDesdeTexto(JSON_PROCESOS_ENUNCIADO), "carga JSON de procesos y configuracion");
        check(panel.getBtnIniciar().isEnabled(), "J08: con ambos JSON validos, Iniciar queda habilitado");
        check(panel.getTextoTotalBloques().contains("200")
                && panel.getTextoSwap().contains("100")
                && panel.getTextoPoliticaDisco().contains("FIFO")
                && panel.getTextoCabezal().contains("50"),
                "J11: los 4 valores nuevos (200 bloques, swap 100, FIFO, cabezal 50) son visibles en la GUI");

        // Iniciar primera corrida y modificar el disco (eliminar img1.png via proceso)
        SimulationSetup.SimulationBundle primera = panel.iniciarSimulacion();
        check(primera != null && primera.getInitialProcesses().size() == 2,
                "J08: iniciarSimulacion construye el bundle listo para correr");

        SecuredFileSystem sfs1 = new SecuredFileSystem(
                primera.getFileSystemData().getFileSystem(),
                new PermissionMatrix(primera.getFileSystemData().getFileTree())
        );
        DiskIoCoordinator coord1 = new DiskIoCoordinator(
                primera.getComputer(), primera.getDisk(), primera.getDiskScheduler(),
                sfs1, new DiskRecordLog(), primera.getCluster().getLogger()
        );
        coord1.submitAll(primera.getInitialProcesses());
        long ciclo = 0;
        int limite = 1000;
        while (primera.getComputer().getFinishedSnapshot().size() < 2 && limite-- > 0) {
            ciclo++;
            primera.getComputer().onTick(ciclo);
            coord1.onTick(ciclo);
        }
        check(!primera.getFileSystemData().getFileTree().find("/usuarios/img1.png").isFound(),
                "J09: en la primera corrida el proceso DELETE elimino /usuarios/img1.png del disco");

        // Guardar FS modificado a archivo temporal y reiniciar con nuevo lote de procesos
        Path archivoGuardado = Files.createTempFile("avilaos_gui_fs_", ".json");
        try {
            int headFinal = Math.min(primera.getDisk().getHead().getPosition(),
                    primera.getFileSystemData().getTotalBlocks() - 1);
            check(panel.guardarFsEnArchivo(
                    primera.getFileSystemData().getFileTree(),
                    primera.getFileSystemData().getAllocator(),
                    primera.getFileSystemData().getTotalBlocks(),
                    headFinal,
                    archivoGuardado.toString()),
                    "J09: guarda el estado modificado del FS en un JSON valido");

            String nuevoLote = """
                    {
                      "globalConfig": { "cycleDurationMs": 150, "swapSizeBlocks": 80, "initialDiskPolicy": "SSTF" },
                      "computer": { "ramCapacity": 512, "policy": "EDF", "quantum": 0 },
                      "processes": [
                        { "name": "LeerBoot", "memory": 16, "deadline": 200, "instructions": 2, "targetPath": "/sistema/boot.bin", "operation": "READ" }
                      ]
                    }
                    """;
            SimulationSetup.SimulationBundle segunda = panel.reiniciarConservandoDisco(
                    primera.getFileSystemData().getFileTree(),
                    primera.getFileSystemData().getAllocator(),
                    primera.getFileSystemData().getTotalBlocks(),
                    headFinal,
                    nuevoLote,
                    true
            );
            check(segunda != null
                    && !segunda.getFileSystemData().getFileTree().find("/usuarios/img1.png").isFound()
                    && segunda.getDiskScheduler().getPolicy().getType() == DiskSchedulingPolicy.SSTF
                    && panel.getTextoPoliticaDisco().contains("SSTF")
                    && panel.getTextoSwap().contains("80"),
                    "J09 + J11: el reinicio conserva la eliminacion de img1.png y actualiza la GUI a SSTF y swap 80");
        } finally {
            Files.deleteIfExists(archivoGuardado);
        }
    }
}
