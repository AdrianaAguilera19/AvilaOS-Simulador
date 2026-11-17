package avilaos.os.filesystem;

/**
 * Color de cada archivo, el mismo en el mapa de bloques y en la tabla de archivos.
 * @author Antonella Ruiz
 */
public final class FileColorPalette {
    /** Valor para los nodos que no tienen color (los directorios). */
    public static final int NO_COLOR = -1;

    /** Pasos del tono, la saturacion y el brillo (secuencia de Kronecker en 3 dimensiones). */
    private static final double HUE_STEP = 0.5497004779;
    private static final double SATURATION_STEP = 0.8191725134;
    private static final double BRIGHTNESS_STEP = 0.6710436067;
    private static final double MIN_SATURATION = 0.40;
    private static final double MAX_SATURATION = 0.90;
    /** El brillo minimo evita colores tan oscuros que no se lean sobre el fondo. */
    private static final double MIN_BRIGHTNESS = 0.66;
    private static final double MAX_BRIGHTNESS = 0.96;

    private FileColorPalette() { }

    /** Color (0xRRGGBB) del archivo cuyo primer bloque es ese. */
    public static int colorFor(int firstBlock) {
        if (firstBlock < 0) {
            throw new IllegalArgumentException("El primer bloque no puede ser negativo");
        }
        double hue = (firstBlock * HUE_STEP) % 1.0;
        double saturation = MIN_SATURATION + (MAX_SATURATION - MIN_SATURATION) * ((firstBlock * SATURATION_STEP) % 1.0);
        double brightness = MIN_BRIGHTNESS + (MAX_BRIGHTNESS - MIN_BRIGHTNESS) * ((firstBlock * BRIGHTNESS_STEP) % 1.0);
        return hsvToRgb(hue, saturation, brightness);
    }

    /** Texto «#RRGGBB» del color, o vacio si no tiene color (para la tabla). */
    public static String toHex(int rgb) {
        if (rgb == NO_COLOR) {
            return "";
        }
        String hex = Integer.toHexString(rgb & 0xFFFFFF).toUpperCase();
        while (hex.length() < 6) {
            hex = "0" + hex;
        }
        return "#" + hex;
    }

    private static int hsvToRgb(double h, double s, double v) {
        double sector = h * 6.0;
        int i = (int) Math.floor(sector);
        double f = sector - i;
        double p = v * (1 - s);
        double q = v * (1 - s * f);
        double t = v * (1 - s * (1 - f));
        double r;
        double g;
        double b;
        switch (i % 6) {
            case 0: r = v; g = t; b = p; break;
            case 1: r = q; g = v; b = p; break;
            case 2: r = p; g = v; b = t; break;
            case 3: r = p; g = q; b = v; break;
            case 4: r = t; g = p; b = v; break;
            default: r = v; g = p; b = q; break;
        }
        return (channel(r) << 16) | (channel(g) << 8) | channel(b);
    }

    private static int channel(double value) {
        return (int) Math.round(value * 255);
    }
}