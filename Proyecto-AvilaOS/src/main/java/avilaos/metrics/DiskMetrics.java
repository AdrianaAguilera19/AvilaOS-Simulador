/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package avilaos.metrics;

import avilaos.enums.DiskSchedulingPolicy;
import avilaos.os.disk.DiskRecord;
import avilaos.os.disk.DiskRecordLog;

/**
 * Metricas del disco (J12, J13, J14): foto de solo lectura calculada a partir del registro de
 * solicitudes atendidas (DiskRecordLog).
 *  - Desplazamiento total del cabezal: suma de |bloque pedido - posicion del cabezal al empezar|.
 *  - Espera promedio en la cola de E/S: (ciclo de inicio del servicio - ciclo de llegada) promediado.
 *  - Solicitudes atendidas por politica: un contador por politica, aunque la politica se cambie en caliente
 *    (cada solicitud cuenta para la politica con que se atendio).
 */
public final class DiskMetrics {

    private final int served;
    private final long totalDisplacement;
    private final double averageWaitCycles;
    private final int[] servedByPolicy;
    private final long[] displacementByPolicy;

    private DiskMetrics(int served, long totalDisplacement, double averageWaitCycles,
            int[] servedByPolicy, long[] displacementByPolicy) {
        this.served = served;
        this.totalDisplacement = totalDisplacement;
        this.averageWaitCycles = averageWaitCycles;
        this.servedByPolicy = servedByPolicy;
        this.displacementByPolicy = displacementByPolicy;
    }

    /** Metricas de un disco que todavia no atendio nada (todo en 0). */
    public static DiskMetrics empty() {
        int n = DiskSchedulingPolicy.values().length;
        return new DiskMetrics(0, 0, 0, new int[n], new long[n]);
    }

    /** Calcula las metricas con lo que el disco lleva atendido. Acepta null (devuelve todo en 0). */
    public static DiskMetrics from(DiskRecordLog log) {
        if (log == null) {
            return empty();
        }
        DiskRecord[] all = log.snapshot();
        int n = DiskSchedulingPolicy.values().length;
        int[] count = new int[n];
        long[] disp = new long[n];
        long total = 0;
        long wait = 0;
        for (int i = 0; i < all.length; i++) {
            int p = all[i].getPolicy().ordinal();
            count[p]++;
            disp[p] += all[i].getDisplacement();
            total += all[i].getDisplacement();
            wait += all[i].getWaitCycles();
        }
        double avg = all.length == 0 ? 0 : (double) wait / all.length;
        return new DiskMetrics(all.length, total, avg, count, disp);
    }

    /** @return solicitudes atendidas en total */
    public int getServed() { return served; }

    /** @return J12: suma de los desplazamientos del cabezal, en bloques */
    public long getTotalDisplacement() { return totalDisplacement; }

    /** @return J13: ciclos promedio que una solicitud espera en la cola antes de que la atiendan (0 si no hay) */
    public double getAverageWaitCycles() { return averageWaitCycles; }

    /** @return J14: solicitudes atendidas con esa politica */
    public int getServedBy(DiskSchedulingPolicy policy) { return servedByPolicy[policy.ordinal()]; }

    /** @return desplazamiento del cabezal acumulado por las solicitudes atendidas con esa politica */
    public long getDisplacementBy(DiskSchedulingPolicy policy) { return displacementByPolicy[policy.ordinal()]; }

    /** @return una linea con todas las metricas del disco, lista para mostrar */
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("disco: atendidas=%d | desplazamiento=%d | espera promedio=%.2f | por politica:",
                served, totalDisplacement, averageWaitCycles));
        DiskSchedulingPolicy[] ps = DiskSchedulingPolicy.values();
        for (int i = 0; i < ps.length; i++) {
            sb.append(' ').append(ps[i]).append('=').append(servedByPolicy[i]);
        }
        return sb.toString();
    }
}