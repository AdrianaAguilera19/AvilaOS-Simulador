/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package avilaos.pruebas;

import avilaos.enums.DiskSchedulingPolicy;
import avilaos.enums.IOOperationType;
import avilaos.gui.PanelGraficaDesplazamiento;
import avilaos.gui.VentanaSimulacion;
import avilaos.model.process.PCB;
import avilaos.model.process.ProcessFactory;
import avilaos.os.disk.DiskRecordLog;
import avilaos.os.disk.DiskRequest;
import avilaos.persistence.SimulationSetup;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.image.BufferedImage;
import javax.swing.JTabbedPane;

/**
 * J15: grafica del desplazamiento acumulado del cabezal contra el tiempo. Se comprueba que el panel
 * toma los datos del registro del disco (una serie por politica usada, el total y las marcas de cambio
 * de politica), que solo se vuelve a armar cuando hay solicitudes nuevas, que se dibuja con los colores
 * de cada politica sin fallar (incluso con miles de puntos) y que esta como pestana de la ventana principal.
 * Se ejecuta como programa (main); termina con "TODAS LAS PRUEBAS PASARON" o con codigo 1.
 */
public class PruebasGraficaDesplazamiento {

    private static int fallas = 0;
    private static int verificaciones = 0;

    private static final int MIN_PIXELES_LINEA = 300;

    private static final String JSON_FS = """
            {
              "diskConfig": { "totalBlocks": 200, "initialHead": 50 },
              "fileSystem": [
                { "path": "/sistema", "type": "DIRECTORY", "owner": "admin", "permissions": "public" },
                { "path": "/sistema/boot.bin", "type": "FILE", "startBlock": 11, "blocks": 23, "owner": "admin", "permissions": "public" },
                { "path": "/sistema/core.dll", "type": "FILE", "startBlock": 64, "blocks": 31, "owner": "admin", "permissions": "public" },
                { "path": "/sistema/config.sys", "type": "FILE", "startBlock": 95, "blocks": 24, "owner": "admin", "permissions": "public" }
              ]
            }
            """;

    private static final String JSON_PROCESOS = """
            {
              "globalConfig": { "cycleDurationMs": 100, "swapSizeBlocks": 100, "initialDiskPolicy": "SSTF" },
              "computer": { "ramCapacity": 1024, "policy": "FCFS", "quantum": 0 },
              "processes": [
                { "name": "P_a", "memory": 16, "deadline": 5000, "instructions": 2, "targetPath": "/sistema/config.sys", "operation": "READ" },
                { "name": "P_b", "memory": 16, "deadline": 5000, "instructions": 2, "targetPath": "/sistema/core.dll", "operation": "READ" },
                { "name": "P_c", "memory": 16, "deadline": 5000, "instructions": 2, "targetPath": "/sistema/boot.bin", "operation": "READ" }
              ]
            }
            """;

    public static void main(String[] args) throws Exception {
        sinSolicitudes();
        unaSeriePorPolitica();
        soloSeRearmaConDatosNuevos();
        sePintaConLosColoresDeCadaPolitica();
        conMilesDePuntos();
        dentroDeLaVentanaPrincipal();

        System.out.println();
        if (fallas == 0) {
            System.out.println("TODAS LAS PRUEBAS PASARON (" + verificaciones + " verificaciones)");
        } else {
            System.out.println("HAY " + fallas + " FALLAS de " + verificaciones + " verificaciones");
            System.exit(1);
        }
    }

    /** Registro con 4 solicitudes: 2 FIFO, 1 SSTF y 1 LOOK (igual que en PruebasMetricasDisco). */
    private static DiskRecordLog registro() {
        PCB p = ProcessFactory.createCpuBound("P", 1, 10, 5, 500, 1, 0);
        DiskRecordLog log = new DiskRecordLog();
        anotar(log, p, 50, 0, 2, 10, 10, DiskSchedulingPolicy.FIFO);    // desplaza 40
        anotar(log, p, 20, 3, 10, 15, 50, DiskSchedulingPolicy.FIFO);   // desplaza 30
        anotar(log, p, 25, 12, 15, 18, 20, DiskSchedulingPolicy.SSTF);  // desplaza 5
        anotar(log, p, 5, 14, 18, 25, 25, DiskSchedulingPolicy.LOOK);   // desplaza 20
        return log;
    }

    private static void anotar(DiskRecordLog log, PCB p, int bloque, long llegada, long inicio, long fin,
            int cabezalAntes, DiskSchedulingPolicy politica) {
        DiskRequest r = new DiskRequest(bloque, IOOperationType.READ, p, llegada, 1);
        r.markServed(politica);
        log.record(r, inicio, fin, cabezalAntes);
    }

    private static void sinSolicitudes() {
        titulo("Sin solicitudes atendidas la grafica esta vacia y no falla");
        PanelGraficaDesplazamiento panel = new PanelGraficaDesplazamiento(new DiskRecordLog());
        check(panel.getSerie().size() == 0 && panel.getPoliticasUsadas().length == 0, "0 puntos y ninguna politica con linea");
        check(panel.getTextoResumen().contains("Sin solicitudes"), "el resumen avisa que todavia no hay solicitudes");
        check(pintar(panel, 700, 320) != null, "se dibuja el mensaje de 'todavia no hay datos' sin errores");
        PanelGraficaDesplazamiento sinRegistro = new PanelGraficaDesplazamiento(null);
        check(sinRegistro.getSerie().size() == 0 && pintar(sinRegistro, 700, 320) != null, "un registro null tampoco rompe");
    }

    private static void unaSeriePorPolitica() {
        titulo("J15: una serie por politica usada, el total y las marcas de cambio");
        PanelGraficaDesplazamiento panel = new PanelGraficaDesplazamiento(registro());
        check(panel.getSerie().size() == 4 && panel.getSerie().total() == 95, "4 puntos y 95 bloques de desplazamiento total");
        DiskSchedulingPolicy[] usadas = panel.getPoliticasUsadas();
        check(usadas.length == 3 && usadas[0] == DiskSchedulingPolicy.FIFO && usadas[1] == DiskSchedulingPolicy.SSTF
                && usadas[2] == DiskSchedulingPolicy.LOOK, "tienen linea FIFO, SSTF y LOOK (las tres que se usaron)");
        check(panel.getSerie().policyChangeIndexes().length == 2, "2 marcas de cambio de politica");
        String t = panel.getTextoResumen();
        check(t.contains("4 solicitudes") && t.contains("95") && t.contains("2 cambios"),
                "el resumen dice 4 solicitudes, 95 bloques y 2 cambios de politica (" + t + ")");
        long[] fifo = panel.getSerie().cumulativeOf(DiskSchedulingPolicy.FIFO);
        check(fifo[3] == 70, "la serie de FIFO termina en 70");
        boolean distintos = !colorDe(DiskSchedulingPolicy.FIFO).equals(colorDe(DiskSchedulingPolicy.SSTF));
        for (DiskSchedulingPolicy a : DiskSchedulingPolicy.values()) {
            for (DiskSchedulingPolicy b : DiskSchedulingPolicy.values()) {
                if (a != b && colorDe(a).equals(colorDe(b))) {
                    distintos = false;
                }
            }
        }
        check(distintos, "cada politica tiene su propio color");
    }

    private static void soloSeRearmaConDatosNuevos() {
        titulo("refresh() solo vuelve a armar la serie si hay solicitudes nuevas");
        DiskRecordLog log = registro();
        PanelGraficaDesplazamiento panel = new PanelGraficaDesplazamiento(log);
        Object antes = panel.getSerie();
        panel.refresh();
        check(panel.getSerie() == antes, "sin solicitudes nuevas no se rehace la foto");
        PCB p = ProcessFactory.createCpuBound("Q", 1, 10, 5, 500, 1, 0);
        anotar(log, p, 100, 20, 25, 30, 5, DiskSchedulingPolicy.C_SCAN);   // desplaza 95
        panel.refresh();
        check(panel.getSerie() != antes && panel.getSerie().size() == 5 && panel.getSerie().total() == 190,
                "con una solicitud nueva la serie pasa a 5 puntos y 190 bloques");
        check(panel.getPoliticasUsadas().length == 4 && panel.getTextoResumen().contains("3 cambios"),
                "aparece la linea de C-SCAN y el resumen cuenta 3 cambios");
    }

    private static void sePintaConLosColoresDeCadaPolitica() {
        titulo("La grafica dibuja la linea de cada politica usada y solo esas");
        PanelGraficaDesplazamiento panel = new PanelGraficaDesplazamiento(registro());
        BufferedImage img = pintar(panel, 900, 380);
        // El punto de la leyenda ocupa unos 100 pixeles; una linea que cruza la grafica ocupa muchos mas
        int fifo = contar(img, colorDe(DiskSchedulingPolicy.FIFO));
        int sstf = contar(img, colorDe(DiskSchedulingPolicy.SSTF));
        int look = contar(img, colorDe(DiskSchedulingPolicy.LOOK));
        System.out.println("       (pixeles de linea: FIFO " + fifo + ", SSTF " + sstf + ", LOOK " + look + ")");
        check(fifo > MIN_PIXELES_LINEA, "se dibuja la linea de FIFO");
        check(sstf > MIN_PIXELES_LINEA, "se dibuja la linea de SSTF");
        check(look > MIN_PIXELES_LINEA, "se dibuja la linea de LOOK");
        check(contar(img, colorDe(DiskSchedulingPolicy.SCAN)) == 0, "no hay color de SCAN (no se uso)");
        check(contar(img, colorDe(DiskSchedulingPolicy.C_SCAN)) == 0, "no hay color de C-SCAN (no se uso)");
        check(pintar(panel, 120, 90) != null, "tambien se dibuja en un espacio muy pequeno");
    }

    private static void conMilesDePuntos() {
        titulo("Con miles de solicitudes se dibuja rapido y sin fallar");
        PCB p = ProcessFactory.createCpuBound("P", 1, 10, 5, 500, 1, 0);
        DiskRecordLog log = new DiskRecordLog();
        DiskSchedulingPolicy[] ps = DiskSchedulingPolicy.values();
        int cabezal = 0;
        for (int i = 0; i < 5000; i++) {
            int bloque = (i * 37) % 200;
            anotar(log, p, bloque, i, i + 1, i + 2, cabezal, ps[(i / 1000) % ps.length]);
            cabezal = bloque;
        }
        long t0 = System.nanoTime();
        PanelGraficaDesplazamiento panel = new PanelGraficaDesplazamiento(log);
        BufferedImage img = pintar(panel, 900, 380);
        long ms = (System.nanoTime() - t0) / 1_000_000;
        check(panel.getSerie().size() == 5000, "la serie tiene los 5000 puntos");
        check(panel.getPoliticasUsadas().length == ps.length, "las cinco politicas tienen linea");
        check(img != null && ms < 2000, "se dibuja en menos de 2 segundos (" + ms + " ms)");
    }

    private static void dentroDeLaVentanaPrincipal() throws Exception {
        titulo("La ventana principal la muestra en una pestana y la mantiene al dia");
        SimulationSetup setup = new SimulationSetup();
        setup.loadFileSystemJson(JSON_FS);
        setup.loadProcessConfigJson(JSON_PROCESOS);
        VentanaSimulacion v = new VentanaSimulacion(setup.build());
        try {
            JTabbedPane tabs = buscarPestanas(v);
            check(tabs != null && tabs.indexOfTab("Desplazamiento del cabezal") >= 0, "existe la pestana 'Desplazamiento del cabezal'");
            PanelGraficaDesplazamiento g = v.getPanelGraficaDesplazamiento();
            check(g != null && tabs != null && tabs.indexOfComponent(g) == tabs.indexOfTab("Desplazamiento del cabezal"),
                    "la pestana contiene el panel de la grafica");
            check(g.getSerie().size() == 0, "al empezar la grafica esta vacia");
            for (int i = 0; i < 3000 && g.getSerie().size() < 3; i++) {
                v.getCluster().tick();
                v.refresh();
            }
            check(g.getSerie().size() >= 3, "al correr la simulacion la grafica recibe las solicitudes atendidas (" + g.getSerie().size() + ")");
            check(g.getSerie().size() == v.getRecordLog().size() && g.getSerie().total() == v.getRecordLog().totalDisplacement(),
                    "la grafica coincide con el registro del disco (mismas solicitudes y mismo desplazamiento total)");
        } finally {
            v.shutdown();
        }
    }

    private static JTabbedPane buscarPestanas(Container c) {
        for (Component h : c.getComponents()) {
            if (h instanceof JTabbedPane) {
                return (JTabbedPane) h;
            }
            if (h instanceof Container) {
                JTabbedPane r = buscarPestanas((Container) h);
                if (r != null) {
                    return r;
                }
            }
        }
        return null;
    }

    private static Color colorDe(DiskSchedulingPolicy p) {
        return PanelGraficaDesplazamiento.colorDe(p);
    }

    /** Dibuja el panel en una imagen (sin ventana) para poder revisar que pinta sin fallar. */
    private static BufferedImage pintar(PanelGraficaDesplazamiento panel, int ancho, int alto) {
        panel.setSize(ancho, alto);
        layoutRecursivo(panel);
        BufferedImage img = new BufferedImage(ancho, alto, BufferedImage.TYPE_INT_ARGB);
        java.awt.Graphics2D g = img.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, ancho, alto);
        panel.paint(g);
        g.dispose();
        return img;
    }

    private static void layoutRecursivo(Container c) {
        c.doLayout();
        for (Component h : c.getComponents()) {
            if (h instanceof Container) {
                layoutRecursivo((Container) h);
            }
        }
    }

    private static int contar(BufferedImage img, Color color) {
        int n = 0;
        int rgb = color.getRGB();
        for (int y = 0; y < img.getHeight(); y++) {
            for (int x = 0; x < img.getWidth(); x++) {
                if (img.getRGB(x, y) == rgb) {
                    n++;
                }
            }
        }
        return n;
    }

    private static void titulo(String texto) {
        System.out.println("--- " + texto + " ---");
    }

    private static void check(boolean ok, String texto) {
        verificaciones++;
        if (ok) {
            System.out.println("  OK   " + texto);
        } else {
            fallas++;
            System.out.println("  FALLO " + texto);
        }
    }
}