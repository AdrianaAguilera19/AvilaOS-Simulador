/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package avilaos.os.filesystem;


import avilaos.datastructures.TreeNode;
import avilaos.enums.NodeType;
import avilaos.enums.Permission;

/**
 * Nodo del sistema de archivos: un archivo o un directorio.
 * Guarda solo los datos del nodo (no simula contenido). La estructura del arbol
 * (padre e hijos) la lleva un TreeNode propio.
 * @author Antonella Ruiz
 */
public class FileNode {
    /** Valor de firstBlock cuando el nodo no tiene bloques (los directorios). */
    public static final int NO_BLOCK = -1;
    public static final String ROOT_NAME = "/";

    private String name;
    private final NodeType type;
    private final String owner;
    private Permission permission;
    private final int firstBlock;
    private final int blockCount;
    private final TreeNode<FileNode> position;

    private FileNode(String name, NodeType type, String owner, Permission permission, int firstBlock, int blockCount) {
        this.name = name;
        this.type = type;
        this.owner = owner;
        this.permission = permission;
        this.firstBlock = firstBlock;
        this.blockCount = blockCount;
        this.position = new TreeNode<>(this);
    }

    /** Crea la raiz «/» del sistema de archivos. */
    public static FileNode root(String owner) {
        checkOwner(owner);
        return new FileNode(ROOT_NAME, NodeType.DIRECTORY, owner.trim(), Permission.PUBLIC, NO_BLOCK, 0);
    }

    /** Crea un directorio (sin bloques). */
    public static FileNode directory(String name, String owner, Permission permission) {
        checkName(name);
        checkOwner(owner);
        checkPermission(permission);
        return new FileNode(name.trim(), NodeType.DIRECTORY, owner.trim(), permission, NO_BLOCK, 0);
    }

    /** Crea un archivo con su primer bloque y su cantidad de bloques (al menos 1). */
    public static FileNode file(String name, String owner, Permission permission, int firstBlock, int blockCount) {
        checkName(name);
        checkOwner(owner);
        checkPermission(permission);
        if (firstBlock < 0) {
            throw new IllegalArgumentException("El primer bloque no puede ser negativo");
        }
        if (blockCount < 1) {
            throw new IllegalArgumentException("Un archivo ocupa al menos un bloque");
        }
        return new FileNode(name.trim(), NodeType.FILE, owner.trim(), permission, firstBlock, blockCount);
    }

    private static void checkName(String name) {
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("El nombre no puede estar vacio");
        }
        if (name.indexOf('/') >= 0) {
            throw new IllegalArgumentException("El nombre no puede contener «/»");
        }
    }

    private static void checkOwner(String owner) {
        if (owner == null || owner.trim().isEmpty()) {
            throw new IllegalArgumentException("El dueno no puede estar vacio");
        }
    }

    private static void checkPermission(Permission permission) {
        if (permission == null) {
            throw new IllegalArgumentException("El permiso no puede ser nulo");
        }
    }

    public String getName() { return name; }

    public NodeType getType() { return type; }

    public String getOwner() { return owner; }

    public Permission getPermission() { return permission; }

    public int getFirstBlock() { return firstBlock; }

    public int getBlockCount() { return blockCount; }

    public boolean isDirectory() { return type == NodeType.DIRECTORY; }

    public boolean isFile() { return type == NodeType.FILE; }

    public boolean isRoot() { return position.isRoot(); }

    public void setPermission(Permission permission) {
        checkPermission(permission);
        this.permission = permission;
    }

    /** Cambia el nombre. Falla si el nombre es invalido, es la raiz o ya existe en el mismo directorio. */
    public void rename(String newName) {
        if (isRoot() && ROOT_NAME.equals(name)) {
            throw new IllegalStateException("La raiz no se puede renombrar");
        }
        checkName(newName);
        String clean = newName.trim();
        FileNode parent = getParent();
        if (parent != null) {
            FileNode other = parent.findChild(clean);
            if (other != null && other != this) {
                throw new IllegalArgumentException("Ya existe «" + clean + "» en ese directorio");
            }
        }
        this.name = clean;
    }

    public FileNode getParent() {
        TreeNode<FileNode> p = position.getParent();
        return p == null ? null : p.getValue();
    }

    public int getChildCount() { return position.getChildCount(); }

    /** Devuelve el hijo en esa posicion (0 es el primero) o null si no existe. */
    public FileNode getChild(int index) {
        TreeNode<FileNode> c = position.getChild(index);
        return c == null ? null : c.getValue();
    }

    /** Busca un hijo por nombre. Devuelve null si no hay ninguno con ese nombre. */
    public FileNode findChild(String childName) {
        for (int i = 0; i < position.getChildCount(); i++) {
            FileNode c = position.getChild(i).getValue();
            if (c.name.equals(childName)) {
                return c;
            }
        }
        return null;
    }

    /** Agrega un hijo. Solo los directorios tienen hijos y los nombres no se repiten en un directorio. */
    public void addChild(FileNode child) {
        if (child == null) {
            throw new IllegalArgumentException("El hijo no puede ser nulo");
        }
        if (!isDirectory()) {
            throw new IllegalStateException("«" + name + "» es un archivo y no puede tener hijos");
        }
        if (child == this || child.isAncestorOf(this)) {
            throw new IllegalArgumentException("Un directorio no puede colgar de si mismo ni de su contenido");
        }
        if (findChild(child.name) != null) {
            throw new IllegalArgumentException("Ya existe «" + child.name + "» en «" + name + "»");
        }
        position.addChild(child.position);
    }

    /** Saca un hijo con todo su contenido. Devuelve false si no era hijo de este nodo. */
    public boolean removeChild(FileNode child) {
        return child != null && position.removeChild(child.position);
    }

    private boolean isAncestorOf(FileNode other) {
        FileNode current = other.getParent();
        while (current != null) {
            if (current == this) {
                return true;
            }
            current = current.getParent();
        }
        return false;
    }

    /** El JTree muestra el nombre del nodo. */
    @Override
    public String toString() {
        return name;
    }
}
