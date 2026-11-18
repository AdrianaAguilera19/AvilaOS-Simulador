package avilaos.persistence;

import avilaos.datastructures.DynamicArray;
import avilaos.enums.DiskSchedulingPolicy;
import avilaos.enums.HeadDirection;
import avilaos.enums.IOOperationType;
import avilaos.enums.SchedulingAlgorithm;
import avilaos.enums.UserMode;
import avilaos.model.hardware.DiskHead;
import avilaos.os.disk.DiskLimits;
import avilaos.os.filesystem.ActiveUser;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

/**
 * Carga y valida el JSON de procesos y configuracion (J04, J05, J06, J07 y J10).
 * Acepta exactamente el formato del enunciado y ademas campos opcionales (headSpeed,
 * initialHeadDirection, computer.name, priority, user/userMode, fileSizeBlocks, newName)
 * con valores por defecto seguros.
 */
public final class ProcessConfigJsonManager {

    public static final String DEFAULT_COMPUTER_NAME = "Computador 1";
    public static final int DEFAULT_PRIORITY = 1;
    public static final HeadDirection DEFAULT_HEAD_DIRECTION = HeadDirection.UP;

    private ProcessConfigJsonManager() { }

    public static ProcessConfigData load(String path) throws ConfigException {
        if (path == null || path.trim().isEmpty()) {
            throw new ConfigException("La ruta del archivo JSON de procesos esta vacia");
        }
        try (Reader reader = Files.newBufferedReader(Paths.get(path), StandardCharsets.UTF_8)) {
            JsonElement root = JsonParser.parseReader(reader);
            return parseElement(root);
        } catch (IOException e) {
            throw new ConfigException("No se pudo leer el archivo de procesos: " + e.getMessage(), e);
        } catch (JsonParseException e) {
            throw new ConfigException("El archivo de procesos no es un JSON valido: " + e.getMessage(), e);
        }
    }

    public static ProcessConfigData parseJson(String jsonText) throws ConfigException {
        if (jsonText == null || jsonText.trim().isEmpty()) {
            throw new ConfigException("El contenido JSON de procesos esta vacio");
        }
        try {
            JsonElement root = JsonParser.parseString(jsonText);
            return parseElement(root);
        } catch (JsonParseException e) {
            throw new ConfigException("El contenido de procesos no es un JSON valido: " + e.getMessage(), e);
        }
    }

    private static ProcessConfigData parseElement(JsonElement rootElement) throws ConfigException {
        if (rootElement == null || rootElement.isJsonNull()) {
            throw new ConfigException("El archivo JSON de procesos esta vacio");
        }
        if (!rootElement.isJsonObject()) {
            throw new ConfigException("La raiz del JSON de procesos debe ser un objeto {}");
        }
        JsonObject root = rootElement.getAsJsonObject();

        // 1. globalConfig
        JsonObject global = requireObject(root, "globalConfig", "la raiz del JSON");
        int cycleDurationMs = requireInt(global, "cycleDurationMs", "globalConfig");
        if (cycleDurationMs <= 0) {
            throw new ConfigException("cycleDurationMs debe ser mayor que 0 (se indico " + cycleDurationMs + ")");
        }

        int swapSizeBlocks = requireInt(global, "swapSizeBlocks", "globalConfig");
        if (swapSizeBlocks <= 0) {
            throw new ConfigException("swapSizeBlocks debe ser mayor que 0 (se indico " + swapSizeBlocks + ")");
        }

        String diskPolicyText = requireString(global, "initialDiskPolicy", "globalConfig");
        DiskSchedulingPolicy initialDiskPolicy = DiskSchedulingPolicy.desdeTexto(diskPolicyText);
        if (initialDiskPolicy == null) {
            throw new ConfigException("Politica de disco desconocida en globalConfig: '" + diskPolicyText
                    + "' (use FIFO, SSTF, SCAN o C-SCAN)");
        }

        // Campos opcionales de globalConfig (J06)
        int headSpeed = optionalInt(global, "headSpeed", DiskHead.DEFAULT_SPEED, "globalConfig");
        String speedErr = DiskLimits.checkSpeed(headSpeed);
        if (speedErr != null) {
            throw new ConfigException(speedErr);
        }

        HeadDirection headDirection = DEFAULT_HEAD_DIRECTION;
        String dirText = optionalString(global, "initialHeadDirection", "globalConfig");
        if (dirText != null) {
            headDirection = HeadDirection.fromText(dirText);
            if (headDirection == null) {
                throw new ConfigException("Direccion inicial del cabezal desconocida: '" + dirText
                        + "' (use UP o DOWN)");
            }
        }

        // 2. computer
        JsonObject comp = requireObject(root, "computer", "la raiz del JSON");
        String computerName = optionalString(comp, "name", "computer");
        if (computerName == null) {
            computerName = DEFAULT_COMPUTER_NAME;
        }

        int ramCapacity = requireInt(comp, "ramCapacity", "computer");
        if (ramCapacity <= 0) {
            throw new ConfigException("ramCapacity debe ser mayor que 0 (se indico " + ramCapacity + ")");
        }

        String cpuPolicyText = requireString(comp, "policy", "computer");
        SchedulingAlgorithm cpuPolicy = parseCpuPolicy(cpuPolicyText);
        if (cpuPolicy == null) {
            throw new ConfigException("Politica de CPU desconocida en computer: '" + cpuPolicyText
                    + "' (use FCFS, EDF, PRIORITY o ROUND_ROBIN)");
        }

        int quantum = optionalInt(comp, "quantum", 0, "computer");
        if (quantum < 0) {
            throw new ConfigException("quantum no puede ser negativo (se indico " + quantum + ")");
        }
        if (cpuPolicy == SchedulingAlgorithm.ROUND_ROBIN && quantum <= 0) {
            throw new ConfigException("Para la politica ROUND_ROBIN, quantum debe ser mayor que 0");
        }

        // 3. processes
        JsonArray procArray = requireArray(root, "processes", "la raiz del JSON");
        if (procArray.size() == 0) {
            throw new ConfigException("El arreglo 'processes' debe contener al menos un proceso");
        }

        DynamicArray<ProcessConfigData.ProcessSpec> specs = new DynamicArray<>();
        for (int i = 0; i < procArray.size(); i++) {
            String ctx = "processes[" + i + "]";
            JsonElement el = procArray.get(i);
            if (el == null || !el.isJsonObject()) {
                throw new ConfigException("El elemento " + ctx + " debe ser un objeto JSON");
            }
            specs.add(parseProcess(el.getAsJsonObject(), ctx));
        }

        return new ProcessConfigData(
                cycleDurationMs, swapSizeBlocks, initialDiskPolicy, headSpeed, headDirection,
                computerName, ramCapacity, cpuPolicy, quantum, specs
        );
    }

    private static ProcessConfigData.ProcessSpec parseProcess(JsonObject obj, String ctx) throws ConfigException {
        String name = requireString(obj, "name", ctx);

        int memory = requireInt(obj, "memory", ctx);
        if (memory <= 0) {
            throw new ConfigException("memory debe ser mayor que 0 en " + ctx + " (" + name + ")");
        }

        int deadline = requireInt(obj, "deadline", ctx);
        if (deadline <= 0) {
            throw new ConfigException("deadline debe ser mayor que 0 en " + ctx + " (" + name + ")");
        }

        int instructions = requireInt(obj, "instructions", ctx);
        if (instructions <= 0) {
            throw new ConfigException("instructions debe ser mayor que 0 en " + ctx + " (" + name + ")");
        }

        String targetPath = requireString(obj, "targetPath", ctx);
        validateTargetPath(targetPath, ctx);

        String opText = requireString(obj, "operation", ctx);
        IOOperationType operation = IOOperationType.fromText(opText);
        if (operation == null || !operation.isFileOperation()) {
            throw new ConfigException("Operacion invalida en " + ctx + ": '" + opText
                    + "' (use CREATE, READ, UPDATE o DELETE)");
        }

        int fileSizeBlocks = optionalInt(obj, "fileSizeBlocks", 0, ctx);
        if (fileSizeBlocks < 0) {
            throw new ConfigException("fileSizeBlocks no puede ser negativo en " + ctx);
        }
        if (operation == IOOperationType.CREATE && fileSizeBlocks <= 0) {
            throw new ConfigException("La operacion CREATE en " + ctx
                    + " requiere 'fileSizeBlocks' mayor que 0");
        }

        String newName = optionalString(obj, "newName", ctx);
        if (newName != null) {
            if (operation != IOOperationType.UPDATE) {
                throw new ConfigException("Solo la operacion UPDATE puede incluir 'newName' (" + ctx + ")");
            }
            if (newName.indexOf('/') >= 0) {
                throw new ConfigException("newName no puede contener '/' en " + ctx + ": '" + newName + "'");
            }
        }

        // Opcionales (J06): prioridad y usuario/modo
        int priority = optionalInt(obj, "priority", DEFAULT_PRIORITY, ctx);
        if (priority <= 0) {
            throw new ConfigException("priority debe ser mayor que 0 en " + ctx);
        }

        ActiveUser actor = resolveActor(obj, ctx);

        return new ProcessConfigData.ProcessSpec(
                name, priority, memory, deadline, instructions, targetPath,
                operation, fileSizeBlocks, newName, actor.getName(), actor.getMode()
        );
    }

    /**
     * Resuelve el usuario y modo del proceso (J06).
     * Si no vienen en el JSON (como en el ejemplo del enunciado), usa admin (ADMIN).
     */
    private static ActiveUser resolveActor(JsonObject obj, String ctx) throws ConfigException {
        String userText = optionalString(obj, "user", ctx);
        if (userText == null) {
            userText = optionalString(obj, "userName", ctx);
        }
        String modeText = optionalString(obj, "userMode", ctx);

        if (userText == null && modeText == null) {
            return ActiveUser.admin();
        }

        UserMode mode;
        if (modeText != null) {
            mode = UserMode.fromText(modeText);
            if (mode == null) {
                throw new ConfigException("userMode desconocido en " + ctx + ": '" + modeText
                        + "' (use ADMIN o USER)");
            }
            if (userText == null) {
                if (mode.isAdmin()) {
                    return ActiveUser.admin();
                }
                throw new ConfigException("En " + ctx + ", userMode USER requiere indicar el campo 'user'");
            }
        } else {
            mode = ActiveUser.ADMIN_NAME.equalsIgnoreCase(userText) ? UserMode.ADMIN : UserMode.USER;
        }

        try {
            return new ActiveUser(userText, mode);
        } catch (IllegalArgumentException e) {
            throw new ConfigException("Usuario invalido en " + ctx + ": " + e.getMessage(), e);
        }
    }

    private static void validateTargetPath(String path, String ctx) throws ConfigException {
        if (!path.startsWith("/")) {
            throw new ConfigException("targetPath en " + ctx + " debe empezar con '/': '" + path + "'");
        }
        if (path.equals("/")) {
            throw new ConfigException("targetPath en " + ctx + " no puede ser la raiz '/'");
        }
        if (path.endsWith("/") || path.contains("//")) {
            throw new ConfigException("targetPath en " + ctx + " tiene partes vacias: '" + path + "'");
        }
    }

    private static SchedulingAlgorithm parseCpuPolicy(String text) {
        String clean = text.trim().replace('-', '_').replace(' ', '_').toUpperCase();
        if ("RR".equals(clean)) {
            return SchedulingAlgorithm.ROUND_ROBIN;
        }
        for (SchedulingAlgorithm alg : SchedulingAlgorithm.values()) {
            if (alg.name().equals(clean)) {
                return alg;
            }
        }
        return null;
    }

    // ------------------------------------------------------------------ Validadores auxiliares

    private static JsonObject requireObject(JsonObject obj, String field, String ctx) throws ConfigException {
        if (!obj.has(field) || obj.get(field).isJsonNull()) {
            throw new ConfigException("Falta el objeto '" + field + "' en " + ctx);
        }
        JsonElement el = obj.get(field);
        if (!el.isJsonObject()) {
            throw new ConfigException("El campo '" + field + "' en " + ctx + " debe ser un objeto JSON");
        }
        return el.getAsJsonObject();
    }

    private static JsonArray requireArray(JsonObject obj, String field, String ctx) throws ConfigException {
        if (!obj.has(field) || obj.get(field).isJsonNull()) {
            throw new ConfigException("Falta el arreglo '" + field + "' en " + ctx);
        }
        JsonElement el = obj.get(field);
        if (!el.isJsonArray()) {
            throw new ConfigException("El campo '" + field + "' en " + ctx + " debe ser un arreglo JSON");
        }
        return el.getAsJsonArray();
    }

    private static String requireString(JsonObject obj, String field, String ctx) throws ConfigException {
        if (!obj.has(field) || obj.get(field).isJsonNull()) {
            throw new ConfigException("Falta el campo '" + field + "' en " + ctx);
        }
        return parseNonEmptyString(obj.get(field), "'" + field + "' en " + ctx);
    }

    private static String optionalString(JsonObject obj, String field, String ctx) throws ConfigException {
        if (!obj.has(field) || obj.get(field).isJsonNull()) {
            return null;
        }
        return parseNonEmptyString(obj.get(field), "'" + field + "' en " + ctx);
    }

    private static String parseNonEmptyString(JsonElement el, String label) throws ConfigException {
        if (el == null || !el.isJsonPrimitive() || !el.getAsJsonPrimitive().isString()) {
            throw new ConfigException("El campo " + label + " debe ser texto");
        }
        String value = el.getAsString().trim();
        if (value.isEmpty()) {
            throw new ConfigException("El campo " + label + " no puede estar vacio");
        }
        return value;
    }

    private static int requireInt(JsonObject obj, String field, String ctx) throws ConfigException {
        if (!obj.has(field) || obj.get(field).isJsonNull()) {
            throw new ConfigException("Falta el campo '" + field + "' en " + ctx);
        }
        return parseInt(obj.get(field), "'" + field + "' en " + ctx);
    }

    private static int optionalInt(JsonObject obj, String field, int defaultValue, String ctx) throws ConfigException {
        if (!obj.has(field) || obj.get(field).isJsonNull()) {
            return defaultValue;
        }
        return parseInt(obj.get(field), "'" + field + "' en " + ctx);
    }

    private static int parseInt(JsonElement el, String label) throws ConfigException {
        if (el == null || !el.isJsonPrimitive()) {
            throw new ConfigException("El campo " + label + " debe ser un numero entero");
        }
        JsonPrimitive prim = el.getAsJsonPrimitive();
        if (!prim.isNumber()) {
            throw new ConfigException("El campo " + label + " debe ser un numero entero (no texto ni booleano)");
        }
        String raw = prim.getAsString().trim();
        try {
            return Integer.parseInt(raw);
        } catch (NumberFormatException e) {
            throw new ConfigException("El campo " + label + " debe ser un numero entero valido: " + raw);
        }
    }
}