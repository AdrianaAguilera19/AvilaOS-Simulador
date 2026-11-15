/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package avilaos.pruebas;

import avilaos.enums.NodeType;
import avilaos.enums.Permission;
import avilaos.os.filesystem.FileNode;
import avilaos.datastructures.DynamicArray;
import avilaos.os.filesystem.FileTree;

/**
 * Pruebas del sistema de archivos del P2. Se ejecuta como programa (main) y
 * termina con "TODAS LAS PRUEBAS PASARON" o con el numero de fallas (codigo de salida 1).
 */
public class PruebasArchivos {

    private static int fallas = 0;
    private static int verificaciones = 0;

    public static void main(String[] args) {
        modeloDeNodo();
        arbolDeArchivos();

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

    private static boolean falla(Runnable accion) {
        try {
            accion.run();
        } catch (IllegalArgumentException | IllegalStateException e) {
            return true;
        }
        return false;
    }

    // ------------------------------------------------------------------ F01

    private static void modeloDeNodo() {
        titulo("F01 - FileNode (modelo de nodo)");
        FileNode raiz = FileNode.root("admin");
        FileNode sistema = FileNode.directory("sistema", "admin", Permission.PUBLIC);
        FileNode boot = FileNode.file("boot.bin", "admin", Permission.PRIVATE, 5, 4);
        raiz.addChild(sistema);
        sistema.addChild(boot);

        check(raiz.getName().equals("/") && raiz.isRoot() && raiz.isDirectory(), "la raiz se llama / y es directorio");
        check(boot.getName().equals("boot.bin") && boot.getType() == NodeType.FILE && boot.isFile(), "archivo con nombre y tipo");
        check(boot.getOwner().equals("admin") && boot.getPermission() == Permission.PRIVATE, "archivo con dueno y permiso");
        check(boot.getFirstBlock() == 5 && boot.getBlockCount() == 4, "archivo con primer bloque y cantidad de bloques");
        check(sistema.getFirstBlock() == FileNode.NO_BLOCK && sistema.getBlockCount() == 0, "el directorio no tiene bloques");
        check(boot.getParent() == sistema && sistema.getParent() == raiz && raiz.getParent() == null, "cada nodo conoce a su padre");
        check(raiz.getChildCount() == 1 && raiz.getChild(0) == sistema && raiz.getChild(1) == null, "el padre conoce a sus hijos");
        check(raiz.findChild("sistema") == sistema && raiz.findChild("nada") == null, "busca un hijo por nombre");
        check(boot.toString().equals("boot.bin"), "toString da el nombre (lo que muestra el JTree)");

        check(falla(() -> boot.addChild(FileNode.directory("x", "admin", Permission.PUBLIC))), "un archivo no puede tener hijos");
        check(falla(() -> raiz.addChild(FileNode.directory("sistema", "admin", Permission.PUBLIC))), "no se repite un nombre en el mismo directorio");
        check(falla(() -> sistema.addChild(raiz)), "un directorio no puede colgar de su propio contenido");
        check(falla(() -> raiz.addChild(sistema)), "un nodo con padre no se agrega a otro");

        check(falla(() -> FileNode.directory("a/b", "admin", Permission.PUBLIC)), "el nombre no puede llevar la barra /");
        check(falla(() -> FileNode.directory("  ", "admin", Permission.PUBLIC)), "el nombre no puede estar vacio");
        check(falla(() -> FileNode.file("a.txt", "admin", Permission.PUBLIC, 0, 0)), "un archivo ocupa al menos 1 bloque");
        check(falla(() -> FileNode.file("a.txt", "admin", Permission.PUBLIC, -3, 2)), "el primer bloque no puede ser negativo");
        check(falla(() -> FileNode.file("a.txt", "admin", null, 0, 2)), "el permiso no puede ser nulo");
        check(falla(() -> FileNode.file("a.txt", " ", Permission.PUBLIC, 0, 2)), "el dueno no puede estar vacio");

        FileNode notas = FileNode.file("notas.txt", "user1", Permission.PUBLIC, 10, 2);
        sistema.addChild(notas);
        notas.rename("apuntes.txt");
        check(notas.getName().equals("apuntes.txt") && sistema.findChild("apuntes.txt") == notas, "renombra un nodo");
        check(falla(() -> notas.rename("boot.bin")), "no renombra a un nombre que ya existe en el directorio");
        check(falla(() -> raiz.rename("otra")), "la raiz no se renombra");
        notas.setPermission(Permission.PRIVATE);
        check(notas.getPermission() == Permission.PRIVATE, "cambia el permiso");

        check(sistema.removeChild(boot) && boot.getParent() == null && sistema.getChildCount() == 1, "quita un hijo y queda sin padre");
        check(!sistema.removeChild(boot), "no quita dos veces el mismo hijo");
    }
    
    // ------------------------------------------------------------------ F02

    private static void arbolDeArchivos() {
        titulo("F02 - FileTree (arbol con raiz /)");
        FileTree arbol = new FileTree("admin");
        FileNode raiz = arbol.getRoot();
        check(raiz.getName().equals("/") && raiz.isRoot() && arbol.countNodes() == 1, "el arbol nuevo solo tiene la raiz /");

        FileNode sistema = FileNode.directory("sistema", "admin", Permission.PUBLIC);
        FileNode boot = FileNode.file("boot.bin", "admin", Permission.PRIVATE, 0, 4);
        FileNode usuarios = FileNode.directory("usuarios", "admin", Permission.PUBLIC);
        FileNode user1 = FileNode.directory("user1", "user1", Permission.PUBLIC);
        FileNode notas = FileNode.file("notas.txt", "user1", Permission.PUBLIC, 10, 3);
        FileNode img = FileNode.file("img1.png", "user1", Permission.PRIVATE, 20, 5);
        FileNode user2 = FileNode.directory("user2", "user2", Permission.PRIVATE);
        FileNode datos = FileNode.file("data.csv", "user2", Permission.PRIVATE, 30, 2);
        arbol.add(raiz, sistema);
        arbol.add(sistema, boot);
        arbol.add(raiz, usuarios);
        arbol.add(usuarios, user1);
        arbol.add(user1, notas);
        arbol.add(user1, img);
        arbol.add(usuarios, user2);
        arbol.add(user2, datos);

        check(arbol.countNodes() == 9, "arma el arbol de ejemplo: 9 nodos");
        check(arbol.countFiles() == 4 && arbol.countDirectories() == 5, "4 archivos y 5 directorios");
        check(arbol.usedBlocks() == 14, "los archivos ocupan 14 bloques en total");
        check(notas.getParent() == user1 && user1.getParent() == usuarios && usuarios.getParent() == raiz, "cada nodo conoce a su padre");
        check(raiz.getChildCount() == 2 && usuarios.getChildCount() == 2, "los directorios conocen a sus hijos");

        DynamicArray<FileNode> orden = arbol.allNodes();
        check(orden.size() == 9 && orden.get(0) == raiz, "el recorrido empieza en la raiz");
        check(orden.indexOf(usuarios) < orden.indexOf(user1) && orden.indexOf(user1) < orden.indexOf(notas),
                "cada directorio va antes que su contenido (preorden)");
        check(orden.get(1) == sistema && orden.get(2) == boot, "los hermanos salen en el orden en que se agregaron");

        check(arbol.contains(datos) && arbol.contains(raiz), "reconoce los nodos del arbol");
        FileNode suelto = FileNode.directory("suelto", "admin", Permission.PUBLIC);
        check(!arbol.contains(suelto) && !arbol.contains(null), "no reconoce un nodo que no cuelga de la raiz");
        check(falla(() -> arbol.add(suelto, FileNode.directory("x", "admin", Permission.PUBLIC))), "no agrega dentro de un directorio de afuera");
        check(falla(() -> arbol.add(null, FileNode.directory("y", "admin", Permission.PUBLIC))), "no agrega si el destino es nulo");
        check(falla(() -> arbol.add(boot, FileNode.directory("z", "admin", Permission.PUBLIC))), "no agrega dentro de un archivo");
        check(falla(() -> arbol.add(raiz, FileNode.directory("sistema", "admin", Permission.PUBLIC))), "no repite un nombre en el mismo directorio");
        check(arbol.countNodes() == 9, "los intentos rechazados no cambian el arbol");
    }
}