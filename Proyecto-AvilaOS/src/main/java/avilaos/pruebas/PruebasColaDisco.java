/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package avilaos.pruebas;

import avilaos.datastructures.DynamicArray;
import avilaos.enums.IOOperationType;
import avilaos.enums.ProcessType;
import avilaos.model.process.PCB;
import avilaos.os.disk.DiskQueue;
import avilaos.os.disk.DiskRequest;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Pruebas de la cola unica de E/S del disco (D08).
 * Termina con "TODAS LAS PRUEBAS PASARON" o con la lista de fallas (codigo de salida 1).
 */
public class PruebasColaDisco {

    private static int fallas = 0;
    private static int verificaciones = 0;

    public static void main(String[] args) throws Exception {
        colaVacia();
        ordenDeLlegada();
        unaSolicitudALaVez();
        sacarDelMedio();
        erroresDeLaCola();
        copiaParaLaGui();
        hilosEncolando();
        hilosPidiendoServicio();

        System.out.println();
        if (fallas == 0) {
            System.out.println("TODAS LAS PRUEBAS PASARON (" + verificaciones + " verificaciones)");
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

    private static void titulo(String texto) {
        System.out.println();
        System.out.println("== " + texto);
    }

    private static boolean falla(Runnable accion) {
        try {
            accion.run();
            return false;
        } catch (RuntimeException e) {
            return true;
        }
    }

    private static PCB proceso(String nombre) {
        return new PCB(nombre, ProcessType.CPU_BOUND, 1, 8, 2, 50, 0, 0);
    }

    private static DiskRequest pedir(PCB p, int bloque, long llegada) {
        return new DiskRequest(bloque, IOOperationType.READ, p, llegada, 1);
    }

    // ------------------------------------------------------------------ D08

    private static void colaVacia() {
        titulo("D08 - cola nueva");
        DiskQueue cola = new DiskQueue();
        check(cola.isEmpty() && cola.size() == 0, "esta vacia");
        check(!cola.isBusy() && cola.getInService() == null, "el disco no esta atendiendo");
        check(cola.pendingSnapshot().isEmpty(), "la copia esta vacia");
    }

    private static void ordenDeLlegada() {
        titulo("D08 - guarda en orden de llegada");
        DiskQueue cola = new DiskQueue();
        int[] bloques = {95, 180, 34, 119, 11, 123, 62, 64};
        for (int i = 0; i < bloques.length; i++) {
            cola.add(pedir(proceso("P" + i), bloques[i], i));
        }
        check(cola.size() == 8, "8 solicitudes encoladas");
        boolean enOrden = true;
        for (int i = 0; i < bloques.length; i++) {
            if (cola.get(i).getTargetBlock() != bloques[i]) {
                enOrden = false;
            }
        }
        check(enOrden, "orden 95,180,34,119,11,123,62,64 (caso P1 del enunciado)");
    }

    private static void unaSolicitudALaVez() {
        titulo("D08 - el disco atiende una sola a la vez");
        DiskQueue cola = new DiskQueue();
        DiskRequest a = pedir(proceso("A"), 10, 0);
        DiskRequest b = pedir(proceso("B"), 20, 1);
        cola.add(a);
        cola.add(b);
        DiskRequest tomada = cola.startService(0);
        check(tomada == a, "empieza a atender la primera");
        check(cola.isBusy() && cola.getInService() == a, "el disco queda ocupado con A");
        check(cola.size() == 1, "A salio de la cola de pendientes");
        check(falla(() -> cola.startService(0)), "no se puede atender otra mientras hay una en servicio");
        check(cola.getInService() == a && cola.size() == 1, "el intento no cambia nada");
        check(cola.finishService() == a, "al terminar devuelve A");
        check(!cola.isBusy(), "el disco queda libre");
        check(cola.startService(0) == b, "ahora si atiende B");
        cola.finishService();
        check(cola.isEmpty() && !cola.isBusy(), "todo atendido, cola vacia");
    }

    private static void sacarDelMedio() {
        titulo("D08 - sacar una del medio (SSTF y SCAN)");
        DiskQueue cola = new DiskQueue();
        cola.add(pedir(proceso("A"), 95, 0));
        cola.add(pedir(proceso("B"), 62, 1));
        cola.add(pedir(proceso("C"), 180, 2));
        DiskRequest elegida = cola.startService(1);
        check(elegida.getTargetBlock() == 62, "saca la del medio (bloque 62)");
        check(cola.size() == 2, "quedan 2 pendientes");
        check(cola.get(0).getTargetBlock() == 95 && cola.get(1).getTargetBlock() == 180, "las demas conservan su orden");
        cola.finishService();
        cola.startService(1);
        check(cola.get(0).getTargetBlock() == 95 && cola.size() == 1, "sacar la ultima tambien funciona");
    }

    private static void erroresDeLaCola() {
        titulo("D08 - usos incorrectos");
        DiskQueue cola = new DiskQueue();
        DiskRequest a = pedir(proceso("A"), 10, 0);
        check(falla(() -> cola.add(null)), "no se encola null");
        cola.add(a);
        check(falla(() -> cola.add(a)), "la misma solicitud no entra dos veces");
        check(cola.size() == 1, "el rechazo no cambia la cola");
        check(falla(() -> cola.startService(5)), "posicion fuera de rango");
        check(falla(() -> cola.finishService()), "terminar sin nada en servicio");
        cola.startService(0);
        check(falla(() -> cola.add(a)), "tampoco entra la que esta en servicio");
        check(falla(() -> cola.get(0)), "get en cola vacia");
    }

    private static void copiaParaLaGui() {
        titulo("D08 - copia para la GUI");
        DiskQueue cola = new DiskQueue();
        cola.add(pedir(proceso("A"), 10, 0));
        cola.add(pedir(proceso("B"), 20, 1));
        DynamicArray<DiskRequest> foto = cola.pendingSnapshot();
        check(foto.size() == 2, "la copia tiene las 2 pendientes");
        cola.startService(0);
        cola.add(pedir(proceso("C"), 30, 2));
        check(foto.size() == 2 && foto.get(0).getTargetBlock() == 10, "la copia no cambia despues");
        check(cola.pendingSnapshot().size() == 2, "una copia nueva si ve los cambios");
        foto.clear();
        check(cola.size() == 2, "vaciar la copia no afecta la cola");
    }

    private static void hilosEncolando() throws Exception {
        titulo("D08 - varios hilos encolando");
        final DiskQueue cola = new DiskQueue();
        final int hilos = 4;
        final int porHilo = 100;
        Thread[] grupo = new Thread[hilos];
        for (int h = 0; h < hilos; h++) {
            final int numero = h;
            grupo[h] = new Thread(() -> {
                PCB p = proceso("H" + numero);
                for (int i = 0; i < porHilo; i++) {
                    cola.add(pedir(p, i, i));
                }
            });
        }
        for (Thread t : grupo) {
            t.start();
        }
        for (Thread t : grupo) {
            t.join();
        }
        check(cola.size() == hilos * porHilo, "las 400 solicitudes quedaron en la cola, ninguna se perdio");
    }

    private static void hilosPidiendoServicio() throws Exception {
        titulo("D08 - varios hilos pidiendo servicio a la vez");
        final DiskQueue cola = new DiskQueue();
        for (int i = 0; i < 20; i++) {
            cola.add(pedir(proceso("P" + i), i, i));
        }
        final AtomicInteger logradas = new AtomicInteger();
        Thread[] grupo = new Thread[8];
        for (int h = 0; h < grupo.length; h++) {
            grupo[h] = new Thread(() -> {
                try {
                    cola.startService(0);
                    logradas.incrementAndGet();
                } catch (IllegalStateException e) {
                    // otro hilo ya lo estaba atendiendo
                }
            });
        }
        for (Thread t : grupo) {
            t.start();
        }
        for (Thread t : grupo) {
            t.join();
        }
        check(logradas.get() == 1, "solo un hilo logra poner una solicitud en servicio");
        check(cola.size() == 19 && cola.isBusy(), "queda una en servicio y 19 pendientes");
    }
}