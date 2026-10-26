/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package avilaos.model.hardware;

/**
 * Copia de solo lectura del historial de utilizacion de la CPU de un computador (P12):
 * una muestra por ciclo, de la mas antigua a la mas reciente. La grafica de I08 la lee
 * sin tocar el historial real, que sigue cambiando en el hilo del reloj.
 */
public class UtilizationSeries {

    private final long[] cycles;
    private final long[] busy;
    private final long[] total;

    /** Los arreglos ya vienen copiados desde UtilizationHistory. */
    UtilizationSeries(long[] cycles, long[] busy, long[] total) {
        this.cycles = cycles;
        this.busy = busy;
        this.total = total;
    }

    /** @return cantidad de muestras */
    public int size() { return cycles.length; }

    /** @return numero de ciclo global de la muestra i (0 es la mas antigua) */
    public long getCycle(int i) { return cycles[i]; }

    /** @return ciclos en que la CPU estuvo ocupada, acumulados hasta esa muestra */
    public long getBusyCycles(int i) { return busy[i]; }

    /** @return ciclos totales de la CPU acumulados hasta esa muestra */
    public long getTotalCycles(int i) { return total[i]; }

    /** @return utilizacion acumulada desde el inicio hasta esa muestra (0.0 a 1.0) */
    public double getUtilization(int i) {
        return total[i] == 0 ? 0.0 : (double) busy[i] / total[i];
    }

    /**
     * Utilizacion solo de los ultimos ciclos (una ventana movil): muestra mejor los cambios
     * recientes que la acumulada.
     * @param i indice de la muestra
     * @param window cuantos ciclos mirar hacia atras (mayor que 0)
     * @return fraccion de esos ciclos en que la CPU estuvo ocupada (0.0 a 1.0)
     */
    public double getRecentUtilization(int i, int window) {
        if (window <= 0) {
            throw new IllegalArgumentException("La ventana debe ser mayor que 0");
        }
        int j = Math.max(0, i - window);
        long ciclos = total[i] - (i == j ? 0 : total[j]);
        long ocupados = busy[i] - (i == j ? 0 : busy[j]);
        return ciclos <= 0 ? 0.0 : (double) ocupados / ciclos;
    }
}
