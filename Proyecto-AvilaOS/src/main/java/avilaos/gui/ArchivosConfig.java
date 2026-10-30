/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package avilaos.gui;

import avilaos.model.hardware.Cluster;
import avilaos.persistence.ConfigDTO;
import avilaos.persistence.ConfigException;
import avilaos.persistence.ConfigExporter;
import avilaos.persistence.ConfigLoader;
import avilaos.persistence.JSONManager;
import java.awt.Component;
import java.io.File;
import java.io.IOException;
import javax.swing.JFileChooser;
import javax.swing.JOptionPane;
import javax.swing.filechooser.FileNameExtensionFilter;

/**
 * Guardar y cargar la configuracion desde la interfaz: abre el selector de archivos, llama a
 * ConfigExporter / JSONManager / ConfigLoader y avisa con un mensaje si algo sale mal, sin
 * detener el simulador. La logica de los archivos vive en el paquete persistence.
 */
public final class ArchivosConfig {

    private ArchivosConfig() { }

    /**
     * Pide un archivo y guarda en el la configuracion del cluster.
     * @param padre ventana sobre la que se muestran los dialogos
     * @param cluster cluster a guardar
     * @return true si se guardo
     */
    public static boolean guardar(Component padre, Cluster cluster) {
        JFileChooser selector = selector("Guardar configuracion");
        selector.setSelectedFile(new File("configuracion.json"));
        if (selector.showSaveDialog(padre) != JFileChooser.APPROVE_OPTION) {
            return false;
        }
        File archivo = selector.getSelectedFile();
        if (!archivo.getName().toLowerCase().endsWith(".json")) {
            archivo = new File(archivo.getPath() + ".json");
        }
        if (archivo.exists() && JOptionPane.showConfirmDialog(padre,
                "El archivo " + archivo.getName() + " ya existe. ¿Reemplazarlo?",
                "Guardar configuracion", JOptionPane.YES_NO_OPTION) != JOptionPane.YES_OPTION) {
            return false;
        }
        try {
            JSONManager.save(ConfigExporter.fromCluster(cluster), archivo.getPath());
        } catch (ConfigException | IOException e) {
            error(padre, "No se pudo guardar la configuracion:\n" + e.getMessage());
            return false;
        }
        JOptionPane.showMessageDialog(padre, "Configuracion guardada en " + archivo.getName(),
                "Guardar configuracion", JOptionPane.INFORMATION_MESSAGE);
        return true;
    }

    /**
     * Pide un archivo y arma el cluster que describe (sin iniciar el reloj).
     * @param padre ventana sobre la que se muestran los dialogos
     * @return el cluster nuevo, o null si se cancelo o el archivo no era valido
     */
    public static Cluster cargar(Component padre) {
        JFileChooser selector = selector("Cargar configuracion");
        if (selector.showOpenDialog(padre) != JFileChooser.APPROVE_OPTION) {
            return null;
        }
        try {
            ConfigDTO config = JSONManager.load(selector.getSelectedFile().getPath());
            return ConfigLoader.build(config);
        } catch (ConfigException e) {
            error(padre, "El archivo no se pudo cargar:\n" + e.getMessage());
            return null;
        }
    }

    private static JFileChooser selector(String titulo) {
        JFileChooser selector = new JFileChooser();
        selector.setDialogTitle(titulo);
        selector.setFileFilter(new FileNameExtensionFilter("Configuracion AvilaOS (*.json)", "json"));
        return selector;
    }

    private static void error(Component padre, String mensaje) {
        JOptionPane.showMessageDialog(padre, mensaje, "AvilaOS", JOptionPane.ERROR_MESSAGE);
    }
}
