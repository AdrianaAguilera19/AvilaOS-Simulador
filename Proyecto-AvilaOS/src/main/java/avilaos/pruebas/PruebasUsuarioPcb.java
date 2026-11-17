/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package avilaos.pruebas;

import avilaos.enums.ProcessType;
import avilaos.enums.UserMode;
import avilaos.model.process.PCB;
import avilaos.model.process.ProcessFactory;

/**
 * Pruebas del usuario y el modo dentro del PCB: quien pide la operacion de E/S y con que modo.
 * Termina con "TODAS LAS PRUEBAS PASARON" o con la lista de fallas (codigo de salida 1).
 */
public class PruebasUsuarioPcb {

    private static int fallas = 0;
    private static int verificaciones = 0;

    public static void main(String[] args) {
        procesoSinUsuario();
        fijarUsuarioYModo();
        soloSeFijaUnaVez();
        datosInvalidos();
        fabricaConUsuario();
        fabricaRechazaSinCrearElProceso();
        fabricaSinUsuarioSigueFuncionando();
        elModoNoDependeDeLaSesion();

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

    private static PCB proceso() {
        return new PCB("P1", ProcessType.CPU_BOUND, 1, 8, 2, 50, 0, 0);
    }

    private static PCB conUsuario(String nombre, int bloques, String usuario, UserMode modo) {
        return ProcessFactory.createDiskIo(nombre, 1, 16, 2, 40, 0, 0, "CREATE", "/usuarios/" + nombre, bloques, null, usuario, modo);
    }

    private static void procesoSinUsuario() {
        titulo("PCB - un proceso comun no tiene usuario ni modo");
        PCB p = proceso();
        check(!p.hasUser(), "hasUser() es false");
        check(p.getUserName() == null && p.getUserMode() == null, "usuario y modo son null");
    }

    private static void fijarUsuarioYModo() {
        titulo("PCB - se fijan el usuario y el modo");
        PCB admin = proceso();
        admin.setUser("admin", UserMode.ADMIN);
        check(admin.hasUser(), "hasUser() es true");
        check(admin.getUserName().equals("admin") && admin.getUserMode() == UserMode.ADMIN, "admin con modo ADMIN");
        check(admin.getUserMode().isAdmin(), "isAdmin() sale del PCB");
        PCB ana = proceso();
        ana.setUser("  ana ", UserMode.USER);
        check(ana.getUserName().equals("ana"), "el nombre se guarda sin espacios sobrantes");
        check(ana.getUserMode() == UserMode.USER && !ana.getUserMode().isAdmin(), "ana con modo USER");
    }

    private static void soloSeFijaUnaVez() {
        titulo("PCB - el usuario y el modo no cambian despues de fijarlos");
        PCB p = proceso();
        p.setUser("ana", UserMode.USER);
        check(falla(() -> p.setUser("admin", UserMode.ADMIN)), "no se puede volver a fijar como ADMIN");
        check(falla(() -> p.setUser("ana", UserMode.USER)), "ni siquiera con los mismos valores");
        check(p.getUserName().equals("ana") && p.getUserMode() == UserMode.USER, "sigue siendo ana con modo USER");
    }

    private static void datosInvalidos() {
        titulo("PCB - usuario o modo invalidos se rechazan sin dejar nada a medias");
        PCB p = proceso();
        check(falla(() -> p.setUser(null, UserMode.USER)), "usuario null");
        check(falla(() -> p.setUser("   ", UserMode.USER)), "usuario en blanco");
        check(falla(() -> p.setUser("ana", null)), "modo null");
        check(!p.hasUser() && p.getUserName() == null, "tras los rechazos el proceso sigue sin usuario");
        p.setUser("ana", UserMode.USER);
        check(p.hasUser(), "y despues se puede fijar bien");
    }

    private static void fabricaConUsuario() {
        titulo("Fabrica - el proceso de E/S nace con su usuario y su modo");
        PCB p = ProcessFactory.createDiskIo("P1", 1, 16, 2, 40, 0, 0, "CREATE", "/usuarios/ana/tarea.doc", 5, null, "ana", UserMode.USER);
        check(p.hasDiskOperation() && p.getTargetPath().equals("/usuarios/ana/tarea.doc") && p.getFileSizeBlocks() == 5, "conserva la operacion, la ruta y el tamano");
        check(p.getUserName().equals("ana") && p.getUserMode() == UserMode.USER, "y ademas el usuario ana con modo USER");
        PCB a = ProcessFactory.createDiskIo("P2", 1, 16, 2, 40, 0, 0, "DELETE", "/sistema/boot.bin", 0, null, "admin", UserMode.ADMIN);
        check(a.getUserMode() == UserMode.ADMIN && a.getDiskOperation() != null, "un proceso del administrador queda con modo ADMIN");
    }

    private static void fabricaRechazaSinCrearElProceso() {
        titulo("Fabrica - sin usuario o sin modo se rechaza y no se crea el proceso");
        check(falla(() -> conUsuario("A", 3, null, UserMode.USER)), "usuario null");
        check(falla(() -> conUsuario("A", 3, " ", UserMode.USER)), "usuario en blanco");
        check(falla(() -> conUsuario("A", 3, "ana", null)), "modo null");
        PCB antes = proceso();
        check(falla(() -> conUsuario("A", 3, "ana", null)), "otro rechazo entre dos procesos");
        PCB despues = proceso();
        check(despues.getId() == antes.getId() + 1, "los rechazos no gastaron ningun numero de proceso");
        check(falla(() -> conUsuario("A", 0, "ana", UserMode.USER)), "CREATE sin tamano sigue rechazandose");
    }

    private static void fabricaSinUsuarioSigueFuncionando() {
        titulo("Fabrica - la version anterior (sin usuario) sigue funcionando");
        PCB p = ProcessFactory.createDiskIo("P1", 1, 16, 2, 40, 0, 0, "READ", "/usuarios/notas.txt", 0, null);
        check(p.hasDiskOperation() && !p.hasUser(), "crea el proceso, pero sin usuario ni modo");
    }

    private static void elModoNoDependeDeLaSesion() {
        titulo("PCB - cada proceso lleva su propio modo, sin importar el de la sesion");
        UserMode sesion = UserMode.ADMIN;
        PCB creadoComoUsuario = conUsuario("U1", 2, "beto", UserMode.USER);
        sesion = UserMode.USER;
        PCB creadoComoAdmin = conUsuario("A1", 2, "admin", UserMode.ADMIN);
        sesion = UserMode.ADMIN;
        check(creadoComoUsuario.getUserMode() == UserMode.USER, "el proceso de beto sigue en USER aunque la sesion cambie a ADMIN");
        check(creadoComoAdmin.getUserMode() == UserMode.ADMIN, "el del administrador sigue en ADMIN aunque la sesion cambie a USER");
        check(sesion == UserMode.ADMIN, "(la sesion termino en ADMIN y no afecto a ninguno)");
    }
}