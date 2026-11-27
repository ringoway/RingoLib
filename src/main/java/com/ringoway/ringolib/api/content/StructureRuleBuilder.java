package com.ringoway.ringolib.api.content;

import com.mojang.logging.LogUtils;
import com.ringoway.ringolib.core.content.ContentSuppressionRegistry;
import com.ringoway.ringolib.core.content.GlobMatcher;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BooleanSupplier;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

public final class StructureRuleBuilder {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Set<ResourceLocation> WARNED_MISSING_TARGETS = ConcurrentHashMap.newKeySet();

    private BooleanSupplier condition;
    @Nullable
    private Predicate<ResourceLocation> templateMatcher;
    private boolean allTemplates = false;

    @Nullable
    private Predicate<ResourceLocation> blockIdMatcher;
    @Nullable
    private Predicate<BlockState> stateFilter;

    private float chance = 1.0f;
    private boolean copyProperties = false;
    private boolean preserveNbt = false;

    @Nullable
    private Function<StructureBlockContext, BlockState> stateReplacer;
    @Nullable
    private Function<StructureBlockContext, StructureTemplate.StructureBlockInfo> fullTransformer;
    private final List<StructureProcessor> processors = new ArrayList<>();

    StructureRuleBuilder(BooleanSupplier condition) {
        this.condition = Objects.requireNonNull(condition, "condition");
    }

    public StructureRuleBuilder when(BooleanSupplier extraCondition) {
        Objects.requireNonNull(extraCondition, "extraCondition");
        BooleanSupplier prev = this.condition;
        this.condition = () -> prev.getAsBoolean() && extraCondition.getAsBoolean();
        return this;
    }

    public StructureRuleBuilder inTemplate(String templateIdOrGlob) {
        Objects.requireNonNull(templateIdOrGlob, "templateIdOrGlob");
        return inTemplates(GlobMatcher.toPredicate(templateIdOrGlob));
    }

    public StructureRuleBuilder inTemplate(ResourceLocation templateId) {
        Objects.requireNonNull(templateId, "templateId");
        return inTemplates(templateId::equals);
    }

    public StructureRuleBuilder inTemplates(String... templateIdsOrGlobs) {
        if (templateIdsOrGlobs != null) {
            for (String pattern : templateIdsOrGlobs) {
                inTemplate(pattern);
            }
        }
        return this;
    }

    public StructureRuleBuilder inTemplates(Predicate<ResourceLocation> matcher) {
        Objects.requireNonNull(matcher, "matcher");
        this.allTemplates = false;
        this.templateMatcher = this.templateMatcher == null ? matcher : this.templateMatcher.or(matcher);
        return this;
    }

    public StructureRuleBuilder inAllTemplates() {
        this.allTemplates = true;
        this.templateMatcher = null;
        return this;
    }

    public StructureRuleBuilder block(String blockIdOrGlob) {
        Objects.requireNonNull(blockIdOrGlob, "blockIdOrGlob");
        return blocks(GlobMatcher.toPredicate(blockIdOrGlob));
    }

    public StructureRuleBuilder block(ResourceLocation blockId) {
        Objects.requireNonNull(blockId, "blockId");
        return blocks(blockId::equals);
    }

    public StructureRuleBuilder blocks(String... blockIdsOrGlobs) {
        if (blockIdsOrGlobs != null) {
            for (String pattern : blockIdsOrGlobs) {
                block(pattern);
            }
        }
        return this;
    }

    public StructureRuleBuilder blocks(Predicate<ResourceLocation> idMatcher) {
        Objects.requireNonNull(idMatcher, "idMatcher");
        this.blockIdMatcher = this.blockIdMatcher == null ? idMatcher : this.blockIdMatcher.or(idMatcher);
        return this;
    }

    public StructureRuleBuilder matchingState(Predicate<BlockState> predicate) {
        Objects.requireNonNull(predicate, "predicate");
        this.stateFilter = this.stateFilter == null ? predicate : this.stateFilter.and(predicate);
        return this;
    }

    public StructureRuleBuilder withTag(TagKey<Block> tag) {
        Objects.requireNonNull(tag, "tag");
        return matchingState(state -> state.is(tag));
    }

    public StructureRuleBuilder chance(float probability) {
        if (probability < 0.0f || probability > 1.0f || Float.isNaN(probability)) {
            throw new IllegalArgumentException("Structure rule probability must be in [0.0, 1.0], got: " + probability);
        }
        this.chance = probability;
        return this;
    }

    public StructureRuleBuilder copyProperties() {
        return copyProperties(true);
    }

    public StructureRuleBuilder copyProperties(boolean copy) {
        this.copyProperties = copy;
        return this;
    }

    public StructureRuleBuilder preserveNbt() {
        return preserveNbt(true);
    }

    public StructureRuleBuilder preserveNbt(boolean preserve) {
        this.preserveNbt = preserve;
        return this;
    }

    public StructureRuleBuilder replaceWith(String targetBlockId) {
        ResourceLocation parsed = ResourceLocation.tryParse(Objects.requireNonNull(targetBlockId, "targetBlockId").trim());
        if (parsed == null) {
            throw new IllegalArgumentException("Invalid target block ResourceLocation: " + targetBlockId);
        }
        return replaceWith(parsed);
    }

    public StructureRuleBuilder replaceWith(ResourceLocation targetBlockId) {
        Objects.requireNonNull(targetBlockId, "targetBlockId");
        this.fullTransformer = null;
        this.stateReplacer = ctx -> {
            if (!BuiltInRegistries.BLOCK.containsKey(targetBlockId)) {
                if (WARNED_MISSING_TARGETS.add(targetBlockId)) {
                    LOGGER.warn("RingoLib: Structure replacement target block '{}' is not registered; keeping original block.", targetBlockId);
                }
                return ctx.originalState();
            }
            return BuiltInRegistries.BLOCK.get(targetBlockId).defaultBlockState();
        };
        return this;
    }

    public StructureRuleBuilder replaceWith(Supplier<BlockState> stateSupplier) {
        Objects.requireNonNull(stateSupplier, "stateSupplier");
        this.fullTransformer = null;
        this.stateReplacer = ctx -> stateSupplier.get();
        return this;
    }

    public StructureRuleBuilder replaceWith(Function<StructureBlockContext, BlockState> stateReplacer) {
        Objects.requireNonNull(stateReplacer, "stateReplacer");
        this.fullTransformer = null;
        this.stateReplacer = stateReplacer;
        return this;
    }

    public StructureRuleBuilder transform(
            Function<StructureBlockContext, StructureTemplate.StructureBlockInfo> infoTransformer
    ) {
        Objects.requireNonNull(infoTransformer, "infoTransformer");
        this.stateReplacer = null;
        this.fullTransformer = infoTransformer;
        return this;
    }

    public StructureRuleBuilder remove() {
        this.stateReplacer = null;
        this.fullTransformer = ctx -> new StructureTemplate.StructureBlockInfo(
                ctx.worldPos(),
                Blocks.AIR.defaultBlockState(),
                null
        );
        return this;
    }

    public StructureRuleBuilder omit() {
        this.stateReplacer = null;
        this.fullTransformer = ctx -> null;
        return this;
    }

    public StructureRuleBuilder addProcessor(StructureProcessor processor) {
        Objects.requireNonNull(processor, "processor");
        this.processors.add(processor);
        return this;
    }

    void apply() {
        Predicate<ResourceLocation> finalTemplateMatcher = allTemplates ? null : templateMatcher;

        for (StructureProcessor processor : processors) {
            ContentSuppressionRegistry.addStructureProcessorRule(new ContentSuppressionRegistry.StructureProcessorRule(
                    finalTemplateMatcher,
                    processor,
                    condition
            ));
        }

        if (stateReplacer == null && fullTransformer == null) {
            return;
        }

        Predicate<BlockState> finalStateMatcher = buildStateMatcher();
        Function<StructureBlockContext, StructureTemplate.StructureBlockInfo> transformer = buildTransformer();

        ContentSuppressionRegistry.addStructureBlockRule(new ContentSuppressionRegistry.StructureBlockRule(
                finalTemplateMatcher,
                finalStateMatcher,
                transformer,
                condition
        ));
    }

    private Predicate<BlockState> buildStateMatcher() {
        final Predicate<ResourceLocation> capturedId = this.blockIdMatcher;
        final Predicate<BlockState> capturedState = this.stateFilter;
        if (capturedId != null && capturedState != null) {
            return state -> capturedId.test(BuiltInRegistries.BLOCK.getKey(state.getBlock()))
                    && capturedState.test(state);
        }
        if (capturedId != null) {
            return state -> capturedId.test(BuiltInRegistries.BLOCK.getKey(state.getBlock()));
        }
        if (capturedState != null) {
            return capturedState;
        }
        return state -> true;
    }

    private Function<StructureBlockContext, StructureTemplate.StructureBlockInfo> buildTransformer() {
        Function<StructureBlockContext, StructureTemplate.StructureBlockInfo> base;
        if (this.fullTransformer != null) {
            base = this.fullTransformer;
        } else {
            final Function<StructureBlockContext, BlockState> replacer = this.stateReplacer;
            final boolean copyProps = this.copyProperties;
            final boolean keepNbt = this.preserveNbt;
            base = ctx -> {
                BlockState nextState = replacer != null ? replacer.apply(ctx) : ctx.originalState();
                if (nextState == null) {
                    return null;
                }
                if (copyProps && nextState != ctx.originalState()) {
                    nextState = ctx.copyMatchingProperties(nextState);
                }
                CompoundTag nextNbt = keepNbt && ctx.originalNbt() != null ? ctx.originalNbt().copy() : null;
                return new StructureTemplate.StructureBlockInfo(ctx.worldPos(), nextState, nextNbt);
            };
        }

        if (this.chance < 1.0f) {
            final float prob = this.chance;
            final Function<StructureBlockContext, StructureTemplate.StructureBlockInfo> delegate = base;
            return ctx -> {
                if (prob <= 0.0f || ctx.random().nextFloat() >= prob) {
                    return new StructureTemplate.StructureBlockInfo(
                            ctx.worldPos(),
                            ctx.originalState(),
                            ctx.originalNbt() != null ? ctx.originalNbt().copy() : null
                    );
                }
                return delegate.apply(ctx);
            };
        }
        return base;
    }
}
