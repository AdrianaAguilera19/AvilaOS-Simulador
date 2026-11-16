package avilaos.pruebas;

import avilaos.datastructures.BlockChain;
import avilaos.enums.Permission;
import avilaos.enums.UserMode;
import avilaos.model.hardware.BlockInfo;
import avilaos.model.hardware.BlockMap;
import avilaos.os.disk.DiskLimits;
import avilaos.os.filesystem.AccessDecision;
import avilaos.os.filesystem.ActiveUser;
import avilaos.os.filesystem.ChainAllocator;
import avilaos.os.filesystem.FileNode;
import avilaos.os.filesystem.FileSystem;
import avilaos.os.filesystem.FileTree;
import avilaos.os.filesystem.FsResult;
import avilaos.os.filesystem.PermissionMatrix;
import avilaos.os.filesystem.SecuredFileSystem;
import avilaos.os.filesystem.UserSession;

/**
 * Pruebas de permisos y modos de usuario (F10, F11 y F12) con el sistema de archivos de ejemplo del
 * enunciado. Se ejecuta como programa (main) y termina con "TODAS LAS PRUEBAS PASARON" o con el numero
 * de fallas (codigo de salida 1).
 */
public class PruebasPermisos {

    private static int fallas = 0;
    private static int verificaciones = 0;

    private static final ActiveUser ADMIN = ActiveUser.admin();
    private static final ActiveUser USER1 = ActiveUser.user("user1");
    private static final ActiveUser USER2 = ActiveUser.user("user2");

    public static void main(String[] args) {
        usuarioActivo();
        matrizLeer();
        matrizCrear();
        matrizActualizarYEliminar();
        archivosDelSistema();
        sistemaSeguro();
        rechazosNoCambianNada();

        System.out.println();
        if (fallas == 0) {
            System.out.println("TODAS LAS PRUEBAS PASARON (" + verificaciones + " verificaciones)");
        } else {
            System.out.println("HAY " + fallas + " FALLAS de " + verificaciones + " verificaciones");
            System.exit(1);
        }
    }

    // ------------------------------------------------------------------ utilidades

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

    private static boolean falla(Runnable accion) {
        try {
            accion.run();
        } catch (IllegalArgumentException e) {
            return true;
        }
        return false;
    }

    private static BlockMap map;

    private static FileNode archivo(FileSystem fs, String ruta, String dueno, Permission permiso, int bloques) {
        int barra = ruta.lastIndexOf('/');
        String padre = barra == 0 ? "/" : ruta.substring(0, barra);
        FileNode destino = fs.getTree().find(padre).getNode();
        BlockChain cadena = fs.getAllocator().request(bloques, ruta).getChain();
        FileNode nodo = FileNode.file(ruta.substring(barra + 1), dueno, permiso, cadena.first(), bloques);
        fs.getTree().add(destino, nodo);
        return nodo;
    }

    /** El sistema de archivos de ejemplo del enunciado (200 bloques, todos ocupados). */
    private static FileSystem ejemplo(int bloquesDeDisco) {
        map = DiskLimits.createMap(bloquesDeDisco);
        FileSystem fs = new FileSystem(new FileTree("admin"), new ChainAllocator(map), map);
        fs.createDirectory("/sistema", "admin", Permission.PUBLIC);
        fs.createDirectory("/usuarios", "admin", Permission.PUBLIC);
        archivo(fs, "/mbr.bin", "admin", Permission.PUBLIC, 11);
        archivo(fs, "/sistema/boot.bin", "admin", Permission.PUBLIC, 23);
        archivo(fs, "/usuarios/notas.txt", "user1", Permission.PRIVATE, 28);
        archivo(fs, "/sistema/script.py", "admin", Permission.PUBLIC, 2);
        archivo(fs, "/sistema/core.dll", "admin", Permission.PUBLIC, 31);
        archivo(fs, "/sistema/config.sys", "admin", Permission.PUBLIC, 24);
        archivo(fs, "/usuarios/img1.png", "user1", Permission.PRIVATE, 4);
        archivo(fs, "/usuarios/data.csv", "user2", Permission.PUBLIC, 57);
        archivo(fs, "/usuarios/video.mp4", "user1", Permission.PUBLIC, 20);
        return fs;
    }

    private static FileNode nodo(FileSystem fs, String ruta) {
        return fs.getTree().find(ruta).getNode();
    }

    private static String fotoMapa() {
        BlockInfo[] todos = map.snapshot();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < todos.length; i++) {
            sb.append(todos[i].toString()).append('\n');
        }
        return sb.toString();
    }

    // ------------------------------------------------------------------ pruebas

    private static void usuarioActivo() {
        titulo("F12: usuario activo");
        check(ADMIN.isAdmin() && ADMIN.getName().equals("admin") && ADMIN.getMode() == UserMode.ADMIN,
                "el administrador se llama admin y tiene modo ADMIN");
        check(!USER1.isAdmin() && USER1.getMode() == UserMode.USER && USER1.getName().equals("user1"),
                "un usuario tiene su nombre y modo USER");
        check(USER1.toString().equals("user1 (USER)"), "se muestra como «user1 (USER)»");
        check(ActiveUser.user("  user3  ").getName().equals("user3"), "recorta los espacios del nombre");
        check(falla(() -> ActiveUser.user("   ")), "rechaza un nombre vacio");
        check(falla(() -> ActiveUser.user(null)), "rechaza un nombre nulo");
        check(falla(() -> ActiveUser.user("a/b")), "rechaza un nombre con /");
        check(falla(() -> new ActiveUser("user1", null)), "rechaza un modo nulo");
        check(falla(() -> ActiveUser.user("admin")) && falla(() -> ActiveUser.user("ADMIN")),
                "un usuario no puede llamarse admin (esta reservado)");
        UserSession sesion = new UserSession();
        check(sesion.getCurrent().isAdmin(), "la sesion empieza en modo administrador");
        sesion.switchTo(USER2);
        check(sesion.getCurrent() == USER2, "se puede cambiar de usuario en cualquier momento");
        check(falla(() -> sesion.switchTo(null)), "no se puede dejar la sesion sin usuario");
    }

    private static void matrizLeer() {
        titulo("F10: leer");
        FileSystem fs = ejemplo(200);
        PermissionMatrix m = new PermissionMatrix(fs.getTree());
        FileNode notas = nodo(fs, "/usuarios/notas.txt");     // de user1, privado
        FileNode data = nodo(fs, "/usuarios/data.csv");       // de user2, publico
        FileNode core = nodo(fs, "/sistema/core.dll");        // de admin, publico
        check(m.canRead(ADMIN, notas).isAllowed(), "el administrador lee un archivo privado de otro");
        check(m.canRead(USER1, notas).isAllowed(), "un usuario lee su propio archivo privado");
        AccessDecision no = m.canRead(USER2, notas);
        check(!no.isAllowed(), "un usuario NO lee el archivo privado de otro");
        check(no.getReason().contains("privado") && no.getReason().contains("user1") && no.getReason().contains("user2"),
                "el motivo dice que es privado, de quien es y quien intento leerlo");
        check(m.canRead(USER2, data).isAllowed(), "un usuario lee su propio archivo publico");
        check(m.canRead(USER1, data).isAllowed(), "un usuario lee el archivo publico de otro");
        check(m.canRead(USER1, core).isAllowed(), "un usuario lee archivos publicos del sistema");
        check(m.canRead(USER2, nodo(fs, "/usuarios")).isAllowed(), "cualquiera puede ver un directorio (la estructura es libre)");
        check(!m.canRead(null, notas).isAllowed(), "sin usuario activo no se lee nada");
        check(!m.canRead(USER1, null).isAllowed(), "un archivo inexistente se rechaza con mensaje");
    }

    private static void matrizCrear() {
        titulo("F10: crear");
        FileSystem fs = ejemplo(200);
        SecuredFileSystem sfs = new SecuredFileSystem(fs, new PermissionMatrix(fs.getTree()));
        PermissionMatrix m = new PermissionMatrix(fs.getTree());
        sfs.createDirectory(ADMIN, "/usuarios/user1", Permission.PRIVATE);
        FileNode usuarios = nodo(fs, "/usuarios");
        FileNode propio = nodo(fs, "/usuarios/user1");
        check(propio.getOwner().equals("admin"), "el directorio lo creo el administrador, asi que es suyo");
        check(m.canCreateDirectory(ADMIN, usuarios).isAllowed(), "el administrador crea directorios");
        AccessDecision dir = m.canCreateDirectory(USER1, usuarios);
        check(!dir.isAllowed() && dir.getReason().contains("administrador"), "un usuario NO crea directorios, y se le explica");
        check(m.canCreateFile(ADMIN, nodo(fs, "/sistema")).isAllowed(), "el administrador crea archivos en cualquier directorio");
        AccessDecision ajeno = m.canCreateFile(USER1, usuarios);
        check(!ajeno.isAllowed() && ajeno.getReason().contains("propios directorios"),
                "un usuario NO crea archivos en un directorio que no es suyo");
        check(!m.canCreateFile(USER1, nodo(fs, "/sistema")).isAllowed(), "tampoco en /sistema");
        check(!m.canCreateFile(USER1, nodo(fs, "/mbr.bin")).isAllowed(), "no se crea un archivo dentro de otro archivo");
        // el administrador crea un directorio cuyo dueno es user1: ahi si puede crear
        FileNode delUser1 = FileNode.directory("user1b", "user1", Permission.PRIVATE);
        fs.getTree().add(usuarios, delUser1);
        check(m.canCreateFile(USER1, delUser1).isAllowed(), "un usuario crea archivos en un directorio del que es dueno");
        check(!m.canCreateFile(USER2, delUser1).isAllowed(), "otro usuario no puede crear ahi");
    }

    private static void matrizActualizarYEliminar() {
        titulo("F10: actualizar y eliminar");
        FileSystem fs = ejemplo(200);
        PermissionMatrix m = new PermissionMatrix(fs.getTree());
        FileNode img = nodo(fs, "/usuarios/img1.png");        // de user1
        FileNode data = nodo(fs, "/usuarios/data.csv");       // de user2
        FileNode usuarios = nodo(fs, "/usuarios");
        check(m.canUpdate(ADMIN, img).isAllowed() && m.canDelete(ADMIN, img).isAllowed(),
                "el administrador renombra y elimina archivos de cualquiera");
        check(m.canUpdate(ADMIN, usuarios).isAllowed() && m.canDelete(ADMIN, usuarios).isAllowed(),
                "y directorios completos");
        check(m.canUpdate(USER1, img).isAllowed() && m.canDelete(USER1, img).isAllowed(),
                "un usuario renombra y elimina sus propios archivos");
        AccessDecision ajeno = m.canDelete(USER1, data);
        check(!ajeno.isAllowed() && ajeno.getReason().contains("user2"), "un usuario NO elimina archivos de otro (el motivo nombra al dueno)");
        check(!m.canUpdate(USER1, data).isAllowed(), "ni los renombra");
        check(!m.canUpdate(USER1, usuarios).isAllowed() && !m.canDelete(USER1, usuarios).isAllowed(),
                "un usuario no renombra ni elimina directorios");
        check(!m.canDelete(null, img).isAllowed(), "sin usuario activo no se elimina nada");
    }

    private static void archivosDelSistema() {
        titulo("F11: los archivos del sistema estan protegidos");
        FileSystem fs = ejemplo(200);
        PermissionMatrix m = new PermissionMatrix(fs.getTree());
        FileNode boot = nodo(fs, "/sistema/boot.bin");
        check(!m.canUpdate(USER1, boot).isAllowed() && !m.canDelete(USER1, boot).isAllowed(),
                "en modo usuario no se renombra ni elimina /sistema/boot.bin");
        check(m.canDelete(USER1, boot).getReason().contains("sistema"), "el motivo dice que es un archivo del sistema");
        check(!m.canDelete(USER2, nodo(fs, "/sistema")).isAllowed(), "tampoco el directorio /sistema");
        check(m.canUpdate(ADMIN, boot).isAllowed() && m.canDelete(ADMIN, boot).isAllowed(),
                "el administrador si puede");
        // aunque el archivo sea de user1, estando en /sistema sigue protegido
        FileNode colado = FileNode.file("colado.bin", "user1", Permission.PUBLIC, 0, 1);
        fs.getTree().add(nodo(fs, "/sistema"), colado);
        check(!m.canDelete(USER1, colado).isAllowed(), "un archivo de user1 dentro de /sistema tambien queda protegido");
        check(m.canRead(USER2, boot).isAllowed(), "pero los archivos publicos del sistema si se pueden leer");
    }

    private static void sistemaSeguro() {
        titulo("SecuredFileSystem: las operaciones pasan por la autorizacion");
        FileSystem fs = ejemplo(200);
        SecuredFileSystem sfs = new SecuredFileSystem(fs, new PermissionMatrix(fs.getTree()));

        FsResult leer = sfs.read(USER2, "/usuarios/notas.txt");
        check(!leer.isOk() && leer.getMessage().startsWith("Permiso denegado"), "user2 no puede leer notas.txt de user1");
        check(sfs.read(USER1, "/usuarios/notas.txt").getBlocks() == 28, "user1 lee su archivo completo (28 bloques)");
        check(sfs.read(USER1, "/usuarios/data.csv").getBlocks() == 57, "user1 lee un archivo publico de user2");
        check(!sfs.read(USER1, "/no/existe").isOk(), "una ruta inexistente se rechaza con el mensaje de la ruta");

        FsResult borrar = sfs.delete(USER1, "/usuarios/img1.png");
        check(borrar.isOk() && borrar.getBlocks() == 4 && map.freeCount() == 4, "user1 elimina su img1.png y se liberan sus 4 bloques");
        check(!sfs.delete(USER1, "/sistema/boot.bin").isOk(), "user1 no puede eliminar boot.bin");
        check(!sfs.delete(USER1, "/usuarios").isOk(), "user1 no puede eliminar un directorio");
        check(sfs.delete(ADMIN, "/usuarios").getBlocks() == 105, "el administrador elimina /usuarios (28 + 57 + 20 = 105 bloques que quedaban)");

        FsResult renombrar = sfs.rename(USER2, "/sistema/core.dll", "x.dll");
        check(!renombrar.isOk() && fs.getTree().find("/sistema/core.dll").isFound(), "user2 no puede renombrar un archivo del sistema");
        check(sfs.rename(ADMIN, "/sistema/core.dll", "nucleo.dll").isOk(), "el administrador si");

        check(sfs.createDirectory(ADMIN, "/usuarios", Permission.PUBLIC).isOk(), "el administrador crea /usuarios otra vez");
        check(!sfs.createDirectory(USER1, "/usuarios/mio", Permission.PRIVATE).isOk(), "user1 no puede crear directorios");
        check(sfs.createDirectory(ADMIN, "/usuarios/user1", Permission.PRIVATE).isOk(), "el administrador crea /usuarios/user1");
        FsResult sinBloques = sfs.createFile(ADMIN, "/usuarios", "grande.bin", 500, Permission.PUBLIC);
        check(!sinBloques.isOk(), "crear un archivo sin bloques suficientes se rechaza con mensaje");
    }

    private static void rechazosNoCambianNada() {
        titulo("Un rechazo no cambia nada");
        FileSystem fs = ejemplo(200);
        SecuredFileSystem sfs = new SecuredFileSystem(fs, new PermissionMatrix(fs.getTree()));
        String mapaAntes = fotoMapa();
        int nodosAntes = fs.getTree().countNodes();
        sfs.read(USER2, "/usuarios/notas.txt");
        sfs.delete(USER2, "/usuarios/notas.txt");
        sfs.delete(USER1, "/sistema/core.dll");
        sfs.rename(USER2, "/usuarios/video.mp4", "otro.mp4");
        sfs.createDirectory(USER1, "/usuarios/nuevo", Permission.PUBLIC);
        sfs.createFile(USER1, "/usuarios", "nuevo.txt", 1, Permission.PUBLIC);
        sfs.delete(null, "/usuarios/notas.txt");
        check(fotoMapa().equals(mapaAntes), "tras 7 operaciones rechazadas el mapa de bloques es identico");
        check(fs.getTree().countNodes() == nodosAntes && fs.getTree().find("/usuarios/video.mp4").isFound(),
                "y el arbol tambien (mismo numero de nodos, mismos nombres)");
        check(map.usedCount() == fs.getTree().usedBlocks(), "el arbol y el disco siguen coincidiendo");

        // user1 con un directorio propio: crea, lee y ve el rechazo cuando el disco esta lleno
        FileSystem libre = ejemplo(300);
        SecuredFileSystem s2 = new SecuredFileSystem(libre, new PermissionMatrix(libre.getTree()));
        libre.getTree().add(libre.getTree().find("/usuarios").getNode(),
                FileNode.directory("user1b", "user1", Permission.PRIVATE));
        FsResult crea = s2.createFile(USER1, "/usuarios/user1b", "tarea.doc", 5, Permission.PRIVATE);
        check(crea.isOk() && crea.getNode().getOwner().equals("user1"), "user1 crea un archivo en su directorio y queda como dueno");
        check(s2.read(USER1, "/usuarios/user1b/tarea.doc").getBlocks() == 5, "y lo puede leer");
        check(!s2.read(USER2, "/usuarios/user1b/tarea.doc").isOk(), "con permiso privado, otro usuario no lo lee");
        check(!s2.createFile(USER1, "/usuarios/user1b", "tarea.doc", 5, Permission.PRIVATE).isOk(), "no se repite un nombre");
        check(!s2.createFile(USER2, "/usuarios/user1b", "otro.doc", 5, Permission.PRIVATE).isOk(), "user2 no crea en el directorio de user1");
        check(s2.createFile(ADMIN, "/sistema", "nuevo.sys", 3, Permission.PUBLIC).getNode().getOwner().equals("admin"),
                "el administrador crea en /sistema y el dueno es admin");
    }
}