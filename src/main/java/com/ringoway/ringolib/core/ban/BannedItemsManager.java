package com.ringoway.ringolib.core.ban;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.mojang.logging.LogUtils;
import com.ringoway.ringolib.core.RingoLibConfig;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import org.slf4j.Logger;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Collections;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Manages the modpack banned items list (config/ringolib/banned_items.json).
 * Banned items cannot be picked up or crafted, are purged from player inventories,
 * and sync to clients so tooltips display a clear warning.
 */
public final class BannedItemsManager {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String FILE_NAME = "banned_items.json";

    private static final Set<ResourceLocation> BANNED_ITEMS = ConcurrentHashMap.newKeySet();
    private static final Set<ResourceLocation> CLIENT_BANNED_ITEMS = ConcurrentHashMap.newKeySet();
    private static volatile Path configFile;

    private BannedItemsManager() {
    }

    public static void init() {
        Path targetDir = RingoLibConfig.getConfigDir();
        Path targetFile = targetDir.resolve(FILE_NAME);

        // Migrate legacy config/eternal_cogworks/banned_items.json if present
        if (!Files.exists(targetFile)) {
            Path legacyEcFile = FMLPaths.CONFIGDIR.get().resolve("eternal_cogworks").resolve(FILE_NAME);
            if (Files.exists(legacyEcFile)) {
                try {
                    Files.createDirectories(targetDir);
                    Files.copy(legacyEcFile, targetFile, StandardCopyOption.REPLACE_EXISTING);
                    LOGGER.info("RingoLib: Imported legacy banned_items.json from config/eternal_cogworks/ into config/ringolib/");
                } catch (Exception e) {
                    LOGGER.warn("RingoLib: Failed to copy legacy banned_items.json from config/eternal_cogworks/", e);
                }
            }
        }

        configFile = targetFile;
        load();
    }

    private static void load() {
        Path file = configFile;
        BANNED_ITEMS.clear();
        if (file == null || !Files.exists(file)) {
            return;
        }

        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            Set<String> items = GSON.fromJson(reader, new TypeToken<Set<String>>() {}.getType());
            if (items != null) {
                for (String raw : items) {
                    if (raw == null || raw.isBlank()) continue;
                    ResourceLocation parsed = ResourceLocation.tryParse(raw.trim());
                    if (parsed != null) {
                        BANNED_ITEMS.add(parsed);
                    } else {
                        LOGGER.warn("RingoLib: Skipping invalid item ID in {}: '{}'", FILE_NAME, raw);
                    }
                }
            }
            LOGGER.info("RingoLib: Loaded {} banned item(s)", BANNED_ITEMS.size());
        } catch (Exception e) {
            LOGGER.error("RingoLib: Failed to load {}", FILE_NAME, e);
        }
    }

    private static void save() {
        Path file = configFile;
        if (file == null) {
            file = RingoLibConfig.getConfigDir().resolve(FILE_NAME);
            configFile = file;
        }

        try {
            Files.createDirectories(file.getParent());
            Set<String> items = BANNED_ITEMS.stream()
                    .map(ResourceLocation::toString)
                    .collect(Collectors.toCollection(TreeSet::new));
            try (Writer writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
                GSON.toJson(items, writer);
            }
        } catch (IOException e) {
            LOGGER.error("RingoLib: Failed to save {}", FILE_NAME, e);
        }
    }

    public static boolean banItem(ResourceLocation itemId) {
        if (itemId != null && BANNED_ITEMS.add(itemId)) {
            save();
            syncToAllPlayers();
            return true;
        }
        return false;
    }

    public static boolean unbanItem(ResourceLocation itemId) {
        if (itemId != null && BANNED_ITEMS.remove(itemId)) {
            save();
            syncToAllPlayers();
            return true;
        }
        return false;
    }

    public static boolean isBanned(Item item) {
        if (BANNED_ITEMS.isEmpty()) return false;
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(item);
        return id != null && BANNED_ITEMS.contains(id);
    }

    public static boolean isBanned(ResourceLocation itemId) {
        return itemId != null && BANNED_ITEMS.contains(itemId);
    }

    public static boolean isBannedClient(Item item) {
        if (CLIENT_BANNED_ITEMS.isEmpty()) return false;
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(item);
        return id != null && CLIENT_BANNED_ITEMS.contains(id);
    }

    public static Set<ResourceLocation> getBannedItems() {
        return Collections.unmodifiableSet(BANNED_ITEMS);
    }

    public static Set<ResourceLocation> getClientBannedItems() {
        return Collections.unmodifiableSet(CLIENT_BANNED_ITEMS);
    }

    public static void updateClientList(Set<ResourceLocation> items) {
        CLIENT_BANNED_ITEMS.clear();
        if (items != null) {
            CLIENT_BANNED_ITEMS.addAll(items);
        }
    }

    public static void clearClientList() {
        CLIENT_BANNED_ITEMS.clear();
    }

    public static void syncToPlayer(ServerPlayer player) {
        PacketDistributor.sendToPlayer(player, new SyncBannedItemsPayload(Set.copyOf(BANNED_ITEMS)));
    }

    public static void syncToAllPlayers() {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) return;
        PacketDistributor.sendToAllPlayers(new SyncBannedItemsPayload(Set.copyOf(BANNED_ITEMS)));
    }

    public static void clearAll() {
        BANNED_ITEMS.clear();
        save();
        syncToAllPlayers();
    }
}
