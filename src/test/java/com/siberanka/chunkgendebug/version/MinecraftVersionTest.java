package com.siberanka.chunkgendebug.version;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class MinecraftVersionTest {
    @Test
    void supportsOldAndNewVersionSchemesWithoutOneDotAssumption() {
        assertTrue(MinecraftVersion.parse("1.21").isDeclaredSupported());
        assertTrue(MinecraftVersion.parse("1.21.11-R0.1-SNAPSHOT").isDeclaredSupported());
        assertTrue(MinecraftVersion.parse("26.1").isDeclaredSupported());
        assertTrue(MinecraftVersion.parse("26.2.4").isDeclaredSupported());
        assertFalse(MinecraftVersion.parse("1.20.6").isDeclaredSupported());
    }

    @Test
    void comparesVariableComponentCounts() {
        assertTrue(MinecraftVersion.parse("26.1.1").compareTo(MinecraftVersion.parse("26.1")) > 0);
        assertTrue(MinecraftVersion.parse("26.2").compareTo(MinecraftVersion.parse("26.1.9")) > 0);
    }

    @Test
    void rejectsMalformedAndOverflowingVersions() {
        assertThrows(IllegalArgumentException.class, () -> MinecraftVersion.parse("v1.21"));
        assertThrows(IllegalArgumentException.class, () -> MinecraftVersion.parse("26"));
        assertThrows(NumberFormatException.class, () -> MinecraftVersion.parse("26.999999999999"));
    }
}
