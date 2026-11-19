/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package avilaos.os.filesystem;

import avilaos.enums.NodeType;
import avilaos.enums.Permission;

/**
 * Foto inmutable de un nodo del sistema de archivos en un instante. La interfaz grafica (JTree, tabla
 * de archivos) lee estas copias y nunca los FileNode vivos, que otro hilo podria estar modificando.
 * @author Antonella Ruiz
 */
public final class FileSnapshot {
    private final String path;
    private final String name;
    private final NodeType type;
    private final String owner;
    private final Permission permission;
    private final int firstBlock;
    private final int blockCount;
    private final int creatorProcessId;
    private final int color;
    private final int depth;

    FileSnapshot(String path, int depth, FileNode node) {
        this.path = path;
        this.depth = depth;
        this.name = node.getName();
        this.type = node.getType();
        this.owner = node.getOwner();
        this.permission = node.getPermission();
        this.firstBlock = node.getFirstBlock();
        this.blockCount = node.getBlockCount();
        this.creatorProcessId = node.getCreatorProcessId();
        this.color = node.getColor();
    }

    /** Ruta absoluta del nodo («/» para la raiz). */
    public String getPath() { return path; }

    public String getName() { return name; }

    public NodeType getType() { return type; }

    public String getOwner() { return owner; }

    public Permission getPermission() { return permission; }

    public int getFirstBlock() { return firstBlock; }

    public int getBlockCount() { return blockCount; }

    /** ID del proceso que creo el archivo, o FileNode.NO_CREATOR. */
    public int getCreatorProcessId() { return creatorProcessId; }

    public int getColor() { return color; }

    public String getColorHex() { return FileColorPalette.toHex(color); }

    /** Nivel en el arbol: 0 la raiz, 1 sus hijos, etc. (para sangrar el JTree). */
    public int getDepth() { return depth; }

    public boolean isDirectory() { return type == NodeType.DIRECTORY; }

    public boolean isFile() { return type == NodeType.FILE; }
}