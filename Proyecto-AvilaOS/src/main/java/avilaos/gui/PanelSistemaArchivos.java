package avilaos.gui;

import avilaos.datastructures.DynamicArray;
import avilaos.os.filesystem.FileColorPalette;
import avilaos.os.filesystem.FileNode;
import avilaos.os.filesystem.FileSnapshot;
import avilaos.os.filesystem.SecuredFileSystem;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.RenderingHints;
import javax.swing.BorderFactory;
import javax.swing.Icon;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.JTree;
import javax.swing.SwingConstants;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.tree.DefaultTreeCellRenderer;
import javax.swing.tree.TreePath;
import javax.swing.tree.TreeSelectionModel;

/**
 * Panel del sistema de archivos (G02, G03, G06 y G07):
 * - Izquierda: JTree jerarquico con modelo propio + tarjeta de informacion del nodo seleccionado
 *   (nombre, tamano en bloques, dueno, permisos y primer bloque).
 * - Derecha: JTable de asignacion de archivos con color, nombre, bloques, primer bloque, dueno y creador.
 *
 * Solo observa el sistema mediante SecuredFileSystem.snapshot(); nunca modifica el FS directamente.
 */
public class PanelSistemaArchivos extends Tema.PanelRedondeado {

    private SecuredFileSystem securedFs;
    private final ModeloArbolArchivos modeloArbol;
    private final ModeloTablaAsignacion modeloTabla;

    private final JTree arbol;
    private final JTable tablaAsignacion;
    private final Tema.Pastilla pillArchivos =
            new Tema.Pastilla("0 ARCHIVOS", Tema.LAVANDA_SUAVE, Tema.LAVANDA);

    // G03: los 5 datos pedidos al seleccionar un nodo (+ ruta/tipo de apoyo)
    private final JLabel lblRuta = Tema.etiqueta("/", Tema.PEQUENA, Tema.TEXTO_SUAVE);
    private final JLabel lblNombre = Tema.etiqueta("-", Tema.NEGRITA, Tema.TEXTO);
    private final JLabel lblBloques = Tema.etiqueta("-", Tema.NORMAL, Tema.TEXTO);
    private final JLabel lblDueno = Tema.etiqueta("-", Tema.NORMAL, Tema.TEXTO);
    private final JLabel lblPermisos = Tema.etiqueta("-", Tema.NORMAL, Tema.TEXTO);
    private final JLabel lblPrimerBloque = Tema.etiqueta("-", Tema.NORMAL, Tema.TEXTO);

    private final DynamicArray<Runnable> selectionListeners = new DynamicArray<>();

    public PanelSistemaArchivos(SecuredFileSystem securedFs) {
        super(Tema.TARJETA, Tema.BORDE, 20);
        if (securedFs == null) {
            throw new IllegalArgumentException("Falta SecuredFileSystem para el panel de archivos");
        }
        this.securedFs = securedFs;

        setLayout(new BorderLayout(10, 10));
        setBorder(BorderFactory.createEmptyBorder(12, 14, 12, 14));

        DynamicArray<FileSnapshot> fotoInicial = securedFs.snapshot();
        this.modeloArbol = new ModeloArbolArchivos(fotoInicial);
        this.modeloTabla = new ModeloTablaAsignacion(fotoInicial);

        // Encabezado del panel
        JPanel header = new JPanel(new BorderLayout());
        header.setOpaque(false);
        header.add(Tema.etiqueta("Sistema de archivos y tabla de asignacion", Tema.SUBTITULO, Tema.TEXTO),
                BorderLayout.WEST);
        header.add(pillArchivos, BorderLayout.EAST);
        add(header, BorderLayout.NORTH);

        // Izquierda: JTree (G02) + Panel de detalle (G03)
        arbol = new JTree(modeloArbol);
        arbol.setRootVisible(true);
        arbol.setShowsRootHandles(true);
        arbol.setFont(Tema.NORMAL);
        arbol.getSelectionModel().setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);
        arbol.setCellRenderer(new RenderizadorNodoArbol());
        arbol.addTreeSelectionListener(e -> {
            actualizarDetalle();
            notificarSeleccion();
        });

        JScrollPane scrollArbol = new JScrollPane(arbol);
        scrollArbol.setBorder(BorderFactory.createLineBorder(Tema.BORDE));
        scrollArbol.setPreferredSize(new Dimension(260, 200));

        JPanel panelIzquierdo = new JPanel(new BorderLayout(0, 8));
        panelIzquierdo.setOpaque(false);
        panelIzquierdo.add(scrollArbol, BorderLayout.CENTER);
        panelIzquierdo.add(construirPanelDetalle(), BorderLayout.SOUTH);

        // Derecha: JTable de asignacion (G06 + G07)
        tablaAsignacion = new JTable(modeloTabla);
        tablaAsignacion.setFont(Tema.NORMAL);
        tablaAsignacion.setRowHeight(24);
        tablaAsignacion.getTableHeader().setFont(Tema.PEQUENA);
        tablaAsignacion.getTableHeader().setBackground(Tema.FONDO);
        tablaAsignacion.getTableHeader().setForeground(Tema.TEXTO);
        tablaAsignacion.setFillsViewportHeight(true);
        tablaAsignacion.setGridColor(Tema.BORDE);
        tablaAsignacion.setShowVerticalLines(false);
        configurarColumnasTabla();

        // Hacer clic en una fila de la tabla selecciona ese archivo en el JTree
        tablaAsignacion.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                int row = tablaAsignacion.getSelectedRow();
                FileSnapshot f = modeloTabla.getArchivo(row);
                if (f != null) {
                    selectPath(f.getPath());
                }
            }
        });

        JScrollPane scrollTabla = new JScrollPane(tablaAsignacion);
        scrollTabla.setBorder(BorderFactory.createLineBorder(Tema.BORDE));

        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, panelIzquierdo, scrollTabla);
        split.setOpaque(false);
        split.setBorder(null);
        split.setResizeWeight(0.38);
        add(split, BorderLayout.CENTER);

        expandirTodo();
        selectPath(FileNode.ROOT_NAME);
        actualizarContadorArchivos();
    }

    private JPanel construirPanelDetalle() {
        Tema.PanelRedondeado tarjeta = new Tema.PanelRedondeado(Tema.FONDO, Tema.BORDE, 14);
        tarjeta.setLayout(new BorderLayout(0, 6));
        tarjeta.setBorder(BorderFactory.createEmptyBorder(8, 10, 8, 10));

        JPanel cabecera = new JPanel(new BorderLayout());
        cabecera.setOpaque(false);
        cabecera.add(Tema.etiqueta("Informacion del elemento", Tema.PEQUENA, Tema.ROSA), BorderLayout.WEST);
        cabecera.add(lblRuta, BorderLayout.EAST);
        tarjeta.add(cabecera, BorderLayout.NORTH);

        JPanel grid = new JPanel(new GridLayout(5, 2, 6, 3));
        grid.setOpaque(false);
        agregarFilaDetalle(grid, "Nombre:", lblNombre);
        agregarFilaDetalle(grid, "Tamano en bloques:", lblBloques);
        agregarFilaDetalle(grid, "Dueno:", lblDueno);
        agregarFilaDetalle(grid, "Permisos:", lblPermisos);
        agregarFilaDetalle(grid, "Primer bloque:", lblPrimerBloque);
        tarjeta.add(grid, BorderLayout.CENTER);
        return tarjeta;
    }

    private static void agregarFilaDetalle(JPanel grid, String titulo, JLabel valor) {
        grid.add(Tema.etiqueta(titulo, Tema.NEGRITA, Tema.TEXTO_SUAVE));
        grid.add(valor);
    }

    private void configurarColumnasTabla() {
        tablaAsignacion.getColumnModel().getColumn(ModeloTablaAsignacion.COL_COLOR)
                .setCellRenderer(new RenderizadorColor(modeloTabla));
        tablaAsignacion.getColumnModel().getColumn(ModeloTablaAsignacion.COL_COLOR).setPreferredWidth(92);
        tablaAsignacion.getColumnModel().getColumn(ModeloTablaAsignacion.COL_NOMBRE).setPreferredWidth(120);
        tablaAsignacion.getColumnModel().getColumn(ModeloTablaAsignacion.COL_BLOQUES).setPreferredWidth(65);
        tablaAsignacion.getColumnModel().getColumn(ModeloTablaAsignacion.COL_PRIMER_BLOQUE).setPreferredWidth(90);
        tablaAsignacion.getColumnModel().getColumn(ModeloTablaAsignacion.COL_DUENO).setPreferredWidth(75);
        tablaAsignacion.getColumnModel().getColumn(ModeloTablaAsignacion.COL_CREADOR).setPreferredWidth(105);

        DefaultTableCellRenderer centrado = new DefaultTableCellRenderer();
        centrado.setHorizontalAlignment(SwingConstants.CENTER);
        tablaAsignacion.getColumnModel().getColumn(ModeloTablaAsignacion.COL_BLOQUES).setCellRenderer(centrado);
        tablaAsignacion.getColumnModel().getColumn(ModeloTablaAsignacion.COL_PRIMER_BLOQUE).setCellRenderer(centrado);
        tablaAsignacion.getColumnModel().getColumn(ModeloTablaAsignacion.COL_CREADOR).setCellRenderer(centrado);
    }

    /**
     * Refresca JTree, panel de detalle y JTable desde un snapshot atomico de SecuredFileSystem (G02, G07).
     * Conserva los directorios expandidos y el elemento seleccionado.
     */
    public void refresh() {
        DynamicArray<FileSnapshot> foto = securedFs.snapshot();

        FileSnapshot seleccionadoAntes = getSelectedSnapshot();
        String rutaSeleccionada = seleccionadoAntes != null ? seleccionadoAntes.getPath() : FileNode.ROOT_NAME;
        DynamicArray<String> expandidas = guardarRutasExpandidas();

        boolean cambioArbol = modeloArbol.actualizar(foto);
        modeloTabla.actualizar(foto);

        if (cambioArbol) {
            restaurarRutasExpandidas(expandidas);
            if (!selectPath(rutaSeleccionada)) {
                selectPath(FileNode.ROOT_NAME);
            }
        } else {
            actualizarDetalle();
        }
        actualizarContadorArchivos();
    }

    /** Cambia el sistema de archivos observado (por ejemplo al reiniciar la simulacion en J09). */
    public void setSecuredFileSystem(SecuredFileSystem nuevoFs) {
        if (nuevoFs == null) {
            throw new IllegalArgumentException("Falta SecuredFileSystem");
        }
        this.securedFs = nuevoFs;
        DynamicArray<FileSnapshot> foto = securedFs.snapshot();
        modeloArbol.actualizar(foto);
        modeloTabla.actualizar(foto);
        expandirTodo();
        selectPath(FileNode.ROOT_NAME);
        actualizarContadorArchivos();
    }

    /** Selecciona un nodo en el JTree por su ruta absoluta y actualiza el panel de detalle (G03). */
    public boolean selectPath(String path) {
        TreePath tp = modeloArbol.treePathPara(path);
        if (tp == null) {
            return false;
        }
        arbol.setSelectionPath(tp);
        arbol.scrollPathToVisible(tp);
        actualizarDetalle();
        return true;
    }

    /** @return foto inmutable del nodo actualmente seleccionado en el JTree, o null si no hay seleccion */
    public FileSnapshot getSelectedSnapshot() {
        TreePath sel = arbol.getSelectionPath();
        if (sel == null) {
            return null;
        }
        Object last = sel.getLastPathComponent();
        if (last instanceof ModeloArbolArchivos.NodoArbol) {
            return ((ModeloArbolArchivos.NodoArbol) last).getSnapshot();
        }
        return null;
    }

    public void addSelectionListener(Runnable listener) {
        if (listener != null && !selectionListeners.contains(listener)) {
            selectionListeners.add(listener);
        }
    }

    private void notificarSeleccion() {
        for (int i = 0; i < selectionListeners.size(); i++) {
            selectionListeners.get(i).run();
        }
    }

    private void actualizarDetalle() {
        FileSnapshot s = getSelectedSnapshot();
        if (s == null) {
            lblRuta.setText("-");
            lblNombre.setText("-");
            lblBloques.setText("-");
            lblDueno.setText("-");
            lblPermisos.setText("-");
            lblPrimerBloque.setText("-");
            return;
        }
        lblRuta.setText(s.getPath() + (s.isDirectory() ? " [DIR]" : " [FILE]"));
        lblNombre.setText(s.getName());
        lblBloques.setText(s.isFile() ? s.getBlockCount() + " bloques" : "0 (directorio)");
        lblDueno.setText(s.getOwner());
        lblPermisos.setText(s.getPermission().name());
        lblPrimerBloque.setText(s.isFile() ? String.valueOf(s.getFirstBlock()) : "-");
    }

    private void actualizarContadorArchivos() {
        int n = modeloTabla.getRowCount();
        pillArchivos.setText(n + (n == 1 ? " ARCHIVO" : " ARCHIVOS"));
    }

    private void expandirTodo() {
        for (int i = 0; i < arbol.getRowCount(); i++) {
            arbol.expandRow(i);
        }
    }

    private DynamicArray<String> guardarRutasExpandidas() {
        DynamicArray<String> rutas = new DynamicArray<>();
        for (int i = 0; i < arbol.getRowCount(); i++) {
            if (arbol.isExpanded(i)) {
                TreePath tp = arbol.getPathForRow(i);
                Object last = tp.getLastPathComponent();
                if (last instanceof ModeloArbolArchivos.NodoArbol) {
                    rutas.add(((ModeloArbolArchivos.NodoArbol) last).getSnapshot().getPath());
                }
            }
        }
        return rutas;
    }

    private void restaurarRutasExpandidas(DynamicArray<String> rutas) {
        TreePath raizPath = modeloArbol.treePathPara(FileNode.ROOT_NAME);
        if (raizPath != null) {
            arbol.expandPath(raizPath);
        }
        for (int i = 0; i < rutas.size(); i++) {
            TreePath tp = modeloArbol.treePathPara(rutas.get(i));
            if (tp != null) {
                arbol.expandPath(tp);
            }
        }
    }

    // Getters de acceso para pruebas y para la Parte B (controles CRUD / modos)
    public JTree getArbol() { return arbol; }
    public JTable getTablaAsignacion() { return tablaAsignacion; }
    public ModeloArbolArchivos getModeloArbol() { return modeloArbol; }
    public ModeloTablaAsignacion getModeloTabla() { return modeloTabla; }
    public String getDetalleNombre() { return lblNombre.getText(); }
    public String getDetalleBloques() { return lblBloques.getText(); }
    public String getDetalleDueno() { return lblDueno.getText(); }
    public String getDetallePermisos() { return lblPermisos.getText(); }
    public String getDetallePrimerBloque() { return lblPrimerBloque.getText(); }

    // ------------------------------------------------------------------ Renderizadores visuales

    /** Muestra un punto de color junto a cada archivo y una etiqueta limpia para directorios. */
    private static final class RenderizadorNodoArbol extends DefaultTreeCellRenderer {
        @Override
        public Component getTreeCellRendererComponent(JTree tree, Object value, boolean sel,
                boolean expanded, boolean leaf, int row, boolean hasFocus) {
            super.getTreeCellRendererComponent(tree, value, sel, expanded, leaf, row, hasFocus);
            setFont(Tema.NORMAL);
            if (value instanceof ModeloArbolArchivos.NodoArbol) {
                FileSnapshot s = ((ModeloArbolArchivos.NodoArbol) value).getSnapshot();
                if (s.isDirectory()) {
                    setText(s.getName() + " (" + s.getOwner() + ")");
                    setIcon(new IconoCuadro(Tema.LAVANDA));
                } else {
                    setText(s.getName() + " [" + s.getBlockCount() + " blk]");
                    Color c = s.getColor() != FileColorPalette.NO_COLOR ? new Color(s.getColor()) : Tema.ROSA;
                    setIcon(new IconoCuadro(c));
                }
            }
            return this;
        }
    }

    /** Dibuja el cuadro del color asignado al archivo junto a su codigo hexadecimal en la tabla (G06). */
    private static final class RenderizadorColor extends DefaultTableCellRenderer {
        private final ModeloTablaAsignacion modelo;

        RenderizadorColor(ModeloTablaAsignacion modelo) {
            this.modelo = modelo;
        }

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected,
                boolean hasFocus, int row, int column) {
            super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
            int modelRow = table.convertRowIndexToModel(row);
            FileSnapshot f = modelo.getArchivo(modelRow);
            if (f != null && f.getColor() != FileColorPalette.NO_COLOR) {
                setIcon(new IconoCuadro(new Color(f.getColor())));
                setText(f.getColorHex());
            } else {
                setIcon(null);
                setText("");
            }
            return this;
        }
    }

    /** Icono cuadrado redondeado de 12x12 para mostrar el color del archivo sin imagenes externas. */
    private static final class IconoCuadro implements Icon {
        private final Color color;

        IconoCuadro(Color color) {
            this.color = color;
        }

        @Override
        public void paintIcon(Component c, Graphics g, int x, int y) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(color);
            g2.fillRoundRect(x, y, 11, 11, 4, 4);
            g2.setColor(Tema.BORDE);
            g2.drawRoundRect(x, y, 11, 11, 4, 4);
            g2.dispose();
        }

        @Override
        public int getIconWidth() { return 12; }

        @Override
        public int getIconHeight() { return 12; }
    }
}