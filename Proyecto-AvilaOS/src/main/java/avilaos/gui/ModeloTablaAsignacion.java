package avilaos.gui;

import avilaos.datastructures.DynamicArray;
import avilaos.os.filesystem.FileNode;
import avilaos.os.filesystem.FileSnapshot;
import javax.swing.table.AbstractTableModel;

/**
 * Modelo propio para la tabla de asignacion de archivos (G06, G07).
 * Extiende AbstractTableModel sin usar DefaultTableModel (que usa Vector) ni colecciones de java.util.
 * Muestra: Color, Nombre, Bloques, Primer bloque, Dueno y Proceso creador.
 */
public class ModeloTablaAsignacion extends AbstractTableModel {

    public static final int COL_COLOR = 0;
    public static final int COL_NOMBRE = 1;
    public static final int COL_BLOQUES = 2;
    public static final int COL_PRIMER_BLOQUE = 3;
    public static final int COL_DUENO = 4;
    public static final int COL_CREADOR = 5;

    private static final String[] COLUMNAS = {
        "Color", "Nombre", "Bloques", "Primer bloque", "Dueno", "Proceso creador"
    };

    private final DynamicArray<FileSnapshot> archivos = new DynamicArray<>();
    private String ultimaFirma = "";

    public ModeloTablaAsignacion(DynamicArray<FileSnapshot> snapshots) {
        this.ultimaFirma = calcularFirmaArchivos(snapshots);
        cargar(snapshots);
    }

    /**
     * Filtra solo los archivos del snapshot y actualiza la tabla si hubo cambios (G07).
     * @return true si cambio la tabla y se disparo fireTableDataChanged()
     */
    public boolean actualizar(DynamicArray<FileSnapshot> snapshots) {
        String firma = calcularFirmaArchivos(snapshots);
        if (firma.equals(ultimaFirma)) {
            return false;
        }
        ultimaFirma = firma;
        cargar(snapshots);
        fireTableDataChanged();
        return true;
    }

    private void cargar(DynamicArray<FileSnapshot> snapshots) {
        archivos.clear();
        if (snapshots == null) {
            return;
        }
        for (int i = 0; i < snapshots.size(); i++) {
            FileSnapshot s = snapshots.get(i);
            if (s.isFile()) {
                archivos.add(s);
            }
        }
    }

    private static String calcularFirmaArchivos(DynamicArray<FileSnapshot> snapshots) {
        if (snapshots == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < snapshots.size(); i++) {
            FileSnapshot s = snapshots.get(i);
            if (!s.isFile()) {
                continue;
            }
            sb.append(s.getPath()).append('|')
              .append(s.getBlockCount()).append('|')
              .append(s.getFirstBlock()).append('|')
              .append(s.getOwner()).append('|')
              .append(s.getCreatorProcessId()).append('|')
              .append(s.getColor()).append(';');
        }
        return sb.toString();
    }

    public FileSnapshot getArchivo(int row) {
        if (row < 0 || row >= archivos.size()) {
            return null;
        }
        return archivos.get(row);
    }

    /** Texto del proceso creador: «JSON/carga» si vino del archivo inicial, o «P<id>» si lo creo un proceso. */
    public static String textoCreador(int creatorProcessId) {
        return creatorProcessId == FileNode.NO_CREATOR ? "JSON/carga" : "P" + creatorProcessId;
    }

    @Override
    public int getRowCount() {
        return archivos.size();
    }

    @Override
    public int getColumnCount() {
        return COLUMNAS.length;
    }

    @Override
    public String getColumnName(int column) {
        return COLUMNAS[column];
    }

    @Override
    public Class<?> getColumnClass(int columnIndex) {
        if (columnIndex == COL_BLOQUES || columnIndex == COL_PRIMER_BLOQUE) {
            return Integer.class;
        }
        return String.class;
    }

    @Override
    public boolean isCellEditable(int rowIndex, int columnIndex) {
        return false;
    }

    @Override
    public Object getValueAt(int rowIndex, int columnIndex) {
        FileSnapshot f = archivos.get(rowIndex);
        switch (columnIndex) {
            case COL_COLOR:
                return f.getColorHex();
            case COL_NOMBRE:
                return f.getName();
            case COL_BLOQUES:
                return f.getBlockCount();
            case COL_PRIMER_BLOQUE:
                return f.getFirstBlock();
            case COL_DUENO:
                return f.getOwner();
            case COL_CREADOR:
                return textoCreador(f.getCreatorProcessId());
            default:
                return "";
        }
    }
}