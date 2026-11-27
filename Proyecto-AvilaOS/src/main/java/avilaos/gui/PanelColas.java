/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package avilaos.gui;

import avilaos.datastructures.CustomQueue;
import avilaos.model.hardware.Computer;
import avilaos.model.hardware.ComputerSnapshot;
import avilaos.model.process.PCB;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.table.AbstractTableModel;

/**
 * G11: tarjeta de colas del computador. Muestra los 7 estados con sus colas,
 * incluidas Listo/Suspendido y Bloqueado/Suspendido, y el motivo de bloqueo de cada proceso bloqueado.
 * Lee solo ComputerSnapshot.
 */
public class PanelColas extends Tema.PanelRedondeado {

    private static final String[] NOMBRES = {
        "Nuevo", "Listo", "Bloqueado", "Listo/Suspendido", "Bloqueado/Suspendido"
    };

    private final Computer computer;
    private final Tema.Contador cntListoSusp = new Tema.Contador("LISTO/SUSPENDIDO", Tema.ROSA_SUAVE);
    private final Tema.Contador cntBloqSusp = new Tema.Contador("BLOQUEADO/SUSPENDIDO", Tema.LAVANDA_SUAVE);
    private final JLabel lblResumen = Tema.etiqueta("-", Tema.NORMAL, Tema.TEXTO_SUAVE);
    private final Modelo modelo = new Modelo();
    private final JTable tabla = new JTable(modelo);

    public PanelColas(Computer computer) {
        super(Tema.TARJETA, Tema.BORDE, 16);
        if (computer == null) {
            throw new IllegalArgumentException("Falta el computador");
        }
        this.computer = computer;
        setLayout(new BorderLayout(0, 8));
        setBorder(BorderFactory.createEmptyBorder(12, 14, 12, 14));

        JPanel cab = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        cab.setOpaque(false);
        cab.add(Tema.etiqueta("Colas del computador", Tema.SUBTITULO, Tema.TEXTO));
        cab.add(cntListoSusp);
        cab.add(cntBloqSusp);
        cab.add(lblResumen);
        add(cab, BorderLayout.NORTH);

        tabla.setFont(Tema.NORMAL);
        tabla.setRowHeight(20);
        tabla.setFillsViewportHeight(true);
        JScrollPane sp = new JScrollPane(tabla);
        sp.setBorder(BorderFactory.createLineBorder(Tema.BORDE));
        add(sp, BorderLayout.CENTER);
        refresh();
    }

    public void refresh() {
        refresh(computer.takeSnapshot());
    }

    public void refresh(ComputerSnapshot s) {
        cntListoSusp.setValor(s.getReadySuspendedQueue().size());
        cntBloqSusp.setValor(s.getBlockedSuspendedQueue().size());
        lblResumen.setText("Nuevos " + s.getNewQueue().size() + "  Listos " + s.getReadyQueue().size()
                + "  Bloqueados " + s.getBlockedQueue().size() + "  Terminados " + s.getFinishedQueue().size());
        modelo.set(s);
    }

    public JTable getTabla() { return tabla; }

    private static final class Modelo extends AbstractTableModel {
        private static final String[] COLS = {"Cola", "Proceso", "Prioridad", "Deadline", "Motivo de bloqueo"};
        private String[] cola = new String[0];
        private PCB[] procs = new PCB[0];

        void set(ComputerSnapshot s) {
            CustomQueue<PCB>[] qs = colas(s);
            int total = 0;
            for (CustomQueue<PCB> q : qs) {
                total += q.size();
            }
            String[] c = new String[total];
            PCB[] p = new PCB[total];
            int k = 0;
            for (int i = 0; i < qs.length; i++) {
                for (int j = 0; j < qs[i].size(); j++) {
                    c[k] = NOMBRES[i];
                    p[k++] = qs[i].get(j);
                }
            }
            cola = c;
            procs = p;
            fireTableDataChanged();
        }

        @SuppressWarnings("unchecked")
        private static CustomQueue<PCB>[] colas(ComputerSnapshot s) {
            return new CustomQueue[] {s.getNewQueue(), s.getReadyQueue(), s.getBlockedQueue(),
                s.getReadySuspendedQueue(), s.getBlockedSuspendedQueue()};
        }

        @Override public int getRowCount() { return procs.length; }
        @Override public int getColumnCount() { return COLS.length; }
        @Override public String getColumnName(int c) { return COLS[c]; }

        @Override
        public Object getValueAt(int r, int c) {
            PCB p = procs[r];
            switch (c) {
                case 0: return cola[r];
                case 1: return p.getName() + " #" + p.getId();
                case 2: return p.getPriority();
                case 3: return p.getDeadline();
                default: return p.getBlockReason() == null ? "-" : p.getBlockReason().name();
            }
        }
    }
}
