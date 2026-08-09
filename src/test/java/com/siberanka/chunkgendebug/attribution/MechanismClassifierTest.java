package com.siberanka.chunkgendebug.attribution;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.jupiter.api.Test;

class MechanismClassifierTest {
    private final MechanismClassifier classifier = new MechanismClassifier();

    @Test
    void prefersSpecificWorldGenerationEvidence() {
        assertEquals(Mechanism.WORLD_GENERATOR,
                classifier.classify(List.of("org.example.CustomChunkGenerator.generateNoise"), true));
    }

    @Test
    void distinguishesScheduledPluginAndServerChunkWork() {
        List<String> schedulerFrames = List.of("org.bukkit.craftbukkit.scheduler.CraftTask.run");
        assertEquals(Mechanism.PLUGIN_SCHEDULER, classifier.classify(schedulerFrames, true));
        assertEquals(Mechanism.CHUNK_SYSTEM, classifier.classify(schedulerFrames, false));
    }

    @Test
    void identifiesNewSchemeServerMechanismsWithoutObfuscatedNames() {
        assertEquals(Mechanism.PLAYER_VIEW_DISTANCE,
                classifier.classify(List.of("ca.spottedleaf.moonrise.PlayerChunkLoader.tick"), false));
        assertEquals(Mechanism.SPAWN_PRELOAD,
                classifier.classify(List.of("net.minecraft.server.MinecraftServer.prepareLevels"), false));
    }
}
