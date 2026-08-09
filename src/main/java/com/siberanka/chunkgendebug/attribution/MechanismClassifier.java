package com.siberanka.chunkgendebug.attribution;

import java.util.List;
import java.util.Locale;

public final class MechanismClassifier {
    public Mechanism classify(List<String> frames, boolean hasPluginCandidate) {
        String joined = String.join("\n", frames).toLowerCase(Locale.ROOT);
        if (contains(joined, "chunkgenerator", "blockpopulator", "worldgen")) {
            return Mechanism.WORLD_GENERATOR;
        }
        if (contains(joined, "structurestart", "structuremanager", "structuregeneration")) {
            return Mechanism.STRUCTURE_GENERATION;
        }
        if (contains(joined, "playerchunkloader", "chunkmap$trackedentity", "viewdistance")) {
            return Mechanism.PLAYER_VIEW_DISTANCE;
        }
        if (contains(joined, "teleport", "placeinportal")) {
            return Mechanism.PLAYER_TELEPORT;
        }
        if (contains(joined, "portalforcer", "portalcreate", "portaltravel")) {
            return Mechanism.PORTAL;
        }
        if (contains(joined, "preparelevels", "loadspawn", "spawnchunks")) {
            return Mechanism.SPAWN_PRELOAD;
        }
        if (contains(joined, "forceload", "pluginticket", "addregionticket")) {
            return Mechanism.FORCED_CHUNK;
        }
        if (contains(joined, "bukkittask", "crafttask", "scheduledtask", "foliascheduledtask")) {
            return hasPluginCandidate ? Mechanism.PLUGIN_SCHEDULER : Mechanism.CHUNK_SYSTEM;
        }
        if (contains(joined, "commanddispatcher", "executecmd", "performcommand")) {
            return Mechanism.COMMAND;
        }
        if (hasPluginCandidate) {
            return Mechanism.PLUGIN_DIRECT;
        }
        if (contains(joined, "chunk", "regionized", "distanceManager".toLowerCase(Locale.ROOT))) {
            return Mechanism.CHUNK_SYSTEM;
        }
        return Mechanism.UNKNOWN;
    }

    private static boolean contains(String value, String... needles) {
        for (String needle : needles) {
            if (value.contains(needle.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }
}
