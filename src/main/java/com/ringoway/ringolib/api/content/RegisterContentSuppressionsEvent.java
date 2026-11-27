package com.ringoway.ringolib.api.content;

import net.neoforged.bus.api.Event;
import net.neoforged.fml.event.IModBusEvent;

import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/**
 * Mod-bus event posted right before {@code RegisterEvent} aliases are applied.
 */
public final class RegisterContentSuppressionsEvent extends Event implements IModBusEvent {

    public void configure(Consumer<ContentSuppressionScope> configurator) {
        RingoContentAPI.configure(configurator);
    }

    public void whenModLoaded(String modId, Consumer<ContentSuppressionScope> configurator) {
        RingoContentAPI.whenModLoaded(modId, configurator);
    }

    public void when(BooleanSupplier condition, Consumer<ContentSuppressionScope> configurator) {
        RingoContentAPI.when(condition, configurator);
    }

    public ContentSuppressionScope suppress() {
        return RingoContentAPI.suppress();
    }

    public ContentSuppressionScope suppressForMod(String modId) {
        return RingoContentAPI.suppressForMod(modId);
    }

    public ContentSuppressionScope suppressWhen(BooleanSupplier condition) {
        return RingoContentAPI.suppressWhen(condition);
    }
}
