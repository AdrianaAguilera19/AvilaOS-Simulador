/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package avilaos.persistence;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

/**
 *Lee y escribe la configuracion inicial en un archivo JSON
 * Usa la libreria Gson, que el enunciado permite para el manejo de JSON
 * @author Antonella Ruiz
 */
public class JSONManager {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private JSONManager() { }

    /**
     * Guarda la configuracion en un archivo JSON. Primero la valida para no escribir datos malos.
     * @param config configuracion a guardar
     * @param path ruta del archivo (se crea o se reemplaza)
     * @throws ConfigException si la configuracion es invalida o no se pudo escribir el archivo
     */
    public static void save(ConfigDTO config, String path) throws ConfigException, IOException {
        if (config == null) {
            throw new ConfigException("No hay configuracion para guardar");
        }
        config.validate();
        try (Writer writer = Files.newBufferedWriter(Paths.get(path), StandardCharsets.UTF_8)) {
            GSON.toJson(config, writer);
        } catch (IOException e) {
            throw new ConfigException("No se pudo guardar el archivo: " + e.getMessage(), e);
        }
    }

    /**
     * Carga una configuracion desde un archivo JSON y la valida.
     * Cualquier problema (archivo que no existe, JSON mal escrito, datos invalidos)
     * se avisa con una ConfigException y no detiene el simulador.
     * @param path ruta del archivo
     * @return la configuracion leida, ya validada
     * @throws ConfigException si el archivo no se puede leer o su contenido no es valido
     */
    public static ConfigDTO load(String path) throws ConfigException {
        try (Reader reader = Files.newBufferedReader(Paths.get(path), StandardCharsets.UTF_8)) {
            ConfigDTO config = GSON.fromJson(reader, ConfigDTO.class);
            // Un archivo vacio hace que Gson devuelva null
            if (config == null) {
                throw new ConfigException("El archivo esta vacio");
            }
            config.validate();
            return config;
        } catch (IOException e) {
            throw new ConfigException("No se pudo leer el archivo: " + e.getMessage(), e);
        } catch (JsonParseException e) {
            throw new ConfigException("El archivo no tiene un JSON valido: " + e.getMessage(), e);
        }
    }

    
}
