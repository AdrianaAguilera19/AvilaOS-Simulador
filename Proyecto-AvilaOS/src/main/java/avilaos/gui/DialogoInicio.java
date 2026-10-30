/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package avilaos.gui;

import avilaos.enums.SchedulingAlgorithm;
import avilaos.model.hardware.Cluster;
import avilaos.scheduler.PolicyFactory;
import java.awt.GridLayout;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;

/**
 * Ventana de inicio: el usuario indica cuantos computadores tendra el cluster, la RAM de cada
 * uno, la duracion del ciclo y la latencia de red, o carga una configuracion guardada.
 * Los campos son selectores numericos con minimo y maximo, asi no se puede escribir un dato invalido.
 */
public final class DialogoInicio {

    private static final String CREAR = "Crear";
    private static final String CARGAR = "Cargar archivo...";
    private static final String SALIR = "Salir";

    private DialogoInicio() { }

    /**
     * @return el cluster que eligio el usuario (todavia sin iniciar), o null si decidio salir
     */
    public static Cluster pedir() {
        while (true) {
            JSpinner computadores = new JSpinner(new SpinnerNumberModel(2, Cluster.MIN_COMPUTERS, 4, 1));
            JSpinner ram = new JSpinner(new SpinnerNumberModel(512, 1, Validador.MAX_VALOR, 64));
            JSpinner cicloMs = new JSpinner(new SpinnerNumberModel(100, 1, 10_000, 10));
            JSpinner latencia = new JSpinner(new SpinnerNumberModel(3, 0, 1000, 1));

            JPanel campos = new JPanel(new GridLayout(0, 2, 8, 8));
            campos.add(new JLabel("Numero de computadores (2 a 4)"));
            campos.add(computadores);
            campos.add(new JLabel("RAM de cada computador"));
            campos.add(ram);
            campos.add(new JLabel("Duracion de un ciclo (ms)"));
            campos.add(cicloMs);
            campos.add(new JLabel("Latencia de red (ciclos)"));
            campos.add(latencia);

            int opcion = JOptionPane.showOptionDialog(null, campos, "AvilaOS - configuracion inicial",
                    JOptionPane.DEFAULT_OPTION, JOptionPane.PLAIN_MESSAGE, null,
                    new Object[] {CREAR, CARGAR, SALIR}, CREAR);
            if (opcion == 0) {
                return crear((Integer) computadores.getValue(), (Integer) ram.getValue(),
                        (Integer) cicloMs.getValue(), (Integer) latencia.getValue());
            }
            if (opcion == 1) {
                Cluster cargado = ArchivosConfig.cargar(null);
                if (cargado != null) {
                    return cargado;
                }
                continue;   // archivo invalido o cancelado: vuelve a mostrar esta ventana
            }
            return null;
        }
    }

    /** Arma un cluster con n computadores iguales (PC1, PC2, ...), todos en FCFS al empezar. */
    static Cluster crear(int computadores, int ram, int cicloMs, int latencia) {
        Cluster cluster = new Cluster(cicloMs, latencia);
        for (int i = 1; i <= computadores; i++) {
            cluster.addComputer("PC" + i,
                    PolicyFactory.create(SchedulingAlgorithm.FCFS, avilaos.model.hardware.Computer.DEFAULT_QUANTUM), ram);
        }
        return cluster;
    }
}
