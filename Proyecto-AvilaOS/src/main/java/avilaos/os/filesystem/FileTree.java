/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package avilaos.os.filesystem;

import avilaos.datastructures.DynamicArray;

/**
 * Arbol jerarquico del sistema de archivos, con raiz «/».
 * Todos los nodos cuelgan de la raiz; cada uno conoce a su padre.
 * @author Antonella Ruiz
 */
public class FileTree {
    private final FileNode root;

    /** Crea el arbol con su raiz «/», cuyo dueno es el administrador. */
    public FileTree(String rootOwner) {
        this.root = FileNode.root(rootOwner);
    }

    public FileNode getRoot() { return root; }

    /** Dice si el nodo cuelga de la raiz de este arbol. */
    public boolean contains(FileNode node) {
        FileNode current = node;
        while (current != null) {
            if (current == root) {
                return true;
            }
            current = current.getParent();
        }
        return false;
    }

    /** Agrega un nodo dentro de un directorio de este arbol. */
    public void add(FileNode parent, FileNode child) {
        if (parent == null || !contains(parent)) {
            throw new IllegalArgumentException("El directorio destino no pertenece al arbol");
        }
        parent.addChild(child);
    }

    /** Todos los nodos en preorden: cada directorio va antes que su contenido. */
    public DynamicArray<FileNode> allNodes() {
        DynamicArray<FileNode> result = new DynamicArray<>();
        collect(root, result);
        return result;
    }

    private void collect(FileNode node, DynamicArray<FileNode> result) {
        result.add(node);
        for (int i = 0; i < node.getChildCount(); i++) {
            collect(node.getChild(i), result);
        }
    }

    public int countNodes() { return allNodes().size(); }

    public int countFiles() {
        DynamicArray<FileNode> all = allNodes();
        int n = 0;
        for (int i = 0; i < all.size(); i++) {
            if (all.get(i).isFile()) {
                n++;
            }
        }
        return n;
    }

    public int countDirectories() { return countNodes() - countFiles(); }

    /** Suma de los bloques que ocupan todos los archivos. */
    public int usedBlocks() {
        DynamicArray<FileNode> all = allNodes();
        int total = 0;
        for (int i = 0; i < all.size(); i++) {
            total += all.get(i).getBlockCount();
        }
        return total;
    }
}
