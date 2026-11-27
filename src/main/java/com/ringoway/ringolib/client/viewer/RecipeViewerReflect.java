package com.ringoway.ringolib.client.viewer;

import com.ringoway.ringolib.api.RecipeFilterManager;
import com.ringoway.ringolib.core.content.ContentSuppressionRegistry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Reflection bridge for optional JEI and EMI types without compile/runtime dependencies.
 */
public final class RecipeViewerReflect {
    private static final Map<String, Method> METHOD_CACHE = new ConcurrentHashMap<>();
    private static final List<Object> SUPPRESSED_EMPTY_LIST = Collections.unmodifiableList(new ArrayList<>(0));
    private static final ItemLike[] SUPPRESSED_EMPTY_ITEM_LIKES = new ItemLike[0];
    private static final AtomicBoolean JEI_HOOKED = new AtomicBoolean(false);
    private static final AtomicBoolean EMI_HOOKED = new AtomicBoolean(false);

    private RecipeViewerReflect() {
    }

    public static void markJeiHooked() {
        if (!JEI_HOOKED.get()) {
            JEI_HOOKED.set(true);
        }
    }

    public static void markEmiHooked() {
        if (!EMI_HOOKED.get()) {
            EMI_HOOKED.set(true);
        }
    }

    public static boolean isJeiHooked() {
        return JEI_HOOKED.get();
    }

    public static boolean isEmiHooked() {
        return EMI_HOOKED.get();
    }

    @Nullable
    public static ResourceLocation getJeiRecipeTypeUid(@Nullable Object recipeType) {
        Object result = invokeNoArg(recipeType, "getUid");
        return result instanceof ResourceLocation loc ? loc : null;
    }

    @Nullable
    public static Object getJeiTypedIngredientValue(@Nullable Object typedIngredient) {
        return invokeNoArg(typedIngredient, "getIngredient");
    }

    public static boolean isSuppressedRawIngredient(@Nullable Object raw) {
        if (raw == null || !ContentSuppressionRegistry.hasViewerItemOrFluidRules()) {
            return false;
        }
        if (raw instanceof ItemStack stack) {
            return !stack.isEmpty() && ContentSuppressionRegistry.isItemHiddenFromRecipeViewer(stack);
        }
        if (raw instanceof Item item) {
            return ContentSuppressionRegistry.isItemHiddenFromRecipeViewer(item);
        }
        if (raw instanceof FluidStack fluidStack) {
            if (fluidStack.isEmpty()) {
                return false;
            }
            ResourceLocation id = BuiltInRegistries.FLUID.getKey(fluidStack.getFluid());
            return ContentSuppressionRegistry.isFluidHiddenFromRecipeViewer(id);
        }
        if (raw instanceof Fluid fluid) {
            ResourceLocation id = BuiltInRegistries.FLUID.getKey(fluid);
            return ContentSuppressionRegistry.isFluidHiddenFromRecipeViewer(id);
        }
        return false;
    }

    public static boolean isFilteredOutRawIngredient(@Nullable Object raw) {
        if (raw == null) {
            return ContentSuppressionRegistry.hasAnyUnregisteredItems()
                    || ContentSuppressionRegistry.hasAnyUnregisteredFluids();
        }
        if (raw instanceof ItemStack stack) {
            if (stack.isEmpty()) {
                return ContentSuppressionRegistry.hasAnyUnregisteredItems();
            }
            return ContentSuppressionRegistry.isItemHiddenFromRecipeViewer(stack);
        }
        if (raw instanceof Item item) {
            if (isAirItem(item)) {
                return ContentSuppressionRegistry.hasAnyUnregisteredItems();
            }
            return ContentSuppressionRegistry.isItemHiddenFromRecipeViewer(item);
        }
        if (raw instanceof FluidStack fluidStack) {
            if (fluidStack.isEmpty()) {
                return ContentSuppressionRegistry.hasAnyUnregisteredFluids();
            }
            ResourceLocation id = BuiltInRegistries.FLUID.getKey(fluidStack.getFluid());
            return ContentSuppressionRegistry.isFluidHiddenFromRecipeViewer(id);
        }
        if (raw instanceof Fluid fluid) {
            if (isEmptyFluid(fluid)) {
                return ContentSuppressionRegistry.hasAnyUnregisteredFluids();
            }
            ResourceLocation id = BuiltInRegistries.FLUID.getKey(fluid);
            return ContentSuppressionRegistry.isFluidHiddenFromRecipeViewer(id);
        }
        return false;
    }

    public static boolean isEmptyOrSuppressedRawIngredient(@Nullable Object raw) {
        if (raw == null) {
            return true;
        }
        if (raw instanceof ItemStack stack && stack.isEmpty()) {
            return true;
        }
        if (raw instanceof Item item && isAirItem(item)) {
            return true;
        }
        if (raw instanceof FluidStack fluidStack && fluidStack.isEmpty()) {
            return true;
        }
        if (raw instanceof Fluid fluid && isEmptyFluid(fluid)) {
            return true;
        }
        return isSuppressedRawIngredient(raw);
    }

    /**
     * Returns true for the {@link #filterSuppressed(Collection)} sentinel or when every non-empty entry is suppressed.
     */
    public static boolean areAllSuppressed(@Nullable Collection<?> collection) {
        if (collection == SUPPRESSED_EMPTY_LIST) {
            return true;
        }
        if (collection == null || collection.isEmpty() || !hasAnyIngredientFilter()) {
            return false;
        }
        boolean sawSuppressed = false;
        for (Object element : collection) {
            if (isFilteredOutRawIngredient(element)) {
                sawSuppressed = true;
            } else if (!isEmptyIngredient(element)) {
                return false;
            }
        }
        return sawSuppressed;
    }

    public static Collection<?> filterSuppressed(Collection<?> collection) {
        if (collection == null || collection.isEmpty() || !hasAnyIngredientFilter()) {
            return collection;
        }
        List<Object> filtered = null;
        int idx = 0;
        for (Object element : collection) {
            if (isFilteredOutRawIngredient(element)) {
                if (filtered == null) {
                    filtered = new ArrayList<>(collection.size());
                    int i = 0;
                    for (Object prev : collection) {
                        if (i++ >= idx) {
                            break;
                        }
                        filtered.add(prev);
                    }
                }
            } else if (filtered != null) {
                filtered.add(element);
            }
            idx++;
        }
        if (filtered == null) {
            return collection;
        }
        return filtered.isEmpty() ? SUPPRESSED_EMPTY_LIST : filtered;
    }

    @SuppressWarnings("unchecked")
    public static <T> List<T> filterSuppressedList(List<T> list) {
        Collection<?> filtered = filterSuppressed(list);
        return filtered instanceof List<?> ? (List<T>) filtered : new ArrayList<>((Collection<T>) filtered);
    }

    @Nullable
    public static ItemLike[] filterSuppressedItemLikes(@Nullable ItemLike[] ingredients) {
        if (ingredients == null || ingredients.length == 0 || !hasAnyIngredientFilter()) {
            return ingredients;
        }
        List<ItemLike> filtered = null;
        for (int i = 0; i < ingredients.length; i++) {
            ItemLike element = ingredients[i];
            if (isFilteredOutItemLike(element)) {
                if (filtered == null) {
                    filtered = new ArrayList<>(ingredients.length);
                    for (int j = 0; j < i; j++) {
                        filtered.add(ingredients[j]);
                    }
                }
            } else if (filtered != null) {
                filtered.add(element);
            }
        }
        if (filtered == null) {
            return ingredients;
        }
        return filtered.isEmpty() ? SUPPRESSED_EMPTY_ITEM_LIKES : filtered.toArray(new ItemLike[0]);
    }

    public static boolean areAllItemLikesSuppressed(@Nullable ItemLike[] ingredients) {
        if (ingredients == SUPPRESSED_EMPTY_ITEM_LIKES) {
            return true;
        }
        if (ingredients == null || ingredients.length == 0 || !hasAnyIngredientFilter()) {
            return false;
        }
        boolean sawSuppressed = false;
        for (ItemLike element : ingredients) {
            if (isFilteredOutItemLike(element)) {
                sawSuppressed = true;
            } else if (element != null && !isAirItem(element.asItem())) {
                return false;
            }
        }
        return sawSuppressed;
    }

    private static boolean hasAnyIngredientFilter() {
        return ContentSuppressionRegistry.hasViewerItemOrFluidRules()
                || ContentSuppressionRegistry.hasAnyUnregisteredItems()
                || ContentSuppressionRegistry.hasAnyUnregisteredFluids();
    }

    private static boolean isFilteredOutItemLike(@Nullable ItemLike element) {
        if (element == null) {
            return ContentSuppressionRegistry.hasAnyUnregisteredItems();
        }
        return isFilteredOutRawIngredient(element.asItem());
    }

    private static boolean isEmptyIngredient(@Nullable Object raw) {
        return raw == null
                || (raw instanceof ItemStack stack && stack.isEmpty())
                || (raw instanceof FluidStack fluidStack && fluidStack.isEmpty());
    }

    private static boolean isAirItem(Item item) {
        return item == Items.AIR;
    }

    private static boolean isEmptyFluid(Fluid fluid) {
        return fluid == Fluids.EMPTY;
    }

    @Nullable
    public static ResourceLocation getEmiCategoryId(@Nullable Object category) {
        Object result = invokeNoArg(category, "getId");
        return result instanceof ResourceLocation loc ? loc : null;
    }

    @Nullable
    public static ResourceLocation getEmiRecipeCategoryId(@Nullable Object recipe) {
        Object category = invokeNoArg(recipe, "getCategory");
        return getEmiCategoryId(category);
    }

    @Nullable
    public static ResourceLocation getEmiRecipeId(@Nullable Object recipe) {
        Object result = invokeNoArg(recipe, "getId");
        return result instanceof ResourceLocation loc ? loc : null;
    }

    public static boolean isEmiRecipeSuppressed(@Nullable Object emiRecipe) {
        if (emiRecipe == null) {
            return false;
        }
        if (ContentSuppressionRegistry.hasCategoryRules()) {
            ResourceLocation catId = getEmiRecipeCategoryId(emiRecipe);
            if (catId != null && ContentSuppressionRegistry.isRecipeCategoryDisabled(catId)) {
                return true;
            }
        }
        ResourceLocation recipeId = getEmiRecipeId(emiRecipe);
        return recipeId != null && RecipeFilterManager.isRecipeDisabled(recipeId);
    }

    public static boolean isEmiStackSuppressed(@Nullable Object emiStack) {
        if (emiStack == null || !hasAnyIngredientFilter()) {
            return false;
        }
        if ((ContentSuppressionRegistry.hasAnyUnregisteredItems() || ContentSuppressionRegistry.hasAnyUnregisteredFluids())
                && Boolean.TRUE.equals(invokeNoArg(emiStack, "isEmpty"))) {
            return true;
        }
        Object key = invokeNoArg(emiStack, "getKey");
        if (key instanceof Item item) {
            Object itemStackObj = invokeNoArg(emiStack, "getItemStack");
            if (itemStackObj instanceof ItemStack stack && !stack.isEmpty()) {
                return ContentSuppressionRegistry.isItemHiddenFromRecipeViewer(stack);
            }
            return ContentSuppressionRegistry.isItemHiddenFromRecipeViewer(item);
        }
        if (key instanceof Fluid fluid) {
            ResourceLocation fluidId = BuiltInRegistries.FLUID.getKey(fluid);
            return ContentSuppressionRegistry.isFluidHiddenFromRecipeViewer(fluidId);
        }
        Object idObj = invokeNoArg(emiStack, "getId");
        if (idObj instanceof ResourceLocation id) {
            return ContentSuppressionRegistry.isFluidHiddenFromRecipeViewer(id);
        }
        return false;
    }

    public static boolean isEmiIngredientSuppressed(@Nullable Object emiIngredient) {
        if (emiIngredient == null || !hasAnyIngredientFilter()) {
            return false;
        }
        Object stacksObj = invokeNoArg(emiIngredient, "getEmiStacks");
        if (!(stacksObj instanceof List<?> stacks) || stacks.isEmpty()) {
            return false;
        }
        for (Object emiStack : stacks) {
            if (!isEmiStackSuppressed(emiStack)) {
                return false;
            }
        }
        return true;
    }

    @Nullable
    private static Object invokeNoArg(@Nullable Object target, String methodName) {
        if (target == null) {
            return null;
        }
        try {
            Class<?> clazz = target.getClass();
            String cacheKey = clazz.getName() + "#" + methodName;
            Method method = METHOD_CACHE.get(cacheKey);
            if (method == null) {
                method = clazz.getMethod(methodName);
                method.setAccessible(true);
                METHOD_CACHE.putIfAbsent(cacheKey, method);
            }
            return method.invoke(target);
        } catch (Throwable ignored) {
            return null;
        }
    }
}
