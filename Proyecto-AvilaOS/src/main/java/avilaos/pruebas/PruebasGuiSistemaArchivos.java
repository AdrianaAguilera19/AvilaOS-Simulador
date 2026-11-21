package avilaos.pruebas;

import avilaos.enums.DiskSchedulingPolicy;
import avilaos.enums.HeadDirection;
import avilaos.enums.Permission;
import avilaos.enums.SchedulingAlgorithm;
import avilaos.gui.ModeloArbolArchivos;
import avilaos.gui.ModeloTablaAsignacion;
import avilaos.gui.PanelSistemaArchivos;
import avilaos.model.hardware.Cluster;
import avilaos.model.hardware.Computer;
import avilaos.model.hardware.Disk;
import avilaos.model.process.PCB;
import avilaos.os.disk.DiskQueue;
import avilaos.os.disk.DiskRecordLog;
import avilaos.os.disk.DiskScheduler;
import avilaos.os.disk.policy.DiskPolicyFactory;
import avilaos.os.filesystem.ActiveUser;
import avilaos.os.filesystem.DiskIoCoordinator;
import avilaos.os.filesystem.FileSnapshot;
import avilaos.os.filesystem.PermissionMatrix;
import avilaos.os.filesystem.SecuredFileSystem;
import avilaos.persistence.FileSystemData;
import avilaos.persistence.FileSystemJsonManager;
import avilaos.scheduler.PolicyFactory;

/**
 * Pruebas de los componentes GUI del sistema de archivos (G02, G03, G06 y G07):
 * - ModeloArbolArchivos (TreeModel propio sin DefaultMutableTreeNode/Vector)
 * - Panel de informacion de nodo (5 datos en archivo y directorio)
 * - ModeloTablaAsignacion (AbstractTableModel propio con 5 columnas + color)
 * - Actualizacion en tiempo real tras operaciones CRUD ejecutadas por procesos de E/S
 */
public class PruebasGuiSistemaArchivos {

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
        modeloArbolJerarquiaEnunciado();
        panelDetalleArchivoYDirectorio();
        tablaAsignacionColumnasYDatos();
        actualizacionTiempoRealTrasCrud();

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

    private static SecuredFileSystem cargarFsEnunciado() throws Exception {
        FileSystemData data = FileSystemJsonManager.parseJson(JSON_FS_ENUNCIADO);
        return new SecuredFileSystem(data.getFileSystem(), new PermissionMatrix(data.getFileTree()));
    }

    private static void modeloArbolJerarquiaEnunciado() throws Exception {
        titulo("G02 - ModeloArbolArchivos (TreeModel propio) con jerarquia del enunciado");
        SecuredFileSystem sfs = cargarFsEnunciado();
        ModeloArbolArchivos modelo = new ModeloArbolArchivos(sfs.snapshot());

        Object raiz = modelo.getRoot();
        check(raiz instanceof ModeloArbolArchivos.NodoArbol, "la raiz es un NodoArbol propio");
        check("/".equals(raiz.toString()), "la raiz se muestra como /");
        check(modelo.getChildCount(raiz) == 3, "la raiz tiene 3 hijos (/mbr.bin, /sistema, /usuarios)");
        check(!modelo.isLeaf(raiz), "la raiz no es hoja");

        ModeloArbolArchivos.NodoArbol mbr = modelo.buscarPorRuta("/mbr.bin");
        ModeloArbolArchivos.NodoArbol sistema = modelo.buscarPorRuta("/sistema");
        ModeloArbolArchivos.NodoArbol usuarios = modelo.buscarPorRuta("/usuarios");
        check(mbr != null && modelo.isLeaf(mbr), "/mbr.bin es archivo (hoja)");
        check(sistema != null && !modelo.isLeaf(sistema) && modelo.getChildCount(sistema) == 4,
                "/sistema es directorio con 4 archivos");
        check(usuarios != null && !modelo.isLeaf(usuarios) && modelo.getChildCount(usuarios) == 4,
                "/usuarios es directorio con 4 archivos");
        check(!modelo.actualizar(sfs.snapshot()),
                "actualizar con la misma foto no dispara recarga innecesaria");
    }

    private static void panelDetalleArchivoYDirectorio() throws Exception {
        titulo("G03 - Panel de informacion muestra los 5 datos en archivo y directorio");
        SecuredFileSystem sfs = cargarFsEnunciado();
        PanelSistemaArchivos panel = new PanelSistemaArchivos(sfs);

        check(panel.selectPath("/usuarios/notas.txt"), "selecciona /usuarios/notas.txt en el JTree");
        check("notas.txt".equals(panel.getDetalleNombre()), "archivo: nombre = notas.txt");
        check(panel.getDetalleBloques().contains("28"), "archivo: tamano = 28 bloques");
        check("user1".equals(panel.getDetalleDueno()), "archivo: dueno = user1");
        check("PRIVATE".equals(panel.getDetallePermisos()), "archivo: permiso = PRIVATE");
        check("34".equals(panel.getDetallePrimerBloque()), "archivo: primer bloque = 34");

        check(panel.selectPath("/sistema"), "selecciona el directorio /sistema en el JTree");
        check("sistema".equals(panel.getDetalleNombre()) && panel.getDetalleBloques().contains("0"),
                "directorio: nombre = sistema y tamano = 0 (directorio)");
        check("admin".equals(panel.getDetalleDueno()) && "PUBLIC".equals(panel.getDetallePermisos()),
                "directorio: dueno = admin y permiso = PUBLIC");
        check("-".equals(panel.getDetallePrimerBloque()), "directorio: primer bloque = -");
    }

    private static void tablaAsignacionColumnasYDatos() throws Exception {
        titulo("G06 - JTable de asignacion con modelo propio (5 columnas + color)");
        SecuredFileSystem sfs = cargarFsEnunciado();
        PanelSistemaArchivos panel = new PanelSistemaArchivos(sfs);
        ModeloTablaAsignacion tabla = panel.getModeloTabla();

        check(tabla.getColumnCount() == 6, "la tabla tiene 6 columnas (Color + 5 columnas pedidas)");
        check("Color".equals(tabla.getColumnName(0)) && "Nombre".equals(tabla.getColumnName(1))
                && "Bloques".equals(tabla.getColumnName(2)) && "Primer bloque".equals(tabla.getColumnName(3))
                && "Dueno".equals(tabla.getColumnName(4)) && "Proceso creador".equals(tabla.getColumnName(5)),
                "los encabezados coinciden con los pedidos en el enunciado");
        check(tabla.getRowCount() == 9, "muestra exactamente los 9 archivos del JSON (sin directorios)");

        // Buscar notas.txt en la tabla
        int filaNotas = -1;
        for (int i = 0; i < tabla.getRowCount(); i++) {
            if ("notas.txt".equals(tabla.getValueAt(i, ModeloTablaAsignacion.COL_NOMBRE))) {
                filaNotas = i;
                break;
            }
        }
        check(filaNotas >= 0, "notas.txt aparece en la tabla de asignacion");
        String hex = String.valueOf(tabla.getValueAt(filaNotas, ModeloTablaAsignacion.COL_COLOR));
        check(hex.startsWith("#") && hex.length() == 7, "incluye codigo de color valido (#RRGGBB): " + hex);
        check(Integer.valueOf(28).equals(tabla.getValueAt(filaNotas, ModeloTablaAsignacion.COL_BLOQUES))
                && Integer.valueOf(34).equals(tabla.getValueAt(filaNotas, ModeloTablaAsignacion.COL_PRIMER_BLOQUE)),
                "bloques=28 y primer bloque=34 para notas.txt");
        check("user1".equals(tabla.getValueAt(filaNotas, ModeloTablaAsignacion.COL_DUENO))
                && "JSON/carga".equals(tabla.getValueAt(filaNotas, ModeloTablaAsignacion.COL_CREADOR)),
                "dueno=user1 y creador=JSON/carga para archivo inicial");
    }

    private static void actualizacionTiempoRealTrasCrud() throws Exception {
        titulo("G02 + G07 - JTree y tabla se actualizan en tiempo real tras CRUD por procesos de E/S");
        FileSystemData fsData = FileSystemJsonManager.parseJson(JSON_FS_ENUNCIADO);
        Cluster cluster = new Cluster(100, 0);
        Computer comp = cluster.addComputer("PC1", PolicyFactory.create(SchedulingAlgorithm.FCFS, 2), 1024);
        Disk disk = fsData.createDisk(100, HeadDirection.UP);
        DiskScheduler scheduler = new DiskScheduler(new DiskQueue(),
                DiskPolicyFactory.create(DiskSchedulingPolicy.FIFO), cluster);
        SecuredFileSystem sfs = new SecuredFileSystem(fsData.getFileSystem(),
                new PermissionMatrix(fsData.getFileTree()));
        DiskIoCoordinator coord = new DiskIoCoordinator(comp, disk, scheduler, sfs,
                new DiskRecordLog(), cluster.getLogger());

        PanelSistemaArchivos panel = new PanelSistemaArchivos(sfs);
        long ciclo = 0;

        // 1. DELETE /usuarios/img1.png mediante proceso de E/S (libera 4 bloques)
        coord.submitFileOperationProcess("P_Del", 1, 16, 2, 200, ciclo,
                "DELETE", "/usuarios/img1.png", 0, null, Permission.PUBLIC, ActiveUser.admin());
        while (comp.getFinishedSnapshot().size() < 1) {
            ciclo++;
            comp.onTick(ciclo);
            coord.onTick(ciclo);
        }
        panel.refresh();
        check(panel.getModeloTabla().getRowCount() == 8,
                "tras DELETE por proceso, la tabla baja de 9 a 8 archivos sin reiniciar");
        check(panel.getModeloArbol().buscarPorRuta("/usuarios/img1.png") == null,
                "tras DELETE, /usuarios/img1.png desaparece del JTree");

        // 2. CREATE /usuarios/reporte.txt (3 bloques) mediante proceso de E/S
        PCB pCrear = coord.submitFileOperationProcess("P_Crear", 1, 16, 2, 200, ciclo,
                "CREATE", "/usuarios/reporte.txt", 3, null, Permission.PUBLIC, ActiveUser.admin());
        while (comp.getFinishedSnapshot().size() < 2) {
            ciclo++;
            comp.onTick(ciclo);
            coord.onTick(ciclo);
        }
        panel.refresh();
        check(panel.getModeloTabla().getRowCount() == 9,
                "tras CREATE por proceso, la tabla vuelve a 9 archivos");
        check(panel.getModeloArbol().buscarPorRuta("/usuarios/reporte.txt") != null,
                "tras CREATE, /usuarios/reporte.txt aparece en el JTree");

        int filaReporte = -1;
        for (int i = 0; i < panel.getModeloTabla().getRowCount(); i++) {
            if ("reporte.txt".equals(panel.getModeloTabla().getValueAt(i, ModeloTablaAsignacion.COL_NOMBRE))) {
                filaReporte = i;
                break;
            }
        }
        check(filaReporte >= 0, "reporte.txt aparece como nueva fila en la tabla");
        String creadorEsperado = "P" + pCrear.getId();
        check(creadorEsperado.equals(panel.getModeloTabla().getValueAt(filaReporte, ModeloTablaAsignacion.COL_CREADOR)),
                "la tabla muestra el proceso creador exacto (" + creadorEsperado + ")");

        // 3. UPDATE /usuarios/reporte.txt -> informe.txt mediante proceso de E/S
        coord.submitFileOperationProcess("P_Ren", 1, 16, 2, 200, ciclo,
                "UPDATE", "/usuarios/reporte.txt", 0, "informe.txt", Permission.PUBLIC, ActiveUser.admin());
        while (comp.getFinishedSnapshot().size() < 3) {
            ciclo++;
            comp.onTick(ciclo);
            coord.onTick(ciclo);
        }
        panel.refresh();
        check(panel.getModeloArbol().buscarPorRuta("/usuarios/reporte.txt") == null
                && panel.getModeloArbol().buscarPorRuta("/usuarios/informe.txt") != null,
                "tras UPDATE, el JTree refleja el nuevo nombre /usuarios/informe.txt");

        boolean encontroInforme = false;
        for (int i = 0; i < panel.getModeloTabla().getRowCount(); i++) {
            if ("informe.txt".equals(panel.getModeloTabla().getValueAt(i, ModeloTablaAsignacion.COL_NOMBRE))) {
                encontroInforme = true;
                break;
            }
        }
        check(encontroInforme, "tras UPDATE, la tabla muestra informe.txt sin reiniciar");

        // Verificar que al seleccionar el archivo renombrado en el JTree el detalle se actualiza
        panel.selectPath("/usuarios/informe.txt");
        FileSnapshot sel = panel.getSelectedSnapshot();
        check(sel != null && "informe.txt".equals(panel.getDetalleNombre())
                && panel.getDetalleBloques().contains("3"),
                "al seleccionar el archivo renombrado el panel de detalle muestra informe.txt y 3 bloques");
    }
}
