package avilaos.pruebas;

import avilaos.metrics.MetricsCalculator;
import avilaos.model.hardware.Cluster;
import avilaos.model.hardware.Computer;
import avilaos.model.process.ProcessFactory;
import avilaos.scheduler.FCFSPolicy;

public class PruebaP11 {

    public static void main(String[] args) {
        Cluster cl = new Cluster(1, 0);
        Computer c1 = cl.addComputer("C1", new FCFSPolicy(), 100);
        Computer c2 = cl.addComputer("C2", new FCFSPolicy(), 100);

        // C1: A y B terminan; C se vence por deadline antes de ejecutarse
        cl.submit(ProcessFactory.createCpuBound("A", 1, 10, 4, 100, 1, 0));
        cl.submit(ProcessFactory.createCpuBound("B", 1, 10, 4, 100, 1, 0));
        cl.submit(ProcessFactory.createCpuBound("C", 1, 10, 4, 6, 1, 0));
        // C2: E termina
        cl.submit(ProcessFactory.createCpuBound("E", 1, 10, 5, 100, 2, 0));

        for (int i = 0; i < 10; i++) cl.tick();

        System.out.println(MetricsCalculator.forComputer(c1));
        System.out.println(MetricsCalculator.forComputer(c2));
        System.out.println(MetricsCalculator.forSystem(cl));

        Cluster vacio = new Cluster(1, 0);
        Computer v = vacio.addComputer("V", new FCFSPolicy(), 100);
        vacio.addComputer("V2", new FCFSPolicy(), 100);
        System.out.println("Sin datos: " + MetricsCalculator.forComputer(v));
    }
}