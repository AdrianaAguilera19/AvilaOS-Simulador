/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package avilaos.model.hardware;

import java.util.concurrent.Semaphore;

/**
 * Historial de la utilizacion de la CPU de UN computador a lo largo del tiempo (P12):
 * cada ciclo el computador anota cuantos ciclos lleva ocupada su CPU y cuantos lleva en
 * total. Sirve para la grafica comparativa de I08.
 * Guarda solo las ultimas MAX_SAMPLES muestras (la mas antigua se descarta) para que la
 * memoria no crezca sin limite en simulaciones largas.
 * Lo escribe el hilo del reloj y lo lee la GUI, por eso protege sus datos con un semaforo
 * y entrega copias (UtilizationSeries).
 */
public class UtilizationHistory {

    /** Maximo de muestras guardadas (un ciclo = una muestra). */
    public static final int MAX_SAMPLES = 10000;

    private final long[] cycles = new long[MAX_SAMPLES];
    private final long[] busy = new long[MAX_SAMPLES];
    private final long[] total = new long[MAX_SAMPLES];
    private int start = 0;    // posicion de la muestra mas antigua
    private int count = 0;    // cuantas muestras hay
    private final Semaphore lock = new Semaphore(1);

    /**
     * Anota la utilizacion de un ciclo.
     * @param cycle ciclo global
     * @param busyCycles ciclos en que la CPU estuvo ocupada hasta ahora
     * @param totalCycles ciclos totales de la CPU hasta ahora (mayor que 0)
     */
    public void record(long cycle, long busyCycles, long totalCycles) {
        if (totalCycles <= 0 || busyCycles < 0 || busyCycles > totalCycles) {
            throw new IllegalArgumentException("Utilizacion invalida: " + busyCycles + " de " + totalCycles);
        }
        lock.acquireUninterruptibly();
        try {
            int idx;
            if (count < MAX_SAMPLES) {
                idx = (start + count) % MAX_SAMPLES;
                count++;
            } else {
                idx = start;                       // esta llena: reemplaza la mas antigua
                start = (start + 1) % MAX_SAMPLES;
            }
            cycles[idx] = cycle;
            busy[idx] = busyCycles;
            total[idx] = totalCycles;
        } finally {
            lock.release();
        }
    }

    /** @return cantidad de muestras guardadas */
    public int size() {
        lock.acquireUninterruptibly();
        try {
            return count;
        } finally {
            lock.release();
        }
    }

    /** @return copia de solo lectura del historial, de la muestra mas antigua a la mas reciente */
    public UtilizationSeries snapshot() {
        lock.acquireUninterruptibly();
        try {
            long[] c = new long[count];
            long[] b = new long[count];
            long[] t = new long[count];
            for (int i = 0; i < count; i++) {
                int idx = (start + i) % MAX_SAMPLES;
                c[i] = cycles[idx];
                b[i] = busy[idx];
                t[i] = total[idx];
            }
            return new UtilizationSeries(c, b, t);
        } finally {
            lock.release();
        }
    }
}
