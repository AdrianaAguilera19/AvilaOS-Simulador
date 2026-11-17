/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package avilaos.pruebas;

import avilaos.datastructures.DynamicArray;
import avilaos.enums.DiskSchedulingPolicy;
import avilaos.enums.HeadDirection;
import avilaos.enums.IOOperationType;
import avilaos.enums.ProcessType;
import avilaos.interfaces.DiskPolicy;
import avilaos.interfaces.ProcessWaker;
import avilaos.model.hardware.BlockMap;
import avilaos.model.hardware.Disk;
import avilaos.model.hardware.DiskHead;
import avilaos.model.process.PCB;
import avilaos.os.disk.DiskQueue;
import avilaos.os.disk.DiskRecordLog;
import avilaos.os.disk.DiskRequest;
import avilaos.os.disk.DiskScheduler;
import avilaos.os.disk.policy.DiskPolicyFactory;
import avilaos.os.disk.policy.PolicyRun;

/**
 * Pruebas del cambio de politica de disco en caliente (D15), con el disco, el planificador y el
 * registro reales: la cola se reordena segun la nueva politica, sin perder ni duplicar solicitudes
 * y sin romper la direccion del cabezal.
 * Termina con "TODAS LAS PRUEBAS PASARON" o con la lista de fallas (codigo de salida 1).
 */
public class PruebasCambioPoliticaDisco {

    private static int fallas = 0;
    private static int verificaciones = 0;

    public static void main(String[] args) {
        fabrica();
        cambioReordenaLaCola();
        seMantieneLaDireccion();
        cambioConUnaSolicitudEnServicio();
        cambiarEnCadaSolicitud();
        colaVaciaYMismaPolitica();
        entradasInvalidas();
        planNoModificaNada();

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

    private static boolean igual(int[] a, int[] b) {
        if (a.length != b.length) {
            return false;
        }
        for (int i = 0; i < a.length; i++) {
            if (a[i] != b[i]) {
                return false;
            }
        }
        return true;
    }

    private static String texto(int[] a) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < a.length; i++) {
            sb.append(i == 0 ? "" : ", ").append(a[i]);
        }
        return sb.toString();
    }

    private static class Despertador implements ProcessWaker {
        int despertados = 0;

        public void wake(PCB pcb) { despertados++; }
    }

    /** Disco de 200 bloques con su cola, su planificador y su registro de solicitudes atendidas. */
    private static class Escenario {
        final DiskQueue cola = new DiskQueue();
        final Despertador despertador = new Despertador();
        final DiskScheduler plan;
        final Disk disco;
        final DiskRecordLog registro = new DiskRecordLog();
        long ciclo = 0;

        Escenario(DiskSchedulingPolicy inicial, int cabezal, HeadDirection direccion) {
            plan = new DiskScheduler(cola, DiskPolicyFactory.create(inicial), despertador);
            disco = new Disk(new BlockMap(200), new DiskHead(200, cabezal, 1, direccion));
            disco.setListener(registro);
        }

        void pedir(int... bloques) {
            for (int b : bloques) {
                PCB p = new PCB("P" + b, ProcessType.CPU_BOUND, 1, 8, 2, 50, 0, 0);
                cola.add(new DiskRequest(b, IOOperationType.READ, p, 0, 1));
            }
        }

        /** Pone en servicio la que elija la politica activa, le da los ticks que necesite y la termina. */
        void atender(int cuantas) {
            for (int i = 0; i < cuantas; i++) {
                DiskRequest r = plan.startNext(disco.getHead().getPosition(), disco.getHead().getDirection());
                disco.begin(r, ciclo);
                while (disco.isBusy()) {
                    disco.onTick(++ciclo);
                }
                plan.completeCurrent();
            }
        }

        int[] atendidas() {
            int[] bloques = new int[registro.size()];
            for (int i = 0; i < bloques.length; i++) {
                bloques[i] = registro.get(i).getTargetBlock();
            }
            return bloques;
        }

        PolicyRun proyeccion() {
            return plan.plan(disco.getHead().getPosition(), disco.getHead().getDirection(), 200);
        }
    }

    private static void fabrica() {
        titulo("D15 - la fabrica crea la politica de cada valor del enum");
        check(DiskPolicyFactory.create(DiskSchedulingPolicy.FIFO).getType() == DiskSchedulingPolicy.FIFO, "FIFO");
        check(DiskPolicyFactory.create(DiskSchedulingPolicy.SSTF).getType() == DiskSchedulingPolicy.SSTF, "SSTF");
        check(DiskPolicyFactory.create(DiskSchedulingPolicy.SCAN).getType() == DiskSchedulingPolicy.SCAN, "SCAN");
        check(DiskPolicyFactory.create(DiskSchedulingPolicy.C_SCAN).getType() == DiskSchedulingPolicy.C_SCAN, "C-SCAN");
        check(falla(() -> DiskPolicyFactory.create(null)), "tipo nulo se rechaza");
    }

    private static void cambioReordenaLaCola() {
        titulo("D15 - FIFO -> SSTF con la cola llena: se reordena y nada se pierde");
        Escenario e = new Escenario(DiskSchedulingPolicy.FIFO, 50, HeadDirection.UP);
        e.pedir(95, 180, 34, 119, 11, 123, 62, 64);
        e.atender(3);
        check(igual(e.atendidas(), new int[]{95, 180, 34}), "con FIFO se atendieron 95, 180, 34");
        check(e.disco.getHead().getPosition() == 34 && e.disco.getHead().getDirection() == HeadDirection.DOWN,
                "el cabezal quedo en el 34 yendo hacia abajo");
        check(igual(e.proyeccion().blocks(), new int[]{119, 11, 123, 62, 64}), "con FIFO seguirian 119, 11, 123, 62, 64");

        DiskPolicy anterior = e.plan.changePolicy(DiskSchedulingPolicy.SSTF);
        check(anterior.getType() == DiskSchedulingPolicy.FIFO, "changePolicy devuelve la politica anterior (FIFO)");
        check(e.plan.getPolicy().getType() == DiskSchedulingPolicy.SSTF, "la activa ahora es SSTF");
        check(e.cola.size() == 5, "las 5 pendientes siguen en la cola");
        check(igual(e.proyeccion().blocks(), new int[]{11, 62, 64, 119, 123}), "ahora seguirian 11, 62, 64, 119, 123 (dio " + texto(e.proyeccion().blocks()) + ")");

        e.atender(5);
        check(igual(e.atendidas(), new int[]{95, 180, 34, 11, 62, 64, 119, 123}), "orden real: 95, 180, 34 y luego 11, 62, 64, 119, 123 (dio " + texto(e.atendidas()) + ")");
        check(e.registro.count(DiskSchedulingPolicy.FIFO) == 3 && e.registro.count(DiskSchedulingPolicy.SSTF) == 5,
                "el registro anota 3 atendidas con FIFO y 5 con SSTF");
        check(e.registro.size() == 8 && e.despertador.despertados == 8, "8 solicitudes atendidas y 8 procesos despertados");
        check(e.cola.isEmpty() && !e.cola.isBusy(), "la cola quedo vacia y el disco libre");
    }

    private static void seMantieneLaDireccion() {
        titulo("D15 - el cambio no toca el cabezal: SCAN sigue en la direccion que llevaba");
        Escenario e = new Escenario(DiskSchedulingPolicy.SSTF, 50, HeadDirection.UP);
        e.pedir(95, 180, 34, 119, 11, 123, 62, 64);
        e.atender(2);
        check(igual(e.atendidas(), new int[]{62, 64}), "con SSTF se atendieron 62 y 64");
        check(e.disco.getHead().getPosition() == 64 && e.disco.getHead().getDirection() == HeadDirection.UP, "el cabezal esta en el 64 yendo hacia arriba");
        e.plan.changePolicy(DiskSchedulingPolicy.SCAN);
        check(e.disco.getHead().getPosition() == 64 && e.disco.getHead().getDirection() == HeadDirection.UP, "cambiar de politica no mueve el cabezal ni cambia su direccion");
        check(igual(e.proyeccion().blocks(), new int[]{95, 119, 123, 180, 34, 11}), "SCAN sigue hacia arriba: 95, 119, 123, 180 y luego 34, 11 (dio " + texto(e.proyeccion().blocks()) + ")");
        e.atender(6);
        check(igual(e.atendidas(), new int[]{62, 64, 95, 119, 123, 180, 34, 11}), "orden real de las 8 (dio " + texto(e.atendidas()) + ")");
        check(e.registro.count(DiskSchedulingPolicy.SSTF) == 2 && e.registro.count(DiskSchedulingPolicy.SCAN) == 6, "2 con SSTF y 6 con SCAN");

        Escenario abajo = new Escenario(DiskSchedulingPolicy.SSTF, 100, HeadDirection.DOWN);
        abajo.pedir(150, 90, 20);
        check(igual(abajo.proyeccion().blocks(), new int[]{90, 150, 20}), "cabezal en 100 hacia abajo, SSTF: 90, 150, 20");
        abajo.plan.changePolicy(DiskSchedulingPolicy.SCAN);
        check(abajo.disco.getHead().getDirection() == HeadDirection.DOWN, "la direccion sigue siendo DOWN");
        check(igual(abajo.proyeccion().blocks(), new int[]{90, 20, 150}), "SCAN hacia abajo: 90, 20 y luego 150 (dio " + texto(abajo.proyeccion().blocks()) + ")");
        abajo.plan.changePolicy(DiskSchedulingPolicy.C_SCAN);
        check(igual(abajo.proyeccion().blocks(), new int[]{150, 20, 90}), "C-SCAN barre hacia arriba: 150, salta al 0, 20, 90 (dio " + texto(abajo.proyeccion().blocks()) + ")");
    }

    private static void cambioConUnaSolicitudEnServicio() {
        titulo("D15 - cambiar con una solicitud en servicio: esa termina con la politica con que se eligio");
        Escenario e = new Escenario(DiskSchedulingPolicy.FIFO, 50, HeadDirection.UP);
        e.pedir(60, 70, 10);
        DiskRequest enServicio = e.plan.startNext(50, HeadDirection.UP);
        e.disco.begin(enServicio, e.ciclo);
        e.disco.onTick(++e.ciclo);
        e.plan.changePolicy(DiskSchedulingPolicy.SSTF);
        check(e.cola.isBusy() && e.cola.getInService() == enServicio, "la solicitud sigue en servicio tras el cambio");
        while (e.disco.isBusy()) {
            e.disco.onTick(++e.ciclo);
        }
        e.plan.completeCurrent();
        check(enServicio.getServedWith() == DiskSchedulingPolicy.FIFO, "la del bloque 60 quedo anotada con FIFO");
        e.atender(2);
        check(igual(e.atendidas(), new int[]{60, 70, 10}), "las siguientes con SSTF: 70 (a 10) y luego 10 (dio " + texto(e.atendidas()) + ")");
        check(e.registro.count(DiskSchedulingPolicy.FIFO) == 1 && e.registro.count(DiskSchedulingPolicy.SSTF) == 2, "1 con FIFO y 2 con SSTF");
    }

    private static void cambiarEnCadaSolicitud() {
        titulo("D15 - cambiar de politica despues de cada solicitud: ninguna se pierde ni se repite");
        Escenario e = new Escenario(DiskSchedulingPolicy.FIFO, 50, HeadDirection.UP);
        int[] pedidos = {95, 180, 34, 119, 11, 123, 62, 64, 82, 17, 43, 140};
        e.pedir(pedidos);
        DiskSchedulingPolicy[] rotacion = {DiskSchedulingPolicy.SSTF, DiskSchedulingPolicy.SCAN,
            DiskSchedulingPolicy.C_SCAN, DiskSchedulingPolicy.FIFO};
        for (int i = 0; i < pedidos.length; i++) {
            e.atender(1);
            e.plan.changePolicy(rotacion[i % rotacion.length]);
        }
        check(e.registro.size() == pedidos.length, "se atendieron las " + pedidos.length + " solicitudes");
        boolean[] vistas = new boolean[200];
        boolean repetida = false;
        for (int b : e.atendidas()) {
            if (vistas[b]) {
                repetida = true;
            }
            vistas[b] = true;
        }
        boolean faltante = false;
        for (int b : pedidos) {
            if (!vistas[b]) {
                faltante = true;
            }
        }
        check(!repetida, "ninguna se atendio dos veces");
        check(!faltante, "ninguna quedo sin atender");
        check(e.cola.isEmpty() && e.despertador.despertados == pedidos.length, "cola vacia y los " + pedidos.length + " procesos despertados");
        check(e.registro.count(DiskSchedulingPolicy.FIFO) == 3 && e.registro.count(DiskSchedulingPolicy.SSTF) == 3
                && e.registro.count(DiskSchedulingPolicy.SCAN) == 3 && e.registro.count(DiskSchedulingPolicy.C_SCAN) == 3,
                "3 atendidas con cada una de las 4 politicas");
    }

    private static void colaVaciaYMismaPolitica() {
        titulo("D15 - cambiar con la cola vacia, o a la misma politica");
        Escenario e = new Escenario(DiskSchedulingPolicy.SSTF, 50, HeadDirection.UP);
        check(e.plan.changePolicy(DiskSchedulingPolicy.SCAN).getType() == DiskSchedulingPolicy.SSTF, "con la cola vacia el cambio se acepta");
        check(e.plan.startNext(50, HeadDirection.UP) == null, "y no hay nada que atender");
        e.pedir(10, 20);
        e.plan.changePolicy(DiskSchedulingPolicy.SCAN);
        check(e.plan.getPolicy().getType() == DiskSchedulingPolicy.SCAN && e.cola.size() == 2, "a la misma politica: sigue igual y la cola intacta");
    }

    private static void entradasInvalidas() {
        titulo("D15 - cambios invalidos no dejan el planificador sin politica");
        Escenario e = new Escenario(DiskSchedulingPolicy.SSTF, 50, HeadDirection.UP);
        check(falla(() -> e.plan.changePolicy((DiskPolicy) null)), "politica nula se rechaza");
        check(falla(() -> e.plan.changePolicy((DiskSchedulingPolicy) null)), "tipo nulo se rechaza");
        check(e.plan.getPolicy().getType() == DiskSchedulingPolicy.SSTF, "tras los rechazos sigue activa SSTF");
    }

    private static void planNoModificaNada() {
        titulo("D15 - la proyeccion de la cola no modifica nada");
        Escenario e = new Escenario(DiskSchedulingPolicy.SCAN, 50, HeadDirection.UP);
        check(e.proyeccion().size() == 0 && e.proyeccion().getTotalTravel() == 0, "cola vacia: proyeccion vacia");
        e.pedir(95, 180, 34, 119, 11, 123, 62, 64);
        PolicyRun p = e.proyeccion();
        check(p.size() == 8 && p.getTotalTravel() == 337, "SCAN sobre el caso P3: 8 solicitudes y 337 bloques (dio " + p.getTotalTravel() + ")");
        check(e.cola.size() == 8 && !e.cola.isBusy(), "la cola sigue con las 8 y el disco libre");
        check(e.disco.getHead().getPosition() == 50, "el cabezal no se movio");
        check(falla(() -> e.plan.plan(300, HeadDirection.UP, 200)), "cabezal fuera del disco se rechaza");
    }
}