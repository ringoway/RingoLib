package com.ringoway.ringolib.api;

import net.minecraft.network.chat.Component;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BooleanSupplier;

public class RingoWarningAPI {
    public record WarningCondition(
            String id,
            BooleanSupplier shouldShow,
            Component title,
            Component message
    ) {}

    private static final List<WarningCondition> TITLE_WARNINGS = new CopyOnWriteArrayList<>();
    private static final List<WarningCondition> WORLD_WARNINGS = new CopyOnWriteArrayList<>();

    public static void registerTitleWarning(String id, BooleanSupplier shouldShow, Component title, Component message) {
        TITLE_WARNINGS.add(new WarningCondition(id, shouldShow, title, message));
    }

    public static void registerWorldWarning(String id, BooleanSupplier shouldShow, Component title, Component message) {
        WORLD_WARNINGS.add(new WarningCondition(id, shouldShow, title, message));
    }

    public static List<WarningCondition> getTitleWarnings() {
        return Collections.unmodifiableList(TITLE_WARNINGS);
    }

    public static List<WarningCondition> getWorldWarnings() {
        return Collections.unmodifiableList(WORLD_WARNINGS);
    }
}
