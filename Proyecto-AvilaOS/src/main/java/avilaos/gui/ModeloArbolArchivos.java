package avilaos.gui;

import avilaos.datastructures.DynamicArray;
import avilaos.os.filesystem.FileNode;
import avilaos.os.filesystem.FileSnapshot;
import javax.swing.event.TreeModelEvent;
import javax.swing.event.TreeModelListener;
import javax.swing.tree.TreeModel;
import javax.swing.tree.TreePath;

/**
 * Modelo propio para el JTree del sistema de archivos (G02).
 * No usa DefaultMutableTreeNode (que usa Vector por dentro) ni ninguna coleccion de java.util.
 * Se construye exclusivamente desde los FileSnapshot inmutables de SecuredFileSystem.snapshot().
 */
public class ModeloArbolArchivos implements TreeModel {

    /** Nodo ligero del arbol visual que envuelve un FileSnapshot inmutable. */
    public static final class NodoArbol {
        private final FileSnapshot snapshot;
        private final DynamicArray<NodoArbol> hijos = new DynamicArray<>();
        private NodoArbol padre;

        NodoArbol(FileSnapshot snapshot) {
            this.snapshot = snapshot;
        }

        public FileSnapshot getSnapshot() { return snapshot; }

        public NodoArbol getPadre() { return padre; }

        public int getChildCount() { return hijos.size(); }

        public NodoArbol getChild(int index) { return hijos.get(index); }

        public int indexOfChild(NodoArbol child) { return hijos.indexOf(child); }

        void addChild(NodoArbol child) {
            child.padre = this;
            hijos.add(child);
        }

        @Override
        public String toString() {
            return snapshot.getName();
        }
    }

    private NodoArbol raiz;
    private final DynamicArray<NodoArbol> todos = new DynamicArray<>();
    private final DynamicArray<TreeModelListener> listeners = new DynamicArray<>();
    private String ultimaFirma = "";

    public ModeloArbolArchivos(DynamicArray<FileSnapshot> snapshots) {
        this.ultimaFirma = calcularFirma(snapshots);
        reconstruir(snapshots);
    }

    /**
     * Actualiza el arbol solo si cambio respecto a la foto anterior (evita parpadeos en el JTree).
     * @return true si el arbol cambio y se notifico a la vista
     */
    public boolean actualizar(DynamicArray<FileSnapshot> snapshots) {
        String firma = calcularFirma(snapshots);
        if (firma.equals(ultimaFirma) && raiz != null) {
            return false;
        }
        ultimaFirma = firma;
        reconstruir(snapshots);
        notificarCambioEstructura();
        return true;
    }

    private void reconstruir(DynamicArray<FileSnapshot> snapshots) {
        todos.clear();
        raiz = null;
        if (snapshots == null || snapshots.isEmpty()) {
            return;
        }
        // snapshot() viene en preorden: cada directorio aparece antes que sus hijos
        for (int i = 0; i < snapshots.size(); i++) {
            FileSnapshot snap = snapshots.get(i);
            NodoArbol nodo = new NodoArbol(snap);
            todos.add(nodo);
            if (FileNode.ROOT_NAME.equals(snap.getPath())) {
                raiz = nodo;
            } else {
                String parentPath = rutaPadre(snap.getPath());
                NodoArbol padre = buscarPorRuta(parentPath);
                if (padre != null) {
                    padre.addChild(nodo);
                } else if (raiz != null) {
                    raiz.addChild(nodo);
                }
            }
        }
    }

    public NodoArbol buscarPorRuta(String path) {
        if (path == null) {
            return null;
        }
        for (int i = 0; i < todos.size(); i++) {
            NodoArbol n = todos.get(i);
            if (path.equals(n.getSnapshot().getPath())) {
                return n;
            }
        }
        return null;
    }

    /** Construye el TreePath de Swing hasta el nodo de esa ruta, o null si ya no existe. */
    public TreePath treePathPara(String path) {
        NodoArbol nodo = buscarPorRuta(path);
        if (nodo == null) {
            return null;
        }
        DynamicArray<NodoArbol> reverso = new DynamicArray<>();
        NodoArbol actual = nodo;
        while (actual != null) {
            reverso.add(actual);
            actual = actual.getPadre();
        }
        Object[] cadena = new Object[reverso.size()];
        for (int i = 0; i < reverso.size(); i++) {
            cadena[i] = reverso.get(reverso.size() - 1 - i);
        }
        return new TreePath(cadena);
    }

    private static String rutaPadre(String path) {
        int slash = path.lastIndexOf('/');
        return slash <= 0 ? FileNode.ROOT_NAME : path.substring(0, slash);
    }

    private static String calcularFirma(DynamicArray<FileSnapshot> snapshots) {
        if (snapshots == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < snapshots.size(); i++) {
            FileSnapshot s = snapshots.get(i);
            sb.append(s.getPath()).append('|')
              .append(s.getType()).append('|')
              .append(s.getOwner()).append('|')
              .append(s.getPermission()).append('|')
              .append(s.getFirstBlock()).append('|')
              .append(s.getBlockCount()).append('|')
              .append(s.getCreatorProcessId()).append('|')
              .append(s.getColor()).append(';');
        }
        return sb.toString();
    }

    private void notificarCambioEstructura() {
        if (raiz == null) {
            return;
        }
        TreeModelEvent evento = new TreeModelEvent(this, new Object[] { raiz });
        for (int i = 0; i < listeners.size(); i++) {
            listeners.get(i).treeStructureChanged(evento);
        }
    }

    // ------------------------------------------------------------------ Metodos de TreeModel

    @Override
    public Object getRoot() {
        return raiz;
    }

    @Override
    public Object getChild(Object parent, int index) {
        NodoArbol nodo = (NodoArbol) parent;
        return nodo.getChild(index);
    }

    @Override
    public int getChildCount(Object parent) {
        NodoArbol nodo = (NodoArbol) parent;
        return nodo.getChildCount();
    }

    @Override
    public boolean isLeaf(Object node) {
        NodoArbol nodo = (NodoArbol) node;
        return nodo.getSnapshot().isFile();
    }

    @Override
    public void valueForPathChanged(TreePath path, Object newValue) {
        // El arbol es de solo lectura: los cambios siempre pasan por procesos de E/S
    }

    @Override
    public int getIndexOfChild(Object parent, Object child) {
        if (parent == null || child == null) {
            return -1;
        }
        NodoArbol p = (NodoArbol) parent;
        return p.indexOfChild((NodoArbol) child);
    }

    @Override
    public void addTreeModelListener(TreeModelListener l) {
        if (l != null && !listeners.contains(l)) {
            listeners.add(l);
        }
    }

    @Override
    public void removeTreeModelListener(TreeModelListener l) {
        if (l != null) {
            listeners.remove(l);
        }
    }
}