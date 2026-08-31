package dev.elytracombat.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public final class ConfigManager {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path PATH = FabricLoader.getInstance().getConfigDir().resolve("elytra-combat.json");

    private static volatile ElytraCombatConfig current = defaults();

    private ConfigManager() {
    }

    public static ElytraCombatConfig get() {
        return current;
    }

    public static ElytraCombatConfig copyCurrent() {
        return copy(current, ElytraCombatConfig.class);
    }

    /** Gson round trip copy used by screens so cancelling reverts edits. */
    public static <T> T copy(T value, Class<T> type) {
        return GSON.fromJson(GSON.toJson(value), type);
    }

    public static synchronized void saveAndApply(ElytraCombatConfig config) throws IOException {
        config.validate();
        write(config);
        current = config;
    }

    public static void loadAtStartup() {
        if (Files.notExists(PATH)) {
            try {
                writeDefaults();
            } catch (IOException exception) {
                dev.elytracombat.ElytraCombat.LOGGER.error("Could not create {}", PATH, exception);
            }
            current = defaults();
            return;
        }

        try {
            current = readValidated();
            write(current);
        } catch (IOException | JsonParseException | IllegalArgumentException exception) {
            current = defaults();
            dev.elytracombat.ElytraCombat.LOGGER.error("Invalid config at {}; using defaults without overwriting it: {}", PATH, exception.getMessage());
        }
    }

    public static void reload() throws IOException {
        current = readValidated();
        write(current);
    }

    private static ElytraCombatConfig readValidated() throws IOException {
        try (Reader reader = Files.newBufferedReader(PATH, StandardCharsets.UTF_8)) {
            ElytraCombatConfig config = GSON.fromJson(reader, ElytraCombatConfig.class);
            if (config == null) {
                throw new JsonParseException("Config is empty");
            }
            config.validate();
            return config;
        }
    }

    private static void writeDefaults() throws IOException {
        write(defaults());
    }

    private static void write(ElytraCombatConfig config) throws IOException {
        Files.createDirectories(PATH.getParent());
        try (Writer writer = Files.newBufferedWriter(PATH, StandardCharsets.UTF_8)) {
            GSON.toJson(config, writer);
        }
    }

    private static ElytraCombatConfig defaults() {
        ElytraCombatConfig config = new ElytraCombatConfig();
        config.validate();
        return config;
    }
}
