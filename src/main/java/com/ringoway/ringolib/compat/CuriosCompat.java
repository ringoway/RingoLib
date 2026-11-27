package com.ringoway.ringolib.compat;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.fml.ModList;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Isolates optional Curios API calls when Curios is absent.
 */
public final class CuriosCompat {
    private static final String CURIOS_MOD_ID = "curios";

    private CuriosCompat() {
    }

    public static boolean isLoaded() {
        return ModList.get().isLoaded(CURIOS_MOD_ID);
    }

    public static int countEquippedCurios(ServerPlayer player) {
        if (!isLoaded()) {
            return 0;
        }
        return CuriosCompatImpl.countEquippedCurios(player);
    }

    public static Map<String, List<String>> getEquippedCuriosByMod(ServerPlayer player) {
        if (!isLoaded()) {
            return Collections.emptyMap();
        }
        return CuriosCompatImpl.getEquippedCuriosByMod(player);
    }

    public static int dropAllCurios(ServerPlayer player) {
        if (!isLoaded()) {
            return 0;
        }
        return CuriosCompatImpl.dropAllCurios(player);
    }
}
