package avilaos.metrics;

import avilaos.datastructures.CustomLinkedList;
import avilaos.datastructures.CustomQueue;
import avilaos.enums.ProcessState;
import avilaos.model.hardware.Cluster;
import avilaos.model.hardware.Computer;
import avilaos.model.hardware.ComputerSnapshot;
import avilaos.model.process.PCB;

/**
 * Calcula las metricas de rendimiento (P11) a partir del estado actual del simulador.
 * No guarda nada ni modifica nada: cada llamada mira una foto consistente de los computadores.
 *
 * COMO SE CALCULA CADA METRICA (definiciones del equipo):
 *  - Throughput: procesos que terminaron todas sus instrucciones / ciclos transcurridos.
 *    Los procesos terminados por deadline NO cuentan como completados.
 *  - Utilizacion de CPU: ciclos con la CPU ocupada (instrucciones de usuario o del SO) / ciclos totales.
 *    En el sistema: suma de ciclos ocupados de todas las CPU / suma de sus ciclos totales.
 *  - Tiempo de respuesta promedio: (ciclo de primera ejecucion - ciclo de llegada), promediado sobre
 *    los procesos que ya usaron la CPU al menos una vez.
 *  - Cumplimiento de deadlines: completados / (completados + terminados por deadline).
 *  - Equidad: indice de Jain  J = (suma x)^2 / (n * suma x^2), donde x es el progreso de cada proceso
 *    admitido (no cuenta los que siguen en Nuevos): 1.0 si termino normalmente; si no,
 *    instrucciones ejecutadas / instrucciones totales. J=1 si todos avanzan igual; baja si alguno se queda sin CPU.
 */
public final class MetricsCalculator {

    private MetricsCalculator() { }

    /**
     * @param computer computador a medir
     * @return metricas de ese computador desde el inicio de la simulacion
     */
    public static Metrics forComputer(Computer computer) {
        Accumulator acc = new Accumulator();
        acc.add(computer);
        return acc.toMetrics(computer.getName(), computer.getCpu().getTotalCycles());
    }

    /**
     * @param cluster sistema completo
     * @return metricas globales sumando todos los computadores
     */
    public static Metrics forSystem(Cluster cluster) {
        Accumulator acc = new Accumulator();
        CustomLinkedList<Computer> computers = cluster.getComputersSnapshot();
        for (int i = 0; i < computers.size(); i++) {
            acc.add(computers.get(i));
        }
        return acc.toMetrics("Sistema", cluster.getCurrentCycle());
    }

    /** Va sumando los datos de uno o varios computadores y al final calcula las metricas. */
    private static final class Accumulator {
        private long busyCycles;
        private long totalCycles;
        private int completed;
        private int missed;
        private long responseSum;
        private int responseCount;
        private int admitted;
        private double sumX;
        private double sumX2;

        void add(Computer computer) {
            ComputerSnapshot s = computer.takeSnapshot();   // foto consistente (P09)
            busyCycles += computer.getCpu().getBusyCycles();
            totalCycles += computer.getCpu().getTotalCycles();

            if (s.getRunning() != null) {
                addProcess(s.getRunning());
            }
            addQueue(s.getReadyQueue());
            addQueue(s.getBlockedQueue());
            CustomQueue<PCB> finished = s.getFinishedQueue();
            for (int i = 0; i < finished.size(); i++) {
                PCB p = finished.get(i);
                if (p.isDeadlineMissed()) {
                    missed++;
                } else {
                    completed++;
                }
                addProcess(p);
            }
            // La cola de Nuevos no cuenta: esos procesos aun no fueron admitidos (esperan RAM)
        }

        private void addQueue(CustomQueue<PCB> queue) {
            for (int i = 0; i < queue.size(); i++) {
                addProcess(queue.get(i));
            }
        }

        private void addProcess(PCB p) {
            if (p.getFirstExecutionCycle() != PCB.NOT_STARTED) {
                responseSum += Math.max(0, p.getFirstExecutionCycle() - p.getArrivalCycle());
                responseCount++;
            }
            double x = progress(p);
            admitted++;
            sumX += x;
            sumX2 += x * x;
        }

        private static double progress(PCB p) {
            if (p.getState() == ProcessState.TERMINATED && !p.isDeadlineMissed()) {
                return 1.0;
            }
            double done = p.getTotalInstructions() - p.getRemainingTime();
            return Math.min(1.0, Math.max(0.0, done / p.getTotalInstructions()));
        }

        Metrics toMetrics(String label, long cycles) {
            double throughput = cycles > 0 ? (double) completed / cycles : 0.0;
            double utilization = totalCycles > 0 ? (double) busyCycles / totalCycles : 0.0;
            double avgResponse = responseCount > 0 ? (double) responseSum / responseCount : Double.NaN;
            int terminated = completed + missed;
            double compliance = terminated > 0 ? (double) completed / terminated : Double.NaN;
            double fairness;
            if (admitted == 0) {
                fairness = Double.NaN;
            } else if (sumX2 == 0) {
                fairness = 1.0;     // nadie ha avanzado: todos tienen lo mismo (nada)
            } else {
                fairness = (sumX * sumX) / (admitted * sumX2);
            }
            return new Metrics(label, cycles, completed, missed, throughput, utilization,
                    avgResponse, compliance, fairness);
        }
    }
}
