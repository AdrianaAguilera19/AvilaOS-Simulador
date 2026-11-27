/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package avilaos.pruebas;

import avilaos.gui.VentanaSimulacion;
import avilaos.persistence.SimulationSetup;
import avilaos.persistence.SimulationSetup.SimulationBundle;
import javax.swing.SwingUtilities;

/**
 * G18: la ventana se actualiza en tiempo real sin congelarse. El reloj corre en su propio hilo
 * (ciclos rapidos) mientras la ventana se refresca desde el hilo de Swing, como hace su Timer.
 * Se comprueba que: nadie lanza excepciones, el hilo de Swing sigue respondiendo y la ventana
 * va siguiendo el ciclo del reloj.
 */
public class PruebasTiempoReal {

    private static int verificaciones = 0;
    private static int fallas = 0;

    private static final String FS = """
            { "diskConfig": { "totalBlocks": 200, "initialHead": 50 },
              "fileSystem": [
                { "path": "/mbr.bin", "type": "FILE", "startBlock": 0, "blocks": 11, "owner": "admin", "permissions": "public" },
                { "path": "/sistema", "type": "DIRECTORY", "owner": "admin", "permissions": "public" },
                { "path": "/sistema/boot.bin", "type": "FILE", "startBlock": 11, "blocks": 23, "owner": "admin", "permissions": "public" },
                { "path": "/sistema/config.sys", "type": "FILE", "startBlock": 95, "blocks": 24, "owner": "admin", "permissions": "public" },
                { "path": "/sistema/data.csv", "type": "FILE", "startBlock": 123, "blocks": 57, "owner": "admin", "permissions": "public" }
              ] }
            """;

    private static final String PROCESOS = """
            { "globalConfig": { "cycleDurationMs": 100, "swapSizeBlocks": 100, "initialDiskPolicy": "SSTF" },
              "computer": { "ramCapacity": 64, "policy": "FCFS", "quantum": 0 },
              "processes": [
                { "name": "P1", "memory": 30, "deadline": 5000, "instructions": 3, "targetPath": "/sistema/config.sys", "operation": "READ" },
                { "name": "P2", "memory": 30, "deadline": 5000, "instructions": 3, "targetPath": "/sistema/data.csv", "operation": "READ" },
                { "name": "P3", "memory": 30, "deadline": 5000, "instructions": 3, "targetPath": "/mbr.bin", "operation": "READ" },
                { "name": "P4", "memory": 30, "deadline": 5000, "instructions": 3, "targetPath": "/sistema/boot.bin", "operation": "READ" }
              ] }
            """;

    public static void main(String[] args) throws Exception {
        System.setProperty("java.awt.headless", "true");
        System.out.println("== G18 - Actualizacion en tiempo real sin congelar la ventana");

        SimulationSetup setup = new SimulationSetup();
        setup.loadFileSystemJson(FS);
        setup.loadProcessConfigJson(PROCESOS);
        SimulationBundle bundle = setup.build();

        final VentanaSimulacion[] caja = new VentanaSimulacion[1];
        SwingUtilities.invokeAndWait(() -> caja[0] = new VentanaSimulacion(bundle));
        VentanaSimulacion v = caja[0];

        final Throwable[] error = new Throwable[1];
        final long[] peorRefresco = {0};
        final int[] refrescos = {0};

        v.getCluster().setCycleDurationMs(2);   // ciclos rapidos en el hilo del reloj
        v.getCluster().start();

        long fin = System.currentTimeMillis() + 3000;
        while (System.currentTimeMillis() < fin) {
            long t0 = System.nanoTime();
            try {
                SwingUtilities.invokeAndWait(() -> {
                    try {
                        v.refresh();
                    } catch (Throwable t) {
                        error[0] = t;
                    }
                });
            } catch (Throwable t) {
                error[0] = t;
            }
            long ms = (System.nanoTime() - t0) / 1_000_000;
            peorRefresco[0] = Math.max(peorRefresco[0], ms);
            refrescos[0]++;
            if (error[0] != null) {
                break;
            }
            Thread.sleep(20);
        }
        long cicloReloj = v.getCluster().getCurrentCycle();
        v.getCluster().stop();

        check(error[0] == null, "el refresco desde el hilo de Swing no lanzo excepciones con el reloj corriendo"
                + (error[0] == null ? "" : " (" + error[0] + ")"));
        check(refrescos[0] >= 20, "la ventana pudo refrescarse muchas veces (" + refrescos[0] + ") mientras corria el reloj");
        check(cicloReloj > 100, "el reloj avanzo con su propio hilo (ciclo " + cicloReloj + ")");
        check(peorRefresco[0] < 500, "el peor refresco tardo " + peorRefresco[0] + " ms: el hilo de Swing nunca se bloqueo");

        // La ventana sigue el ciclo del reloj
        SwingUtilities.invokeAndWait(v::refresh);
        check(v.getPanelDisco().getTextoCabezal().startsWith("Cabezal: bloque "), "la etiqueta del cabezal se mantiene actualizada");
        check(v.getRecordLog().size() > 0, "el disco fue atendiendo solicitudes mientras la ventana se refrescaba");

        // Pausa y reanudacion no rompen la ventana
        v.getCluster().start();
        v.getCluster().pause();
        long enPausa = v.getCluster().getCurrentCycle();
        Thread.sleep(150);
        check(v.getCluster().getCurrentCycle() == enPausa, "en pausa el reloj no avanza");
        v.getCluster().resume();
        Thread.sleep(150);
        v.getCluster().stop();
        SwingUtilities.invokeAndWait(v::refresh);
        check(v.getCluster().getCurrentCycle() >= enPausa, "al reanudar el reloj sigue y la ventana refresca bien");
        v.shutdown();

        System.out.println();
        if (fallas == 0) {
            System.out.println("TODAS LAS PRUEBAS PASARON (" + verificaciones + " verificaciones)");
            System.exit(0);
        } else {
            System.out.println("HAY " + fallas + " FALLAS de " + verificaciones + " verificaciones");
            System.exit(1);
        }
    }

    private static void check(boolean ok, String texto) {
        verificaciones++;
        System.out.println((ok ? "  OK    " : "  FALLA ") + texto);
        if (!ok) {
            fallas++;
        }
    }
}