package com.ringoway.ringolib.core.content;

import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GlobMatcherTest {

    @Test
    void exactResourceLocationMatchesLiterally() {
        Predicate<ResourceLocation> predicate = GlobMatcher.toPredicate("dwarfs_tyranny:deepbrew_bottle");
        assertTrue(predicate.test(ResourceLocation.parse("dwarfs_tyranny:deepbrew_bottle")));
        assertFalse(predicate.test(ResourceLocation.parse("dwarfs_tyranny:deepbrew")));
        assertFalse(predicate.test(null));
    }

    @Test
    void wildcardsMatchStarsAndSingleChars() {
        Predicate<ResourceLocation> star = GlobMatcher.toPredicate("tfmg:crafting/*_ingot");
        assertTrue(star.test(ResourceLocation.parse("tfmg:crafting/steel_ingot")));
        assertFalse(star.test(ResourceLocation.parse("tfmg:crafting/steel_plate")));

        Predicate<ResourceLocation> single = GlobMatcher.toPredicate("mod:tier_?_dust");
        assertTrue(single.test(ResourceLocation.parse("mod:tier_1_dust")));
        assertFalse(single.test(ResourceLocation.parse("mod:tier_10_dust")));
    }

    @Test
    void repeatedWildcardsAndRegexMetacharactersAreEscapedSafely() {
        assertTrue(GlobMatcher.isGlob("mod:a*b"));
        assertTrue(GlobMatcher.isGlob("mod:a?b"));
        assertFalse(GlobMatcher.isGlob("mod:plain_id"));

        Predicate<ResourceLocation> metachars = GlobMatcher.toPredicate("mod:a.b***");
        assertTrue(metachars.test(ResourceLocation.parse("mod:a.b_anything")));
        assertFalse(metachars.test(ResourceLocation.parse("mod:axb_anything")));
    }

    @Test
    void nullBlankAndInvalidInputsReturnFalsePredicate() {
        assertFalse(GlobMatcher.toPredicate(null).test(ResourceLocation.parse("minecraft:stone")));
        assertFalse(GlobMatcher.toPredicate("   ").test(ResourceLocation.parse("minecraft:stone")));
        assertFalse(GlobMatcher.toPredicate("Not A Valid ID!").test(ResourceLocation.parse("minecraft:stone")));
    }
}
