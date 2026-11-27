package com.ringoway.ringolib.core;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.logging.LogUtils;
import com.ringoway.ringolib.api.RecipeFilterManager;
import com.ringoway.ringolib.api.RegistryRedirectManager;
import com.ringoway.ringolib.api.RingoMigrationAPI;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.fml.loading.FMLPaths;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

public class RingoLibConfig {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public static final String SUBDIR = "ringolib";
    public static final String MIGRATIONS_FILE = "migrations.json";
    public static final String DISABLED_RECIPES_FILE = "disabled_recipes.json";

    private static final String LEGACY_MIGRATIONS_FILE = "ringolib_migrations.json";
    private static final String LEGACY_DISABLED_RECIPES_FILE = "ringolib_disabled_recipes.json";

    @FunctionalInterface
    private interface RuleRegistrar {
        void register(String from, String to, @Nullable String requiredMod, @Nullable String description);
    }

    public static Path getConfigDir() {
        Path dir = FMLPaths.CONFIGDIR.get().resolve(SUBDIR);
        if (!Files.exists(dir)) {
            try {
                Files.createDirectories(dir);
            } catch (Exception e) {
                LOGGER.error("RingoLib: Failed to create config/{} directory", SUBDIR, e);
            }
        }
        return dir;
    }

    public static void loadModpackRules() {
        loadDisabledRecipes();

        Path file = resolvePathWithLegacyFallback(MIGRATIONS_FILE, LEGACY_MIGRATIONS_FILE);
        if (!Files.exists(file)) {
            createDefaultConfig(file);
            return;
        }

        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            JsonObject root = GSON.fromJson(reader, JsonObject.class);
            if (root == null) return;

            parseSection(root, "items", RingoMigrationAPI::registerItemRedirect, RingoMigrationAPI::registerItemMigration);
            parseSection(root, "blocks", RingoMigrationAPI::registerBlockRedirect, RingoMigrationAPI::registerBlockMigration);
            parseSection(root, "fluids", RingoMigrationAPI::registerFluidRedirect, RingoMigrationAPI::registerFluidMigration);
            parseRegistryRedirects(root);

            LOGGER.info("RingoLib: Loaded custom migration & redirect rules from config/{}/{}", SUBDIR, file.getFileName());
        } catch (Exception e) {
            LOGGER.error("RingoLib: Failed to load config/{}/{}", SUBDIR, file.getFileName(), e);
        }
    }

    private static void parseSection(
            JsonObject root,
            String sectionName,
            RuleRegistrar redirectRegistrar,
            RuleRegistrar migrationRegistrar
    ) {
        if (!root.has(sectionName) || !root.get(sectionName).isJsonArray()) {
            return;
        }
        JsonArray entries = root.getAsJsonArray(sectionName);
        for (JsonElement el : entries) {
            if (!el.isJsonObject()) continue;
            JsonObject obj = el.getAsJsonObject();

            String from = getOptionalString(obj, "from");
            String to = getOptionalString(obj, "to");
            if (from == null || to == null || ResourceLocation.tryParse(from) == null || ResourceLocation.tryParse(to) == null) {
                LOGGER.warn("RingoLib: Skipping invalid '{}' entry in {}: from='{}', to='{}'",
                        sectionName, MIGRATIONS_FILE, from, to);
                continue;
            }

            String req = getOptionalString(obj, "required_mod");
            String desc = getOptionalString(obj, "description");
            boolean redirectRegistry = obj.has("redirect_registry")
                    && obj.get("redirect_registry").isJsonPrimitive()
                    && obj.get("redirect_registry").getAsBoolean();

            if (redirectRegistry) {
                redirectRegistrar.register(from, to, req, desc);
            } else {
                migrationRegistrar.register(from, to, req, desc);
            }
        }
    }

    private static void parseRegistryRedirects(JsonObject root) {
        if (!root.has("registry_redirects") || !root.get("registry_redirects").isJsonArray()) {
            return;
        }
        JsonArray redirects = root.getAsJsonArray("registry_redirects");
        for (JsonElement el : redirects) {
            if (!el.isJsonObject()) continue;
            JsonObject obj = el.getAsJsonObject();

            String registryStr = getOptionalString(obj, "registry");
            String fromStr = getOptionalString(obj, "from");
            String toStr = getOptionalString(obj, "to");

            ResourceLocation regLoc = registryStr != null ? ResourceLocation.tryParse(registryStr) : null;
            ResourceLocation fromLoc = fromStr != null ? ResourceLocation.tryParse(fromStr) : null;
            ResourceLocation toLoc = toStr != null ? ResourceLocation.tryParse(toStr) : null;

            if (regLoc == null || fromLoc == null || toLoc == null) {
                LOGGER.warn("RingoLib: Skipping invalid 'registry_redirects' entry in {}: registry='{}', from='{}', to='{}'",
                        MIGRATIONS_FILE, registryStr, fromStr, toStr);
                continue;
            }

            String req = getOptionalString(obj, "required_mod");
            String desc = getOptionalString(obj, "description");

            ResourceKey<? extends Registry<?>> regKey = ResourceKey.createRegistryKey(regLoc);
            RegistryRedirectManager.registerRedirect(regKey, fromLoc, toLoc, req, desc);
        }
    }

    @Nullable
    private static String getOptionalString(JsonObject obj, String memberName) {
        if (!obj.has(memberName)) return null;
        JsonElement el = obj.get(memberName);
        if (el == null || el.isJsonNull() || !el.isJsonPrimitive()) return null;
        String value = el.getAsString().trim();
        return value.isEmpty() ? null : value;
    }

    public static void loadDisabledRecipes() {
        RecipeFilterManager.clearConfigRules();
        Path file = resolvePathWithLegacyFallback(DISABLED_RECIPES_FILE, LEGACY_DISABLED_RECIPES_FILE);
        if (!Files.exists(file)) {
            createDefaultDisabledRecipesConfig(file);
            return;
        }

        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            JsonObject root = GSON.fromJson(reader, JsonObject.class);
            if (root != null && root.has("disabled_recipes") && root.get("disabled_recipes").isJsonArray()) {
                JsonArray arr = root.getAsJsonArray("disabled_recipes");
                int count = 0;
                for (JsonElement el : arr) {
                    if (el != null && el.isJsonPrimitive()) {
                        RecipeFilterManager.disableConfigRecipe(el.getAsString());
                        count++;
                    }
                }
                LOGGER.info("RingoLib: Loaded {} disabled recipe rule(s) from config/{}/{}", count, SUBDIR, file.getFileName());
            }
        } catch (Exception e) {
            LOGGER.error("RingoLib: Failed to load config/{}/{}", SUBDIR, file.getFileName(), e);
        }
    }

    public static synchronized boolean addDisabledRecipeToFile(String rule) {
        Path file = resolvePathWithLegacyFallback(DISABLED_RECIPES_FILE, LEGACY_DISABLED_RECIPES_FILE);
        if (!Files.exists(file)) {
            createDefaultDisabledRecipesConfig(file);
        }

        try {
            JsonObject root = readDisabledRecipesRoot(file);
            JsonArray arr = root.has("disabled_recipes") && root.get("disabled_recipes").isJsonArray()
                    ? root.getAsJsonArray("disabled_recipes")
                    : new JsonArray();

            for (JsonElement el : arr) {
                if (el != null && el.isJsonPrimitive() && el.getAsString().trim().equals(rule)) {
                    return false;
                }
            }

            arr.add(rule);
            root.add("disabled_recipes", arr);
            writeDisabledRecipesRoot(file, root);
            return true;
        } catch (Exception e) {
            LOGGER.error("RingoLib: Failed to update {}", DISABLED_RECIPES_FILE, e);
            return false;
        }
    }

    public static synchronized boolean removeDisabledRecipeFromFile(String rule) {
        Path file = resolvePathWithLegacyFallback(DISABLED_RECIPES_FILE, LEGACY_DISABLED_RECIPES_FILE);
        if (!Files.exists(file)) {
            return false;
        }

        try {
            JsonObject root = readDisabledRecipesRoot(file);
            if (!root.has("disabled_recipes") || !root.get("disabled_recipes").isJsonArray()) {
                return false;
            }

            JsonArray oldArr = root.getAsJsonArray("disabled_recipes");
            JsonArray newArr = new JsonArray();
            boolean removed = false;

            for (JsonElement el : oldArr) {
                if (el != null && el.isJsonPrimitive() && el.getAsString().trim().equals(rule)) {
                    removed = true;
                } else {
                    newArr.add(el);
                }
            }

            if (removed) {
                root.add("disabled_recipes", newArr);
                writeDisabledRecipesRoot(file, root);
            }
            return removed;
        } catch (Exception e) {
            LOGGER.error("RingoLib: Failed to remove recipe from {}", DISABLED_RECIPES_FILE, e);
            return false;
        }
    }

    private static JsonObject readDisabledRecipesRoot(Path file) throws Exception {
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            JsonObject root = GSON.fromJson(reader, JsonObject.class);
            if (root != null) {
                return root;
            }
        }
        JsonObject fallback = new JsonObject();
        fallback.addProperty("_comment", "Define recipes to disable by full ID or wildcard pattern (e.g. tfmg:crafting/materials/lead_*)");
        fallback.add("disabled_recipes", new JsonArray());
        return fallback;
    }

    private static void writeDisabledRecipesRoot(Path file, JsonObject root) throws Exception {
        Files.createDirectories(file.getParent());
        try (Writer writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
            GSON.toJson(root, writer);
        }
    }

    public static Path resolvePathWithLegacyFallback(String newFileName, String legacyFileName) {
        Path targetDir = getConfigDir();
        Path targetPath = targetDir.resolve(newFileName);

        if (Files.exists(targetPath)) {
            return targetPath;
        }

        Path legacyInSubdir = targetDir.resolve(legacyFileName);
        if (Files.exists(legacyInSubdir)) {
            try {
                Files.move(legacyInSubdir, targetPath, StandardCopyOption.REPLACE_EXISTING);
                LOGGER.info("RingoLib: Migrated config/{}/{} to config/{}/{}", SUBDIR, legacyFileName, SUBDIR, newFileName);
                return targetPath;
            } catch (Exception e) {
                LOGGER.warn("RingoLib: Could not rename config/{}/{} to {}, using existing file", SUBDIR, legacyFileName, newFileName, e);
                return legacyInSubdir;
            }
        }

        Path legacyInRoot = FMLPaths.CONFIGDIR.get().resolve(legacyFileName);
        if (Files.exists(legacyInRoot)) {
            try {
                Files.move(legacyInRoot, targetPath, StandardCopyOption.REPLACE_EXISTING);
                LOGGER.info("RingoLib: Moved legacy config/{} to modern config/{}/{}", legacyFileName, SUBDIR, newFileName);
                return targetPath;
            } catch (Exception e) {
                LOGGER.warn("RingoLib: Could not move config/{} to config/{}/{}, using legacy path", legacyFileName, SUBDIR, newFileName, e);
                return legacyInRoot;
            }
        }

        return targetPath;
    }

    private static void createDefaultDisabledRecipesConfig(Path file) {
        try {
            Files.createDirectories(file.getParent());
            JsonObject root = new JsonObject();
            root.addProperty("_comment", "Define recipes to disable by full ID or wildcard pattern (e.g. tfmg:crafting/materials/lead_*)");
            root.add("disabled_recipes", new JsonArray());
            try (Writer writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
                GSON.toJson(root, writer);
            }
        } catch (Exception e) {
            LOGGER.error("RingoLib: Failed to create default disabled recipes config", e);
        }
    }

    private static void createDefaultConfig(Path file) {
        try {
            Files.createDirectories(file.getParent());
            JsonObject root = new JsonObject();
            root.addProperty("_comment", "Define custom modpack migrations and registry redirects for removed, renamed or deduplicated items");
            root.add("items", new JsonArray());
            root.add("blocks", new JsonArray());
            root.add("fluids", new JsonArray());
            root.add("registry_redirects", new JsonArray());
            try (Writer writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
                GSON.toJson(root, writer);
            }
        } catch (Exception e) {
            LOGGER.error("RingoLib: Failed to create default config", e);
        }
    }
}
