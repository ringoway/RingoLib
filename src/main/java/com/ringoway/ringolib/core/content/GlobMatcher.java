package com.ringoway.ringolib.core.content;

import net.minecraft.resources.ResourceLocation;

import java.util.function.Predicate;
import java.util.regex.Pattern;

/**
 * Wildcard glob compiler ({@code *} and {@code ?}) with escaped literal segments.
 */
public final class GlobMatcher {

    private GlobMatcher() {
    }

    public static boolean isGlob(String pattern) {
        return pattern != null && (pattern.indexOf('*') >= 0 || pattern.indexOf('?') >= 0);
    }

    public static Pattern compile(String glob) {
        String normalized = glob.replaceAll("\\*{2,}", "*");
        StringBuilder regex = new StringBuilder("^");
        int segmentStart = 0;
        for (int i = 0; i < normalized.length(); i++) {
            char c = normalized.charAt(i);
            if (c == '*' || c == '?') {
                if (i > segmentStart) {
                    regex.append(Pattern.quote(normalized.substring(segmentStart, i)));
                }
                regex.append(c == '*' ? ".*" : ".");
                segmentStart = i + 1;
            }
        }
        if (segmentStart < normalized.length()) {
            regex.append(Pattern.quote(normalized.substring(segmentStart)));
        }
        regex.append('$');
        return Pattern.compile(regex.toString());
    }

    public static Predicate<ResourceLocation> toPredicate(String idOrGlob) {
        if (idOrGlob == null || idOrGlob.isBlank()) {
            return id -> false;
        }
        String trimmed = idOrGlob.trim();
        if (isGlob(trimmed)) {
            Pattern pattern = compile(trimmed);
            return id -> id != null && pattern.matcher(id.toString()).matches();
        }
        ResourceLocation exact = ResourceLocation.tryParse(trimmed);
        if (exact == null) {
            return id -> false;
        }
        return exact::equals;
    }
}
