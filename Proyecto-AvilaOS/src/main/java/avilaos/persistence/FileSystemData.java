package avilaos.persistence;

import avilaos.enums.HeadDirection;
import avilaos.model.hardware.BlockMap;
import avilaos.model.hardware.Disk;
import avilaos.model.hardware.DiskHead;
import avilaos.os.disk.DiskLimits;
import avilaos.os.disk.SwapArea;
import avilaos.os.filesystem.ChainAllocator;
import avilaos.os.filesystem.FileSystem;
import avilaos.os.filesystem.FileTree;

/**
 * Resultado completo de cargar un JSON del sistema de archivos:
 * conserva la configuracion del disco, el mapa de bloques, el arbol jerarquico
 * y las piezas del SO listas para conectarse a la simulacion.
 */
public final class FileSystemData {

    private final int totalBlocks;
    private final int initialHead;
    private final int swapSizeBlocks;
    private final BlockMap blockMap;
    private final FileTree fileTree;
    private final ChainAllocator allocator;
    private final FileSystem fileSystem;
    private final SwapArea swapArea;

    public FileSystemData(int totalBlocks, int initialHead, int swapSizeBlocks,
            BlockMap blockMap, FileTree fileTree) {
        if (blockMap == null || fileTree == null) {
            throw new IllegalArgumentException("Falta el mapa de bloques o el arbol de archivos");
        }
        if (swapSizeBlocks < 0) {
            throw new IllegalArgumentException("El tamano del swap no puede ser negativo");
        }
        this.totalBlocks = totalBlocks;
        this.initialHead = initialHead;
        this.swapSizeBlocks = swapSizeBlocks;
        this.blockMap = blockMap;
        this.fileTree = fileTree;
        this.allocator = new ChainAllocator(blockMap, totalBlocks);
        this.fileSystem = new FileSystem(fileTree, allocator, blockMap);
        this.swapArea = swapSizeBlocks > 0 ? new SwapArea(blockMap, totalBlocks, swapSizeBlocks) : null;
    }

    /** Bloques de la zona de archivos (los definidos en diskConfig.totalBlocks). */
    public int getTotalBlocks() { return totalBlocks; }

    /** Posicion inicial del cabezal leida del JSON. */
    public int getInitialHead() { return initialHead; }

    /** Bloques reservados para el swap (0 si se cargo sin swap). */
    public int getSwapSizeBlocks() { return swapSizeBlocks; }

    /** Mapa de bloques completo (zona de archivos + swap si aplica). */
    public BlockMap getBlockMap() { return blockMap; }

    public FileTree getFileTree() { return fileTree; }

    public ChainAllocator getAllocator() { return allocator; }

    public FileSystem getFileSystem() { return fileSystem; }

    /** Area de intercambio, o null si se cargo con swapSizeBlocks == 0. */
    public SwapArea getSwapArea() { return swapArea; }

    /**
     * Construye el componente de hardware Disk con el mapa cargado y el cabezal
     * en initialHead. Valida k usando DiskLimits.
     */
    public Disk createDisk(int headSpeed, HeadDirection direction) throws ConfigException {
        String speedError = DiskLimits.checkSpeed(headSpeed);
        if (speedError != null) {
            throw new ConfigException(speedError);
        }
        if (direction == null) {
            throw new ConfigException("Falta la direccion inicial del cabezal");
        }
        DiskHead head = new DiskHead(blockMap.size(), initialHead, headSpeed, direction);
        return new Disk(blockMap, head);
    }
}