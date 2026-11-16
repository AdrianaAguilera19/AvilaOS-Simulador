/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package avilaos.os.filesystem;

import avilaos.enums.Permission;
import avilaos.enums.UserMode;

/**
 * Crea archivos en el sistema de archivos: valida el lugar y el permiso, reserva los bloques
 * encadenados y cuelga el nodo del arbol. Si algo falla no se modifica ni el arbol ni el disco.
 * El administrador crea en cualquier directorio; el usuario solo en los directorios que son suyos.
 * @author Antonella Ruiz
 */
public class FileCreator {
    private final FileTree tree;
    private final ChainAllocator allocator;

    public FileCreator(FileTree tree, ChainAllocator allocator) {
        if (tree == null || allocator == null) {
            throw new IllegalArgumentException("Falta el arbol o el asignador de bloques");
        }
        this.tree = tree;
        this.allocator = allocator;
    }

    /**
     * Crea un archivo de sizeBlocks bloques dentro del directorio indicado por su ruta.
     * El dueno del archivo es quien lo crea.
     */
    public FileOpResult create(String directoryPath, String name, int sizeBlocks,
            String user, UserMode mode, Permission permission) {
        if (user == null || user.trim().isEmpty()) {
            return FileOpResult.rejected("Falta el usuario que crea el archivo");
        }
        if (mode == null) {
            return FileOpResult.rejected("Falta el modo de usuario");
        }
        if (permission == null) {
            return FileOpResult.rejected("Falta el permiso del archivo");
        }
        if (name == null || name.trim().isEmpty()) {
            return FileOpResult.rejected("El nombre del archivo esta vacio");
        }
        if (name.indexOf('/') >= 0) {
            return FileOpResult.rejected("El nombre «" + name.trim() + "» no puede contener «/»");
        }
        if (sizeBlocks < 1) {
            return FileOpResult.rejected("El archivo debe tener al menos 1 bloque");
        }
        PathResult found = tree.find(directoryPath);
        if (!found.isFound()) {
            return FileOpResult.rejected(found.getMessage());
        }
        FileNode directory = found.getNode();
        if (!directory.isDirectory()) {
            return FileOpResult.rejected("«" + directory.getName() + "» es un archivo, no un directorio");
        }
        if (!mode.isAdmin() && !directory.getOwner().equals(user.trim())) {
            return FileOpResult.rejected("El usuario «" + user.trim() + "» solo puede crear en sus propios directorios; «"
                    + directory.getName() + "» es de «" + directory.getOwner() + "»");
        }
        String cleanName = name.trim();
        if (directory.findChild(cleanName) != null) {
            return FileOpResult.rejected("Ya existe «" + cleanName + "» en «" + directory.getName() + "»");
        }
        String fullPath = tree.pathOf(directory);
        fullPath = FileNode.ROOT_NAME.equals(fullPath) ? "/" + cleanName : fullPath + "/" + cleanName;
        AllocationResult blocks = allocator.request(sizeBlocks, fullPath);
        if (!blocks.isGranted()) {
            return FileOpResult.rejected(blocks.getMessage());
        }
        FileNode file = FileNode.file(cleanName, user.trim(), permission, blocks.getChain().first(), sizeBlocks);
        tree.add(directory, file);
        return FileOpResult.done(file);
    }
}