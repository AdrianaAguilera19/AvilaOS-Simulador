package avilaos.os.filesystem;

import avilaos.datastructures.BlockChain;

/**
 * Resultado de una operacion sobre el sistema de archivos. Los errores del usuario (ruta que no
 * existe, nombre repetido, archivo danado...) NO lanzan excepciones: se devuelven aqui con un mensaje
 * que la interfaz puede mostrar, y la simulacion sigue.
 */
public final class FsResult {

    private final boolean ok;
    private final String message;
    private final FileNode node;
    private final BlockChain chain;
    private final int blocks;

    private FsResult(boolean ok, String message, FileNode node, BlockChain chain, int blocks) {
        this.ok = ok;
        this.message = message;
        this.node = node;
        this.chain = chain;
        this.blocks = blocks;
    }

    /**
     * @param message lo que se hizo, en texto
     * @param node archivo o directorio afectado (puede ser null)
     * @param chain bloques recorridos al leer (puede ser null)
     * @param blocks bloques leidos o liberados segun la operacion
     */
    public static FsResult done(String message, FileNode node, BlockChain chain, int blocks) {
        return new FsResult(true, message, node, chain, blocks);
    }

    /** La operacion no se hizo y no cambio nada; el mensaje explica por que. */
    public static FsResult rejected(String message) {
        return new FsResult(false, message, null, null, 0);
    }

    public boolean isOk() { return ok; }

    public String getMessage() { return message; }

    /** @return nodo afectado (el creado, el leido, el renombrado o el eliminado) o null */
    public FileNode getNode() { return node; }

    /** @return bloques en el orden en que se leyeron (solo en read) o null */
    public BlockChain getChain() { return chain; }

    /** @return bloques leidos (read) o liberados (delete) */
    public int getBlocks() { return blocks; }

    @Override
    public String toString() {
        return (ok ? "OK: " : "RECHAZADO: ") + message;
    }
}
