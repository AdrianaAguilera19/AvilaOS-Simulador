/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package avilaos.pruebas;

import avilaos.enums.DiskSchedulingPolicy;
import avilaos.enums.IOOperationType;
import avilaos.enums.SchedulingAlgorithm;
import avilaos.metrics.DiskDisplacementSeries;
import avilaos.metrics.DiskMetrics;
import avilaos.metrics.Metrics;
import avilaos.metrics.MetricsCalculator;
import avilaos.model.hardware.Cluster;
import avilaos.model.hardware.Computer;
import avilaos.model.process.PCB;
import avilaos.model.process.ProcessFactory;
import avilaos.os.disk.DiskRecordLog;
import avilaos.os.disk.DiskRequest;
import avilaos.scheduler.PolicyFactory;

/**
 * J12, J13, J14, J16 y J18: metricas del disco. Desplazamiento total del cabezal, espera promedio en la
 * cola de E/S, solicitudes atendidas por politica (con cambios de politica en caliente), datos de la
 * grafica (acumulado, una serie por politica y marcas de cambio) e integracion en Metrics y
 * MetricsCalculator sin romper las metricas del P1.
 * Se ejecuta como programa (main); termina con "TODAS LAS PRUEBAS PASARON" o con codigo 1.
 */
public class PruebasMetricasDisco {

    private static int fallas = 0;
    private static int verificaciones = 0;

    public static void main(String[] args) {
        sinActividad();
        metricasConCuatroSolicitudes();
        serieYMarcasDeCambio();
        integracionEnMetrics();

        System.out.println();
        if (fallas == 0) {
            System.out.println("TODAS LAS PRUEBAS PASARON (" + verificaciones + " verificaciones)");
        } else {
            System.out.println("HAY " + fallas + " FALLAS de " + verificaciones + " verificaciones");
            System.exit(1);
        }
    }

    /** Registro con 4 solicitudes: 2 FIFO, 1 SSTF y 1 LOOK (cambios de politica en caliente). */
    private static DiskRecordLog registro() {
        PCB p = ProcessFactory.createCpuBound("P", 1, 10, 5, 500, 1, 0);
        DiskRecordLog log = new DiskRecordLog();
        anotar(log, p, 50, 0, 2, 10, 10, DiskSchedulingPolicy.FIFO);    // desplaza 40, espera 2
        anotar(log, p, 20, 3, 10, 15, 50, DiskSchedulingPolicy.FIFO);   // desplaza 30, espera 7
        anotar(log, p, 25, 12, 15, 18, 20, DiskSchedulingPolicy.SSTF);  // desplaza 5, espera 3
        anotar(log, p, 5, 14, 18, 25, 25, DiskSchedulingPolicy.LOOK);   // desplaza 20, espera 4
        return log;
    }

    private static void anotar(DiskRecordLog log, PCB p, int bloque, long llegada, long inicio, long fin,
            int cabezalAntes, DiskSchedulingPolicy politica) {
        DiskRequest r = new DiskRequest(bloque, IOOperationType.READ, p, llegada, 1);
        r.markServed(politica);
        log.record(r, inicio, fin, cabezalAntes);
    }

    private static void sinActividad() {
        titulo("Sin solicitudes atendidas todo vale 0");
        DiskMetrics m = DiskMetrics.from(new DiskRecordLog());
        check(m.getServed() == 0 && m.getTotalDisplacement() == 0 && m.getAverageWaitCycles() == 0, "0 atendidas, 0 desplazamiento, 0 espera");
        boolean ceros = true;
        for (DiskSchedulingPolicy p : DiskSchedulingPolicy.values()) {
            ceros = ceros && m.getServedBy(p) == 0 && m.getDisplacementBy(p) == 0;
        }
        check(ceros, "ninguna politica tiene atenciones");
        check(DiskMetrics.from(null).getServed() == 0, "un registro null no rompe: todo en 0");
        check(DiskDisplacementSeries.from(null).size() == 0 && DiskDisplacementSeries.from(null).total() == 0, "la serie de un registro null es vacia");
    }

    private static void metricasConCuatroSolicitudes() {
        titulo("J12, J13 y J14 con cuatro solicitudes y dos cambios de politica");
        DiskMetrics m = DiskMetrics.from(registro());
        check(m.getServed() == 4, "4 solicitudes atendidas");
        check(m.getTotalDisplacement() == 95, "J12: desplazamiento total 40+30+5+20 = 95 (" + m.getTotalDisplacement() + ")");
        check(Math.abs(m.getAverageWaitCycles() - 4.0) < 1e-9, "J13: espera promedio (2+7+3+4)/4 = 4.0 (" + m.getAverageWaitCycles() + ")");
        check(m.getServedBy(DiskSchedulingPolicy.FIFO) == 2 && m.getServedBy(DiskSchedulingPolicy.SSTF) == 1
                && m.getServedBy(DiskSchedulingPolicy.LOOK) == 1, "J14: FIFO 2, SSTF 1, LOOK 1");
        check(m.getServedBy(DiskSchedulingPolicy.SCAN) == 0 && m.getServedBy(DiskSchedulingPolicy.C_SCAN) == 0, "SCAN y C-SCAN 0");
        check(m.getDisplacementBy(DiskSchedulingPolicy.FIFO) == 70 && m.getDisplacementBy(DiskSchedulingPolicy.SSTF) == 5
                && m.getDisplacementBy(DiskSchedulingPolicy.LOOK) == 20, "desplazamiento por politica: FIFO 70, SSTF 5, LOOK 20");
        check(m.toString().contains("atendidas=4") && m.toString().contains("desplazamiento=95"), "el texto muestra los valores");
    }

    private static void serieYMarcasDeCambio() {
        titulo("J15 y J16: serie acumulada, una serie por politica y marcas de cambio");
        DiskDisplacementSeries s = DiskDisplacementSeries.from(registro());
        check(s.size() == 4 && s.total() == 95, "4 puntos y 95 al final");
        check(s.cycleAt(0) == 10 && s.cycleAt(3) == 25, "el eje del tiempo es el ciclo en que termina cada solicitud");
        check(s.cumulativeAt(0) == 40 && s.cumulativeAt(1) == 70 && s.cumulativeAt(2) == 75 && s.cumulativeAt(3) == 95,
                "acumulado 40, 70, 75, 95");
        long[] fifo = s.cumulativeOf(DiskSchedulingPolicy.FIFO);
        long[] sstf = s.cumulativeOf(DiskSchedulingPolicy.SSTF);
        long[] look = s.cumulativeOf(DiskSchedulingPolicy.LOOK);
        check(fifo[0] == 40 && fifo[1] == 70 && fifo[2] == 70 && fifo[3] == 70, "serie FIFO: 40, 70, 70, 70");
        check(sstf[0] == 0 && sstf[2] == 5 && sstf[3] == 5, "serie SSTF: 0, 0, 5, 5");
        check(look[2] == 0 && look[3] == 20, "serie LOOK: 0, 0, 0, 20");
        int[] marcas = s.policyChangeIndexes();
        check(marcas.length == 2 && marcas[0] == 2 && marcas[1] == 3, "marcas de cambio de politica en los puntos 2 (FIFO->SSTF) y 3 (SSTF->LOOK)");
        check(s.policyAt(2) == DiskSchedulingPolicy.SSTF, "cada punto sabe con que politica se atendio");
    }

    private static void integracionEnMetrics() {
        titulo("J18: las metricas de disco conviven con las del P1");
        Cluster c = new Cluster(100, 0);
        Computer pc = c.addComputer("PC1", PolicyFactory.create(SchedulingAlgorithm.FCFS, 2), 50);
        PCB p = ProcessFactory.createCpuBound("A", 1, 10, 8, 500, pc.getId(), 0);
        c.submit(p);
        for (int i = 0; i < 20; i++) {
            c.tick();
        }
        Metrics sin = MetricsCalculator.forSystem(c);
        Metrics con = MetricsCalculator.forSystem(c, registro());
        check(sin.getDisk().getServed() == 0, "sin registro de disco las metricas del disco valen 0");
        check(con.getDisk().getTotalDisplacement() == 95 && con.getDisk().getServed() == 4, "con registro salen 95 de desplazamiento y 4 atendidas");
        check(con.getCompleted() == sin.getCompleted() && con.getThroughput() == sin.getThroughput()
                && con.getCpuUtilization() == sin.getCpuUtilization(), "las del P1 (completados, throughput, CPU) no cambian");
        check(con.getCompleted() == 1 && con.getMissedDeadline() == 0, "el proceso termino y las metricas del P1 lo cuentan");
        Metrics comp = MetricsCalculator.forComputer(pc, registro());
        check(comp.getDisk().getTotalDisplacement() == 95, "por computador tambien");
        check(con.toString().contains("throughput=") && con.toString().contains("desplazamiento=95"), "el texto junta las metricas del P1 y las del disco");
    }

    private static void titulo(String texto) {
        System.out.println("--- " + texto + " ---");
    }

    private static void check(boolean ok, String texto) {
        verificaciones++;
        System.out.println((ok ? "  OK    " : "  FALLA ") + texto);
        if (!ok) {
            fallas++;
        }
    }
}