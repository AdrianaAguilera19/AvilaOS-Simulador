/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package avilaos.datastructures;

/**
 * Nodo de un arbol n-ario propio (directorios y archivos).
 * Los hijos se guardan como hermanos enlazados: cada nodo apunta a su primer hijo
 * y a su siguiente hermano, asi un nodo puede tener cualquier cantidad de hijos.
 * @param <T> tipo del valor que guarda el nodo
 */
public class TreeNode<T> {
    private T value;
    private TreeNode<T> parent;
    private TreeNode<T> firstChild;
    private TreeNode<T> nextSibling;
    private int childCount;

    public TreeNode(T value) {
        this.value = value;
    }

    public T getValue() { return value; }

    public void setValue(T value) { this.value = value; }

    public TreeNode<T> getParent() { return parent; }

    public int getChildCount() { return childCount; }

    public boolean isLeaf() { return childCount == 0; }

    public boolean isRoot() { return parent == null; }

    /** Agrega un hijo nuevo al final con ese valor y lo devuelve. */
    public TreeNode<T> addChild(T childValue) {
        TreeNode<T> child = new TreeNode<>(childValue);
        addChild(child);
        return child;
    }

    /** Agrega un nodo como ultimo hijo. El nodo no puede tener otro padre. */
    public void addChild(TreeNode<T> child) {
        if (child == null) {
            throw new IllegalArgumentException("El hijo no puede ser nulo");
        }
        if (child.parent != null) {
            throw new IllegalStateException("El nodo ya tiene padre");
        }
        child.parent = this;
        child.nextSibling = null;
        if (firstChild == null) {
            firstChild = child;
        } else {
            TreeNode<T> last = firstChild;
            while (last.nextSibling != null) {
                last = last.nextSibling;
            }
            last.nextSibling = child;
        }
        childCount++;
    }

    /** Devuelve el hijo en esa posicion (0 es el primero) o null si no existe. */
    public TreeNode<T> getChild(int index) {
        if (index < 0 || index >= childCount) {
            return null;
        }
        TreeNode<T> current = firstChild;
        for (int i = 0; i < index; i++) {
            current = current.nextSibling;
        }
        return current;
    }

    /** Saca un hijo (con todo lo que cuelga de el). Devuelve false si no era hijo de este nodo. */
    public boolean removeChild(TreeNode<T> child) {
        if (child == null || child.parent != this) {
            return false;
        }
        if (firstChild == child) {
            firstChild = child.nextSibling;
        } else {
            TreeNode<T> previous = firstChild;
            while (previous.nextSibling != child) {
                previous = previous.nextSibling;
            }
            previous.nextSibling = child.nextSibling;
        }
        child.parent = null;
        child.nextSibling = null;
        childCount--;
        return true;
    }

    /** Cantidad de nodos del subarbol, contando este. */
    public int countNodes() {
        int total = 1;
        TreeNode<T> current = firstChild;
        while (current != null) {
            total += current.countNodes();
            current = current.nextSibling;
        }
        return total;
    }

    /** Distancia hasta la raiz (la raiz tiene profundidad 0). */
    public int depth() {
        int d = 0;
        TreeNode<T> current = parent;
        while (current != null) {
            d++;
            current = current.parent;
        }
        return d;
    }
}
