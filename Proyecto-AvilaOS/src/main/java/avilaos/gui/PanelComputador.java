/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package avilaos.gui;

import avilaos.datastructures.CustomQueue;
import avilaos.enums.ExecutionMode;
import avilaos.model.hardware.Computer;
import avilaos.model.process.PCB;
import java.awt.BorderLayout;
import java.awt.GridLayout;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JProgressBar;

/**
 * Tarjeta que muestra el estado de UN computador: politica, proceso en CPU
 * (con su progreso), RAM, contadores de colas y el contenido de cada cola.
 * Solo lee (snapshots y getters): no modifica la simulacion.
 * refresh() se llama desde el hilo de Swing.
 * @author Antonella Ruiz
 */
public class PanelComputador extends Tema.PanelRedondeado {

    // Cuantos procesos se listan como maximo en cada cola (el resto se resume en "+N")
    private static final int MAX_LISTA = 4;

    private final Computer computer;

    private final Tema.Pastilla pillPolitica =
            new Tema.Pastilla("POLITICA", Tema.LAVANDA_SUAVE, Tema.LAVANDA);
    private final Tema.Pastilla pillModo =
            new Tema.Pastilla("MODO USUARIO", Tema.MENTA_SUAVE, new java.awt.Color(0x3E8E70));
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
    private final JLabel lblListos = Tema.etiqueta("-", Tema.NORMAL, Tema.TEXTO);
    private final JLabel lblBloqueados = Tema.etiqueta("-", Tema.NORMAL, Tema.TEXTO);
    private final JLabel lblTerminados = Tema.etiqueta("-", Tema.NORMAL, Tema.TEXTO);

    /** Arma la tarjeta para el computador indicado. */
    public PanelComputador(Computer computer) {
        super(Tema.TARJETA, Tema.BORDE, 24);
        this.computer = computer;
        setLayout(new BorderLayout());
        setBorder(BorderFactory.createEmptyBorder(20, 22, 20, 22));

        barRam.setMinimum(0);
        barRam.setMaximum(computer.getRam().getTotalSize());

        // Encabezado: nombre a la izquierda, politica a la derecha
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

        // Cuerpo: CPU, RAM, colas y contenido de las colas
        JPanel cuerpo = new JPanel();
        cuerpo.setOpaque(false);
        cuerpo.setLayout(new BoxLayout(cuerpo, BoxLayout.Y_AXIS));

        // --- Proceso en CPU ---
        cuerpo.add(Box.createVerticalStrut(18));
        cuerpo.add(alinear(Tema.etiqueta("PROCESO EN CPU", Tema.PEQUENA, Tema.TEXTO_SUAVE)));
        cuerpo.add(Box.createVerticalStrut(4));
        cuerpo.add(alinear(lblCpu));
        cuerpo.add(Box.createVerticalStrut(2));
        cuerpo.add(alinear(lblDetalle));
        cuerpo.add(Box.createVerticalStrut(8));
        cuerpo.add(alinear(pillModo));
        cuerpo.add(Box.createVerticalStrut(10));
        JPanel filaProgreso = new JPanel(new BorderLayout());
        filaProgreso.setOpaque(false);
        filaProgreso.add(Tema.etiqueta("INSTRUCCIONES", Tema.PEQUENA, Tema.TEXTO_SUAVE), BorderLayout.WEST);
        filaProgreso.add(lblProgreso, BorderLayout.EAST);
        cuerpo.add(alinear(filaProgreso));
        cuerpo.add(Box.createVerticalStrut(6));
        cuerpo.add(alinear(barProceso));

        // --- RAM ---
        cuerpo.add(Box.createVerticalStrut(18));
        JPanel filaRam = new JPanel(new BorderLayout());
        filaRam.setOpaque(false);
        filaRam.add(Tema.etiqueta("MEMORIA RAM", Tema.PEQUENA, Tema.TEXTO_SUAVE), BorderLayout.WEST);
        filaRam.add(lblRam, BorderLayout.EAST);
        cuerpo.add(alinear(filaRam));
        cuerpo.add(Box.createVerticalStrut(6));
        cuerpo.add(alinear(barRam));

        // --- Contadores de colas ---
        cuerpo.add(Box.createVerticalStrut(18));
        JPanel colas = new JPanel(new GridLayout(2, 2, 10, 10));
        colas.setOpaque(false);
        colas.add(cntNuevos);
        colas.add(cntListos);
        colas.add(cntBloqueados);
        colas.add(cntTerminados);
        cuerpo.add(alinear(colas));

        // --- Contenido de las colas ---
        cuerpo.add(Box.createVerticalStrut(16));
        cuerpo.add(alinear(Tema.etiqueta("COLA DE LISTOS (EN ORDEN)", Tema.PEQUENA, Tema.TEXTO_SUAVE)));
        cuerpo.add(Box.createVerticalStrut(3));
        cuerpo.add(alinear(lblListos));
        cuerpo.add(Box.createVerticalStrut(10));
        cuerpo.add(alinear(Tema.etiqueta("BLOQUEADOS", Tema.PEQUENA, Tema.TEXTO_SUAVE)));
        cuerpo.add(Box.createVerticalStrut(3));
        cuerpo.add(alinear(lblBloqueados));
        cuerpo.add(Box.createVerticalStrut(10));
        cuerpo.add(alinear(Tema.etiqueta("TERMINADOS RECIENTES", Tema.PEQUENA, Tema.TEXTO_SUAVE)));
        cuerpo.add(Box.createVerticalStrut(3));
        cuerpo.add(alinear(lblTerminados));

        add(header, BorderLayout.NORTH);
        add(cuerpo, BorderLayout.CENTER);
        refresh();
    }

    /** Hace que un componente ocupe todo el ancho del BoxLayout. */
    private static JPanel alinear(java.awt.Component c) {
        JPanel p = new JPanel(new BorderLayout());
        p.setOpaque(false);
        // Las pastillas y etiquetas no deben estirarse: se pegan a la izquierda
        if (c instanceof Tema.Pastilla || c instanceof JLabel) {
            p.add(c, BorderLayout.WEST);
        } else {
            p.add(c, BorderLayout.CENTER);
        }
        p.setAlignmentX(LEFT_ALIGNMENT);
        p.setMaximumSize(new java.awt.Dimension(Integer.MAX_VALUE, p.getPreferredSize().height));
        return p;
    }

    /**
     * Convierte una cola en texto: "a › b › c  +2". Si recientes es true toma los
     * ultimos de la cola (los mas nuevos) y marca con ✓ o ✗ si vencio el deadline.
     */
    private static String lista(CustomQueue<PCB> q, boolean recientes, boolean conMotivo) {
        int n = q.size();
        if (n == 0) {
            return "-";
        }
        int mostrar = Math.min(n, MAX_LISTA);
        int desde = recientes ? n - mostrar : 0;
        String texto = "";
        for (int i = desde; i < desde + mostrar; i++) {
            PCB p = q.get(i);
            if (i > desde) {
                texto += recientes ? "   " : "  ›  ";
            }
            texto += p.getName();
            if (recientes) {
                texto += p.isDeadlineMissed() ? " ✗" : " ✓";
            }
            if (conMotivo) {
                texto += " (" + p.getBlockReason().toString().toLowerCase().replace('_', ' ') + ")";
            }
        }
        if (n > mostrar) {
            texto += "   +" + (n - mostrar);
        }
        return texto;
    }

    /** Vuelve a leer el estado del computador y actualiza la tarjeta. */
    public void refresh() {
        pillPolitica.setText(computer.getPolicy().getAlgorithm().toString().replace('_', ' '));

        // Se lee una sola vez para que no cambie a mitad de la actualizacion
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
        pillModo.setText(modo == ExecutionMode.USUARIO ? "MODO USUARIO" : "MODO SISTEMA");

        int used = computer.getRam().getUsedSize();
        barRam.setValue(used);
        lblRam.setText(used + " / " + computer.getRam().getTotalSize());

        // Cada snapshot se pide una vez: se usa para el contador y para la lista
        CustomQueue<PCB> nuevos = computer.getNewSnapshot();
        CustomQueue<PCB> listos = computer.getReadySnapshot();
        CustomQueue<PCB> bloqueados = computer.getBlockedSnapshot();
        CustomQueue<PCB> terminados = computer.getFinishedSnapshot();

        cntNuevos.setValor(nuevos.size());
        cntListos.setValor(listos.size());
        cntBloqueados.setValor(bloqueados.size());
        cntTerminados.setValor(terminados.size());

        lblListos.setText(lista(listos, false, false));
        lblBloqueados.setText(lista(bloqueados, false, true));
        lblTerminados.setText(lista(terminados, true, false));
    }
}