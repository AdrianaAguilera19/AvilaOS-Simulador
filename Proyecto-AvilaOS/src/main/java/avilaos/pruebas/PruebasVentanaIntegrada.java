/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package avilaos.pruebas;

import avilaos.enums.BlockUse;
import avilaos.enums.DiskSchedulingPolicy;
import avilaos.gui.PanelDisco;
import avilaos.gui.VentanaSimulacion;
import avilaos.metrics.DiskMetrics;
import avilaos.metrics.Metrics;
import avilaos.metrics.MetricsCalculator;
import avilaos.model.hardware.BlockInfo;
import avilaos.model.hardware.Computer;
import avilaos.model.hardware.ComputerSnapshot;
import avilaos.model.hardware.DiskSnapshot;
import avilaos.persistence.SimulationSetup;
import avilaos.persistence.SimulationSetup.SimulationBundle;

/**
 * G19, G20, G04, G05, G08, G09, G10, G11, G16, G21 y J17: la ventana integrada se cablea sola,
 * lee fotos inmutables y las metricas de P1 siguen coherentes con carga real (disco, suspension y reactivacion).
 */
public class PruebasVentanaIntegrada {

    private static int verificaciones = 0;
    private static int fallas = 0;

    private static final String FS = """
            {
              "diskConfig": { "totalBlocks": 200, "initialHead": 50 },
              "fileSystem": [
                { "path": "/mbr.bin", "type": "FILE", "startBlock": 0, "blocks": 11, "owner": "admin", "permissions": "public" },
                { "path": "/sistema", "type": "DIRECTORY", "owner": "admin", "permissions": "public" },
                { "path": "/usuarios", "type": "DIRECTORY", "owner": "user1", "permissions": "public" },
                { "path": "/sistema/boot.bin", "type": "FILE", "startBlock": 11, "blocks": 23, "owner": "admin", "permissions": "public" },
                { "path": "/usuarios/notas.txt", "type": "FILE", "startBlock": 34, "blocks": 28, "owner": "user1", "permissions": "public" },
                { "path": "/sistema/config.sys", "type": "FILE", "startBlock": 95, "blocks": 24, "owner": "admin", "permissions": "public" },
                { "path": "/usuarios/data.csv", "type": "FILE", "startBlock": 123, "blocks": 57, "owner": "user2", "permissions": "public" }
              ]
            }
            """;

    // RAM chica (64) y procesos de 30: no caben todos, asi que hay suspension y reactivacion de verdad
    private static final String PROCESOS = """
            {
              "globalConfig": { "cycleDurationMs": 100, "swapSizeBlocks": 100, "initialDiskPolicy": "SSTF" },
              "computer": { "ramCapacity": 64, "policy": "FCFS", "quantum": 0 },
              "processes": [
                { "name": "P1", "memory": 30, "deadline": 5000, "instructions": 3, "targetPath": "/sistema/config.sys", "operation": "READ" },
                { "name": "P2", "memory": 30, "deadline": 5000, "instructions": 3, "targetPath": "/usuarios/data.csv", "operation": "READ" },
                { "name": "P3", "memory": 30, "deadline": 5000, "instructions": 3, "targetPath": "/mbr.bin", "operation": "READ" },
                { "name": "P4", "memory": 30, "deadline": 5000, "instructions": 3, "targetPath": "/usuarios/notas.txt", "operation": "READ" },
                { "name": "P5", "memory": 30, "deadline": 5000, "instructions": 3, "targetPath": "/sistema/boot.bin", "operation": "READ" }
              ]
            }
            """;

    public static void main(String[] args) throws Exception {
        System.setProperty("java.awt.headless", "true");
        ventanaYCableado();
        fotosInmutables();
        selectorDePoliticaEnCaliente();
        mapaYColas();
        metricasConCargaReal();

        System.out.println();
        if (fallas == 0) {
            System.out.println("TODAS LAS PRUEBAS PASARON (" + verificaciones + " verificaciones)");
        } else {
            System.out.println("HAY " + fallas + " FALLAS de " + verificaciones + " verificaciones");
            System.exit(1);
        }
    }

    private static SimulationBundle armar() throws Exception {
        SimulationSetup setup = new SimulationSetup();
        setup.loadFileSystemJson(FS);
        setup.loadProcessConfigJson(PROCESOS);
        return setup.build();
    }

    private static void tick(VentanaSimulacion v, int n) {
        for (int i = 0; i < n; i++) {
            v.getCluster().tick();
        }
    }

    private static void ventanaYCableado() throws Exception {
        titulo("G19 - La ventana cablea el coordinador de disco al reloj y admite los procesos");
        VentanaSimulacion v = new VentanaSimulacion(armar());
        Computer pc = v.getCluster().getComputer(1);
        check(pc.takeSnapshot().totalProcesses() == 5, "los 5 procesos iniciales entraron al computador");
        check(v.getPanelDisco().getUltimaFoto().queueSize() == 0, "antes de arrancar la cola de E/S esta vacia");
        tick(v, 150);
        check(v.getRecordLog().size() > 0, "con solo cluster.tick() el disco atiende solicitudes (coordinador dentro del reloj)");
        check(v.getPanelDisco().getTextoPolitica().contains("SSTF"), "el indicador muestra la politica inicial SSTF (G10)");
        check(v.getPanelDisco().getTextoCabezal().startsWith("Cabezal: bloque "), "la etiqueta del cabezal tiene su posicion (G05)");
        check(v.getPanelDisco().getTextoCabezal().contains("↑") || v.getPanelDisco().getTextoCabezal().contains("↓"),
                "la etiqueta del cabezal incluye el sentido (flecha)");
        v.refresh();
        check(v.getPanelLog() != null && v.getCluster().getLogger().getTotalLogged() > 0, "el log tiene eventos");
        v.shutdown();
    }

    private static void fotosInmutables() throws Exception {
        titulo("G20 - DiskSnapshot es una foto: no cambia aunque el disco siga corriendo");
        VentanaSimulacion v = new VentanaSimulacion(armar());
        tick(v, 3);
        DiskSnapshot antes = DiskSnapshot.take(v.getCoordinator().getDisk(), v.getCoordinator().getDiskScheduler());
        int cabezal = antes.getHeadPosition();
        int cola = antes.queueSize();
        int libres = antes.freeCount();
        String bloque0 = antes.getBlock(0).toString();
        tick(v, 40);
        check(antes.getHeadPosition() == cabezal && antes.queueSize() == cola && antes.freeCount() == libres
                && antes.getBlock(0).toString().equals(bloque0), "la foto vieja conserva cabezal, cola y bloques");
        DiskSnapshot despues = DiskSnapshot.take(v.getCoordinator().getDisk(), v.getCoordinator().getDiskScheduler());
        check(despues.getHeadPosition() != cabezal || despues.queueSize() != cola || despues.freeCount() != libres,
                "una foto nueva si refleja los cambios");
        check(antes.totalBlocks() == 300, "la foto trae los 300 bloques (200 de archivos + 100 de swap)");
        ComputerSnapshot cs = v.getCluster().getComputer(1).takeSnapshot();
        check(cs.getDisk() == null, "ComputerSnapshot sin disco devuelve getDisk() null");
        ComputerSnapshot conDisco = cs.withDisk(despues);
        check(conDisco.getDisk() == despues && conDisco.totalProcesses() == cs.totalProcesses(),
                "withDisk adjunta la foto del disco y conserva las colas");
        v.shutdown();
    }

    private static void selectorDePoliticaEnCaliente() throws Exception {
        titulo("G09 + G10 - Cambio de politica de disco en caliente");
        VentanaSimulacion v = new VentanaSimulacion(armar());
        tick(v, 2);
        PanelDisco pd = v.getPanelDisco();
        check(pd.getComboPolitica().getItemCount() == 5, "el selector ofrece las 5 politicas");
        pd.getComboPolitica().setSelectedItem(DiskSchedulingPolicy.FIFO);
        check(v.getCoordinator().getDiskScheduler().getPolicy().getType() == DiskSchedulingPolicy.FIFO,
                "al elegir FIFO el planificador de disco cambia");
        check(pd.getTextoPolitica().contains("FIFO"), "el indicador pasa a FIFO");
        tick(v, 30);
        boolean log = v.getCluster().getLogger().getLastAsText(300).contains("cambia de politica");
        check(log, "el cambio queda en el log (G16)");
        pd.getComboPolitica().setSelectedItem(DiskSchedulingPolicy.C_SCAN);
        check(pd.getTextoPolitica().contains("C-SCAN"), "otro cambio: el indicador pasa a C-SCAN");
        v.shutdown();
    }

    private static void mapaYColas() throws Exception {
        titulo("G04 + G08 + G11 - Mapa de bloques, cola de E/S y colas de suspendidos");
        VentanaSimulacion v = new VentanaSimulacion(armar());
        BlockInfo libre = null;
        BlockInfo archivo = null;
        DiskSnapshot s = v.getPanelDisco().getUltimaFoto();
        for (int i = 0; i < s.totalBlocks(); i++) {
            BlockInfo b = s.getBlock(i);
            if (libre == null && b.isFree()) {
                libre = b;
            }
            if (archivo == null && b.getUse() == BlockUse.FILE) {
                archivo = b;
            }
        }
        check(libre != null && archivo != null, "el mapa tiene bloques libres y de archivo");
        check(!PanelDisco.colorDe(libre).equals(PanelDisco.colorDe(archivo)), "libre y archivo tienen colores distintos");
        check(PanelDisco.detalleDe(archivo).contains("archivo ") && PanelDisco.detalleDe(libre).endsWith("libre"),
                "el detalle dice quien ocupa cada bloque");

        boolean vioSwap = false;
        boolean vioListoSusp = false;
        boolean vioMotivo = false;
        boolean vioCola = false;
        int maxCola = 0;
        for (int i = 0; i < 3000; i++) {
            v.getCluster().tick();
            v.refresh();
            DiskSnapshot d = v.getPanelDisco().getUltimaFoto();
            for (int k = 0; k < d.totalBlocks(); k++) {
                if (d.getBlock(k).getUse() == BlockUse.SUSPENDED_PROCESS) {
                    BlockInfo sw = d.getBlock(k);
                    if (!vioSwap) check(!PanelDisco.colorDe(sw).equals(PanelDisco.colorDe(archivo)) && PanelDisco.detalleDe(sw).contains("swap"),
                            "un bloque de swap se distingue de uno de archivo y se describe como swap");
                    vioSwap = true;
                    k = d.totalBlocks();
                }
            }
            maxCola = Math.max(maxCola, d.queueSize());
            vioCola |= v.getPanelDisco().getTablaCola().getRowCount() > 0;
            ComputerSnapshot cs = v.getCluster().getComputer(1).takeSnapshot();
            vioListoSusp |= cs.getReadySuspendedQueue().size() + cs.getBlockedSuspendedQueue().size() > 0;
            for (int k = 0; k < cs.getBlockedQueue().size(); k++) {
                vioMotivo |= cs.getBlockedQueue().get(k).getBlockReason() != null;
            }
            if (v.getCluster().getComputer(1).takeSnapshot().getFinishedQueue().size() == 5 && i > 10) {
                break;
            }
        }
        check(vioListoSusp, "las colas de suspendidos tuvieron procesos (G11)");
        check(vioSwap, "el mapa mostro bloques de swap");
        check(vioCola, "la tabla de cola de E/S mostro solicitudes (G08)");
        check(maxCola >= 1, "la foto del disco trajo solicitudes en la cola");
        check(vioMotivo, "los procesos bloqueados traen su motivo de bloqueo (G11)");
        System.out.println("  (info) swap visto=" + vioSwap + " suspendidos vistos=" + vioListoSusp);
        check(v.getPanelColas().getTabla().getColumnCount() == 5, "la tabla de colas tiene 5 columnas");
        v.shutdown();
    }

    private static void metricasConCargaReal() throws Exception {
        titulo("J17 - Metricas de P1 coherentes con carga real (disco + suspension + reactivacion)");
        VentanaSimulacion v = new VentanaSimulacion(armar());
        for (int i = 0; i < 3000; i++) {
            v.getCluster().tick();
            if (v.getCluster().getComputer(1).takeSnapshot().getFinishedQueue().size() == 5) {
                break;
            }
        }
        v.refresh();
        Metrics m = MetricsCalculator.forSystem(v.getCluster(), v.getRecordLog());
        Computer pc = v.getCluster().getComputer(1);
        check(pc.takeSnapshot().getFinishedQueue().size() == 5, "los 5 procesos terminaron");
        check(m.getCompleted() + m.getMissedDeadline() == 5, "terminados + vencidos = 5 procesos");
        check(m.getCycles() == v.getCluster().getCurrentCycle(), "los ciclos de la metrica son los del reloj");
        check(Math.abs(m.getThroughput() - (double) m.getCompleted() / m.getCycles()) < 1e-9,
                "throughput = terminados / ciclos");
        check(m.getCpuUtilization() >= 0.0 && m.getCpuUtilization() <= 1.0, "uso de CPU entre 0 y 1");
        check(Double.isNaN(m.getDeadlineCompliance()) || (m.getDeadlineCompliance() >= 0 && m.getDeadlineCompliance() <= 1),
                "cumplimiento de deadline entre 0 y 1");
        check(m.getFairness() > 0 && m.getFairness() <= 1.0000001, "equidad en (0, 1]");
        DiskMetrics d = m.getDisk();
        check(d.getServed() == v.getRecordLog().size() && d.getServed() >= 5,
                "el disco atendio al menos 1 solicitud por proceso y coincide con el registro");
        long porPolitica = 0;
        long desplazPolitica = 0;
        for (DiskSchedulingPolicy p : DiskSchedulingPolicy.values()) {
            porPolitica += d.getServedBy(p);
            desplazPolitica += d.getDisplacementBy(p);
        }
        check(porPolitica == d.getServed() && desplazPolitica == d.getTotalDisplacement(),
                "la suma por politica coincide con el total de atendidas y de desplazamiento");
        check(m.getSuspensions() >= 1 && m.getReactivations() >= 1,
                "con esta carga hubo al menos 1 suspension y 1 reactivacion completas");
        check(m.getSuspensions() >= m.getReactivations(), "nunca hay mas reactivaciones que suspensiones");
        check(pc.getMediumTermScheduler().getSuspensionCount() == m.getSuspensions(),
                "las suspensiones de la metrica son las del planificador de mediano plazo");
        System.out.println("  (info) " + m);
        check(v.getPanelMetricas().getTextoAtendidas().equals(String.valueOf(d.getServed())),
                "el panel de metricas muestra las atendidas del disco (G21)");
        check(v.getPanelMetricas().getValor(1).equals(String.valueOf(m.getCompleted())),
                "el panel de metricas muestra los terminados de P1 (G21)");
        v.shutdown();
    }

    private static void check(boolean ok, String texto) {
        verificaciones++;
        System.out.println((ok ? "  OK    " : "  FALLA ") + texto);
        if (!ok) {
            fallas++;
        }
    }

    private static void titulo(String texto) {
        System.out.println();
        System.out.println("== " + texto);
    }
}
