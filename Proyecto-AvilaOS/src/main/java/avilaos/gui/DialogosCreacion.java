/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package avilaos.gui;

import avilaos.datastructures.CustomLinkedList;
import avilaos.enums.ProcessType;
import avilaos.model.hardware.Cluster;
import avilaos.model.hardware.Computer;
import avilaos.persistence.ConfigDTO;
import avilaos.sync.BoundedBuffer;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Window;
import javax.swing.BorderFactory;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;

/**
 * Ventanas para crear un buffer o un proceso nuevo desde la interfaz (I05), con la
 * simulacion corriendo o en pausa. Solo recogen los datos; las reglas y la creacion
 * las hace CreadorManual, y cualquier error se muestra dentro de la ventana.
 */
public final class DialogosCreacion {

    private DialogosCreacion() {}

    /** Abre la ventana para crear un buffer. */
    public static void nuevoBuffer(Window owner, Cluster cluster) {
        new DialogoBuffer(owner, cluster).setVisible(true);
    }

    /** Abre la ventana para crear un proceso. */
    public static void nuevoProceso(Window owner, Cluster cluster) {
        new DialogoProceso(owner, cluster).setVisible(true);
    }

    /** Texto que se ve en los selectores de computador. */
    private static String textoComputador(Computer c) {
        return c.getName() + " (id " + c.getId() + ")";
    }

    /** Selector con los computadores del cluster, en el mismo orden que la lista. */
    private static JComboBox<String> selectorComputadores(Cluster cluster) {
        CustomLinkedList<Computer> pcs = cluster.getComputersSnapshot();
        JComboBox<String> combo = new JComboBox<>();
        for (int i = 0; i < pcs.size(); i++) {
            combo.addItem(textoComputador(pcs.get(i)));
        }
        return combo;
    }

    /** Id del computador elegido (el selector sigue el orden de la lista). */
    private static int idComputadorElegido(Cluster cluster, JComboBox<String> combo) {
        return cluster.getComputersSnapshot().get(combo.getSelectedIndex()).getId();
    }

    /** Lee un entero; si no lo es, avisa con un mensaje que dice cual campo falla. */
    private static int entero(JTextField campo, String nombre) {
        try {
            return Integer.parseInt(campo.getText().trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(nombre + " debe ser un numero entero");
        }
    }

    /** Base comun: formulario de filas "etiqueta + campo", mensaje de error y botones. */
    private abstract static class Base extends JDialog {

        protected final Cluster cluster;
        private final JPanel formulario = new JPanel(new GridBagLayout());
        private final JLabel lblError = Tema.etiqueta(" ", Tema.PEQUENA, Tema.ROSA);
        private int fila = 0;

        Base(Window owner, Cluster cluster, String titulo, String accion) {
            super(owner, titulo, ModalityType.APPLICATION_MODAL);
            this.cluster = cluster;

            JPanel contenido = new JPanel(new BorderLayout(0, 12));
            contenido.setBackground(Tema.FONDO);
            contenido.setBorder(BorderFactory.createEmptyBorder(18, 22, 16, 22));
            contenido.add(Tema.etiqueta(titulo, Tema.SUBTITULO, Tema.TEXTO), BorderLayout.NORTH);

            formulario.setOpaque(false);
            contenido.add(formulario, BorderLayout.CENTER);

            Tema.Boton btnCrear = new Tema.Boton(accion, true);
            Tema.Boton btnCancelar = new Tema.Boton("Cancelar", false);
            btnCrear.addActionListener(e -> intentar());
            btnCancelar.addActionListener(e -> dispose());
            JPanel botones = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
            botones.setOpaque(false);
            botones.add(btnCancelar);
            botones.add(btnCrear);
            JPanel sur = new JPanel(new BorderLayout(0, 8));
            sur.setOpaque(false);
            sur.add(lblError, BorderLayout.NORTH);
            sur.add(botones, BorderLayout.SOUTH);
            contenido.add(sur, BorderLayout.SOUTH);

            setContentPane(contenido);
            getRootPane().setDefaultButton(btnCrear);
            setResizable(false);
        }

        /** Agrega una fila y devuelve {etiqueta, campo} para poder mostrarla u ocultarla. */
        protected javax.swing.JComponent[] agregarFila(String texto, javax.swing.JComponent campo) {
            JLabel et = Tema.etiqueta(texto, Tema.NORMAL, Tema.TEXTO_SUAVE);
            GridBagConstraints g = new GridBagConstraints();
            g.gridy = fila++;
            g.insets = new Insets(4, 0, 4, 12);
            g.anchor = GridBagConstraints.WEST;
            g.gridx = 0;
            formulario.add(et, g);
            g.gridx = 1;
            g.fill = GridBagConstraints.HORIZONTAL;
            g.weightx = 1;
            g.insets = new Insets(4, 0, 4, 0);
            formulario.add(campo, g);
            return new javax.swing.JComponent[] {et, campo};
        }

        protected JTextField campoTexto(String inicial) {
            JTextField t = new JTextField(inicial, 14);
            t.setFont(Tema.NORMAL);
            return t;
        }

        protected static void mostrar(javax.swing.JComponent[] fila, boolean visible) {
            fila[0].setVisible(visible);
            fila[1].setVisible(visible);
        }

        protected void mostrarError(String texto) {
            lblError.setText(texto);
            pack();
        }

        private void intentar() {
            try {
                crear();
                dispose();
            } catch (IllegalArgumentException ex) {
                mostrarError(ex.getMessage());
            }
        }

        /** Crea lo que pide la ventana; lanza IllegalArgumentException con el motivo si no se puede. */
        protected abstract void crear();

        protected void abrir(Window owner) {
            pack();
            setLocationRelativeTo(owner);
        }
    }

    /** Ventana "Nuevo buffer": capacidad y computador anfitrion. */
    private static final class DialogoBuffer extends Base {

        private final JTextField txtCapacidad = campoTexto("5");
        private final JComboBox<String> cmbHost;

        DialogoBuffer(Window owner, Cluster cluster) {
            super(owner, cluster, "Nuevo buffer", "Crear buffer");
            cmbHost = selectorComputadores(cluster);
            agregarFila("Capacidad", txtCapacidad);
            agregarFila("Computador anfitrion", cmbHost);
            abrir(owner);
        }

        @Override
        protected void crear() {
            int capacidad = entero(txtCapacidad, "La capacidad");
            CreadorManual.crearBuffer(cluster, capacidad, idComputadorElegido(cluster, cmbHost));
        }
    }

    /** Ventana "Nuevo proceso": los campos cambian segun el tipo elegido. */
    private static final class DialogoProceso extends Base {

        private final JComboBox<ProcessType> cmbTipo = new JComboBox<>(ProcessType.values());
        private final JTextField txtNombre = campoTexto("");
        private final JComboBox<String> cmbComputador;
        private final JTextField txtPrioridad = campoTexto("1");
        private final JTextField txtMemoria = campoTexto("10");
        private final JTextField txtDeadline = campoTexto("100");
        private final JTextField txtCiclos = campoTexto("10");
        private final JTextField txtIntervalo = campoTexto("3");
        private final JTextField txtDuracionEs = campoTexto("2");
        private final JComboBox<String> cmbBuffer = new JComboBox<>();
        private final JTextField txtCiclosElemento = campoTexto("2");
        private final JTextField txtElementos = campoTexto("5");

        private javax.swing.JComponent[] filaCiclos, filaIntervalo, filaDuracion,
                filaBuffer, filaCiclosElemento, filaElementos;
        private final int[] idsBuffers;

        DialogoProceso(Window owner, Cluster cluster) {
            super(owner, cluster, "Nuevo proceso", "Crear proceso");
            cmbComputador = selectorComputadores(cluster);

            CustomLinkedList<BoundedBuffer> buffers = cluster.getBufferManager().getSnapshot();
            idsBuffers = new int[buffers.size()];
            for (int i = 0; i < buffers.size(); i++) {
                BoundedBuffer b = buffers.get(i);
                idsBuffers[i] = b.getId();
                cmbBuffer.addItem("B" + b.getId() + " (capacidad " + b.getCapacity()
                        + ", en computador " + b.getHostComputerId() + ")");
            }

            agregarFila("Tipo", cmbTipo);
            agregarFila("Nombre", txtNombre);
            agregarFila("Computador", cmbComputador);
            agregarFila("Prioridad (1 = la mayor)", txtPrioridad);
            agregarFila("Memoria", txtMemoria);
            agregarFila("Deadline (ciclos)", txtDeadline);
            filaCiclos = agregarFila("Ciclos de ejecucion", txtCiclos);
            filaIntervalo = agregarFila("Cada cuantos ciclos pide E/S", txtIntervalo);
            filaDuracion = agregarFila("Duracion de la E/S (ciclos)", txtDuracionEs);
            filaBuffer = agregarFila("Buffer", cmbBuffer);
            filaCiclosElemento = agregarFila("Ciclos por elemento", txtCiclosElemento);
            filaElementos = agregarFila("Elementos a procesar", txtElementos);

            cmbTipo.addActionListener(e -> {
                actualizarCampos();
                mostrarError(" ");
            });
            actualizarCampos();
            abrir(owner);
        }

        /** Muestra solo los campos que usa el tipo elegido. */
        private void actualizarCampos() {
            ProcessType t = (ProcessType) cmbTipo.getSelectedItem();
            boolean cpu = t == ProcessType.CPU_BOUND;
            boolean io = t == ProcessType.IO_BOUND;
            boolean sync = t == ProcessType.PRODUCER || t == ProcessType.CONSUMER;
            mostrar(filaCiclos, cpu || io);
            mostrar(filaIntervalo, io);
            mostrar(filaDuracion, io);
            mostrar(filaBuffer, sync);
            mostrar(filaCiclosElemento, sync);
            mostrar(filaElementos, sync);
            pack();
        }

        @Override
        protected void crear() {
            ConfigDTO.ProcessConfig d = new ConfigDTO.ProcessConfig();
            d.type = (ProcessType) cmbTipo.getSelectedItem();
            d.name = txtNombre.getText();
            d.computerId = idComputadorElegido(cluster, cmbComputador);
            d.priority = entero(txtPrioridad, "La prioridad");
            d.memorySize = entero(txtMemoria, "La memoria");
            d.deadline = entero(txtDeadline, "El deadline");
            if (d.type == ProcessType.CPU_BOUND || d.type == ProcessType.IO_BOUND) {
                d.cycles = entero(txtCiclos, "Los ciclos de ejecucion");
            }
            if (d.type == ProcessType.IO_BOUND) {
                d.ioInterval = entero(txtIntervalo, "El intervalo de E/S");
                d.ioDuration = entero(txtDuracionEs, "La duracion de la E/S");
            }
            if (d.type == ProcessType.PRODUCER || d.type == ProcessType.CONSUMER) {
                if (idsBuffers.length == 0) {
                    throw new IllegalArgumentException("No hay buffers: crea uno primero (boton Nuevo buffer)");
                }
                d.bufferId = idsBuffers[cmbBuffer.getSelectedIndex()];
                d.cyclesPerItem = entero(txtCiclosElemento, "Los ciclos por elemento");
                d.itemsRequired = entero(txtElementos, "Los elementos a procesar");
            }
            CreadorManual.crearProceso(cluster, d);
        }
    }
}
