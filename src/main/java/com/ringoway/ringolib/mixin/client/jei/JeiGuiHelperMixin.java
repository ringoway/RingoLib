package com.ringoway.ringolib.mixin.client.jei;

import com.ringoway.ringolib.core.content.ContentSuppressionRegistry;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Pseudo
@Mixin(targets = "mezz.jei.library.gui.helpers.GuiHelper", remap = false)
public abstract class JeiGuiHelperMixin {

    @ModifyVariable(
            method = "createDrawableIngredient(Lmezz/jei/api/ingredients/IIngredientType;Ljava/lang/Object;)Lmezz/jei/api/gui/drawable/IDrawable;",
            at = @At("HEAD"),
            argsOnly = true,
            ordinal = 0,
            remap = false,
            require = 0
    )
    private Object ringolib$fallbackEmptyDrawableIcon(Object ingredient) {
        if (ContentSuppressionRegistry.hasAnyUnregisteredItems()
                && ingredient instanceof ItemStack stack
                && stack.isEmpty()) {
            return new ItemStack(Items.BARRIER);
        }
        return ingredient;
    }
}
