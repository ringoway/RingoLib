package com.ringoway.ringolib.api;

import com.mojang.logging.LogUtils;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.RegisterEvent;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BooleanSupplier;

/**
 * Registry-level redirection, registration cancellation, and alias management.
 */
public class RegistryRedirectManager {
    private static final Logger LOGGER = LogUtils.getLogger();

    public record RedirectEntry(
            ResourceKey<? extends Registry<?>> registryKey,
            ResourceLocation fromId,
            ResourceLocation toId,
            @Nullable String requiredMod,
            @Nullable BooleanSupplier condition,
            @Nullable String description
    ) {
        public RedirectEntry(
                ResourceKey<? extends Registry<?>> registryKey,
                ResourceLocation fromId,
                ResourceLocation toId,
                @Nullable String requiredMod,
                @Nullable String description
        ) {
            this(registryKey, fromId, toId, requiredMod, null, description);
        }

        public boolean isTargetAvailable() {
            boolean modOk = requiredMod == null || requiredMod.isBlank() || ModList.get().isLoaded(requiredMod);
            if (!modOk) {
                return false;
            }
            return condition == null || condition.getAsBoolean();
        }
    }

    private static final Map<ResourceKey<? extends Registry<?>>, Map<ResourceLocation, RedirectEntry>> REDIRECTS = new ConcurrentHashMap<>();

    public static void registerRedirect(
            ResourceKey<? extends Registry<?>> registryKey,
            ResourceLocation fromId,
            ResourceLocation toId,
            @Nullable String requiredMod,
            @Nullable BooleanSupplier condition,
            @Nullable String description
    ) {
        REDIRECTS.computeIfAbsent(registryKey, k -> new ConcurrentHashMap<>())
                .put(fromId, new RedirectEntry(registryKey, fromId, toId, requiredMod, condition, description));
        LOGGER.debug("RingoLib: Registered registry redirect [{}] {} -> {} (req: {})",
                registryKey.location(), fromId, toId, requiredMod != null ? requiredMod : "none");
    }

    public static void registerRedirect(
            ResourceKey<? extends Registry<?>> registryKey,
            ResourceLocation fromId,
            ResourceLocation toId,
            @Nullable String requiredMod,
            @Nullable String description
    ) {
        registerRedirect(registryKey, fromId, toId, requiredMod, null, description);
    }

    public static void registerRedirect(
            ResourceKey<? extends Registry<?>> registryKey,
            ResourceLocation fromId,
            ResourceLocation toId,
            @Nullable String requiredMod
    ) {
        registerRedirect(registryKey, fromId, toId, requiredMod, null);
    }

    public static boolean shouldCancel(ResourceKey<? extends Registry<?>> registryKey, ResourceLocation id) {
        Map<ResourceLocation, RedirectEntry> map = REDIRECTS.get(registryKey);
        if (map == null) return false;
        RedirectEntry entry = map.get(id);
        if (entry == null) return false;
        return entry.isTargetAvailable();
    }

    public static boolean isItemRedirected(ResourceLocation id) {
        return shouldCancel(Registries.ITEM, id);
    }

    @Nullable
    public static ResourceLocation getRedirectTarget(ResourceKey<? extends Registry<?>> registryKey, ResourceLocation id) {
        Map<ResourceLocation, RedirectEntry> map = REDIRECTS.get(registryKey);
        if (map == null) return null;
        RedirectEntry entry = map.get(id);
        if (entry == null || !entry.isTargetAvailable()) return null;
        return entry.toId();
    }

    /**
     * Registers aliases at {@code EventPriority.HIGHEST} before mods register entries.
     */
    public static void applyAliases(RegisterEvent event) {
        ResourceKey<? extends Registry<?>> registryKey = event.getRegistryKey();
        Map<ResourceLocation, RedirectEntry> map = REDIRECTS.get(registryKey);
        if (map == null || map.isEmpty()) return;

        @SuppressWarnings({"rawtypes", "unchecked"})
        Registry<?> registry = event.getRegistry((ResourceKey) registryKey);
        if (registry == null) return;

        for (RedirectEntry entry : map.values()) {
            if (entry.isTargetAvailable()) {
                registry.addAlias(entry.fromId(), entry.toId());
                LOGGER.info("RingoLib: Applied alias in [{}] {} -> {}",
                        registryKey.location(), entry.fromId(), entry.toId());
            }
        }
    }

    /**
     * Strips redirected items from source creative tabs at {@code EventPriority.LOWEST}.
     */
    public static void sanitizeCreativeTab(BuildCreativeModeTabContentsEvent event) {
        try {
            if (event.getTabKey() == null) return;
            ResourceLocation tabId = event.getTabKey().location();
            Map<ResourceLocation, RedirectEntry> itemRedirects = REDIRECTS.get(Registries.ITEM);
            if (itemRedirects == null || itemRedirects.isEmpty()) return;

            Set<ItemStack> toRemove = new HashSet<>();
            for (ItemStack stack : event.getParentEntries()) {
                if (shouldRemoveFromTab(stack, tabId, itemRedirects)) {
                    toRemove.add(stack);
                }
            }
            for (ItemStack stack : event.getSearchEntries()) {
                if (shouldRemoveFromTab(stack, tabId, itemRedirects)) {
                    toRemove.add(stack);
                }
            }

            for (ItemStack stack : toRemove) {
                event.remove(stack, CreativeModeTab.TabVisibility.PARENT_AND_SEARCH_TABS);
            }
        } catch (Throwable t) {
            LOGGER.error("RingoLib: Error sanitizing creative tab {}", event.getTabKey(), t);
        }
    }

    private static boolean shouldRemoveFromTab(
            ItemStack stack,
            ResourceLocation tabId,
            Map<ResourceLocation, RedirectEntry> itemRedirects
    ) {
        ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(stack.getItem());
        for (RedirectEntry red : itemRedirects.values()) {
            if (red.isTargetAvailable()
                    && red.fromId().getNamespace().equals(tabId.getNamespace())
                    && !red.toId().getNamespace().equals(tabId.getNamespace())) {
                if (red.toId().equals(itemId)) {
                    return true;
                }
            }
        }
        return false;
    }

    public static Map<ResourceKey<? extends Registry<?>>, Map<ResourceLocation, RedirectEntry>> getAllRedirects() {
        return Collections.unmodifiableMap(REDIRECTS);
    }
}
