/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package avilaos.pruebas;

import avilaos.enums.ProcessState;
import avilaos.model.process.PCB;
import avilaos.model.process.ProcessFactory;

/**
 * B03: el modelo de siete estados. Comprueba que el enum tiene exactamente los siete estados,
 * que isSuspended() distingue los dos suspendidos y que un PCB acepta y conserva cada estado.
 * Se ejecuta como programa (main); termina con "TODAS LAS PRUEBAS PASARON" o con codigo 1.
 */
public class PruebasEstadosProceso {

    private static int fallas = 0;
    private static int verificaciones = 0;

    public static void main(String[] args) {
        System.out.println("--- los siete estados ---");
        ProcessState[] todos = ProcessState.values();
        check(todos.length == 7, "el enum tiene 7 estados (" + todos.length + ")");
        String[] esperados = {"NEW", "READY", "RUNNING", "BLOCKED", "READY_SUSPENDED", "BLOCKED_SUSPENDED", "TERMINATED"};
        for (int i = 0; i < esperados.length; i++) {
            check(contiene(todos, esperados[i]), "existe " + esperados[i]);
        }

        System.out.println("--- isSuspended ---");
        for (int i = 0; i < todos.length; i++) {
            boolean debe = todos[i] == ProcessState.READY_SUSPENDED || todos[i] == ProcessState.BLOCKED_SUSPENDED;
            check(todos[i].isSuspended() == debe, todos[i] + (debe ? " esta suspendido" : " no esta suspendido"));
        }

        System.out.println("--- el PCB usa el enum ---");
        PCB p = ProcessFactory.createCpuBound("p", 1, 10, 5, 100, 1, 0);
        check(p.getState() == ProcessState.NEW, "un proceso recien creado esta en NEW");
        for (int i = 0; i < todos.length; i++) {
            p.setState(todos[i]);
            check(p.getState() == todos[i], "el PCB guarda " + todos[i]);
        }

        System.out.println();
        if (fallas == 0) {
            System.out.println("TODAS LAS PRUEBAS PASARON (" + verificaciones + " verificaciones)");
        } else {
            System.out.println("HAY " + fallas + " FALLAS de " + verificaciones + " verificaciones");
            System.exit(1);
        }
    }

    private static boolean contiene(ProcessState[] todos, String nombre) {
        for (int i = 0; i < todos.length; i++) {
            if (todos[i].name().equals(nombre)) {
                return true;
            }
        }
        return false;
    }

    private static void check(boolean ok, String texto) {
        verificaciones++;
        System.out.println((ok ? "  OK    " : "  FALLA ") + texto);
        if (!ok) {
            fallas++;
        }
    }
}
