package com.siberanka.chunkgendebug.context;

import com.siberanka.chunkgendebug.attribution.PluginIdentityIndex;
import com.siberanka.chunkgendebug.log.AsyncLogWriter;
import com.siberanka.chunkgendebug.log.DiagnosticRecord;
import com.siberanka.chunkgendebug.log.LogStream;
import com.siberanka.chunkgendebug.log.LogTarget;
import io.papermc.paper.datapack.Datapack;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.generator.BlockPopulator;
import org.bukkit.generator.ChunkGenerator;

public final class WorldContextRegistry {
    private final ConcurrentHashMap<UUID, ContextRef> contexts = new ConcurrentHashMap<>();
    private final AtomicLong revision = new AtomicLong();
    private final AsyncLogWriter writer;
    private final PluginIdentityIndex pluginIndex;

    public WorldContextRegistry(AsyncLogWriter writer, PluginIdentityIndex pluginIndex) {
        this.writer = writer;
        this.pluginIndex = pluginIndex;
    }

    public ContextRef refresh(World world, String reason) {
        String directory = writer.worldDirectory(world.getName());
        String contextId = world.getUID() + ":" + revision.incrementAndGet();
        ContextRef reference = new ContextRef(contextId, directory);
        contexts.put(world.getUID(), reference);

        List<String> datapacks = Bukkit.getServer().getDatapackManager().getEnabledPacks().stream()
                .map(Datapack::getName)
                .sorted()
                .toList();
        ChunkGenerator generator = world.getGenerator();
        List<Map<String, Object>> populators = new ArrayList<>();
        for (BlockPopulator populator : world.getPopulators()) {
            populators.add(owner(populator.getClass()));
        }
        populators.sort(Comparator.comparing(item -> String.valueOf(item.get("class"))));

        DiagnosticRecord record = DiagnosticRecord.builder("world_context", contextId)
                .put("reason", reason)
                .put("contextId", contextId)
                .put("server", serverContext())
                .put("world", Map.of(
                        "name", world.getName(),
                        "key", world.getKey().toString(),
                        "uuid", world.getUID().toString(),
                        "environment", world.getEnvironment().name(),
                        "minHeight", world.getMinHeight(),
                        "maxHeight", world.getMaxHeight(),
                        "structures", world.canGenerateStructures()))
                .put("generator", generator == null ? Map.of("class", "vanilla/default") : owner(generator.getClass()))
                .put("populators", List.copyOf(populators))
                .put("enabledDatapacks", datapacks)
                .put("attributionNotice", "datapacks/listeners are context, not proof of chunk causation")
                .build();
        for (LogStream stream : LogStream.values()) {
            writer.submit(new LogTarget(directory, stream), record);
        }
        return reference;
    }

    public ContextRef getOrCreate(World world) {
        ContextRef existing = contexts.get(world.getUID());
        if (existing != null) {
            return existing;
        }
        // Do not query global datapack/generator state from a Folia chunk callback.
        // WorldInit/WorldLoad will replace this minimal reference with full context.
        ContextRef minimal = new ContextRef(
                world.getUID() + ":lazy:" + revision.incrementAndGet(),
                writer.worldDirectory(world.getName()));
        ContextRef raced = contexts.putIfAbsent(world.getUID(), minimal);
        return raced != null ? raced : minimal;
    }

    public void remove(World world) {
        contexts.remove(world.getUID());
    }

    private Map<String, Object> owner(Class<?> type) {
        PluginIdentityIndex.Identity identity = pluginIndex.find(type);
        LinkedHashMap<String, Object> result = new LinkedHashMap<>();
        result.put("class", type.getName());
        if (identity != null) {
            result.put("plugin", identity.name());
            result.put("pluginVersion", identity.version());
            result.put("ownershipEvidence", "plugin classloader");
        } else {
            result.put("plugin", "none/server");
        }
        return Map.copyOf(result);
    }

    private static Map<String, Object> serverContext() {
        return Map.of(
                "name", Bukkit.getName(),
                "minecraftVersion", Bukkit.getMinecraftVersion(),
                "bukkitVersion", Bukkit.getBukkitVersion(),
                "javaVersion", System.getProperty("java.version"),
                "javaVendor", System.getProperty("java.vendor"));
    }

    public record ContextRef(String contextId, String worldDirectory) {
    }
}
