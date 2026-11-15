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
import avilaos.model.process.PCB;
import avilaos.os.disk.DiskQueue;
import avilaos.os.disk.DiskRequest;
import avilaos.os.disk.DiskScheduler;

/**
 * Pruebas del planificador de disco (D09), con politicas y despertador de prueba.
 * Termina con "TODAS LAS PRUEBAS PASARON" o con la lista de fallas (codigo de salida 1).
 */
public class PruebasPlanificadorDisco {

    private static int fallas = 0;
    private static int verificaciones = 0;

    public static void main(String[] args) {
        sinSolicitudes();
        tomaSegunLaPolitica();
        discoOcupado();
        alTerminarDespierta();
        cambioDePolitica();
        politicaConErrores();
        entradasInvalidas();
        corridaCompleta();

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

    private static class PrimeraLlegada implements DiskPolicy {
        public DiskSchedulingPolicy getType() { return DiskSchedulingPolicy.FIFO; }

        public int selectNext(DynamicArray<DiskRequest> pending, int headPosition, HeadDirection direction) {
            return pending.isEmpty() ? -1 : 0;
        }
    }

    private static class MasCercana implements DiskPolicy {
        public DiskSchedulingPolicy getType() { return DiskSchedulingPolicy.SSTF; }

        public int selectNext(DynamicArray<DiskRequest> pending, int headPosition, HeadDirection direction) {
            int mejor = -1;
            int menor = Integer.MAX_VALUE;
            for (int i = 0; i < pending.size(); i++) {
                int distancia = Math.abs(pending.get(i).getTargetBlock() - headPosition);
                if (distancia < menor) {
                    menor = distancia;
                    mejor = i;
                }
            }
            return mejor;
        }
    }

    private static class Rota implements DiskPolicy {
        private final int respuesta;

        Rota(int respuesta) { this.respuesta = respuesta; }

        public DiskSchedulingPolicy getType() { return DiskSchedulingPolicy.SCAN; }

        public int selectNext(DynamicArray<DiskRequest> pending, int headPosition, HeadDirection direction) {
            return respuesta;
        }
    }

    private static class Despertador implements ProcessWaker {
        final DynamicArray<PCB> despertados = new DynamicArray<>();

        public void wake(PCB pcb) { despertados.add(pcb); }
    }

    private static PCB proceso(String nombre) {
        return new PCB(nombre, ProcessType.CPU_BOUND, 1, 8, 2, 50, 0, 0);
    }

    private static DiskRequest pedir(PCB p, int bloque, long llegada) {
        return new DiskRequest(bloque, IOOperationType.READ, p, llegada, 1);
    }

    private static void sinSolicitudes() {
        titulo("D09 - cola vacia");
        DiskScheduler plan = new DiskScheduler(new DiskQueue(), new PrimeraLlegada(), new Despertador());
        check(plan.startNext(50, HeadDirection.UP) == null, "no hay nada que atender");
        check(!plan.getQueue().isBusy(), "el disco sigue libre");
    }

    private static void tomaSegunLaPolitica() {
        titulo("D09 - toma de la cola segun la politica activa");
        DiskQueue cola = new DiskQueue();
        cola.add(pedir(proceso("A"), 95, 0));
        cola.add(pedir(proceso("B"), 180, 1));
        cola.add(pedir(proceso("C"), 34, 2));
        DiskScheduler plan = new DiskScheduler(cola, new MasCercana(), new Despertador());
        DiskRequest elegida = plan.startNext(50, HeadDirection.UP);
        check(elegida.getTargetBlock() == 34, "con el cabezal en 50 elige el bloque 34");
        check(cola.getInService() == elegida && cola.size() == 2, "queda en servicio y sale de la cola");
        check(elegida.getServedWith() == DiskSchedulingPolicy.SSTF, "se anota la politica con que se atendio");
        check(plan.getPolicy().getType() == DiskSchedulingPolicy.SSTF, "getPolicy devuelve la activa");
    }

    private static void discoOcupado() {
        titulo("D09 - no toma otra mientras hay una en servicio");
        DiskQueue cola = new DiskQueue();
        cola.add(pedir(proceso("A"), 10, 0));
        cola.add(pedir(proceso("B"), 20, 1));
        DiskScheduler plan = new DiskScheduler(cola, new PrimeraLlegada(), new Despertador());
        DiskRequest primera = plan.startNext(0, HeadDirection.UP);
        check(plan.startNext(10, HeadDirection.UP) == null, "con el disco ocupado devuelve null");
        check(cola.getInService() == primera && cola.size() == 1, "nada cambia");
    }

    private static void alTerminarDespierta() {
        titulo("D09 - al terminar despierta al proceso");
        DiskQueue cola = new DiskQueue();
        Despertador despertador = new Despertador();
        PCB a = proceso("A");
        PCB b = proceso("B");
        cola.add(pedir(a, 10, 0));
        cola.add(pedir(b, 20, 1));
        DiskScheduler plan = new DiskScheduler(cola, new PrimeraLlegada(), despertador);
        plan.startNext(0, HeadDirection.UP);
        check(despertador.despertados.isEmpty(), "mientras se atiende nadie se despierta");
        DiskRequest hecha = plan.completeCurrent();
        check(hecha.getRequester() == a, "devuelve la solicitud terminada");
        check(despertador.despertados.size() == 1 && despertador.despertados.get(0) == a, "se despierta a A");
        check(!cola.isBusy(), "el disco queda libre");
        plan.startNext(10, HeadDirection.UP);
        plan.completeCurrent();
        check(despertador.despertados.size() == 2 && despertador.despertados.get(1) == b, "luego se despierta a B");
        check(falla(() -> plan.completeCurrent()), "terminar sin nada en servicio da error");
        check(despertador.despertados.size() == 2, "el error no despierta a nadie");
    }

    private static void cambioDePolitica() {
        titulo("D09 - cambio de politica en caliente");
        DiskQueue cola = new DiskQueue();
        cola.add(pedir(proceso("A"), 95, 0));
        cola.add(pedir(proceso("B"), 62, 1));
        cola.add(pedir(proceso("C"), 180, 2));
        DiskScheduler plan = new DiskScheduler(cola, new PrimeraLlegada(), new Despertador());
        check(plan.startNext(50, HeadDirection.UP).getTargetBlock() == 95, "con la primera llegada atiende 95");
        plan.completeCurrent();
        plan.setPolicy(new MasCercana());
        check(plan.startNext(95, HeadDirection.UP).getTargetBlock() == 62, "tras el cambio atiende la mas cercana a 95: el 62");
        plan.completeCurrent();
        check(plan.getPolicy().getType() == DiskSchedulingPolicy.SSTF, "la politica activa es la nueva");
        check(cola.size() == 1 && cola.get(0).getTargetBlock() == 180, "no se perdio ninguna solicitud");
    }

    private static void politicaConErrores() {
        titulo("D09 - politica que elige una posicion invalida");
        DiskQueue cola = new DiskQueue();
        cola.add(pedir(proceso("A"), 10, 0));
        DiskScheduler plan = new DiskScheduler(cola, new Rota(99), new Despertador());
        check(falla(() -> plan.startNext(0, HeadDirection.UP)), "posicion fuera de rango da error");
        check(!cola.isBusy() && cola.size() == 1, "el disco queda libre y la solicitud sigue en la cola");
        plan.setPolicy(new Rota(-1));
        check(falla(() -> plan.startNext(0, HeadDirection.UP)), "devolver -1 con solicitudes pendientes da error");
        check(cola.size() == 1, "la cola no cambia");
    }

    private static void entradasInvalidas() {
        titulo("D09 - entradas invalidas");
        DiskQueue cola = new DiskQueue();
        check(falla(() -> new DiskScheduler(null, new PrimeraLlegada(), new Despertador())), "sin cola");
        check(falla(() -> new DiskScheduler(cola, null, new Despertador())), "sin politica");
        check(falla(() -> new DiskScheduler(cola, new PrimeraLlegada(), null)), "sin despertador");
        DiskScheduler plan = new DiskScheduler(cola, new PrimeraLlegada(), new Despertador());
        check(falla(() -> plan.setPolicy(null)), "cambiar a politica vacia");
        check(plan.getPolicy().getType() == DiskSchedulingPolicy.FIFO, "la politica no cambia por el rechazo");
    }

    private static void corridaCompleta() {
        titulo("D09 - caso P1 del enunciado con la primera llegada");
        DiskQueue cola = new DiskQueue();
        Despertador despertador = new Despertador();
        int[] bloques = {95, 180, 34, 119, 11, 123, 62, 64};
        PCB[] procesos = new PCB[bloques.length];
        for (int i = 0; i < bloques.length; i++) {
            procesos[i] = proceso("P" + i);
            cola.add(pedir(procesos[i], bloques[i], i));
        }
        DiskScheduler plan = new DiskScheduler(cola, new PrimeraLlegada(), despertador);
        boolean ordenOk = true;
        int atendidas = 0;
        while (!cola.isEmpty()) {
            DiskRequest r = plan.startNext(50, HeadDirection.UP);
            if (r.getTargetBlock() != bloques[atendidas]) {
                ordenOk = false;
            }
            plan.completeCurrent();
            atendidas++;
        }
        check(atendidas == 8 && ordenOk, "atiende 95,180,34,119,11,123,62,64 en ese orden");
        boolean despertadosOk = despertador.despertados.size() == 8;
        for (int i = 0; i < 8 && despertadosOk; i++) {
            if (despertador.despertados.get(i) != procesos[i]) {
                despertadosOk = false;
            }
        }
        check(despertadosOk, "los 8 procesos se despiertan en el mismo orden");
    }
}