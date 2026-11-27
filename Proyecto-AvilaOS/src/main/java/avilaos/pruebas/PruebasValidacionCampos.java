/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package avilaos.pruebas;

import avilaos.persistence.ConfigException;
import avilaos.persistence.SimulationSetup;

/**
 * G17: todos los campos de entrada de la configuracion se validan con un mensaje claro:
 * ruta, tamano del disco (100-500), swap, cabezal, k, ciclo, quantum, RAM, memoria, deadline,
 * instrucciones y prioridad. Ningun valor absurdo llega a la simulacion.
 */
public class PruebasValidacionCampos {

    private static int verificaciones = 0;
    private static int fallas = 0;

    private static String fs(int total, int head) {
        return "{\"diskConfig\":{\"totalBlocks\":" + total + ",\"initialHead\":" + head + "},\"fileSystem\":["
                + "{\"path\":\"/a.txt\",\"type\":\"FILE\",\"startBlock\":0,\"blocks\":2,\"owner\":\"admin\",\"permissions\":\"public\"}]}";
    }

    private static String pr(String g, String c, String p) {
        return "{\"globalConfig\":{" + g + "},\"computer\":{" + c + "},\"processes\":[{" + p + "}]}";
    }

    private static final String G = "\"cycleDurationMs\":100,\"swapSizeBlocks\":50,\"initialDiskPolicy\":\"FIFO\"";
    private static final String C = "\"ramCapacity\":64,\"policy\":\"FCFS\",\"quantum\":0";
    private static final String P = "\"name\":\"A\",\"memory\":10,\"deadline\":100,\"instructions\":3,"
            + "\"targetPath\":\"/a.txt\",\"operation\":\"READ\"";

    /** Devuelve el mensaje de rechazo, o null si la configuracion se acepto. */
    private static String probar(String fsJson, String prJson) {
        try {
            SimulationSetup s = new SimulationSetup();
            s.loadFileSystemJson(fsJson);
            s.loadProcessConfigJson(prJson);
            s.build();
            return null;
        } catch (ConfigException e) {
            return e.getMessage();
        } catch (Exception e) {
            return "EXCEPCION NO CONTROLADA: " + e;
        }
    }

    private static void rechaza(String texto, String fsJson, String prJson, String fragmento) {
        String m = probar(fsJson, prJson);
        check(m != null && m.contains(fragmento) && !m.startsWith("EXCEPCION"),
                texto + " -> " + (m == null ? "SE ACEPTO" : m));
    }

    public static void main(String[] args) {
        String f = fs(200, 50);
        System.out.println("== Configuracion valida");
        check(probar(f, pr(G, C, P)) == null, "la configuracion base se acepta");
        check(probar(fs(100, 0), pr(G, C, P)) == null && probar(fs(500, 499), pr(G, C, P)) == null,
                "los extremos validos del disco (100 y 500) se aceptan");

        System.out.println("\n== Disco, cabezal y swap");
        rechaza("disco de 99 bloques", fs(99, 0), pr(G, C, P), "100 y 500");
        rechaza("disco de 501 bloques", fs(501, 0), pr(G, C, P), "100 y 500");
        rechaza("cabezal -1", fs(200, -1), pr(G, C, P), "cabezal");
        rechaza("cabezal fuera del disco", fs(200, 200), pr(G, C, P), "cabezal");
        rechaza("swap 0", f, pr(G.replace("\"swapSizeBlocks\":50", "\"swapSizeBlocks\":0"), C, P), "swapSizeBlocks");
        rechaza("swap enorme", f, pr(G.replace("\"swapSizeBlocks\":50", "\"swapSizeBlocks\":999999999"), C, P), "swapSizeBlocks");
        rechaza("k = 0", f, pr(G + ",\"headSpeed\":0", C, P), "(k)");
        rechaza("k enorme", f, pr(G + ",\"headSpeed\":100000", C, P), "headSpeed");

        System.out.println("\n== Ciclo, RAM y quantum");
        rechaza("ciclo 0", f, pr(G.replace("\"cycleDurationMs\":100", "\"cycleDurationMs\":0"), C, P), "cycleDurationMs");
        rechaza("ciclo enorme", f, pr(G.replace("\"cycleDurationMs\":100", "\"cycleDurationMs\":99999999"), C, P), "cycleDurationMs");
        rechaza("RAM 0", f, pr(G, C.replace("\"ramCapacity\":64", "\"ramCapacity\":0"), P), "ramCapacity");
        rechaza("RAM enorme", f, pr(G, C.replace("\"ramCapacity\":64", "\"ramCapacity\":999999999"), P), "ramCapacity");
        rechaza("quantum negativo", f, pr(G, C.replace("\"quantum\":0", "\"quantum\":-1"), P), "quantum");
        rechaza("quantum enorme", f, pr(G, C.replace("\"quantum\":0", "\"quantum\":99999999"), P), "quantum");
        rechaza("Round Robin sin quantum", f, pr(G, C.replace("FCFS", "ROUND_ROBIN"), P), "quantum");

        System.out.println("\n== Procesos");
        rechaza("memoria 0", f, pr(G, C, P.replace("\"memory\":10", "\"memory\":0")), "memory");
        rechaza("memoria mayor que la RAM", f, pr(G, C, P.replace("\"memory\":10", "\"memory\":65")), "RAM");
        rechaza("memoria con texto", f, pr(G, C, P.replace("\"memory\":10", "\"memory\":\"abc\"")), "memory");
        rechaza("deadline 0", f, pr(G, C, P.replace("\"deadline\":100", "\"deadline\":0")), "deadline");
        rechaza("deadline enorme", f, pr(G, C, P.replace("\"deadline\":100", "\"deadline\":999999999")), "deadline");
        rechaza("instrucciones 0", f, pr(G, C, P.replace("\"instructions\":3", "\"instructions\":0")), "instructions");
        rechaza("instrucciones enormes", f, pr(G, C, P.replace("\"instructions\":3", "\"instructions\":999999999")), "instructions");
        rechaza("prioridad 0", f, pr(G, C, P + ",\"priority\":0"), "priority");
        rechaza("prioridad enorme", f, pr(G, C, P + ",\"priority\":999999999"), "priority");

        System.out.println("\n== Rutas");
        rechaza("ruta sin /", f, pr(G, C, P.replace("/a.txt", "a.txt")), "empezar con '/'");
        rechaza("ruta con partes vacias", f, pr(G, C, P.replace("/a.txt", "//a.txt")), "partes vacias");
        rechaza("ruta raiz", f, pr(G, C, P.replace("/a.txt", "/")), "raiz");
        check(probar(f, pr(G, C, P.replace("/a.txt", "/no-existe.txt"))) == null,
                "una ruta que no existe se acepta en la configuracion: el error se controla al ejecutar la E/S");

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
}
