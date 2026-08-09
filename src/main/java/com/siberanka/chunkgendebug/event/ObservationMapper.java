package com.siberanka.chunkgendebug.event;

import com.siberanka.chunkgendebug.attribution.AttributionSnapshot;
import com.siberanka.chunkgendebug.attribution.PluginCandidate;
import com.siberanka.chunkgendebug.attribution.StackFrameView;
import com.siberanka.chunkgendebug.correlation.CausalMarker;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;

public final class ObservationMapper {
    private ObservationMapper() {
    }

    public static Map<String, Object> thread() {
        Thread current = Thread.currentThread();
        return Map.of(
                "name", current.getName(),
                "id", current.threadId(),
                "virtual", current.isVirtual(),
                "bukkitPrimary", Bukkit.isPrimaryThread());
    }

    public static Map<String, Object> chunk(Chunk chunk, boolean folia) {
        LinkedHashMap<String, Object> result = new LinkedHashMap<>();
        result.put("x", chunk.getX());
        result.put("z", chunk.getZ());
        result.put("packedKey", packedKey(chunk.getX(), chunk.getZ()));
        result.put("regionX", chunk.getX() >> 5);
        result.put("regionZ", chunk.getZ() >> 5);
        result.put("loaded", chunk.isLoaded());
        result.put("inhabitedTicks", chunk.getInhabitedTime());
        if (folia) {
            // Folia guards these world-global ticket reads even while this event owns the chunk region.
            result.put("forceLoaded", "UNAVAILABLE_REQUIRES_GLOBAL_REGION");
            result.put("pluginTickets", "UNAVAILABLE_REQUIRES_GLOBAL_REGION");
        } else {
            result.put("forceLoaded", chunk.isForceLoaded());
            result.put("pluginTickets", chunk.getPluginChunkTickets().stream()
                    .map(plugin -> plugin.getName())
                    .sorted()
                    .toList());
        }
        return Map.copyOf(result);
    }

    public static Map<String, Object> attribution(AttributionSnapshot snapshot) {
        List<Map<String, Object>> candidates = snapshot.pluginCandidates().stream()
                .map(ObservationMapper::candidate)
                .toList();
        List<Map<String, Object>> frames = snapshot.stack().stream()
                .map(ObservationMapper::frame)
                .toList();
        return Map.of(
                "status", snapshot.attributionStatus(),
                "mechanism", snapshot.mechanism().name(),
                "pluginCandidates", candidates,
                "stack", frames,
                "notice", "candidates/heuristics are evidence, not universal proof of semantic causation");
    }

    public static Map<String, Object> correlation(CausalMarker marker, long nowNanos) {
        if (marker == null) {
            return Map.of("status", "NONE");
        }
        return Map.of(
                "status", "CORRELATED_NOT_PROOF",
                "type", marker.type(),
                "ageMicros", Math.max(0, nowNanos - marker.createdNanos()) / 1_000L,
                "details", marker.details());
    }

    public static long packedKey(int x, int z) {
        return (x & 0xffffffffL) | ((z & 0xffffffffL) << 32);
    }

    private static Map<String, Object> candidate(PluginCandidate candidate) {
        return Map.of(
                "plugin", candidate.plugin(),
                "version", candidate.version(),
                "evidence", candidate.evidence(),
                "confidence", candidate.confidence(),
                "firstFrame", candidate.firstFrame());
    }

    private static Map<String, Object> frame(StackFrameView frame) {
        LinkedHashMap<String, Object> result = new LinkedHashMap<>();
        result.put("index", frame.index());
        result.put("class", frame.className());
        result.put("method", frame.methodName());
        if (frame.fileName() != null) {
            result.put("file", frame.fileName());
        }
        result.put("line", frame.lineNumber());
        if (frame.moduleName() != null) {
            result.put("module", frame.moduleName());
        }
        if (frame.plugin() != null) {
            result.put("plugin", frame.plugin());
        }
        return Map.copyOf(result);
    }
}
