package com.ringoway.ringolib.api;

import com.mojang.logging.LogUtils;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Public API for save migrations and registry redirects.
 */
public class RingoMigrationAPI {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final ResourceKey<Registry<Object>> FLUID_TYPE_REGISTRY_KEY =
            ResourceKey.createRegistryKey(ResourceLocation.fromNamespaceAndPath("neoforge", "fluid_type"));

    private static final Map<String, MigrationEntry> ITEM_MIGRATIONS = new ConcurrentHashMap<>();
    private static final Map<String, MigrationEntry> BLOCK_MIGRATIONS = new ConcurrentHashMap<>();
    private static final Map<String, MigrationEntry> FLUID_MIGRATIONS = new ConcurrentHashMap<>();

    public static void registerItemMigration(String oldId, String targetId, @Nullable String requiredMod, @Nullable String description) {
        ITEM_MIGRATIONS.put(oldId, new MigrationEntry(oldId, targetId, requiredMod, description));
        LOGGER.debug("RingoLib: Registered item migration {} -> {} (req: {})", oldId, targetId, requiredMod);
    }

    public static void registerItemMigration(String oldId, String targetId, @Nullable String requiredMod) {
        registerItemMigration(oldId, targetId, requiredMod, null);
    }

    public static void registerBlockMigration(String oldId, String targetId, @Nullable String requiredMod, @Nullable String description) {
        BLOCK_MIGRATIONS.put(oldId, new MigrationEntry(oldId, targetId, requiredMod, description));
        LOGGER.debug("RingoLib: Registered block migration {} -> {} (req: {})", oldId, targetId, requiredMod);
    }

    public static void registerBlockMigration(String oldId, String targetId, @Nullable String requiredMod) {
        registerBlockMigration(oldId, targetId, requiredMod, null);
    }

    public static void registerBlockAndItem(String oldId, String targetId, @Nullable String requiredMod) {
        registerItemMigration(oldId, targetId, requiredMod);
        registerBlockMigration(oldId, targetId, requiredMod);
    }

    public static void registerItemRedirect(String oldId, String targetId, @Nullable String requiredMod, @Nullable String description) {
        ResourceLocation from = ResourceLocation.tryParse(oldId);
        ResourceLocation to = ResourceLocation.tryParse(targetId);
        if (from == null || to == null) {
            LOGGER.warn("RingoLib: Invalid item redirect ResourceLocation: '{}' -> '{}'", oldId, targetId);
            return;
        }
        registerItemMigration(oldId, targetId, requiredMod, description);
        RegistryRedirectManager.registerRedirect(Registries.ITEM, from, to, requiredMod, description);
    }

    public static void registerItemRedirect(String oldId, String targetId, @Nullable String requiredMod) {
        registerItemRedirect(oldId, targetId, requiredMod, null);
    }

    public static void registerBlockRedirect(String oldId, String targetId, @Nullable String requiredMod, @Nullable String description) {
        ResourceLocation from = ResourceLocation.tryParse(oldId);
        ResourceLocation to = ResourceLocation.tryParse(targetId);
        if (from == null || to == null) {
            LOGGER.warn("RingoLib: Invalid block redirect ResourceLocation: '{}' -> '{}'", oldId, targetId);
            return;
        }
        registerBlockMigration(oldId, targetId, requiredMod, description);
        RegistryRedirectManager.registerRedirect(Registries.BLOCK, from, to, requiredMod, description);
    }

    public static void registerBlockRedirect(String oldId, String targetId, @Nullable String requiredMod) {
        registerBlockRedirect(oldId, targetId, requiredMod, null);
    }

    public static void registerBlockAndItemRedirect(String oldId, String targetId, @Nullable String requiredMod) {
        registerItemRedirect(oldId, targetId, requiredMod);
        registerBlockRedirect(oldId, targetId, requiredMod);
    }

    public static void registerFluidMigration(String oldId, String targetId, @Nullable String requiredMod, @Nullable String description) {
        FLUID_MIGRATIONS.put(oldId, new MigrationEntry(oldId, targetId, requiredMod, description));
        LOGGER.debug("RingoLib: Registered fluid migration {} -> {} (req: {})", oldId, targetId, requiredMod);
    }

    public static void registerFluidMigration(String oldId, String targetId, @Nullable String requiredMod) {
        registerFluidMigration(oldId, targetId, requiredMod, null);
    }

    public static void registerFluidRedirect(String oldId, String targetId, @Nullable String requiredMod, @Nullable String description) {
        ResourceLocation from = ResourceLocation.tryParse(oldId);
        ResourceLocation to = ResourceLocation.tryParse(targetId);
        if (from == null || to == null) {
            LOGGER.warn("RingoLib: Invalid fluid redirect ResourceLocation: '{}' -> '{}'", oldId, targetId);
            return;
        }

        registerFluidMigration(oldId, targetId, requiredMod, description);

        RegistryRedirectManager.registerRedirect(Registries.FLUID, from, to, requiredMod, description);

        ResourceLocation flowingFrom = ResourceLocation.fromNamespaceAndPath(from.getNamespace(), "flowing_" + from.getPath());
        ResourceLocation flowingTo = ResourceLocation.fromNamespaceAndPath(to.getNamespace(), "flowing_" + to.getPath());
        RegistryRedirectManager.registerRedirect(Registries.FLUID, flowingFrom, flowingTo, requiredMod, description);

        RegistryRedirectManager.registerRedirect(FLUID_TYPE_REGISTRY_KEY, from, to, requiredMod, description);
        RegistryRedirectManager.registerRedirect(Registries.BLOCK, from, to, requiredMod, description);
    }

    public static void registerFluidRedirect(String oldId, String targetId, @Nullable String requiredMod) {
        registerFluidRedirect(oldId, targetId, requiredMod, null);
    }

    @Nullable
    public static MigrationEntry getItemEntry(String oldId) {
        return ITEM_MIGRATIONS.get(oldId);
    }

    @Nullable
    public static MigrationEntry getBlockEntry(String oldId) {
        return BLOCK_MIGRATIONS.get(oldId);
    }

    @Nullable
    public static MigrationEntry getFluidEntry(String oldId) {
        return FLUID_MIGRATIONS.get(oldId);
    }

    public static boolean hasItemMigration(String oldId) {
        return ITEM_MIGRATIONS.containsKey(oldId);
    }

    public static boolean hasBlockMigration(String oldId) {
        return BLOCK_MIGRATIONS.containsKey(oldId);
    }

    public static boolean hasFluidMigration(String oldId) {
        return FLUID_MIGRATIONS.containsKey(oldId);
    }

    public static Map<String, MigrationEntry> getAllItemMigrations() {
        return Collections.unmodifiableMap(ITEM_MIGRATIONS);
    }

    public static Map<String, MigrationEntry> getAllBlockMigrations() {
        return Collections.unmodifiableMap(BLOCK_MIGRATIONS);
    }

    public static Map<String, MigrationEntry> getAllFluidMigrations() {
        return Collections.unmodifiableMap(FLUID_MIGRATIONS);
    }
}
