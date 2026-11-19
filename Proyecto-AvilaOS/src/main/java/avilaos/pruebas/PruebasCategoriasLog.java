/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package avilaos.pruebas;

import avilaos.datastructures.CustomQueue;
import avilaos.enums.DiskSchedulingPolicy;
import avilaos.enums.IOOperationType;
import avilaos.enums.LogCategory;
import avilaos.enums.ProcessType;
import avilaos.log.EventLogger;
import avilaos.log.LogEvent;
import avilaos.model.process.PCB;
import avilaos.os.disk.DiskDecisionLogger;
import avilaos.os.disk.DiskRequest;

/**
 * Pruebas de las categorias del log (D20): DISCO, FS, SUSPENSION y PERMISO se escriben en el mismo
 * log con ciclo y categoria, el formato del P1 no cambia y se puede filtrar por categoria.
 * Termina con "TODAS LAS PRUEBAS PASARON" o con la lista de fallas (codigo de salida 1).
 */
public class PruebasCategoriasLog {

    private static int fallas = 0;
    private static int verificaciones = 0;

    public static void main(String[] args) {
        formatoDelP1();
        formatoConCategoria();
        todasLasCategoriasEntranAlMismoLog();
        filtroPorCategoria();
        filtroRespetaElLimiteDeLineas();
        capacidadDelLog();
        loggerDeDecisionesDelDisco();
        entradasInvalidas();

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

    private static void formatoDelP1() {
        titulo("Los eventos del P1 no cambian");
        EventLogger log = new EventLogger();
        log.log(87, 1, "P3 pasa a ejecucion");
        log.logSystem(88, "Arranca la simulacion");
        CustomQueue<LogEvent> todos = log.getSnapshot();
        check(todos.get(0).getCategory() == LogCategory.GENERAL, "log(ciclo, computador, mensaje) es GENERAL");
        check(todos.get(0).toString().equals("[Ciclo 87] Computador 1: P3 pasa a ejecucion"), "mismo texto de siempre con computador");
        check(todos.get(1).toString().equals("[Ciclo 88] Sistema: Arranca la simulacion"), "mismo texto de siempre global");
        check(new LogEvent(5, 1, "x").getCategory() == LogCategory.GENERAL, "el constructor viejo de LogEvent sigue valiendo");
        check(LogCategory.GENERAL.tag().isEmpty(), "GENERAL no agrega etiqueta");
    }

    private static void formatoConCategoria() {
        titulo("Los eventos nuevos muestran ciclo y categoria");
        EventLogger log = new EventLogger();
        log.logSystem(87, LogCategory.DISCO, "El disco atiende la solicitud de P4 en el bloque 87 (SSTF)");
        log.log(90, 1, LogCategory.PERMISO, "P2 intenta borrar /sistema/boot.bin sin permiso");
        CustomQueue<LogEvent> todos = log.getSnapshot();
        check(todos.get(0).toString().equals("[Ciclo 87] [DISCO] Sistema: El disco atiende la solicitud de P4 en el bloque 87 (SSTF)"),
                "formato: [Ciclo] [CATEGORIA] quien: mensaje");
        check(todos.get(1).toString().equals("[Ciclo 90] [PERMISO] Computador 1: P2 intenta borrar /sistema/boot.bin sin permiso"),
                "tambien con computador");
        check(todos.get(0).getCycle() == 87 && todos.get(0).getCategory() == LogCategory.DISCO, "guarda ciclo y categoria");
    }

    private static void todasLasCategoriasEntranAlMismoLog() {
        titulo("Las cuatro categorias nuevas escriben en el mismo log");
        EventLogger log = new EventLogger();
        log.logSystem(1, LogCategory.DISCO, "d");
        log.logSystem(2, LogCategory.FS, "f");
        log.logSystem(3, LogCategory.SUSPENSION, "s");
        log.logSystem(4, LogCategory.PERMISO, "p");
        log.logSystem(5, "g");
        check(log.size() == 5 && log.getTotalLogged() == 5, "5 eventos en un solo log");
        String texto = log.getLastAsText(10);
        check(texto.contains("[DISCO]") && texto.contains("[FS]") && texto.contains("[SUSPENSION]") && texto.contains("[PERMISO]"),
                "el texto sin filtrar muestra todas las categorias");
        check(texto.indexOf("d\n") < texto.indexOf("f\n") && texto.indexOf("f\n") < texto.indexOf("g\n"), "en orden de llegada");
        check(LogCategory.values().length == 5, "GENERAL + las 4 nuevas");
    }

    private static void filtroPorCategoria() {
        titulo("Filtro por categoria");
        EventLogger log = new EventLogger();
        log.log(1, 1, "g1");
        log.logSystem(2, LogCategory.DISCO, "d1");
        log.logSystem(3, LogCategory.FS, "f1");
        log.logSystem(4, LogCategory.DISCO, "d2");
        log.log(5, 1, "g2");
        CustomQueue<LogEvent> discos = log.getLast(100, LogCategory.DISCO);
        check(discos.size() == 2 && discos.get(0).getMessage().equals("d1") && discos.get(1).getMessage().equals("d2"),
                "solo los DISCO, del mas antiguo al mas reciente");
        check(log.getLast(100, LogCategory.PERMISO).size() == 0, "categoria sin eventos: lista vacia");
        check(log.getLastAsText(100, LogCategory.PERMISO).isEmpty(), "y texto vacio");
        check(log.getLast(100, null).size() == 5, "filtro null muestra todo");
        check(log.getLast(100).size() == 5, "getLast(n) de siempre sigue mostrando todo");
        check(log.getLastAsText(100, LogCategory.GENERAL).equals(log.getLastAsText(100, LogCategory.GENERAL)), "estable");
        check(log.getLastAsText(100, LogCategory.GENERAL).contains("g1") && !log.getLastAsText(100, LogCategory.GENERAL).contains("d1"),
                "GENERAL tambien se puede filtrar");
        check(log.size() == 5 && log.getTotalLogged() == 5, "filtrar no modifica el log");
    }

    private static void filtroRespetaElLimiteDeLineas() {
        titulo("El limite de lineas cuenta solo los de la categoria");
        EventLogger log = new EventLogger();
        for (int i = 1; i <= 6; i++) {
            log.logSystem(i, LogCategory.DISCO, "d" + i);
            log.log(i, 1, "g" + i);
        }
        CustomQueue<LogEvent> ultimos = log.getLast(3, LogCategory.DISCO);
        check(ultimos.size() == 3, "pide 3 y recibe 3");
        check(ultimos.get(0).getMessage().equals("d4") && ultimos.get(2).getMessage().equals("d6"), "son los 3 mas recientes: d4, d5, d6");
        check(log.getLast(3).size() == 3 && log.getLast(3).get(2).getMessage().equals("g6"), "sin filtro: los 3 ultimos de todo");
        check(log.getLast(0, LogCategory.DISCO).size() == 0, "n = 0 no devuelve nada");
    }

    private static void capacidadDelLog() {
        titulo("La capacidad sigue descartando los mas antiguos");
        EventLogger log = new EventLogger(3);
        log.logSystem(1, LogCategory.FS, "a");
        log.logSystem(2, LogCategory.DISCO, "b");
        log.logSystem(3, LogCategory.DISCO, "c");
        log.logSystem(4, LogCategory.DISCO, "d");
        check(log.size() == 3 && log.getTotalLogged() == 4, "guarda 3 de 4");
        check(log.getLast(10, LogCategory.FS).size() == 0, "el FS antiguo ya se descarto");
        check(log.getLast(10, LogCategory.DISCO).size() == 3, "quedan los 3 DISCO");
    }

    private static void loggerDeDecisionesDelDisco() {
        titulo("Las decisiones del disco (D18) quedan en categoria DISCO");
        EventLogger log = new EventLogger();
        DiskDecisionLogger decisiones = new DiskDecisionLogger(log, 200);
        PCB p4 = new PCB("P4", ProcessType.CPU_BOUND, 1, 8, 2, 50, 0, 0);
        decisiones.onAttend(10, new DiskRequest(87, IOOperationType.READ, p4, 8, 1), 50, DiskSchedulingPolicy.SSTF);
        decisiones.onPolicyChange(11, DiskSchedulingPolicy.SSTF, DiskSchedulingPolicy.SCAN, 3);
        decisiones.onWrapAround(12, 180, 11);
        check(log.getLast(10, LogCategory.DISCO).size() == 3, "atencion, cambio de politica y salto de C-SCAN en DISCO");
        check(log.getLast(10, LogCategory.GENERAL).size() == 0, "ninguno cae en GENERAL");
        check(log.getSnapshot().get(0).toString().equals("[Ciclo 10] [DISCO] Sistema: El disco atiende la solicitud de P4 en el bloque 87 (SSTF)"),
                "la frase del enunciado, con ciclo y categoria");
    }

    private static void entradasInvalidas() {
        titulo("Entradas invalidas");
        EventLogger log = new EventLogger();
        check(falla(() -> log.log(1, 1, null, "x")), "categoria null se rechaza");
        check(falla(() -> log.logSystem(1, null, "x")), "categoria null global se rechaza");
        check(falla(() -> log.logSystem(1, LogCategory.FS, " ")), "mensaje vacio se rechaza");
        check(falla(() -> new LogEvent(1, 1, null, "x")), "LogEvent sin categoria se rechaza");
        check(log.size() == 0, "nada se registro");
    }
}