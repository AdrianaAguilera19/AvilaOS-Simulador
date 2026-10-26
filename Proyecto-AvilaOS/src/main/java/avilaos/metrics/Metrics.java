package avilaos.metrics;

/**
 * Resultado de las metricas de rendimiento de UN computador o de TODO el sistema (P11).
 * Es un objeto de solo lectura: la GUI lo muestra y no modifica nada.
 *
 * Los valores que no se pueden calcular todavia (por ejemplo, el cumplimiento de deadlines
 * cuando aun no ha terminado ningun proceso) valen Double.NaN. La GUI debe mostrar "-" en ese caso.
 */
public final class Metrics {

    private final String label;
    private final long cycles;
    private final int completed;
    private final int missedDeadline;
    private final double throughput;
    private final double cpuUtilization;
    private final double avgResponseTime;
    private final double deadlineCompliance;
    private final double fairness;

    public Metrics(String label, long cycles, int completed, int missedDeadline, double throughput,
                   double cpuUtilization, double avgResponseTime, double deadlineCompliance,
                   double fairness) {
        this.label = label;
        this.cycles = cycles;
        this.completed = completed;
        this.missedDeadline = missedDeadline;
        this.throughput = throughput;
        this.cpuUtilization = cpuUtilization;
        this.avgResponseTime = avgResponseTime;
        this.deadlineCompliance = deadlineCompliance;
        this.fairness = fairness;
    }

    /** @return nombre del computador, o "Sistema" para las metricas globales */
    public String getLabel() { return label; }
    /** @return ciclos transcurridos sobre los que se calculo */
    public long getCycles() { return cycles; }
    /** @return procesos que terminaron TODAS sus instrucciones */
    public int getCompleted() { return completed; }
    /** @return procesos terminados a la fuerza porque su deadline llego a 0 */
    public int getMissedDeadline() { return missedDeadline; }
    /** @return procesos completados por ciclo de reloj (multiplicar por 100 para "por 100 ciclos") */
    public double getThroughput() { return throughput; }
    /** @return fraccion de ciclos con la CPU ocupada, de 0.0 a 1.0 (en el sistema: promedio ponderado) */
    public double getCpuUtilization() { return cpuUtilization; }
    /** @return ciclos promedio entre la llegada de un proceso y su primera ejecucion; NaN si ninguno ha empezado */
    public double getAvgResponseTime() { return avgResponseTime; }
    /** @return completados / (completados + terminados por deadline), de 0.0 a 1.0; NaN si ninguno ha terminado */
    public double getDeadlineCompliance() { return deadlineCompliance; }
    /** @return indice de equidad de Jain, de 1/n (injusto) a 1.0 (equitativo); NaN si no hay procesos admitidos */
    public double getFairness() { return fairness; }

    @Override
    public String toString() {
        return String.format("%s | ciclos=%d completados=%d vencidos=%d | throughput=%.4f | CPU=%.1f%% | "
                + "respuesta=%s | deadlines=%s | equidad=%s",
                label, cycles, completed, missedDeadline, throughput, cpuUtilization * 100,
                fmt(avgResponseTime, "%.2f"), pct(deadlineCompliance), fmt(fairness, "%.3f"));
    }

    private static String fmt(double v, String f) {
        return Double.isNaN(v) ? "-" : String.format(f, v);
    }

    private static String pct(double v) {
        return Double.isNaN(v) ? "-" : String.format("%.1f%%", v * 100);
    }
}
