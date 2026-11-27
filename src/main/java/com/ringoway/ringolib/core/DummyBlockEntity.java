package com.ringoway.ringolib.core;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import org.jetbrains.annotations.NotNull;

public class DummyBlockEntity extends BlockEntity {
    public static final String TAG_STORED_BLOCK_ID = "RingoLib_StoredBlockId";
    public static final String TAG_REQUIRED_MOD = "RingoLib_RequiredMod";
    public static final String TAG_SAVED_TE_DATA = "RingoLib_SavedTeData";

    private String storedBlockId = "";
    private String requiredMod = "";
    private CompoundTag savedTeData = new CompoundTag();
    private boolean restoreChecked;

    public DummyBlockEntity(BlockPos pos, BlockState blockState) {
        super(RingoLibRegistry.DUMMY_BLOCK_ENTITY.get(), pos, blockState);
    }

    public void setStoredBlock(String blockId, String requiredMod, CompoundTag originalTeNbt) {
        this.storedBlockId = blockId != null ? blockId : "";
        this.requiredMod = requiredMod != null ? requiredMod : "";
        this.savedTeData = originalTeNbt != null ? originalTeNbt.copy() : new CompoundTag();
        setChanged();
    }

    public String getStoredBlockId() { return storedBlockId; }
    public String getRequiredMod() { return requiredMod; }
    public CompoundTag getSavedTeData() { return savedTeData; }
    public boolean isRestoreChecked() { return restoreChecked; }
    public void markRestoreChecked() { this.restoreChecked = true; }

    @Override
    protected void loadAdditional(@NotNull CompoundTag tag, @NotNull HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains(TAG_STORED_BLOCK_ID)) this.storedBlockId = tag.getString(TAG_STORED_BLOCK_ID);
        if (tag.contains(TAG_REQUIRED_MOD)) this.requiredMod = tag.getString(TAG_REQUIRED_MOD);
        if (tag.contains(TAG_SAVED_TE_DATA)) this.savedTeData = tag.getCompound(TAG_SAVED_TE_DATA);
    }

    @Override
    protected void saveAdditional(@NotNull CompoundTag tag, @NotNull HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (!this.storedBlockId.isEmpty()) tag.putString(TAG_STORED_BLOCK_ID, this.storedBlockId);
        if (!this.requiredMod.isEmpty()) tag.putString(TAG_REQUIRED_MOD, this.requiredMod);
        if (!this.savedTeData.isEmpty()) tag.put(TAG_SAVED_TE_DATA, this.savedTeData);
    }
}
