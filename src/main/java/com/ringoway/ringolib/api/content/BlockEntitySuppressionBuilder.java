package com.ringoway.ringolib.api.content;

import com.mojang.logging.LogUtils;
import com.ringoway.ringolib.api.RegistryRedirectManager;
import com.ringoway.ringolib.core.RingoLibRegistry;
import com.ringoway.ringolib.core.content.ContentSuppressionRegistry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.util.Objects;
import java.util.function.BooleanSupplier;
import java.util.function.Predicate;

public final class BlockEntitySuppressionBuilder {
    private static final Logger LOGGER = LogUtils.getLogger();

    @Nullable
    private final ResourceLocation exactId;
    private final Predicate<ResourceLocation> idMatcher;
    @Nullable
    private final String requiredMod;
    private BooleanSupplier condition;

    @Nullable
    private Predicate<BlockState> stateMatcher;
    private boolean disableTicker = true;
    private boolean disableGameEvents = true;
    private boolean unregister = false;
    @Nullable
    private ResourceLocation unregisterTarget;

    BlockEntitySuppressionBuilder(
            @Nullable ResourceLocation exactId,
            Predicate<ResourceLocation> idMatcher,
            @Nullable Predicate<BlockState> stateMatcher,
            BooleanSupplier condition,
            @Nullable String requiredMod
    ) {
        this.exactId = exactId;
        this.idMatcher = Objects.requireNonNull(idMatcher, "idMatcher");
        this.stateMatcher = stateMatcher;
        this.condition = Objects.requireNonNull(condition, "condition");
        this.requiredMod = requiredMod;
    }

    public BlockEntitySuppressionBuilder when(BooleanSupplier extraCondition) {
        Objects.requireNonNull(extraCondition, "extraCondition");
        BooleanSupplier prev = this.condition;
        this.condition = () -> prev.getAsBoolean() && extraCondition.getAsBoolean();
        return this;
    }

    public BlockEntitySuppressionBuilder matchingState(Predicate<BlockState> predicate) {
        Objects.requireNonNull(predicate, "predicate");
        this.stateMatcher = this.stateMatcher == null ? predicate : this.stateMatcher.and(predicate);
        return this;
    }

    public BlockEntitySuppressionBuilder disableTicker(boolean disable) {
        this.disableTicker = disable;
        return this;
    }

    public BlockEntitySuppressionBuilder keepTicker() {
        this.disableTicker = false;
        return this;
    }

    public BlockEntitySuppressionBuilder disableGameEvents(boolean disable) {
        this.disableGameEvents = disable;
        return this;
    }

    /**
     * Cancels registration and aliases to {@code ringolib:disabled_block_entity}.
     */
    public BlockEntitySuppressionBuilder unregister() {
        this.unregister = true;
        return this;
    }

    public BlockEntitySuppressionBuilder unregisterTo(ResourceLocation targetBlockEntityId) {
        this.unregister = true;
        this.unregisterTarget = Objects.requireNonNull(targetBlockEntityId, "targetBlockEntityId");
        return this;
    }

    void apply() {
        ContentSuppressionRegistry.addBlockEntityRule(
                exactId,
                new ContentSuppressionRegistry.BlockEntityRule(
                        idMatcher,
                        stateMatcher,
                        condition,
                        disableTicker,
                        disableGameEvents
                )
        );

        if (unregister) {
            if (exactId == null) {
                LOGGER.warn("RingoLib: .unregister() on BlockEntity rules requires an exact BlockEntityType ID; applying ticker/event suppression only.");
                return;
            }
            ResourceLocation target = unregisterTarget != null
                    ? unregisterTarget
                    : RingoLibRegistry.DISABLED_BLOCK_ENTITY_ID;
            RegistryRedirectManager.registerRedirect(
                    Registries.BLOCK_ENTITY_TYPE,
                    exactId,
                    target,
                    requiredMod,
                    condition,
                    "Suppressed BlockEntityType via RingoContentAPI"
            );
        }
    }
}
