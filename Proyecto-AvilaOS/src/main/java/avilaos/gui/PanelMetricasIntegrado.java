/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package avilaos.gui;

import avilaos.enums.DiskSchedulingPolicy;
import avilaos.metrics.DiskMetrics;
import avilaos.metrics.Metrics;
import avilaos.metrics.MetricsCalculator;
import avilaos.model.hardware.Cluster;
import avilaos.os.disk.DiskRecordLog;
import java.awt.BorderLayout;
import java.awt.GridLayout;
import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;

/**
 * G21: metricas siempre visibles. Tres bloques: las de P1 (planificacion), las de suspension
 * y las del disco (con el desglose por politica). Se recalculan con MetricsCalculator.
 */
public class PanelMetricasIntegrado extends Tema.PanelRedondeado {

    private final Cluster cluster;
    private final DiskRecordLog recordLog;
    private final JLabel[] valores = new JLabel[10];
    private final JLabel[] porPolitica = new JLabel[DiskSchedulingPolicy.values().length];
    private static final String[] NOMBRES = {
        "Ciclos", "Terminados", "Vencieron deadline", "Throughput", "Uso de CPU",
        "Respuesta promedio", "Cumplimiento deadline", "Equidad (fairness)",
        "Suspensiones", "Reactivaciones"
    };
    private final JLabel lblAtendidas = Tema.etiqueta("-", Tema.NEGRITA, Tema.TEXTO);
    private final JLabel lblDesplazamiento = Tema.etiqueta("-", Tema.NEGRITA, Tema.TEXTO);
    private final JLabel lblEspera = Tema.etiqueta("-", Tema.NEGRITA, Tema.TEXTO);

    public PanelMetricasIntegrado(Cluster cluster, DiskRecordLog recordLog) {
        super(Tema.TARJETA, Tema.BORDE, 16);
        if (cluster == null) {
            throw new IllegalArgumentException("Falta el cluster");
        }
        this.cluster = cluster;
        this.recordLog = recordLog;
        setLayout(new BorderLayout(0, 8));
        setBorder(BorderFactory.createEmptyBorder(12, 14, 12, 14));
        add(Tema.etiqueta("Metricas", Tema.SUBTITULO, Tema.TEXTO), BorderLayout.NORTH);

        JPanel grid = new JPanel(new GridLayout(0, 4, 18, 4));
        grid.setOpaque(false);
        for (int i = 0; i < NOMBRES.length; i++) {
            valores[i] = Tema.etiqueta("-", Tema.NEGRITA, Tema.TEXTO);
            grid.add(Tema.etiqueta(NOMBRES[i], Tema.NORMAL, Tema.TEXTO_SUAVE));
            grid.add(valores[i]);
        }
        grid.add(Tema.etiqueta("Solicitudes de disco atendidas", Tema.NORMAL, Tema.TEXTO_SUAVE));
        grid.add(lblAtendidas);
        grid.add(Tema.etiqueta("Desplazamiento total del cabezal", Tema.NORMAL, Tema.TEXTO_SUAVE));
        grid.add(lblDesplazamiento);
        grid.add(Tema.etiqueta("Espera promedio en disco", Tema.NORMAL, Tema.TEXTO_SUAVE));
        grid.add(lblEspera);
        grid.add(new JLabel());
        grid.add(new JLabel());
        DiskSchedulingPolicy[] ps = DiskSchedulingPolicy.values();
        for (int i = 0; i < ps.length; i++) {
            porPolitica[i] = Tema.etiqueta("-", Tema.NEGRITA, Tema.TEXTO);
            grid.add(Tema.etiqueta("Con " + ps[i] + " (atendidas / desplaz.)", Tema.NORMAL, Tema.TEXTO_SUAVE));
            grid.add(porPolitica[i]);
        }
        add(grid, BorderLayout.CENTER);
        refresh();
    }

    public void refresh() {
        refresh(MetricsCalculator.forSystem(cluster, recordLog));
    }

    public void refresh(Metrics m) {
        valores[0].setText(String.valueOf(m.getCycles()));
        valores[1].setText(String.valueOf(m.getCompleted()));
        valores[2].setText(String.valueOf(m.getMissedDeadline()));
        valores[3].setText(String.format("%.3f", m.getThroughput()));
        valores[4].setText(String.format("%.1f %%", m.getCpuUtilization() * 100));
        valores[5].setText(fmt(m.getAvgResponseTime(), "%.2f"));
        valores[6].setText(Double.isNaN(m.getDeadlineCompliance()) ? "-" : String.format("%.1f %%", m.getDeadlineCompliance() * 100));
        valores[7].setText(fmt(m.getFairness(), "%.3f"));
        valores[8].setText(String.valueOf(m.getSuspensions()));
        valores[9].setText(String.valueOf(m.getReactivations()));
        DiskMetrics d = m.getDisk();
        lblAtendidas.setText(String.valueOf(d.getServed()));
        lblDesplazamiento.setText(String.valueOf(d.getTotalDisplacement()));
        lblEspera.setText(String.format("%.2f ciclos", d.getAverageWaitCycles()));
        DiskSchedulingPolicy[] ps = DiskSchedulingPolicy.values();
        for (int i = 0; i < ps.length; i++) {
            porPolitica[i].setText(d.getServedBy(ps[i]) + " / " + d.getDisplacementBy(ps[i]));
        }
    }

    private static String fmt(double v, String f) {
        return Double.isNaN(v) ? "-" : String.format(f, v);
    }

    public String getValor(int i) { return valores[i].getText(); }
    public String getTextoAtendidas() { return lblAtendidas.getText(); }
    public String getTextoDesplazamiento() { return lblDesplazamiento.getText(); }
}
