/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package avilaos.metrics;

import avilaos.enums.DiskSchedulingPolicy;
import avilaos.os.disk.DiskRecord;
import avilaos.os.disk.DiskRecordLog;

/**
 * Datos de la grafica del desplazamiento acumulado del cabezal contra el tiempo (J15, J16).
 * Hay un punto por cada solicitud atendida: el ciclo en que termino, el desplazamiento acumulado total
 * hasta ese momento y la politica con que se atendio. De ahi salen:
 *  - la serie total (una sola linea),
 *  - una serie por politica (lo que cada politica ha aportado al desplazamiento, para compararlas),
 *  - las marcas de cambio de politica (los puntos donde la politica es distinta a la del punto anterior).
 * Es una foto inmutable: se vuelve a crear en cada refresco de la GUI.
 */
public final class DiskDisplacementSeries {

    private final long[] cycles;
    private final long[] cumulative;
    private final DiskSchedulingPolicy[] policies;

    private DiskDisplacementSeries(long[] cycles, long[] cumulative, DiskSchedulingPolicy[] policies) {
        this.cycles = cycles;
        this.cumulative = cumulative;
        this.policies = policies;
    }

    /** Arma la serie con lo que el disco lleva atendido. Acepta null (serie vacia). */
    public static DiskDisplacementSeries from(DiskRecordLog log) {
        DiskRecord[] all = log == null ? new DiskRecord[0] : log.snapshot();
        long[] c = new long[all.length];
        long[] acc = new long[all.length];
        DiskSchedulingPolicy[] p = new DiskSchedulingPolicy[all.length];
        long total = 0;
        for (int i = 0; i < all.length; i++) {
            total += all[i].getDisplacement();
            c[i] = all[i].getEndCycle();
            acc[i] = total;
            p[i] = all[i].getPolicy();
        }
        return new DiskDisplacementSeries(c, acc, p);
    }

    /** @return cantidad de puntos (solicitudes atendidas) */
    public int size() { return cycles.length; }

    /** @return ciclo en que termino la solicitud i */
    public long cycleAt(int i) { return cycles[i]; }

    /** @return desplazamiento total acumulado hasta la solicitud i (incluida) */
    public long cumulativeAt(int i) { return cumulative[i]; }

    /** @return politica con que se atendio la solicitud i */
    public DiskSchedulingPolicy policyAt(int i) { return policies[i]; }

    /** @return desplazamiento acumulado total al final (0 si no hay puntos) */
    public long total() { return cycles.length == 0 ? 0 : cumulative[cumulative.length - 1]; }

    /**
     * Serie de una politica: para cada punto, cuanto desplazamiento han aportado hasta ese momento las
     * solicitudes atendidas con esa politica (crece solo mientras esa politica esta en uso).
     */
    public long[] cumulativeOf(DiskSchedulingPolicy policy) {
        long[] r = new long[cycles.length];
        long acc = 0;
        for (int i = 0; i < r.length; i++) {
            long step = cumulative[i] - (i == 0 ? 0 : cumulative[i - 1]);
            if (policies[i] == policy) {
                acc += step;
            }
            r[i] = acc;
        }
        return r;
    }

    /** @return indices de los puntos donde la politica cambia respecto del punto anterior */
    public int[] policyChangeIndexes() {
        int n = 0;
        for (int i = 1; i < policies.length; i++) {
            if (policies[i] != policies[i - 1]) {
                n++;
            }
        }
        int[] r = new int[n];
        int k = 0;
        for (int i = 1; i < policies.length; i++) {
            if (policies[i] != policies[i - 1]) {
                r[k++] = i;
            }
        }
        return r;
    }
}