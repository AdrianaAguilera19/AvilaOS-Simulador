/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package avilaos.os.disk.policy;

import avilaos.enums.DiskSchedulingPolicy;
import avilaos.interfaces.DiskPolicy;

/**
 * Crea la politica que corresponde a cada valor del enum. Lo usan el selector de la GUI y el JSON
 * (initialDiskPolicy). Una politica extra (LOOK, C-LOOK) solo agrega aqui su caso.
 * @author Antonella Ruiz
 */
public final class DiskPolicyFactory {

    private DiskPolicyFactory() {
    }

    public static DiskPolicy create(DiskSchedulingPolicy type) {
        if (type == null) {
            throw new IllegalArgumentException("Falta el tipo de politica de disco");
        }
        switch (type) {
            case FIFO:
                return new FifoPolicy();
            case SSTF:
                return new SstfPolicy();
            case SCAN:
                return new ScanPolicy();
            case C_SCAN:
                return new CScanPolicy();
            case LOOK:
                return new LookPolicy();
            default:
                throw new IllegalArgumentException("No hay clase para la politica " + type);
        }
    }
}