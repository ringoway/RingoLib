package com.ringoway.ringolib.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.logging.LogUtils;
import com.ringoway.ringolib.core.RingoLibConfig;
import org.slf4j.Logger;

import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;

public class RingoWarningData {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String FILE_NAME = "shown_warnings.json";
    private static final String LEGACY_FILE_NAME = "ringolib_shown_warnings.json";

    private static final Set<String> SHOWN_KEYS = new HashSet<>();
    private static boolean loaded = false;

    public static synchronized void load() {
        if (loaded) return;
        loaded = true;
        Path file = RingoLibConfig.resolvePathWithLegacyFallback(FILE_NAME, LEGACY_FILE_NAME);
        if (Files.exists(file)) {
            try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                JsonObject json = GSON.fromJson(reader, JsonObject.class);
                if (json != null && json.has("shown") && json.get("shown").isJsonArray()) {
                    for (JsonElement el : json.getAsJsonArray("shown")) {
                        if (el != null && el.isJsonPrimitive()) {
                            SHOWN_KEYS.add(el.getAsString());
                        }
                    }
                }
            } catch (Exception e) {
                LOGGER.error("RingoLib: Failed to load shown warnings", e);
            }
        }
    }

    public static synchronized void markShown(String key) {
        load();
        if (SHOWN_KEYS.add(key)) {
            save();
        }
    }

    public static synchronized boolean isShown(String key) {
        load();
        return SHOWN_KEYS.contains(key);
    }

    public static synchronized void reset() {
        SHOWN_KEYS.clear();
        loaded = true;
        save();
    }

    private static void save() {
        Path file = RingoLibConfig.getConfigDir().resolve(FILE_NAME);
        try {
            Files.createDirectories(file.getParent());
            JsonObject json = new JsonObject();
            JsonArray arr = new JsonArray();
            SHOWN_KEYS.forEach(arr::add);
            json.add("shown", arr);
            try (Writer writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
                GSON.toJson(json, writer);
            }
        } catch (Exception e) {
            LOGGER.error("RingoLib: Failed to save shown warnings", e);
        }
    }
}
