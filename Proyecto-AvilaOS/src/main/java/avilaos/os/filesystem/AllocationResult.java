/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package avilaos.os.filesystem;

import avilaos.datastructures.BlockChain;

/**
 * Resultado de pedir bloques para un archivo: la cadena reservada o el motivo del rechazo.
 * @author Antonella Ruiz
 */
public final class AllocationResult {
    private final BlockChain chain;
    private final String message;

    private AllocationResult(BlockChain chain, String message) {
        this.chain = chain;
        this.message = message;
    }

    public static AllocationResult granted(BlockChain chain) {
        return new AllocationResult(chain, null);
    }

    public static AllocationResult rejected(String message) {
        return new AllocationResult(null, message);
    }

    public boolean isGranted() { return chain != null; }

    /** La cadena reservada, o null si se rechazo. */
    public BlockChain getChain() { return chain; }

    /** El motivo del rechazo, o null si se concedio. */
    public String getMessage() { return message; }
}