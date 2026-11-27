package com.ringoway.ringolib.client.viewer;

import com.ringoway.ringolib.core.content.ContentSuppressionRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RecipeViewerReflectTest {

    @AfterEach
    void tearDown() {
        ContentSuppressionRegistry.clear();
    }

    @Test
    void passesThroughUnchangedWhenNoRulesOrUnregisteredItemsExist() {
        List<String> input = List.of("a", "b");
        assertSame(input, RecipeViewerReflect.filterSuppressed(input));
        assertFalse(RecipeViewerReflect.areAllSuppressed(input));
        assertFalse(RecipeViewerReflect.areAllSuppressed(Collections.emptyList()));
    }

    @Test
    void filtersNullEmptyMarkersOrderIndependentlyWhenItemsUnregistered() {
        ContentSuppressionRegistry.markItemUnregistered();

        List<Object> allNull = Arrays.asList(null, null);
        Collection<?> sentinel = RecipeViewerReflect.filterSuppressed(allNull);
        assertTrue(sentinel.isEmpty());
        assertTrue(RecipeViewerReflect.areAllSuppressed(sentinel));

        assertTrue(RecipeViewerReflect.areAllSuppressed(allNull));

        List<Object> mixed = Arrays.asList(null, "valid_entry");
        assertFalse(RecipeViewerReflect.areAllSuppressed(mixed));
        assertEquals(List.of("valid_entry"), RecipeViewerReflect.filterSuppressed(mixed));

        List<Object> empty = Collections.emptyList();
        assertSame(empty, RecipeViewerReflect.filterSuppressed(empty));
        assertFalse(RecipeViewerReflect.areAllSuppressed(empty));
    }

    @Test
    void tracksViewerHookDiagnostics() {
        RecipeViewerReflect.markJeiHooked();
        RecipeViewerReflect.markEmiHooked();
        assertTrue(RecipeViewerReflect.isJeiHooked());
        assertTrue(RecipeViewerReflect.isEmiHooked());
    }
}
