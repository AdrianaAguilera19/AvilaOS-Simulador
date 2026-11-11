package avilaos.gui;

import avilaos.datastructures.CustomQueue;
import avilaos.enums.ExecutionMode;
import avilaos.enums.SchedulingAlgorithm;
import avilaos.model.hardware.Cluster;
import avilaos.model.hardware.Computer;
import avilaos.model.process.PCB;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;

public class PanelComputador extends Tema.PanelRedondeado {

    private final Cluster cluster;
    private final Computer computer;

    private final Tema.Pastilla pillPolitica =
            new Tema.Pastilla("POLITICA", Tema.LAVANDA_SUAVE, Tema.LAVANDA);
    private final Tema.Pastilla pillModo =
            new Tema.Pastilla("MODO USUARIO", Tema.MENTA_SUAVE, new java.awt.Color(0x3E8E70));
    private final JLabel lblKernel = Tema.etiqueta("Entradas a modo kernel: 0", Tema.PEQUENA, Tema.TEXTO_SUAVE);
    private long kernelVisto = 0;
    private final JLabel lblCpu = Tema.etiqueta("CPU libre", Tema.SUBTITULO, Tema.TEXTO);
    private final JLabel lblDetalle = Tema.etiqueta(" ", Tema.NORMAL, Tema.TEXTO_SUAVE);
    private final JLabel lblProgreso = Tema.etiqueta("0 / 0", Tema.NEGRITA, Tema.TEXTO_SUAVE);
    private final JProgressBar barProceso = Tema.barra(Tema.MENTA, false);
    private final JLabel lblRam = Tema.etiqueta("0 / 0", Tema.NEGRITA, Tema.TEXTO_SUAVE);
    private final JProgressBar barRam = Tema.barra();
    private final Tema.Contador cntNuevos = new Tema.Contador("NUEVOS", Tema.FONDO);
    private final Tema.Contador cntListos = new Tema.Contador("LISTOS", Tema.ROSA_SUAVE);
    private final Tema.Contador cntBloqueados = new Tema.Contador("BLOQUEADOS", Tema.LAVANDA_SUAVE);
    private final Tema.Contador cntTerminados = new Tema.Contador("TERMINADOS", Tema.MENTA_SUAVE);

    private final JComboBox<SchedulingAlgorithm> cmbPolitica = new JComboBox<>(SchedulingAlgorithm.values());
    private final JLabel lblQuantum = Tema.etiqueta("Quantum", Tema.NORMAL, Tema.TEXTO_SUAVE);
    private final JTextField txtQuantum = new JTextField(3);
    private final JLabel lblMsgPolitica = Tema.etiqueta(" ", Tema.PEQUENA, Tema.TEXTO_SUAVE);

    private final PanelProcesos tablaProcesos;
    private DialogoProcesos dialogo;

    public PanelComputador(Cluster cluster, Computer computer) {
        super(Tema.TARJETA, Tema.BORDE, 24);
        this.cluster = cluster;
        this.computer = computer;
        setLayout(new BorderLayout());
        setBorder(BorderFactory.createEmptyBorder(10, 14, 10, 14));

        barRam.setMinimum(0);
        barRam.setMaximum(computer.getRam().getTotalSize());

        JPanel header = new JPanel(new BorderLayout());
        header.setOpaque(false);
        JPanel titulos = new JPanel();
        titulos.setOpaque(false);
        titulos.setLayout(new BoxLayout(titulos, BoxLayout.Y_AXIS));
        titulos.add(Tema.etiqueta(computer.getName(), Tema.TITULO, Tema.TEXTO));
        titulos.add(Tema.etiqueta("Computador " + computer.getId(), Tema.NORMAL, Tema.TEXTO_SUAVE));
        header.add(titulos, BorderLayout.WEST);
        JPanel derecha = new JPanel(new BorderLayout());
        derecha.setOpaque(false);
        derecha.add(pillPolitica, BorderLayout.NORTH);
        header.add(derecha, BorderLayout.EAST);

        JPanel cuerpo = new JPanel();
        cuerpo.setOpaque(false);
        cuerpo.setLayout(new BoxLayout(cuerpo, BoxLayout.Y_AXIS));

        cuerpo.add(Box.createVerticalStrut(5));
        cmbPolitica.setSelectedItem(computer.getPolicy().getAlgorithm());
        cmbPolitica.addActionListener(e -> actualizarQuantumVisible());
        txtQuantum.setText(String.valueOf(computer.getQuantum()));
        Tema.Boton btnAplicar = new Tema.Boton("Aplicar", false);
        btnAplicar.addActionListener(e -> aplicarPolitica());
        JPanel filaPolitica = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        filaPolitica.setOpaque(false);
        filaPolitica.add(Tema.etiqueta("POLITICA", Tema.PEQUENA, Tema.TEXTO_SUAVE));
        filaPolitica.add(cmbPolitica);
        filaPolitica.add(lblQuantum);
        filaPolitica.add(txtQuantum);
        filaPolitica.add(btnAplicar);
        filaPolitica.add(lblMsgPolitica);
        cuerpo.add(alinear(filaPolitica));
        actualizarQuantumVisible();

        cuerpo.add(Box.createVerticalStrut(5));
        cuerpo.add(alinear(Tema.etiqueta("PROCESO EN CPU", Tema.PEQUENA, Tema.TEXTO_SUAVE)));
        cuerpo.add(Box.createVerticalStrut(4));
        cuerpo.add(alinear(lblCpu));
        cuerpo.add(Box.createVerticalStrut(2));
        cuerpo.add(alinear(lblDetalle));
        cuerpo.add(Box.createVerticalStrut(4));
        JPanel filaModo = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        filaModo.setOpaque(false);
        filaModo.add(pillModo);
        filaModo.add(lblKernel);
        cuerpo.add(alinear(filaModo));
        cuerpo.add(Box.createVerticalStrut(4));
        JPanel filaProgreso = new JPanel(new BorderLayout());
        filaProgreso.setOpaque(false);
        filaProgreso.add(Tema.etiqueta("INSTRUCCIONES", Tema.PEQUENA, Tema.TEXTO_SUAVE), BorderLayout.WEST);
        filaProgreso.add(lblProgreso, BorderLayout.EAST);
        cuerpo.add(alinear(filaProgreso));
        cuerpo.add(Box.createVerticalStrut(4));
        cuerpo.add(alinear(barProceso));

        cuerpo.add(Box.createVerticalStrut(5));
        JPanel filaRam = new JPanel(new BorderLayout());
        filaRam.setOpaque(false);
        filaRam.add(Tema.etiqueta("MEMORIA RAM", Tema.PEQUENA, Tema.TEXTO_SUAVE), BorderLayout.WEST);
        filaRam.add(lblRam, BorderLayout.EAST);
        cuerpo.add(alinear(filaRam));
        cuerpo.add(Box.createVerticalStrut(4));
        cuerpo.add(alinear(barRam));

        cuerpo.add(Box.createVerticalStrut(5));
        JPanel colas = new JPanel(new GridLayout(1, 4, 8, 0));
        colas.setOpaque(false);
        colas.add(cntNuevos);
        colas.add(cntListos);
        colas.add(cntBloqueados);
        colas.add(cntTerminados);
        cuerpo.add(alinear(colas));

        JPanel filaTabla = new JPanel(new BorderLayout());
        filaTabla.setOpaque(false);
        filaTabla.add(Tema.etiqueta("PROCESOS DEL COMPUTADOR", Tema.PEQUENA, Tema.TEXTO_SUAVE), BorderLayout.WEST);
        Tema.Boton btnPcb = new Tema.Boton("Tabla completa", false);
        btnPcb.addActionListener(e -> abrirProcesos());
        filaTabla.add(btnPcb, BorderLayout.EAST);
        tablaProcesos = new PanelProcesos(computer, true, false);
        JPanel zonaTabla = new JPanel(new BorderLayout(0, 4));
        zonaTabla.setOpaque(false);
        zonaTabla.setBorder(BorderFactory.createEmptyBorder(8, 0, 0, 0));
        zonaTabla.add(filaTabla, BorderLayout.NORTH);
        zonaTabla.add(tablaProcesos, BorderLayout.CENTER);

        JPanel centro = new JPanel(new BorderLayout());
        centro.setOpaque(false);
        centro.add(cuerpo, BorderLayout.NORTH);
        centro.add(zonaTabla, BorderLayout.CENTER);

        add(header, BorderLayout.NORTH);
        add(centro, BorderLayout.CENTER);
        refresh();
    }

    private void actualizarQuantumVisible() {
        boolean rr = cmbPolitica.getSelectedItem() == SchedulingAlgorithm.ROUND_ROBIN;
        lblQuantum.setVisible(rr);
        txtQuantum.setVisible(rr);
    }

    private void aplicarPolitica() {
        SchedulingAlgorithm algo = (SchedulingAlgorithm) cmbPolitica.getSelectedItem();
        try {
            if (algo == SchedulingAlgorithm.ROUND_ROBIN) {
                cluster.setQuantum(computer.getId(), leerQuantum());
            }
            cluster.setPolicy(computer.getId(), algo);
            mostrarMensaje("Politica aplicada: " + algo.toString().replace('_', ' '), false);
        } catch (IllegalArgumentException ex) {
            mostrarMensaje(ex.getMessage(), true);
        }
    }

    private int leerQuantum() {
        return Validador.entero(txtQuantum.getText(), "El quantum", 1, Validador.MAX_VALOR);
    }

    private void mostrarMensaje(String texto, boolean error) {
        lblMsgPolitica.setText(texto);
        lblMsgPolitica.setForeground(error ? new Color(0xB3261E) : new Color(0x3E8E70));
    }

    private void abrirProcesos() {
        if (dialogo == null) {
            dialogo = new DialogoProcesos(SwingUtilities.getWindowAncestor(this), computer);
        }
        dialogo.setVisible(true);
        dialogo.toFront();
    }

    private static JPanel alinear(java.awt.Component c) {
        JPanel p = new JPanel(new BorderLayout());
        p.setOpaque(false);
        if (c instanceof Tema.Pastilla || c instanceof JLabel) {
            p.add(c, BorderLayout.WEST);
        } else {
            p.add(c, BorderLayout.CENTER);
        }
        p.setAlignmentX(LEFT_ALIGNMENT);
        p.setMaximumSize(new java.awt.Dimension(Integer.MAX_VALUE, p.getPreferredSize().height));
        return p;
    }

    public void refresh() {
        pillPolitica.setText(computer.getPolicy().getAlgorithm().toString().replace('_', ' '));

        PCB running = computer.getRunning();
        if (running == null) {
            lblCpu.setText("CPU libre");
            lblCpu.setForeground(Tema.TEXTO_SUAVE);
            lblDetalle.setText(" ");
            lblProgreso.setText("-");
            barProceso.setMaximum(1);
            barProceso.setValue(0);
        } else {
            lblCpu.setText(running.getName() + "  #" + running.getId());
            lblCpu.setForeground(Tema.TEXTO);
            lblDetalle.setText("Prioridad " + running.getPriority()
                    + "   ·   Deadline " + running.getDeadline()
                    + "   ·   Quedan " + running.getRemainingTime());
            lblProgreso.setText("PC " + running.getProgramCounter()
                    + " / " + running.getTotalInstructions());
            barProceso.setMaximum(running.getTotalInstructions());
            barProceso.setValue(running.getProgramCounter());
        }

        ExecutionMode modo = computer.getMode();
        long entradas = computer.getKernelEntries();
        boolean kernel = modo == ExecutionMode.S_O || entradas != kernelVisto;
        kernelVisto = entradas;
        if (kernel) {
            pillModo.setText("MODO KERNEL");
            pillModo.cambiarColores(Tema.ROSA_SUAVE, Tema.ROSA);
        } else {
            pillModo.setText("MODO USUARIO");
            pillModo.cambiarColores(Tema.MENTA_SUAVE, new Color(0x3E8E70));
        }
        lblKernel.setText("Entradas a modo kernel: " + entradas);

        int used = computer.getRam().getUsedSize();
        barRam.setValue(used);
        lblRam.setText(used + " / " + computer.getRam().getTotalSize());

        CustomQueue<PCB> nuevos = computer.getNewSnapshot();
        CustomQueue<PCB> listos = computer.getReadySnapshot();
        CustomQueue<PCB> bloqueados = computer.getBlockedSnapshot();
        CustomQueue<PCB> terminados = computer.getFinishedSnapshot();

        cntNuevos.setValor(nuevos.size());
        cntListos.setValor(listos.size());
        cntBloqueados.setValor(bloqueados.size());
        cntTerminados.setValor(terminados.size());

        tablaProcesos.refrescar();
    }
}